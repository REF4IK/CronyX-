package com.winlator.cmod.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;
import android.view.Surface;

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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ASurfaceRenderer implements HostRenderer, WindowManager.OnWindowModificationListener,
                                          Pointer.OnPointerMotionListener,
                                          XServerRenderer {
    static {
        System.loadLibrary("asurface_renderer");
    }

    private static final AtomicLong NATIVE_CONTEXT_GENERATION = new AtomicLong();

    static long getNativeContextGeneration() {
        return NATIVE_CONTEXT_GENERATION.get();
    }

    public final XServerView xServerView;
    private final XServer xServer;
    public final ViewTransformation viewTransformation = new ViewTransformation();
    public int surfaceWidth;
    public int surfaceHeight;
    private boolean sfCompatMode = false;
    private String[] unviewableWMClasses = null;
    private String forceFullscreenWMClass = null;
    private boolean containerCursorVisible = true;
    private boolean gameCursorVisible = true;
    private final Drawable rootCursorDrawable;
    private Cursor lastCursor = null;
    private boolean surfaceInitialized = false;
    private int renderListSize = 0;
    private Rect cachedDesktopDst = null;
    private int cachedDesktopSrcW = 0, cachedDesktopSrcH = 0;
    private Window desktopWindow = null;
    private final ArrayList<RenderableWindow> renderList = new ArrayList<>();
    private int fpsLimit = 0;

    private static class RenderableWindow {
        public Drawable content;
        public Window window;
        public int rootX, rootY;
        public boolean isDesktopChild;
        public boolean isDesktopWindow;

        public void set(Drawable c, Window w, int x, int y, boolean dc, boolean dw) {
            content = c;
            window = w;
            rootX = x;
            rootY = y;
            isDesktopChild = dc;
            isDesktopWindow = dw;
        }
    }

    public ASurfaceRenderer(XServerView xServerView, XServer xServer) {
        this.xServerView = xServerView;
        this.xServer = xServer;
        this.surfaceWidth = xServer.screenInfo.width;
        this.surfaceHeight = xServer.screenInfo.height;
        rootCursorDrawable = createRootCursorDrawable();
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
        Log.d("ASurfaceRenderer", "Constructor: surfaceWidth=" + surfaceWidth + " surfaceHeight=" + surfaceHeight);
    }

    public void setSfCompatMode(boolean apply) {
        this.sfCompatMode = apply;
    }

    private Drawable createRootCursorDrawable() {
        try {
            Context context = xServerView.getContext();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPremultiplied = false;
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.cursor, options);
            return Drawable.fromBitmap(bitmap);
        } catch (Exception e) {
            return null;
        }
    }

    public interface ScanoutFrameListener {
        void onFrameLatched(int windowId, int serial, long presentTimeNs);
    }

    private final java.util.HashMap<Integer, WindowSurface> windowSurfaces = new java.util.HashMap<>();

    private static class WindowGeometry {
        final Rect src = new Rect();
        final Rect dst = new Rect();
    }

    private static class WindowSurface {
        int width, height;
        int zOrder;
        boolean visible = false;
        final Rect lastSrc = new Rect();
        final Rect lastDst = new Rect();
    }

    private volatile ScanoutFrameListener scanoutFrameListener = null;

    public void setScanoutFrameListener(ScanoutFrameListener listener) {
        this.scanoutFrameListener = listener;
    }

    @androidx.annotation.Keep
    public void onScanoutFrameComplete(long packed) {
        int windowId = (int)((packed >>> 32) & 0xFFFFFFFFL);
        int serial   = (int)(packed          & 0xFFFFFFFFL);
        long presentTimeNs = System.nanoTime();
        ASurfaceRenderer.ScanoutFrameListener listener = scanoutFrameListener;
        if (listener != null) {
            listener.onFrameLatched(windowId, serial, presentTimeNs);
        }
    }

    private int pendingPresentSerial = 0;
    private AtomicInteger skipFPSCount = new AtomicInteger(0);
    private FrameRating hudRef = null;

    public void setPendingPresentSerial(int serial) {
        pendingPresentSerial = serial;
    }

    private int consumePresentSerial() {
        int s = pendingPresentSerial;
        pendingPresentSerial = 0;
        return s;
    }

    // ---- Native JNI methods ----
    private native boolean nativeInit(Surface surface, int screenWidth, int screenHeight);
    private native void nativeDestroy();
    private native void nativeInitScanout();
    private native boolean nativeReattachSurface(Surface surface);
    private native void nativeDestroyScanout();
    private native void nativeSetWindowBuffer(long contentId, long ahbPtr, int fenceFd, long windowId, long serial, AHBImage ahbImage, int slot, boolean sfCompatMode);
    static native boolean nativePrepareCpuSourceBuffers(long ahb0, long ahb1, long ahb2);
    static native void nativeReleaseCpuSourceBuffers(long ahb0, long ahb1, long ahb2);
    private native void nativeScanoutSetCursorVisibility(boolean visible);
    private native void nativeRegisterWindowSC(long contentId, String debugName);
    private native void nativeUnregisterWindowSC(long contentId);
    private native void nativeScanoutSetCursorImage(ByteBuffer pixels, short w, short h, short stride);
    private native void nativeScanoutSetCursorPos(short x, short y, short hotX, short hotY, boolean cursorVisible);
    private native void nativeScanoutSetDst(int x, int y, int w, int h);
    private native void nativeSetSfCallbackTarget(Object rendererRef);
    private native void nativeBeginTransaction();
    private native void nativeApplyTransaction();
    private native void nativeUpdateWindow(long contentId, boolean visible, int zOrder,
                                            int srcL, int srcT, int srcR, int srcB,
                                            int dstL, int dstT, int dstR, int dstB);

    // ---- Window surface management ----
    private WindowSurface getOrCreateWindowSurface(int contentId, int w, int h, String debugName) {
        WindowSurface ws = windowSurfaces.get(contentId);
        if (ws != null && (ws.width != w || ws.height != h)) {
            windowSurfaces.remove(contentId);
            ws = null;
        }
        if (ws == null) {
            try {
                ws = new WindowSurface();
                ws.width = w;
                ws.height = h;
                nativeRegisterWindowSC(contentId, debugName);
                windowSurfaces.put(contentId, ws);
            } catch (Exception e) {
                Log.e("ASurfaceRenderer", "getOrCreateWindowSurface failed: " + e);
                return null;
            }
        }
        return ws;
    }

    // ---- Desktop window detection ----
    private Window findDesktopWindow() {
        if (desktopWindow != null && desktopWindow.attributes.isMapped()) {
            return desktopWindow;
        }
        desktopWindow = null;
        for (Window child : xServer.windowManager.rootWindow.getChildren()) {
            if (!child.attributes.isOverrideRedirect() && "explorer.exe".equals(child.getClassName())) {
                desktopWindow = child;
                break;
            }
        }
        return desktopWindow;
    }

    private boolean isDesktopChild(Window window) {
        Window desktop = findDesktopWindow();
        if (desktop == null) return false;
        Window parent = window.getParent();
        while (parent != null) {
            if (parent == desktop) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private boolean adjustRectLT(Rect src, Rect dst) {
        final int originalDstW = dst.width();
        final int originalDstH = dst.height();
        if (originalDstW <= 0 || originalDstH <= 0) return false;

        if (dst.left < 0) {
            int clip = -dst.left;
            int srcClip = (int)(((long) clip * src.width()) / originalDstW);
            src.left += srcClip;
            dst.left = 0;
        }
        if (dst.top < 0) {
            int clip = -dst.top;
            int srcClip = (int)(((long) clip * src.height()) / originalDstH);
            src.top += srcClip;
            dst.top = 0;
        }
        return src.right > src.left && src.bottom > src.top &&
                dst.right > dst.left && dst.bottom > dst.top;
    }

    private void computeLetterboxRect(int srcW, int srcH, int dstW, int dstH, Rect outRect) {
        if (dstW <= 0 || dstH <= 0) {
            Log.w("ASurfaceRenderer", "computeLetterboxRect: bad dst size " + dstW + "x" + dstH);
            outRect.set(0, 0, 0, 0);
            return;
        }
        float srcAspect = (float) srcW / srcH;
        float dstAspect = (float) dstW / dstH;
        int scaledW, scaledH;
        if (srcAspect > dstAspect) {
            scaledW = dstW;
            scaledH = (int)(dstW / srcAspect);
        } else {
            scaledH = dstH;
            scaledW = (int)(dstH * srcAspect);
        }
        outRect.set((dstW - scaledW) / 2, (dstH - scaledH) / 2,
                    (dstW - scaledW) / 2 + scaledW, (dstH - scaledH) / 2 + scaledH);
        Log.d("ASurfaceRenderer", "computeLetterboxRect src=" + srcW + "x" + srcH + " dst=" + dstW + "x" + dstH + " -> " + outRect.toShortString());
    }

    // ---- Surface lifecycle ----
    public void onSurfaceCreated(Surface surface) {
        Log.d("ASurfaceRenderer", "onSurfaceCreated surfaceInitialized=" + surfaceInitialized + " surface=" + surface);
        if (surfaceInitialized) {
            boolean ok = nativeReattachSurface(surface);
            Log.d("ASurfaceRenderer", "onSurfaceCreated reattach ok=" + ok);
            if (!ok) {
                surfaceInitialized = false;
                nativeDestroy();
            } else {
                updateScene();
                return;
            }
        }
        skipFPSCount.set(0);
        surfaceInitialized = nativeInit(surface, xServer.screenInfo.width, xServer.screenInfo.height);
        Log.d("ASurfaceRenderer", "onSurfaceCreated nativeInit result=" + surfaceInitialized);
        if (surfaceInitialized) {
            NATIVE_CONTEXT_GENERATION.incrementAndGet();
            nativeSetSfCallbackTarget(this);
            updateTransform();
            nativeInitScanout();
            sendCursorToNative(lastCursor);
            updateScene();
            resubmitAllBuffers();
        }
    }

    private void resubmitAllBuffers() {
        try (XLock xl = xServer.lock(
                XServer.Lockable.WINDOW_MANAGER,
                XServer.Lockable.DRAWABLE_MANAGER)) {
            resubmitBuffersForWindow(xServer.windowManager.rootWindow);
        }
    }

    private void resubmitBuffersForWindow(Window window) {
        if (window != xServer.windowManager.rootWindow && window.attributes.isMapped() && window.getContent() != null) {
            if (windowSurfaces.containsKey(window.id)) {
                pushCpuImageToNative(window.id, window.getContent());
            }
        }
        for (Window child : window.getChildren()) resubmitBuffersForWindow(child);
    }

    public void onSurfaceChanged(int width, int height) {
        Log.d("ASurfaceRenderer", "onSurfaceChanged width=" + width + " height=" + height);
        surfaceWidth = width;
        surfaceHeight = height;
        viewTransformation.update(width, height, xServer.screenInfo.width, xServer.screenInfo.height);
        updateTransform();
    }

    public void onSurfaceDestroyed() {
        if (surfaceInitialized) {
            nativeDestroyScanout();
            nativeDestroy();
            surfaceInitialized = false;
        }
        skipFPSCount.set(0);
        windowSurfaces.clear();
        cachedDesktopDst = null;
    }

    private void updateTransform() {
        Log.d("ASurfaceRenderer", "updateTransform vOff=" + viewTransformation.viewOffsetX + "," + viewTransformation.viewOffsetY +
                " vSize=" + viewTransformation.viewWidth + "x" + viewTransformation.viewHeight +
                " surf=" + surfaceWidth + "x" + surfaceHeight);
        float t = getStretchAmount();
        if (t > 0f) {
            // Тот же интерполированный прямоугольник, что и в computeWindowRect (растяжение ползунком).
            int w = Math.round(viewTransformation.viewWidth + (surfaceWidth - viewTransformation.viewWidth) * t);
            int h = Math.round(viewTransformation.viewHeight + (surfaceHeight - viewTransformation.viewHeight) * t);
            nativeScanoutSetDst((surfaceWidth - w) / 2, (surfaceHeight - h) / 2, w, h);
        } else {
            nativeScanoutSetDst(viewTransformation.viewOffsetX,
                    viewTransformation.viewOffsetY,
                    viewTransformation.viewWidth,
                    viewTransformation.viewHeight);
        }
    }

    public void updateScene() {
        Log.d("ASurfaceRenderer", "updateScene START");
        renderListSize = 0;
        try (XLock xl = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
            collectWindows(xServer.windowManager.rootWindow,
                    xServer.windowManager.rootWindow.getX(),
                    xServer.windowManager.rootWindow.getY());
        }
        Log.d("ASurfaceRenderer", "updateScene collected=" + renderListSize);
        pushRenderList();
        Log.d("ASurfaceRenderer", "updateScene END");
    }

    private void collectWindows(Window window, int x, int y) {
        if (!window.attributes.isMapped()) return;
        if (window != xServer.windowManager.rootWindow) {
            RenderableWindow rw;
            if (renderList.size() <= renderListSize) {
                rw = new RenderableWindow();
                renderList.add(rw);
            } else {
                rw = renderList.get(renderListSize);
            }
            renderListSize++;
            rw.set(window.getContent(), window, x, y, isDesktopChild(window), window == findDesktopWindow());
        }
        for (Window child : window.getChildren()) {
            collectWindows(child, child.getX() + x, child.getY() + y);
        }
    }

    private boolean forceFirstFrame = true;

    // ---- Render list / SurfaceControl update ----
    private void pushRenderList() {
        Log.d("ASurfaceRenderer", "pushRenderList START renderListSize=" + renderListSize + " surfaceWidth=" + surfaceWidth + " surfaceHeight=" + surfaceHeight);
        nativeBeginTransaction();
        HashSet<Integer> visibleIds = new HashSet<>();
        for (int i = 0; i < renderListSize; i++) {
            RenderableWindow rw = renderList.get(i);
            if (rw.content == null) continue;

            int contentId = rw.window.id;
            visibleIds.add(contentId);
            String debugName = rw.window.getClassName();
            if (debugName == null || debugName.isEmpty()) debugName = "(x11_window)";

            WindowGeometry geom = new WindowGeometry();
            boolean geometryOk = computeWindowRect(
                    rw.rootX, rw.rootY,
                    rw.content.width, rw.content.height,
                    rw.isDesktopWindow, rw.isDesktopChild, geom);

            if (unviewableWMClasses != null) {
                String wc = rw.window.getClassName();
                boolean skip = false;
                for (String cls : unviewableWMClasses) {
                    if (wc.contains(cls)) { skip = true; break; }
                }
                if (skip) continue;
            }

            WindowSurface ws = getOrCreateWindowSurface(contentId, rw.content.width, rw.content.height, debugName);
            if (ws == null) {
                Log.w("ASurfaceRenderer", "pushRenderList: ws null for id=" + contentId + " cls=" + debugName);
                continue;
            }

            // Force geometry for the first frame to ensure SurfaceControls are properly initialized
            if (forceFirstFrame && geometryOk) {
                Log.d("ASurfaceRenderer", "FORCE update: id=" + contentId + " cls=" + debugName +
                        " isDesktop=" + rw.isDesktopWindow + " isDesktopChild=" + rw.isDesktopChild);
                ws.visible = true;
                ws.zOrder = i;
                ws.lastDst.set(geom.dst);
                ws.lastSrc.set(geom.src);
                nativeUpdateWindow(contentId, true, i,
                        geom.src.left, geom.src.top, geom.src.right, geom.src.bottom,
                        geom.dst.left, geom.dst.top, geom.dst.right, geom.dst.bottom);
                continue;
            }

            boolean needsUpdate = !ws.visible || ws.zOrder != i
                    || !geom.dst.equals(ws.lastDst)
                    || !geom.src.equals(ws.lastSrc);

            if (geometryOk && needsUpdate) {
                Log.d("ASurfaceRenderer", "updateWindow id=" + contentId + " dst=[" + geom.dst.left + "," + geom.dst.top + "," + geom.dst.right + "," + geom.dst.bottom + "]" +
                        " src=[" + geom.src.left + "," + geom.src.top + "," + geom.src.right + "," + geom.src.bottom + "] z=" + i);
                ws.visible = true;
                ws.zOrder = i;
                ws.lastDst.set(geom.dst);
                ws.lastSrc.set(geom.src);
                nativeUpdateWindow(contentId, true, i,
                        geom.src.left, geom.src.top, geom.src.right, geom.src.bottom,
                        geom.dst.left, geom.dst.top, geom.dst.right, geom.dst.bottom);
            }
        }

        for (Map.Entry<Integer, WindowSurface> entry : windowSurfaces.entrySet()) {
            if (!visibleIds.contains(entry.getKey())) {
                WindowSurface ws = entry.getValue();
                if (ws.visible) {
                    ws.visible = false;
                    nativeUpdateWindow(entry.getKey(), false, ws.zOrder,
                            0, 0, 0, 0, 0, 0, 0, 0);
                }
            }
        }
        nativeApplyTransaction();
        forceFirstFrame = false;
        Log.d("ASurfaceRenderer", "pushRenderList END");
    }

    // ---- Cursor handling ----
    private void sendCursorToNative(Cursor cursor) {
        if (!containerCursorVisible) return;
        Drawable cd = cursor != null ? cursor.cursorImage : rootCursorDrawable;
        if (cursor != null && !cursor.isVisible()) return;
        if (cd == null || cd.getBuffer() == null) return;
        synchronized (cd.renderLock) {
            ByteBuffer buf = cd.getBuffer();
            nativeScanoutSetCursorImage(buf, cd.width, cd.height,
                    (short)(buf.capacity() / (cd.height * 4)));
        }
    }

    // ---- Buffer submission ----
    private void pushCpuImageToNative(int windowId, Drawable drawable) {
        if (drawable == null) return;
        Window pw = xServer.inputDeviceManager.getPointWindow();
        Cursor cursor = pw != null ? pw.attributes.getCursor() : null;
        if (cursor != null && gameCursorVisible != cursor.isVisible()) {
            gameCursorVisible = cursor.isVisible();
            nativeScanoutSetCursorVisibility(containerCursorVisible && gameCursorVisible);
        }
        synchronized (drawable.renderLock) {
            if (drawable.getTexture() instanceof AHBImage g) {
                g.prepareScanoutSources();
                long ahbPtr = g.getScanoutHardwareBufferPtr();
                Log.d("ASurfaceRenderer", "pushCpuImageToNative id=" + windowId + " ahbPtr=0x" + Long.toHexString(ahbPtr) + " w=" + drawable.width + " h=" + drawable.height);
                if (ahbPtr != 0) {
                    int acquireFence = g.consumeAcquireFence();
                    nativeSetWindowBuffer(windowId, ahbPtr, acquireFence, 0, 0, g, g.getLastUsedSlot(), sfCompatMode);
                    if (hudRef != null && skipFPSCount.get() >= 1) {
                        hudRef.update();
                        skipFPSCount.set(0);
                    } else {
                        skipFPSCount.incrementAndGet();
                    }
                }
            }
        }
    }

    private void pushGpuImageToNative(int windowId, Drawable drawable, int xSerial) {
        if (drawable == null) return;
        Window pw = xServer.inputDeviceManager.getPointWindow();
        Cursor cursor = pw != null ? pw.attributes.getCursor() : null;
        if (cursor != null && gameCursorVisible != cursor.isVisible()) {
            gameCursorVisible = cursor.isVisible();
            nativeScanoutSetCursorVisibility(containerCursorVisible && gameCursorVisible);
        }
        synchronized (drawable.renderLock) {
            if (drawable.getTexture() instanceof AHBImage g) {
                long ahbPtr = g.getHardwareBufferPtr();
                if (ahbPtr != 0) {
                    nativeSetWindowBuffer(windowId, ahbPtr, -1, windowId, xSerial, null, -1, sfCompatMode);
                    if (hudRef != null) hudRef.update();
                }
            } else if (drawable.getTexture() instanceof GPUImage g) {
                long ahbPtr = g.getHardwareBufferPtr();
                if (ahbPtr != 0) {
                    nativeSetWindowBuffer(windowId, ahbPtr, -1, windowId, xSerial, null, -1, sfCompatMode);
                    if (hudRef != null) hudRef.update();
                }
            }
        }
    }

    private boolean computeWindowRect(int rootX, int rootY, int w, int h,
                                      boolean isDesktopWindow, boolean isDesktopChild,
                                      WindowGeometry out) {
        out.src.set(0, 0, w, h);
        if (isDesktopWindow && rootX == 0 && rootY == 0) {
            computeLetterboxRect(w, h, surfaceWidth, surfaceHeight, out.dst);
            float t = getStretchAmount();
            if (t > 0f) {
                // Ползунок «Растяжение экрана»: десктоп-прямоугольник интерполируется от
                // aspect-fit letterbox до всей поверхности; дочерние окна масштабируются
                // от него через cachedDesktopDst.
                int fitW = out.dst.width(), fitH = out.dst.height();
                int newW = Math.round(fitW + (surfaceWidth - fitW) * t);
                int newH = Math.round(fitH + (surfaceHeight - fitH) * t);
                int left = (surfaceWidth - newW) / 2, top = (surfaceHeight - newH) / 2;
                out.dst.set(left, top, left + newW, top + newH);
            }
            if (cachedDesktopDst == null) cachedDesktopDst = new Rect();
            cachedDesktopDst.set(out.dst);
            cachedDesktopSrcW = w;
            cachedDesktopSrcH = h;
        } else if (isDesktopChild && cachedDesktopDst != null &&
                cachedDesktopSrcW > 0 && cachedDesktopSrcH > 0) {
            float scaleX = (float) cachedDesktopDst.width()  / cachedDesktopSrcW;
            float scaleY = (float) cachedDesktopDst.height() / cachedDesktopSrcH;
            out.dst.set(
                cachedDesktopDst.left + (int)(rootX * scaleX),
                cachedDesktopDst.top  + (int)(rootY * scaleY),
                cachedDesktopDst.left + (int)(rootX * scaleX) + (int)(w * scaleX),
                cachedDesktopDst.top  + (int)(rootY * scaleY) + (int)(h * scaleY));
        }
        return adjustRectLT(out.src, out.dst);
    }

    // ---- XServerRenderer interface impl ----
    @Override
    public void onUpdateWindowContent(Window window) {
        Drawable drawable = window.getContent();
        if (drawable == null || !window.attributes.isMapped()) return;
        if (unviewableWMClasses != null) {
            String wc = window.getClassName();
            for (String cls : unviewableWMClasses) if (wc.contains(cls)) return;
        }
        pushGpuImageToNative(window.id, drawable, consumePresentSerial());
    }

    @Override
    public void onPointerMove(short x, short y) {
        Window pw = xServer.inputDeviceManager.getPointWindow();
        Cursor cursor = pw != null ? pw.attributes.getCursor() : null;
        if (cursor != null) {
            if (cursor != lastCursor) {
                lastCursor = cursor;
                sendCursorToNative(cursor);
            }
            nativeScanoutSetCursorPos(x, y, (short) cursor.hotSpotX, (short) cursor.hotSpotY,
                    containerCursorVisible && gameCursorVisible);
        }
    }

    @Override
    public void onMapWindow(Window window) {
        if (unviewableWMClasses != null) {
            String wc = window.getClassName();
            for (String cls : unviewableWMClasses) if (wc.contains(cls)) return;
        }
        Drawable content = window.getContent();
        if (content != null && content.width > 0 && content.height > 0) {
            if (!(content.getTexture() instanceof AHBImage)) {
                AHBImage g = new AHBImage(content.width, content.height);
                if (g.getHardwareBufferPtr() != 0) {
                    content.setTexture(g);
                }
            }
            if (content.getTexture() instanceof AHBImage g) {
                g.prepareScanoutSources();
            }
        }
        updateScene();
        // Push initial buffer AFTER window surface geometry is set up by updateScene
        if (content != null && windowSurfaces.containsKey(window.id)) {
            pushCpuImageToNative(window.id, content);
        }
    }

    @Override
    public void onUnmapWindow(Window window) {
        windowSurfaces.remove(window.id);
        nativeUnregisterWindowSC(window.id);
        updateScene();
    }

    public void onDestroyWindow(Window window) {
        if (window == desktopWindow) desktopWindow = null;
        windowSurfaces.remove(window.id);
        windowSurfaces.remove(window.id);
        nativeUnregisterWindowSC(window.id);
        updateScene();
    }

    @Override public void onChangeWindowZOrder(Window window) { updateScene(); }

    @Override
    public void onUpdateWindowGeometry(Window window, boolean resized) {
        if (unviewableWMClasses != null) {
            String wc = window.getClassName();
            for (String cls : unviewableWMClasses) if (wc.contains(cls)) return;
        }
        if (resized) {
            windowSurfaces.remove(window.id);
            nativeUnregisterWindowSC(window.id);
        }
        updateScene();
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            Window pw = xServer.inputDeviceManager.getPointWindow();
            if (pw == window) {
                lastCursor = window.attributes.getCursor();
                sendCursorToNative(lastCursor);
            }
        }
    }

    @Override
    public void setCursorVisible(boolean visible) {
        containerCursorVisible = visible;
        if (visible) sendCursorToNative(lastCursor);
        else nativeScanoutSetCursorVisibility(false);
    }

    @Override
    public boolean isCursorVisible() { return containerCursorVisible; }

    @Override
    public boolean isFullscreen() { return false; }

    // Ползунок «Растяжение экрана» (0..1): 0 — aspect-fit letterbox, 1 — весь экран.
    // ASurface не имеет fullscreen-режимов, действует всегда.
    private volatile float stretchAmount = 1.0f;

    @Override
    public float getStretchAmount() { return Math.max(0f, Math.min(1f, stretchAmount)); }

    public void setStretchAmount(float amount) {
        stretchAmount = Math.max(0f, Math.min(1f, amount));
        if (!surfaceInitialized) return; // применится в onSurfaceCreated → updateTransform/updateScene
        updateTransform();
        updateScene();
    }

    @Override
    public boolean isNativeMode() { return true; }

    @Override
    public void setFpsLimit(int limit) { fpsLimit = Math.max(0, Math.min(limit, 1000)); }

    @Override
    public int getFpsLimit() { return fpsLimit; }

    @Override
    public void setFpsWindowId(int windowId) {}

    @Override
    public void setScreenOffsetYRelativeToCursor(boolean enabled) {}

    @Override
    public void onUpdateWindowContentDirect(Window window, Drawable drawable, short xOff, short yOff) {
        onUpdateWindowContent(window);
    }

    @Override
    public void requestRender() {}

    @Override
    public void forceCleanup() {
        if (surfaceInitialized) {
            nativeDestroyScanout();
            nativeDestroy();
            surfaceInitialized = false;
        }
        windowSurfaces.clear();
    }

    @Override
    public XServerView getXServerView() { return xServerView; }

    @Override
    public XServerView getRendererView() { return xServerView; }

    @Override
    public void setFrameRating(Object fr) {
        if (fr instanceof FrameRating) hudRef = (FrameRating) fr;
    }

    @Override
    public void setUnviewableWMClasses(String... classes) { this.unviewableWMClasses = classes; }

    @Override
    public void setOnFrameRenderedListener(Runnable listener) {}

    @Override
    public String getForceFullscreenWMClass() { return forceFullscreenWMClass; }

    @Override
    public void setForceFullscreenWMClass(String wmClass) { this.forceFullscreenWMClass = wmClass; }
}

