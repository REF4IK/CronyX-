// See cronyfg_chain.h.

#include "cronyfg_chain.h"

#include "../VulkanRendererContext.h"   // VkTable

#include "cronyfg_luma_comp.h"
#include "cronyfg_downsample_comp.h"
#include "cronyfg_search_comp.h"
#include "cronyfg_refine_comp.h"
#include "cronyfg_interp_comp.h"

#include <algorithm>
#include <cmath>

#define CRONYFG_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CronyFg", __VA_ARGS__)

namespace cronyfg {

namespace {

constexpr uint32_t kPushSize = 64;

uint32_t roundUp8(uint32_t v) {
    return (v + 7u) & ~7u;
}

struct LumaPush {
    float dims[4];   // srcW, srcH, dstW, dstH
};
// Reused by downsample: srcW, srcH (L0), dstW, dstH (L1).
struct SearchPush {
    float dims[4];   // lumaW, lumaH, blocksX, blocksY   (this level)
    float geom[4];   // pixelScale, firstStep, minStep, useNeighbors
    float flags[4];  // x = hasHistory, y = sadStride (coarse 2, fine 1)
};
struct RefinePush {
    float dims[4];    // flowW, flowH, blocksX, blocksY
    float tune[4];    // costScale, agreeScale, rtLoPx, rtHiPx
    float tune2[4];   // x = rtPenalty
};
struct InterpPush {
    float dims[4];    // dstW, dstH, flowW, flowH
    float params[4];  // x = t
    float tune2[4];   // x = gamutStrength, y = confFloor, z = sharpen, w = confGamma
};

} // namespace

Chain::Chain(const VkTable& vk, VkDevice device, VkPhysicalDevice physicalDevice)
    : vk_(vk), device_(device), physicalDevice_(physicalDevice) {
    build();
}

Chain::~Chain() {
    destroyResources();
    for (VkPipeline& p : pipelines_) {
        if (p != VK_NULL_HANDLE) vk_.DestroyPipeline(device_, p, nullptr);
    }
    for (VkShaderModule& m : modules_) {
        if (m != VK_NULL_HANDLE) vk_.DestroyShaderModule(device_, m, nullptr);
    }
    if (vk_.DestroySampler && sampler_ != VK_NULL_HANDLE)
        vk_.DestroySampler(device_, sampler_, nullptr);
    if (pool_           != VK_NULL_HANDLE) vk_.DestroyDescriptorPool(device_, pool_, nullptr);
    if (pipelineLayout_ != VK_NULL_HANDLE) vk_.DestroyPipelineLayout(device_, pipelineLayout_, nullptr);
    if (setLayout_      != VK_NULL_HANDLE) vk_.DestroyDescriptorSetLayout(device_, setLayout_, nullptr);
}

bool Chain::build() {
    const uint32_t* code[kPassCount] = {
        cronyfg_luma_comp_code,
        cronyfg_downsample_comp_code,
        cronyfg_search_comp_code,
        cronyfg_refine_comp_code,
        cronyfg_interp_comp_code,
    };
    const size_t sizes[kPassCount] = {
        sizeof(cronyfg_luma_comp_code),
        sizeof(cronyfg_downsample_comp_code),
        sizeof(cronyfg_search_comp_code),
        sizeof(cronyfg_refine_comp_code),
        sizeof(cronyfg_interp_comp_code),
    };

    for (int i = 0; i < kPassCount; ++i) {
        VkShaderModuleCreateInfo sci{};
        sci.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
        sci.codeSize = sizes[i];
        sci.pCode = code[i];
        if (vk_.CreateShaderModule(device_, &sci, nullptr, &modules_[i]) != VK_SUCCESS) {
            RLOG_E("cronyfg: shader module %d failed", i);
            return false;
        }
    }

    VkDescriptorSetLayoutBinding bindings[5]{};
    for (uint32_t i = 0; i < 4; ++i) {
        bindings[i].binding = i;
        bindings[i].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        bindings[i].descriptorCount = 1;
        bindings[i].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    }
    bindings[4].binding = 4;
    bindings[4].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    bindings[4].descriptorCount = 1;
    bindings[4].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    VkDescriptorSetLayoutCreateInfo sli{};
    sli.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    sli.bindingCount = 5;
    sli.pBindings = bindings;
    if (vk_.CreateDescriptorSetLayout(device_, &sli, nullptr, &setLayout_) != VK_SUCCESS) {
        RLOG_E("cronyfg: descriptor set layout failed");
        return false;
    }

    VkPushConstantRange range{};
    range.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
    range.offset = 0;
    range.size = kPushSize;

    VkPipelineLayoutCreateInfo pli{};
    pli.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    pli.setLayoutCount = 1;
    pli.pSetLayouts = &setLayout_;
    pli.pushConstantRangeCount = 1;
    pli.pPushConstantRanges = &range;
    if (vk_.CreatePipelineLayout(device_, &pli, nullptr, &pipelineLayout_) != VK_SUCCESS) {
        RLOG_E("cronyfg: pipeline layout failed");
        return false;
    }

    VkDescriptorPoolSize sizesPool[2]{};
    sizesPool[0].type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    sizesPool[0].descriptorCount = 128;
    sizesPool[1].type = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
    sizesPool[1].descriptorCount = 40;
    VkDescriptorPoolCreateInfo dpi{};
    dpi.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    dpi.maxSets = 40;
    dpi.poolSizeCount = 2;
    dpi.pPoolSizes = sizesPool;
    if (vk_.CreateDescriptorPool(device_, &dpi, nullptr, &pool_) != VK_SUCCESS) {
        RLOG_E("cronyfg: descriptor pool failed");
        return false;
    }

    VkDescriptorSet sets[kFixedSets + kMaxInterpSets]{};
    VkDescriptorSetLayout layouts[kFixedSets + kMaxInterpSets]{};
    for (VkDescriptorSetLayout& l : layouts) l = setLayout_;
    VkDescriptorSetAllocateInfo dsai{};
    dsai.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    dsai.descriptorPool = pool_;
    dsai.descriptorSetCount = kFixedSets + kMaxInterpSets;
    dsai.pSetLayouts = layouts;
    if (vk_.AllocateDescriptorSets(device_, &dsai, sets) != VK_SUCCESS) {
        RLOG_E("cronyfg: descriptor sets failed");
        return false;
    }
    for (uint32_t p = 0; p < 2; ++p) {
        lumaSets_[p]    = sets[p * kFixedPasses + 0];
        downSets_[p]    = sets[p * kFixedPasses + 1];
        coarseSets_[p]  = sets[p * kFixedPasses + 2];
        fineSets_[p]    = sets[p * kFixedPasses + 3];
        fwdSets_[p]     = sets[p * kFixedPasses + 4];
        refineSets_[p]  = sets[p * kFixedPasses + 5];
    }
    for (uint32_t i = 0; i < kMaxInterpSets; ++i) interpSets_[i] = sets[kFixedSets + i];
    VkSamplerCreateInfo smi{};
    smi.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    smi.magFilter = VK_FILTER_LINEAR;
    smi.minFilter = VK_FILTER_LINEAR;
    smi.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;
    smi.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    smi.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    smi.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    if (vk_.CreateSampler(device_, &smi, nullptr, &sampler_) != VK_SUCCESS) {
        RLOG_E("cronyfg: sampler failed");
        return false;
    }

    for (int i = 0; i < kPassCount; ++i) {
        pipelines_[i] = buildPipeline(modules_[i]);
        if (pipelines_[i] == VK_NULL_HANDLE) {
            RLOG_E("cronyfg: pipeline %d failed", i);
            return false;
        }
    }
    return true;
}

VkPipeline Chain::buildPipeline(VkShaderModule module) {
    VkComputePipelineCreateInfo cpi{};
    cpi.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
    cpi.stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    cpi.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    cpi.stage.module = module;
    cpi.stage.pName = "main";
    cpi.layout = pipelineLayout_;
    VkPipeline pipeline = VK_NULL_HANDLE;
    if (vk_.CreateComputePipelines(device_, VK_NULL_HANDLE, 1, &cpi, nullptr, &pipeline)
            != VK_SUCCESS) {
        return VK_NULL_HANDLE;
    }
    return pipeline;
}

bool Chain::prepare(uint32_t width, uint32_t height, uint32_t flowPixels) {
    if (!valid() || width == 0 || height == 0 || flowPixels == 0) return false;

    const uint32_t shortSide = std::max(1u, std::min(width, height));
    const float scale = (float)flowPixels / (float)shortSide;
    const uint32_t flowW = std::max(8u, roundUp8((uint32_t)std::lround((float)width * scale)));
    const uint32_t flowH = std::max(8u, roundUp8((uint32_t)std::lround((float)height * scale)));

    if (outW_ == width && outH_ == height && flowW_ == flowW && flowH_ == flowH &&
        luma_[0].image != VK_NULL_HANDLE) {
        return true;
    }

    destroyResources();
    outW_ = width;
    outH_ = height;
    flowW_ = flowW;
    flowH_ = flowH;
    blocksX_ = flowW_ / 8;
    blocksY_ = flowH_ / 8;
    halfW_ = std::max(8u, roundUp8(flowW_ / 2u));
    halfH_ = std::max(8u, roundUp8(flowH_ / 2u));
    halfBlocksX_ = halfW_ / 8;
    halfBlocksY_ = halfH_ / 8;
    if (!buildResources()) {
        destroyResources();
        return false;
    }
    CRONYFG_LOGI("cronyfg: chain %ux%u flow %ux%u blocks %ux%u half %ux%u halfBlocks %ux%u",
         width, height, flowW_, flowH_, blocksX_, blocksY_, halfW_, halfH_,
         halfBlocksX_, halfBlocksY_);
    return true;
}

bool Chain::buildResources() {
    if (!createImage(luma_[0], flowW_, flowH_, VK_FORMAT_R16_SFLOAT)) return false;
    if (!createImage(luma_[1], flowW_, flowH_, VK_FORMAT_R16_SFLOAT)) return false;
    if (!createImage(lumaHalf_[0], halfW_, halfH_, VK_FORMAT_R16_SFLOAT)) return false;
    if (!createImage(lumaHalf_[1], halfW_, halfH_, VK_FORMAT_R16_SFLOAT)) return false;
    if (!createImage(flowCoarse_, halfBlocksX_, halfBlocksY_, VK_FORMAT_R16G16B16A16_SFLOAT)) return false;
    if (!createImage(flowCoarseFwd_, halfBlocksX_, halfBlocksY_, VK_FORMAT_R16G16B16A16_SFLOAT)) return false;
    if (!createImage(flow_, blocksX_, blocksY_, VK_FORMAT_R16G16B16A16_SFLOAT)) return false;
    if (!createImage(flowRefined_, blocksX_, blocksY_, VK_FORMAT_R16G16B16A16_SFLOAT)) return false;
    initialized_ = false;
    return true;
}

bool Chain::createImage(Image& out, uint32_t w, uint32_t h, VkFormat format) {
    VkImageCreateInfo ii{};
    ii.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ii.imageType = VK_IMAGE_TYPE_2D;
    ii.extent = VkExtent3D{w, h, 1};
    ii.mipLevels = 1;
    ii.arrayLayers = 1;
    ii.format = format;
    ii.tiling = VK_IMAGE_TILING_OPTIMAL;
    ii.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    ii.usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT;
    ii.samples = VK_SAMPLE_COUNT_1_BIT;
    ii.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateImage(device_, &ii, nullptr, &out.image) != VK_SUCCESS) {
        RLOG_E("cronyfg: image %ux%u fmt=%d failed", w, h, (int)format);
        return false;
    }

    VkMemoryRequirements req{};
    vk_.GetImageMemoryRequirements(device_, out.image, &req);
    VkPhysicalDeviceMemoryProperties mp{};
    vk_.GetPhysicalDeviceMemoryProperties(physicalDevice_, &mp);
    uint32_t typeIndex = 0;
    bool found = false;
    for (uint32_t i = 0; i < mp.memoryTypeCount; ++i) {
        if ((req.memoryTypeBits & (1u << i)) &&
            (mp.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
            typeIndex = i;
            found = true;
            break;
        }
    }
    if (!found) {
        for (uint32_t i = 0; i < mp.memoryTypeCount; ++i) {
            if (req.memoryTypeBits & (1u << i)) { typeIndex = i; break; }
        }
    }

    VkMemoryAllocateInfo ai{};
    ai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    ai.allocationSize = req.size;
    ai.memoryTypeIndex = typeIndex;
    if (vk_.AllocateMemory(device_, &ai, nullptr, &out.mem) != VK_SUCCESS) {
        destroyImage(out);
        return false;
    }
    if (vk_.BindImageMemory(device_, out.image, out.mem, 0) != VK_SUCCESS) {
        destroyImage(out);
        return false;
    }

    VkImageViewCreateInfo vci{};
    vci.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    vci.image = out.image;
    vci.viewType = VK_IMAGE_VIEW_TYPE_2D;
    vci.format = format;
    vci.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    if (vk_.CreateImageView(device_, &vci, nullptr, &out.view) != VK_SUCCESS) {
        destroyImage(out);
        return false;
    }
    return true;
}

void Chain::destroyImage(Image& img) {
    if (img.view != VK_NULL_HANDLE && vk_.DestroyImageView) {
        vk_.DestroyImageView(device_, img.view, nullptr);
    }
    if (img.image != VK_NULL_HANDLE) vk_.DestroyImage(device_, img.image, nullptr);
    if (img.mem != VK_NULL_HANDLE) vk_.FreeMemory(device_, img.mem, nullptr);
    img = Image{};
}

void Chain::destroyResources() {
    for (Image& img : luma_) destroyImage(img);
    for (Image& img : lumaHalf_) destroyImage(img);
    destroyImage(flowCoarse_);
    destroyImage(flowCoarseFwd_);
    destroyImage(flow_);
    destroyImage(flowRefined_);
    initialized_ = false;
}

void Chain::writeSet(VkDescriptorSet set, VkImageView s0, VkImageView s1, VkImageView s2,
                     VkImageView s3, VkImageView storage) {
    VkDescriptorImageInfo infos[5]{};
    infos[0] = {sampler_, s0, VK_IMAGE_LAYOUT_GENERAL};
    infos[1] = {sampler_, s1, VK_IMAGE_LAYOUT_GENERAL};
    infos[2] = {sampler_, s2, VK_IMAGE_LAYOUT_GENERAL};
    infos[3] = {sampler_, s3, VK_IMAGE_LAYOUT_GENERAL};
    infos[4] = {VK_NULL_HANDLE, storage, VK_IMAGE_LAYOUT_GENERAL};

    VkWriteDescriptorSet writes[5]{};
    for (uint32_t i = 0; i < 5; ++i) {
        writes[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        writes[i].dstSet = set;
        writes[i].dstBinding = i;
        writes[i].descriptorType = (i == 4) ? VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
                                            : VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        writes[i].descriptorCount = 1;
        writes[i].pImageInfo = &infos[i];
    }
    vk_.UpdateDescriptorSets(device_, 5, writes, 0, nullptr);
}

void Chain::bindPass(VkCommandBuffer cmd, Pass pass, VkDescriptorSet set,
                     const void* push, uint32_t pushSize) {
    vk_.CmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipelines_[pass]);
    vk_.CmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout_,
                              0, 1, &set, 0, nullptr);
    vk_.CmdPushConstants(cmd, pipelineLayout_, VK_SHADER_STAGE_COMPUTE_BIT, 0, pushSize, push);
}

void Chain::memoryBarrier(VkCommandBuffer cmd) {
    VkMemoryBarrier mb{};
    mb.sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER;
    mb.srcAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
    mb.dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
    vk_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                           VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                           0, 1, &mb, 0, nullptr, 0, nullptr);
}

void Chain::luma(VkCommandBuffer cmd, VkImageView source, uint32_t parity,
                 uint32_t width, uint32_t height) {
    if (!valid() || source == VK_NULL_HANDLE || luma_[parity & 1u].view == VK_NULL_HANDLE)
        return;

    // Slot-0 buffers carry no renderer barrier of their own; order this frame's
    // chain reads/writes against the previous frame's chain in submission order.
    memoryBarrier(cmd);

    if (!initialized_) {
        // First use after (re)build: move every image out of UNDEFINED once.
        VkImageMemoryBarrier bars[8]{};
        Image* images[8] = {&luma_[0], &luma_[1], &lumaHalf_[0], &lumaHalf_[1],
                            &flowCoarse_, &flowCoarseFwd_, &flow_, &flowRefined_};
        for (int i = 0; i < 8; ++i) {
            bars[i].sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            bars[i].oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            bars[i].newLayout = VK_IMAGE_LAYOUT_GENERAL;
            bars[i].srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            bars[i].dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            bars[i].image = images[i]->image;
            bars[i].subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            bars[i].dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
        }
        vk_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                               VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                               0, 0, nullptr, 0, nullptr, 8, bars);
        initialized_ = true;
    }

    LumaPush push{};
    push.dims[0] = (float)width;
    push.dims[1] = (float)height;
    push.dims[2] = (float)flowW_;
    push.dims[3] = (float)flowH_;

    const Image& target = luma_[parity & 1u];
    frameParity_ = parity & 1u;
    writeSet(lumaSets_[parity & 1u], source, source, source, source, target.view);
    bindPass(cmd, kLuma, lumaSets_[parity & 1u], &push, sizeof(push));
    vk_.CmdDispatch(cmd, (flowW_ + 7u) / 8u, (flowH_ + 7u) / 8u, 1);
    memoryBarrier(cmd);
}

void Chain::downsample(VkCommandBuffer cmd, uint32_t parity) {
    if (!valid() || luma_[parity & 1u].view == VK_NULL_HANDLE ||
        lumaHalf_[parity & 1u].view == VK_NULL_HANDLE) {
        return;
    }

    LumaPush push{};
    push.dims[0] = (float)flowW_;
    push.dims[1] = (float)flowH_;
    push.dims[2] = (float)halfW_;
    push.dims[3] = (float)halfH_;

    const Image& src = luma_[parity & 1u];
    const Image& target = lumaHalf_[parity & 1u];
    writeSet(downSets_[parity & 1u], src.view, src.view, src.view, src.view, target.view);
    bindPass(cmd, kDownsample, downSets_[parity & 1u], &push, sizeof(push));
    vk_.CmdDispatch(cmd, (halfW_ + 7u) / 8u, (halfH_ + 7u) / 8u, 1);
    memoryBarrier(cmd);
}

void Chain::searchCoarse(VkCommandBuffer cmd, uint32_t parity, bool hasHistory) {
    if (!valid() || flowCoarse_.view == VK_NULL_HANDLE) return;

    SearchPush push{};
    push.dims[0] = (float)halfW_;
    push.dims[1] = (float)halfH_;
    push.dims[2] = (float)halfBlocksX_;
    push.dims[3] = (float)halfBlocksY_;
    push.geom[0] = 2.0f;    // 1 L1 px = 2 flow px
    push.geom[1] = quality_.coarseFirstStep;   // first step, flow px (пресет)
    push.geom[2] = 2.0f;    // min step, flow px
    push.geom[3] = 1.0f;    // use spatial neighbours
    push.flags[0] = hasHistory ? 1.0f : 0.0f;
    push.flags[1] = 2.0f;   // coarse: прорежённая SAD (4x4) — MV доводит fine-поиск

    const Image& curr = lumaHalf_[parity & 1u];
    const Image& prev = lumaHalf_[(parity & 1u) ^ 1u];
    // Temporal seed: the previous frame's refined flow (still in flow pixels).
    // binding 3 (the extra sampler slot) is unused by search; bind a valid view.
    writeSet(coarseSets_[parity & 1u], curr.view, prev.view, flowRefined_.view, flowCoarseFwd_.view, flowCoarse_.view);
    bindPass(cmd, kSearch, coarseSets_[parity & 1u], &push, sizeof(push));
    vk_.CmdDispatch(cmd, (halfBlocksX_ + 7u) / 8u, (halfBlocksY_ + 7u) / 8u, 1);
    memoryBarrier(cmd);
}

void Chain::searchFine(VkCommandBuffer cmd, uint32_t parity) {
    if (!valid() || flow_.view == VK_NULL_HANDLE) return;

    SearchPush push{};
    push.dims[0] = (float)flowW_;
    push.dims[1] = (float)flowH_;
    push.dims[2] = (float)blocksX_;
    push.dims[3] = (float)blocksY_;
    push.geom[0] = 1.0f;    // L0: 1 luma px = 1 flow px
    push.geom[1] = 2.0f;    // first step, flow px
    push.geom[2] = 1.0f;    // min step, flow px
    push.geom[3] = 1.0f;    // spatial neighbours: helps propagate corrections
    push.flags[0] = 1.0f;   // coarse flow always exists for this frame
    push.flags[1] = 1.0f;   // fine: полная SAD 8x8 (точный MV)

    const Image& curr = luma_[parity & 1u];
    const Image& prev = luma_[(parity & 1u) ^ 1u];
    // Upsampled coarse flow is the seed; written into the L0 block field.
    writeSet(fineSets_[parity & 1u], curr.view, prev.view, flowCoarse_.view, flowCoarseFwd_.view, flow_.view);
    bindPass(cmd, kSearch, fineSets_[parity & 1u], &push, sizeof(push));
    vk_.CmdDispatch(cmd, (blocksX_ + 7u) / 8u, (blocksY_ + 7u) / 8u, 1);
    memoryBarrier(cmd);
}

void Chain::searchFwd(VkCommandBuffer cmd, uint32_t parity) {
    if (!valid() || flowCoarseFwd_.view == VK_NULL_HANDLE) return;

    SearchPush push{};
    push.dims[0] = (float)halfW_;
    push.dims[1] = (float)halfH_;
    push.dims[2] = (float)halfBlocksX_;
    push.dims[3] = (float)halfBlocksY_;
    push.geom[0] = 2.0f;    // 1 L1 px = 2 flow px
    push.geom[1] = quality_.coarseFirstStep;   // first step, flow px (пресет)
    push.geom[2] = 2.0f;    // min step, flow px
    push.geom[3] = 1.0f;    // (ignored: no history seed on the first frame)
    push.flags[0] = 0.0f;   // fresh search: zero candidate + TSS, no temporal seed
    push.flags[1] = 2.0f;   // forward coarse: та же прорежённая SAD

    // Swap the roles: index blocks on the PREV frame, match into the CURR frame,
    // so the stored vectors point prev -> curr. refine samples this at the
    // backward flow's landing point for the round-trip test.
    const Image& curr = lumaHalf_[(parity & 1u) ^ 1u];   // prev frame, the "current" of this search
    const Image& prev = lumaHalf_[parity & 1u];          // curr frame, the search target
    writeSet(fwdSets_[parity & 1u], curr.view, prev.view, flowCoarse_.view, prev.view, flowCoarseFwd_.view);
    bindPass(cmd, kSearch, fwdSets_[parity & 1u], &push, sizeof(push));
    vk_.CmdDispatch(cmd, (halfBlocksX_ + 7u) / 8u, (halfBlocksY_ + 7u) / 8u, 1);
    memoryBarrier(cmd);
}

void Chain::refine(VkCommandBuffer cmd, uint32_t parity) {
    if (!valid() || flowRefined_.view == VK_NULL_HANDLE) return;

    RefinePush push{};
    push.dims[0] = (float)flowW_;
    push.dims[1] = (float)flowH_;
    push.dims[2] = (float)blocksX_;
    push.dims[3] = (float)blocksY_;
    // Пороги round-trip заданы как доли короткого поля потока: пересчитываем их
    // в реальные flow-пиксели, чтобы они масштабировались с разрешением (главное
    // исправление «поток меняется, плавность нет»). Остальное — множители conf.
    const float flowShortPx = (float)std::min(flowW_, flowH_);
    push.tune[0] = quality_.costScale;
    push.tune[1] = quality_.agreeScale;
    push.tune[2] = quality_.rtLoFrac * flowShortPx;
    push.tune[3] = quality_.rtHiFrac * flowShortPx;
    push.tune2[0] = quality_.rtPenalty;

    const Image& curr = luma_[parity & 1u];
    const Image& prev = luma_[(parity & 1u) ^ 1u];
    // binding 3 = the forward field, read for the round-trip occlusion test.
    writeSet(refineSets_[parity & 1u], flow_.view, curr.view, prev.view, flowCoarseFwd_.view, flowRefined_.view);
    bindPass(cmd, kRefine, refineSets_[parity & 1u], &push, sizeof(push));
    vk_.CmdDispatch(cmd, (blocksX_ + 7u) / 8u, (blocksY_ + 7u) / 8u, 1);
    memoryBarrier(cmd);
}

void Chain::interp(VkCommandBuffer cmd, VkImage targetImage, VkImageView prev, VkImageView curr,
                   VkImageView target, uint32_t generation, uint32_t count,
                   uint32_t width, uint32_t height) {
    if (!valid() || targetImage == VK_NULL_HANDLE || prev == VK_NULL_HANDLE ||
        curr == VK_NULL_HANDLE || target == VK_NULL_HANDLE) {
        return;
    }

    // The target is a ring slot whose contents are fully overwritten here; the
    // barrier also moves a never-used slot out of UNDEFINED.
    VkImageMemoryBarrier targetBar{};
    targetBar.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    targetBar.srcAccessMask = 0;
    targetBar.dstAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    targetBar.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    targetBar.newLayout = VK_IMAGE_LAYOUT_GENERAL;
    targetBar.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    targetBar.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    targetBar.image = targetImage;
    targetBar.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vk_.CmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                           VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                           0, 0, nullptr, 0, nullptr, 1, &targetBar);

    const float t = (float)(generation + 1u) / (float)(count + 1u);
    InterpPush push{};
    push.dims[0] = (float)width;
    push.dims[1] = (float)height;
    push.dims[2] = (float)flowW_;
    push.dims[3] = (float)flowH_;
    push.params[0] = t;
    // Multi-scale/selective warping: включается пресетом (msSel>0.5, presets 3/4/5),
    // применяется в шейдере только на быстрых пикселях. params.y=enable, z=scale, w=beta.
    push.params[1] = quality_.msSel;
    push.params[2] = 0.75f;   // msScale — альтернативный масштаб величины потока
    push.params[3] = 25.0f;   // msBeta — резкость sigmoid-выбора по gamut-penalty
    push.tune2[0] = quality_.gamutStrength;
    push.tune2[1] = quality_.confFloor;
    push.tune2[2] = quality_.sharpen;
    push.tune2[3] = quality_.confGamma;

    VkDescriptorSet set =
        interpSets_[frameParity_ * kGenerationsPerParity + (generation % kGenerationsPerParity)];
    writeSet(set, flowRefined_.view, prev, curr, flowRefined_.view, target);
    bindPass(cmd, kInterp, set, &push, sizeof(push));
    vk_.CmdDispatch(cmd, (width + 7u) / 8u, (height + 7u) / 8u, 1);
}

} // namespace cronyfg
