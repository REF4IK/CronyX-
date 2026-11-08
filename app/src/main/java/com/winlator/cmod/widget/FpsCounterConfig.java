package com.winlator.cmod.widget;

import android.content.Context;
import android.content.SharedPreferences;
import com.winlator.cmod.core.MmkvPreferences;

public class FpsCounterConfig {
    public static final int STYLE_DEFAULT = 0;
    public static final int STYLE_CYBER = 1;
    public static final int STYLE_RETRO = 2;
    public static final int STYLE_GLASS = 3;
    public static final int STYLE_WINLATOR_LUDASHI = 4;
    public static final int STYLE_GAMENATIVE = 5;
    public static final int STYLE_MANGOHUD = 6;

    private static final String PREFS_NAME = "fps_counter_config";
    private static final String KEY_ENABLED = "fps_counter_enabled";
    private static final String KEY_SHOW_FPS = "fps_counter_show_fps";
    private static final String KEY_SHOW_RAM = "fps_counter_show_ram";
    private static final String KEY_SHOW_GPU = "fps_counter_show_gpu";
    private static final String KEY_SHOW_GPU_LOAD = "fps_counter_show_gpu_load";
    private static final String KEY_SHOW_GPU_TEMP = "fps_counter_show_gpu_temp";
    private static final String KEY_SHOW_FRAME_TIME_GRAPH = "fps_counter_show_frame_time_graph";
    private static final String KEY_SHOW_RENDERER = "fps_counter_show_renderer";
    private static final String KEY_SHOW_CPU_LOAD = "fps_counter_show_cpu_load";
    private static final String KEY_SHOW_CPU_TEMP = "fps_counter_show_cpu_temp";
    private static final String KEY_SHOW_BATTERY_TEMP = "fps_counter_show_battery_temp";
    private static final String KEY_SHOW_BATTERY_VOLTAGE = "fps_counter_show_battery_voltage";
    private static final String KEY_SHOW_CPU_CORES = "fps_counter_show_cpu_cores";
    private static final String KEY_SHOW_GPU_FREQ = "fps_counter_show_gpu_freq";
    private static final String KEY_SHOW_BATTERY_LEVEL = "fps_counter_show_battery_percent";
    private static final String KEY_HORIZONTAL_LAYOUT = "fps_counter_horizontal_layout";
    private static final String KEY_BACKGROUND_OPACITY = "fps_counter_background_opacity";
    private static final String KEY_COUNTER_SCALE = "fps_counter_scale";
    private static final String KEY_FPS_LIMIT = "fps_counter_fps_limit";
    private static final String KEY_COUNTER_STYLE = "fps_counter_style";
    private static final String KEY_WHITE_FONTS = "fps_counter_white_fonts";
    private static final int OLD_DEFAULT_BACKGROUND_OPACITY = 51;
    private static final int DEFAULT_BACKGROUND_OPACITY = 153;

    public enum Module {
        FPS,
        RAM,
        GPU,
        GPU_LOAD,
        GPU_TEMP,
        FRAME_TIME_GRAPH,
        RENDERER,
        CPU_LOAD,
        CPU_TEMP,
        BATTERY_TEMP,
        BATTERY_VOLTAGE,
        CPU_CORES,
        GPU_FREQ,
        BATTERY_LEVEL
    }

    private final SharedPreferences prefs;

    public FpsCounterConfig(Context context) {
        prefs = new MmkvPreferences(PREFS_NAME);
        migrateDefaultBackgroundOpacity();
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean isModuleVisible(Module module) {
        switch (module) {
            case FPS:
                return prefs.getBoolean(KEY_SHOW_FPS, false);
            case RAM:
                return prefs.getBoolean(KEY_SHOW_RAM, false);
            case GPU:
                return prefs.getBoolean(KEY_SHOW_GPU, false);
            case GPU_LOAD:
                return prefs.getBoolean(KEY_SHOW_GPU_LOAD, false);
            case GPU_TEMP:
                return prefs.getBoolean(KEY_SHOW_GPU_TEMP, false);
            case FRAME_TIME_GRAPH:
                return prefs.getBoolean(KEY_SHOW_FRAME_TIME_GRAPH, false);
            case RENDERER:
                return prefs.getBoolean(KEY_SHOW_RENDERER, false);
            case CPU_LOAD:
                return prefs.getBoolean(KEY_SHOW_CPU_LOAD, false);
            case CPU_TEMP:
                return prefs.getBoolean(KEY_SHOW_CPU_TEMP, false);
            case BATTERY_TEMP:
                return prefs.getBoolean(KEY_SHOW_BATTERY_TEMP, false);
            case BATTERY_VOLTAGE:
                return prefs.getBoolean(KEY_SHOW_BATTERY_VOLTAGE, false);
            case CPU_CORES:
                return prefs.getBoolean(KEY_SHOW_CPU_CORES, false);
            case GPU_FREQ:
                return prefs.getBoolean(KEY_SHOW_GPU_FREQ, false);
            case BATTERY_LEVEL:
                return prefs.getBoolean(KEY_SHOW_BATTERY_LEVEL, false);
            default:
                return false;
        }
    }

    public void setModuleVisible(Module module, boolean visible) {
        String key;
        switch (module) {
            case FPS:
                key = KEY_SHOW_FPS;
                break;
            case RAM:
                key = KEY_SHOW_RAM;
                break;
            case GPU:
                key = KEY_SHOW_GPU;
                break;
            case GPU_LOAD:
                key = KEY_SHOW_GPU_LOAD;
                break;
            case GPU_TEMP:
                key = KEY_SHOW_GPU_TEMP;
                break;
            case FRAME_TIME_GRAPH:
                key = KEY_SHOW_FRAME_TIME_GRAPH;
                break;
            case RENDERER:
                key = KEY_SHOW_RENDERER;
                break;
            case CPU_LOAD:
                key = KEY_SHOW_CPU_LOAD;
                break;
            case CPU_TEMP:
                key = KEY_SHOW_CPU_TEMP;
                break;
            case BATTERY_TEMP:
                key = KEY_SHOW_BATTERY_TEMP;
                break;
            case BATTERY_VOLTAGE:
                key = KEY_SHOW_BATTERY_VOLTAGE;
                break;
            case CPU_CORES:
                key = KEY_SHOW_CPU_CORES;
                break;
            case GPU_FREQ:
                key = KEY_SHOW_GPU_FREQ;
                break;
            case BATTERY_LEVEL:
                key = KEY_SHOW_BATTERY_LEVEL;
                break;
            default:
                return;
        }
        prefs.edit().putBoolean(key, visible).apply();
    }

    public boolean isHorizontalLayout() {
        return prefs.getBoolean(KEY_HORIZONTAL_LAYOUT, false);
    }

    public void setHorizontalLayout(boolean horizontal) {
        prefs.edit().putBoolean(KEY_HORIZONTAL_LAYOUT, horizontal).apply();
    }

    public int getBackgroundOpacity() {
        return prefs.getInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY);
    }

    public void setBackgroundOpacity(int opacity) {
        opacity = Math.max(0, Math.min(255, opacity));
        prefs.edit().putInt(KEY_BACKGROUND_OPACITY, opacity).apply();
    }

    public int getCounterScale() {
        return prefs.getInt(KEY_COUNTER_SCALE, 60);
    }

    public void setCounterScale(int scale) {
        scale = Math.max(60, Math.min(200, scale));
        prefs.edit().putInt(KEY_COUNTER_SCALE, scale).apply();
    }

    public int getFpsLimit() {
        return prefs.getInt(KEY_FPS_LIMIT, 0);
    }

    public void setFpsLimit(int limit) {
        limit = Math.max(0, Math.min(240, limit));
        prefs.edit().putInt(KEY_FPS_LIMIT, limit).apply();
    }

    public int getCounterStyle() {
        return prefs.getInt(KEY_COUNTER_STYLE, STYLE_DEFAULT);
    }

    public void setCounterStyle(int style) {
        prefs.edit().putInt(KEY_COUNTER_STYLE, style).apply();
    }

    public boolean isWhiteFonts() {
        return prefs.getBoolean(KEY_WHITE_FONTS, false);
    }

    public void setWhiteFonts(boolean whiteFonts) {
        prefs.edit().putBoolean(KEY_WHITE_FONTS, whiteFonts).apply();
    }

    public void resetToDefaults() {
        prefs.edit().clear()
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_SHOW_FPS, false)
            .putBoolean(KEY_SHOW_RAM, false)
            .putBoolean(KEY_SHOW_GPU, false)
            .putBoolean(KEY_SHOW_GPU_LOAD, false)
            .putBoolean(KEY_SHOW_GPU_TEMP, false)
            .putBoolean(KEY_SHOW_FRAME_TIME_GRAPH, false)
            .putBoolean(KEY_SHOW_RENDERER, false)
            .putBoolean(KEY_SHOW_CPU_LOAD, false)
            .putBoolean(KEY_SHOW_CPU_TEMP, false)
            .putBoolean(KEY_SHOW_BATTERY_TEMP, false)
            .putBoolean(KEY_SHOW_BATTERY_VOLTAGE, false)
            .putBoolean(KEY_SHOW_CPU_CORES, false)
            .putBoolean(KEY_SHOW_GPU_FREQ, false)
            .putBoolean(KEY_SHOW_BATTERY_LEVEL, false)
            .putBoolean(KEY_HORIZONTAL_LAYOUT, false)
            .putInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY)
            .putInt(KEY_COUNTER_SCALE, 60)
            .putInt(KEY_FPS_LIMIT, 0)
            .putInt(KEY_COUNTER_STYLE, STYLE_DEFAULT)
            .putBoolean(KEY_WHITE_FONTS, false)
            .apply();
    }

    private void migrateDefaultBackgroundOpacity() {
        if (prefs.contains(KEY_BACKGROUND_OPACITY)
                && prefs.getInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY) == OLD_DEFAULT_BACKGROUND_OPACITY) {
            prefs.edit().putInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY).apply();
        }
    }
}
