#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wmissing-field-initializers"
#include "VulkanRendererContext.h"
#include "lsfg/lsfg_engine.h"
#include "lsfg/lsfg_vkd.h"
#include <stdexcept>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <inttypes.h>
#include <dlfcn.h>
#include <cstdio>
#include <sys/stat.h>
#include "window_vert.h"
#include "window_frag.h"
#include "effect_vert.h"
#include "effect_color_frag.h"
#include "effect_fxaa_frag.h"
#include "effect_crt_frag.h"
#include "effect_toon_frag.h"
#include "effect_vignette_frag.h"
#include "effect_sepia_frag.h"
#include "effect_blur_frag.h"
#include "effect_pixelate_frag.h"
#include "effect_grayscale_frag.h"
#include "effect_sharpen_frag.h"
#include "effect_smooth_frag.h"
#include "effect_hdr_frag.h"
#include "effect_ntsc_frag.h"

VulkanRendererContext::VulkanRendererContext(ANativeWindow* win, int cW, int cH, void* aHandle)
    : window(win), surfaceWidth(cW), surfaceHeight(cH), containerWidth(cW), containerHeight(cH),
      adrenotoolsHandle(aHandle)
{
    createInstance(); createSurface(); pickPhysicalDevice(); createLogicalDevice();
    createSwapchain(); createRenderPass(); createDSLayout();
    createPipeline(true, pipeline);
    createFramebuffers(); createCmdPool(); createSampler();
    createWinTexPool(); createCursorDS(); createCmdBufs(); createSyncObjects();
    createEffectResources();
    isRunning = true;
    renderThread = std::thread(&VulkanRendererContext::renderLoop, this);
}

VulkanRendererContext::~VulkanRendererContext() {
    isRunning = false; dirtyCV.notify_all();
    if (renderThread.joinable()) renderThread.join();
    std::lock_guard<std::mutex> lk(renderMutex);
    vk_.DeviceWaitIdle(device);
    vk_.QueueWaitIdle(graphicsQueue);
    for (auto& [id, wt] : texMap) destroyWinTex(wt);
    texMap.clear();
    
    for (auto& wt : deleteQueue) {
        if (wt.ds   != VK_NULL_HANDLE) vk_.FreeDescriptorSets(device, winTexPool, 1, &wt.ds);
        if (wt.view != VK_NULL_HANDLE) vk_.DestroyImageView(device, wt.view, nullptr);
        if (wt.img  != VK_NULL_HANDLE) vk_.DestroyImage(device, wt.img, nullptr);
        if (wt.mem  != VK_NULL_HANDLE) vk_.FreeMemory(device, wt.mem, nullptr);
        if (wt.stg  != VK_NULL_HANDLE) { vk_.DestroyBuffer(device, wt.stg, nullptr); vk_.FreeMemory(device, wt.stgMem, nullptr); }
    }
    deleteQueue.clear();
    cleanupSwapchain(); cleanupCursorTex();
    destroyEffectResources();
    
    vk_.DestroySampler(device, sampler, nullptr);
    vk_.DestroyDescriptorPool(device, winTexPool, nullptr);
    vk_.DestroyPipeline(device, pipeline, nullptr);
    vk_.DestroyPipelineLayout(device, pipeLayout, nullptr);
    vk_.DestroyDescriptorSetLayout(device, dsLayout, nullptr);
    if (compositeRenderPass != VK_NULL_HANDLE) { vk_.DestroyRenderPass(device, compositeRenderPass, nullptr); compositeRenderPass = VK_NULL_HANDLE; }
    if (cursorOverlayRenderPass != VK_NULL_HANDLE) { vk_.DestroyRenderPass(device, cursorOverlayRenderPass, nullptr); cursorOverlayRenderPass = VK_NULL_HANDLE; }
    for (uint32_t i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        vk_.DestroySemaphore(device, renderDoneSems[i], nullptr);
        vk_.DestroySemaphore(device, imgAvailSems[i], nullptr);
        vk_.DestroyFence(device, inFlightFences[i], nullptr);
    }
    for (size_t i = MAX_FRAMES_IN_FLIGHT; i < renderDoneSems.size(); i++)
        vk_.DestroySemaphore(device, renderDoneSems[i], nullptr);
    for (size_t i = MAX_FRAMES_IN_FLIGHT; i < imgAvailSems.size(); i++)
        vk_.DestroySemaphore(device, imgAvailSems[i], nullptr);
    lsfgEngine_.reset();
    destroyFgQueryPool();
    vk_.DestroyCommandPool(device, cmdPool, nullptr);
    vk_.DestroyRenderPass(device, renderPass, nullptr);
    vk_.DestroyDevice(device, nullptr);
    vk_.DestroySurfaceKHR(instance, surface, nullptr);
    vk_.DestroyInstance(instance, nullptr);
    if (adrenotoolsHandle) { dlclose(adrenotoolsHandle); adrenotoolsHandle = nullptr; }
}

void VulkanRendererContext::loadInstanceDispatch() {
    auto i = [&](const char* name) { return gipa ? gipa(instance, name) : nullptr; };
#define LOAD_I2(fn) vk_.fn = (PFN_vk##fn)i("vk"#fn)
    LOAD_I2(DestroyInstance);
    LOAD_I2(EnumeratePhysicalDevices);
    LOAD_I2(GetPhysicalDeviceProperties);
    LOAD_I2(GetPhysicalDeviceMemoryProperties);
    LOAD_I2(GetPhysicalDeviceSurfaceCapabilitiesKHR);
    LOAD_I2(GetPhysicalDeviceSurfaceFormatsKHR);
    LOAD_I2(GetPhysicalDeviceSurfacePresentModesKHR);
    LOAD_I2(GetPhysicalDeviceQueueFamilyProperties);
    LOAD_I2(GetPhysicalDeviceSurfaceSupportKHR);
    LOAD_I2(GetPhysicalDeviceFormatProperties);
    LOAD_I2(GetPhysicalDeviceFeatures2);
    LOAD_I2(CreateDevice);
    LOAD_I2(DestroySurfaceKHR);
    LOAD_I2(CreateAndroidSurfaceKHR);
    LOAD_I2(GetDeviceProcAddr);
}

void VulkanRendererContext::loadDeviceDispatch() {
    auto d = [&](const char* name) -> PFN_vkVoidFunction {
        return vk_.GetDeviceProcAddr ? vk_.GetDeviceProcAddr(device, name) : nullptr;
    };
#define LOAD_D2(fn) vk_.fn = (PFN_vk##fn)d("vk"#fn)
    LOAD_D2(DestroyDevice);
    LOAD_D2(GetDeviceQueue);
    LOAD_D2(DeviceWaitIdle);
    LOAD_D2(QueueWaitIdle);
    LOAD_D2(CreateSwapchainKHR);
    LOAD_D2(DestroySwapchainKHR);
    LOAD_D2(GetSwapchainImagesKHR);
    LOAD_D2(AcquireNextImageKHR);
    LOAD_D2(QueuePresentKHR);
    LOAD_D2(QueueSubmit);
    LOAD_D2(CreateQueryPool);
    LOAD_D2(DestroyQueryPool);
    LOAD_D2(CmdResetQueryPool);
    LOAD_D2(CmdWriteTimestamp);
    LOAD_D2(GetQueryPoolResults);
    LOAD_D2(CreateRenderPass);
    LOAD_D2(DestroyRenderPass);
    LOAD_D2(CreateFramebuffer);
    LOAD_D2(DestroyFramebuffer);
    LOAD_D2(CreateImageView);
    LOAD_D2(DestroyImageView);
    LOAD_D2(CreateImage);
    LOAD_D2(DestroyImage);
    LOAD_D2(CreateBuffer);
    LOAD_D2(DestroyBuffer);
    LOAD_D2(AllocateMemory);
    LOAD_D2(FreeMemory);
    LOAD_D2(MapMemory);
    LOAD_D2(FlushMappedMemoryRanges);
    LOAD_D2(BindBufferMemory);
    LOAD_D2(BindImageMemory);
    LOAD_D2(GetBufferMemoryRequirements);
    LOAD_D2(GetImageMemoryRequirements);
    LOAD_D2(CreateDescriptorSetLayout);
    LOAD_D2(DestroyDescriptorSetLayout);
    LOAD_D2(CreateDescriptorPool);
    LOAD_D2(DestroyDescriptorPool);
    LOAD_D2(AllocateDescriptorSets);
    LOAD_D2(FreeDescriptorSets);
    LOAD_D2(UpdateDescriptorSets);
    LOAD_D2(CreatePipelineLayout);
    LOAD_D2(DestroyPipelineLayout);
    LOAD_D2(CreateShaderModule);
    LOAD_D2(DestroyShaderModule);
    LOAD_D2(CreateGraphicsPipelines);
    LOAD_D2(DestroyPipeline);
    LOAD_D2(CreateCommandPool);
    LOAD_D2(DestroyCommandPool);
    LOAD_D2(AllocateCommandBuffers);
    LOAD_D2(FreeCommandBuffers);
    LOAD_D2(BeginCommandBuffer);
    LOAD_D2(EndCommandBuffer);
    LOAD_D2(ResetCommandBuffer);
    LOAD_D2(CmdBeginRenderPass);
    LOAD_D2(CmdEndRenderPass);
    LOAD_D2(CmdBindPipeline);
    LOAD_D2(CmdBindDescriptorSets);
    LOAD_D2(CmdDraw);
    LOAD_D2(CmdPushConstants);
    LOAD_D2(CmdSetViewport);
    LOAD_D2(CmdSetScissor);
    LOAD_D2(CmdPipelineBarrier);
    LOAD_D2(CmdCopyImage);
    LOAD_D2(CmdBlitImage);
    LOAD_D2(CmdCopyBufferToImage);
    // Compute: the native LSFG chain is 25 compute dispatches.
    LOAD_D2(CmdDispatch);
    LOAD_D2(CreateComputePipelines);
    LOAD_D2(UnmapMemory);
    LOAD_D2(CreateSampler);
    LOAD_D2(DestroySampler);
    LOAD_D2(CreateSemaphore);
    LOAD_D2(DestroySemaphore);
    LOAD_D2(CreateFence);
    LOAD_D2(DestroyFence);
    LOAD_D2(WaitForFences);
    LOAD_D2(ResetFences);
    LOAD_D2(GetFenceStatus);

    vk_.GetAndroidHardwareBufferPropertiesANDROID =
        (PFN_vkGetAndroidHardwareBufferPropertiesANDROID)d("vkGetAndroidHardwareBufferPropertiesANDROID");
}

void VulkanRendererContext::createInstance() {
    RLOG("createInstance: adrenotoolsHandle=%p (custom driver %s)",
        adrenotoolsHandle, adrenotoolsHandle?"ACTIVE":"NOT SET - using stock driver");

    if (adrenotoolsHandle) {
        gipa = (PFN_vkGetInstanceProcAddr)dlsym(adrenotoolsHandle, "vkGetInstanceProcAddr");
    }
    if (!gipa) {
        {
            const char *jpeg_candidates[] = {
                "/system/lib64/libjpeg.so",
                "/system_ext/lib64/libjpeg.so",
                "libjpeg.so",
                NULL,
            };
            for (int i = 0; jpeg_candidates[i]; i++) {
                if (dlopen(jpeg_candidates[i], RTLD_GLOBAL | RTLD_NOW)) break;
            }
            const char *crypto_candidates[] = {
                "libcrypto.so",
                NULL,
            };
            for (int i = 0; crypto_candidates[i]; i++) {
                if (dlopen(crypto_candidates[i], RTLD_GLOBAL | RTLD_NOW)) break;
            }
        }
        void* loaderLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_GLOBAL);
        if (loaderLib)
            gipa = (PFN_vkGetInstanceProcAddr)dlsym(loaderLib, "vkGetInstanceProcAddr");
    }

    vk_.CreateInstance = (PFN_vkCreateInstance)gipa(nullptr, "vkCreateInstance");
    VkApplicationInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO;
    ai.pApplicationName="Winlator"; ai.apiVersion=VK_API_VERSION_1_3;
    const char* ext[]={"VK_KHR_surface","VK_KHR_android_surface"};
    VkInstanceCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ci.pApplicationInfo=&ai; ci.enabledExtensionCount=2; ci.ppEnabledExtensionNames=ext;
    if (vk_.CreateInstance(&ci,nullptr,&instance)!=VK_SUCCESS) throw std::runtime_error("instance");

    loadInstanceDispatch();
}

void VulkanRendererContext::createSurface() {
    VkAndroidSurfaceCreateInfoKHR ci{}; ci.sType=VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    ci.window=window;
    if (vk_.CreateAndroidSurfaceKHR(instance,&ci,nullptr,&surface)!=VK_SUCCESS) throw std::runtime_error("surface");
}

void VulkanRendererContext::pickPhysicalDevice() {
    uint32_t n=0; vk_.EnumeratePhysicalDevices(instance,&n,nullptr);
    std::vector<VkPhysicalDevice> devs(n); vk_.EnumeratePhysicalDevices(instance,&n,devs.data());
    physicalDevice = VK_NULL_HANDLE;
    graphicsQueueFamilyIndex = 0;
    for (auto d : devs) {
        uint32_t qCount = 0;
        vk_.GetPhysicalDeviceQueueFamilyProperties(d, &qCount, nullptr);
        std::vector<VkQueueFamilyProperties> qProps(qCount);
        vk_.GetPhysicalDeviceQueueFamilyProperties(d, &qCount, qProps.data());
        for (uint32_t i = 0; i < qCount; i++) {
            VkBool32 present = VK_FALSE;
            vk_.GetPhysicalDeviceSurfaceSupportKHR(d, i, surface, &present);
            if ((qProps[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) {
                physicalDevice = d;
                graphicsQueueFamilyIndex = i;
                fgTimestampsOk_ = qProps[i].timestampValidBits > 0;
                return;
            }
        }
    }
    if (n > 0) physicalDevice = devs[0];
}

void VulkanRendererContext::createLogicalDevice() {
    float p=1.f;
    VkDeviceQueueCreateInfo qi{}; qi.sType=VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    qi.queueFamilyIndex=graphicsQueueFamilyIndex; qi.queueCount=1; qi.pQueuePriorities=&p;

    PFN_vkEnumerateDeviceExtensionProperties enumDevExts =
        (PFN_vkEnumerateDeviceExtensionProperties)gipa(instance, "vkEnumerateDeviceExtensionProperties");
    { uint32_t n=0; if(enumDevExts) enumDevExts(physicalDevice,nullptr,&n,nullptr);
      std::vector<VkExtensionProperties> av(n);
      if(enumDevExts) enumDevExts(physicalDevice,nullptr,&n,av.data());
      for (auto& e:av) {
          if (strcmp(e.extensionName,"VK_EXT_filter_cubic")==0
           || strcmp(e.extensionName,"VK_IMG_filter_cubic")==0) cubicSupported=true;
      } }
    std::vector<const char*> extList = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME,
        VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME
    };
    if (cubicSupported) extList.push_back("VK_EXT_filter_cubic");
    VkDeviceCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    ci.pQueueCreateInfos=&qi; ci.queueCreateInfoCount=1;
    ci.enabledExtensionCount=(uint32_t)extList.size(); ci.ppEnabledExtensionNames=extList.data();
    // --- Native LSFG: enable the three features its shaders need. Gated:
    // on a device failing any gate this block is inert and vkCreateDevice is
    // called exactly as before (with retry-without on rejection).
    lsfgCaps_ = lsfg::Caps{};
    lsfgCaps_.features = lsfg::queryFeatures(vk_, physicalDevice);

    VkPhysicalDeviceVulkan12Features lsfgV12{};
    VkPhysicalDeviceFeatures2        lsfgF2{};
    if (lsfgCaps_.features.deviceGatesPass()) {
        lsfgV12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
        lsfgV12.vulkanMemoryModel = VK_TRUE;
        lsfgV12.vulkanMemoryModelDeviceScope =
            lsfgCaps_.features.vulkanMemoryModelDeviceScope ? VK_TRUE : VK_FALSE;

        lsfgF2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
        lsfgF2.pNext = &lsfgV12;
        lsfgF2.features.shaderStorageImageWriteWithoutFormat = VK_TRUE;
        lsfgF2.features.shaderStorageImageExtendedFormats    = VK_TRUE;

        ci.pNext = &lsfgF2;
        lsfgCaps_.featuresEnabled = true;
    }

    if (vk_.CreateDevice(physicalDevice,&ci,nullptr,&device)!=VK_SUCCESS) {
        // A driver that rejects the feature chain must not cost the whole
        // renderer: retry once without it.
        if (lsfgCaps_.featuresEnabled) {
            RLOG_E("createLogicalDevice: CreateDevice failed WITH LSFG features; retrying without");
            ci.pNext = nullptr;
            lsfgCaps_.featuresEnabled = false;
            if (vk_.CreateDevice(physicalDevice,&ci,nullptr,&device)!=VK_SUCCESS) throw std::runtime_error("device");
        } else {
            throw std::runtime_error("device");
        }
    }
    vk_.GetDeviceProcAddr = (PFN_vkGetDeviceProcAddr)gipa(instance, "vkGetDeviceProcAddr");
    loadDeviceDispatch();
    vk_.GetDeviceQueue(device,graphicsQueueFamilyIndex,0,&graphicsQueue);

    vk_.GetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);

    VkPhysicalDeviceProperties props{};
    vk_.GetPhysicalDeviceProperties(physicalDevice, &props);
    maxAnisotropy = props.limits.maxSamplerAnisotropy;
    fgTimestampPeriodNs_ = props.limits.timestampPeriod;
}

void VulkanRendererContext::createSwapchain() {
    VkSurfaceCapabilitiesKHR caps;
    vk_.GetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice,surface,&caps);
    swapchainExt=(caps.currentExtent.width!=0xFFFFFFFF)?caps.currentExtent:VkExtent2D{(uint32_t)surfaceWidth,(uint32_t)surfaceHeight};
    uint32_t fmtN=0; vk_.GetPhysicalDeviceSurfaceFormatsKHR(physicalDevice,surface,&fmtN,nullptr);
    std::vector<VkSurfaceFormatKHR> fmts(fmtN); vk_.GetPhysicalDeviceSurfaceFormatsKHR(physicalDevice,surface,&fmtN,fmts.data());
    swapchainFmt = VK_FORMAT_R8G8B8A8_UNORM;

    // Native LSFG: probe storage support on the swapchain format for the
    // `generate` compute writes; settle the capability verdict for the UI.
    lsfgCaps_.probedFormat = swapchainFmt;
    lsfgCaps_.storageOnSwapchainFormat =
        lsfg::probeStorageFormat(vk_, physicalDevice, swapchainFmt);
    lsfg::explain(lsfgCaps_);
    RLOG("lsfg-native: %s (features enabled=%d, storage-on-fmt=%d)",
         lsfgCaps_.reason, (int)lsfgCaps_.featuresEnabled,
         (int)lsfgCaps_.storageOnSwapchainFormat);

    uint32_t imgCount=caps.minImageCount+1;
    // Frame gen presents several images per source frame, so the queue needs
    // depth to keep them in flight without stalling on AcquireNextImageKHR.
    // Only while armed: extra images cost memory.
    if (fgArmed_.load(std::memory_order_relaxed)) {
        const uint32_t want = std::min<uint32_t>(caps.minImageCount + kMaxPresentsPerFrame, 8u);
        if (want > imgCount) imgCount = want;
    }
    if (caps.maxImageCount>0&&imgCount>caps.maxImageCount) imgCount=caps.maxImageCount;

    uint32_t pmCount=0;
    vk_.GetPhysicalDeviceSurfacePresentModesKHR(physicalDevice,surface,&pmCount,nullptr);
    availablePresentModes.resize(pmCount);
    vk_.GetPhysicalDeviceSurfacePresentModesKHR(physicalDevice,surface,&pmCount,availablePresentModes.data());
    VkPresentModeKHR presentMode=VK_PRESENT_MODE_FIFO_KHR;
    for (auto pm:availablePresentModes) if(pm==requestedPresentMode){presentMode=pm;break;}
    if(verboseLog){
        std::string pmList;
        for(auto pm:availablePresentModes) pmList+=std::to_string((int)pm)+" ";
        RLOG("createSwapchain: %dx%d fmt=%d supportedPresentModes=[%s] chosen=%d req=%d",
            swapchainExt.width,swapchainExt.height,(int)swapchainFmt,pmList.c_str(),(int)presentMode,(int)requestedPresentMode);
    }

    VkSurfaceTransformFlagBitsKHR pre=
        (caps.supportedTransforms&VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)?
        VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR:caps.currentTransform;

    VkCompositeAlphaFlagBitsKHR compositeAlpha=
        (caps.supportedCompositeAlpha&VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)?
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR:VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;

    VkSwapchainKHR oldSwapchain=swapchain;
    VkSwapchainCreateInfoKHR ci{}; ci.sType=VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    ci.surface=surface; ci.minImageCount=imgCount; ci.imageFormat=swapchainFmt;
    ci.imageColorSpace=VK_COLOR_SPACE_SRGB_NONLINEAR_KHR; ci.imageExtent=swapchainExt;
    ci.imageArrayLayers=1; ci.imageUsage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    // The composite path copies INTO the swapchain image (TRANSFER_DST).
    // Only while frame gen is armed; toggling armed state recreates this.
    if (fgArmed_.load(std::memory_order_relaxed) &&
        (caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT)) {
        ci.imageUsage |= VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        swapchainTransferDst = true;
    } else {
        swapchainTransferDst = false;
    }
    ci.imageSharingMode=VK_SHARING_MODE_EXCLUSIVE; ci.preTransform=pre;
    ci.compositeAlpha=compositeAlpha; ci.presentMode=presentMode; ci.clipped=VK_TRUE;
    ci.oldSwapchain=oldSwapchain;
    if (vk_.CreateSwapchainKHR(device,&ci,nullptr,&swapchain)!=VK_SUCCESS) throw std::runtime_error("swapchain");
    RLOG("swapchain created: %dx%d format=%d presentMode=%d compositeAlpha=%d imgCount=%u",
        swapchainExt.width,swapchainExt.height,(int)swapchainFmt,(int)presentMode,(int)compositeAlpha,imgCount);
    if (oldSwapchain!=VK_NULL_HANDLE) vk_.DestroySwapchainKHR(device,oldSwapchain,nullptr);
    vk_.GetSwapchainImagesKHR(device,swapchain,&imgCount,nullptr);
    swapchainImages.resize(imgCount); vk_.GetSwapchainImagesKHR(device,swapchain,&imgCount,swapchainImages.data());
    swapchainViews.resize(imgCount);
    for (size_t i=0;i<imgCount;i++) {
        VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        vi.image=swapchainImages[i]; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=swapchainFmt;
        vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        VkComponentMapping mapping{};
        mapping.r = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.g = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.b = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.a = VK_COMPONENT_SWIZZLE_IDENTITY;
        vi.components = mapping;
        if (vk_.CreateImageView(device,&vi,nullptr,&swapchainViews[i])!=VK_SUCCESS) throw std::runtime_error("imgview");
    }
}

void VulkanRendererContext::createRenderPass() {
    VkAttachmentDescription att{}; att.format=swapchainFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR; att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE; att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED; att.finalLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;
    VkSubpassDependency dep{}; dep.srcSubpass=VK_SUBPASS_EXTERNAL; dep.dstSubpass=0;
    dep.srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT; dep.srcAccessMask=0;
    dep.dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=1; ci.pDependencies=&dep;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&renderPass)!=VK_SUCCESS) throw std::runtime_error("renderpass");
}

void VulkanRendererContext::createDSLayout() {
    VkDescriptorSetLayoutBinding b{}; b.binding=0; b.descriptorCount=1;
    b.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; b.stageFlags=VK_SHADER_STAGE_FRAGMENT_BIT;
    VkDescriptorSetLayoutCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    ci.bindingCount=1; ci.pBindings=&b;
    if (vk_.CreateDescriptorSetLayout(device,&ci,nullptr,&dsLayout)!=VK_SUCCESS) throw std::runtime_error("dslayout");
}
 

VkShaderModule VulkanRendererContext::makeShader(const uint32_t* code, size_t sz) {
    VkShaderModuleCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    ci.codeSize=sz; ci.pCode=code; VkShaderModule m;
    if (vk_.CreateShaderModule(device,&ci,nullptr,&m)!=VK_SUCCESS) throw std::runtime_error("shader");
    return m;
}

void VulkanRendererContext::createPipeline(bool blend, VkPipeline& out) {
    if (pipeLayout==VK_NULL_HANDLE) {
        VkPushConstantRange pc{}; pc.stageFlags=VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT;
        pc.size=sizeof(WindowPushConstants);
        VkPipelineLayoutCreateInfo li{}; li.sType=VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        li.setLayoutCount=1; li.pSetLayouts=&dsLayout; li.pushConstantRangeCount=1; li.pPushConstantRanges=&pc;
        if (vk_.CreatePipelineLayout(device,&li,nullptr,&pipeLayout)!=VK_SUCCESS) throw std::runtime_error("pipelayout");
    }
    auto vert=makeShader(window_vert_code,sizeof(window_vert_code));
    auto frag=makeShader(window_frag_code,sizeof(window_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO; rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=blend?VK_TRUE:VK_FALSE;
    if (blend){ba.srcColorBlendFactor=VK_BLEND_FACTOR_SRC_ALPHA;ba.dstColorBlendFactor=VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;ba.colorBlendOp=VK_BLEND_OP_ADD;ba.srcAlphaBlendFactor=VK_BLEND_FACTOR_ONE;ba.dstAlphaBlendFactor=VK_BLEND_FACTOR_ZERO;ba.alphaBlendOp=VK_BLEND_OP_ADD;}
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=pipeLayout; pi.renderPass=renderPass; pi.subpass=0;
    if (vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&out)!=VK_SUCCESS) throw std::runtime_error("pipeline");
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
}


void VulkanRendererContext::createCursorPipeline() {  }
void VulkanRendererContext::createFramebuffers() {
    swapchainFBs.resize(swapchainViews.size());
    for (size_t i=0;i<swapchainViews.size();i++) {
        VkImageView att[]={swapchainViews[i]};
        VkFramebufferCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        fi.renderPass=renderPass; fi.attachmentCount=1; fi.pAttachments=att;
        fi.width=swapchainExt.width; fi.height=swapchainExt.height; fi.layers=1;
        if (vk_.CreateFramebuffer(device,&fi,nullptr,&swapchainFBs[i])!=VK_SUCCESS) throw std::runtime_error("fb");
    }
}

void VulkanRendererContext::createCmdPool() {
    VkCommandPoolCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    ci.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT; ci.queueFamilyIndex=graphicsQueueFamilyIndex;
    if (vk_.CreateCommandPool(device,&ci,nullptr,&cmdPool)!=VK_SUCCESS) throw std::runtime_error("cmdpool");
}

void VulkanRendererContext::createSampler() {
    bool useCubic = (filterMode == 2) && cubicSupported;
    VkFilter filter = (filterMode == 1) ? VK_FILTER_NEAREST
                    : (useCubic)         ? VK_FILTER_CUBIC_EXT
                    :                      VK_FILTER_LINEAR;
    RLOG("createSampler: filter=%s (filterMode=%d, cubicSupported=%d)",
        filterMode==2?(cubicSupported?"CUBIC":"LINEAR_FALLBACK"):filterMode==1?"NEAREST":"LINEAR",
        filterMode, (int)cubicSupported);
    VkSamplerCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    ci.magFilter=filter; ci.minFilter=filter;
    ci.addressModeU=ci.addressModeV=ci.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
    ci.minLod=0.f; ci.maxLod=0.f;
    if (vk_.CreateSampler(device,&ci,nullptr,&sampler)!=VK_SUCCESS) throw std::runtime_error("sampler");
}

void VulkanRendererContext::createWinTexPool() {

    VkDescriptorPoolSize ps{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 160};
    VkDescriptorPoolCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    ci.flags=VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
    ci.poolSizeCount=1; ci.pPoolSizes=&ps; ci.maxSets=160;
    if (vk_.CreateDescriptorPool(device,&ci,nullptr,&winTexPool)!=VK_SUCCESS) throw std::runtime_error("wintexpool");
}


void VulkanRendererContext::createCursorDS() {
    VkDescriptorSetAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    ai.descriptorPool=winTexPool; ai.descriptorSetCount=1; ai.pSetLayouts=&dsLayout;
    vk_.AllocateDescriptorSets(device,&ai,&cursorDS);
}

void VulkanRendererContext::createCmdBufs() {
    cmdBufs.resize(MAX_FRAMES_IN_FLIGHT * kMaxPresentsPerFrame);
    VkCommandBufferAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    ai.commandPool=cmdPool; ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; ai.commandBufferCount=(uint32_t)cmdBufs.size();
    if (vk_.AllocateCommandBuffers(device,&ai,cmdBufs.data())!=VK_SUCCESS) throw std::runtime_error("cmdbuf");
}

void VulkanRendererContext::createSyncObjects() {
    // One semaphore PAIR per pending present (frame slot x presents), but one
    // fence per frame slot: all presents of a source frame share its completion.
    const uint32_t semCount = MAX_FRAMES_IN_FLIGHT * kMaxPresentsPerFrame;
    imgAvailSems.resize(semCount); renderDoneSems.resize(semCount); inFlightFences.resize(MAX_FRAMES_IN_FLIGHT);
    VkSemaphoreCreateInfo si{}; si.sType=VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; fi.flags=VK_FENCE_CREATE_SIGNALED_BIT;
    for (uint32_t i=0;i<semCount;i++) {
        if (vk_.CreateSemaphore(device,&si,nullptr,&imgAvailSems[i])!=VK_SUCCESS||
            vk_.CreateSemaphore(device,&si,nullptr,&renderDoneSems[i])!=VK_SUCCESS) throw std::runtime_error("sync");
    }
    for (uint32_t i=0;i<MAX_FRAMES_IN_FLIGHT;i++) {
        if (vk_.CreateFence(device,&fi,nullptr,&inFlightFences[i])!=VK_SUCCESS) throw std::runtime_error("sync");
    }
}

void VulkanRendererContext::cleanupSwapchain() {
    // The composite ring is sized to the swapchain extent, so it goes with it.
    destroyCompositeTargets();
    compositeArmed = false;
    for (auto fb:swapchainFBs) vk_.DestroyFramebuffer(device,fb,nullptr); swapchainFBs.clear();
    for (auto iv:swapchainViews) vk_.DestroyImageView(device,iv,nullptr); swapchainViews.clear();
    if (!cmdBufs.empty()){vk_.FreeCommandBuffers(device,cmdPool,(uint32_t)cmdBufs.size(),cmdBufs.data());cmdBufs.clear();}
    if (swapchain!=VK_NULL_HANDLE) { vk_.DestroySwapchainKHR(device,swapchain,nullptr); swapchain=VK_NULL_HANDLE; }
}

uint32_t VulkanRendererContext::findMemType(uint32_t filter, VkMemoryPropertyFlags props) {
    for (uint32_t i=0;i<memProperties.memoryTypeCount;i++)
        if ((filter&(1u<<i))&&(memProperties.memoryTypes[i].propertyFlags&props)==props) return i;
    throw std::runtime_error("memtype");
}

void VulkanRendererContext::createBuffer(VkDeviceSize sz, VkBufferUsageFlags usage,
    VkMemoryPropertyFlags props, VkBuffer& buf, VkDeviceMemory& mem)
{
    VkBufferCreateInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO; bi.size=sz; bi.usage=usage; bi.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateBuffer(device,&bi,nullptr,&buf)!=VK_SUCCESS) throw std::runtime_error("buffer");
    VkMemoryRequirements req; vk_.GetBufferMemoryRequirements(device,buf,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,props);
    if (vk_.AllocateMemory(device,&ai,nullptr,&mem)!=VK_SUCCESS) throw std::runtime_error("bufmem");
    vk_.BindBufferMemory(device,buf,mem,0);
}

VkCommandBuffer VulkanRendererContext::beginOneTime() {
    VkCommandBufferAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; ai.commandPool=cmdPool; ai.commandBufferCount=1;
    VkCommandBuffer cb; vk_.AllocateCommandBuffers(device,&ai,&cb);
    VkCommandBufferBeginInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO; bi.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vk_.BeginCommandBuffer(cb,&bi); return cb;
}

void VulkanRendererContext::endOneTime(VkCommandBuffer cb) {
    vk_.EndCommandBuffer(cb);
    VkSubmitInfo si{}; si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO; si.commandBufferCount=1; si.pCommandBuffers=&cb;
    VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; VkFence fence;
    vk_.CreateFence(device,&fi,nullptr,&fence);
    vk_.QueueSubmit(graphicsQueue,1,&si,fence); vk_.WaitForFences(device,1,&fence,VK_TRUE,UINT64_MAX);
    vk_.DestroyFence(device,fence,nullptr); vk_.FreeCommandBuffers(device,cmdPool,1,&cb);
}

void VulkanRendererContext::transition(VkCommandBuffer cb, VkImage img,
    VkImageLayout ol, VkImageLayout nl, VkAccessFlags sa, VkAccessFlags da,
    VkPipelineStageFlags ss, VkPipelineStageFlags ds)
{
    VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    b.oldLayout=ol; b.newLayout=nl; b.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    b.image=img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}; b.srcAccessMask=sa; b.dstAccessMask=da;
    vk_.CmdPipelineBarrier(cb,ss,ds,0,0,nullptr,0,nullptr,1,&b);
}

bool VulkanRendererContext::createWinTexResources(WinTex& wt, int w, int h) {

    VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.extent={(uint32_t)w,(uint32_t)h,1}; ii.mipLevels=1; ii.arrayLayers=1; ii.format=VK_FORMAT_B8G8R8A8_UNORM;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    ii.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT; ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateImage(device,&ii,nullptr,&wt.img)!=VK_SUCCESS) return false;
    VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,wt.img,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (vk_.AllocateMemory(device,&ai,nullptr,&wt.mem)!=VK_SUCCESS){vk_.DestroyImage(device,wt.img,nullptr);wt.img=VK_NULL_HANDLE;return false;}
    vk_.BindImageMemory(device,wt.img,wt.mem,0);
    VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO; vi.image=wt.img; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=VK_FORMAT_B8G8R8A8_UNORM; vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    vi.components={swapRB?VK_COMPONENT_SWIZZLE_B:VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,swapRB?VK_COMPONENT_SWIZZLE_R:VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
    if (vk_.CreateImageView(device,&vi,nullptr,&wt.view)!=VK_SUCCESS){destroyWinTex(wt);return false;}
    VkDescriptorSetAllocateInfo dsai{}; dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO; dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
    if (vk_.AllocateDescriptorSets(device,&dsai,&wt.ds)!=VK_SUCCESS){destroyWinTex(wt);return false;}
    VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; dii.imageView=wt.view; dii.sampler=sampler;
    VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; wr.dstSet=wt.ds; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    VkDeviceSize stgSz=(VkDeviceSize)w*h*4;
    createBuffer(stgSz,VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,wt.stg,wt.stgMem);
    vk_.MapMemory(device,wt.stgMem,0,stgSz,0,&wt.mapped);
    wt.cap=stgSz; wt.w=w; wt.h=h; wt.needsTransition=true;
    return true;
}

bool VulkanRendererContext::importAHBToWinTex(WinTex& wt, AHardwareBuffer* ahb) {
    if (!vk_.GetAndroidHardwareBufferPropertiesANDROID)
        return false;

    VkAndroidHardwareBufferFormatPropertiesANDROID fmtP{};
    fmtP.sType=VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID;
    VkAndroidHardwareBufferPropertiesANDROID props{};
    props.sType=VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID;
    props.pNext=&fmtP;
    if (vk_.GetAndroidHardwareBufferPropertiesANDROID(device,ahb,&props)!=VK_SUCCESS)
        return false;

    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(ahb,&desc);

    VkExternalFormatANDROID ef{};
    ef.sType=VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID;
    ef.externalFormat=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;

    VkExternalMemoryImageCreateInfo emi{};
    emi.sType=VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
    emi.handleTypes=VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
    ef.pNext=const_cast<void*>(emi.pNext);
    emi.pNext=&ef;

    VkImageCreateInfo ii{};
    ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ii.pNext=&emi; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.format=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;
    ii.extent={desc.width,desc.height,1};
    ii.mipLevels=1; ii.arrayLayers=1; ii.samples=VK_SAMPLE_COUNT_1_BIT;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.usage=VK_IMAGE_USAGE_SAMPLED_BIT;
    ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    if (vk_.CreateImage(device,&ii,nullptr,&wt.img)!=VK_SUCCESS)
        return false;

    VkImportAndroidHardwareBufferInfoANDROID imp{};
    imp.sType=VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    imp.buffer=ahb;

    VkMemoryDedicatedAllocateInfo ded{};
    ded.sType=VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    ded.pNext=&imp; ded.image=wt.img;

    VkMemoryAllocateInfo mai{};
    mai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.pNext=&ded; mai.allocationSize=props.allocationSize;
    mai.memoryTypeIndex=findMemType(props.memoryTypeBits,0);
    if (vk_.AllocateMemory(device,&mai,nullptr,&wt.mem)!=VK_SUCCESS){
        vk_.DestroyImage(device,wt.img,nullptr);
        wt.img=VK_NULL_HANDLE;
        return false;
    }
    vk_.BindImageMemory(device,wt.img,wt.mem,0);

    VkExternalFormatANDROID vef{};
    vef.sType=VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID;
    vef.externalFormat=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;

    VkImageViewCreateInfo vi{};
    vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    vi.pNext=&vef; vi.image=wt.img; vi.viewType=VK_IMAGE_VIEW_TYPE_2D;
    vi.format=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;
    vi.components={VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,
                   VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
    vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    if (vk_.CreateImageView(device,&vi,nullptr,&wt.view)!=VK_SUCCESS){
        destroyWinTex(wt);
        return false;
    }

    VkDescriptorSetAllocateInfo dsai{};
    dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
    VkResult dsRes=vk_.AllocateDescriptorSets(device,&dsai,&wt.ds);
    if (dsRes==VK_ERROR_OUT_OF_POOL_MEMORY){
        RLOG_E("importAHBToWinTex: descriptor pool exhausted for AHB texture");
        destroyWinTex(wt);
        return false;
    }
    if (dsRes!=VK_SUCCESS){ destroyWinTex(wt); return false; }

    VkDescriptorImageInfo dii{};
    dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    dii.imageView=wt.view; dii.sampler=sampler;

    VkWriteDescriptorSet wr{};
    wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    wr.dstSet=wt.ds; wr.dstBinding=0;
    wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);

    wt.needsTransition=true;
    wt.isAHB=true;
    wt.w=(int)desc.width;
    wt.h=(int)desc.height;
    return true;
}

void VulkanRendererContext::destroyWinTex(WinTex& wt) {
    if (wt.isAHB) {


        wt = {};
        return;
    }
    if (wt.img!=VK_NULL_HANDLE || wt.stg!=VK_NULL_HANDLE) {
        
        WinTex deferred = wt;
        deferred.isAHB = false;
        deleteQueue.push_back(deferred);
    }
    wt={};
}

void VulkanRendererContext::ensureCursorTex(short w, short h) {
    if (cursorImg!=VK_NULL_HANDLE && cursorTexW==w && cursorTexH==h) return;
    cleanupCursorTex();
    VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.extent={(uint32_t)w,(uint32_t)h,1}; ii.mipLevels=1; ii.arrayLayers=1; ii.format=VK_FORMAT_B8G8R8A8_UNORM;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    ii.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT; ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    vk_.CreateImage(device,&ii,nullptr,&cursorImg);
    VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,cursorImg,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    vk_.AllocateMemory(device,&ai,nullptr,&cursorMem); vk_.BindImageMemory(device,cursorImg,cursorMem,0);
    VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO; vi.image=cursorImg; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=VK_FORMAT_B8G8R8A8_UNORM; vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    vk_.CreateImageView(device,&vi,nullptr,&cursorView);
    VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; dii.imageView=cursorView; dii.sampler=sampler;
    VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; wr.dstSet=cursorDS; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);

    cursorTexW=w; cursorTexH=h;
}

void VulkanRendererContext::cleanupCursorTex() {
    if (cursorView!=VK_NULL_HANDLE){vk_.DestroyImageView(device,cursorView,nullptr);cursorView=VK_NULL_HANDLE;}
    if (cursorImg!=VK_NULL_HANDLE){vk_.DestroyImage(device,cursorImg,nullptr);cursorImg=VK_NULL_HANDLE;}
    if (cursorMem!=VK_NULL_HANDLE){vk_.FreeMemory(device,cursorMem,nullptr);cursorMem=VK_NULL_HANDLE;}
    if (cursorStg!=VK_NULL_HANDLE){vk_.DestroyBuffer(device,cursorStg,nullptr);vk_.FreeMemory(device,cursorStgM,nullptr);cursorStg=VK_NULL_HANDLE;cursorStgP=nullptr;cursorStgC=0;}
    cursorTexW=0; cursorTexH=0;
}

void VulkanRendererContext::ensureCursorStaging(VkDeviceSize sz) {
    if (cursorStgC>=sz) return;
    if (cursorStg!=VK_NULL_HANDLE){vk_.DestroyBuffer(device,cursorStg,nullptr);vk_.FreeMemory(device,cursorStgM,nullptr);}
    createBuffer(sz,VK_BUFFER_USAGE_TRANSFER_SRC_BIT,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,cursorStg,cursorStgM);
    vk_.MapMemory(device,cursorStgM,0,sz,0,&cursorStgP); cursorStgC=sz;
}

void VulkanRendererContext::recordCmdBuf(VkCommandBuffer cb, uint32_t imgIdx,
    const std::vector<DrawEntry>& draws,
    std::vector<VkImageMemoryBarrier>& ahbTransitions,
    std::vector<VkImageMemoryBarrier>& preUpload,
    std::vector<VkImageMemoryBarrier>& postUpload,
    VkBuffer cursorUpload, bool hasCursorUpload,
    float ox, float oy, float sx, float sy, float cw, float ch,
    short ptrX, short ptrY, short curHotX, short curHotY,
    short curW, short curH, bool curVis)
{
    VkCommandBufferBeginInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    if (vk_.BeginCommandBuffer(cb,&bi)!=VK_SUCCESS) throw std::runtime_error("begin cb");







    ahbTransitions.clear(); preUpload.clear(); postUpload.clear();

    for (auto& d : draws) {
        if (d.img==VK_NULL_HANDLE) continue;
        if (d.isAHB && d.needsTransition) {
            VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            b.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            b.image=d.img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            b.srcAccessMask=0; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            ahbTransitions.push_back(b);
        } else if (!d.isAHB && (d.needsTransition || d.upload!=VK_NULL_HANDLE)) {
            VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            b.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; b.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            b.image=d.img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            b.srcAccessMask=0; b.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
            preUpload.push_back(b);
            b.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            postUpload.push_back(b);
        }
    }

    if (!ahbTransitions.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)ahbTransitions.size(), ahbTransitions.data());
    if (!preUpload.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)preUpload.size(), preUpload.data());


    for (auto& d : draws) {
        if (d.isAHB || d.upload==VK_NULL_HANDLE || d.img==VK_NULL_HANDLE) continue;
        VkBufferImageCopy r{}; r.bufferOffset=0; r.bufferRowLength=0; r.bufferImageHeight=0;
        r.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        r.imageExtent={(uint32_t)d.w,(uint32_t)d.h,1};
        vk_.CmdCopyBufferToImage(cb, d.upload, d.img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &r);
    }

    bool cursorDrawn = curVis && cursorImg!=VK_NULL_HANDLE && cursorDS!=VK_NULL_HANDLE;
    // Native LSFG: keep the cursor OUT of the composite (it would be warped
    // along the flow field) and draw it into every presented image instead.
    cursorOverlay_ = CursorOverlay{};
    if (cursorDrawnPerPresent()) {
        cursorOverlay_.draw = cursorDrawn;
        cursorOverlay_.ox = ox; cursorOverlay_.oy = oy;
        cursorOverlay_.sx = sx; cursorOverlay_.sy = sy;
        cursorOverlay_.cw = cw; cursorOverlay_.ch = ch;
        cursorOverlay_.ptrX = ptrX; cursorOverlay_.ptrY = ptrY;
        cursorOverlay_.hotX = curHotX; cursorOverlay_.hotY = curHotY;
        cursorOverlay_.w = curW; cursorOverlay_.h = curH;
        cursorDrawn = false;
    }
    bool hasCursorCopy = hasCursorUpload && cursorImg!=VK_NULL_HANDLE && cursorUpload!=VK_NULL_HANDLE;
    if (hasCursorCopy) {
        VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        b.oldLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        b.image=cursorImg; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        b.srcAccessMask=VK_ACCESS_SHADER_READ_BIT; b.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
            0, 0, nullptr, 0, nullptr, 1, &b);
        VkBufferImageCopy r{}; r.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        r.imageExtent={(uint32_t)curW,(uint32_t)curH,1};
        vk_.CmdCopyBufferToImage(cb, cursorUpload, cursorImg, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &r);
        b.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
        postUpload.push_back(b);
    }

    if (!postUpload.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)postUpload.size(), postUpload.data());

    bool useEffects = !activeEffects.empty() && effectReadBuf.img != VK_NULL_HANDLE && effectWriteBuf.img != VK_NULL_HANDLE;

    if (useEffects) {
        // === Effect path: render scene to offscreen, apply effects, copy to swapchain ===

        // Step 1: Render scene into effectReadBuf
        transition(cb, effectReadBuf.img,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);

        // Full-res: сцена в effect-буфер размера swapchain тем же NDC-пассом
        // (координаты относительные cw/ch — раскладка не меняется, растет детализация).
        // Буфер и вьюпорт всегда одного размера — "маленького экрана" нет.
        VkRenderPassBeginInfo rpiOff{}; rpiOff.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        rpiOff.renderPass = effectRenderPass; rpiOff.framebuffer = effectReadBuf.fb;
        rpiOff.renderArea = {{0,0}, {(uint32_t)effectW, (uint32_t)effectH}};
        VkClearValue clrOff = {{{0.f,0.f,0.f,1.f}}}; rpiOff.clearValueCount = 1; rpiOff.pClearValues = &clrOff;
        vk_.CmdBeginRenderPass(cb, &rpiOff, VK_SUBPASS_CONTENTS_INLINE);

        VkViewport vpOff{0, 0, (float)effectW, (float)effectH, 0, 1};
        vk_.CmdSetViewport(cb, 0, 1, &vpOff);
        VkRect2D scOff{{0,0}, {(uint32_t)effectW, (uint32_t)effectH}};
        vk_.CmdSetScissor(cb, 0, 1, &scOff);

        vk_.CmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        for (auto& d : draws) {
            if (d.ds == VK_NULL_HANDLE) continue;
            vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLayout, 0, 1, &d.ds, 0, nullptr);
            WindowPushConstants pc{};
            pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;
            pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
            pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;
            pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            pc.useTexAlpha = 0;
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
            vk_.CmdDraw(cb, 4, 1, 0, 0);
        }
        if (cursorDrawn) {
            vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLayout, 0, 1, &cursorDS, 0, nullptr);
            float cx=(float)std::max(0,(int)ptrX-curHotX), cy=(float)std::max(0,(int)ptrY-curHotY);
            WindowPushConstants cpc{};
            cpc.ndcX0=(ox+cx*sx)/cw*2.f-1.f; cpc.ndcY0=(oy+cy*sy)/ch*2.f-1.f;
            cpc.ndcX1=(ox+(cx+curW)*sx)/cw*2.f-1.f; cpc.ndcY1=(oy+(cy+curH)*sy)/ch*2.f-1.f;
            cpc.useTexAlpha = 1;
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(cpc), &cpc);
            vk_.CmdDraw(cb, 4, 1, 0, 0);
        }
        vk_.CmdEndRenderPass(cb);

        // Transition read buffer to shader read
        transition(cb, effectReadBuf.img,
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);

        // Step 2: Ping-pong effect passes
        for (size_t i = 0; i < activeEffects.size(); i++) {
            auto& effect = activeEffects[i];
            VkPipeline effPipe = effectPipelines[effect.type];
            if (effPipe == VK_NULL_HANDLE) continue;

            EffectOffscreen& src = (i % 2 == 0) ? effectReadBuf : effectWriteBuf;
            EffectOffscreen& dst = (i % 2 == 0) ? effectWriteBuf : effectReadBuf;

            // Update source descriptor
            {
                VkDescriptorImageInfo dii{}; dii.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                dii.imageView = src.view; dii.sampler = effectSampler;
                VkWriteDescriptorSet wr{}; wr.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
                wr.dstSet = src.ds; wr.dstBinding = 0; wr.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
                wr.descriptorCount = 1; wr.pImageInfo = &dii;
                vk_.UpdateDescriptorSets(device, 1, &wr, 0, nullptr);
            }

            transition(cb, dst.img,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);

            VkRenderPassBeginInfo rpiEff{}; rpiEff.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
            rpiEff.renderPass = effectRenderPass; rpiEff.framebuffer = dst.fb;
            // Full-res ping-pong: размер буфера == вьюпорт, шаг texel от него же.
            rpiEff.renderArea = {{0,0}, {(uint32_t)effectW, (uint32_t)effectH}};
            VkClearValue clrEff = {{{0.f,0.f,0.f,1.f}}}; rpiEff.clearValueCount = 1; rpiEff.pClearValues = &clrEff;
            vk_.CmdBeginRenderPass(cb, &rpiEff, VK_SUBPASS_CONTENTS_INLINE);

            VkViewport vpEff{0, 0, (float)effectW, (float)effectH, 0, 1};
            vk_.CmdSetViewport(cb, 0, 1, &vpEff);
            VkRect2D scEff{{0,0}, {(uint32_t)effectW, (uint32_t)effectH}};
            vk_.CmdSetScissor(cb, 0, 1, &scEff);

            vk_.CmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, effPipe);
            vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, effectPipeLayout, 0, 1, &src.ds, 0, nullptr);

            EffectPushConstants epc{};
            // texel-шаг от реального размера буфера (full-res) — как GL передает
            // разрешение экрана. Иначе FXAA/blur/sharpen мылят.
            epc.resolutionX = (float)effectW;
            epc.resolutionY = (float)effectH;
            memcpy(epc.params, effect.params, sizeof(effect.params));
            vk_.CmdPushConstants(cb, effectPipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(epc), &epc);
            vk_.CmdDraw(cb, 4, 1, 0, 0);
            vk_.CmdEndRenderPass(cb);

            transition(cb, dst.img,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        }

        // Step 3: Copy final effect output to swapchain — or, when frame gen
        // is armed, blit it into the composite ring target instead (same
        // swapchain size, so the blit scales exactly like the swapchain one).
        size_t lastIdx = activeEffects.size() - 1;
        EffectOffscreen& finalBuf = (lastIdx % 2 == 0) ? effectWriteBuf : effectReadBuf;

        if (compositeActive() && compositeIndex < compositeTargets.size()) {
            CompositeTarget& ct = compositeTargets[compositeIndex];
            // Composite targets start UNDEFINED; after the first blit they
            // rest in GENERAL (see below), matching the render-pass path.
            transition(cb, ct.img,
                ct.fresh ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_GENERAL,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                ct.fresh ? 0 : VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                ct.fresh ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT);

            transition(cb, finalBuf.img,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

            VkImageBlit blitRegion{};
            blitRegion.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
            blitRegion.srcOffsets[0] = {0, 0, 0};
            blitRegion.srcOffsets[1] = {effectW, effectH, 1};
            blitRegion.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
            blitRegion.dstOffsets[0] = {0, 0, 0};
            blitRegion.dstOffsets[1] = {(int32_t)compositeW, (int32_t)compositeH, 1};
            vk_.CmdBlitImage(cb,
                finalBuf.img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                ct.img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                1, &blitRegion, VK_FILTER_LINEAR);

            transition(cb, ct.img,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
            ct.fresh = false;

            transition(cb, finalBuf.img,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        } else {
        // Transition swapchain image to transfer dst
        transition(cb, swapchainImages[imgIdx],
            VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            0, VK_ACCESS_TRANSFER_WRITE_BIT,
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

        // Transition final effect output to transfer src
        transition(cb, finalBuf.img,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT,
            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

        // Full-res: размеры совпадают — точное копирование без фильтрации
        // (было: LINEAR-апскейл 720p, съедавший резкость всех эффектов).
        if (effectW == (int)swapchainExt.width && effectH == (int)swapchainExt.height) {
            VkImageCopy copyRegion{};
            copyRegion.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
            copyRegion.srcOffset = {0, 0, 0};
            copyRegion.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
            copyRegion.dstOffset = {0, 0, 0};
            copyRegion.extent = {swapchainExt.width, swapchainExt.height, 1};
            vk_.CmdCopyImage(cb,
                finalBuf.img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                swapchainImages[imgIdx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                1, &copyRegion);
        } else {
            VkImageBlit blitRegion{};
            blitRegion.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
            blitRegion.srcOffsets[0] = {0, 0, 0};
            blitRegion.srcOffsets[1] = {effectW, effectH, 1};
            blitRegion.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
            blitRegion.dstOffsets[0] = {0, 0, 0};
            blitRegion.dstOffsets[1] = {(int32_t)swapchainExt.width, (int32_t)swapchainExt.height, 1};
            vk_.CmdBlitImage(cb,
                finalBuf.img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                swapchainImages[imgIdx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                1, &blitRegion, VK_FILTER_LINEAR);
        }

        // Transition swapchain image to present
        transition(cb, swapchainImages[imgIdx],
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            VK_ACCESS_TRANSFER_WRITE_BIT, 0,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);

        // Transition final effect output back to shader read
        transition(cb, finalBuf.img,
            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        }

    } else {
        // === Normal path: render directly to swapchain (or composite ring) ===
        VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        // Frame gen redirects the final target to a composite image we own;
        // the accessors return the swapchain pair unchanged when it is off.
        rpi.renderPass=targetRenderPass(); rpi.framebuffer=targetFramebuffer(imgIdx); rpi.renderArea={{0,0},swapchainExt};
        VkClearValue clr={{{0.f,0.f,0.f,1.f}}}; rpi.clearValueCount=1; rpi.pClearValues=&clr;

        vk_.CmdBeginRenderPass(cb, &rpi, VK_SUBPASS_CONTENTS_INLINE);
        VkViewport vp{0,0,(float)swapchainExt.width,(float)swapchainExt.height,0,1};
        vk_.CmdSetViewport(cb, 0, 1, &vp);
        VkRect2D sc{{0,0},swapchainExt}; vk_.CmdSetScissor(cb, 0, 1, &sc);

        vk_.CmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        for (auto& d : draws) {
            if (d.ds==VK_NULL_HANDLE) continue;
            vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLayout, 0, 1, &d.ds, 0, nullptr);
            WindowPushConstants pc{};
            pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;
            pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
            pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;
            pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            pc.useTexAlpha = 0;
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
            vk_.CmdDraw(cb, 4, 1, 0, 0);
        }

        if (cursorDrawn) {

            vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLayout, 0, 1, &cursorDS, 0, nullptr);
            float cx=(float)std::max(0,(int)ptrX-curHotX), cy=(float)std::max(0,(int)ptrY-curHotY);
            WindowPushConstants cpc{};
            cpc.ndcX0=(ox+cx*sx)/cw*2.f-1.f; cpc.ndcY0=(oy+cy*sy)/ch*2.f-1.f;
            cpc.ndcX1=(ox+(cx+curW)*sx)/cw*2.f-1.f; cpc.ndcY1=(oy+(cy+curH)*sy)/ch*2.f-1.f;
            cpc.useTexAlpha = 1;
            vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(cpc), &cpc);
            vk_.CmdDraw(cb, 4, 1, 0, 0);
        }
        vk_.CmdEndRenderPass(cb);
    }

    // Native LSFG: the chain's shared passes and the FIRST generated frame go
    // in this command buffer (slot 0). Later generations and the real frame
    // are recorded into their own buffers in renderFrame. process() runs EVERY
    // composite frame even with zero generations planned: it counts frames and
    // seeds history, otherwise the engine stays cold forever.
    recordFrameGenProcess(cb);
    if (fgPlan_.generations > 0) recordFrameGenGeneration(cb, 0);
    else copyCompositeToSwapchain(cb,
        compositeActive() ? fgPlan_.imgIdx[fgPlan_.presents - 1] : imgIdx);

    VkResult endStatus = vk_.EndCommandBuffer(cb);
    if (endStatus!=VK_SUCCESS) {
        RLOG_E("recordCmdBuf: EndCommandBuffer failed with status=%d (swapRB=%d draws=%zu imgIdx=%u)",
            (int)endStatus, (int)swapRB, draws.size(), imgIdx);
        throw std::runtime_error("end cb");
    }
}

void VulkanRendererContext::renderLoop() {

    while (isRunning) {
        { std::unique_lock<std::mutex> lk(dirtyMutex);
          dirtyCV.wait(lk,[this]{
              return !isRunning||(!surfaceDetached.load()&&(needsRender.load()||fbResized.load()))||cursorMoved.load(); }); }
        if (!isRunning) break;

        if (swapchain == VK_NULL_HANDLE || cmdBufs.empty()) continue;
        try { renderFrame(); } catch(...) {}
    }
}

void VulkanRendererContext::flushDeleteQueue() {
    // Deferred destruction: move to per-frame pending queue.
    // Resources will be freed when the GPU finishes the next frame.
    std::lock_guard<std::mutex> lk(renderMutex);
    if (deleteQueue.empty()) return;
    // Distribute across pendingDelete slots so we don't bloat one slot.
    // currentFrame is not protected by renderMutex, but reads are benign.
    size_t slot = currentFrame;
    pendingDelete[slot].insert(
        pendingDelete[slot].end(),
        std::make_move_iterator(deleteQueue.begin()),
        std::make_move_iterator(deleteQueue.end()));
    deleteQueue.clear();
}

void VulkanRendererContext::renderFrame() {
    std::shared_lock<std::shared_mutex> frameLock(frameMutex);

    needsRender.store(false,std::memory_order_relaxed);
    cursorMoved.store(false,std::memory_order_relaxed);

    if (surfaceDetached.load(std::memory_order_acquire)) return;
    if (scanoutActive.load() && !scanoutDisabled.load()) {
        applyScanoutBuffer();

        if (!scanoutBlackFrameDone.load()) {
            scanoutBlackFrameDone.store(true);

            std::lock_guard<std::mutex> lk(renderMutex);
            renderList.clear();
        } else {
            return;
        }
    } else {
        scanoutBlackFrameDone.store(false);
    }
    if (surfaceWidth==0||surfaceHeight==0) return;

    if (fbResized.load()) {
        for (auto& f:inFlightFences) vk_.WaitForFences(device,1,&f,VK_TRUE,2000000000ULL);
        cleanupSwapchain();
        bool ok=false;
        try{createSwapchain();createFramebuffers();createCmdBufs();recreateSyncObjects();imgInFlight.assign(swapchainImages.size(),VK_NULL_HANDLE);
ok=true;}catch(...){}
        if (ok) fbResized.store(false);
        return;
    }

    if (cmdSlot(kMaxPresentsPerFrame - 1) >= cmdBufs.size() || cmdBufs[cmdSlot(0)] == VK_NULL_HANDLE) return;
    bool currentFenceWaited = false;
    if (!vk_.GetFenceStatus || vk_.GetFenceStatus(device, inFlightFences[currentFrame]) == VK_NOT_READY) {
        // Bounded wait (2s): an stuck GPU must skip the frame, not hang us.
        if (vk_.WaitForFences(device,1,&inFlightFences[currentFrame],VK_TRUE,2000000000ULL) != VK_SUCCESS) {
            RLOG_E("renderFrame: current-frame fence wait timed out (2s), skipping frame");
            return;
        }
        currentFenceWaited = true;
    }
    // This slot's previous frame is complete, so its chain timestamps are final.
    readFgQueryResult();

    // --- Frame gen: decide whether THIS frame composites off-swapchain. The
    // targets are created lazily on the first armed frame and torn down when it
    // disarms, so a session that never turns frame gen on never allocates them.
    if (fgArmed_.load(std::memory_order_relaxed) && lsfgCaps_.supported() && !scanoutActive.load()) {
        const int mult = fgMultiplier_.load(std::memory_order_relaxed);
        const uint32_t want = (uint32_t)std::min(std::max(mult, 2), 4) + 1u;
        compositeArmed = ensureCompositeTargets(swapchainExt.width, swapchainExt.height, want);
        if (compositeArmed) createCursorOverlayRenderPass();
        if (compositeArmed && !compositeTargets.empty())
            compositeIndex = (compositeIndex + 1) % (uint32_t)compositeTargets.size();
    } else if (compositeArmed || !compositeTargets.empty()) {
        // Disarmed (or the swapchain went away): drop the ring so the direct
        // path is byte-identical to a session that never armed it.
        compositeArmed = false;
        vk_.DeviceWaitIdle(device);
        destroyCompositeTargets();
    }

    // --- Frame gen: decide how many frames to synthesise for this source
    // frame, BEFORE acquiring, since that sets how many images we need.
    fgPlan_ = FrameGenPlan{};
    if (lsfgEngine_ && fgConfigDirty_.exchange(false, std::memory_order_relaxed)) {
        lsfgEngine_->configure(
            (uint32_t)std::max(fgMultiplier_.load(std::memory_order_relaxed), 2),
            fgTargetRate_.load(std::memory_order_relaxed),
            fgFlowScale_.load(std::memory_order_relaxed),
            fgRefreshHz_.load(std::memory_order_relaxed));
    }
    if (compositeActive() && ensureLsfgEngine() &&
        lsfgEngine_->prepare(swapchainExt.width, swapchainExt.height, swapchainFmt)) {
        lsfgEngine_->setPresentedRate(fgPresentedRate_);
        if (containerWidth > 0 && containerHeight > 0)
            lsfgEngine_->setGuestExtent((uint32_t)containerWidth, (uint32_t)containerHeight);
        const uint32_t capacity = (uint32_t)std::min<size_t>(
            kMaxPresentsPerFrame - 1,
            compositeTargets.empty() ? 0 : compositeTargets.size() - 1);
        fgPlan_.generations = lsfgEngine_->plan(capacity, ++fgSourceFrames_);
    }
    fgPlan_.presents = fgPlan_.generations + 1;

    for (uint32_t k = 0; k < fgPlan_.presents; k++) {
        uint32_t idx = 0;
        VkResult ar = vk_.AcquireNextImageKHR(device,swapchain,2000000000ULL,
                                              imgAvailSems[syncSlot(k)],VK_NULL_HANDLE,&idx);
        if (ar==VK_ERROR_OUT_OF_DATE_KHR||ar==VK_ERROR_SURFACE_LOST_KHR){
            if ((fgAcquireFailLog_++ % 60u) == 0u)
                RLOG_E("renderFrame: acquire %u/%u -> %s (recreating swapchain)",
                       k, fgPlan_.presents,
                       ar==VK_ERROR_OUT_OF_DATE_KHR ? "OUT_OF_DATE" : "SURFACE_LOST");
            fbResized.store(true);
            return;
        }
        if (ar!=VK_SUCCESS&&ar!=VK_SUBOPTIMAL_KHR) {
            // Could not get every image we planned for. Anything already
            // acquired has a semaphore nobody will wait on, so drop this
            // frame entirely rather than leak a signal.
            if (k == 0) return;
            RLOG_E("renderFrame: acquire %u/%u failed (res=%d) - dropping frame",
                   k, fgPlan_.presents, (int)ar);
            fbResized.store(true);
            return;
        }
        if (idx >= swapchainFBs.size() || idx >= swapchainImages.size()) {
            RLOG_E("renderFrame: invalid acquired image index=%u (fb=%zu images=%zu)",
                idx, swapchainFBs.size(), swapchainImages.size());
            return;
        }
        fgPlan_.imgIdx[k] = idx;
    }
    // Real frame N is presented LAST; generated frames take the earlier slots.
    const uint32_t imgIdx = fgPlan_.imgIdx[fgPlan_.presents - 1];
    VkResult res = VK_SUCCESS;

    if (imgInFlight.size()!=swapchainImages.size()) imgInFlight.assign(swapchainImages.size(),VK_NULL_HANDLE);
    for (uint32_t k = 0; k < fgPlan_.presents; k++) {
        const uint32_t idx = fgPlan_.imgIdx[k];
        if (imgInFlight[idx]!=VK_NULL_HANDLE &&
            (!currentFenceWaited || imgInFlight[idx] != inFlightFences[currentFrame])) {
            if (!vk_.GetFenceStatus || vk_.GetFenceStatus(device, imgInFlight[idx]) == VK_NOT_READY) {
                if (vk_.WaitForFences(device,1,&imgInFlight[idx],VK_TRUE,2000000000ULL) != VK_SUCCESS) {
                    RLOG_E("renderFrame: in-flight image fence wait timed out (2s), skipping frame");
                    return;
                }
            }
        }
        imgInFlight[idx]=inFlightFences[currentFrame];
    }

    for (uint32_t k = 0; k < fgPlan_.presents; k++) vk_.ResetCommandBuffer(cmdBufs[cmdSlot(k)],0);

    float ox,oy,sx,sy,cw,ch;
    short ptrX,ptrY,curHotX,curHotY,curW,curH; bool curVis;
    VkBuffer curUpload=VK_NULL_HANDLE; bool hasCurUpload=false;

    {
        std::lock_guard<std::mutex> lk(renderMutex);


        if (!deleteQueue.empty()) {
            pendingDelete[currentFrame].insert(
                pendingDelete[currentFrame].end(),
                std::make_move_iterator(deleteQueue.begin()),
                std::make_move_iterator(deleteQueue.end()));
            deleteQueue.clear();
        }

        // Drain pending deletions for the frame we're about to reuse.
        // The fence for this frame has already been waited above, so
        // all resources queued for that frame are safe to destroy.
        for (auto& wt : pendingDelete[currentFrame]) {
            if (wt.ds  !=VK_NULL_HANDLE) vk_.FreeDescriptorSets(device,winTexPool,1,&wt.ds);
            if (wt.view!=VK_NULL_HANDLE) vk_.DestroyImageView(device,wt.view,nullptr);
            if (wt.img !=VK_NULL_HANDLE) vk_.DestroyImage(device,wt.img,nullptr);
            if (wt.mem !=VK_NULL_HANDLE) vk_.FreeMemory(device,wt.mem,nullptr);
            if (wt.stg !=VK_NULL_HANDLE){vk_.DestroyBuffer(device,wt.stg,nullptr);vk_.FreeMemory(device,wt.stgMem,nullptr);}
        }
        pendingDelete[currentFrame].clear();

        // Swap in the latest render list from producer threads
        if (renderListDirty.load(std::memory_order_acquire)) {
            renderList = std::move(pendingRenderList);
            renderListDirty.store(false, std::memory_order_release);
        }

        ox=sceneOffsetX; oy=sceneOffsetY; sx=sceneScaleX; sy=sceneScaleY;
        cw=(float)containerWidth; ch=(float)containerHeight;
        ptrX=(short)pointerX.load(); ptrY=(short)pointerY.load();
        curHotX=cursorHotX; curHotY=cursorHotY; curW=cursorTexW; curH=cursorTexH;
        curVis=cursorVisible.load();

        frameDraws.clear();
        for (auto& re:renderList) {
            auto it=texMap.find(re.id);
            if (it==texMap.end()) continue;
            WinTex& wt=it->second;
            if (wt.ds==VK_NULL_HANDLE) continue;
            DrawEntry de{wt.img,wt.ds,VK_NULL_HANDLE,re.x,re.y,wt.w,wt.h};
            de.isAHB=wt.isAHB;
            if (wt.needsTransition) { de.needsTransition=true; wt.needsTransition=false; }
            bool isDirty = dirtyFlags[re.id].load(std::memory_order_acquire);
            if (isDirty && !wt.isAHB && wt.stg!=VK_NULL_HANDLE) {
                de.upload=wt.stg;
                dirtyFlags[re.id].store(false, std::memory_order_release);
            } else if (wt.isAHB) {
                dirtyFlags[re.id].store(false, std::memory_order_release);
            }
            frameDraws.push_back(de);
        }

        if (isCursorImageDirty.load() && cursorImg!=VK_NULL_HANDLE && !cursorPixels.empty()) {
            VkDeviceSize csz=(VkDeviceSize)cursorTexW*cursorTexH*4;
            ensureCursorStaging(csz);
            isCursorImageDirty.store(false); hasCurUpload=true; curUpload=cursorStg;

            cursorUploadSize = csz;
        }

        // Держать effect-буферы в размере swapchain (поворот/ресайз surface).
        if (!activeEffects.empty()) ensureEffectBuffersLocked();
    }


    if (hasCurUpload && cursorStgP && !cursorPixels.empty())
        memcpy(cursorStgP, cursorPixels.data(), cursorUploadSize);

    bool effectiveCurVis = curVis && !scanoutActive.load();
    // Slot 0 carries the scene composite (+ shared chain passes + gen 0).
    // effectiveCurVis is forced off inside recordCmdBuf while the cursor is
    // drawn per present (snapshot into cursorOverlay_).
    recordCmdBuf(cmdBufs[cmdSlot(0)],imgIdx,frameDraws,
        frameAhbTransitions,framePreUpload,framePostUpload,
        curUpload,hasCurUpload,
        ox,oy,sx,sy,cw,ch,ptrX,ptrY,curHotX,curHotY,curW,curH,effectiveCurVis);

    // Each pending present gets its own submit: slot 0 (above) already holds
    // the composite + shared passes + generated frame 0; later generations
    // and the real frame are recorded and submitted one at a time, each
    // presented the moment its submit is queued. On the composite path the
    // first touch of an acquired image is a TRANSFER write, so wait on
    // TRANSFER too or the copy can run before the release.
    const VkPipelineStageFlags waitStage = compositeActive()
        ? (VkPipelineStageFlags)(VK_PIPELINE_STAGE_TRANSFER_BIT|VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        : (VkPipelineStageFlags)VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSwapchainKHR scs[]={swapchain};

    // The slot's fence rides on the LAST submit: ordered after everything
    // queued earlier on the same queue, so it still covers all work.
    vk_.ResetFences(device,1,&inFlightFences[currentFrame]);
    uint32_t presented = 0;
    bool fenceSubmitted = false;
    // Generated frames belong between N-1 and N: out first, real frame last.
    for (uint32_t k = 0; k < fgPlan_.presents; k++) {
        VkCommandBuffer cb = cmdBufs[cmdSlot(k)];
        if (k > 0) {
            VkCommandBufferBeginInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
            if (vk_.BeginCommandBuffer(cb,&bi)!=VK_SUCCESS) { fbResized.store(true); break; }
            VkMemoryBarrier mb{}; mb.sType=VK_STRUCTURE_TYPE_MEMORY_BARRIER;
            mb.srcAccessMask=VK_ACCESS_MEMORY_WRITE_BIT;
            mb.dstAccessMask=VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT;
            vk_.CmdPipelineBarrier(cb,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT,
                0, 1,&mb, 0,nullptr, 0,nullptr);
            if (k < fgPlan_.generations) recordFrameGenGeneration(cb, k);
            else                          copyCompositeToSwapchain(cb, imgIdx);
            if (vk_.EndCommandBuffer(cb)!=VK_SUCCESS) {
                RLOG_E("renderFrame: EndCommandBuffer failed for present %u/%u", k, fgPlan_.presents);
                fbResized.store(true);
                break;
            }
        }
        const bool last = (k + 1 == fgPlan_.presents);
        VkSemaphore wSem = imgAvailSems[syncSlot(k)];
        VkSemaphore sSem = renderDoneSems[syncSlot(k)];
        VkSubmitInfo si{}; si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO;
        si.waitSemaphoreCount=1; si.pWaitSemaphores=&wSem; si.pWaitDstStageMask=&waitStage;
        si.commandBufferCount=1; si.pCommandBuffers=&cb;
        si.signalSemaphoreCount=1; si.pSignalSemaphores=&sSem;
        if (vk_.QueueSubmit(graphicsQueue,1,&si, last ? inFlightFences[currentFrame] : VK_NULL_HANDLE)!=VK_SUCCESS) {
            vk_.DestroyFence(device,inFlightFences[currentFrame],nullptr);
            VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; fi.flags=VK_FENCE_CREATE_SIGNALED_BIT;
            vk_.CreateFence(device,&fi,nullptr,&inFlightFences[currentFrame]);
            return;
        }
        fenceSubmitted = last;
        VkPresentInfoKHR pi{}; pi.sType=VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        pi.waitSemaphoreCount=1; pi.pWaitSemaphores=&sSem;
        pi.swapchainCount=1; pi.pSwapchains=scs; pi.pImageIndices=&fgPlan_.imgIdx[k];
        res=vk_.QueuePresentKHR(graphicsQueue,&pi);
        presented++;
        if (res==VK_ERROR_OUT_OF_DATE_KHR||res==VK_ERROR_SURFACE_LOST_KHR) {
            // The swapchain recreate this triggers also rebuilds the
            // semaphores, so stranded acquire signals cannot leak into it.
            fbResized.store(true);
            break;
        }
    }
    if (!fenceSubmitted) {
        VkSubmitInfo si{}; si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO;
        vk_.QueueSubmit(graphicsQueue,1,&si,inFlightFences[currentFrame]);
    }
    trackPresentedRate(presented);
    currentFrame=(currentFrame+1)%MAX_FRAMES_IN_FLIGHT;
}

void VulkanRendererContext::onSurfaceResized(int w, int h) {
    std::lock_guard<std::mutex> lk(renderMutex);
    if (w==0||h==0) return;
    surfaceWidth=w; surfaceHeight=h; fbResized.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::detachSurface() {
    surfaceDetached.store(true, std::memory_order_release);
    dirtyCV.notify_all();

    { std::unique_lock<std::shared_mutex> frameLock(frameMutex); }

    vk_.DeviceWaitIdle(device);
    cleanupSwapchain();
    if (surface != VK_NULL_HANDLE) {
        vk_.DestroySurfaceKHR(instance, surface, nullptr);
        surface = VK_NULL_HANDLE;
    }
    if (window) {
        ANativeWindow_release(window);
        window = nullptr;
    }
}

bool VulkanRendererContext::reattachSurface(ANativeWindow* newWindow) {
    if (window) { ANativeWindow_release(window); window = nullptr; }
    window = newWindow;
    VkAndroidSurfaceCreateInfoKHR ci{};
    ci.sType  = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    ci.window = window;
    if (vk_.CreateAndroidSurfaceKHR(instance, &ci, nullptr, &surface) != VK_SUCCESS) {
        __android_log_print(ANDROID_LOG_ERROR, "Winlator_Renderer", "reattachSurface: CreateAndroidSurface failed");
        ANativeWindow_release(window); window = nullptr;
        return false;
    }
    {
        std::unique_lock<std::shared_mutex> frameLock(frameMutex);
        try {
            createSwapchain();
            createFramebuffers();
            createCmdBufs();
            imgInFlight.assign(swapchainImages.size(), VK_NULL_HANDLE);
        } catch (...) {
            __android_log_print(ANDROID_LOG_ERROR, "Winlator_Renderer", "reattachSurface: swapchain recreate failed");
            return false;
        }
        surfaceDetached.store(false, std::memory_order_release);
    }
    needsRender.store(true, std::memory_order_release);
    dirtyCV.notify_all();
    __android_log_print(ANDROID_LOG_DEBUG, "Winlator_Renderer", "reattachSurface: OK");
    return true;
}

void VulkanRendererContext::setTransform(float ox, float oy, float sx, float sy) {
    { std::lock_guard<std::mutex> lk(renderMutex); sceneOffsetX=ox;sceneOffsetY=oy;sceneScaleX=sx;sceneScaleY=sy; }
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updatePointerPosition(short x, short y) {
    pointerX.store(x); pointerY.store(y);
    if (cursorVisible.load()) { cursorMoved.store(true); dirtyCV.notify_one(); }
}

void VulkanRendererContext::setCursorVisible(bool v) {
    cursorVisible.store(v); cursorMoved.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateCursorImage(void* px, short w, short h, short hotX, short hotY) {
    if (!px||w<=0||h<=0) return;
    std::lock_guard<std::mutex> lk(renderMutex);
    ensureCursorTex(w,h);
    cursorPixels.resize((size_t)w*h); memcpy(cursorPixels.data(),px,(size_t)w*h*4);
    cursorHotX=hotX; cursorHotY=hotY;
    isCursorImageDirty.store(true); needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateWindowContent(int64_t id, void* px, short w, short h, short stride, int, int) {
    if (!px||w<=0||h<=0) return;

    void* mapped=nullptr;
    {
        std::lock_guard<std::mutex> lk(renderMutex);
        WinTex& wt=texMap[id];
        if (wt.img==VK_NULL_HANDLE || wt.w!=w || wt.h!=h) {
            if (wt.img!=VK_NULL_HANDLE) {
                pendingDelete[currentFrame].push_back(std::move(wt));
            }
            if (!createWinTexResources(wt,w,h)) { texMap.erase(id); return; }
        }
        mapped=wt.mapped;
    }

    if (!mapped) return;
    const size_t dstPitch=(size_t)w*4;
    const int32_t srcStride=stride>0?stride:w;
    uint32_t* src2=static_cast<uint32_t*>(px);
    uint8_t*  dst2=static_cast<uint8_t*>(mapped);
    for (int row=0;row<h;++row)
        memcpy(dst2+(size_t)row*dstPitch,
               &src2[(size_t)row*srcStride],(size_t)w*4);
    {
        std::lock_guard<std::mutex> lk(renderMutex);
        auto it=texMap.find(id);
        if (it!=texMap.end()) dirtyFlags[id].store(true, std::memory_order_release);
    }
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateWindowContentAHB(int64_t id, AHardwareBuffer* ahb, short, short, int, int) {
    if (!ahb) return;
    std::lock_guard<std::mutex> lk(renderMutex);





    auto cit = ahbImportCache.find(ahb);
    if (cit == ahbImportCache.end()) {
        WinTex tmp{};
        if (!importAHBToWinTex(tmp, ahb)) {
            RLOG_E("updateWindowContentAHB: import failed for id=%" PRId64, id);
            return;
        }
        AHardwareBuffer_acquire(ahb);
        ahbImportCache[ahb] = tmp;
        windowAhbs[id].push_back(ahb);
        cit = ahbImportCache.find(ahb);
        RLOG("updateWindowContentAHB: imported new AHB %p for id=%" PRId64 " (%dx%d)",
            (void*)ahb, id, tmp.w, tmp.h);
    }


    WinTex& src = cit->second;
    WinTex& wt  = texMap[id];
    wt.img  = src.img;
    wt.mem  = src.mem;
    wt.view = src.view;
    wt.ds   = src.ds;
    wt.isAHB = true;
    wt.ahb  = ahb;
    wt.w    = src.w;
    wt.h    = src.h;

    if (src.needsTransition) {
        wt.needsTransition  = true;
        src.needsTransition = false;
    }
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setRenderList(const int64_t* ids, const int* xs, const int* ys, int count) {
    // Double-buffered: write to pending, atomically swap in renderFrame.
    // No renderMutex needed — producer and consumer never access the same
    // vector concurrently.
    {
        std::lock_guard<std::mutex> lk(renderMutex);
        pendingRenderList.resize(count);
        for (int i=0;i<count;i++) pendingRenderList[i]={ids[i],xs[i],ys[i]};
    }
    renderListDirty.store(true, std::memory_order_release);
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::removeWindow(int64_t id) {
    std::lock_guard<std::mutex> lk(renderMutex);



    auto it = texMap.find(id);
    if (it != texMap.end()) {
        if (!it->second.isAHB) destroyWinTex(it->second);
        else it->second = {};
        texMap.erase(it);
    }
    dirtyFlags.erase(id);


    auto wit = windowAhbs.find(id);
    if (wit != windowAhbs.end()) {
        for (AHardwareBuffer* ahb : wit->second) {
            auto cit = ahbImportCache.find(ahb);
            if (cit != ahbImportCache.end()) {
                WinTex deferred = cit->second;
                deferred.isAHB  = false;
                deleteQueue.push_back(deferred);
                AHardwareBuffer_release(ahb);
                ahbImportCache.erase(cit);
            }
        }
        windowAhbs.erase(wit);
    }

    renderList.erase(std::remove_if(renderList.begin(),renderList.end(),
        [id](const RenderEntry& e){return e.id==id;}),renderList.end());
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::cleanupAllAHBCache() {
    for (auto& [ahb, wt] : ahbImportCache) {
        if (wt.ds   != VK_NULL_HANDLE) vk_.FreeDescriptorSets(device, winTexPool, 1, &wt.ds);
        if (wt.view != VK_NULL_HANDLE) vk_.DestroyImageView(device, wt.view, nullptr);
        if (wt.img  != VK_NULL_HANDLE) vk_.DestroyImage(device, wt.img, nullptr);
        if (wt.mem  != VK_NULL_HANDLE) vk_.FreeMemory(device, wt.mem, nullptr);
        AHardwareBuffer_release(ahb);
    }
    ahbImportCache.clear();
    windowAhbs.clear();
}


void VulkanRendererContext::dumpRendererInfo() {
    VkPhysicalDeviceProperties props{};
    vk_.GetPhysicalDeviceProperties(physicalDevice,&props);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "=== RENDERER INFO ===");
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "GPU: %s vendorID=0x%x driverVersion=0x%x apiVersion=%d.%d.%d",
        props.deviceName,props.vendorID,props.driverVersion,
        VK_VERSION_MAJOR(props.apiVersion),VK_VERSION_MINOR(props.apiVersion),VK_VERSION_PATCH(props.apiVersion));
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Swapchain: %dx%d fmt=%d",swapchainExt.width,swapchainExt.height,(int)swapchainFmt);
    std::string pmList;
    for(auto pm:availablePresentModes) pmList+=std::to_string((int)pm)+" ";
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "SupportedPresentModes: [%s] current=%d",pmList.c_str(),(int)requestedPresentMode);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Filter: mode=%d (%s)", filterMode, filterMode==2?(cubicSupported?"CUBIC":"LINEAR"):filterMode==1?"NEAREST":"LINEAR");
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Scanout: active=%d gameFrameDelivered=%d scanoutGameSC=%p",
        (int)scanoutActive.load(),(int)gameFrameDelivered.load(),scanoutGameSC);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Surface: %dx%d container: %dx%d",
        surfaceWidth,surfaceHeight,containerWidth,containerHeight);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,"=== END RENDERER INFO ===");
}

void VulkanRendererContext::setFilterMode(int mode) {
    RLOG("setFilterMode: %d -> %d (%s->%s)", filterMode, mode,
        filterMode==2?(cubicSupported?"CUBIC":"LINEAR"):filterMode==1?"NEAREST":"LINEAR", mode==2?(cubicSupported?"CUBIC":"LINEAR"):mode==1?"NEAREST":"LINEAR");
    if (filterMode==mode) { RLOG("setFilterMode: already set, skipping"); return; }
    filterMode=mode;
    vk_.DeviceWaitIdle(device);
    if (sampler!=VK_NULL_HANDLE){vk_.DestroySampler(device,sampler,nullptr);sampler=VK_NULL_HANDLE;}
    createSampler();
    auto updateDS=[&](VkDescriptorSet ds, VkImageView view){
        if(ds==VK_NULL_HANDLE||view==VK_NULL_HANDLE) return;
        VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        dii.imageView=view; dii.sampler=sampler;
        VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        wr.dstSet=ds; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        wr.descriptorCount=1; wr.pImageInfo=&dii;
        vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    };
    
    for (auto& [id,wt]:texMap) updateDS(wt.ds, wt.view);

    for (auto& [ahb,wt]:ahbImportCache) updateDS(wt.ds, wt.view);
    if (cursorDS!=VK_NULL_HANDLE&&cursorView!=VK_NULL_HANDLE) updateDS(cursorDS, cursorView);
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setSwapRB(bool enabled) {
    if (swapRB == enabled) return;
    swapRB = enabled;
    RLOG("setSwapRB: %d", (int)swapRB);


}

void VulkanRendererContext::setPresentMode(VkPresentModeKHR mode) {
    bool supported = false;
    for (auto pm : availablePresentModes) if (pm == mode) { supported = true; break; }
    VkPresentModeKHR target = supported ? mode : VK_PRESENT_MODE_FIFO_KHR;
    RLOG("setPresentMode: requested=%d supported=%d -> applying=%d",
        (int)mode, (int)supported, (int)target);
    if (requestedPresentMode==target) { RLOG("setPresentMode: already set, skipping"); return; }
    requestedPresentMode=target;
    fbResized.store(true); dirtyCV.notify_one();
}

std::vector<int> VulkanRendererContext::getSupportedPresentModes() const {
    std::vector<int> out;
    for (auto pm:availablePresentModes) out.push_back((int)pm);
    return out;
}

// ============ Effect post-processing ============

void VulkanRendererContext::setEffects(const EffectEntry* entries, int count) {
    std::lock_guard<std::mutex> lk(renderMutex);
    activeEffects.clear();
    for (int i = 0; i < count; i++) activeEffects.push_back(entries[i]);
    RLOG("setEffects: %d effects set", count);
    // Full-res: буферы в размере swapchain. NDC-математика сцены относительная
    // (cw/ch), раскладка от размера вьюпорта не зависит — только детализация.
    if (!activeEffects.empty()) ensureEffectBuffersLocked();
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::ensureEffectBuffersLocked() {
    int tw = (int)swapchainExt.width > 0 ? (int)swapchainExt.width : containerWidth;
    int th = (int)swapchainExt.height > 0 ? (int)swapchainExt.height : containerHeight;
    if (tw <= 0 || th <= 0) return;
    if (effectReadBuf.img != VK_NULL_HANDLE && (effectW != tw || effectH != th)) {
        destroyEffectOffscreen(effectReadBuf);
        destroyEffectOffscreen(effectWriteBuf);
        effectW = effectH = 0;
    }
    if (effectReadBuf.img == VK_NULL_HANDLE) {
        createEffectOffscreen(effectReadBuf, tw, th);
        createEffectOffscreen(effectWriteBuf, tw, th);
        effectW = tw; effectH = th;
        RLOG("ensureEffectBuffers: %dx%d", tw, th);
    }
}

void VulkanRendererContext::clearEffects() {
    std::lock_guard<std::mutex> lk(renderMutex);
    activeEffects.clear();
    RLOG("clearEffects: all effects removed");
    needsRender.store(true); dirtyCV.notify_one();
}

bool VulkanRendererContext::hasEffects() const {
    return !activeEffects.empty();
}

void VulkanRendererContext::createEffectResources() {
    createEffectRenderPass();
    createEffectDSLayout();
    createEffectPipelineLayout();
    createEffectPipelines();
    // Create sampler for effect passes
    VkSamplerCreateInfo sci{}; sci.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    sci.magFilter = VK_FILTER_LINEAR; sci.minFilter = VK_FILTER_LINEAR;
    sci.addressModeU = sci.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    sci.maxLod = VK_LOD_CLAMP_NONE; sci.minLod = 0; sci.mipLodBias = 0;
    sci.anisotropyEnable = VK_FALSE;
    vk_.CreateSampler(device, &sci, nullptr, &effectSampler);
}

void VulkanRendererContext::destroyEffectResources() {
    destroyEffectOffscreen(effectReadBuf);
    destroyEffectOffscreen(effectWriteBuf);
    for (int i = 0; i < EFFECT_COUNT; i++) {
        if (effectPipelines[i] != VK_NULL_HANDLE) {
            vk_.DestroyPipeline(device, effectPipelines[i], nullptr);
            effectPipelines[i] = VK_NULL_HANDLE;
        }
    }
    if (effectPipeLayout != VK_NULL_HANDLE) { vk_.DestroyPipelineLayout(device, effectPipeLayout, nullptr); effectPipeLayout = VK_NULL_HANDLE; }
    if (effectDSLayout != VK_NULL_HANDLE) { vk_.DestroyDescriptorSetLayout(device, effectDSLayout, nullptr); effectDSLayout = VK_NULL_HANDLE; }
    if (effectRenderPass != VK_NULL_HANDLE) { vk_.DestroyRenderPass(device, effectRenderPass, nullptr); effectRenderPass = VK_NULL_HANDLE; }
    if (effectSampler != VK_NULL_HANDLE) { vk_.DestroySampler(device, effectSampler, nullptr); effectSampler = VK_NULL_HANDLE; }
}

void VulkanRendererContext::createEffectRenderPass() {
    VkAttachmentDescription att{}; att.format = swapchainFmt;
    att.samples = VK_SAMPLE_COUNT_1_BIT;
    att.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR; att.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    att.initialLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL; att.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    VkAttachmentReference ref{}; ref.attachment = 0; ref.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    VkSubpassDescription sp{}; sp.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    sp.colorAttachmentCount = 1; sp.pColorAttachments = &ref;
    VkRenderPassCreateInfo rpi{}; rpi.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    rpi.attachmentCount = 1; rpi.pAttachments = &att; rpi.subpassCount = 1; rpi.pSubpasses = &sp;
    if (vk_.CreateRenderPass(device, &rpi, nullptr, &effectRenderPass) != VK_SUCCESS)
        RLOG_E("createEffectRenderPass: failed");
}

void VulkanRendererContext::createEffectDSLayout() {
    VkDescriptorSetLayoutBinding bind{};
    bind.binding = 0; bind.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    bind.descriptorCount = 1; bind.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    VkDescriptorSetLayoutCreateInfo ci{}; ci.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    ci.bindingCount = 1; ci.pBindings = &bind;
    if (vk_.CreateDescriptorSetLayout(device, &ci, nullptr, &effectDSLayout) != VK_SUCCESS)
        RLOG_E("createEffectDSLayout: failed");
}

void VulkanRendererContext::createEffectPipelineLayout() {
    VkPushConstantRange pcr{};
    pcr.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
    pcr.offset = 0; pcr.size = sizeof(EffectPushConstants);
    VkPipelineLayoutCreateInfo ci{}; ci.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    ci.setLayoutCount = 1; ci.pSetLayouts = &effectDSLayout;
    ci.pushConstantRangeCount = 1; ci.pPushConstantRanges = &pcr;
    if (vk_.CreatePipelineLayout(device, &ci, nullptr, &effectPipeLayout) != VK_SUCCESS)
        RLOG_E("createEffectPipelineLayout: failed");
}

static const uint32_t* getEffectFragCode(EffectType type, size_t& sz) {
    switch (type) {
        case EFFECT_COLOR:      sz = sizeof(effect_color_frag_code)/sizeof(uint32_t); return effect_color_frag_code;
        case EFFECT_FXAA:       sz = sizeof(effect_fxaa_frag_code)/sizeof(uint32_t); return effect_fxaa_frag_code;
        case EFFECT_CRT:        sz = sizeof(effect_crt_frag_code)/sizeof(uint32_t); return effect_crt_frag_code;
        case EFFECT_TOON:       sz = sizeof(effect_toon_frag_code)/sizeof(uint32_t); return effect_toon_frag_code;
        case EFFECT_VIGNETTE:   sz = sizeof(effect_vignette_frag_code)/sizeof(uint32_t); return effect_vignette_frag_code;
        case EFFECT_SEPIA:      sz = sizeof(effect_sepia_frag_code)/sizeof(uint32_t); return effect_sepia_frag_code;
        case EFFECT_BLUR:       sz = sizeof(effect_blur_frag_code)/sizeof(uint32_t); return effect_blur_frag_code;
        case EFFECT_PIXELATE:   sz = sizeof(effect_pixelate_frag_code)/sizeof(uint32_t); return effect_pixelate_frag_code;
        case EFFECT_GRAYSCALE:  sz = sizeof(effect_grayscale_frag_code)/sizeof(uint32_t); return effect_grayscale_frag_code;
        case EFFECT_SHARPEN:   sz = sizeof(effect_sharpen_frag_code)/sizeof(uint32_t); return effect_sharpen_frag_code;
        case EFFECT_SMOOTH:     sz = sizeof(effect_smooth_frag_code)/sizeof(uint32_t); return effect_smooth_frag_code;
        case EFFECT_HDR:        sz = sizeof(effect_hdr_frag_code)/sizeof(uint32_t); return effect_hdr_frag_code;
        case EFFECT_NTSC:       sz = sizeof(effect_ntsc_frag_code)/sizeof(uint32_t); return effect_ntsc_frag_code;
        default: return nullptr;
    }
}

void VulkanRendererContext::createEffectPipelines() {
    VkShaderModule vertMod = makeShader(effect_vert_code, sizeof(effect_vert_code));
    if (vertMod == VK_NULL_HANDLE) { RLOG_E("createEffectPipelines: failed to create vert shader"); return; }

    VkPipelineShaderStageCreateInfo vertStage{};
    vertStage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    vertStage.stage = VK_SHADER_STAGE_VERTEX_BIT; vertStage.module = vertMod; vertStage.pName = "main";

    for (int i = EFFECT_NONE + 1; i < EFFECT_COUNT; i++) {
        size_t fragSz = 0;
        const uint32_t* fragCode = getEffectFragCode((EffectType)i, fragSz);
        if (!fragCode) continue;
        VkShaderModule fragMod = makeShader(fragCode, fragSz * sizeof(uint32_t));
        if (fragMod == VK_NULL_HANDLE) { RLOG_E("createEffectPipelines: failed to create frag shader for effect %d", i); continue; }

        VkPipelineShaderStageCreateInfo stages[] = {vertStage, {}};
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module = fragMod; stages[1].pName = "main";

        VkPipelineVertexInputStateCreateInfo vi{}; vi.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
        VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
        ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;

        VkDynamicState dynStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynCI{}; dynCI.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
        dynCI.dynamicStateCount = 2; dynCI.pDynamicStates = dynStates;

        VkViewport vp{0,0,1,1,0,1}; VkRect2D sc{{0,0},{1,1}};
        VkPipelineViewportStateCreateInfo vs{}; vs.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
        vs.viewportCount = 1; vs.pViewports = &vp; vs.scissorCount = 1; vs.pScissors = &sc;

        VkPipelineRasterizationStateCreateInfo rs{}; rs.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
        rs.lineWidth = 1.f;

        VkPipelineMultisampleStateCreateInfo ms{}; ms.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
        ms.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

        VkPipelineColorBlendAttachmentState cba{}; cba.colorWriteMask = VK_COLOR_COMPONENT_R_BIT|VK_COLOR_COMPONENT_G_BIT|VK_COLOR_COMPONENT_B_BIT|VK_COLOR_COMPONENT_A_BIT;
        VkPipelineColorBlendStateCreateInfo cb{}; cb.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
        cb.attachmentCount = 1; cb.pAttachments = &cba;

        VkGraphicsPipelineCreateInfo gpci{}; gpci.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
        gpci.stageCount = 2; gpci.pStages = stages;
        gpci.pVertexInputState = &vi; gpci.pInputAssemblyState = &ia;
        gpci.pViewportState = &vs; gpci.pRasterizationState = &rs;
        gpci.pMultisampleState = &ms; gpci.pColorBlendState = &cb;
        gpci.pDynamicState = &dynCI;
        gpci.layout = effectPipeLayout; gpci.renderPass = effectRenderPass;

        VkResult pipeRes = vk_.CreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &gpci, nullptr, &effectPipelines[i]);
        if (pipeRes != VK_SUCCESS) { RLOG_E("createEffectPipelines: pipeline creation failed for effect %d res=%d", i, (int)pipeRes); }
        else { RLOG("createEffectPipelines: created pipeline for effect %d", i); }
        vk_.DestroyShaderModule(device, fragMod, nullptr);
    }
    vk_.DestroyShaderModule(device, vertMod, nullptr);
}

void VulkanRendererContext::createEffectOffscreen(EffectOffscreen& off, int w, int h) {
    VkImageCreateInfo ici{}; ici.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ici.imageType = VK_IMAGE_TYPE_2D; ici.format = swapchainFmt;
    ici.extent = {(uint32_t)w, (uint32_t)h, 1}; ici.mipLevels = 1; ici.arrayLayers = 1;
    ici.samples = VK_SAMPLE_COUNT_1_BIT; ici.tiling = VK_IMAGE_TILING_OPTIMAL;
    ici.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    vk_.CreateImage(device, &ici, nullptr, &off.img);

    VkMemoryRequirements mr; vk_.GetImageMemoryRequirements(device, off.img, &mr);
    VkMemoryAllocateInfo mai{}; mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize = mr.size; mai.memoryTypeIndex = findMemType(mr.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    vk_.AllocateMemory(device, &mai, nullptr, &off.mem);
    vk_.BindImageMemory(device, off.img, off.mem, 0);

    VkImageViewCreateInfo ivci{}; ivci.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    ivci.image = off.img; ivci.viewType = VK_IMAGE_VIEW_TYPE_2D; ivci.format = swapchainFmt;
    ivci.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vk_.CreateImageView(device, &ivci, nullptr, &off.view);

    VkFramebufferCreateInfo fci{}; fci.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    fci.renderPass = effectRenderPass; fci.attachmentCount = 1; fci.pAttachments = &off.view;
    fci.width = w; fci.height = h; fci.layers = 1;
    vk_.CreateFramebuffer(device, &fci, nullptr, &off.fb);

    // Allocate descriptor set for this offscreen
    VkDescriptorSetAllocateInfo dsai{}; dsai.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    dsai.descriptorPool = winTexPool; dsai.descriptorSetCount = 1; dsai.pSetLayouts = &effectDSLayout;
    vk_.AllocateDescriptorSets(device, &dsai, &off.ds);

    VkDescriptorImageInfo dii{}; dii.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    dii.imageView = off.view; dii.sampler = effectSampler;
    VkWriteDescriptorSet wr{}; wr.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    wr.dstSet = off.ds; wr.dstBinding = 0; wr.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    wr.descriptorCount = 1; wr.pImageInfo = &dii;
    vk_.UpdateDescriptorSets(device, 1, &wr, 0, nullptr);

    // Transition to shader read-only
    VkCommandBuffer cmd = beginOneTime();
    transition(cmd, off.img, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        0, VK_ACCESS_SHADER_READ_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
    endOneTime(cmd);
}

void VulkanRendererContext::destroyEffectOffscreen(EffectOffscreen& off) {
    if (off.ds != VK_NULL_HANDLE) { vk_.FreeDescriptorSets(device, winTexPool, 1, &off.ds); off.ds = VK_NULL_HANDLE; }
    if (off.fb != VK_NULL_HANDLE) { vk_.DestroyFramebuffer(device, off.fb, nullptr); off.fb = VK_NULL_HANDLE; }
    if (off.view != VK_NULL_HANDLE) { vk_.DestroyImageView(device, off.view, nullptr); off.view = VK_NULL_HANDLE; }
    if (off.img != VK_NULL_HANDLE) { vk_.DestroyImage(device, off.img, nullptr); off.img = VK_NULL_HANDLE; }
    if (off.mem != VK_NULL_HANDLE) { vk_.FreeMemory(device, off.mem, nullptr); off.mem = VK_NULL_HANDLE; }
}

#include <chrono>

// ===================== Native LSFG frame generation =====================
// Host-side Lossless Scaling (ported from Bannerlator lsfg-native): the scene
// is composited into an owned ring image, the compute chain interpolates
// between history frames, and N+1 presents go out back-to-back under FIFO.

bool VulkanRendererContext::createCompositeRenderPass() {
    if (compositeRenderPass != VK_NULL_HANDLE) return true;

    VkAttachmentDescription att{}; att.format=swapchainFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR; att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE; att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    // GENERAL, so both a compute dispatch (generate) and the copy can touch the
    // finished frame without another layout transition per use.
    att.finalLayout=VK_IMAGE_LAYOUT_GENERAL;

    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;

    VkSubpassDependency deps[2]{};
    deps[0].srcSubpass=VK_SUBPASS_EXTERNAL; deps[0].dstSubpass=0;
    deps[0].srcStageMask=VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT;
    deps[0].dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[0].srcAccessMask=VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_TRANSFER_READ_BIT;
    deps[0].dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    deps[1].srcSubpass=0; deps[1].dstSubpass=VK_SUBPASS_EXTERNAL;
    deps[1].srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[1].dstStageMask=VK_PIPELINE_STAGE_TRANSFER_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    deps[1].srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    deps[1].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT|VK_ACCESS_SHADER_READ_BIT;

    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=2; ci.pDependencies=deps;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&compositeRenderPass)!=VK_SUCCESS) {
        RLOG_E("createCompositeRenderPass failed");
        compositeRenderPass = VK_NULL_HANDLE;
        return false;
    }
    return true;
}

bool VulkanRendererContext::ensureCompositeTargets(uint32_t w, uint32_t h, uint32_t count) {
    if (w == 0 || h == 0 || count == 0) return false;
    if (count > kMaxCompositeTargets) count = kMaxCompositeTargets;

    if (compositeW == w && compositeH == h && compositeTargets.size() == count) return true;
    if (!createCompositeRenderPass()) return false;

    if (!compositeTargets.empty()) vk_.DeviceWaitIdle(device);
    destroyCompositeTargets();

    compositeTargets.resize(count);
    for (uint32_t i = 0; i < count; i++) {
        CompositeTarget& t = compositeTargets[i];

        VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        ii.imageType=VK_IMAGE_TYPE_2D; ii.extent={w,h,1};
        ii.mipLevels=1; ii.arrayLayers=1; ii.format=swapchainFmt;
        ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        ii.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                 | VK_IMAGE_USAGE_SAMPLED_BIT
                 | VK_IMAGE_USAGE_STORAGE_BIT
                 | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                 | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
        if (vk_.CreateImage(device,&ii,nullptr,&t.img)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,t.img,&req);
        VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        ai.allocationSize=req.size;
        ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (vk_.AllocateMemory(device,&ai,nullptr,&t.mem)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }
        vk_.BindImageMemory(device,t.img,t.mem,0);

        VkImageViewCreateInfo vci{}; vci.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        vci.image=t.img; vci.viewType=VK_IMAGE_VIEW_TYPE_2D; vci.format=swapchainFmt;
        vci.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        vci.components={VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,
                        VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
        if (vk_.CreateImageView(device,&vci,nullptr,&t.view)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }
        if (vk_.CreateImageView(device,&vci,nullptr,&t.storageView)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkImageView att[]={t.view};
        VkFramebufferCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        fi.renderPass=compositeRenderPass; fi.attachmentCount=1; fi.pAttachments=att;
        fi.width=w; fi.height=h; fi.layers=1;
        if (vk_.CreateFramebuffer(device,&fi,nullptr,&t.fb)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkDescriptorSetAllocateInfo dsai{}; dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
        if (vk_.AllocateDescriptorSets(device,&dsai,&t.ds)!=VK_SUCCESS) { t.ds=VK_NULL_HANDLE; destroyCompositeTargets(); return false; }
        VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_GENERAL;
        dii.imageView=t.view; dii.sampler=sampler;
        VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        wr.dstSet=t.ds; wr.dstBinding=0;
        wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1; wr.pImageInfo=&dii;
        vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    }

    compositeW = w; compositeH = h; compositeIndex = 0;
    RLOG("composite ring: %ux%u x%u targets", w, h, count);
    return true;
}

void VulkanRendererContext::destroyCompositeTargets() {
    for (CompositeTarget& t : compositeTargets) {
        if (t.ds          !=VK_NULL_HANDLE){ vk_.FreeDescriptorSets(device,winTexPool,1,&t.ds); t.ds=VK_NULL_HANDLE; }
        if (t.fb          !=VK_NULL_HANDLE){ vk_.DestroyFramebuffer(device,t.fb,nullptr); t.fb=VK_NULL_HANDLE; }
        if (t.storageView !=VK_NULL_HANDLE){ vk_.DestroyImageView(device,t.storageView,nullptr); t.storageView=VK_NULL_HANDLE; }
        if (t.view        !=VK_NULL_HANDLE){ vk_.DestroyImageView(device,t.view,nullptr); t.view=VK_NULL_HANDLE; }
        if (t.img         !=VK_NULL_HANDLE){ vk_.DestroyImage(device,t.img,nullptr); t.img=VK_NULL_HANDLE; }
        if (t.mem         !=VK_NULL_HANDLE){ vk_.FreeMemory(device,t.mem,nullptr); t.mem=VK_NULL_HANDLE; }
    }
    compositeTargets.clear();
    compositeW = compositeH = 0;
    compositeIndex = 0;
}

bool VulkanRendererContext::ensureLsfgEngine() {
    if (lsfgEngine_) return lsfgEngine_->valid();
    // The cache file may land after arming (background build). Retry a failed
    // attempt once its identity changed AND it sat untouched for 2s (build
    // finished) — no per-frame spam, no stuck latch.
    if (!lsfgCachePath_.empty()) {
        struct stat st{};
        if (stat(lsfgCachePath_.c_str(), &st) == 0) {
            char buf[64];
            snprintf(buf, sizeof(buf), "%lld:%lld",
                     (long long)st.st_size, (long long)st.st_mtime);
            std::string id = buf;
            if (id != lsfgCacheIdentity_) {
                const auto quietFor = std::chrono::system_clock::now()
                    - std::chrono::system_clock::from_time_t(st.st_mtime);
                if (quietFor > std::chrono::seconds(2)) {
                    lsfgCacheIdentity_ = id;
                    if (lsfgEngineTried_)
                        RLOG("lsfg-native: cache settled, retrying engine init");
                    lsfgEngineTried_ = false;
                }
            }
        } else if (!lsfgCacheIdentity_.empty()) {
            lsfgCacheIdentity_.clear();
        }
    }
    if (lsfgEngineTried_) return false;      // failed once; don't retry every frame
    lsfgEngineTried_ = true;

    if (lsfgCachePath_.empty()) {
        RLOG_E("lsfg-native: no shader cache path set");
        return false;
    }
    if (!lsfgVkdInit(vk_)) {
        RLOG_E("lsfg-native: dispatch incomplete; frame generation unavailable");
        return false;
    }

    auto engine = std::make_unique<lsfg::Engine>();
    if (!engine->init(device, physicalDevice, lsfgCachePath_)) {
        RLOG_E("lsfg-native: engine init failed (cache %s)", lsfgCachePath_.c_str());
        return false;
    }
    lsfgEngine_ = std::move(engine);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
    RLOG("lsfg-native: engine ready");
    return true;
}

void VulkanRendererContext::ensureFgQueryPool() {
    if (fgQueryPool_ != VK_NULL_HANDLE || !fgTimestampsOk_ || !vk_.CreateQueryPool) return;
    VkQueryPoolCreateInfo qi{}; qi.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    qi.queryType = VK_QUERY_TYPE_TIMESTAMP; qi.queryCount = MAX_FRAMES_IN_FLIGHT * 2;
    if (vk_.CreateQueryPool(device, &qi, nullptr, &fgQueryPool_) != VK_SUCCESS) {
        fgQueryPool_ = VK_NULL_HANDLE;
        fgTimestampsOk_ = false;
        RLOG_E("lsfg-native: timestamp query pool unavailable; chain cost will not be reported");
    }
}

void VulkanRendererContext::destroyFgQueryPool() {
    if (fgQueryPool_ != VK_NULL_HANDLE && vk_.DestroyQueryPool)
        vk_.DestroyQueryPool(device, fgQueryPool_, nullptr);
    fgQueryPool_ = VK_NULL_HANDLE;
    for (auto& p : fgQueryPending_) p = false;
}

void VulkanRendererContext::readFgQueryResult() {
    if (fgQueryPool_ == VK_NULL_HANDLE || !fgQueryPending_[currentFrame]) return;
    fgQueryPending_[currentFrame] = false;
    uint64_t ts[2] = {0, 0};
    const VkResult r = vk_.GetQueryPoolResults(device, fgQueryPool_, currentFrame * 2, 2,
                                               sizeof(ts), ts, sizeof(uint64_t),
                                               VK_QUERY_RESULT_64_BIT);
    if (r != VK_SUCCESS || ts[1] <= ts[0]) return;
    const double ms = (double)(ts[1] - ts[0]) * (double)fgTimestampPeriodNs_ / 1.0e6;
    const float perGen = (float)(ms / (double)std::max(1u, fgQueryGens_[currentFrame]));
    fgChainMsPerGen_ = fgChainMsPerGen_ < 0.0f
        ? perGen
        : fgChainMsPerGen_ + (perGen - fgChainMsPerGen_) * 0.1f;
    if ((fgChainLogCount_++ % 120u) == 0u)
        RLOG("lsfg-native: chain %.2f ms per generated frame (%.2f ms for %u, smoothed %.2f)",
             perGen, (float)ms, fgQueryGens_[currentFrame], fgChainMsPerGen_);
}

void VulkanRendererContext::recordFrameGenProcess(VkCommandBuffer cb) {
    if (!compositeActive() || !lsfgEngine_ || compositeTargets.empty()) return;
    const CompositeTarget& src = compositeTargets[compositeIndex];
    ensureFgQueryPool();
    if (fgQueryPool_ != VK_NULL_HANDLE && fgPlan_.generations > 0) {
        vk_.CmdResetQueryPool(cb, fgQueryPool_, currentFrame * 2, 2);
        vk_.CmdWriteTimestamp(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, fgQueryPool_, currentFrame * 2);
    }
    lsfgEngine_->process(cb, src.img, compositeW, compositeH, fgPlan_.generations);
}

void VulkanRendererContext::recordFrameGenGeneration(VkCommandBuffer cb, uint32_t g) {
    if (!compositeActive() || !lsfgEngine_ || compositeTargets.empty()) return;
    if (g >= fgPlan_.generations) return;
    const uint32_t w = compositeW, h = compositeH;
    {
        const size_t slot = (compositeIndex + 1 + g) % compositeTargets.size();
        const CompositeTarget& dst = compositeTargets[slot];
        if (dst.img == VK_NULL_HANDLE || dst.storageView == VK_NULL_HANDLE) return;

        lsfgEngine_->generateInto(cb, g, g, dst.img, dst.storageView, w, h);

        if (fgQueryPool_ != VK_NULL_HANDLE && g + 1 == fgPlan_.generations) {
            vk_.CmdWriteTimestamp(cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, fgQueryPool_, currentFrame * 2 + 1);
            fgQueryPending_[currentFrame] = true;
            fgQueryGens_[currentFrame]    = fgPlan_.generations;
        }

        const uint32_t imgIdx = fgPlan_.imgIdx[g];
        VkImageMemoryBarrier pre[2]{};
        pre[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        pre[0].oldLayout=VK_IMAGE_LAYOUT_GENERAL; pre[0].newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        pre[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        pre[0].image=dst.img; pre[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        pre[0].srcAccessMask=VK_ACCESS_SHADER_WRITE_BIT; pre[0].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;

        pre[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        pre[1].oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; pre[1].newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        pre[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        pre[1].image=swapchainImages[imgIdx]; pre[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        pre[1].srcAccessMask=0; pre[1].dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;

        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,nullptr, 0,nullptr, 2,pre);

        VkImageCopy region{};
        region.srcSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        region.dstSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        region.extent={w,h,1};
        vk_.CmdCopyImage(cb, dst.img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                         swapchainImages[imgIdx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                         1, &region);

        VkImageMemoryBarrier post[2]{};
        post[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        post[0].oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; post[0].newLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        post[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        post[0].image=swapchainImages[imgIdx]; post[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        post[0].srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; post[0].dstAccessMask=0;
        post[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        post[1].oldLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL; post[1].newLayout=VK_IMAGE_LAYOUT_GENERAL;
        post[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        post[1].image=dst.img; post[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        post[1].srcAccessMask=VK_ACCESS_TRANSFER_READ_BIT; post[1].dstAccessMask=VK_ACCESS_SHADER_WRITE_BIT;

        if (cursorDrawnPerPresent() && cursorOverlay_.draw) {
            vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0,nullptr, 0,nullptr, 1,&post[1]);
            recordCursorOverlay(cb, imgIdx);
        } else {
            vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                0, 0,nullptr, 0,nullptr, 2,post);
        }
    }
}

void VulkanRendererContext::setLsfgCachePath(const char* path) {
    std::lock_guard<std::mutex> lk(renderMutex);
    const std::string next = path ? path : "";
    // Only a genuinely NEW cache justifies throwing the engine away.
    if (next == lsfgCachePath_ && lsfgEngine_) return;
    lsfgCachePath_ = next;
    lsfgEngineTried_ = false;
    lsfgEngine_.reset();
}

void VulkanRendererContext::setFrameGenTuning(float flowScale, float refreshHz) {
    fgFlowScale_.store(flowScale, std::memory_order_relaxed);
    if (refreshHz > 1.0f) fgRefreshHz_.store(refreshHz, std::memory_order_relaxed);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
}

void VulkanRendererContext::setFrameGenTargetRate(uint32_t targetRate) {
    fgTargetRate_.store(targetRate, std::memory_order_relaxed);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
}

bool VulkanRendererContext::cursorDrawnPerPresent() const {
    return compositeActive() && cursorOverlayRenderPass != VK_NULL_HANDLE;
}

bool VulkanRendererContext::createCursorOverlayRenderPass() {
    if (cursorOverlayRenderPass != VK_NULL_HANDLE) return true;

    VkAttachmentDescription att{}; att.format=swapchainFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=VK_ATTACHMENT_LOAD_OP_LOAD;
    att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    att.finalLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;

    VkSubpassDependency dep{}; dep.srcSubpass=VK_SUBPASS_EXTERNAL; dep.dstSubpass=0;
    dep.srcStageMask=VK_PIPELINE_STAGE_TRANSFER_BIT;
    dep.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
    dep.dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=1; ci.pDependencies=&dep;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&cursorOverlayRenderPass)!=VK_SUCCESS) {
        RLOG_E("createCursorOverlayRenderPass failed");
        cursorOverlayRenderPass = VK_NULL_HANDLE;
        return false;
    }
    return true;
}

void VulkanRendererContext::recordCursorOverlay(VkCommandBuffer cb, uint32_t imgIdx) {
    if (!cursorDrawnPerPresent()) return;
    const CursorOverlay& c = cursorOverlay_;
    if (!c.draw || cursorDS == VK_NULL_HANDLE || imgIdx >= swapchainFBs.size()) return;

    VkImageMemoryBarrier toAttachment{};
    toAttachment.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    toAttachment.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toAttachment.newLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    toAttachment.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    toAttachment.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    toAttachment.image=swapchainImages[imgIdx];
    toAttachment.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    toAttachment.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
    toAttachment.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 0, 0,nullptr, 0,nullptr, 1,&toAttachment);

    VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    rpi.renderPass=cursorOverlayRenderPass; rpi.framebuffer=swapchainFBs[imgIdx];
    rpi.renderArea={{0,0},swapchainExt};
    rpi.clearValueCount=0; rpi.pClearValues=nullptr;
    vk_.CmdBeginRenderPass(cb,&rpi,VK_SUBPASS_CONTENTS_INLINE);

    VkViewport vp{0,0,(float)swapchainExt.width,(float)swapchainExt.height,0,1};
    VkRect2D   sc{{0,0},swapchainExt};
    vk_.CmdSetViewport(cb, 0, 1, &vp);
    vk_.CmdSetScissor(cb, 0, 1, &sc);
    vk_.CmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
    vk_.CmdBindDescriptorSets(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeLayout,0,1,&cursorDS,0,nullptr);

    const float cx=(float)std::max(0,(int)c.ptrX-c.hotX), cy=(float)std::max(0,(int)c.ptrY-c.hotY);
    WindowPushConstants pc{};
    pc.ndcX0=(c.ox+cx*c.sx)/c.cw*2.f-1.f;
    pc.ndcY0=(c.oy+cy*c.sy)/c.ch*2.f-1.f;
    pc.ndcX1=(c.ox+(cx+c.w)*c.sx)/c.cw*2.f-1.f;
    pc.ndcY1=(c.oy+(cy+c.h)*c.sy)/c.ch*2.f-1.f;
    pc.useTexAlpha=1;
    vk_.CmdPushConstants(cb,pipeLayout,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,
                         0,sizeof(pc),&pc);
    vk_.CmdDraw(cb,4,1,0,0);
    vk_.CmdEndRenderPass(cb);
}

void VulkanRendererContext::frameGenStats(float out[6]) const {
    out[0] = out[1] = out[2] = 0.0f;
    out[4] = -1.0f;
    out[3] = fgPresentedRate_;
    out[5] = fgChainMsPerGen_;
    if (!lsfgEngine_) return;
    out[0] = (float)lsfgEngine_->acceptedGenerations();
    out[1] = (float)fgPlan_.generations;
    out[2] = lsfgEngine_->sourceRate();
    out[4] = (float)lsfgEngine_->thermalStatus();
}

void VulkanRendererContext::trackPresentedRate(uint32_t presents) {
    const auto now = std::chrono::steady_clock::now();
    if (!fgRateWindowOpen_) {
        fgRateWindowStart_ = now;
        fgRateWindowOpen_  = true;
        fgPresentAccum_    = 0;
    }
    fgPresentAccum_ += presents;

    const float elapsed = std::chrono::duration<float>(now - fgRateWindowStart_).count();
    if (elapsed < 0.5f) return;
    const float rate = (float)fgPresentAccum_ / elapsed;
    fgPresentedRate_ = fgPresentedRate_ > 0.0f
        ? fgPresentedRate_ + (rate - fgPresentedRate_) * 0.25f
        : rate;
    fgRateWindowStart_ = now;
    fgPresentAccum_    = 0;
}

void VulkanRendererContext::setFrameGenArmed(bool armed, int multiplier) {
    const bool was = fgArmed_.load(std::memory_order_relaxed);
    const int  wasMult = fgMultiplier_.load(std::memory_order_relaxed);
    fgMultiplier_.store(multiplier, std::memory_order_relaxed);
    if (wasMult != multiplier) {
        fgConfigDirty_.store(true, std::memory_order_relaxed);
        RLOG("lsfg-native: multiplier %d -> %d (armed=%d)", wasMult, multiplier, (int)armed);
    }
    if (was == armed) return;

    fgArmed_.store(armed, std::memory_order_relaxed);
    RLOG("lsfg-native: frame gen %s (multiplier=%d) - recreating swapchain",
         armed ? "ARMED" : "disarmed", multiplier);
    fbResized.store(true);
    dirtyCV.notify_one();
}

bool VulkanRendererContext::compositeActive() const {
    if (!fgArmed_.load(std::memory_order_relaxed)) return false;
    if (!lsfgCaps_.supported()) return false;
    if (scanoutActive.load()) return false;   // direct scanout bypasses the compositor entirely
    return compositeArmed;
}

VkRenderPass VulkanRendererContext::targetRenderPass() const {
    return compositeActive() ? compositeRenderPass : renderPass;
}

VkFramebuffer VulkanRendererContext::targetFramebuffer(uint32_t imgIdx) const {
    if (compositeActive() && compositeIndex < compositeTargets.size())
        return compositeTargets[compositeIndex].fb;
    return swapchainFBs[imgIdx];
}

void VulkanRendererContext::copyCompositeToSwapchain(VkCommandBuffer cb, uint32_t imgIdx) {
    if (!compositeActive() || compositeIndex >= compositeTargets.size()) return;
    const CompositeTarget& t = compositeTargets[compositeIndex];

    VkImageMemoryBarrier pre[2]{};
    pre[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    pre[0].oldLayout=VK_IMAGE_LAYOUT_GENERAL; pre[0].newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    pre[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    pre[0].image=t.img; pre[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    pre[0].srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT|VK_ACCESS_SHADER_WRITE_BIT;
    pre[0].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;

    pre[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    pre[1].oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; pre[1].newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    pre[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    pre[1].image=swapchainImages[imgIdx]; pre[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    pre[1].srcAccessMask=0; pre[1].dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;

    vk_.CmdPipelineBarrier(cb,
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,nullptr, 0,nullptr, 2,pre);

    VkImageCopy region{};
    region.srcSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
    region.dstSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
    region.extent={compositeW, compositeH, 1};
    vk_.CmdCopyImage(cb,
        t.img, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        swapchainImages[imgIdx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        1, &region);

    if (cursorDrawnPerPresent() && cursorOverlay_.draw) {
        recordCursorOverlay(cb, imgIdx);
        return;
    }

    VkImageMemoryBarrier post{};
    post.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    post.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; post.newLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    post.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    post.image=swapchainImages[imgIdx]; post.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    post.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; post.dstAccessMask=0;
    vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0,nullptr, 0,nullptr, 1,&post);
}

void VulkanRendererContext::recreateSyncObjects() {
    vk_.DeviceWaitIdle(device);
    for (size_t i = 0; i < imgAvailSems.size(); i++)
        if (imgAvailSems[i] != VK_NULL_HANDLE) vk_.DestroySemaphore(device, imgAvailSems[i], nullptr);
    for (size_t i = 0; i < renderDoneSems.size(); i++)
        if (renderDoneSems[i] != VK_NULL_HANDLE) vk_.DestroySemaphore(device, renderDoneSems[i], nullptr);

    const uint32_t semCount = MAX_FRAMES_IN_FLIGHT * kMaxPresentsPerFrame;
    imgAvailSems.assign(semCount, VK_NULL_HANDLE);
    renderDoneSems.assign(semCount, VK_NULL_HANDLE);

    VkSemaphoreCreateInfo si{}; si.sType=VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    for (uint32_t i = 0; i < semCount; i++) {
        if (vk_.CreateSemaphore(device,&si,nullptr,&imgAvailSems[i])!=VK_SUCCESS ||
            vk_.CreateSemaphore(device,&si,nullptr,&renderDoneSems[i])!=VK_SUCCESS)
            throw std::runtime_error("sync");
    }
    currentFrame = 0;
}

#pragma GCC diagnostic pop
