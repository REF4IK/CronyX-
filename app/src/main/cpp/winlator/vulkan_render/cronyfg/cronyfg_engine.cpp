// See cronyfg_engine.h.

#include "cronyfg_engine.h"

#include "../VulkanRendererContext.h"   // VkTable
#include "cronyfg_chain.h"

#include <algorithm>
#include <cmath>

#define CRONYFG_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CronyFg", __VA_ARGS__)

namespace cronyfg {

namespace {

// Two real frames must exist before a generated one can be meaningful, and the
// plan has to stay warm for a couple of frames so one settled sample cannot
// start generation from a cold history.
constexpr uint64_t kRequiredFrames = 2;
constexpr uint32_t kRecurrenceFrames = 2;
constexpr uint32_t kMaxGenerations = framegen::kMaxMultiplier - 1;

// Passive governor thresholds. The present rate is window-smoothed in the
// renderer and lags by a few frames, so require a real streak before acting,
// shed only one generation at a time, and restore slowly (a phone can briefly
// burst, but a sustained deficit means the queue is backing up).
constexpr uint32_t kDeficitFrames   = 6;
constexpr uint32_t kHeadroomFrames  = 40;
constexpr float    kDeficitRatio    = 0.85f;   // below this -> overloaded
constexpr float    kHeadroomRatio   = 0.97f;   // above this -> has margin

} // namespace

Engine::Engine() = default;

Engine::~Engine() {
    chain_.reset();
}

bool Engine::init(const VkTable& vk, VkDevice device, VkPhysicalDevice physicalDevice) {
    if (device == VK_NULL_HANDLE) return false;
    device_ = device;

    chain_ = std::make_unique<Chain>(vk, device, physicalDevice);
    if (!chain_->valid()) {
        chain_.reset();
        unavailable_ = true;
        RLOG_E("cronyfg: chain build failed; engine unavailable");
        return false;
    }
    CRONYFG_LOGI("engine ready");
    return true;
}

void Engine::configure(uint32_t multiplier, uint32_t targetRate, uint32_t flowPixels,
                       float refreshRate, uint32_t qualityPreset) {
    multiplier_ = std::max(multiplier, 2u);
    targetRate_ = targetRate;
    // Разрешение потока и tuning берутся из пресета качества; flowPixels
    // (вызывается сверху) остаётся запасным значением для legacy-вызовов.
    qualityPreset_ = qualityPreset;
    quality_ = cronyfg::presetAt((int)qualityPreset);
    flowPixels_ = std::clamp(quality_.flowShort ? quality_.flowShort : flowPixels, 120u, 720u);
    if (refreshRate > 1.0f) refreshRate_ = refreshRate;

    framegen::Config config = pacer_.GetConfig();
    config.multiplier = multiplier_;
    config.target_rate = targetRate_;
    config.refresh_rate = refreshRate_;
    pacer_.SetConfig(config);
}

bool Engine::prepare(uint32_t width, uint32_t height, VkFormat format, uint32_t ringSlots) {
    if (unavailable_ || !chain_ || !chain_->valid()) return false;
    if (width == 0 || height == 0) return false;
    // Stage 1's interp shader writes RGBA8 and samples the composite format;
    // the renderer hardcodes it for the swapchain.
    if (format != VK_FORMAT_R8G8B8A8_UNORM) {
        RLOG_E("cronyfg: unsupported composite format %d", (int)format);
        unavailable_ = true;
        return false;
    }
    if (builtExtent_.width == width && builtExtent_.height == height &&
        builtFormat_ == format && builtFlowPixels_ == flowPixels_ && ringSlots_ == ringSlots) {
        return true;
    }
    if (!chain_->prepare(width, height, flowPixels_)) {
        RLOG_E("cronyfg: chain prepare failed at %ux%u flow=%u", width, height, flowPixels_);
        unavailable_ = true;
        return false;
    }
    builtExtent_ = VkExtent2D{width, height};
    builtFormat_ = format;
    builtFlowPixels_ = flowPixels_;
    ringSlots_ = ringSlots;
    frameCount_ = 0;
    lumaFrames_ = 0;
    flowFrames_ = 0;
    warmStreak_ = 0;
    planned_ = 0;
    loggedPlanned_ = 0xFFFFFFFFu;
    warm_ = false;
    capped_ = false;
    allowed_ = 0;
    deficitStreak_ = 0;
    headroomStreak_ = 0;
    presentedRate_ = 0.0f;
    pacer_.Reset();
    CRONYFG_LOGI("prepared %ux%u ring=%u flow=%u", width, height, ringSlots, flowPixels_);
    return true;
}

uint32_t Engine::plan(uint32_t capacity, uint64_t sourceFrames) {
    if (unavailable_ || !chain_ || !chain_->valid()) return 0;
    // The renderer must keep the real frame before the current one alive for
    // the whole source frame; without that, generation would read a slot that
    // a later generation is about to overwrite.
    if (ringSlots_ < multiplier_ + 2) {
        planned_ = 0;
        return 0;
    }

    framegen::Plan p =
        pacer_.PlanFor(std::min<size_t>(capacity, kMaxGenerations), sourceFrames);

    // "Fixed" multiplier ignores the panel budget, same as the shared pacer.
    // Cap it to the number of presents the panel can actually show per source
    // frame, floor(refresh/source). Using ceil here over-promised: 3x of a 50 fps
    // source on a 120 Hz panel queues 150 presents the panel drops, so every
    // third frame was late and the motion looked smeared. Never queue more than
    // refresh: extra presents add latency and drops, not smoothness.
    if (targetRate_ == 0 && refreshRate_ > 0.0f) {
        const float source = pacer_.GetStats().source_rate;
        if (source > 0.0f) {
            const float perFrame = refreshRate_ / source + 1.0e-3f;   // presents that fit
            const size_t maxPresents = perFrame < 1.0f ? 1u : (size_t)perFrame;
            const size_t maxGen = maxPresents >= 2u ? maxPresents - 1u : 0u;
            p.generations = std::min(p.generations, maxGen);
        }
    }

    warm_ = p.warm && frameCount_ + 1 >= kRequiredFrames;
    warmStreak_ = warm_ ? warmStreak_ + 1 : 0;
    const uint32_t desired =
        (warm_ && warmStreak_ >= kRecurrenceFrames) ? (uint32_t)p.generations : 0u;

    planned_ = desired;
    if (desired == 0) {
        // Cold or not generating: forget any cap so the next warm start obeys
        // the user's request outright.
        capped_ = false;
        deficitStreak_ = 0;
        headroomStreak_ = 0;
    } else if (governorEnabled_) {
        const float source = pacer_.GetStats().source_rate;
        if (presentedRate_ > 1.0f && source > 1.0f) {
            const uint32_t applied = capped_ ? std::min(allowed_, desired) : desired;
            const float need = source * (float)(applied + 1u);
            if (presentedRate_ < need * kDeficitRatio) {
                deficitStreak_++;
                headroomStreak_ = 0;
                if (deficitStreak_ >= kDeficitFrames) {
                    allowed_ = applied > 0u ? applied - 1u : 0u;
                    capped_ = allowed_ < desired;
                    deficitStreak_ = 0;
                }
            } else if (presentedRate_ >= need * kHeadroomRatio) {
                headroomStreak_++;
                deficitStreak_ = 0;
                if (capped_ && headroomStreak_ >= kHeadroomFrames) {
                    if (allowed_ < desired) ++allowed_;
                    capped_ = allowed_ < desired;
                    headroomStreak_ = 0;
                }
            } else {
                deficitStreak_ = 0;
                headroomStreak_ = 0;
            }
            planned_ = capped_ ? std::min(allowed_, desired) : desired;
        }
    }

    if (planned_ != loggedPlanned_) {
        const framegen::Stats s = pacer_.GetStats();
        CRONYFG_LOGI("plan generations=%u (want=%u cap=%d allowed=%u presented=%.1f "
                     "ring=%u multiplier=%u warm=%d streak=%u source=%.1f refresh=%.1f "
                     "target=%u)",
                     planned_, desired, (int)capped_, allowed_, (double)presentedRate_,
                     ringSlots_, multiplier_, (int)warm_, warmStreak_,
                     (double)s.source_rate, (double)refreshRate_, targetRate_);
        loggedPlanned_ = planned_;
    }
    return planned_;
}

void Engine::process(VkCommandBuffer cmd, uint32_t currSlot, VkImageView prev, VkImageView curr,
                     uint32_t width, uint32_t height, uint32_t generations) {
    if (!chain_ || !chain_->valid()) return;
    chain_->setQuality(quality_);
    prevView_ = prev;
    currView_ = curr;
    currSlot_ = currSlot;

    const uint32_t parity = (uint32_t)(frameCount_ & 1u);
    ++frameCount_;

    // Luma runs every source frame, even while idle: the next frame's search
    // needs a valid "previous" image, and one dispatch at flow resolution is
    // cheaper than detecting and reseeding a stale history. The L1 half is just
    // a box filter of luma, so it stays live for the same reason.
    chain_->luma(cmd, curr, parity, width, height);
    chain_->downsample(cmd, parity);
    ++lumaFrames_;

    if (generations == 0) return;

    // Temporal candidates are only valid after a frame that actually ran the
    // search/refine pair; the luma history alone is not enough. The coarse level
    // carries that temporal seed, the fine level is seeded by this frame's coarse
    // flow and so always runs.
    chain_->searchCoarse(cmd, parity, lumaFrames_ >= 2 && flowFrames_ > 0);
    chain_->searchFine(cmd, parity);
    // Forward (prev->curr) field for the round-trip occlusion test in refine.
    chain_->searchFwd(cmd, parity);
    chain_->refine(cmd, parity);
    ++flowFrames_;
}

void Engine::generateInto(VkCommandBuffer cmd, uint32_t generation, uint32_t count,
                          VkImage targetImage, VkImageView targetView,
                          uint32_t width, uint32_t height) {
    if (!chain_ || !chain_->valid()) return;
    chain_->setQuality(quality_);
    if (prevView_ == VK_NULL_HANDLE || currView_ == VK_NULL_HANDLE ||
        targetView == VK_NULL_HANDLE) {
        return;
    }
    chain_->interp(cmd, targetImage, prevView_, currView_, targetView,
                   generation, count, width, height);
}

float Engine::sourceRate() const {
    return pacer_.GetStats().source_rate;
}

uint32_t Engine::flowShort() const {
    if (!chain_) return 0u;
    return std::min(chain_->flowWidth(), chain_->flowHeight());
}

void Engine::reset() {
    pacer_.Reset();
    frameCount_ = 0;
    lumaFrames_ = 0;
    flowFrames_ = 0;
    warmStreak_ = 0;
    planned_ = 0;
    loggedPlanned_ = 0xFFFFFFFFu;
    warm_ = false;
    capped_ = false;
    allowed_ = 0;
    deficitStreak_ = 0;
    headroomStreak_ = 0;
    prevView_ = VK_NULL_HANDLE;
    currView_ = VK_NULL_HANDLE;
    currSlot_ = 0;
}

} // namespace cronyfg
