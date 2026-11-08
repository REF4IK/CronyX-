package com.winlator.cmod.renderer;

import android.view.Surface;

import com.winlator.cmod.widget.XServerView;

public interface HostRenderer {
    XServerView getXServerView();
    default void setRenderingEnabled(boolean enabled) {}
    void requestRender();
    void forceCleanup();
    void setCursorVisible(boolean visible);
    boolean isCursorVisible();
    default void setUnviewableWMClasses(String wmClasses) {}
    default void setUnviewableWMClasses(String... wmClasses) {}
    default void setFilterMode(int mode) {}
    default void setMagnifierZoom(float zoom) {}
    default void setEffects(int[] types, float[][] paramsArr) {}
    default void clearEffects() {}
    default float getMagnifierZoom() { return 1f; }
    default void toggleFullscreen() {}
    default boolean isFullscreen() { return false; }
    /** Эффективное растяжение экрана ползунком (0..1); 0 — обычный aspect-fit. */
    default float getStretchAmount() { return 0f; }
    default void setFullscreenMode(int mode) {}
    default int getFullscreenMode() { return 0; }
    default void setScreenOffsetYRelativeToCursor(boolean b) {}
    default boolean isScreenOffsetYRelativeToCursor() { return false; }
    default void setFpsWindowId(int id) {}
    default void setFrameRating(Object fr) {}
    default int getFpsLimit() { return 0; }
    default void setFpsLimit(int limit) {}
    default int getSurfaceWidth() { return 0; }
    default int getSurfaceHeight() { return 0; }
    /** Подтолкнуть реальный размер surface, если surfaceChanged потерялся (выход из PiP). */
    default void onSurfaceChanged(int width, int height) {}
    /** Реанимировать рендерер, если surfaceCreated потерялся, а surface жив. */
    default void onSurfaceCreated(Surface surface) {}
}

