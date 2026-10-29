package com.winlator.cmod.inputcontrols;

/**
 * Стили отрисовки экранных кнопок (порт WinNative).
 * Коды 0/1/2 совпадают со старыми значениями в .icp профилях, поэтому миграция не нужна.
 */
public enum VisualStyle {
    ORIGINAL(0),
    GAMEHUB(1),
    SHADOW(2),
    SLATE(3),
    HALO(4),
    GLINT(5),
    RETICLE(6),
    NEON(7),
    LUMINA(8);

    public final int code;

    VisualStyle(int code) {
        this.code = code;
    }

    public static VisualStyle fromCode(int code) {
        VisualStyle[] styles = values();
        for (VisualStyle style : styles) if (style.code == code) return style;
        return GAMEHUB;
    }

    public static String[] displayNames() {
        return new String[] {"Original", "Glass", "Shadow", "Slate", "Halo", "Glint", "Reticle", "Neon", "Lumina"};
    }
}
