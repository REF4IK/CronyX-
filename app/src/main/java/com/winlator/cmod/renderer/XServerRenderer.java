package com.winlator.cmod.renderer;

import android.view.Surface;

import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Window;

public interface XServerRenderer {
    void onSurfaceCreated(Surface surface);
    void onSurfaceChanged(int width, int height);
    void onSurfaceDestroyed();

    void setFpsLimit(int limit);
    int getFpsLimit();
    void setCursorVisible(boolean visible);
    boolean isCursorVisible();

    void forceCleanup();
    void requestRender();

    XServerView getXServerView();
    XServerView getRendererView();

    void onUpdateWindowContent(Window window);
    void onUpdateWindowContentDirect(Window window, Drawable drawable, short xOff, short yOff);
    boolean isNativeMode();
    boolean isFullscreen();

    /** Эффективное растяжение экрана ползунком (0..1); 0 — обычный aspect-fit. */
    default float getStretchAmount() { return 0f; }

    void setFrameRating(Object frameRating);
    void setFpsWindowId(int windowId);

    void setScreenOffsetYRelativeToCursor(boolean enabled);

    void setUnviewableWMClasses(String... classes);
    void setOnFrameRenderedListener(Runnable listener);
    String getForceFullscreenWMClass();
    void setForceFullscreenWMClass(String wmClass);
}
