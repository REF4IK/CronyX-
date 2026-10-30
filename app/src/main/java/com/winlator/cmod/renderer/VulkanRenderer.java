package com.winlator.cmod.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Surface;
import android.widget.Toast;

import com.winlator.cmod.R;
import com.winlator.cmod.widget.FrameRating;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.xserver.Bitmask;
import com.winlator.cmod.xserver.Cursor;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowAttributes;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.util.ArrayList;

public class VulkanRenderer implements XServerRenderer, HostRenderer,
                                       WindowManager.OnWindowModificationListener,
                                       Pointer.OnPointerMotionListener {

    static { System.loadLibrary("vulkan_renderer"); }

    public final XServerView xServerView;
    private final XServer xServer;
    private long nativeHandle = 0;
    private final Object lock = new Object();

    public final ViewTransformation viewTransformation = new ViewTransformation();
    private boolean fullscreen = false;
    private float magnifierZoom = 1.0f;
    private boolean screenOffsetYRelativeToCursor = false;
    public int surfaceWidth;
    public int surfaceHeight;
    private String[] unviewableWMClasses = null;
    private boolean cursorVisible = false;
    private boolean nativeMode = false;
    private String driverPath = null;
    private java.util.concurrent.ExecutorService initExecutor = null;
    private volatile boolean initComplete = false;
    private String driverLibraryName = null;
    private String nativeLibDir = null;
    private Drawable rootCursorDrawable;
    private Cursor lastCursor = null;
    private boolean xRenderingPausedForScanout = false;
    private int[] pendingEffectTypes = null;
    private float[][] pendingEffectParams = null;
    private boolean scanoutBlockedForEffects = false;

    private volatile ArrayList<RenderableWindow> renderableWindows = new ArrayList<>();
    private android.view.SurfaceControl scanoutGameSC;
    private android.view.SurfaceControl scanoutCursorSC;
    private android.view.Surface        scanoutGameSurface;
    private android.view.Surface        scanoutCursorSurface;

    public VulkanRenderer(XServerView xServerView, XServer xServer) {
        this.xServerView = xServerView;
        this.xServer = xServer;
        rootCursorDrawable = createRootCursorDrawable();
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
    }

    private Drawable createRootCursorDrawable() {
        try {
            Context context = xServerView.getContext();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.cursor, options);
            return Drawable.fromBitmap(bitmap);
        } catch (Exception e) { return null; }
    }

    private native long nativeInit(Surface surface, int screenWidth, int screenHeight, String driverPath, String libraryName, String nativeLibDir);
    private native void nativeResize(long handle, int width, int height);
    private native void nativeDestroy(long handle);
    private native void nativeUpdateWindowContent(long handle, long id, java.nio.ByteBuffer pixels,
        short width, short height, short stride, int x, int y);
    private native void nativeUpdateWindowContentAHB(long handle, long id, long ahbPtr,
        short width, short height, int x, int y);
    private native void nativeSetTransform(long handle, float ox, float oy, float sx, float sy);
    private native void nativeSetPointerPos(long handle, short x, short y);
    private native void nativeSetCursorVisible(long handle, boolean visible);
    private native void nativeUpdateCursorImage(long handle, java.nio.ByteBuffer pixels,
        short width, short height, short hotX, short hotY);
    private native void nativeSetRenderList(long handle, long[] ids, int[] xs, int[] ys, int count);
    private native void nativeRemoveWindow(long handle, long id);

    private native void nativeInitScanout(long handle);
    private native void nativeDetachSurface(long handle);
    private native boolean nativeReattachSurface(long handle, android.view.Surface surface);
    private native void nativeDestroyScanout(long handle);
    private native void nativeSetScanoutDisabled(long handle, boolean disabled);
    private native void nativeScanoutSetBuffer(long handle, long ahbPtr, int x, int y, int w, int h, int fenceFd);
    private native void nativeScanoutSetCursorImage(long handle, java.nio.ByteBuffer pixels, short w, short h, short stride);
    private native void nativeScanoutSetCursorPos(long handle, short x, short y, short hotX, short hotY);
    private native boolean nativeIsScanoutActive(long handle);
    private native boolean nativeIsGameFrameDelivered(long handle);
    private native void nativeSetScanoutWindow(long handle, android.view.Surface game, android.view.Surface cursor);
    private native void nativeScanoutSetDst(long handle, int x, int y, int w, int h);
    private native void nativeSetVerboseLog(long handle, boolean v);
    private native void nativeDumpRendererInfo(long handle);
    private native void nativeSetFilterMode(long handle, int mode);
    private native void nativeSetSwapRB(long handle, boolean enabled);
    private native void nativeSetPresentMode(long handle, int mode);
    private native int[] nativeGetSupportedPresentModes(long handle);
    private native void nativeSetEffects(long handle, int[] types, float[] params, int count);
    private native void nativeClearEffects(long handle);
    private native boolean nativeHasEffects(long handle);
    private native boolean nativeLsfgSupported(long handle);
    private native String nativeLsfgCapsReason(long handle);
    private native void nativeSetFrameGenArmed(long handle, boolean armed, int multiplier);
    private native void nativeSetLsfgCachePath(long handle, String path);
    private native void nativeSetFrameGenTuning(long handle, float flowScale, float refreshHz);
    private native void nativeSetFrameGenTargetRate(long handle, int targetRate);
    private native float[] nativeFrameGenStats(long handle);

    private static volatile boolean gpuImageChecked = false;

    private static long did(Drawable d) {
        return (long) System.identityHashCode(d);
    }

    public void onSurfaceCreated(Surface surface) {
        if (!gpuImageChecked) { GPUImage.checkIsSupported(); gpuImageChecked = true; }
        if (initExecutor != null) {
            initExecutor.shutdownNow();
            try { initExecutor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        initExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        initExecutor.execute(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) {
                    boolean ok = nativeReattachSurface(nativeHandle, surface);
                    if (!ok) {
                        nativeDestroy(nativeHandle);
                        nativeHandle = 0;
                    } else {
                        initComplete = true;
                        uploadAllMappedWindows();
                        xServerView.queueEvent(this::updateScene);
                        return;
                    }
                }
                nativeHandle = nativeInit(surface, xServer.screenInfo.width, xServer.screenInfo.height, driverPath, driverLibraryName, nativeLibDir);
                if (nativeHandle != 0) {
                    nativeSetPresentMode(nativeHandle, pendingPresentMode);
                    nativeSetFilterMode(nativeHandle, pendingFilterMode);
                    nativeSetSwapRB(nativeHandle, pendingSwapRB);
                    if (pendingLsfgCachePath != null) nativeSetLsfgCachePath(nativeHandle, pendingLsfgCachePath);
                    nativeSetFrameGenTuning(nativeHandle, pendingFgFlowScale, pendingFgRefreshHz);
                    nativeSetFrameGenTargetRate(nativeHandle, pendingFgTargetRate);
                    if (pendingFgArmed) nativeSetFrameGenArmed(nativeHandle, true, pendingFgMultiplier);
                    updateTransform();
                    nativeSetCursorVisible(nativeHandle, cursorVisible);

                    // Apply any pending effects that were set before renderer was initialized
                    if (pendingEffectTypes != null) {
                        nativeSetScanoutDisabled(nativeHandle, true);
                        int pCount = pendingEffectTypes.length;
                        float[] pFlat = new float[pCount * 8];
                        for (int pi = 0; pi < pCount; pi++) {
                            if (pendingEffectParams[pi] != null) {
                                int plen = Math.min(pendingEffectParams[pi].length, 8);
                                System.arraycopy(pendingEffectParams[pi], 0, pFlat, pi * 8, plen);
                            }
                        }
                        nativeSetEffects(nativeHandle, pendingEffectTypes, pFlat, pCount);
                        pendingEffectTypes = null;
                        pendingEffectParams = null;
                    }

                    if (nativeMode) {
                        xServerView.post(() -> {
                            releaseScanoutSurfaces();
                            if (android.os.Build.VERSION.SDK_INT >= 29) {
                                try {
                                    android.view.SurfaceControl xsc = (android.view.SurfaceControl) xServerView.getSurfaceControl();
                                    scanoutGameSC = new android.view.SurfaceControl.Builder()
                                        .setParent(xsc).setName("winlator_game").setOpaque(true).build();
                                    scanoutGameSurface = new android.view.Surface(scanoutGameSC);
                                    scanoutCursorSC = new android.view.SurfaceControl.Builder()
                                        .setParent(xsc).setName("winlator_cursor").setFormat(1).build();
                                    scanoutCursorSurface = new android.view.Surface(scanoutCursorSC);
                                    // BUG FIX: Р ВµРЎРѓР В»Р С‘ Р В°Р С”РЎвЂљР С‘Р Р†Р Р…РЎвЂ№ РЎРЊРЎвЂћРЎвЂћР ВµР С”РЎвЂљРЎвЂ№ РЎРЊР С”РЎР‚Р В°Р Р…Р В° (scanoutBlockedForEffects=true),
                                    // Р СњР вЂў Р Т‘Р ВµР В»Р В°Р ВµР С SurfaceControl РЎРѓР В»Р С•Р С‘ Р Р†Р С‘Р Т‘Р С‘Р СРЎвЂ№Р СР С‘ РІР‚вЂќ Р С‘Р Р…Р В°РЎвЂЎР Вµ Р С•Р Р…Р С‘ Р Р…Р В°Р С”Р В»Р В°Р Т‘РЎвЂ№Р Р†Р В°РЎР‹РЎвЂљРЎРѓРЎРЏ
                                    // Р С—Р С•Р Р†Р ВµРЎР‚РЎвЂ¦ РЎР‚Р ВµР Р…Р Т‘Р ВµРЎР‚Р В° РЎРѓ РЎРЊРЎвЂћРЎвЂћР ВµР С”РЎвЂљР В°Р СР С‘ Р С‘ РЎРѓР С•Р В·Р Т‘Р В°РЎР‹РЎвЂљ "Р Т‘Р Р†Р С•Р в„–Р Р…Р С•Р в„– РЎРЊР С”РЎР‚Р В°Р Р…".
                                    boolean scVisible = !scanoutBlockedForEffects;
                                    new android.view.SurfaceControl.Transaction()
                                        .setLayer(scanoutGameSC,   1)
                                        .setLayer(scanoutCursorSC, 2)
                                        .setVisibility(scanoutGameSC,   scVisible)
                                        .setVisibility(scanoutCursorSC, scVisible)
                                        .apply();
                                    applyScanoutSwapTransform();
                                    synchronized (lock) {
                                        if (nativeHandle != 0) {
                                            nativeSetScanoutWindow(nativeHandle, scanoutGameSurface, scanoutCursorSurface);
                                            updateTransform();
                                        }
                                    }
                                } catch (Exception e) {
                                    android.util.Log.w("VulkanRenderer", "SC recreate failed on surface restore: " + e);
                                    synchronized (lock) {
                                        if (nativeHandle != 0) nativeInitScanout(nativeHandle);
                                    }
                                }
                            } else {
                                synchronized (lock) { if (nativeHandle != 0) nativeInitScanout(nativeHandle); }
                            }
                        });
                    }
                }
            }
            synchronized (lock) {
                if (nativeHandle != 0) {
                    nativeSetVerboseLog(nativeHandle, true);
                    nativeDumpRendererInfo(nativeHandle);
                }
            }
            initComplete = true;
            uploadAllMappedWindows();
            xServerView.queueEvent(this::updateScene);
        });
    }

    private void uploadAllMappedWindows() {
        try (XLock xl = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
            uploadWindowsRecursive(xServer.windowManager.rootWindow);
        }
    }

    private void uploadWindowsRecursive(Window window) {
        if (!window.attributes.isMapped()) return;
        if (window != xServer.windowManager.rootWindow) {
            onUpdateWindowContent(window);
        }
        for (Window child : window.getChildren()) {
            uploadWindowsRecursive(child);
        }
    }

    public void onSurfaceChanged(int width, int height) {
        surfaceWidth = width; surfaceHeight = height;
        viewTransformation.update(width, height, xServer.screenInfo.width, xServer.screenInfo.height);
        synchronized (lock) {
            if (nativeHandle != 0) { nativeResize(nativeHandle, width, height); updateTransform(); }
        }
    }

    public void onSurfaceDestroyed() {
        initComplete = false;
        if (initExecutor != null) {
            initExecutor.shutdownNow();
            try { initExecutor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            initExecutor = null;
        }
        synchronized (lock) {
            if (nativeHandle != 0) {
                if (nativeMode) {
                    nativeDestroyScanout(nativeHandle);
                    nativeDestroy(nativeHandle);
                    nativeHandle = 0;
                } else {
                    nativeDetachSurface(nativeHandle);
                }
            }
        }
        if (nativeMode) xServerView.post(this::releaseScanoutSurfaces);
    }

    public XServerView getXServerView() { return xServerView; }

    public XServerView getRendererView() { return xServerView; }

    public void setOnFrameRenderedListener(Runnable listener) {}

    public String getForceFullscreenWMClass() { return null; }

    public void setForceFullscreenWMClass(String wmClass) {}

    public void forceCleanup() {
        initComplete = false;
        if (initExecutor != null) {
            initExecutor.shutdownNow();
            try { initExecutor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            initExecutor = null;
        }
        synchronized (lock) {
            if (nativeHandle != 0) {
                if (nativeMode) nativeDestroyScanout(nativeHandle);
                nativeDestroy(nativeHandle);
                nativeHandle = 0;
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.view.SurfaceControl.Transaction txn = new android.view.SurfaceControl.Transaction();
                if (scanoutGameSC != null) txn.setVisibility(scanoutGameSC, false);
                if (scanoutCursorSC != null) txn.setVisibility(scanoutCursorSC, false);
                txn.apply();
            } catch (Exception e) {
                android.util.Log.w("VulkanRenderer", "Failed to hide scanout layers on destroy: " + e);
            }
        }
        releaseScanoutSurfaces();
    }

    private void releaseScanoutSurfaces() {
        if (scanoutGameSurface   != null) { scanoutGameSurface.release();   scanoutGameSurface   = null; }
        if (scanoutCursorSurface != null) { scanoutCursorSurface.release(); scanoutCursorSurface = null; }
        if (scanoutGameSC        != null) { scanoutGameSC.release();        scanoutGameSC        = null; }
        if (scanoutCursorSC      != null) { scanoutCursorSC.release();      scanoutCursorSC      = null; }
    }

    private void applyScanoutSwapTransform() {
        if (scanoutGameSC == null || android.os.Build.VERSION.SDK_INT < 29) return;
        try {
            android.view.SurfaceControl.Transaction txn = new android.view.SurfaceControl.Transaction();
            float[] matrix = pendingSwapRB
                ? new float[]{0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f}
                : new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
            float[] translation = new float[]{0f, 0f, 0f};
            java.lang.reflect.Method setColorTransform = android.view.SurfaceControl.Transaction.class.getMethod(
                "setColorTransform",
                android.view.SurfaceControl.class,
                float[].class,
                float[].class
            );
            setColorTransform.invoke(txn, scanoutGameSC, matrix, translation);
            txn.apply();
            txn.close();
        } catch (Exception e) {
            android.util.Log.w("VulkanRenderer", "Scanout color transform unavailable: " + e);
        }
    }

    private void updateTransform() {
        if (nativeHandle == 0) return;
        float zoom = magnifierZoom;
        float cx = surfaceWidth / 2f;
        float cy = surfaceHeight / 2f;
        if (fullscreen) {
            nativeSetTransform(nativeHandle,
                cx * (1f - zoom), cy * (1f - zoom), zoom, zoom);
            viewTransformation.update(surfaceWidth, surfaceHeight,
                xServer.screenInfo.width, xServer.screenInfo.height);
            nativeScanoutSetDst(nativeHandle,
                viewTransformation.viewOffsetX,
                viewTransformation.viewOffsetY,
                viewTransformation.viewWidth,
                viewTransformation.viewHeight);
        } else {
            float py = 0;
            if (screenOffsetYRelativeToCursor) {
                short halfH = (short)(xServer.screenInfo.height / 2);
                py = Math.max(0, Math.min(xServer.pointer.getY() - halfH / 2.0f, halfH));
            }
            float baseOx = viewTransformation.sceneOffsetX;
            float baseOy = viewTransformation.sceneOffsetY - py;
            float baseSx = viewTransformation.sceneScaleX;
            float baseSy = viewTransformation.sceneScaleY;
            nativeSetTransform(nativeHandle,
                baseOx * zoom + cx * (1f - zoom),
                baseOy * zoom + cy * (1f - zoom),
                baseSx * zoom,
                baseSy * zoom);
            nativeScanoutSetDst(nativeHandle,
                viewTransformation.viewOffsetX,
                viewTransformation.viewOffsetY,
                viewTransformation.viewWidth,
                viewTransformation.viewHeight);
        }
    }

    public void updateScene() {
        ArrayList<RenderableWindow> newList = new ArrayList<>();
        try (XLock xl = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            collectWindows(newList, xServer.windowManager.rootWindow,
                xServer.windowManager.rootWindow.getX(),
                xServer.windowManager.rootWindow.getY());
        }
        synchronized (lock) {
            renderableWindows = newList;
            pushRenderList(newList);
        }
    }

    private void collectWindows(ArrayList<RenderableWindow> list, Window window, int x, int y) {
        if (!window.attributes.isMapped()) return;
        if (window != xServer.windowManager.rootWindow) {
            boolean viewable = true;
            if (unviewableWMClasses != null) {
                String wc = window.getClassName();
                for (String cls : unviewableWMClasses) {
                    if (wc.contains(cls)) {
                        if (window.attributes.isEnabled()) window.disableAllDescendants();
                        viewable = false; break;
                    }
                }
            }
            if (viewable) list.add(new RenderableWindow(window.getContent(), x, y));
        }
        for (Window child : window.getChildren())
            collectWindows(list, child, child.getX() + x, child.getY() + y);
    }

    private void pushRenderList(ArrayList<RenderableWindow> list) {
        if (nativeHandle == 0) return;
        int screenW = xServer.screenInfo.width, screenH = xServer.screenInfo.height;

        int start = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            RenderableWindow rw = list.get(i);
            if (rw.content != null && rw.content.width >= screenW && rw.content.height >= screenH) {
                start = i; break;
            }
        }

        if (nativeMode) {
            ArrayList<RenderableWindow> ns = new ArrayList<>();
            for (int i = start; i < list.size(); i++) {
                RenderableWindow rw = list.get(i);
                if (rw.content != null && !rw.content.isDirectScanout()) ns.add(rw);
            }
            int n = ns.size();
            long[] ids = new long[n]; int[] xs = new int[n]; int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                ids[i] = did(ns.get(i).content);
                xs[i] = ns.get(i).rootX; ys[i] = ns.get(i).rootY;
            }
            nativeSetRenderList(nativeHandle, ids, xs, ys, n);
            return;
        }
        if (fullscreen) {
            int n = list.size() - start;
            if (n <= 0) { nativeSetRenderList(nativeHandle, new long[0], new int[0], new int[0], 0); return; }
            long[] ids = new long[n]; int[] xs = new int[n]; int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                RenderableWindow rw = list.get(start + i);
                ids[i] = did(rw.content);
                xs[i] = rw.rootX; ys[i] = rw.rootY;
            }
            nativeSetRenderList(nativeHandle, ids, xs, ys, n);
            return;
        }

        int n = list.size() - start;
        long[] ids = new long[n]; int[] xs = new int[n]; int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            RenderableWindow rw = list.get(start + i);
            ids[i] = did(rw.content);
            xs[i] = rw.rootX; ys[i] = rw.rootY;
        }
        nativeSetRenderList(nativeHandle, ids, xs, ys, n);
    }

    private void sendCursorToNative(Cursor cursor) {
        if (nativeHandle == 0) return;
        Drawable cd; short hotX = 0, hotY = 0;
        boolean effVis = cursorVisible;
        if (cursor != null) {
            if (!cursor.isVisible()) effVis = false;
            cd = cursor.cursorImage; hotX = (short)cursor.hotSpotX; hotY = (short)cursor.hotSpotY;
        } else { cd = rootCursorDrawable; }
        nativeSetCursorVisible(nativeHandle, effVis);
        if (effVis && cd != null && cd.getBuffer() != null) {
            synchronized (cd.renderLock) {
                nativeUpdateCursorImage(nativeHandle, cd.getBuffer(), cd.width, cd.height, hotX, hotY);
                if (nativeMode && !scanoutBlockedForEffects) {
                    java.nio.ByteBuffer buf = cd.getBuffer();
                    short stride = (short)(buf.capacity() / (cd.height * 4));
                    nativeScanoutSetCursorImage(nativeHandle, buf, cd.width, cd.height, stride);
                }
            }
        }
    }

    public void onUpdateWindowContentDirect(Window window, Drawable pixmap, short xOff, short yOff) {
        if (!nativeMode && window.id == fpsWindowId) {
            if (classicHudRef != null) classicHudRef.update();
        }
        synchronized (lock) {
            if (nativeHandle == 0 || pixmap == null) return;
            int rx = window.getRootX() + xOff, ry = window.getRootY() + yOff;
            synchronized (pixmap.renderLock) {
                if (pixmap.getTexture() instanceof GPUImage) {
                    GPUImage g = (GPUImage) pixmap.getTexture();
                    long ahbPtr = g.getHardwareBufferPtr();
                    if (ahbPtr != 0) {
                        if (nativeMode && pixmap.isDirectScanout() && nativeIsScanoutActive(nativeHandle) && !scanoutBlockedForEffects) {
                            int fence = g.unlock();
                            nativeScanoutSetBuffer(nativeHandle, ahbPtr,
                                rx, ry, pixmap.width, pixmap.height, fence);
                            g.lock();
                            java.nio.ByteBuffer lockedVd = g.getVirtualData();
                            if (lockedVd != null) pixmap.setData(lockedVd);
                        } else {
                            long contentId = did(window.getContent());
                            nativeUpdateWindowContentAHB(nativeHandle, contentId, ahbPtr,
                                pixmap.width, pixmap.height, rx, ry);
                        }
                        return;
                    }
                    java.nio.ByteBuffer vd = g.getVirtualData();
                    if (vd != null) {
                        short s = g.getStride() > 0 ? g.getStride() : pixmap.width;
                        nativeUpdateWindowContent(nativeHandle, did(window.getContent()), vd,
                            pixmap.width, pixmap.height, s, rx, ry);
                        return;
                    }
                }
                java.nio.ByteBuffer buf = pixmap.getBuffer();
                if (buf == null) return;
                short stride = (short)(buf.capacity() / (pixmap.height * 4));
                nativeUpdateWindowContent(nativeHandle, did(window.getContent()), buf,
                    pixmap.width, pixmap.height, stride, rx, ry);
            }
        }
    }

    @Override
    public void onUpdateWindowContent(Window window) {
        synchronized (lock) {
            if (nativeHandle == 0) return;
            Drawable drawable = window.getContent();
            if (drawable == null || !window.attributes.isMapped()) return;
            if (unviewableWMClasses != null) {
                String wc = window.getClassName();
                for (String cls : unviewableWMClasses) if (wc.contains(cls)) return;
            }
            int rx = window.getRootX(), ry = window.getRootY();
            synchronized (drawable.renderLock) {
                if (drawable.getTexture() instanceof GPUImage) {
                    GPUImage g = (GPUImage) drawable.getTexture();
                    long ahbPtr = g.getHardwareBufferPtr();
                    if (ahbPtr != 0) {
                        boolean scanoutNow = nativeMode && nativeIsScanoutActive(nativeHandle) && !scanoutBlockedForEffects;
                        if (nativeMode && drawable.isDirectScanout() && scanoutNow) {
                            boolean wasDelivered = nativeIsGameFrameDelivered(nativeHandle);

                            int fence = g.unlock();
                            nativeScanoutSetBuffer(nativeHandle, ahbPtr,
                                rx, ry, drawable.width, drawable.height, fence);
                            g.lock();
                            java.nio.ByteBuffer lockedVd = g.getVirtualData();
                            if (lockedVd != null) drawable.setData(lockedVd);
                            boolean delivered = nativeIsGameFrameDelivered(nativeHandle);

                            if (!xRenderingPausedForScanout && !wasDelivered && delivered) {
                                xServer.setRenderingEnabled(false);
                                xRenderingPausedForScanout = true;
                            }
                            if (classicHudRef != null) classicHudRef.update();
                        } else if (!scanoutNow) {
                            nativeUpdateWindowContentAHB(nativeHandle, did(drawable), ahbPtr,
                                drawable.width, drawable.height, rx, ry);
                        }
                        return;
                    }
                    java.nio.ByteBuffer vd = g.getVirtualData();
                    if (vd != null) {
                        short s = g.getStride() > 0 ? g.getStride() : drawable.width;
                        nativeUpdateWindowContent(nativeHandle, did(drawable), vd,
                            drawable.width, drawable.height, s, rx, ry);
                        return;
                    }
                }
                java.nio.ByteBuffer buf = drawable.getBuffer();
                if (buf == null) return;
                short stride = (short)(buf.capacity() / (drawable.height * 4));
                nativeUpdateWindowContent(nativeHandle, did(drawable), buf,
                    drawable.width, drawable.height, stride, rx, ry);
            }
        }
    }

    @Override
    public void onPointerMove(short x, short y) {
        synchronized (lock) {
            if (nativeHandle == 0) return;
            // Always draw the cursor inside the X screen bounds
            short px = xServer.pointer.getClampedX();
            short py = xServer.pointer.getClampedY();
            nativeSetPointerPos(nativeHandle, px, py);
            Window pw = xServer.inputDeviceManager.getPointWindow();
            Cursor cursor = pw != null ? pw.attributes.getCursor() : null;
            if (cursor != lastCursor) { lastCursor = cursor; sendCursorToNative(cursor); }
            if (nativeMode) {
                short hotX = 0, hotY = 0;
                if (cursor != null) { hotX = (short)cursor.hotSpotX; hotY = (short)cursor.hotSpotY; }
                nativeScanoutSetCursorPos(nativeHandle, px, py, hotX, hotY);
            }
            if (screenOffsetYRelativeToCursor) updateTransform();
        }
    }

    public void onDestroyWindow(Window window) {
        final long id = did(window.getContent());
        xServerView.queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeRemoveWindow(nativeHandle, id);
            }
            updateScene();
        });
    }

    @Override public void onMapWindow(Window window) { xServerView.queueEvent(this::updateScene); }

    @Override
    public void onUnmapWindow(Window window) {
        final long id = did(window.getContent());
        xServerView.queueEvent(() -> {
            synchronized (lock) {
                if (nativeHandle != 0) nativeRemoveWindow(nativeHandle, id);
            }
            updateScene();
        });
    }

    @Override public void onChangeWindowZOrder(Window window) { xServerView.queueEvent(this::updateScene); }

    @Override
    public void onUpdateWindowGeometry(Window window, boolean resized) {
        xServerView.queueEvent(this::updateScene);
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            synchronized (lock) {
                Window pw = xServer.inputDeviceManager.getPointWindow();
                if (pw == window) { lastCursor = window.attributes.getCursor(); sendCursorToNative(lastCursor); }
            }
        }
    }

    public void setCursorVisible(boolean visible) {
        cursorVisible = visible;
        synchronized (lock) {
            if (nativeHandle != 0) { nativeSetCursorVisible(nativeHandle, visible); if (visible) sendCursorToNative(lastCursor); }
        }
    }

    public boolean isCursorVisible() { return cursorVisible; }

    public void setNativeMode(boolean mode) {
        if (this.nativeMode == mode) return;
        if (mode && scanoutBlockedForEffects) return;
        this.nativeMode = mode;
        xRenderingPausedForScanout = false;
        if (mode) {
            xServer.setRenderingEnabled(true);
            xServerView.post(() -> {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    try {
                        android.view.SurfaceControl xsc = (android.view.SurfaceControl) xServerView.getSurfaceControl();
                        scanoutGameSC = new android.view.SurfaceControl.Builder()
                            .setParent(xsc).setName("winlator_game").setOpaque(true).build();
                        scanoutGameSurface = new android.view.Surface(scanoutGameSC);
                        scanoutCursorSC = new android.view.SurfaceControl.Builder()
                            .setParent(xsc).setName("winlator_cursor").setFormat(1).build();
                        scanoutCursorSurface = new android.view.Surface(scanoutCursorSC);
                        android.view.SurfaceControl.Transaction scTxn =
                            new android.view.SurfaceControl.Transaction()
                            .setLayer(scanoutGameSC,   1)
                            .setLayer(scanoutCursorSC, 2)
                            .setVisibility(scanoutGameSC,   true)
                            .setVisibility(scanoutCursorSC, true);

                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            float targetFps = fpsLimit > 0 ? (float)fpsLimit
                                : xServerView.getDisplay() != null
                                    ? xServerView.getDisplay().getRefreshRate() : 60f;
                            scTxn.setFrameRate(scanoutGameSC, targetFps,
                                android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                        }
                        scTxn.apply();
                        applyScanoutSwapTransform();
                        synchronized (lock) {
                            if (nativeHandle != 0) {
                                nativeSetScanoutWindow(nativeHandle,
                                    scanoutGameSurface, scanoutCursorSurface);
                                updateTransform();
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.w("VulkanRenderer", "Sibling SC failed, using child SC: " + e);
                        synchronized (lock) {
                            if (nativeHandle != 0) nativeInitScanout(nativeHandle);
                        }
                    }
                } else {
                    synchronized (lock) { if (nativeHandle != 0) nativeInitScanout(nativeHandle); }
                }
            });
        } else {
            synchronized (lock) {
                if (nativeHandle != 0) nativeDestroyScanout(nativeHandle);
            }
            xServerView.post(() -> {
                xServer.setRenderingEnabled(true);
                releaseScanoutSurfaces();
            });
        }
        xServerView.queueEvent(this::updateScene);
        final String msg = mode ? "Native Rendering+ Enabled" : "Native Rendering+ Disabled";
        xServerView.post(() -> Toast.makeText(xServerView.getContext(), msg, Toast.LENGTH_SHORT).show());
    }

    public boolean isNativeMode() { return nativeMode; }

    public void disableScanoutForEffects() {
        scanoutBlockedForEffects = true;
        synchronized (lock) {
            if (nativeHandle != 0) {
                nativeSetScanoutDisabled(nativeHandle, true);
                if (nativeIsScanoutActive(nativeHandle)) {
                    nativeDestroyScanout(nativeHandle);
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.view.SurfaceControl.Transaction txn = new android.view.SurfaceControl.Transaction();
                if (scanoutGameSC != null) txn.setVisibility(scanoutGameSC, false);
                if (scanoutCursorSC != null) txn.setVisibility(scanoutCursorSC, false);
                txn.apply();
            } catch (Exception e) {
                android.util.Log.w("VulkanRenderer", "Failed to hide scanout layers: " + e);
            }
        }
    }

    public void setDriverInfo(String driverPath, String libraryName, String nativeLibDir) {
        this.driverPath = driverPath;
        this.driverLibraryName = libraryName;
        this.nativeLibDir = nativeLibDir;
        android.util.Log.d("Winlator_Renderer",
            "setDriverInfo: path=" + driverPath + " lib=" + libraryName);
    }

    public void setVerboseLog(boolean v) {
        synchronized (lock) { if (nativeHandle != 0) nativeSetVerboseLog(nativeHandle, v); }
    }

    public void dumpRendererInfo() {
        synchronized (lock) { if (nativeHandle != 0) nativeDumpRendererInfo(nativeHandle); }
    }

    public void setFilterMode(int mode) {
        pendingFilterMode = mode;
        synchronized (lock) { if (nativeHandle != 0) nativeSetFilterMode(nativeHandle, mode); }
    }

    public void setSwapRB(boolean enabled) {
        pendingSwapRB = enabled;
        synchronized (lock) { if (nativeHandle != 0) nativeSetSwapRB(nativeHandle, enabled); }
    }

    public void setVkPresentMode(int mode) {
        pendingPresentMode = mode;
        synchronized (lock) { if (nativeHandle != 0) nativeSetPresentMode(nativeHandle, mode); }
    }

    public int[] getSupportedPresentModes() {
        synchronized (lock) {
            if (nativeHandle != 0) return nativeGetSupportedPresentModes(nativeHandle);
        }
        return new int[0];
    }

    /**
     * Whether this device can run the native (compositor-side) LSFG chain:
     * Vulkan 1.3, required shader features enabled, storage-capable swapchain
     * format. False while the renderer is not up — "not yet known", not "no".
     */
    public boolean isLsfgNativeSupported() {
        synchronized (lock) {
            if (nativeHandle != 0) return nativeLsfgSupported(nativeHandle);
        }
        return false;
    }

    /**
     * Arm or disarm native LSFG frame generation. Changing this recreates the
     * swapchain (TRANSFER_DST usage + deeper image queue).
     *
     * @param multiplier requested 2x-4x ceiling.
     */
    public void setFrameGenArmed(boolean armed, int multiplier) {
        pendingFgArmed = armed;
        pendingFgMultiplier = multiplier;
        synchronized (lock) {
            if (nativeHandle != 0) nativeSetFrameGenArmed(nativeHandle, armed, multiplier);
        }
    }

    /** Whether native frame generation is currently armed in the renderer. */
    public boolean isFrameGenArmed() { return pendingFgArmed; }

    /** Path to the SPIR-V cache built from the user's Lossless.dll. */
    public void setLsfgCachePath(String path) {
        pendingLsfgCachePath = path;
        synchronized (lock) {
            if (nativeHandle != 0) nativeSetLsfgCachePath(nativeHandle, path);
        }
    }

    /**
     * Live native frame-gen telemetry, or null when the renderer is down:
     * {generations trusted, generations planned, real fps, presented fps,
     * thermal status (-1 = no signal), GPU ms per generated frame (-1)}.
     */
    public float[] getFrameGenStats() {
        synchronized (lock) {
            if (nativeHandle != 0) return nativeFrameGenStats(nativeHandle);
        }
        return null;
    }

    /** Flow scale (0.25-1.0) and the panel's real refresh rate. */
    public void setFrameGenTuning(float flowScale, float refreshHz) {
        pendingFgFlowScale = flowScale;
        pendingFgRefreshHz = refreshHz;
        synchronized (lock) {
            if (nativeHandle != 0) nativeSetFrameGenTuning(nativeHandle, flowScale, refreshHz);
        }
    }

    /**
     * Adaptive output-FPS target, 0 = fixed multiplier. Takes effect live;
     * replayed on renderer init like the other FG settings.
     */
    public void setFrameGenTargetRate(int targetRate) {
        pendingFgTargetRate = Math.max(0, targetRate);
        synchronized (lock) {
            if (nativeHandle != 0) nativeSetFrameGenTargetRate(nativeHandle, pendingFgTargetRate);
        }
    }

    /** Human-readable verdict, naming the first gate that failed. */
    public String getLsfgCapsReason() {
        synchronized (lock) {
            if (nativeHandle != 0) {
                String r = nativeLsfgCapsReason(nativeHandle);
                if (r != null) return r;
            }
        }
        return "renderer not started";
    }
    public void setNativeColorFormat(int format) {}
    public int getNativeColorFormat() { return 0; }

    private FrameRating classicHudRef = null;
    private int fpsWindowId = -1;

    public void setFpsWindowId(int id) { fpsWindowId = id; }

    public void setFrameRating(Object fr) {
        if (fr instanceof FrameRating) classicHudRef = (FrameRating) fr;
    }

    public boolean isFullscreen() { return fullscreen; }
    public void toggleFullscreen() { fullscreen = !fullscreen; synchronized (lock) { updateTransform(); } xServerView.queueEvent(this::updateScene); }
    public void setScreenOffsetYRelativeToCursor(boolean b) { screenOffsetYRelativeToCursor = b; synchronized (lock) { updateTransform(); } }
    public boolean isScreenOffsetYRelativeToCursor() { return screenOffsetYRelativeToCursor; }
    public void setMagnifierZoom(float zoom) {
        magnifierZoom = zoom;
        synchronized (lock) { updateTransform(); }
    }
    public float getMagnifierZoom() { return magnifierZoom; }
    public void setUnviewableWMClasses(String... classes) { this.unviewableWMClasses = classes; }
    public String[] getUnviewableWMClasses() { return unviewableWMClasses; }
    private int fpsLimit = 0;
    private int     pendingPresentMode    = 2;
    private int     pendingFilterMode     = 0;
    private boolean pendingSwapRB         = false;
    private boolean pendingFgArmed        = false;
    private int     pendingFgMultiplier   = 2;
    private float   pendingFgFlowScale    = 0.8f;
    private float   pendingFgRefreshHz    = 0f;
    private int     pendingFgTargetRate   = 0;
    private String  pendingLsfgCachePath  = null;
    public int getFpsLimit() { return fpsLimit; }
    public void setFpsLimit(int limit) {
        this.fpsLimit = limit;
        if (android.os.Build.VERSION.SDK_INT >= 30 && scanoutGameSC != null) {
            float targetFps = limit > 0 ? (float)limit
                : xServerView.getDisplay() != null
                    ? xServerView.getDisplay().getRefreshRate() : 60f;
            new android.view.SurfaceControl.Transaction()
                .setFrameRate(scanoutGameSC, targetFps,
                    android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                .apply();
        }
    }
    public int getSurfaceWidth() { return surfaceWidth; }
    public int getSurfaceHeight() { return surfaceHeight; }
    public void requestRender() {}

    // ============ Effect API ============
    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_COLOR = 1;
    public static final int EFFECT_FXAA = 2;
    public static final int EFFECT_CRT = 3;
    public static final int EFFECT_TOON = 4;
    public static final int EFFECT_VIGNETTE = 5;
    public static final int EFFECT_SEPIA = 6;
    public static final int EFFECT_BLUR = 7;
    public static final int EFFECT_PIXELATE = 8;
    public static final int EFFECT_GRAYSCALE = 9;
    public static final int EFFECT_SHARPEN = 10;
    public static final int EFFECT_SMOOTH = 11;
    public static final int EFFECT_HDR = 12;
    public static final int EFFECT_NTSC = 13;

    public void setEffects(int[] types, float[][] paramsArr) {
        synchronized (lock) {
            if (nativeHandle == 0) {
                // Renderer not initialized yet - store as pending
                pendingEffectTypes = types;
                pendingEffectParams = paramsArr;
                return;
            }
            pendingEffectTypes = null;
            pendingEffectParams = null;
            if (types == null || types.length == 0) {
                scanoutBlockedForEffects = false;
                if (nativeHandle != 0) nativeSetScanoutDisabled(nativeHandle, false);
                nativeClearEffects(nativeHandle);
                return;
            }
            int count = types.length;
            float[] flatParams = new float[count * 8];
            for (int i = 0; i < count; i++) {
                if (paramsArr[i] != null) {
                    int len = Math.min(paramsArr[i].length, 8);
                    System.arraycopy(paramsArr[i], 0, flatParams, i * 8, len);
                }
            }
            nativeSetEffects(nativeHandle, types, flatParams, count);
        }
    }

    public void clearEffects() {
        synchronized (lock) {
            scanoutBlockedForEffects = false;
            if (nativeHandle != 0) {
                nativeSetScanoutDisabled(nativeHandle, false);
                nativeClearEffects(nativeHandle);
            }
        }
        // BUG FIX: Р С”Р С•Р С–Р Т‘Р В° РЎРЊРЎвЂћРЎвЂћР ВµР С”РЎвЂљРЎвЂ№ Р С•РЎвЂЎР С‘РЎвЂ°Р В°РЎР‹РЎвЂљРЎРѓРЎРЏ, Р Р…РЎС“Р В¶Р Р…Р С• Р Р†Р ВµРЎР‚Р Р…РЎС“РЎвЂљРЎРЉ Р Р†Р С‘Р Т‘Р С‘Р СР С•РЎРѓРЎвЂљРЎРЉ SurfaceControl РЎРѓР В»Р С•РЎвЂР Р†
        // Р ВµРЎРѓР В»Р С‘ nativeMode Р В°Р С”РЎвЂљР С‘Р Р†Р ВµР Р…, Р С‘Р Р…Р В°РЎвЂЎР Вµ scanout Р Р…Р Вµ Р Р†Р С•Р В·Р С•Р В±Р Р…Р С•Р Р†Р С‘РЎвЂљРЎРѓРЎРЏ (РЎРЊР С”РЎР‚Р В°Р Р… Р С•РЎРѓРЎвЂљР В°Р Р…Р ВµРЎвЂљРЎРѓРЎРЏ Р С—РЎС“РЎРѓРЎвЂљРЎвЂ№Р С).
        if (nativeMode && android.os.Build.VERSION.SDK_INT >= 29) {
            xServerView.post(() -> {
                try {
                    android.view.SurfaceControl.Transaction txn = new android.view.SurfaceControl.Transaction();
                    if (scanoutGameSC != null) txn.setVisibility(scanoutGameSC, true);
                    if (scanoutCursorSC != null) txn.setVisibility(scanoutCursorSC, true);
                    txn.apply();
                } catch (Exception e) {
                    android.util.Log.w("VulkanRenderer", "Failed to restore scanout layers visibility: " + e);
                }
            });
        }
    }

    public boolean hasEffects() {
        synchronized (lock) { return nativeHandle != 0 && nativeHasEffects(nativeHandle); }
    }

    private static class RenderableWindow {
        public final Drawable content; public int rootX, rootY;
        public RenderableWindow(Drawable c, int x, int y) { content=c; rootX=x; rootY=y; }
    }
}

