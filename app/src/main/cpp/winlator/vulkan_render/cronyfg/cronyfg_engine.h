#pragma once
// CronyFG — in-house frame generation for the host compositor.
//
// Contract with VulkanRendererContext (the owner of the composite ring):
//   * `prev`/`curr` are ring slots in VK_IMAGE_LAYOUT_GENERAL, same format as
//     the swapchain; the renderer keeps `curr` and the real frame before it
//     alive for the whole source frame (ring size >= multiplier + 2).
//   * `generateInto` writes `targetImage` via a storage image, leaving it in
//     GENERAL. The renderer copies it to the acquired swapchain image itself.
//   * Every call happens on the render thread. The chain records its own
//     execution barriers; layouts never change.

#include <cstdint>
#include <memory>

#include <vulkan/vulkan.h>

#include "../framegen/framegen_pacer.h"

#include "cronyfg_quality.h"

struct VkTable;

namespace cronyfg {

struct Caps {
    bool ok = false;
    char reason[160] = "not probed";
};

class Chain;

class Engine {
public:
    Engine();
    ~Engine();

    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    bool init(const VkTable& vk, VkDevice device, VkPhysicalDevice physicalDevice);
    bool valid() const { return chain_ != nullptr; }
    bool unavailable() const { return unavailable_; }

    void configure(uint32_t multiplier, uint32_t targetRate, uint32_t flowPixels,
                   float refreshRate, uint32_t qualityPreset);

    bool prepare(uint32_t width, uint32_t height, VkFormat format, uint32_t ringSlots);
    uint32_t plan(uint32_t capacity, uint64_t sourceFrames);

    void process(VkCommandBuffer cmd, uint32_t currSlot, VkImageView prev, VkImageView curr,
                 uint32_t width, uint32_t height, uint32_t generations);

    void generateInto(VkCommandBuffer cmd, uint32_t generation, uint32_t count,
                      VkImage targetImage, VkImageView targetView,
                      uint32_t width, uint32_t height);

    float sourceRate() const;
    uint32_t plannedGenerations() const { return planned_; }

    // Effective flow short side actually built (px, the short side of the L0
    // flow field). Surfaced for the panel so the Flow Scale slider is visible.
    uint32_t flowShort() const;

    // The rate that actually reaches the panel (real + generated presents).
    // Supplied by the renderer, the only thing that counts presents; feeds the
    // passive overload limiter in plan().
    void setPresentedRate(float fps) { presentedRate_ = fps; }

    void reset();

private:
    VkDevice device_ = VK_NULL_HANDLE;

    std::unique_ptr<Chain> chain_;
    framegen::Pacer pacer_;

    uint32_t multiplier_ = 2;
    uint32_t targetRate_ = 0;
    uint32_t flowPixels_ = 252;
    float    refreshRate_ = 0.0f;

    // Пресет качества (эконом..максимальный). Задаёт flowPixels_ и tuning-пороги,
    // которые chain кладёт в push при каждом process()/interp().
    uint32_t     qualityPreset_ = kCronyfgDefaultPreset;
    QualityPreset quality_ = kCronyfgPresets[kCronyfgDefaultPreset];

    VkExtent2D builtExtent_{};
    VkFormat   builtFormat_ = VK_FORMAT_UNDEFINED;
    uint32_t   builtFlowPixels_ = 0;
    uint32_t   ringSlots_ = 0;

    VkImageView prevView_ = VK_NULL_HANDLE;
    VkImageView currView_ = VK_NULL_HANDLE;
    uint32_t    currSlot_ = 0;

    uint64_t frameCount_ = 0;
    uint64_t lumaFrames_ = 0;
    uint64_t flowFrames_ = 0;
    uint32_t warmStreak_ = 0;
    uint32_t planned_ = 0;
    uint32_t loggedPlanned_ = 0xFFFFFFFFu;
    bool     warm_ = false;
    bool     unavailable_ = false;

    // Passive governor: normally the pacer's plan is obeyed. Only when the
    // measured present rate falls clearly below what the current generation
    // count demands do we shed one generation, and we hand it back after a long
    // stretch of headroom. Never probes above the user's request.
    // OFF by default (same call as LSFG): a dynamic veto that misreads a
    // vsync-limited or lagging present rate makes generation "stop working".
    float    presentedRate_ = 0.0f;
    bool     governorEnabled_ = false;
    bool     capped_ = false;
    uint32_t allowed_ = 0;
    uint32_t deficitStreak_ = 0;
    uint32_t headroomStreak_ = 0;
};

} // namespace cronyfg
