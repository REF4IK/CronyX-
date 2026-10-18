#pragma once
#include <vulkan/vulkan.h>
#include <list>
#include <vulkan/vulkan_android.h>
struct VkTable {

    PFN_vkCreateInstance CreateInstance;

    PFN_vkDestroyInstance DestroyInstance;
    PFN_vkEnumeratePhysicalDevices EnumeratePhysicalDevices;
    PFN_vkGetPhysicalDeviceProperties GetPhysicalDeviceProperties;
    PFN_vkGetPhysicalDeviceMemoryProperties GetPhysicalDeviceMemoryProperties;
    PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR GetPhysicalDeviceSurfaceCapabilitiesKHR;
    PFN_vkGetPhysicalDeviceSurfaceFormatsKHR GetPhysicalDeviceSurfaceFormatsKHR;
    PFN_vkGetPhysicalDeviceSurfacePresentModesKHR GetPhysicalDeviceSurfacePresentModesKHR;
    PFN_vkGetPhysicalDeviceQueueFamilyProperties GetPhysicalDeviceQueueFamilyProperties;
    PFN_vkGetPhysicalDeviceSurfaceSupportKHR GetPhysicalDeviceSurfaceSupportKHR;
    PFN_vkGetPhysicalDeviceFormatProperties GetPhysicalDeviceFormatProperties;
    PFN_vkGetPhysicalDeviceFeatures2 GetPhysicalDeviceFeatures2;
    PFN_vkCreateDevice CreateDevice;
    PFN_vkDestroySurfaceKHR DestroySurfaceKHR;
    PFN_vkCreateAndroidSurfaceKHR CreateAndroidSurfaceKHR;

    PFN_vkGetDeviceProcAddr GetDeviceProcAddr;
    PFN_vkDestroyDevice DestroyDevice;
    PFN_vkGetDeviceQueue GetDeviceQueue;
    PFN_vkDeviceWaitIdle DeviceWaitIdle;
    PFN_vkQueueWaitIdle QueueWaitIdle;
    PFN_vkCreateSwapchainKHR CreateSwapchainKHR;
    PFN_vkDestroySwapchainKHR DestroySwapchainKHR;
    PFN_vkGetSwapchainImagesKHR GetSwapchainImagesKHR;
    PFN_vkAcquireNextImageKHR AcquireNextImageKHR;
    PFN_vkQueuePresentKHR QueuePresentKHR;
    PFN_vkQueueSubmit QueueSubmit;
    PFN_vkCreateQueryPool CreateQueryPool;
    PFN_vkDestroyQueryPool DestroyQueryPool;
    PFN_vkCmdResetQueryPool CmdResetQueryPool;
    PFN_vkCmdWriteTimestamp CmdWriteTimestamp;
    PFN_vkGetQueryPoolResults GetQueryPoolResults;
    PFN_vkCreateRenderPass CreateRenderPass;
    PFN_vkDestroyRenderPass DestroyRenderPass;
    PFN_vkCreateFramebuffer CreateFramebuffer;
    PFN_vkDestroyFramebuffer DestroyFramebuffer;
    PFN_vkCreateImageView CreateImageView;
    PFN_vkDestroyImageView DestroyImageView;
    PFN_vkCreateImage CreateImage;
    PFN_vkDestroyImage DestroyImage;
    PFN_vkCreateBuffer CreateBuffer;
    PFN_vkDestroyBuffer DestroyBuffer;
    PFN_vkAllocateMemory AllocateMemory;
    PFN_vkFreeMemory FreeMemory;
    PFN_vkMapMemory MapMemory;
    PFN_vkFlushMappedMemoryRanges FlushMappedMemoryRanges;
    PFN_vkBindBufferMemory BindBufferMemory;
    PFN_vkBindImageMemory BindImageMemory;
    PFN_vkGetBufferMemoryRequirements GetBufferMemoryRequirements;
    PFN_vkGetImageMemoryRequirements GetImageMemoryRequirements;
    PFN_vkCreateDescriptorSetLayout CreateDescriptorSetLayout;
    PFN_vkDestroyDescriptorSetLayout DestroyDescriptorSetLayout;
    PFN_vkCreateDescriptorPool CreateDescriptorPool;
    PFN_vkDestroyDescriptorPool DestroyDescriptorPool;
    PFN_vkAllocateDescriptorSets AllocateDescriptorSets;
    PFN_vkFreeDescriptorSets FreeDescriptorSets;
    PFN_vkUpdateDescriptorSets UpdateDescriptorSets;
    PFN_vkCreatePipelineLayout CreatePipelineLayout;
    PFN_vkDestroyPipelineLayout DestroyPipelineLayout;
    PFN_vkCreateShaderModule CreateShaderModule;
    PFN_vkDestroyShaderModule DestroyShaderModule;
    PFN_vkCreateGraphicsPipelines CreateGraphicsPipelines;
    PFN_vkDestroyPipeline DestroyPipeline;
    PFN_vkCreateCommandPool CreateCommandPool;
    PFN_vkDestroyCommandPool DestroyCommandPool;
    PFN_vkAllocateCommandBuffers AllocateCommandBuffers;
    PFN_vkFreeCommandBuffers FreeCommandBuffers;
    PFN_vkBeginCommandBuffer BeginCommandBuffer;
    PFN_vkEndCommandBuffer EndCommandBuffer;
    PFN_vkResetCommandBuffer ResetCommandBuffer;
    PFN_vkCmdBeginRenderPass CmdBeginRenderPass;
    PFN_vkCmdEndRenderPass CmdEndRenderPass;
    PFN_vkCmdBindPipeline CmdBindPipeline;
    PFN_vkCmdBindDescriptorSets CmdBindDescriptorSets;
    PFN_vkCmdDraw CmdDraw;
    PFN_vkCmdPushConstants CmdPushConstants;
    PFN_vkCmdSetViewport CmdSetViewport;
    PFN_vkCmdSetScissor CmdSetScissor;
    PFN_vkCmdPipelineBarrier CmdPipelineBarrier;
    PFN_vkCmdCopyImage CmdCopyImage;
    PFN_vkCmdBlitImage CmdBlitImage;
    // Compute: the native LSFG chain is 25 compute dispatches.
    PFN_vkCmdDispatch CmdDispatch;
    PFN_vkCreateComputePipelines CreateComputePipelines;
    PFN_vkUnmapMemory UnmapMemory;
    PFN_vkCmdCopyBufferToImage CmdCopyBufferToImage;
    PFN_vkCreateSampler CreateSampler;
    PFN_vkDestroySampler DestroySampler;
    PFN_vkCreateSemaphore CreateSemaphore;
    PFN_vkDestroySemaphore DestroySemaphore;
    PFN_vkCreateFence CreateFence;
    PFN_vkDestroyFence DestroyFence;
    PFN_vkWaitForFences WaitForFences;
    PFN_vkResetFences ResetFences;
    PFN_vkGetFenceStatus GetFenceStatus;

    PFN_vkGetAndroidHardwareBufferPropertiesANDROID GetAndroidHardwareBufferPropertiesANDROID;
};

#include <android/log.h>
#include <string>
#define WLOG_TAG "Winlator_Renderer"
#define RLOG(...) if(verboseLog) __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,__VA_ARGS__)
#define RLOG_E(...) __android_log_print(ANDROID_LOG_ERROR,WLOG_TAG,__VA_ARGS__)
#define SCANOUT_LOG(...) __android_log_print(ANDROID_LOG_DEBUG,"Winlator_Scanout",__VA_ARGS__)

#include <vulkan/vulkan_android.h>
#include <android/hardware_buffer.h>
#include <android/native_window.h>
#include <vector>
#include <unordered_map>
#include <thread>
#include <atomic>
#include <mutex>
#include <shared_mutex>
#include <condition_variable>
#include <chrono>
#include <memory>

// Native (compositor-side) LSFG frame generation — capability gate + engine.
#include "lsfg/lsfg_probe.h"
namespace lsfg { class Engine; }

static constexpr uint32_t MAX_FRAMES_IN_FLIGHT = 2;

struct WindowPushConstants { float ndcX0, ndcY0, ndcX1, ndcY1; int useTexAlpha; };

struct EffectPushConstants {
    float resolutionX, resolutionY;
    float params[8];
};

enum EffectType : int32_t {
    EFFECT_NONE = 0,
    EFFECT_COLOR,
    EFFECT_FXAA,
    EFFECT_CRT,
    EFFECT_TOON,
    EFFECT_VIGNETTE,
    EFFECT_SEPIA,
    EFFECT_BLUR,
    EFFECT_PIXELATE,
    EFFECT_GRAYSCALE,
    EFFECT_SHARPEN,
    EFFECT_SMOOTH,
    EFFECT_HDR,
    EFFECT_NTSC,
    EFFECT_COUNT
};

struct EffectEntry {
    EffectType type;
    float params[8] = {};
};

class VulkanRendererContext {
public:
    VulkanRendererContext(ANativeWindow* window, int cWidth, int cHeight, void* adrenotoolsHandle = nullptr);
    ~VulkanRendererContext();

    void onSurfaceResized(int width, int height);
    void setTransform(float ox, float oy, float sx, float sy);
    void updatePointerPosition(short x, short y);
    void updateWindowContent(int64_t id, void* pixels, short w, short h, short stride, int x, int y);
    void updateWindowContentAHB(int64_t id, AHardwareBuffer* ahb, short w, short h, int x, int y);
    void updateCursorImage(void* pixels, short w, short h, short hotX, short hotY);
    void setCursorVisible(bool visible);
    void setRenderList(const int64_t* ids, const int* xs, const int* ys, int count);
    void removeWindow(int64_t id);
    void clearBackbuffer() {}
    void beginBatch() {}
    void endBatch() {}
    void initScanout();
    void destroyScanout();
    void applyScanoutBuffer();
    void initScanoutFromWindows(ANativeWindow* gameWin, ANativeWindow* cursorWin);
    void scanoutSetDst(int x, int y, int w, int h);
    void scanoutSetBuffer(AHardwareBuffer* ahb, int x, int y, int w, int h, int fenceFd = -1);
    void scanoutSetCursorImage(void* pixels, short w, short h, short stride);
    void scanoutSetCursorPos(short x, short y, short hotX, short hotY);
    std::atomic<bool> scanoutActive{false};
    std::atomic<bool> scanoutDisabled{false};
    std::atomic<bool> gameFrameDelivered{false};
    std::atomic<bool> surfaceDetached{false};

    void detachSurface();
    bool reattachSurface(ANativeWindow* newWindow);

    bool verboseLog = true;
    void setVerboseLog(bool v) { verboseLog = v; }
    void dumpRendererInfo();

    std::string adrenoDriverPath;
    std::string adrenoDriverName;
    std::string adrenoNativeLibDir;
    void* vulkanHandle = nullptr;
    std::atomic<bool> scanoutBlackFrameDone{false};
    PFN_vkGetInstanceProcAddr gipa = nullptr;
    VkTable vk_ = {};
    void loadCustomDriver();
    void loadInstanceDispatch();
    void loadDeviceDispatch();

    void setFilterMode(int mode);
    void setSwapRB(bool enabled);
    void setPresentMode(VkPresentModeKHR mode);
    std::vector<int> getSupportedPresentModes() const;

    // --- Native LSFG frame generation (host-side, ported from Bannerlator
    // lsfg-native) ---------------------------------------------------------
    // Capability verdict for the compositor-side LSFG engine. Filled at device
    // creation and completed once the swapchain format is known; read by the
    // UI (through JNI) to grey the engine out with a reason.
    const lsfg::Caps& lsfgCaps() const { return lsfgCaps_; }

    // Arm/disarm native LSFG frame generation for this session. Changing the
    // armed state recreates the swapchain, because the composite path needs
    // TRANSFER_DST usage and a deeper image queue that a normal session does
    // not pay for.
    void setFrameGenArmed(bool armed, int multiplier);
    bool frameGenArmed() const { return fgArmed_.load(std::memory_order_relaxed); }
    // Live frame-gen telemetry for the in-game readout:
    //   [0] generations the governor currently trusts
    //   [1] generations actually planned for the last source frame
    //   [2] measured source (real) frames per second
    //   [3] measured presented frames per second
    //   [4] thermal status, -1 when the device gives no signal
    //   [5] GPU milliseconds the chain spends per generated frame, -1 unknown
    void frameGenStats(float out[6]) const;
    // Path to the SPIR-V cache built from the user's Lossless.dll. Setting it
    // drops any existing engine so the next armed frame rebuilds from it.
    void setLsfgCachePath(const char* path);
    // Flow scale (0.25-1.0) and the panel's real refresh rate. The pacer never
    // generates above the refresh rate.
    void setFrameGenTuning(float flowScale, float refreshHz);
    // Adaptive output-FPS target (0 = fixed multiplier). Fed to the pacer,
    // which varies generations per source frame to hit min(target, refresh).
    void setFrameGenTargetRate(uint32_t targetRate);

    // Effect composer
    void setEffects(const EffectEntry* entries, int count);
    void clearEffects();
    bool hasEffects() const;

private:
    struct WinTex {
        VkImage              img            = VK_NULL_HANDLE;
        VkDeviceMemory       mem            = VK_NULL_HANDLE;
        VkImageView          view           = VK_NULL_HANDLE;
        VkDescriptorSet      ds             = VK_NULL_HANDLE;
        VkBuffer             stg            = VK_NULL_HANDLE;
        VkDeviceMemory       stgMem         = VK_NULL_HANDLE;
        void*                mapped         = nullptr;
        VkDeviceSize         cap            = 0;
        int                  w              = 0;
        int                  h              = 0;
        bool                 dirty          = false;
        bool                 isAHB          = false;
        bool                 needsTransition = false;
        AHardwareBuffer*     ahb            = nullptr;
    };

    struct RenderEntry { int64_t id; int x, y; };
    struct DrawEntry {
        VkImage         img            = VK_NULL_HANDLE;
        VkDescriptorSet ds             = VK_NULL_HANDLE;
        VkBuffer        upload         = VK_NULL_HANDLE;
        int             x=0, y=0, w=0, h=0;
        bool            needsTransition = false;
        bool            isAHB          = false;
    };

    ANativeWindow* window;
    int surfaceWidth, surfaceHeight, containerWidth, containerHeight;
    void* adrenotoolsHandle = nullptr;
    int filterMode = 0;
    bool swapRB = false;
    float maxAnisotropy           = 1.0f;
    bool  cubicSupported          = false;
    VkPhysicalDeviceMemoryProperties memProperties{};
    VkPresentModeKHR requestedPresentMode = VK_PRESENT_MODE_FIFO_KHR;
    uint32_t graphicsQueueFamilyIndex = 0;
    std::vector<VkPresentModeKHR> availablePresentModes;

    std::unordered_map<int64_t, WinTex>         texMap;
    std::unordered_map<int64_t, std::atomic<bool>> dirtyFlags;

    std::unordered_map<AHardwareBuffer*, WinTex>              ahbImportCache;
    std::unordered_map<int64_t, std::vector<AHardwareBuffer*>> windowAhbs;

    std::vector<WinTex>    deleteQueue;
    std::vector<WinTex>    pendingDelete[MAX_FRAMES_IN_FLIGHT];
    std::vector<RenderEntry> renderList;
    std::vector<RenderEntry> pendingRenderList;
    std::atomic<bool>      renderListDirty{false};

    std::vector<DrawEntry>             frameDraws;
    std::vector<VkImageMemoryBarrier>  frameAhbTransitions;
    std::vector<VkImageMemoryBarrier>  framePreUpload;
    std::vector<VkImageMemoryBarrier>  framePostUpload;

    void*  scanoutGameSC      = nullptr;
    void*  scanoutCursorSC    = nullptr;
    void*  scanoutCursorBuf   = nullptr;
    int32_t scanoutCursorBufW = 0;
    int32_t scanoutCursorBufH = 0;

    void*  scanoutTx          = nullptr;
    void*  scanoutGameTx      = nullptr;

    ARect  scanoutLastSrc{}, scanoutLastDst{};
    bool   scanoutGeoDirty    = true;
    bool   scanoutVisShown    = false;
    bool   scanoutApiLoaded   = false;
    void*  fnSCCreateFromWin  = nullptr;
    void*  fnSCRelease        = nullptr;
    void*  fnSTCreate         = nullptr;
    void*  fnSTDelete         = nullptr;
    void*  fnSTApply          = nullptr;
    void*  fnSTSetBuffer      = nullptr;
    void*  fnSTSetZOrder      = nullptr;
    void*  fnSTSetVisibility  = nullptr;
    void*  fnSTSetGeometry    = nullptr;
    void*  fnSTSetBackPressure = nullptr;
    bool   loadScanoutApi();

    int32_t scanoutDstX=0, scanoutDstY=0, scanoutDstW=0, scanoutDstH=0;

    int32_t lastDstX=0, lastDstY=0, lastDstW=0, lastDstH=0;
    bool    gameScVisible      = false;

    struct ScanoutPending { AHardwareBuffer* ahb=nullptr; int x=0,y=0,w=0,h=0; int fenceFd=-1; };
    std::mutex        scanoutMutex;
    ScanoutPending    scanoutPending{};
    std::atomic<bool> scanoutPendingDirty{false};

    short  pendingCursorX=0, pendingCursorY=0, pendingCursorHotX=0, pendingCursorHotY=0;
    bool   cursorPosDirty=false;
    bool   cursorImageDirty=false;

    std::atomic<int>  pointerX{0}, pointerY{0};
    float sceneOffsetX=0.f, sceneOffsetY=0.f, sceneScaleX=1.f, sceneScaleY=1.f;

    std::atomic<bool> cursorVisible{false};
    short  cursorHotX=0, cursorHotY=0, cursorTexW=0, cursorTexH=0;
    std::vector<uint32_t>  cursorPixels;
    std::atomic<bool> isCursorImageDirty{false};
    std::atomic<bool> cursorMoved{false};

    VkImage         cursorImg   = VK_NULL_HANDLE;
    VkDeviceMemory  cursorMem   = VK_NULL_HANDLE;
    VkImageView     cursorView  = VK_NULL_HANDLE;
    VkDescriptorSet  cursorDS   = VK_NULL_HANDLE;
    VkBuffer         cursorStg  = VK_NULL_HANDLE;
    VkDeviceMemory   cursorStgM = VK_NULL_HANDLE;
    void*            cursorStgP = nullptr;
    VkDeviceSize     cursorStgC = 0;
    VkDeviceSize     cursorUploadSize = 0;

    VkInstance       instance;
    VkSurfaceKHR     surface;
    VkPhysicalDevice physicalDevice;
    VkDevice         device;
    VkQueue          graphicsQueue;
    VkSwapchainKHR   swapchain   = VK_NULL_HANDLE;
    VkFormat         swapchainFmt;
    VkExtent2D       swapchainExt;

    std::vector<VkImage>       swapchainImages;
    std::vector<VkImageView>   swapchainViews;
    std::vector<VkFramebuffer> swapchainFBs;

    VkRenderPass          renderPass  = VK_NULL_HANDLE;
    VkDescriptorSetLayout dsLayout    = VK_NULL_HANDLE;
    VkPipelineLayout      pipeLayout  = VK_NULL_HANDLE;

    VkPipeline            pipeline    = VK_NULL_HANDLE;

    VkCommandPool                cmdPool = VK_NULL_HANDLE;
    std::vector<VkCommandBuffer> cmdBufs;

    std::vector<VkSemaphore> imgAvailSems;
    std::vector<VkSemaphore> renderDoneSems;
    std::vector<VkFence>     inFlightFences;
    std::vector<VkFence>     imgInFlight;
    uint32_t                 currentFrame = 0;

    VkSampler        sampler    = VK_NULL_HANDLE;
    VkDescriptorPool winTexPool = VK_NULL_HANDLE;

    std::atomic<bool> needsRender{false};
    std::thread       renderThread;
    std::atomic<bool> isRunning{false};
    std::atomic<bool> fbResized{false};
    std::mutex        renderMutex;
    std::mutex        dirtyMutex;
    std::condition_variable dirtyCV;
    std::shared_mutex frameMutex;

    void createInstance();
    void createSurface();
    void pickPhysicalDevice();
    void createLogicalDevice();
    void createSwapchain();
    void createRenderPass();
    void createDSLayout();
    void createPipeline(bool blend, VkPipeline& out);
    void createFramebuffers();
    void createCmdPool();
    void createSampler();
    void createWinTexPool();
    void createCursorPipeline();
    void createCursorDS();
    void createCmdBufs();
    void createSyncObjects();
    void cleanupSwapchain();

    bool  createWinTexResources(WinTex& wt, int w, int h);
    bool  importAHBToWinTex(WinTex& wt, AHardwareBuffer* ahb);
    void  cleanupAllAHBCache();
    void  flushDeleteQueue();
    void  destroyWinTex(WinTex& wt);
    void  ensureCursorTex(short w, short h);
    void  cleanupCursorTex();
    void  ensureCursorStaging(VkDeviceSize sz);

    void recordCmdBuf(VkCommandBuffer cb, uint32_t imgIdx,
        const std::vector<DrawEntry>& draws,
        std::vector<VkImageMemoryBarrier>& ahbTransitions,
        std::vector<VkImageMemoryBarrier>& preUpload,
        std::vector<VkImageMemoryBarrier>& postUpload,
        VkBuffer cursorUpload, bool hasCursorUpload,
        float ox, float oy, float sx, float sy, float cw, float ch,
        short ptrX, short ptrY, short curHotX, short curHotY,
        short curW, short curH, bool curVis);
    void renderLoop();
    void renderFrame();

    uint32_t        findMemType(uint32_t filter, VkMemoryPropertyFlags props);
    void            createBuffer(VkDeviceSize sz, VkBufferUsageFlags usage,
                                 VkMemoryPropertyFlags props, VkBuffer& buf, VkDeviceMemory& mem);
    VkCommandBuffer beginOneTime();
    void            endOneTime(VkCommandBuffer cmd);
    void            transition(VkCommandBuffer cmd, VkImage img,
                               VkImageLayout oldL, VkImageLayout newL,
                               VkAccessFlags srcA, VkAccessFlags dstA,
                               VkPipelineStageFlags srcS, VkPipelineStageFlags dstS);
    VkShaderModule  makeShader(const uint32_t* code, size_t sz);

    // --- Effect post-processing ---
    std::vector<EffectEntry> activeEffects;

    // === Native LSFG: composite target ring =================================
    // Frame generation cannot composite straight into a swapchain image: the
    // finished frame has to be READABLE (it becomes the next frame's LSFG
    // input) and generated frames have to be STORAGE-WRITABLE by a compute
    // dispatch. So when frame gen is armed the recording is redirected at a
    // composite image we own — format-identical to the swapchain — and a copy
    // moves it into the acquired swapchain image at the end. With frame gen
    // off, none of these objects is created and the direct path is untouched.
    struct CompositeTarget {
        VkImage         img         = VK_NULL_HANDLE;
        VkDeviceMemory  mem         = VK_NULL_HANDLE;
        VkImageView     view        = VK_NULL_HANDLE;  // colour attachment + sampled
        VkImageView     storageView = VK_NULL_HANDLE;  // compute writes (generate)
        VkFramebuffer   fb          = VK_NULL_HANDLE;
        VkDescriptorSet ds          = VK_NULL_HANDLE;  // sampled, for later passes
        bool            fresh       = true;            // never transitioned out of UNDEFINED
    };
    // Hard ceiling: (max generations + 1) presentable frames per source frame,
    // times a queue depth of 2, capped so the footprint stays bounded.
    static constexpr uint32_t kMaxCompositeTargets = 7;

    std::vector<CompositeTarget> compositeTargets;
    VkRenderPass compositeRenderPass = VK_NULL_HANDLE;  // CLEAR -> GENERAL
    uint32_t     compositeW = 0, compositeH = 0;
    uint32_t     compositeIndex = 0;      // rotates per composite; gives history for free
    bool         compositeArmed = false;  // targets exist AND this frame uses them
    bool         swapchainTransferDst = false; // swapchain was created with TRANSFER_DST

    // Set from the app when the native LSFG engine is selected for this
    // session. Read on the render thread; false keeps every path as it was.
    std::atomic<bool> fgArmed_{false};
    std::atomic<int>  fgMultiplier_{0};
    std::atomic<float> fgFlowScale_{1.0f};
    std::atomic<float> fgRefreshHz_{0.0f};
    std::atomic<bool>  fgConfigDirty_{true};
    std::atomic<uint32_t> fgTargetRate_{0};

    bool  createCompositeRenderPass();
    bool  ensureCompositeTargets(uint32_t w, uint32_t h, uint32_t count);
    void  destroyCompositeTargets();
    // True when this frame should composite off-swapchain. Call on the render
    // thread; every gate must hold or we fall back to the untouched path.
    bool  compositeActive() const;
    VkRenderPass  targetRenderPass() const;
    VkFramebuffer targetFramebuffer(uint32_t imgIdx) const;
    void  copyCompositeToSwapchain(VkCommandBuffer cb, uint32_t imgIdx);

    // === Native LSFG: the software cursor ===================================
    // LSFG interpolates whatever it is given, so a cursor composited into the
    // frame gets warped along the flow field and smears. It is therefore
    // excluded from the composite while frame gen is armed and drawn once into
    // EVERY presented image instead - real and generated alike.
    VkRenderPass cursorOverlayRenderPass = VK_NULL_HANDLE;
    struct CursorOverlay {
        bool  draw = false;
        float ox = 0, oy = 0, sx = 0, sy = 0, cw = 0, ch = 0;
        short ptrX = 0, ptrY = 0, hotX = 0, hotY = 0, w = 0, h = 0;
    };
    CursorOverlay cursorOverlay_{};

    bool createCursorOverlayRenderPass();
    void recordCursorOverlay(VkCommandBuffer cb, uint32_t imgIdx);
    bool cursorDrawnPerPresent() const;

    // === Native LSFG: the per-source-frame present plan =====================
    static constexpr uint32_t kMaxPresentsPerFrame = 4;   // 1 real + up to 3 generated
    struct FrameGenPlan {
        uint32_t generations = 0;
        uint32_t presents    = 1;
        uint32_t imgIdx[kMaxPresentsPerFrame] = {};
    };
    FrameGenPlan fgPlan_{};
    std::unique_ptr<lsfg::Engine> lsfgEngine_;
    uint64_t    fgSourceFrames_ = 0;
    std::string lsfgCachePath_;
    bool        lsfgEngineTried_ = false;
    // Identity (size+mtime) of the cache file at the last engine attempt.
    // A settled-but-changed file clears the fail-once latch, so an
    // arm-before-build ordering heals itself once the background build lands.
    std::string lsfgCacheIdentity_;

    uint32_t syncSlot(uint32_t k) const { return currentFrame * kMaxPresentsPerFrame + k; }
    void recreateSyncObjects();
    uint32_t fgAcquireFailLog_ = 0;

    float    fgPresentedRate_   = 0.0f;
    uint32_t fgPresentAccum_    = 0;
    std::chrono::steady_clock::time_point fgRateWindowStart_{};
    bool     fgRateWindowOpen_  = false;
    void     trackPresentedRate(uint32_t presents);

    bool ensureLsfgEngine();
    uint32_t cmdSlot(uint32_t k) const { return currentFrame * kMaxPresentsPerFrame + k; }
    void recordFrameGenProcess(VkCommandBuffer cb);
    void recordFrameGenGeneration(VkCommandBuffer cb, uint32_t g);

    VkQueryPool fgQueryPool_        = VK_NULL_HANDLE;
    bool        fgTimestampsOk_     = false;
    float       fgTimestampPeriodNs_ = 0.0f;
    bool        fgQueryPending_[MAX_FRAMES_IN_FLIGHT] = {};
    uint32_t    fgQueryGens_[MAX_FRAMES_IN_FLIGHT]    = {};
    float       fgChainMsPerGen_    = -1.0f;
    uint32_t    fgChainLogCount_    = 0;
    void ensureFgQueryPool();
    void destroyFgQueryPool();
    void readFgQueryResult();

    lsfg::Caps lsfgCaps_;

    struct EffectOffscreen {
        VkImage         img     = VK_NULL_HANDLE;
        VkDeviceMemory  mem     = VK_NULL_HANDLE;
        VkImageView     view    = VK_NULL_HANDLE;
        VkFramebuffer   fb      = VK_NULL_HANDLE;
        VkDescriptorSet ds      = VK_NULL_HANDLE;
    };

    EffectOffscreen  effectReadBuf;
    EffectOffscreen  effectWriteBuf;
    // Full-res effect path: буферы в размере swapchain (как GL в разрешении
    // экрана), а не контейнера — иначе финальный апскейл съедает резкость.
    int effectW = 0, effectH = 0;
    // Вызывать только под renderMutex. Создает/пересоздает буферы под
    // текущий swapchainExt.
    void ensureEffectBuffersLocked();
    VkRenderPass     effectRenderPass  = VK_NULL_HANDLE;
    VkPipelineLayout effectPipeLayout  = VK_NULL_HANDLE;
    VkPipeline       effectPipelines[EFFECT_COUNT] = {};
    VkDescriptorSetLayout effectDSLayout = VK_NULL_HANDLE;
    VkSampler        effectSampler     = VK_NULL_HANDLE;

    void createEffectResources();
    void destroyEffectResources();
    void createEffectRenderPass();
    void createEffectDSLayout();
    void createEffectPipelineLayout();
    void createEffectPipelines();
    void createEffectOffscreen(EffectOffscreen& off, int w, int h);
    void destroyEffectOffscreen(EffectOffscreen& off);
};
