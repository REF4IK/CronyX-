#pragma once
// CronyFG compute chain: owns the passes, their images and descriptors.
//
// Passes per source frame:
//   luma       — composite RGB -> R16F luma at the flow (L0) resolution, one
//                image per frame parity
//   downsample — L0 luma -> L1 (half) luma, one image per parity
//   searchCoarse — block matching on L1, seeds zero + temporal (previous
//                frame's refined flow), wide search; writes flowCoarse (flow px)
//   searchFine   — block matching on L0, seed = upsampled coarse flow, tight
//                search; writes flow_ (raw block field)
//   refine     — componentwise 3x3 median, parabolic subpixel, confidence
//   interp     — bidirectional warp of the two real frames at full resolution
//
// Flow vectors are BACKWARD (curr -> prev), always in flow (L0) pixels.
// The search pipeline is one shader reused for both levels via push constants.

#include <cstdint>

#include <vulkan/vulkan.h>

#include "cronyfg_quality.h"

struct VkTable;

namespace cronyfg {

class Chain {
public:
    Chain(const VkTable& vk, VkDevice device, VkPhysicalDevice physicalDevice);
    ~Chain();

    Chain(const Chain&) = delete;
    Chain& operator=(const Chain&) = delete;

    bool valid() const { return pipelines_[0] != VK_NULL_HANDLE; }

    // (Re)builds the flow images for this output size and flow short side.
    bool prepare(uint32_t width, uint32_t height, uint32_t flowPixels);

    // Writes luma[parity] from the composite frame. Runs every source frame.
    void luma(VkCommandBuffer cmd, VkImageView source, uint32_t parity,
              uint32_t width, uint32_t height);

    // L0 luma -> L1 (half) luma for this parity. Runs every source frame so the
    // next frame always has a valid previous L1 image.
    void downsample(VkCommandBuffer cmd, uint32_t parity);

    // Coarse level: block matching on L1, temporal seed from the previous
    // frame's refined flow, writes flowCoarse (in flow pixels). `hasHistory`
    // enables the temporal and spatial seeds; false on the first frame after a
    // rebuild.
    void searchCoarse(VkCommandBuffer cmd, uint32_t parity, bool hasHistory);
    // Fine level: block matching on L0 seeded by the upsampled coarse flow,
    // writes flow_ (the raw block field consumed by refine).
    void searchFine(VkCommandBuffer cmd, uint32_t parity);
    // Forward level: the same search pipeline with curr/prev luma swapped, so
    // it produces prev->curr vectors (coarse/L1 grid, flow px). refine reads it
    // for the forward/backward round-trip occlusion test.
    void searchFwd(VkCommandBuffer cmd, uint32_t parity);
    void refine(VkCommandBuffer cmd, uint32_t parity);

    // Synthesises generation `generation` of `count` between prev and curr.
    void interp(VkCommandBuffer cmd, VkImage targetImage, VkImageView prev, VkImageView curr,
                VkImageView targetView, uint32_t generation, uint32_t count,
                uint32_t width, uint32_t height);

    uint32_t flowWidth() const { return flowW_; }
    uint32_t flowHeight() const { return flowH_; }

    // Пресет качества: задаёт firstStep поиска и tuning-пороги refine/interp.
    // Вызывается движком перед process()/interp(). Пороги round-trip пересчитаны
    // из долей в реальные flow-пиксели при записи push (chain знает flowW_/H_).
    void setQuality(const QualityPreset& q) { quality_ = q; }

private:
    enum Pass { kLuma = 0, kDownsample = 1, kSearch = 2, kRefine = 3, kInterp = 4,
               kPassCount = 5 };

    struct Image {
        VkImage        image = VK_NULL_HANDLE;
        VkDeviceMemory mem   = VK_NULL_HANDLE;
        VkImageView    view  = VK_NULL_HANDLE;
    };

    bool build();
    bool buildResources();
    void destroyResources();
    bool createImage(Image& out, uint32_t w, uint32_t h, VkFormat format);
    void destroyImage(Image& img);
    VkPipeline buildPipeline(VkShaderModule module);
    void writeSet(VkDescriptorSet set, VkImageView s0, VkImageView s1, VkImageView s2,
                  VkImageView s3, VkImageView storage);
    void bindPass(VkCommandBuffer cmd, Pass pass, VkDescriptorSet set,
                  const void* push, uint32_t pushSize);
    void memoryBarrier(VkCommandBuffer cmd);

    const VkTable& vk_;
    VkDevice        device_;
    VkPhysicalDevice physicalDevice_;

    VkDescriptorSetLayout setLayout_     = VK_NULL_HANDLE;
    VkPipelineLayout      pipelineLayout_ = VK_NULL_HANDLE;
    VkDescriptorPool      pool_           = VK_NULL_HANDLE;
    VkSampler             sampler_        = VK_NULL_HANDLE;
    VkShaderModule        modules_[kPassCount] = {};
    VkPipeline            pipelines_[kPassCount] = {};

    // One set per parity for the shared passes, plus 3 generations x 2 parities
    // for interp: a set is only rewritten after the frame that used it has been
    // waited on (MAX_FRAMES_IN_FLIGHT == 2).
    // Fixed passes per parity: luma, downsample, searchCoarse, searchFine,
    // searchFwd, refine.
    static constexpr uint32_t kGenerationsPerParity = 3;   // kMaxMultiplier - 1
    static constexpr uint32_t kMaxInterpSets = kGenerationsPerParity * 2;
    static constexpr uint32_t kFixedPasses = 6;            // luma+down+coarse+fine+fwd+refine
    static constexpr uint32_t kFixedSets = kFixedPasses * 2;
    VkDescriptorSet lumaSets_[2] = {};
    VkDescriptorSet downSets_[2] = {};
    VkDescriptorSet coarseSets_[2] = {};
    VkDescriptorSet fineSets_[2] = {};
    VkDescriptorSet fwdSets_[2] = {};
    VkDescriptorSet refineSets_[2] = {};
    VkDescriptorSet interpSets_[kMaxInterpSets] = {};

    Image luma_[2];        // L0 luma (flow resolution)
    Image lumaHalf_[2];    // L1 luma (half the flow resolution)
    Image flowCoarse_;     // coarse backward search output (L1 blocks, flow px)
    Image flowCoarseFwd_;  // coarse forward (prev->curr) search, round-trip test
    Image flow_;           // fine search output (raw block field)
    Image flowRefined_;    // refine output, also next frame's temporal source

    uint32_t outW_ = 0, outH_ = 0;
    uint32_t flowW_ = 0, flowH_ = 0;
    uint32_t blocksX_ = 0, blocksY_ = 0;
    uint32_t halfW_ = 0, halfH_ = 0;
    uint32_t halfBlocksX_ = 0, halfBlocksY_ = 0;
    uint32_t frameParity_ = 0;       // set by luma(), selects interp sets
    bool     initialized_ = false;   // images transitioned out of UNDEFINED

    QualityPreset quality_ = kCronyfgPresets[kCronyfgDefaultPreset];
};

} // namespace cronyfg
