package com.winlator.cmod.inputcontrols;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import com.winlator.cmod.inputcontrols.IconPackManager;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import androidx.core.graphics.ColorUtils;

import com.winlator.cmod.core.CubicBezierInterpolator;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class ControlElement {
    public static final float STICK_DEAD_ZONE = 0.15f;
    public static final float DPAD_DEAD_ZONE = 0.3f;
    public static final float STICK_SENSITIVITY = 3.0f;
    public static final float TRACKPAD_MIN_SPEED = 0.8f;
    public static final float TRACKPAD_MAX_SPEED = 20.0f;
    public static final byte TRACKPAD_ACCELERATION_THRESHOLD = 4;
    public static final short BUTTON_MIN_TIME_TO_KEEP_PRESSED = 300;
    public enum Type {
        BUTTON, COMBO_BUTTON, D_PAD, RANGE_BUTTON, STICK, TRACKPAD, STEERING_WHEEL;

        public static String[] names() {
            Type[] types = values();
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) names[i] = types[i].name().replace("_", "-");
            return names;
        }
    }
    public enum Shape {
        CIRCLE, RECT, ROUND_RECT, SQUARE;

        public static String[] names() {
            Shape[] shapes = values();
            String[] names = new String[shapes.length];
            for (int i = 0; i < shapes.length; i++) names[i] = shapes[i].name().replace("_", " ");
            return names;
        }
    }
    public enum Range {
        FROM_A_TO_Z(26), FROM_0_TO_9(10), FROM_F1_TO_F12(12), FROM_NP0_TO_NP9(10);
        public final byte max;

        Range(int max) {
            this.max = (byte)max;
        }

        public static String[] names() {
            Range[] ranges = values();
            String[] names = new String[ranges.length];
            for (int i = 0; i < ranges.length; i++) names[i] = ranges[i].name().replace("_", " ");
            return names;
        }
    }
    private final InputControlsView inputControlsView;
    private Type type = Type.BUTTON;
    private Shape shape = Shape.CIRCLE;
    private Binding[] bindings = {Binding.NONE, Binding.NONE, Binding.NONE, Binding.NONE};
    private float scale = 1.0f;
    private short x;
    private short y;
    private boolean selected = false;
    private boolean toggleSwitch = false;
    private boolean hideBorder = false;
    private int currentPointerId = -1;
    private final Rect boundingBox = new Rect();
    private boolean[] states = new boolean[4];
    private boolean boundingBoxNeedsUpdate = true;
    private String text = "";
    private int iconId;
    private Range range;
    private byte orientation;
    private PointF currentPosition;
    private RangeScroller scroller;
    private CubicBezierInterpolator interpolator;
    private Object touchTime;
    private float accumulatedDelta;
    private float iconScale = 1.0f; // Новое поле для размера иконки
    private float opacity = 0.5f; // Прозрачность кнопки (0.1 - 1.0)

    private float fillOpacity = 0.12f;
    private float rotation = 0.0f;
    private int borderColor = Color.TRANSPARENT;
    private int fillColor = Color.TRANSPARENT;
    private int textColor = Color.TRANSPARENT;

    public ControlElement(InputControlsView inputControlsView) {
        this.inputControlsView = inputControlsView;
    }

    private void reset() {
        setBinding(Binding.NONE);
        scroller = null;

        if (type == Type.D_PAD || type == Type.STICK) {
            bindings[0] = Binding.KEY_W;
            bindings[1] = Binding.KEY_D;
            bindings[2] = Binding.KEY_S;
            bindings[3] = Binding.KEY_A;
        }
        else if (type == Type.TRACKPAD) {
            bindings[0] = Binding.MOUSE_MOVE_UP;
            bindings[1] = Binding.MOUSE_MOVE_RIGHT;
            bindings[2] = Binding.MOUSE_MOVE_DOWN;
            bindings[3] = Binding.MOUSE_MOVE_LEFT;
        }
        else if (type == Type.STEERING_WHEEL) {
            bindings[0] = Binding.NONE;  // Up (not used)
            bindings[1] = Binding.KEY_D; // Right
            bindings[2] = Binding.NONE;  // Down (not used)
            bindings[3] = Binding.KEY_A; // Left
        }
        else if (type == Type.COMBO_BUTTON) {
            setBindingCount(Math.max(3, bindings.length));
        }
        else if (type == Type.RANGE_BUTTON) {
            scroller = new RangeScroller(inputControlsView, this);
        }

        text = "";
        iconId = 0;
        range = null;
        boundingBoxNeedsUpdate = true;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
        reset();
    }

    public int getBindingCount() {
        return bindings.length;
    }

    public void setBindingCount(int bindingCount) {
        bindings = new Binding[bindingCount];
        setBinding(Binding.NONE);
        states = new boolean[bindingCount];
        boundingBoxNeedsUpdate = true;
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
        boundingBoxNeedsUpdate = true;
    }

    public Range getRange() {
        return range != null ? range : Range.FROM_A_TO_Z;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public byte getOrientation() {
        return orientation;
    }

    public void setOrientation(byte orientation) {
        this.orientation = orientation;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isToggleSwitch() {
        return toggleSwitch;
    }

    public void setToggleSwitch(boolean toggleSwitch) {
        this.toggleSwitch = toggleSwitch;
    }

    public boolean isHideBorder() {
        return hideBorder;
    }

    public void setHideBorder(boolean hideBorder) {
        this.hideBorder = hideBorder;
    }

    public Binding getBindingAt(int index) {
        return index < bindings.length ? bindings[index] : Binding.NONE;
    }

    public void setBindingAt(int index, Binding binding) {
        if (index >= bindings.length) {
            int oldLength = bindings.length;
            bindings = Arrays.copyOf(bindings, index+1);
            Arrays.fill(bindings, oldLength-1, bindings.length, Binding.NONE);
            states = new boolean[bindings.length];
            boundingBoxNeedsUpdate = true;
        }
        bindings[index] = binding;
    }

    public void setBinding(Binding binding) {
        Arrays.fill(bindings, binding);
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        boundingBoxNeedsUpdate = true;
    }

    public short getX() {
        return x;
    }

    public void setX(int x) {
        this.x = (short)x;
        boundingBoxNeedsUpdate = true;
    }

    public short getY() {
        return y;
    }

    public void setY(int y) {
        this.y = (short)y;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isSelected() {
        return selected;
    }

    public boolean isEngaged() {
        return currentPointerId != -1 || (toggleSwitch && selected);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public int getIconId() {
        return iconId;
    }

    public void setIconId(int iconId) {
        this.iconId = iconId;
    }

    public float getIconScale() {
        return iconScale;
    }

    public void setIconScale(float iconScale) {
        this.iconScale = iconScale;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    public float getFillOpacity() {
        return fillOpacity;
    }

    public void setFillOpacity(float fillOpacity) {
        this.fillOpacity = Mathf.clamp(fillOpacity, 0.0f, 1.0f);
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = ((rotation % 360.0f) + 360.0f) % 360.0f;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
    }

    public int getFillColor() {
        return fillColor;
    }

    public void setFillColor(int fillColor) {
        this.fillColor = fillColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public Rect getBoundingBox() {
        if (boundingBoxNeedsUpdate) computeBoundingBox();
        return boundingBox;
    }

    private Rect computeBoundingBox() {
        int snappingSize = inputControlsView.getSnappingSize();
        int halfWidth = 0;
        int halfHeight = 0;

        switch (type) {
            case BUTTON:
            case COMBO_BUTTON:
                switch (shape) {
                    case RECT:
                    case ROUND_RECT:
                        halfWidth = snappingSize * (type == Type.COMBO_BUTTON ? 7 : 4);
                        halfHeight = snappingSize * 2;
                        break;
                    case SQUARE:
                        halfWidth = (int)(snappingSize * 2.5f);
                        halfHeight = (int)(snappingSize * 2.5f);
                        break;
                    case CIRCLE:
                        halfWidth = snappingSize * 3;
                        halfHeight = snappingSize * 3;
                        break;
                }
                break;
            case D_PAD: {
                halfWidth = snappingSize * 7;
                halfHeight = snappingSize * 7;
                break;
            }
            case TRACKPAD:
            case STEERING_WHEEL:
            case STICK: {
                halfWidth = snappingSize * 6;
                halfHeight = snappingSize * 6;
                break;
            }
            case RANGE_BUTTON: {
                halfWidth = snappingSize * ((bindings.length * 4) / 2);
                halfHeight = snappingSize * 2;

                if (orientation == 1) {
                    int tmp = halfWidth;
                    halfWidth = halfHeight;
                    halfHeight = tmp;
                }
                break;
            }
        }

        halfWidth *= scale;
        halfHeight *= scale;
        boundingBox.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
        boundingBoxNeedsUpdate = false;
        return boundingBox;
    }



    private String getDisplayText() {
        if (text != null && !text.isEmpty()) {
            return text;
        }
        else {
            Binding binding = getBindingAt(0);
            String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
            if (text.length() > 7) {
                String[] parts = text.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) sb.append(part.charAt(0));
                return (binding.isMouse() ? "M" : "")+ sb;
            }
            else return text;
        }
    }

    private static float getTextSizeForWidth(Paint paint, String text, float desiredWidth) {
        final byte testTextSize = 48;
        paint.setTextSize(testTextSize);
        return testTextSize * desiredWidth / paint.measureText(text);
    }

    private static String getRangeTextForIndex(Range range, int index) {
        String text = "";
        switch (range) {
            case FROM_A_TO_Z:
                text = String.valueOf((char)(65 + index));
                break;
            case FROM_0_TO_9:
                text = String.valueOf((index + 1) % 10);
                break;
            case FROM_F1_TO_F12:
                text = "F"+(index + 1);
                break;
            case FROM_NP0_TO_NP9:
                text = "NP"+((index + 1) % 10);
                break;
        }
        return text;
    }

    public void draw(Canvas canvas) {
        VisualStyle visualStyle = inputControlsView.getVisualStyle();
        switch (visualStyle) {
            case GAMEHUB:
            case ORIGINAL:
                drawLegacy(canvas, visualStyle);
                break;
            case SHADOW:
                drawShadow(canvas);
                break;
            case RETICLE:
                drawReticle(canvas);
                break;
            case NEON:
                drawNeon(canvas);
                break;
            case LUMINA:
                drawLumina(canvas);
                break;
            default:
                drawClean(canvas, visualStyle);
                break;
        }
    }

    private void drawLegacy(Canvas canvas, VisualStyle visualStyle) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        int style = visualStyle == VisualStyle.GAMEHUB ? 1 : 0;
        int primaryColor = textColor != Color.TRANSPARENT ? ColorUtils.setAlphaComponent(textColor, (int)(opacity * 255)) : inputControlsView.getPrimaryColor();
        int strokeColor = borderColor != Color.TRANSPARENT ? ColorUtils.setAlphaComponent(borderColor, (int)(opacity * 255)) : (selected ? inputControlsView.getSecondaryColor() : inputControlsView.getPrimaryColor());
        int currentFillColor = fillColor != Color.TRANSPARENT ? fillColor : Color.WHITE;

        paint.setColor(strokeColor);
        paint.setAlpha((int)(opacity * 255)); // Применяем прозрачность
        paint.setStyle(Paint.Style.STROKE);
        float strokeWidth = snappingSize * 0.25f;
        paint.setStrokeWidth(strokeWidth);
        Rect boundingBox = getBoundingBox();
        canvas.save();
        canvas.rotate(rotation, x, y);

        drawStyleBody(canvas, paint, boundingBox, snappingSize, strokeColor, currentFillColor, strokeWidth);

        switch (type) {
            case BUTTON: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                if (style != 1 && style != 2) drawButtonFill(canvas, paint, boundingBox, currentFillColor, fillOpacity, shape, scale);

                // Рисовать границу только если hideBorder = false (для Glass обводка не нужна)
                if (!hideBorder && style != 1) {
                    switch (shape) {
                        case CIRCLE:
                            canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                            break;
                        case RECT:
                            canvas.drawRect(boundingBox, paint);
                            break;
                        case ROUND_RECT: {
                            float radius = boundingBox.height() * 0.5f;
                            canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                            break;
                        }
                        case SQUARE: {
                            float radius = snappingSize * 0.75f * scale;
                            canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                            break;
                        }
                    }
                }

                if (iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId);
                }
                else {
                    String text = getDisplayText();
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), snappingSize * 2 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(primaryColor);
                    if (style == 1) paint.setFakeBoldText(true);
                    canvas.drawText(text, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                    paint.setFakeBoldText(false);
                }
                break;
            }
            case COMBO_BUTTON: {
                float radius = snappingSize * 0.75f * scale;
                if (style != 1 && style != 2) drawButtonFill(canvas, paint, boundingBox, currentFillColor, fillOpacity, Shape.ROUND_RECT, scale);
                if (!hideBorder && style != 1) canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                float segmentWidth = boundingBox.width() / (float)Math.max(1, bindings.length);
                for (int i = 1; i < bindings.length; i++) {
                    float lineX = boundingBox.left + segmentWidth * i;
                    canvas.drawLine(lineX, boundingBox.top + strokeWidth, lineX, boundingBox.bottom - strokeWidth, paint);
                }

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(primaryColor);
                if (style == 1) paint.setFakeBoldText(true);
                paint.setTextAlign(Paint.Align.CENTER);
                for (int i = 0; i < bindings.length; i++) {
                    String itemText = getBindingAt(i).toString().replace("BUTTON ", "");
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, itemText, segmentWidth - strokeWidth * 2), snappingSize * 1.6f * scale));
                    float centerX = boundingBox.left + segmentWidth * i + segmentWidth * 0.5f;
                    canvas.drawText(itemText, centerX, y - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                    if (i < bindings.length - 1) {
                        paint.setTextSize(snappingSize * 1.2f * scale);
                        canvas.drawText("+", boundingBox.left + segmentWidth * (i + 1), y - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                    }
                }
                paint.setFakeBoldText(false);
                break;
            }
            case D_PAD: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float offsetX = snappingSize * 2 * scale;
                float offsetY = snappingSize * 3 * scale;
                float start = snappingSize * scale;
                Path path = inputControlsView.getPath();
                path.reset();

                path.moveTo(cx, cy - start);
                path.lineTo(cx - offsetX, cy - offsetY);
                path.lineTo(cx - offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, cy - offsetY);
                path.close();

                path.moveTo(cx - start, cy);
                path.lineTo(cx - offsetY, cy - offsetX);
                path.lineTo(boundingBox.left, cy - offsetX);
                path.lineTo(boundingBox.left, cy + offsetX);
                path.lineTo(cx - offsetY, cy + offsetX);
                path.close();

                path.moveTo(cx, cy + start);
                path.lineTo(cx - offsetX, cy + offsetY);
                path.lineTo(cx - offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, cy + offsetY);
                path.close();

                path.moveTo(cx + start, cy);
                path.lineTo(cx + offsetY, cy - offsetX);
                path.lineTo(boundingBox.right, cy - offsetX);
                path.lineTo(boundingBox.right, cy + offsetX);
                path.lineTo(cx + offsetY, cy + offsetX);
                path.close();

                if (style == 1) { // Glass: стеклянный крест (заливка + виньетка + обводка)
                    int[] g = computeGlassColors();
                    boolean engaged = isEngaged();

                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(g[0]);
                    canvas.drawPath(path, paint);
                    if (engaged) {
                        paint.setColor(g[2]);
                        canvas.drawPath(path, paint);
                    }

                    float gradR = Math.max(boundingBox.width(), boundingBox.height()) * 0.5f;
                    drawGlassPath(canvas, paint, path, cx, cy, gradR, engaged ? g[5] : g[4], boundingBox, snappingSize);

                    if (!hideBorder) {
                        paint.setShader(null);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(Math.max(2f, snappingSize * 0.18f));
                        paint.setStrokeJoin(Paint.Join.ROUND);
                        paint.setStrokeCap(Paint.Cap.ROUND);
                        paint.setColor(engaged ? g[3] : g[1]);
                        canvas.drawPath(path, paint);
                        paint.setStrokeJoin(Paint.Join.MITER);
                        paint.setStrokeCap(Paint.Cap.BUTT);
                    }
                }
                else {
                    canvas.drawPath(path, paint);
                }
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                int oldColor = paint.getColor();
                float radius = snappingSize * 0.75f * scale;
                float elementSize = scroller.getElementSize();
                float minTextSize = snappingSize * 2 * scale;
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                Path path = inputControlsView.getPath();
                path.reset();

                if (orientation == 0) {
                    float lineTop = boundingBox.top + strokeWidth * 0.5f;
                    float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
                    float startX = boundingBox.left;
                    if (style != 1) canvas.drawRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                    canvas.save();
                    path.addRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(path);
                    startX -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);

                        if (startX > boundingBox.left && startX  < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        String text = getRangeTextForIndex(range, index);

                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, elementSize - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();
                }
                else {
                    float lineLeft = boundingBox.left + strokeWidth * 0.5f;
                    float lineRight = boundingBox.right - strokeWidth * 0.5f;
                    float startY = boundingBox.top;
                    if (style != 1) canvas.drawRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                    canvas.save();
                    path.addRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(inputControlsView.getPath());
                    startY -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);

                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        String text = getRangeTextForIndex(range, i);

                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();
                }
                break;
            }
            case STICK: {
                int cx = boundingBox.centerX();  // Fixed outer circle center
                int cy = boundingBox.centerY();  // Fixed outer circle center
                int oldColor = paint.getColor();

                // Draw the outer circle (base of the stick). Для Glass контур уже нарисован стилем.
                if (style != 1) canvas.drawCircle(cx, cy, boundingBox.height() * 0.5f, paint);

                // Draw the inner thumbstick (current position based on gyroscope movement)
                float thumbstickX = getCurrentPosition().x;
                float thumbstickY = getCurrentPosition().y;

                short thumbRadius = (short) (snappingSize * 3.5f * scale); // Radius of the thumbstick
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(primaryColor, 50)); // Semi-transparent fill for thumbstick
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius, paint); // Draw thumbstick

                // Draw the thumbstick border
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(oldColor);
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius + strokeWidth * 0.5f, paint);

                if (iconId > 0) {
                    drawIcon(canvas, thumbstickX, thumbstickY, thumbRadius * 2, thumbRadius * 2, iconId);
                }
                break;
            }

            case TRACKPAD: {
                float radius = boundingBox.height() * 0.15f;
                // Для Glass контейнер с виньеткой уже нарисован стилем
                if (style != 1) {
                    canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                    float offset = strokeWidth * 2.5f;
                    float innerStrokeWidth = strokeWidth * 2;
                    float innerHeight = boundingBox.height() - offset * 2;
                    radius = (innerHeight / boundingBox.height()) * radius - (innerStrokeWidth * 0.5f + strokeWidth * 0.5f);
                    paint.setStrokeWidth(innerStrokeWidth);
                    canvas.drawRoundRect(boundingBox.left + offset, boundingBox.top + offset, boundingBox.right - offset, boundingBox.bottom - offset, radius, radius, paint);
                }

                if (iconId > 0) {
                    drawIcon(canvas, boundingBox.centerX(), boundingBox.centerY(), boundingBox.width() - strokeWidth * 5, boundingBox.height() - strokeWidth * 5, iconId);
                }
                break;
            }

            case STEERING_WHEEL: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float radius = boundingBox.width() * 0.4f;
                int oldColor = paint.getColor();

                // Draw outer circle (steering wheel rim). Для Glass контур уже нарисован стилем.
                if (style != 1) canvas.drawCircle(cx, cy, radius, paint);

                // Get current rotation angle from touch position
                float rotationAngle = 0f;
                if (currentPosition != null) {
                    float dx = currentPosition.x - cx;
                    float dy = currentPosition.y - cy;
                    rotationAngle = (float) Math.atan2(dy, dx);
                }

                // Draw steering wheel spokes (3 spokes at 120 degree intervals)
                for (int i = 0; i < 3; i++) {
                    float spokeAngle = rotationAngle + (i * 2.0f * (float) Math.PI / 3.0f);
                    float spokeStartX = cx + (float) Math.cos(spokeAngle) * radius * 0.3f;
                    float spokeStartY = cy + (float) Math.sin(spokeAngle) * radius * 0.3f;
                    float spokeEndX = cx + (float) Math.cos(spokeAngle) * radius * 0.9f;
                    float spokeEndY = cy + (float) Math.sin(spokeAngle) * radius * 0.9f;
                    canvas.drawLine(spokeStartX, spokeStartY, spokeEndX, spokeEndY, paint);
                }

                // Draw center hub
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(primaryColor, 100));
                canvas.drawCircle(cx, cy, radius * 0.2f, paint);

                // Draw center hub border
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(oldColor);
                canvas.drawCircle(cx, cy, radius * 0.2f, paint);

                // Draw rotation indicator (small dot on the rim)
                float indicatorX = cx + (float) Math.cos(rotationAngle) * radius * 0.8f;
                float indicatorY = cy + (float) Math.sin(rotationAngle) * radius * 0.8f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(inputControlsView.getSecondaryColor());
                canvas.drawCircle(indicatorX, indicatorY, strokeWidth * 2, paint);
                break;
            }
        }
        if (selected && inputControlsView.isEditMode()) drawSelectionOverlay(canvas, paint, boundingBox);
        canvas.restore();
    }

    // ---- Визуальные стили (0=Original, 1=Glass по умолчанию, 2=Shadow) ----

    // Шейдеры стекла (как в WinNative/GameHub). Рисование только на UI-потоке, статический кэш безопасен.
    private static Shader bloomShader;
    private static Shader edgeShadeShader;
    private static final Matrix shaderMatrix = new Matrix();

    private static Shader getBloomShader() {
        if (bloomShader == null) {
            bloomShader = new RadialGradient(0f, 0f, 1f,
                new int[] {0x8CFFFFFF, 0x3EFFFFFF, 0x00FFFFFF},
                new float[] {0f, 0.6f, 1f},
                Shader.TileMode.CLAMP);
        }
        return bloomShader;
    }

    private static Shader getEdgeShadeShader() {
        if (edgeShadeShader == null) {
            edgeShadeShader = new RadialGradient(0f, 0f, 1f, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
        }
        return edgeShadeShader;
    }

    private static void placeShader(Shader shader, float cx, float cy, float r) {
        shaderMatrix.reset();
        shaderMatrix.postScale(r, r);
        shaderMatrix.postTranslate(cx, cy);
        shader.setLocalMatrix(shaderMatrix);
    }

    // Стеклянная виньетка: та же форма, заполненная радиальным градиентом (прозрачный центр -> тёмные края)
    private void drawGlassShape(Canvas canvas, Paint paint, Rect boundingBox, int snappingSize, int alpha) {
        if (alpha <= 0) return;
        float cx = boundingBox.exactCenterX();
        float cy = boundingBox.exactCenterY();
        float gradR = Math.max(boundingBox.width(), boundingBox.height()) * 0.5f;
        drawGlassPath(canvas, paint, null, cx, cy, gradR, alpha, boundingBox, snappingSize);
    }

    private void drawGlassPath(Canvas canvas, Paint paint, Path path, float cx, float cy, float gradR, int alpha, Rect boundingBox, int snappingSize) {
        if (alpha <= 0) return;
        placeShader(getEdgeShadeShader(), cx, cy, gradR);
        paint.setShader(getEdgeShadeShader());
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(alpha);
        if (path != null) canvas.drawPath(path, paint);
        else drawBodyShape(canvas, paint, boundingBox, snappingSize);
        paint.setShader(null);
    }

    // Цвета стекла (как в WinNative/GameHub): [0]=тело, [1]=обводка, [2]=тело-нажатие, [3]=обводка-нажатие, [4]=виньетка, [5]=виньетка-нажатие
    private int[] computeGlassColors() {
        float effectiveOpacity = inputControlsView.isEditMode() ? Math.max(0.15f, opacity) : opacity;
        float overlayOpacity = inputControlsView.getOverlayOpacity();
        float dim = overlayOpacity <= 0.4f
            ? 0.28f + (overlayOpacity - 0.1f) * (0.5f / 0.3f)
            : 0.78f + (overlayOpacity - 0.4f) * (0.22f / 0.6f);
        boolean hasAccent = borderColor != Color.TRANSPARENT;

        int fillAlpha = (int)(90 * dim * effectiveOpacity);
        int strokeAlpha = (int)(150 * dim * effectiveOpacity);
        int glassEdgeAlpha = (int)(75 * dim * effectiveOpacity);
        int pressedFillAlpha = (int)(90 * dim * effectiveOpacity);
        int pressedStrokeAlpha = (int)(220 * dim * effectiveOpacity);

        return new int[] {
            Color.argb(fillAlpha, 0, 0, 0),
            hasAccent ? ColorUtils.setAlphaComponent(borderColor, Math.max(strokeAlpha, 110)) : Color.argb(strokeAlpha, 255, 255, 255),
            ColorUtils.setAlphaComponent(hasAccent ? borderColor : Color.WHITE, pressedFillAlpha),
            hasAccent ? ColorUtils.setAlphaComponent(borderColor, Math.max(pressedStrokeAlpha, 160)) : Color.argb(pressedStrokeAlpha, 255, 255, 255),
            glassEdgeAlpha * fillAlpha / 255,
            glassEdgeAlpha * pressedFillAlpha / 255
        };
    }

    private void drawStyleBody(Canvas canvas, Paint paint, Rect boundingBox, int snappingSize, int strokeColor, int currentFillColor, float strokeWidth) {
        int style = inputControlsView.getVisualStyle() == VisualStyle.GAMEHUB ? 1 : 0;

        if (style == 1) { // Glass (GameHub) — точный порт отрисовки WinNative
            if (type != Type.D_PAD) { // D-Pad рисует стекло сам (нужна форма креста)
                int[] g = computeGlassColors();
                boolean engaged = isEngaged();

                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(g[0]);
                drawBodyShape(canvas, paint, boundingBox, snappingSize);

                if (engaged) {
                    paint.setColor(g[2]);
                    drawBodyShape(canvas, paint, boundingBox, snappingSize);
                }

                drawGlassShape(canvas, paint, boundingBox, snappingSize, engaged ? g[5] : g[4]);

                if (!hideBorder) {
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(2f, snappingSize * 0.18f));
                    paint.setStrokeJoin(Paint.Join.ROUND);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setColor(engaged ? g[3] : g[1]);
                    drawBodyShape(canvas, paint, boundingBox, snappingSize);
                    paint.setStrokeJoin(Paint.Join.MITER);
                    paint.setStrokeCap(Paint.Cap.BUTT);
                }
            }
        }
        else if (style == 2) { // Shadow — мягкое тёмное тело с тенью
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.setAlphaComponent(0xff1b1b26, (int)(opacity * 210)));
            paint.setShadowLayer(snappingSize * 0.55f, 0, snappingSize * 0.15f, 0xAA000000);
            drawBodyShape(canvas, paint, boundingBox, snappingSize);
            paint.clearShadowLayer();
        }

        // Возвращаем исходное состояние кисти
        paint.setShader(null);
        paint.setColor(strokeColor);
        paint.setAlpha((int)(opacity * 255));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
    }

    private void drawBodyShape(Canvas canvas, Paint paint, Rect boundingBox, int snappingSize) {
        float cx = boundingBox.centerX();
        float cy = boundingBox.centerY();
        switch (type) {
            case BUTTON:
            case COMBO_BUTTON:
                switch (shape) {
                    case CIRCLE:
                        canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                        break;
                    case RECT:
                        canvas.drawRect(boundingBox, paint);
                        break;
                    case ROUND_RECT: {
                        float radius = boundingBox.height() * 0.5f;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                    case SQUARE: {
                        float radius = snappingSize * 0.75f * scale;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                }
                break;
            case STICK:
                canvas.drawCircle(cx, cy, boundingBox.height() * 0.5f, paint);
                break;
            case TRACKPAD:
            case RANGE_BUTTON: {
                float radius = boundingBox.height() * 0.15f;
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                break;
            }
            case STEERING_WHEEL:
                canvas.drawCircle(cx, cy, boundingBox.width() * 0.4f, paint);
                break;
            case D_PAD: {
                float tileX = boundingBox.width() * 0.17f;
                float tileY = boundingBox.height() * 0.17f;
                float radius = snappingSize * 0.3f;
                canvas.drawRoundRect(cx - tileX, boundingBox.top, cx + tileX, cy - tileY, radius, radius, paint);
                canvas.drawRoundRect(cx - tileX, cy + tileY, cx + tileX, boundingBox.bottom, radius, radius, paint);
                canvas.drawRoundRect(boundingBox.left, cy - tileX, cx - tileY, cy + tileX, radius, radius, paint);
                canvas.drawRoundRect(cx + tileY, cy - tileX, boundingBox.right, cy + tileX, radius, radius, paint);
                break;
            }
            default:
                break;
        }
    }

    private void drawButtonFill(Canvas canvas, Paint paint, Rect boundingBox, int fillColor, float fillOpacity, Shape shape, float scale) {
        if (fillOpacity <= 0.0f) return;

        int oldColor = paint.getColor();
        Paint.Style oldStyle = paint.getStyle();
        float radius = shape == Shape.ROUND_RECT ? boundingBox.height() * 0.5f : inputControlsView.getSnappingSize() * 0.75f * scale;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(fillColor, (int)(fillOpacity * opacity * 255)));
        switch (shape) {
            case CIRCLE:
                canvas.drawCircle(boundingBox.centerX(), boundingBox.centerY(), boundingBox.width() * 0.5f, paint);
                break;
            case RECT:
                canvas.drawRect(boundingBox, paint);
                break;
            case ROUND_RECT:
            case SQUARE:
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                break;
        }

        paint.setColor(oldColor);
        paint.setStyle(oldStyle);
    }

    private void drawSelectionOverlay(Canvas canvas, Paint paint, Rect boundingBox) {
        int snappingSize = inputControlsView.getSnappingSize();
        float handleRadius = snappingSize * 1.2f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(snappingSize * 0.18f);
        paint.setColor(inputControlsView.getSecondaryColor());
        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, snappingSize, snappingSize, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), 230));
        RectF resize = getResizeHandleBounds();
        RectF rotate = getRotateHandleBounds();
        canvas.drawOval(resize, paint);
        canvas.drawOval(rotate, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(snappingSize * 0.16f);
        paint.setColor(Color.WHITE);
        canvas.drawLine(resize.left + handleRadius * 0.55f, resize.bottom - handleRadius * 0.45f, resize.right - handleRadius * 0.45f, resize.top + handleRadius * 0.55f, paint);
        canvas.drawCircle(rotate.centerX(), rotate.centerY(), handleRadius * 0.45f, paint);
    }

    public RectF getResizeHandleBounds() {
        Rect box = getBoundingBox();
        float r = inputControlsView.getSnappingSize() * 1.2f;
        return new RectF(box.right - r, box.bottom - r, box.right + r, box.bottom + r);
    }

    public RectF getRotateHandleBounds() {
        Rect box = getBoundingBox();
        float r = inputControlsView.getSnappingSize() * 1.2f;
        return new RectF(box.centerX() - r, box.top - r * 3.0f, box.centerX() + r, box.top - r);
    }

    public boolean containsResizeHandle(float screenX, float screenY) {
        return getRotatedHandleBounds(getResizeHandleBounds()).contains(screenX, screenY);
    }

    public boolean containsRotateHandle(float screenX, float screenY) {
        return getRotatedHandleBounds(getRotateHandleBounds()).contains(screenX, screenY);
    }

    private RectF getRotatedHandleBounds(RectF localBounds) {
        float cx = localBounds.centerX();
        float cy = localBounds.centerY();
        if (rotation != 0.0f) {
            double radians = Math.toRadians(rotation);
            float dx = cx - x;
            float dy = cy - y;
            cx = x + (float)(dx * Math.cos(radians) - dy * Math.sin(radians));
            cy = y + (float)(dx * Math.sin(radians) + dy * Math.cos(radians));
        }

        float touchRadius = Math.max(localBounds.width(), inputControlsView.getSnappingSize() * 4.0f) * 0.5f;
        return new RectF(cx - touchRadius, cy - touchRadius, cx + touchRadius, cy + touchRadius);
    }

    private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId) {
        drawIcon(canvas, cx, cy, width, height, iconId, inputControlsView.getColorFilter());
    }

    private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId, ColorFilter tint) {
        Paint paint = inputControlsView.getPaint();
        Bitmap icon = inputControlsView.getIcon(iconId);
        
        // Only apply color filter to built-in icons, not custom or pack icons
        boolean isCustomOrPack = CustomIconManager.isCustomIcon(iconId) || IconPackManager.isPackIcon(iconId);
        if (!isCustomOrPack) {
            paint.setColorFilter(tint);
        }
        
        int margin = (int)(inputControlsView.getSnappingSize() * (shape == Shape.CIRCLE || shape == Shape.SQUARE ? 2.0f : 1.0f) * scale);
        int halfSize = (int)((Math.min(width, height) - margin) * 0.5f);

        if (icon != null) {
            Rect srcRect = new Rect(0, 0, icon.getWidth(), icon.getHeight());
            int scaledHalfSize = (int)(halfSize * this.iconScale);
            Rect dstRect = new Rect((int)(cx - scaledHalfSize), (int)(cy - scaledHalfSize), (int)(cx + scaledHalfSize), (int)(cy + scaledHalfSize));
            canvas.drawBitmap(icon, srcRect, dstRect, paint);
        }
        
        paint.setColorFilter(null);
    }

    public JSONObject toJSONObject() {
        try {
            JSONObject elementJSONObject = new JSONObject();
            elementJSONObject.put("type", type.name());
            elementJSONObject.put("shape", shape.name());

            JSONArray bindingsJSONArray = new JSONArray();
            for (Binding binding : bindings) bindingsJSONArray.put(binding.name());
            elementJSONObject.put("bindings", bindingsJSONArray);

            elementJSONObject.put("scale", Float.valueOf(scale));
            elementJSONObject.put("x", (float)x / inputControlsView.getMaxWidth());
            elementJSONObject.put("y", (float)y / inputControlsView.getMaxHeight());
            elementJSONObject.put("toggleSwitch", toggleSwitch);
            elementJSONObject.put("hideBorder", hideBorder);
            elementJSONObject.put("text", text);
            elementJSONObject.put("iconId", iconId);
            elementJSONObject.put("iconScale", iconScale);
            elementJSONObject.put("opacity", opacity);
            elementJSONObject.put("fillOpacity", fillOpacity);
            elementJSONObject.put("rotation", rotation);
            elementJSONObject.put("borderColor", borderColor);
            elementJSONObject.put("fillColor", fillColor);
            elementJSONObject.put("textColor", textColor);

            if (type == Type.RANGE_BUTTON && range != null) {
                elementJSONObject.put("range", range.name());
                if (orientation != 0) elementJSONObject.put("orientation", orientation);
            }
            return elementJSONObject;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public boolean containsPoint(float x, float y) {
        if (rotation != 0.0f) {
            double radians = Math.toRadians(-rotation);
            float dx = x - this.x;
            float dy = y - this.y;
            x = this.x + (float)(dx * Math.cos(radians) - dy * Math.sin(radians));
            y = this.y + (float)(dx * Math.sin(radians) + dy * Math.cos(radians));
        }
        return getBoundingBox().contains((int)(x + 0.5f), (int)(y + 0.5f));
    }

    private boolean isKeepButtonPressedAfterMinTime() {
        Binding binding = getBindingAt(0);
        return !toggleSwitch && (binding == Binding.GAMEPAD_BUTTON_L3 || binding == Binding.GAMEPAD_BUTTON_R3);
    }

    public boolean handleTouchDown(int pointerId, float x, float y) {
        if (currentPointerId == -1 && containsPoint(x, y)) {
            currentPointerId = pointerId;
            if (type == Type.BUTTON || type == Type.COMBO_BUTTON) {
                inputControlsView.invalidate();
                if (isKeepButtonPressedAfterMinTime()) touchTime = System.currentTimeMillis();
                if (!toggleSwitch || !selected) {
                    for (Binding binding : bindings) if (binding != Binding.NONE) inputControlsView.handleInputEvent(binding, true);
                }
                return true;
            }
            else if (type == Type.RANGE_BUTTON) {
                scroller.handleTouchDown(x, y);
                return true;
            }
            else {
                if (type == Type.TRACKPAD) {
                    if (currentPosition == null) currentPosition = new PointF();
                    currentPosition.set(x, y);
                    touchTime = System.currentTimeMillis();
                    accumulatedDelta = 0;
                }
                else if (type == Type.STEERING_WHEEL) {
                    if (currentPosition == null) currentPosition = new PointF();
                    Rect boundingBox = getBoundingBox();
                    currentPosition.set(x, y);
                }
                return handleTouchMove(pointerId, x, y);
            }
        }
        else return false;
    }

    public boolean handleTouchMove(int pointerId, float x, float y) {
        if (pointerId == currentPointerId && (type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.STEERING_WHEEL)) {
            inputControlsView.invalidate();
            float deltaX, deltaY;
            Rect boundingBox = getBoundingBox();
            float radius = boundingBox.width() * 0.5f;
            TouchpadView touchpadView =  inputControlsView.getTouchpadView();

            if (type == Type.TRACKPAD) {
                if (currentPosition == null) currentPosition = new PointF();
                float[] deltaPoint = touchpadView.computeDeltaPoint(currentPosition.x, currentPosition.y, x, y);
                deltaX = deltaPoint[0];
                deltaY = deltaPoint[1];
                accumulatedDelta += Math.abs(deltaX) + Math.abs(deltaY);
                currentPosition.set(x, y);
            }
            else {
                float localX = x - boundingBox.left;
                float localY = y - boundingBox.top;
                float offsetX = localX - radius;
                float offsetY = localY - radius;

                float distance = Mathf.lengthSq(radius - localX, radius - localY);
                if (distance > radius * radius) {
                    float angle = (float)Math.atan2(offsetY, offsetX);
                    offsetX = (float)(Math.cos(angle) * radius);
                    offsetY = (float)(Math.sin(angle) * radius);
                }

                deltaX = Mathf.clamp(offsetX / radius, -1, 1);
                deltaY = Mathf.clamp(offsetY / radius, -1, 1);
            }

            if (type == Type.STICK) {
                if (currentPosition == null) currentPosition = new PointF();
                currentPosition.x = boundingBox.left + deltaX * radius + radius;
                currentPosition.y = boundingBox.top + deltaY * radius + radius;
                final boolean[] states = {deltaY <= -STICK_DEAD_ZONE, deltaX >= STICK_DEAD_ZONE, deltaY >= STICK_DEAD_ZONE, deltaX <= -STICK_DEAD_ZONE};

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    if (binding.isGamepad()) {
                        value = Mathf.clamp(Math.max(0, Math.abs(value) - 0.01f) * Mathf.sign(value) * STICK_SENSITIVITY, -1, 1);
                        inputControlsView.handleInputEvent(binding, true, value);
                        this.states[i] = true;
                    }
                    else {
                        boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                        inputControlsView.handleInputEvent(binding, state, value);
                        this.states[i] = state;
                    }
                }

                inputControlsView.invalidate();
            }
            else if (type == Type.TRACKPAD) {
                final boolean[] states = {deltaY <= -TRACKPAD_MIN_SPEED, deltaX >= TRACKPAD_MIN_SPEED, deltaY >= TRACKPAD_MIN_SPEED, deltaX <= -TRACKPAD_MIN_SPEED};
                int cursorDx = 0;
                int cursorDy = 0;

                for (byte i = 0; i < 4; i++) {
                    float value = (i == 1 || i == 3 ? deltaX : deltaY);
                    Binding binding = getBindingAt(i);
                    if (binding.isGamepad()) {
                        if (interpolator == null) interpolator = new CubicBezierInterpolator();
                        if (Math.abs(value) > TRACKPAD_ACCELERATION_THRESHOLD) value *= STICK_SENSITIVITY;
                        interpolator.set(0.075f, 0.95f, 0.45f, 0.95f);
                        float interpolatedValue = interpolator.getInterpolation(Math.min(1.0f, Math.abs(value / TRACKPAD_MAX_SPEED)));
                        inputControlsView.handleInputEvent(binding, true, Mathf.clamp(interpolatedValue * Mathf.sign(value), -1, 1));
                        this.states[i] = true;
                    }
                    else {
                        if (Math.abs(value) > TouchpadView.CURSOR_ACCELERATION_THRESHOLD) value *= TouchpadView.CURSOR_ACCELERATION;
                        if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                            cursorDx = Mathf.roundPoint(value);
                        }
                        else if (binding == Binding.MOUSE_MOVE_UP || binding == Binding.MOUSE_MOVE_DOWN) {
                            cursorDy = Mathf.roundPoint(value);
                        }
                        else {
                            inputControlsView.handleInputEvent(binding, states[i], value);
                            this.states[i] = states[i];
                        }
                    }
                }

                if (cursorDx != 0 || cursorDy != 0)  {
                    XServer xServer = inputControlsView.getXServer();
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, cursorDx, cursorDy, 0);
                    else
                        inputControlsView.getXServer().injectPointerMoveDelta(cursorDx, cursorDy);
                }
            }
            else if (type == Type.STEERING_WHEEL) {
                // Update current position for wheel visualization
                if (currentPosition == null) currentPosition = new PointF();
                currentPosition.set(x, y);
                
                // Calculate rotation angle from center
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float angle = (float) Math.atan2(y - cy, x - cx);
                
                // Normalize angle to -1 to 1 range for horizontal steering
                float normalizedAngle = angle / (float) Math.PI; // -1 to 1
                
                // Apply sensitivity and dead zone
                float steerValue = Math.abs(normalizedAngle) > STICK_DEAD_ZONE ? normalizedAngle : 0f;
                
                // Map to left/right bindings (only horizontal steering)
                Binding leftBinding = getBindingAt(3);  // Left
                Binding rightBinding = getBindingAt(1); // Right
                
                // Reset previous states
                for (byte i = 0; i < states.length; i++) {
                    if (states[i]) {
                        inputControlsView.handleInputEvent(getBindingAt(i), false);
                        states[i] = false;
                    }
                }
                
                // Set new state based on steering direction
                if (steerValue > STICK_DEAD_ZONE) {
                    // Steering right
                    inputControlsView.handleInputEvent(rightBinding, true, steerValue);
                    states[1] = true;
                } else if (steerValue < -STICK_DEAD_ZONE) {
                    // Steering left
                    inputControlsView.handleInputEvent(leftBinding, true, Math.abs(steerValue));
                    states[3] = true;
                }
                
                inputControlsView.invalidate();
            }
            else {
                final boolean[] states = {deltaY <= -DPAD_DEAD_ZONE, deltaX >= DPAD_DEAD_ZONE, deltaY >= DPAD_DEAD_ZONE, deltaX <= -DPAD_DEAD_ZONE};

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                    inputControlsView.handleInputEvent(binding, state, value);
                    this.states[i] = state;
                }
            }

            return true;
        }
        else if (pointerId == currentPointerId && type == Type.RANGE_BUTTON) {
            scroller.handleTouchMove(x, y);
            return true;
        }
        else return false;
    }

    public boolean handleTouchUp(int pointerId) {
        if (pointerId == currentPointerId) {
            if (type == Type.BUTTON || type == Type.COMBO_BUTTON) {
                inputControlsView.invalidate();
                Binding binding = getBindingAt(0);
                if (isKeepButtonPressedAfterMinTime() && touchTime != null) {
                    selected = (System.currentTimeMillis() - (long)touchTime) > BUTTON_MIN_TIME_TO_KEEP_PRESSED;
                    if (!selected) inputControlsView.handleInputEvent(binding, false);
                    touchTime = null;
                }
                else if (!toggleSwitch || selected) {
                    for (Binding comboBinding : bindings) if (comboBinding != Binding.NONE) inputControlsView.handleInputEvent(comboBinding, false);
                }

                if (toggleSwitch) {
                    selected = !selected;
                }
            }
            else if (type == Type.RANGE_BUTTON || type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.STEERING_WHEEL) {
                if (type == Type.TRACKPAD) {
                    if (touchTime != null) {
                        long duration = System.currentTimeMillis() - (long) touchTime;
                        if (duration < 200 && accumulatedDelta < 15) {
                            Binding leftClick = Binding.MOUSE_LEFT_BUTTON;
                            inputControlsView.handleInputEvent(leftClick, true);
                            inputControlsView.postDelayed(() -> {
                                inputControlsView.handleInputEvent(leftClick, false);
                            }, 50);
                        }
                        touchTime = null;
                    }
                }

                for (byte i = 0; i < states.length; i++) {
                    if (states[i]) inputControlsView.handleInputEvent(getBindingAt(i), false);
                    states[i] = false;
                }

                if (type == Type.RANGE_BUTTON) {
                    scroller.handleTouchUp();
                }

                // Всегда перерисовываем: подсветка нажатия должна гаснуть при отпускании
                inputControlsView.invalidate();

                if (currentPosition != null) currentPosition = null;
            }
            currentPointerId = -1;
            return true;
        }
        return false;
    }

    public PointF getCurrentPosition() {
        if (currentPosition == null) {
            currentPosition = new PointF(x, y); // Initialize to the center (same as outer circle)
        }
        return currentPosition;
    }

    // New setter for current position to allow resetting
    public void setCurrentPosition(float x, float y) {
        if (currentPosition == null) {
            currentPosition = new PointF();
        }
        currentPosition.set(x, y);
        // Optionally invalidate the view to trigger a redraw
        inputControlsView.invalidate();
    }

    // =====================================================================
    // Стилевая отрисовка (порт WinNative): Slate, Halo, Glint, Reticle,
    // Neon, Lumina, Shadow. Общие кэши и помощники ниже.
    // =====================================================================

    private static final RectF tempRect = new RectF();
    private PorterDuffColorFilter cachedAccentFilter;
    private int cachedAccentFilterColor = 1;
    private CornerPathEffect cachedCornerEffect;
    private float cachedCornerRadius = -1f;

    private boolean isHighlighted() {
        return selected;
    }

    // Цвет акцента элемента (borderColor) или -1, если пользователь его не задал
    private int resolveAccentColor() {
        return borderColor != Color.TRANSPARENT ? borderColor : -1;
    }

    private int resolveThemedAccent() {
        int custom = resolveAccentColor();
        return custom != -1 ? custom : inputControlsView.getAccentTheme().accent;
    }

    private PorterDuffColorFilter accentFilter(int color) {
        if (cachedAccentFilterColor != color) {
            cachedAccentFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            cachedAccentFilterColor = color;
        }
        return cachedAccentFilter;
    }

    private CornerPathEffect cornerEffect(float radius) {
        if (cachedCornerEffect == null || cachedCornerRadius != radius) {
            cachedCornerEffect = new CornerPathEffect(radius);
            cachedCornerRadius = radius;
        }
        return cachedCornerEffect;
    }

    private void beginBloom(Paint paint, float cx, float cy, float r, int accent, float alpha) {
        Shader bloom = getBloomShader();
        placeShader(bloom, cx, cy, r);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(bloom);
        paint.setColorFilter(accentFilter(accent));
        paint.setAlpha((int) (255 * alpha));
    }

    private static void endBloom(Paint paint) {
        paint.setShader(null);
        paint.setColorFilter(null);
    }

    // Прозрачность стиля от overlayOpacity (как в WinNative)
    private float styleOpacity() {
        float effectiveOpacity = inputControlsView.isEditMode() ? Math.max(0.15f, opacity) : opacity;
        float overlayOpacity = inputControlsView.getOverlayOpacity();
        float dim = overlayOpacity <= 0.4f
            ? 0.28f + (overlayOpacity - 0.1f) * (0.5f / 0.3f)
            : 0.78f + (overlayOpacity - 0.4f) * (0.22f / 0.6f);
        return Mathf.clamp(dim, 0f, 1f) * effectiveOpacity;
    }

    // Пользовательский textColor перекрывает цвет подписи
    private int resolveLabelColor(int base) {
        if (textColor == Color.TRANSPARENT) return base;
        return ColorUtils.setAlphaComponent(textColor, (int) (Color.alpha(textColor) * (Color.alpha(base) / 255f)));
    }

    // Пользовательский fillColor перекрывает цвет тела
    private int resolveBodyColor(int base) {
        if (fillColor == Color.TRANSPARENT || fillOpacity <= 0f) return base;
        return ColorUtils.setAlphaComponent(fillColor, (int) (fillOpacity * 255 * (Color.alpha(base) / 255f)));
    }

    // COMBO_BUTTON в WinNative нет: разделители и подписи сегментов рисуем поверх тела стиля
    private void drawComboOverlay(Canvas canvas, Paint paint, Rect bb, int labelColor, float a) {
        if (type != Type.COMBO_BUTTON) return;
        int snappingSize = inputControlsView.getSnappingSize();
        float strokeWidth = Math.max(1f, snappingSize * 0.18f);
        float segmentWidth = bb.width() / (float)Math.max(1, bindings.length);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.argb((int) (60 * a), 255, 255, 255));
        for (int i = 1; i < bindings.length; i++) {
            float lineX = bb.left + segmentWidth * i;
            canvas.drawLine(lineX, bb.top + strokeWidth, lineX, bb.bottom - strokeWidth, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(labelColor);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < bindings.length; i++) {
            String itemText = getBindingAt(i).toString().replace("BUTTON ", "");
            paint.setTextSize(Math.min(getTextSizeForWidth(paint, itemText, segmentWidth - strokeWidth * 2), snappingSize * 1.6f * scale));
            float centerX = bb.left + segmentWidth * i + segmentWidth * 0.5f;
            canvas.drawText(itemText, centerX, y - ((paint.descent() + paint.ascent()) * 0.5f), paint);
        }
    }

    // STEERING_WHEEL в WinNative нет: обод + спицы + ступица в цветах стиля
    private void drawSteeringWheel(Canvas canvas, Paint paint, int accent, float a, float rimWidth) {
        Rect bb = getBoundingBox();
        float cx = bb.centerX();
        float cy = bb.centerY();
        float radius = bb.width() * 0.4f;
        float rotationAngle = 0f;
        if (currentPosition != null) rotationAngle = (float)Math.atan2(currentPosition.y - cy, currentPosition.x - cx);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(rimWidth);
        paint.setColor(accent);
        canvas.drawCircle(cx, cy, radius, paint);
        for (int i = 0; i < 3; i++) {
            float spokeAngle = rotationAngle + (i * 2.0f * (float)Math.PI / 3.0f);
            canvas.drawLine(
                cx + (float)Math.cos(spokeAngle) * radius * 0.3f,
                cy + (float)Math.sin(spokeAngle) * radius * 0.3f,
                cx + (float)Math.cos(spokeAngle) * radius * 0.9f,
                cy + (float)Math.sin(spokeAngle) * radius * 0.9f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (90 * a)));
        canvas.drawCircle(cx, cy, radius * 0.2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(accent);
        canvas.drawCircle(cx, cy, radius * 0.2f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(
            cx + (float)Math.cos(rotationAngle) * radius * 0.8f,
            cy + (float)Math.sin(rotationAngle) * radius * 0.8f, rimWidth * 1.6f, paint);
    }

    private float cleanCornerRadius(Rect bb) {
        switch (shape) {
            case ROUND_RECT:
                return bb.height() * 0.5f;
            case SQUARE:
                return Math.min(bb.width(), bb.height()) * 0.28f;
            case RECT:
                return bb.height() * 0.22f;
            default:
                return 0f;
        }
    }

    private void drawCleanBody(Canvas canvas, Paint paint, Rect bb, float inset) {
        if (shape == Shape.CIRCLE) {
            canvas.drawCircle(bb.exactCenterX(), bb.exactCenterY(), bb.width() * 0.5f - inset, paint);
        }
        else {
            float r = Math.max(2f, cleanCornerRadius(bb) - inset);
            canvas.drawRoundRect(bb.left + inset, bb.top + inset, bb.right - inset, bb.bottom - inset, r, r, paint);
        }
    }

    // Стрелка направления D-pad (0=вверх..3=влево)
    private static void buildDpadArrowPath(Path p, float ax, float ay, float t, int dir) {
        p.reset();
        if (dir == 0) {
            p.moveTo(ax, ay - t);
            p.lineTo(ax + 0.85f * t, ay + 0.65f * t);
            p.lineTo(ax - 0.85f * t, ay + 0.65f * t);
        }
        else if (dir == 1) {
            p.moveTo(ax + t, ay);
            p.lineTo(ax - 0.65f * t, ay + 0.85f * t);
            p.lineTo(ax - 0.65f * t, ay - 0.85f * t);
        }
        else if (dir == 2) {
            p.moveTo(ax, ay + t);
            p.lineTo(ax - 0.85f * t, ay - 0.65f * t);
            p.lineTo(ax + 0.85f * t, ay - 0.65f * t);
        }
        else {
            p.moveTo(ax - t, ay);
            p.lineTo(ax + 0.65f * t, ay - 0.85f * t);
            p.lineTo(ax + 0.65f * t, ay + 0.85f * t);
        }
        p.close();
    }

    private void drawCleanHotCell(
        Canvas canvas, Paint paint, VisualStyle style, int accent, float a, float hairline,
        float left, float top, float right, float bottom) {
        if (style == VisualStyle.GLINT) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(hairline * 2.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (240 * a)));
            float cx = (left + right) * 0.5f;
            float halfBar = (right - left) * 0.275f;
            float lineY = bottom - (bottom - top) * 0.12f;
            canvas.drawLine(cx - halfBar, lineY, cx + halfBar, lineY, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            return;
        }
        tempRect.set(left + 4, top + 5, right - 4, bottom - 5);
        float r = tempRect.height() * 0.5f;
        if (style == VisualStyle.HALO) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(hairline * 1.6f);
            paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (235 * a)));
        }
        else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (70 * a)));
        }
        canvas.drawRoundRect(tempRect, r, r, paint);
    }

    // SLATE/HALO/GLINT — плоские графитовые тела, акцент в обводке, индикаторах и подписях
    private void drawClean(Canvas canvas, VisualStyle style) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        float a = styleOpacity();
        boolean engaged = isEngaged();
        Rect boundingBox = getBoundingBox();
        int accent = resolveThemedAccent();
        int bodyColor = resolveBodyColor(Color.argb((int) (150 * a), 0x14, 0x18, 0x1F));
        Path path = inputControlsView.getPath();
        float hairline = Math.max(1.5f, snappingSize * 0.09f * scale);
        boolean halo = style == VisualStyle.HALO;
        boolean glint = style == VisualStyle.GLINT;

        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.BUTT);

        int frameColor;
        float frameWidth;
        if (halo) {
            frameColor = ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 250 : 215) * a));
            frameWidth = hairline * (engaged ? 2.8f : 1.9f);
        }
        else if (glint) {
            frameColor = engaged
                ? ColorUtils.setAlphaComponent(accent, (int) (245 * a))
                : Color.argb((int) (55 * a), 255, 255, 255);
            frameWidth = hairline * (engaged ? 1.8f : 1f);
        }
        else {
            frameColor = ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 240 : 150) * a));
            frameWidth = hairline * (engaged ? 1.6f : 1f);
        }
        if (isHighlighted() && resolveAccentColor() == -1) {
            frameColor = ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), (int) (235 * a));
        }
        if (hideBorder) frameColor = Color.TRANSPARENT;
        float frameInset = halo ? hairline * 2.5f : 0f;
        int labelColor = resolveLabelColor(
            (halo || engaged) ? Color.argb((int) (240 * a), 255, 255, 255) : ColorUtils.setAlphaComponent(accent, (int) (235 * a)));
        ColorFilter iconTint = (halo || engaged) ? inputControlsView.getColorFilter() : accentFilter(accent);

        switch (type) {
            case BUTTON:
            case COMBO_BUTTON: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(bodyColor);
                drawCleanBody(canvas, paint, boundingBox, 0f);
                if (engaged) {
                    if (!halo && !glint) {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (105 * a)));
                        drawCleanBody(canvas, paint, boundingBox, 0f);
                    }
                    float bloomRadius = Math.min(boundingBox.width(), boundingBox.height()) * 0.52f;
                    beginBloom(paint, cx, cy, bloomRadius, accent, a * 0.9f);
                    drawCleanBody(canvas, paint, boundingBox, 0f);
                    endBloom(paint);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(frameWidth);
                paint.setColor(frameColor);
                drawCleanBody(canvas, paint, boundingBox, frameInset);

                if (glint && !engaged) {
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeWidth(hairline * 1.9f);
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (220 * a)));
                    if (shape == Shape.CIRCLE) {
                        tempRect.set(
                            boundingBox.left + hairline, boundingBox.top + hairline,
                            boundingBox.right - hairline, boundingBox.bottom - hairline);
                        canvas.drawArc(tempRect, 62, 56, false, paint);
                    }
                    else {
                        float lineY = boundingBox.bottom - hairline * 2.2f;
                        float halfLength = boundingBox.width() * 0.21f;
                        canvas.drawLine(cx - halfLength, lineY, cx + halfLength, lineY, paint);
                    }
                    paint.setStrokeCap(Paint.Cap.BUTT);
                }

                if (type == Type.COMBO_BUTTON) {
                    drawComboOverlay(canvas, paint, boundingBox, labelColor, a);
                }
                else if (iconId > 0) {
                    paint.setColor(labelColor);
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, iconTint);
                }
                else {
                    String label = getDisplayText();
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(labelColor);
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, label, boundingBox.width() - hairline * 4), snappingSize * 2 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(label, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                break;
            }
            case STEERING_WHEEL: {
                drawSteeringWheel(canvas, paint, frameColor, a, frameWidth * 2f);
                break;
            }
            case D_PAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float base = Math.min(boundingBox.width(), boundingBox.height());
                float half = base * 0.5f;
                float arm = base * 0.17f;
                path.reset();
                path.moveTo(cx - arm, cy - half);
                path.lineTo(cx + arm, cy - half);
                path.lineTo(cx + arm, cy - arm);
                path.lineTo(cx + half, cy - arm);
                path.lineTo(cx + half, cy + arm);
                path.lineTo(cx + arm, cy + arm);
                path.lineTo(cx + arm, cy + half);
                path.lineTo(cx - arm, cy + half);
                path.lineTo(cx - arm, cy + arm);
                path.lineTo(cx - half, cy + arm);
                path.lineTo(cx - half, cy - arm);
                path.lineTo(cx - arm, cy - arm);
                path.close();

                paint.setPathEffect(cornerEffect(base * 0.09f));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(bodyColor);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(frameWidth);
                paint.setColor(frameColor);
                canvas.drawPath(path, paint);
                paint.setPathEffect(null);

                boolean hasStates = engaged && states.length >= 4;
                for (int i = 0; i < 4; i++) {
                    int dx = i == 1 ? 1 : i == 3 ? -1 : 0;
                    int dy = i == 2 ? 1 : i == 0 ? -1 : 0;
                    boolean hot = hasStates && states[i];
                    if (hot) {
                        float bx = cx + dx * half * 0.55f;
                        float by = cy + dy * half * 0.55f;
                        beginBloom(paint, bx, by, base * 0.24f, accent, a);
                        canvas.drawCircle(bx, by, base * 0.24f, paint);
                        endBloom(paint);
                    }
                    float ax = cx + dx * half * 0.62f;
                    float ay = cy + dy * half * 0.62f;
                    buildDpadArrowPath(path, ax, ay, base * 0.055f, i);
                    paint.setStyle(Paint.Style.FILL);
                    if (halo) {
                        paint.setColor(hot
                            ? ColorUtils.setAlphaComponent(accent, (int) (240 * a))
                            : Color.argb((int) (190 * a), 255, 255, 255));
                    }
                    else {
                        paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((hot ? 250 : 220) * a)));
                    }
                    canvas.drawPath(path, paint);
                }

                if (glint) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(hairline * 1.9f);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    float tickInset = hairline * 1.6f;
                    float span = arm * 0.45f;
                    for (int i = 0; i < 4; i++) {
                        boolean hot = hasStates && states[i];
                        paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((hot ? 245 : 170) * a)));
                        if (i == 0) {
                            canvas.drawLine(cx - span, cy - half + tickInset, cx + span, cy - half + tickInset, paint);
                        }
                        else if (i == 1) {
                            canvas.drawLine(cx + half - tickInset, cy - span, cx + half - tickInset, cy + span, paint);
                        }
                        else if (i == 2) {
                            canvas.drawLine(cx - span, cy + half - tickInset, cx + span, cy + half - tickInset, paint);
                        }
                        else {
                            canvas.drawLine(cx - half + tickInset, cy - span, cx - half + tickInset, cy + span, paint);
                        }
                    }
                    paint.setStrokeCap(Paint.Cap.BUTT);
                }
                break;
            }
            case STICK: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float r = boundingBox.height() * 0.5f;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb((int) (120 * a), 0x14, 0x18, 0x1F));
                canvas.drawCircle(cx, cy, r, paint);

                float thumbX = getCurrentPosition().x;
                float thumbY = getCurrentPosition().y;
                float thumbRadius = snappingSize * 3.5f * scale;

                if (engaged) {
                    beginBloom(paint, thumbX, thumbY, thumbRadius * 1.6f, accent, a * 0.8f);
                    canvas.drawCircle(thumbX, thumbY, thumbRadius * 1.6f, paint);
                    endBloom(paint);
                }

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(frameWidth);
                paint.setColor(frameColor);
                canvas.drawCircle(cx, cy, r - frameInset, paint);

                float guideWidth = Math.max(2f, snappingSize * 0.14f * scale);
                paint.setStrokeWidth(guideWidth);
                if (glint && !engaged) {
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (110 * a)));
                    float arcRadius = r - hairline;
                    tempRect.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius);
                    canvas.drawArc(tempRect, 62, 56, false, paint);
                    paint.setStrokeCap(Paint.Cap.BUTT);
                }
                else {
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 220 : 110) * a)));
                    canvas.drawCircle(cx, cy, r * 0.58f, paint);
                }

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb((int) (200 * a), 0x1C, 0x22, 0x2B));
                canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                if (glint) {
                    paint.setStrokeWidth(hairline);
                    paint.setColor(Color.argb((int) (55 * a), 255, 255, 255));
                    canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (240 * a)));
                    canvas.drawCircle(thumbX, thumbY, Math.max(2.5f, snappingSize * 0.3f), paint);
                }
                else {
                    paint.setStrokeWidth(hairline * (halo ? 2.2f : 1.4f));
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 245 : 150) * a)));
                    canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
                }
                break;
            }
            case TRACKPAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float radius = boundingBox.height() * 0.18f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(bodyColor);
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(frameWidth);
                paint.setColor(frameColor);
                if (halo) {
                    float in = frameInset;
                    float fr = Math.max(2f, radius - in);
                    canvas.drawRoundRect(
                        boundingBox.left + in, boundingBox.top + in,
                        boundingBox.right - in, boundingBox.bottom - in, fr, fr, paint);
                }
                else {
                    canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                }
                if (glint && !engaged) {
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeWidth(hairline * 1.9f);
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (220 * a)));
                    float lineY = boundingBox.bottom - hairline * 2.2f;
                    float halfLength = boundingBox.width() * 0.175f;
                    canvas.drawLine(cx - halfLength, lineY, cx + halfLength, lineY, paint);
                    paint.setStrokeCap(Paint.Cap.BUTT);
                }

                float s = Math.min(boundingBox.width(), boundingBox.height()) * 0.45f;
                float glyphW = 0.42f * s;
                float glyphH = 0.62f * s;
                float glyphRadius = glyphW * 0.5f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, s * 0.035f));
                paint.setColor(ColorUtils.setAlphaComponent(accent, (int) ((engaged ? 220 : 110) * a)));
                canvas.drawRoundRect(
                    cx - glyphW * 0.5f, cy - glyphH * 0.5f, cx + glyphW * 0.5f, cy + glyphH * 0.5f,
                    glyphRadius, glyphRadius, paint);
                canvas.drawLine(cx, cy - 0.30f * glyphH, cx, cy - 0.02f * glyphH, paint);
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                float rr = (orientation == 0 ? boundingBox.height() : boundingBox.width()) * 0.45f;
                float elementSize = scroller.getElementSize();
                float minTextSize = snappingSize * 2 * scale;
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(bodyColor);
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, rr, rr, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(frameWidth);
                paint.setColor(frameColor);
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, rr, rr, paint);

                canvas.save();
                path.reset();
                path.addRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, rr, rr, Path.Direction.CW);
                canvas.clipPath(path);

                int pressedIndex = currentPointerId != -1 && !scroller.isScrolling() ? scroller.getBindingIndex() : -1;
                int dividerColor = Color.argb((int) (45 * a), 255, 255, 255);
                float dividerWidth = Math.max(1f, snappingSize * 0.06f);
                int hotTextColor = Color.argb((int) (240 * a), 255, 255, 255);
                int cellTextColor = ColorUtils.setAlphaComponent(accent, (int) (225 * a));

                if (orientation == 0) {
                    float lineTop = boundingBox.top + boundingBox.height() * 0.25f;
                    float lineBottom = boundingBox.bottom - boundingBox.height() * 0.25f;
                    float startX = boundingBox.left - (scrollOffset % elementSize);

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dividerWidth);
                        paint.setColor(dividerColor);
                        if (startX > boundingBox.left && startX < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);

                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                drawCleanHotCell(canvas, paint, style, accent, a, hairline,
                                    startX, boundingBox.top, startX + elementSize, boundingBox.bottom);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : cellTextColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, elementSize - hairline * 4), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }
                }
                else {
                    float lineLeft = boundingBox.left + boundingBox.width() * 0.25f;
                    float lineRight = boundingBox.right - boundingBox.width() * 0.25f;
                    float startY = boundingBox.top - (scrollOffset % elementSize);

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dividerWidth);
                        paint.setColor(dividerColor);
                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);

                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                drawCleanHotCell(canvas, paint, style, accent, a, hairline,
                                    boundingBox.left, startY, boundingBox.right, startY + elementSize);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : cellTextColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, boundingBox.width() - hairline * 4), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }
                }
                canvas.restore();
                break;
            }
            default:
                break;
        }
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private static void buildChamferRectPath(Path p, float l, float t, float r, float b, float c) {
        p.reset();
        p.moveTo(l + c, t);
        p.lineTo(r - c, t);
        p.lineTo(r, t + c);
        p.lineTo(r, b - c);
        p.lineTo(r - c, b);
        p.lineTo(l + c, b);
        p.lineTo(l, b - c);
        p.lineTo(l, t + c);
        p.close();
    }

    // Плоский шестиугольник (вершины каждые 60 градусов)
    private static void buildHexagonPath(Path p, float cx, float cy, float r) {
        p.reset();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            float px = cx + (float)(r * Math.cos(angle));
            float py = cy + (float)(r * Math.sin(angle));
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.close();
    }

    // Одна гранёная лопасть D-pad, строится для "вверх" и поворачивается на место
    private static void buildReticleArmPath(
        Path p, float cx, float cy, float half, float armHalf, float gap, float chamfer, int dir) {
        p.reset();
        p.moveTo(cx - armHalf + chamfer, cy - half);
        p.lineTo(cx + armHalf - chamfer, cy - half);
        p.lineTo(cx + armHalf, cy - half + chamfer);
        p.lineTo(cx + armHalf, cy - gap - armHalf);
        p.lineTo(cx, cy - gap);
        p.lineTo(cx - armHalf, cy - gap - armHalf);
        p.lineTo(cx - armHalf, cy - half + chamfer);
        p.close();
        if (dir != 0) {
            shaderMatrix.setRotate(90f * dir, cx, cy);
            p.transform(shaderMatrix);
        }
    }

    private static void drawCornerBrackets(Canvas canvas, Paint paint, Rect bb, float len) {
        canvas.drawLine(bb.left, bb.top, bb.left + len, bb.top, paint);
        canvas.drawLine(bb.left, bb.top, bb.left, bb.top + len, paint);
        canvas.drawLine(bb.right, bb.top, bb.right - len, bb.top, paint);
        canvas.drawLine(bb.right, bb.top, bb.right, bb.top + len, paint);
        canvas.drawLine(bb.left, bb.bottom, bb.left + len, bb.bottom, paint);
        canvas.drawLine(bb.left, bb.bottom, bb.left, bb.bottom - len, paint);
        canvas.drawLine(bb.right, bb.bottom, bb.right - len, bb.bottom, paint);
        canvas.drawLine(bb.right, bb.bottom, bb.right, bb.bottom - len, paint);
    }

    // RETICLE — открытая hairline-графика: гранёные рамки и акцентные скобки, растущие при нажатии
    private void drawReticle(Canvas canvas) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        float a = styleOpacity();
        boolean engaged = isEngaged();
        Rect boundingBox = getBoundingBox();
        int accent = resolveThemedAccent();
        if (isHighlighted() && resolveAccentColor() == -1) {
            accent = ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), 255);
        }
        float hairline = Math.max(1.5f, snappingSize * 0.09f * scale);
        float bracket = hairline * 2.2f;
        Path path = inputControlsView.getPath();
        int lineColor = hideBorder ? Color.TRANSPARENT : Color.argb((int) (185 * a), 255, 255, 255);
        int scrimColor = resolveBodyColor(Color.argb((int) (24 * a), 0, 0, 0));
        int accentIdle = ColorUtils.setAlphaComponent(accent, (int) (220 * a));
        int accentFull = ColorUtils.setAlphaComponent(accent, (int) (250 * a));
        int labelColor = resolveLabelColor(engaged ? Color.argb((int) (245 * a), 255, 255, 255) : accentIdle);
        int ghostFill = ColorUtils.setAlphaComponent(accent, (int) (46 * a));
        float minTextSize = snappingSize * 1.9f * scale;
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);

        switch (type) {
            case BUTTON:
            case COMBO_BUTTON: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float w = boundingBox.width();
                float h = boundingBox.height();
                if (shape == Shape.CIRCLE) {
                    float r = w * 0.5f - hairline;
                    buildHexagonPath(path, cx, cy, r);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(scrimColor);
                    canvas.drawPath(path, paint);
                    if (engaged) {
                        paint.setColor(ghostFill);
                        canvas.drawPath(path, paint);
                    }
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(hairline);
                    paint.setColor(lineColor);
                    canvas.drawPath(path, paint);
                    if (engaged) {
                        buildHexagonPath(path, cx, cy, r - hairline * 3f);
                        paint.setColor(accentFull);
                        canvas.drawPath(path, paint);
                    }
                    float tick = r * (engaged ? 0.30f : 0.18f);
                    paint.setStrokeWidth(bracket * (engaged ? 1.3f : 1f));
                    paint.setColor(engaged ? accentFull : accentIdle);
                    canvas.drawLine(cx - r, cy, cx - r - tick, cy, paint);
                    canvas.drawLine(cx + r, cy, cx + r + tick, cy, paint);
                }
                else {
                    float chamfer = shape == Shape.SQUARE ? snappingSize * 0.9f * scale : h * 0.35f;
                    buildChamferRectPath(path, boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, chamfer);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(scrimColor);
                    canvas.drawPath(path, paint);
                    if (engaged) {
                        paint.setColor(ghostFill);
                        canvas.drawPath(path, paint);
                    }
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(hairline);
                    paint.setColor(lineColor);
                    canvas.drawPath(path, paint);
                    float len = Math.min(w, h) * (engaged ? 0.34f : 0.22f);
                    paint.setStrokeWidth(engaged ? hairline * 3f : bracket);
                    paint.setColor(engaged ? accentFull : accentIdle);
                    if (shape == Shape.SQUARE) {
                        drawCornerBrackets(canvas, paint, boundingBox, len);
                    }
                    else {
                        canvas.drawLine(boundingBox.left, boundingBox.bottom - chamfer, boundingBox.left + chamfer, boundingBox.bottom, paint);
                        canvas.drawLine(boundingBox.left, boundingBox.top + chamfer, boundingBox.left + chamfer, boundingBox.top, paint);
                        canvas.drawLine(boundingBox.right, boundingBox.bottom - chamfer, boundingBox.right - chamfer, boundingBox.bottom, paint);
                        canvas.drawLine(boundingBox.right, boundingBox.top + chamfer, boundingBox.right - chamfer, boundingBox.top, paint);
                    }
                }
                if (type == Type.COMBO_BUTTON) {
                    drawComboOverlay(canvas, paint, boundingBox, labelColor, a);
                }
                else if (iconId > 0) {
                    drawIcon(canvas, cx, cy, w, h, iconId, engaged ? inputControlsView.getColorFilter() : accentFilter(accent));
                }
                else {
                    String label = getDisplayText();
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(labelColor);
                    paint.setLetterSpacing(0.08f);
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, label, w * 0.62f), minTextSize));
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(label, cx, (cy - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                    paint.setLetterSpacing(0f);
                }
                break;
            }
            case STEERING_WHEEL: {
                drawSteeringWheel(canvas, paint, accentIdle, a, bracket);
                break;
            }
            case D_PAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float base = Math.min(boundingBox.width(), boundingBox.height());
                float half = base * 0.5f;
                float armHalf = base * 0.15f;
                float gap = base * 0.10f;
                float chamfer = armHalf * 0.56f;
                boolean hasStates = engaged && states.length >= 4;
                for (int i = 0; i < 4; i++) {
                    boolean hot = hasStates && states[i];
                    buildReticleArmPath(path, cx, cy, half, armHalf, gap, chamfer, i);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(hot ? ghostFill : scrimColor);
                    canvas.drawPath(path, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(hairline);
                    paint.setColor(lineColor);
                    canvas.drawPath(path, paint);
                    int dx = i == 1 ? 1 : i == 3 ? -1 : 0;
                    int dy = i == 2 ? 1 : i == 0 ? -1 : 0;
                    float pos = gap + 0.62f * (half - gap);
                    float chevSize = base * 0.045f;
                    paint.setStrokeWidth(bracket);
                    paint.setColor(hot ? Color.argb((int) (250 * a), 255, 255, 255) : accentIdle);
                    int chevrons = hot ? 2 : 1;
                    for (int k = 0; k < chevrons; k++) {
                        float off = pos + (k - (chevrons - 1) * 0.5f) * chevSize * 2.4f;
                        float ax = cx + dx * off;
                        float ay = cy + dy * off;
                        if (dy != 0) {
                            canvas.drawLine(ax - chevSize, ay - dy * chevSize, ax, ay + dy * chevSize, paint);
                            canvas.drawLine(ax + chevSize, ay - dy * chevSize, ax, ay + dy * chevSize, paint);
                        }
                        else {
                            canvas.drawLine(ax - dx * chevSize, ay - chevSize, ax + dx * chevSize, ay, paint);
                            canvas.drawLine(ax - dx * chevSize, ay + chevSize, ax + dx * chevSize, ay, paint);
                        }
                    }
                }
                float dm = base * 0.045f;
                path.reset();
                path.moveTo(cx, cy - dm);
                path.lineTo(cx + dm, cy);
                path.lineTo(cx, cy + dm);
                path.lineTo(cx - dm, cy);
                path.close();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline);
                paint.setColor(accentIdle);
                canvas.drawPath(path, paint);
                break;
            }
            case STICK: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float r = boundingBox.height() * 0.5f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(scrimColor);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline);
                paint.setColor(lineColor);
                canvas.drawCircle(cx, cy, r, paint);
                PointF pos = getCurrentPosition();
                float dxp = pos.x - cx;
                float dyp = pos.y - cy;
                int hotTick = -1;
                if (engaged && (dxp != 0f || dyp != 0f)) {
                    hotTick = Math.abs(dxp) >= Math.abs(dyp) ? (dxp >= 0 ? 1 : 3) : (dyp >= 0 ? 2 : 0);
                }
                float tickLen = snappingSize * 0.8f * scale;
                paint.setStrokeWidth(bracket);
                for (int i = 0; i < 4; i++) {
                    int dx = i == 1 ? 1 : i == 3 ? -1 : 0;
                    int dy = i == 2 ? 1 : i == 0 ? -1 : 0;
                    paint.setColor(i == hotTick ? accentFull : accentIdle);
                    canvas.drawLine(
                        cx + dx * (r - tickLen * 0.5f), cy + dy * (r - tickLen * 0.5f),
                        cx + dx * (r + tickLen * 0.5f), cy + dy * (r + tickLen * 0.5f), paint);
                }
                tempRect.set(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f);
                paint.setStrokeWidth(hairline);
                paint.setColor(engaged ? accentFull : accentIdle);
                for (int i = 0; i < 8; i++) canvas.drawArc(tempRect, i * 45f - 9f, 18f, false, paint);
                if (engaged) {
                    paint.setColor(accentFull);
                    canvas.drawLine(cx, cy, pos.x, pos.y, paint);
                }
                float thumbR = snappingSize * 2.2f * scale;
                buildHexagonPath(path, pos.x, pos.y, thumbR);
                if (engaged) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (90 * a)));
                    canvas.drawPath(path, paint);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline);
                paint.setColor(engaged ? accentFull : accentIdle);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(lineColor);
                canvas.drawCircle(pos.x, pos.y, hairline, paint);
                break;
            }
            case TRACKPAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float chamfer = boundingBox.height() * 0.22f;
                buildChamferRectPath(path, boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, chamfer);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(scrimColor);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline);
                paint.setColor(lineColor);
                canvas.drawPath(path, paint);
                float len = Math.min(boundingBox.width(), boundingBox.height()) * 0.22f;
                paint.setStrokeWidth(bracket);
                paint.setColor(accentIdle);
                drawCornerBrackets(canvas, paint, boundingBox, len);
                float gap = hairline * 2.2f;
                float cross = snappingSize * 1.2f * scale;
                paint.setStrokeWidth(hairline);
                paint.setColor(engaged ? accentFull : accentIdle);
                canvas.drawLine(cx - cross, cy, cx - gap, cy, paint);
                canvas.drawLine(cx + gap, cy, cx + cross, cy, paint);
                canvas.drawLine(cx, cy - cross, cx, cy - gap, paint);
                canvas.drawLine(cx, cy + gap, cx, cy + cross, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, hairline, paint);
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                float chamfer = (orientation == 0 ? boundingBox.height() : boundingBox.width()) * 0.30f;
                float elementSize = scroller.getElementSize();
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                buildChamferRectPath(path, boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, chamfer);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(scrimColor);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline);
                paint.setColor(lineColor);
                canvas.drawPath(path, paint);

                canvas.save();
                canvas.clipPath(path);
                int pressedIndex = currentPointerId != -1 && !scroller.isScrolling() ? scroller.getBindingIndex() : -1;
                int dividerColor = Color.argb((int) (70 * a), 255, 255, 255);
                int hotTextColor = Color.argb((int) (245 * a), 255, 255, 255);
                paint.setLetterSpacing(0.08f);

                if (orientation == 0) {
                    float lineTop = boundingBox.top + boundingBox.height() * 0.225f;
                    float lineBottom = boundingBox.bottom - boundingBox.height() * 0.225f;
                    float startX = boundingBox.left - (scrollOffset % elementSize);
                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(hairline * 0.8f);
                        paint.setColor(dividerColor);
                        if (startX > boundingBox.left && startX < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                paint.setStrokeWidth(hairline * 2.5f);
                                paint.setColor(accentFull);
                                float uy = boundingBox.bottom - boundingBox.height() * 0.15f;
                                canvas.drawLine(startX + elementSize * 0.2f, uy, startX + elementSize * 0.8f, uy, paint);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : accentIdle);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, elementSize - snappingSize * 0.5f), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }
                }
                else {
                    float lineLeft = boundingBox.left + boundingBox.width() * 0.225f;
                    float lineRight = boundingBox.right - boundingBox.width() * 0.225f;
                    float startY = boundingBox.top - (scrollOffset % elementSize);
                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(hairline * 0.8f);
                        paint.setColor(dividerColor);
                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                paint.setStrokeWidth(hairline * 2.5f);
                                paint.setColor(accentFull);
                                float ux = boundingBox.right - boundingBox.width() * 0.15f;
                                canvas.drawLine(ux, startY + elementSize * 0.2f, ux, startY + elementSize * 0.8f, paint);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : accentIdle);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, boundingBox.width() - snappingSize * 0.5f), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }
                }
                paint.setLetterSpacing(0f);
                canvas.restore();
                break;
            }
            default:
                break;
        }
    }

    private static final int NEON_SCRIM = 0xFF0A0A16;
    private static final float[] hsvTemp = new float[3];

    // Контрастный партнёр акцента (тон повёрнут на 150 градусов)
    private static int neonSecondary(int accent) {
        Color.colorToHSV(accent, hsvTemp);
        hsvTemp[0] = (hsvTemp[0] + 150f) % 360f;
        return Color.HSVToColor(Color.alpha(accent), hsvTemp);
    }

    // Слоистое свечение вокруг path: широкие тусклые гало под чётким ядром
    private void neonStrokePath(
        Canvas canvas, Paint paint, Path p, int color, float core, float a, boolean pressed) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(ColorUtils.setAlphaComponent(color, (int) (18 * a)));
        paint.setStrokeWidth(core * 4.2f);
        canvas.drawPath(p, paint);
        paint.setColor(ColorUtils.setAlphaComponent(color, (int) (36 * a)));
        paint.setStrokeWidth(core * 2.6f);
        canvas.drawPath(p, paint);
        paint.setColor(ColorUtils.setAlphaComponent(color, (int) (66 * a)));
        paint.setStrokeWidth(core * 1.6f);
        canvas.drawPath(p, paint);
        if (pressed) {
            paint.setColor(ColorUtils.setAlphaComponent(color, (int) (26 * a)));
            paint.setStrokeWidth(core * 6f);
            canvas.drawPath(p, paint);
        }
        paint.setColor(ColorUtils.setAlphaComponent(color, (int) (235 * a)));
        paint.setStrokeWidth(core);
        canvas.drawPath(p, paint);
        if (pressed) {
            paint.setColor(Color.argb((int) (235 * a), 255, 255, 255));
            paint.setStrokeWidth(core * 0.55f);
            canvas.drawPath(p, paint);
        }
    }

    // NEON — полые гранёные каркасы со слоистым свечением, при нажатии ядро белеет
    private void drawNeon(Canvas canvas) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        float a = styleOpacity();
        boolean engaged = isEngaged();
        Rect boundingBox = getBoundingBox();
        int accent = resolveThemedAccent();
        if (isHighlighted() && resolveAccentColor() == -1) {
            accent = ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), 255);
        }
        int secondary = neonSecondary(accent);
        Path path = inputControlsView.getPath();
        float core = Math.max(1.5f, snappingSize * 0.14f * scale);
        int scrimColor = resolveBodyColor(ColorUtils.setAlphaComponent(NEON_SCRIM, (int) (61 * a)));
        int washColor = ColorUtils.setAlphaComponent(accent, (int) (33 * a));
        int labelColor = resolveLabelColor(engaged
            ? Color.argb((int) (245 * a), 255, 255, 255)
            : ColorUtils.setAlphaComponent(accent, (int) (235 * a)));
        float minTextSize = snappingSize * 2 * scale;
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        switch (type) {
            case BUTTON:
            case COMBO_BUTTON: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float w = boundingBox.width();
                float h = boundingBox.height();
                if (shape == Shape.CIRCLE) {
                    float r = w * 0.5f - core;
                    buildChamferRectPath(path, cx - r, cy - r, cx + r, cy + r, r * 0.586f);
                }
                else {
                    float chamfer = shape == Shape.SQUARE
                        ? snappingSize * 0.8f * scale
                        : shape == Shape.RECT ? h * 0.25f : h * 0.30f;
                    buildChamferRectPath(path, boundingBox.left + core, boundingBox.top + core,
                        boundingBox.right - core, boundingBox.bottom - core, chamfer);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(scrimColor);
                canvas.drawPath(path, paint);
                if (engaged) {
                    paint.setColor(washColor);
                    canvas.drawPath(path, paint);
                }
                if (!hideBorder) neonStrokePath(canvas, paint, path, accent, core, a, engaged);
                if (shape == Shape.CIRCLE) {
                    float r = w * 0.5f - core;
                    tempRect.set(cx - r - core * 1.8f, cy - r - core * 1.8f, cx + r + core * 1.8f, cy + r + core * 1.8f);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(core * 0.7f);
                    paint.setColor(ColorUtils.setAlphaComponent(secondary, (int) (150 * a)));
                    canvas.drawArc(tempRect, -80f, 60f, false, paint);
                }
                if (type == Type.COMBO_BUTTON) {
                    drawComboOverlay(canvas, paint, boundingBox, labelColor, a);
                }
                else if (iconId > 0) {
                    drawIcon(canvas, cx, cy, w, h, iconId, engaged ? inputControlsView.getColorFilter() : accentFilter(accent));
                }
                else {
                    String label = getDisplayText();
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(labelColor);
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, label, w * 0.62f), minTextSize));
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(label, cx, (cy - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                break;
            }
            case STEERING_WHEEL: {
                drawSteeringWheel(canvas, paint, accent, a, core * 1.6f);
                break;
            }
            case D_PAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float base = Math.min(boundingBox.width(), boundingBox.height());
                float half = base * 0.5f - core;
                float arm = base * 0.17f;
                float ch = base * 0.075f;
                path.reset();
                path.moveTo(cx - arm, cy - half + ch);
                path.lineTo(cx - arm + ch, cy - half);
                path.lineTo(cx + arm - ch, cy - half);
                path.lineTo(cx + arm, cy - half + ch);
                path.lineTo(cx + arm, cy - arm);
                path.lineTo(cx + half - ch, cy - arm);
                path.lineTo(cx + half, cy - arm + ch);
                path.lineTo(cx + half, cy + arm - ch);
                path.lineTo(cx + half - ch, cy + arm);
                path.lineTo(cx + arm, cy + arm);
                path.lineTo(cx + arm, cy + half - ch);
                path.lineTo(cx + arm - ch, cy + half);
                path.lineTo(cx - arm + ch, cy + half);
                path.lineTo(cx - arm, cy + half - ch);
                path.lineTo(cx - arm, cy + arm);
                path.lineTo(cx - half + ch, cy + arm);
                path.lineTo(cx - half, cy + arm - ch);
                path.lineTo(cx - half, cy - arm + ch);
                path.lineTo(cx - half + ch, cy - arm);
                path.lineTo(cx - arm, cy - arm);
                path.close();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(scrimColor);
                canvas.drawPath(path, paint);
                if (!hideBorder) neonStrokePath(canvas, paint, path, accent, core, a, false);

                boolean hasStates = engaged && states.length >= 4;
                float chev = base * 0.043f;
                for (int i = 0; i < 4; i++) {
                    boolean hot = hasStates && states[i];
                    int dx = i == 1 ? 1 : i == 3 ? -1 : 0;
                    int dy = i == 2 ? 1 : i == 0 ? -1 : 0;
                    float ax = cx + dx * half * 0.60f;
                    float ay = cy + dy * half * 0.60f;
                    paint.setStyle(Paint.Style.STROKE);
                    if (hot) {
                        paint.setStrokeWidth(core * 2.8f);
                        paint.setColor(ColorUtils.setAlphaComponent(secondary, (int) (90 * a)));
                        if (dy != 0) {
                            canvas.drawLine(ax - chev, ay - dy * chev, ax, ay + dy * chev, paint);
                            canvas.drawLine(ax + chev, ay - dy * chev, ax, ay + dy * chev, paint);
                        }
                        else {
                            canvas.drawLine(ax - dx * chev, ay - chev, ax + dx * chev, ay, paint);
                            canvas.drawLine(ax - dx * chev, ay + chev, ax + dx * chev, ay, paint);
                        }
                    }
                    paint.setStrokeWidth(core);
                    paint.setColor(hot
                        ? Color.argb((int) (245 * a), 255, 255, 255)
                        : ColorUtils.setAlphaComponent(secondary, (int) (190 * a)));
                    if (dy != 0) {
                        canvas.drawLine(ax - chev, ay - dy * chev, ax, ay + dy * chev, paint);
                        canvas.drawLine(ax + chev, ay - dy * chev, ax, ay + dy * chev, paint);
                    }
                    else {
                        canvas.drawLine(ax - dx * chev, ay - chev, ax + dx * chev, ay, paint);
                        canvas.drawLine(ax - dx * chev, ay + chev, ax + dx * chev, ay, paint);
                    }
                }
                float dm = base * 0.055f;
                path.reset();
                path.moveTo(cx, cy - dm);
                path.lineTo(cx + dm, cy);
                path.lineTo(cx, cy + dm);
                path.lineTo(cx - dm, cy);
                path.close();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(core * 0.7f);
                paint.setColor(ColorUtils.setAlphaComponent(secondary, (int) (100 * a)));
                canvas.drawPath(path, paint);
                break;
            }
            case STICK: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float r = boundingBox.height() * 0.5f - core;
                path.reset();
                path.addCircle(cx, cy, r, Path.Direction.CW);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(NEON_SCRIM, (int) (46 * a)));
                canvas.drawPath(path, paint);
                if (!hideBorder) neonStrokePath(canvas, paint, path, accent, core * 0.8f, a, false);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(core * 0.8f);
                paint.setColor(ColorUtils.setAlphaComponent(secondary, (int) (165 * a)));
                float tick = snappingSize * 0.5f * scale;
                canvas.drawLine(cx, cy - r - tick, cx, cy - r + tick, paint);
                canvas.drawLine(cx, cy + r - tick, cx, cy + r + tick, paint);
                canvas.drawLine(cx - r - tick, cy, cx - r + tick, cy, paint);
                canvas.drawLine(cx + r - tick, cy, cx + r + tick, cy, paint);
                paint.setStrokeWidth(Math.max(1f, core * 0.5f));
                paint.setColor(ColorUtils.setAlphaComponent(secondary, (int) (150 * a)));
                canvas.drawCircle(cx, cy, r * 0.55f, paint);
                PointF pos = getCurrentPosition();
                float thumbR = snappingSize * 2.6f * scale;
                path.reset();
                path.addCircle(pos.x, pos.y, thumbR, Path.Direction.CW);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(engaged ? washColor : scrimColor);
                canvas.drawPath(path, paint);
                neonStrokePath(canvas, paint, path, accent, core * 0.8f, a, engaged);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb((int) (240 * a), 255, 255, 255));
                canvas.drawCircle(pos.x, pos.y, core, paint);
                break;
            }
            case TRACKPAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float chamfer = boundingBox.height() * 0.20f;
                buildChamferRectPath(path, boundingBox.left + core, boundingBox.top + core,
                    boundingBox.right - core, boundingBox.bottom - core, chamfer);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(scrimColor);
                canvas.drawPath(path, paint);
                if (!hideBorder) neonStrokePath(canvas, paint, path, accent, core, a, engaged);
                float dm = snappingSize * 1.1f * scale;
                path.reset();
                path.moveTo(cx, cy - dm);
                path.lineTo(cx + dm, cy);
                path.lineTo(cx, cy + dm);
                path.lineTo(cx - dm, cy);
                path.close();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(core * 0.7f);
                paint.setColor(ColorUtils.setAlphaComponent(secondary, (int) ((engaged ? 220 : 150) * a)));
                canvas.drawPath(path, paint);
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                float chamfer = (orientation == 0 ? boundingBox.height() : boundingBox.width()) * 0.30f;
                float elementSize = scroller.getElementSize();
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                buildChamferRectPath(path, boundingBox.left + core, boundingBox.top + core,
                    boundingBox.right - core, boundingBox.bottom - core, chamfer);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(scrimColor);
                canvas.drawPath(path, paint);
                if (!hideBorder) neonStrokePath(canvas, paint, path, accent, core, a, false);

                canvas.save();
                canvas.clipPath(path);
                int pressedIndex = currentPointerId != -1 && !scroller.isScrolling() ? scroller.getBindingIndex() : -1;
                int dividerColor = ColorUtils.setAlphaComponent(accent, (int) (80 * a));
                int hotTextColor = Color.argb((int) (245 * a), 255, 255, 255);
                int cellTextColor = ColorUtils.setAlphaComponent(accent, (int) (220 * a));
                int hotFill = ColorUtils.setAlphaComponent(secondary, (int) (41 * a));

                if (orientation == 0) {
                    float lineTop = boundingBox.top + boundingBox.height() * 0.2f;
                    float lineBottom = boundingBox.bottom - boundingBox.height() * 0.2f;
                    float startX = boundingBox.left - (scrollOffset % elementSize);
                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(Math.max(1f, core * 0.4f));
                        paint.setColor(dividerColor);
                        if (startX > boundingBox.left && startX < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                paint.setStyle(Paint.Style.FILL);
                                paint.setColor(hotFill);
                                canvas.drawRect(startX, boundingBox.top, startX + elementSize, boundingBox.bottom, paint);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : cellTextColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, elementSize - snappingSize * 0.5f), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }
                }
                else {
                    float lineLeft = boundingBox.left + boundingBox.width() * 0.2f;
                    float lineRight = boundingBox.right - boundingBox.width() * 0.2f;
                    float startY = boundingBox.top - (scrollOffset % elementSize);
                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(Math.max(1f, core * 0.4f));
                        paint.setColor(dividerColor);
                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                paint.setStyle(Paint.Style.FILL);
                                paint.setColor(hotFill);
                                canvas.drawRect(boundingBox.left, startY, boundingBox.right, startY + elementSize, paint);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : cellTextColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, boundingBox.width() - snappingSize * 0.5f), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }
                }
                canvas.restore();
                break;
            }
            default:
                break;
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
    }

    private static final int LUMINA_RIM = 0xFFC7D0DA;
    private static final int LUMINA_TEXT = 0xFFF4F7FA;
    private static Shader luminaBodyShader;
    private static Shader luminaShadeShader;
    private static Shader luminaSpecularShader;
    private static Shader luminaDomeShader;

    private static Shader getLuminaBodyShader() {
        if (luminaBodyShader == null) {
            luminaBodyShader = new LinearGradient(0f, 0f, 0f, 1f, 0xFF2E343C, 0xB512151A, Shader.TileMode.CLAMP);
        }
        return luminaBodyShader;
    }

    private static Shader getLuminaShadeShader() {
        if (luminaShadeShader == null) {
            luminaShadeShader = new LinearGradient(0f, 0f, 0f, 1f,
                new int[] {0x00000000, 0x00000000, 0x4D000000},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        return luminaShadeShader;
    }

    private static Shader getLuminaSpecularShader() {
        if (luminaSpecularShader == null) {
            luminaSpecularShader = new LinearGradient(0f, 0f, 0f, 1f,
                new int[] {0xFFFFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        return luminaSpecularShader;
    }

    private static Shader getLuminaDomeShader() {
        if (luminaDomeShader == null) {
            luminaDomeShader = new RadialGradient(0f, 0f, 1f,
                new int[] {0x64FFFFFF, 0xB92E343C, 0xE112151A},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        return luminaDomeShader;
    }

    // Матовое тело Lumina: градиент + нижнее затемнение
    private void luminaGlassBody(Canvas canvas, Paint paint, Rect bb, float a) {
        paint.setStyle(Paint.Style.FILL);
        placeShader(getLuminaBodyShader(), bb.left, bb.top, Math.max(1, bb.height()));
        paint.setShader(getLuminaBodyShader());
        paint.setAlpha((int)(170 * a));
        drawCleanBody(canvas, paint, bb, 0f);
        placeShader(getLuminaShadeShader(), bb.left, bb.top, Math.max(1, bb.height()));
        paint.setShader(getLuminaShadeShader());
        paint.setAlpha((int)(255 * a));
        drawCleanBody(canvas, paint, bb, 0f);
        paint.setShader(null);
    }

    // LUMINA — матовое стекло: градиентное тело, серебряная hairline-рамка, блик сверху, акцент при нажатии
    private void drawLumina(Canvas canvas) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        float a = styleOpacity();
        boolean engaged = isEngaged();
        Rect boundingBox = getBoundingBox();
        int custom = resolveAccentColor();
        int accent = resolveThemedAccent();
        if (isHighlighted() && custom == -1) {
            accent = ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), 255);
        }
        float hairline = Math.max(1.5f, snappingSize * 0.11f * scale);
        int rimIdle = custom != -1 ? ColorUtils.blendARGB(LUMINA_RIM, custom, 0.25f) : LUMINA_RIM;
        Path path = inputControlsView.getPath();
        int rimColor = engaged
            ? ColorUtils.setAlphaComponent(accent, (int)(235 * a))
            : ColorUtils.setAlphaComponent(rimIdle, (int)(150 * a));
        if (hideBorder) rimColor = Color.TRANSPARENT;
        int labelColor = resolveLabelColor(engaged
            ? Color.argb((int)(250 * a), 255, 255, 255)
            : ColorUtils.setAlphaComponent(accent, (int)(230 * a)));
        float minTextSize = snappingSize * 2 * scale;
        ColorFilter iconTint = engaged ? inputControlsView.getColorFilter() : accentFilter(accent);

        switch (type) {
            case BUTTON:
            case COMBO_BUTTON: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                luminaGlassBody(canvas, paint, boundingBox, a);
                if (fillColor != Color.TRANSPARENT && fillOpacity > 0f) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setShader(null);
                    paint.setColor(ColorUtils.setAlphaComponent(fillColor, (int)(fillOpacity * 255 * a)));
                    drawCleanBody(canvas, paint, boundingBox, 0f);
                }
                if (engaged) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int)(85 * a)));
                    drawCleanBody(canvas, paint, boundingBox, 0f);
                    beginBloom(paint, cx, cy, Math.min(boundingBox.width(), boundingBox.height()) * 0.58f, accent, a);
                    drawCleanBody(canvas, paint, boundingBox, 0f);
                    endBloom(paint);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline * 1.2f);
                paint.setColor(rimColor);
                drawCleanBody(canvas, paint, boundingBox, 0f);
                placeShader(getLuminaSpecularShader(), boundingBox.left, boundingBox.top, Math.max(1, boundingBox.height()));
                paint.setShader(getLuminaSpecularShader());
                paint.setStrokeWidth(hairline * 1.3f);
                paint.setAlpha((int)(150 * a));
                drawCleanBody(canvas, paint, boundingBox, hairline);
                paint.setShader(null);
                if (type == Type.COMBO_BUTTON) {
                    drawComboOverlay(canvas, paint, boundingBox, labelColor, a);
                }
                else if (iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, iconTint);
                }
                else {
                    String label = getDisplayText();
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(labelColor);
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, label, boundingBox.width() - snappingSize * 0.6f), minTextSize));
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(label, cx, (cy - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                break;
            }
            case STEERING_WHEEL: {
                drawSteeringWheel(canvas, paint, rimColor, a, hairline * 2.4f);
                break;
            }
            case D_PAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float base = Math.min(boundingBox.width(), boundingBox.height());
                float half = base * 0.5f;
                float arm = base * 0.17f;
                path.reset();
                path.moveTo(cx - arm, cy - half);
                path.lineTo(cx + arm, cy - half);
                path.lineTo(cx + arm, cy - arm);
                path.lineTo(cx + half, cy - arm);
                path.lineTo(cx + half, cy + arm);
                path.lineTo(cx + arm, cy + arm);
                path.lineTo(cx + arm, cy + half);
                path.lineTo(cx - arm, cy + half);
                path.lineTo(cx - arm, cy + arm);
                path.lineTo(cx - half, cy + arm);
                path.lineTo(cx - half, cy - arm);
                path.lineTo(cx - arm, cy - arm);
                path.close();
                paint.setPathEffect(cornerEffect(base * 0.09f));
                paint.setStyle(Paint.Style.FILL);
                placeShader(getLuminaBodyShader(), boundingBox.left, cy - half, Math.max(1f, base));
                paint.setShader(getLuminaBodyShader());
                paint.setAlpha((int)(170 * a));
                canvas.drawPath(path, paint);
                placeShader(getLuminaShadeShader(), boundingBox.left, cy - half, Math.max(1f, base));
                paint.setShader(getLuminaShadeShader());
                paint.setAlpha((int)(255 * a));
                canvas.drawPath(path, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline * 1.2f);
                paint.setColor(rimColor);
                canvas.drawPath(path, paint);
                paint.setPathEffect(null);

                boolean hasStates = engaged && states.length >= 4;
                float chev = base * 0.05f;
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                for (int i = 0; i < 4; i++) {
                    boolean hot = hasStates && states[i];
                    int dx = i == 1 ? 1 : i == 3 ? -1 : 0;
                    int dy = i == 2 ? 1 : i == 0 ? -1 : 0;
                    if (hot) {
                        float bx = cx + dx * half * 0.6f;
                        float by = cy + dy * half * 0.6f;
                        beginBloom(paint, bx, by, base * 0.26f, accent, a);
                        canvas.drawCircle(bx, by, base * 0.26f, paint);
                        endBloom(paint);
                    }
                    float ax = cx + dx * half * 0.62f;
                    float ay = cy + dy * half * 0.62f;
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(2f, snappingSize * 0.16f * scale));
                    paint.setColor(hot
                        ? Color.argb((int)(250 * a), 255, 255, 255)
                        : ColorUtils.setAlphaComponent(accent, (int)(160 * a)));
                    if (dy != 0) {
                        canvas.drawLine(ax - chev, ay - dy * chev * 0.4f, ax, ay + dy * chev, paint);
                        canvas.drawLine(ax + chev, ay - dy * chev * 0.4f, ax, ay + dy * chev, paint);
                    }
                    else {
                        canvas.drawLine(ax - dx * chev * 0.4f, ay - chev, ax + dx * chev, ay, paint);
                        canvas.drawLine(ax - dx * chev * 0.4f, ay + chev, ax + dx * chev, ay, paint);
                    }
                }
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStrokeJoin(Paint.Join.MITER);
                break;
            }
            case STICK: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float r = boundingBox.height() * 0.5f;
                paint.setStyle(Paint.Style.FILL);
                placeShader(getLuminaBodyShader(), cx - r, cy - r, Math.max(1f, r * 2f));
                paint.setShader(getLuminaBodyShader());
                paint.setAlpha((int)(95 * a));
                canvas.drawCircle(cx, cy, r, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                float bezel = Math.max(2f, snappingSize * 0.3f * scale);
                paint.setStrokeWidth(bezel);
                paint.setColor(ColorUtils.setAlphaComponent(LUMINA_RIM, (int)(55 * a)));
                canvas.drawCircle(cx, cy, r * 0.97f, paint);
                tempRect.set(cx - r * 0.97f, cy - r * 0.97f, cx + r * 0.97f, cy + r * 0.97f);
                paint.setColor(Color.argb((int)(110 * a), 255, 255, 255));
                canvas.drawArc(tempRect, 150f, 180f, false, paint);
                paint.setStrokeWidth(hairline * 1.3f);
                paint.setColor(ColorUtils.setAlphaComponent(accent, (int)((engaged ? 210 : 95) * a)));
                canvas.drawCircle(cx, cy, r * 0.55f, paint);
                PointF pos = getCurrentPosition();
                float thumbR = snappingSize * 3.5f * scale;
                paint.setStyle(Paint.Style.FILL);
                placeShader(getLuminaDomeShader(), pos.x - thumbR * 0.35f, pos.y - thumbR * 0.38f, Math.max(1f, thumbR * 1.6f));
                paint.setShader(getLuminaDomeShader());
                paint.setAlpha((int)(235 * a));
                canvas.drawCircle(pos.x, pos.y, thumbR, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline * 1.2f);
                paint.setColor(engaged
                    ? ColorUtils.setAlphaComponent(accent, (int)(235 * a))
                    : ColorUtils.setAlphaComponent(rimIdle, (int)(170 * a)));
                canvas.drawCircle(pos.x, pos.y, thumbR, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb((int)(128 * a), 255, 255, 255));
                canvas.drawCircle(pos.x - thumbR * 0.35f, pos.y - thumbR * 0.38f, thumbR * 0.14f, paint);
                break;
            }
            case TRACKPAD: {
                luminaGlassBody(canvas, paint, boundingBox, a);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline * 1.2f);
                paint.setColor(rimColor);
                drawCleanBody(canvas, paint, boundingBox, 0f);
                float len = Math.min(boundingBox.width(), boundingBox.height()) * 0.08f;
                float ins = Math.min(boundingBox.width(), boundingBox.height()) * 0.06f;
                paint.setStrokeWidth(hairline);
                paint.setColor(engaged
                    ? ColorUtils.setAlphaComponent(accent, (int)(200 * a))
                    : ColorUtils.setAlphaComponent(LUMINA_RIM, (int)(90 * a)));
                canvas.drawLine(boundingBox.left + ins, boundingBox.top + ins, boundingBox.left + ins + len, boundingBox.top + ins, paint);
                canvas.drawLine(boundingBox.left + ins, boundingBox.top + ins, boundingBox.left + ins, boundingBox.top + ins + len, paint);
                canvas.drawLine(boundingBox.right - ins, boundingBox.top + ins, boundingBox.right - ins - len, boundingBox.top + ins, paint);
                canvas.drawLine(boundingBox.right - ins, boundingBox.top + ins, boundingBox.right - ins, boundingBox.top + ins + len, paint);
                canvas.drawLine(boundingBox.left + ins, boundingBox.bottom - ins, boundingBox.left + ins + len, boundingBox.bottom - ins, paint);
                canvas.drawLine(boundingBox.left + ins, boundingBox.bottom - ins, boundingBox.left + ins, boundingBox.bottom - ins - len, paint);
                canvas.drawLine(boundingBox.right - ins, boundingBox.bottom - ins, boundingBox.right - ins - len, boundingBox.bottom - ins, paint);
                canvas.drawLine(boundingBox.right - ins, boundingBox.bottom - ins, boundingBox.right - ins, boundingBox.bottom - ins - len, paint);
                if (iconId > 0) {
                    paint.setShader(null);
                    drawIcon(canvas, boundingBox.exactCenterX(), boundingBox.exactCenterY(),
                        boundingBox.width() * 0.6f, boundingBox.height() * 0.6f, iconId, iconTint);
                }
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                float rr = (orientation == 0 ? boundingBox.height() : boundingBox.width()) * 0.45f;
                float elementSize = scroller.getElementSize();
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                paint.setStyle(Paint.Style.FILL);
                placeShader(getLuminaBodyShader(), boundingBox.left, boundingBox.top, Math.max(1, boundingBox.height()));
                paint.setShader(getLuminaBodyShader());
                paint.setAlpha((int)(170 * a));
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, rr, rr, paint);
                placeShader(getLuminaShadeShader(), boundingBox.left, boundingBox.top, Math.max(1, boundingBox.height()));
                paint.setShader(getLuminaShadeShader());
                paint.setAlpha((int)(255 * a));
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, rr, rr, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(hairline * 1.2f);
                paint.setColor(rimColor);
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, rr, rr, paint);

                canvas.save();
                path.reset();
                path.addRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, rr, rr, Path.Direction.CW);
                canvas.clipPath(path);
                int pressedIndex = currentPointerId != -1 && !scroller.isScrolling() ? scroller.getBindingIndex() : -1;
                int dividerColor = ColorUtils.setAlphaComponent(LUMINA_RIM, (int)(60 * a));
                float dividerWidth = Math.max(1f, snappingSize * 0.06f);
                int hotTextColor = Color.argb((int)(250 * a), 255, 255, 255);
                int cellTextColor = ColorUtils.setAlphaComponent(LUMINA_TEXT, (int)(205 * a));

                if (orientation == 0) {
                    float lineTop = boundingBox.top + boundingBox.height() * 0.25f;
                    float lineBottom = boundingBox.bottom - boundingBox.height() * 0.25f;
                    float startX = boundingBox.left - (scrollOffset % elementSize);
                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dividerWidth);
                        paint.setColor(dividerColor);
                        if (startX > boundingBox.left && startX < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                paint.setStyle(Paint.Style.FILL);
                                paint.setColor(ColorUtils.setAlphaComponent(accent, (int)(240 * a)));
                                float uy = boundingBox.bottom - boundingBox.height() * 0.16f;
                                tempRect.set(startX + elementSize * 0.225f, uy, startX + elementSize * 0.775f, uy + hairline * 2.2f);
                                canvas.drawRoundRect(tempRect, hairline, hairline, paint);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : cellTextColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, elementSize - snappingSize * 0.5f), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }
                }
                else {
                    float lineLeft = boundingBox.left + boundingBox.width() * 0.25f;
                    float lineRight = boundingBox.right - boundingBox.width() * 0.25f;
                    float startY = boundingBox.top - (scrollOffset % elementSize);
                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dividerWidth);
                        paint.setColor(dividerColor);
                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                paint.setStyle(Paint.Style.FILL);
                                paint.setColor(ColorUtils.setAlphaComponent(accent, (int)(240 * a)));
                                float ux = boundingBox.right - boundingBox.width() * 0.16f;
                                tempRect.set(ux, startY + elementSize * 0.225f, ux + hairline * 2.2f, startY + elementSize * 0.775f);
                                canvas.drawRoundRect(tempRect, hairline, hairline, paint);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : cellTextColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, boundingBox.width() - snappingSize * 0.5f), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }
                }
                canvas.restore();
                break;
            }
            default:
                break;
        }
        paint.setShader(null);
    }

    private static final int SHADOW_BODY = 0xFF0A1422;
    private static final int SHADOW_EDGE = 0xFF84A9D4;

    // Цвет тела Shadow с учётом пользовательского fillColor
    private int shadowBody(int alpha) {
        if (fillColor != Color.TRANSPARENT && fillOpacity > 0f) {
            return ColorUtils.setAlphaComponent(fillColor, (int)(alpha * fillOpacity));
        }
        return ColorUtils.setAlphaComponent(SHADOW_BODY, alpha);
    }

    // Мягкое слоистое тело-прямоугольник
    private void shadowSoftRect(Canvas canvas, Paint paint, float l, float t, float r, float b, float radius,
                                boolean active, int accent, float a, int snappingSize) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, snappingSize * 0.55f * scale));
        paint.setColor(ColorUtils.setAlphaComponent(accent, (int)((active ? 64 : 16) * a)));
        canvas.drawRoundRect(l, t, r, b, radius, radius, paint);
        paint.setStrokeWidth(Math.max(1f, snappingSize * 0.22f * scale));
        paint.setColor(ColorUtils.setAlphaComponent(SHADOW_EDGE, (int)((active ? 70 : 28) * a)));
        canvas.drawRoundRect(l, t, r, b, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? ColorUtils.setAlphaComponent(accent, (int)(190 * a)) : shadowBody((int)(135 * a)));
        canvas.drawRoundRect(l, t, r, b, radius, radius, paint);
        float inset = Math.max(1f, snappingSize * 0.35f * scale);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, snappingSize * 0.08f * scale));
        paint.setColor(Color.argb((int)((active ? 58 : 24) * a), 255, 255, 255));
        canvas.drawRoundRect(l + inset, t + inset, r - inset, b - inset,
            Math.max(1f, radius - inset), Math.max(1f, radius - inset), paint);
    }

    // Мягкое слоистое тело-круг
    private void shadowSoftCircle(Canvas canvas, Paint paint, float cx, float cy, float r,
                                  boolean active, int accent, float a, int snappingSize) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, snappingSize * 0.55f * scale));
        paint.setColor(ColorUtils.setAlphaComponent(accent, (int)((active ? 72 : 18) * a)));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStrokeWidth(Math.max(1f, snappingSize * 0.2f * scale));
        paint.setColor(ColorUtils.setAlphaComponent(SHADOW_EDGE, (int)((active ? 74 : 30) * a)));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? ColorUtils.setAlphaComponent(accent, (int)(190 * a)) : shadowBody((int)(140 * a)));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, snappingSize * 0.08f * scale));
        paint.setColor(Color.argb((int)((active ? 44 : 18) * a), 255, 255, 255));
        canvas.drawCircle(cx, cy, r * 0.88f, paint);
    }

    // SHADOW — мягкие слоистые тела, при нажатии заливаются акцентом; D-pad из четырёх плиток
    private void drawShadow(Canvas canvas) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        float a = styleOpacity();
        boolean engaged = isEngaged();
        Rect boundingBox = getBoundingBox();
        int accent = resolveThemedAccent();
        if (isHighlighted() && resolveAccentColor() == -1) {
            accent = ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), 255);
        }
        int accentStroke = hideBorder ? Color.TRANSPARENT : accent;
        Path path = inputControlsView.getPath();
        int labelColor = resolveLabelColor(engaged
            ? Color.argb((int)(245 * a), 255, 255, 255)
            : ColorUtils.setAlphaComponent(accent, (int)(235 * a)));
        ColorFilter iconTint = engaged ? inputControlsView.getColorFilter() : accentFilter(accent);
        float minTextSize = snappingSize * 2 * scale;

        switch (type) {
            case BUTTON:
            case COMBO_BUTTON: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                switch (shape) {
                    case CIRCLE:
                        shadowSoftCircle(canvas, paint, cx, cy, boundingBox.width() * 0.5f, engaged, accentStroke, a, snappingSize);
                        break;
                    case SQUARE:
                        shadowSoftRect(canvas, paint, boundingBox.left, boundingBox.top, boundingBox.right,
                            boundingBox.bottom, snappingSize * 1.05f * scale, engaged, accentStroke, a, snappingSize);
                        break;
                    default:
                        shadowSoftRect(canvas, paint, boundingBox.left, boundingBox.top, boundingBox.right,
                            boundingBox.bottom, boundingBox.height() * 0.45f, engaged, accentStroke, a, snappingSize);
                        break;
                }
                if (type == Type.COMBO_BUTTON) {
                    drawComboOverlay(canvas, paint, boundingBox, labelColor, a);
                }
                else if (iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, iconTint);
                }
                else {
                    String label = getDisplayText();
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(labelColor);
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, label, boundingBox.width() - snappingSize * 0.6f), minTextSize));
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(label, cx, (cy - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                break;
            }
            case STEERING_WHEEL: {
                drawSteeringWheel(canvas, paint, accentStroke, a, Math.max(2f, snappingSize * 0.35f * scale));
                break;
            }
            case D_PAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float base = Math.min(boundingBox.width(), boundingBox.height());
                float piece = base * 0.30f;
                float dist = piece * 1.15f;
                float c = piece * 0.13f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(shadowBody((int)(60 * a)));
                canvas.drawRoundRect(cx - c, cy - c, cx + c, cy + c, c * 0.45f, c * 0.45f, paint);
                boolean hasStates = engaged && states.length >= 4;
                for (int i = 0; i < 4; i++) {
                    int dx = i == 1 ? 1 : i == 3 ? -1 : 0;
                    int dy = i == 2 ? 1 : i == 0 ? -1 : 0;
                    float px = cx + dx * dist;
                    float py = cy + dy * dist;
                    boolean hot = hasStates && states[i];
                    shadowSoftRect(canvas, paint, px - piece * 0.5f, py - piece * 0.5f, px + piece * 0.5f,
                        py + piece * 0.5f, piece * 0.28f, hot, accentStroke, a, snappingSize);
                    buildDpadArrowPath(path, px, py, piece * 0.22f, i);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ColorUtils.setAlphaComponent(accent, (int)((hot ? 250 : 220) * a)));
                    canvas.drawPath(path, paint);
                }
                break;
            }
            case STICK: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                float r = boundingBox.height() * 0.5f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(shadowBody((int)((engaged ? 155 : 118) * a)));
                canvas.drawCircle(cx, cy, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, snappingSize * 0.2f * scale));
                paint.setColor(ColorUtils.setAlphaComponent(SHADOW_EDGE, (int)((engaged ? 62 : 26) * a)));
                canvas.drawCircle(cx, cy, r * 0.96f, paint);
                paint.setStrokeWidth(Math.max(1f, snappingSize * 0.5f * scale));
                paint.setColor(ColorUtils.setAlphaComponent(accentStroke, (int)((engaged ? 72 : 20) * a)));
                canvas.drawCircle(cx, cy, r * 0.98f, paint);
                paint.setStrokeWidth(Math.max(1f, snappingSize * 0.22f * scale));
                paint.setColor(ColorUtils.setAlphaComponent(accent, (int)((engaged ? 235 : 205) * a)));
                canvas.drawCircle(cx, cy, r * 0.52f, paint);
                PointF pos = getCurrentPosition();
                float thumbRadius = snappingSize * 3.5f * scale;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(shadowBody((int)(190 * a)));
                canvas.drawCircle(pos.x, pos.y, thumbRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, snappingSize * 0.12f * scale));
                paint.setColor(ColorUtils.setAlphaComponent(SHADOW_EDGE, (int)(45 * a)));
                canvas.drawCircle(pos.x, pos.y, thumbRadius * 0.96f, paint);
                break;
            }
            case TRACKPAD: {
                float cx = boundingBox.exactCenterX();
                float cy = boundingBox.exactCenterY();
                shadowSoftRect(canvas, paint, boundingBox.left, boundingBox.top, boundingBox.right,
                    boundingBox.bottom, boundingBox.height() * 0.18f, engaged, accentStroke, a, snappingSize);
                float glyph = Math.min(boundingBox.width(), boundingBox.height()) * 0.45f;
                float gw = glyph * 0.42f;
                float gh = glyph * 0.62f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.5f, glyph * 0.035f));
                paint.setColor(ColorUtils.setAlphaComponent(engaged ? Color.WHITE : accent, (int)((engaged ? 220 : 95) * a)));
                canvas.drawRoundRect(cx - gw * 0.5f, cy - gh * 0.5f, cx + gw * 0.5f, cy + gh * 0.5f, gw * 0.5f, gw * 0.5f, paint);
                canvas.drawLine(cx, cy - gh * 0.30f, cx, cy - gh * 0.02f, paint);
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                float rr = (orientation == 0 ? boundingBox.height() : boundingBox.width()) * 0.45f;
                float elementSize = scroller.getElementSize();
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                shadowSoftRect(canvas, paint, boundingBox.left, boundingBox.top, boundingBox.right,
                    boundingBox.bottom, rr, false, accentStroke, a, snappingSize);

                canvas.save();
                path.reset();
                path.addRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, rr, rr, Path.Direction.CW);
                canvas.clipPath(path);

                int pressedIndex = currentPointerId != -1 && !scroller.isScrolling() ? scroller.getBindingIndex() : -1;
                int dividerColor = ColorUtils.setAlphaComponent(SHADOW_EDGE, (int)(35 * a));
                float dividerWidth = Math.max(1f, snappingSize * 0.06f);
                int hotTextColor = Color.argb((int)(245 * a), 255, 255, 255);
                int cellTextColor = ColorUtils.setAlphaComponent(accent, (int)(225 * a));

                if (orientation == 0) {
                    float lineTop = boundingBox.top + boundingBox.height() * 0.25f;
                    float lineBottom = boundingBox.bottom - boundingBox.height() * 0.25f;
                    float startX = boundingBox.left - (scrollOffset % elementSize);
                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dividerWidth);
                        paint.setColor(dividerColor);
                        if (startX > boundingBox.left && startX < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                paint.setStyle(Paint.Style.FILL);
                                paint.setColor(ColorUtils.setAlphaComponent(accent, (int)(150 * a)));
                                canvas.drawRect(startX, boundingBox.top, startX + elementSize, boundingBox.bottom, paint);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : cellTextColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, elementSize - snappingSize * 0.5f), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }
                }
                else {
                    float lineLeft = boundingBox.left + boundingBox.width() * 0.25f;
                    float lineRight = boundingBox.right - boundingBox.width() * 0.25f;
                    float startY = boundingBox.top - (scrollOffset % elementSize);
                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dividerWidth);
                        paint.setColor(dividerColor);
                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            boolean hot = index == pressedIndex;
                            if (hot) {
                                paint.setStyle(Paint.Style.FILL);
                                paint.setColor(ColorUtils.setAlphaComponent(accent, (int)(150 * a)));
                                canvas.drawRect(boundingBox.left, startY, boundingBox.right, startY + elementSize, paint);
                            }
                            String cellText = getRangeTextForIndex(range, index);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(hot ? hotTextColor : cellTextColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, cellText, boundingBox.width() - snappingSize * 0.5f), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(cellText, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }
                }
                canvas.restore();
                break;
            }
            default:
                break;
        }
    }
}
