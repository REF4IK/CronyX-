package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.AccentTheme;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.CustomIconManager;
import com.winlator.cmod.inputcontrols.IconPackManager;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.ExternalControllerBinding;
import com.winlator.cmod.inputcontrols.GamepadState;
import com.winlator.cmod.inputcontrols.VisualStyle;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.XServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Timer;
import java.util.TimerTask;

public class InputControlsView extends View {
    public static final float DEFAULT_OVERLAY_OPACITY = 0.4f;
    private static final byte MOUSE_WHEEL_DELTA = 120;
    private boolean editMode = false;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final ColorFilter colorFilter = new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN);
    private final Point cursor = new Point();
    private boolean readyToDraw = false;
    private boolean moveCursor = false;
    private static final byte EDIT_DRAG_NONE = 0;
    private static final byte EDIT_DRAG_MOVE = 1;
    private static final byte EDIT_DRAG_RESIZE = 2;
    private static final byte EDIT_DRAG_ROTATE = 3;
    private byte editDragMode = EDIT_DRAG_NONE;
    private int snappingSize;
    private float offsetX;
    private float offsetY;
    private float guideX = Float.NaN;
    private float guideY = Float.NaN;
    private float resizeStartDistance;
    private float resizeStartScale;
    private ElementSnapshot dragStartSnapshot;
    private final ArrayDeque<ElementSnapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<ElementSnapshot> redoStack = new ArrayDeque<>();
    private ControlElement selectedElement;
    private ControlsProfile profile;
    private float overlayOpacity = DEFAULT_OVERLAY_OPACITY;
    private TouchpadView touchpadView;
    private XServer xServer;
    private final Bitmap[] icons = new Bitmap[17];
    private CustomIconManager customIconManager;
    private IconPackManager iconPackManager;
    private Timer mouseMoveTimer;
    private final PointF mouseMoveOffset = new PointF();
    private boolean showTouchscreenControls = true;

    private Handler timeoutHandler; // Reference to the activity's timeout handler
    private Runnable hideControlsRunnable; // Runnable to hide the controls
    private final android.util.SparseArray<Bitmap> iconCache = new android.util.SparseArray<>();

    private SharedPreferences preferences;

    private ControlElement stickElement;

    private boolean focusOnStick = false; // A flag to determine if we are focusing on the stick

    public boolean isFocusedOnStick() {
        return focusOnStick;
    }

    public void setFocusOnStick(boolean focus) {
        this.focusOnStick = focus;
        invalidate(); // Redraw the view with the new focus setting
    }



    @SuppressLint("ResourceType")
    public InputControlsView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus(); // Add this line to request focus
        setBackgroundColor(0x00000000);
        setPointerIcon(PointerIcon.load(getResources(), R.drawable.hidden_pointer_arrow));
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        preferences = new com.winlator.cmod.core.MmkvPreferences();
        customIconManager = new CustomIconManager(context);
        iconPackManager = new IconPackManager(context);
    }

    public InputControlsView(Context context, Handler timeoutHandler, Runnable hideControlsRunnable) {
        super(context);
        this.timeoutHandler = timeoutHandler; // Store the reference to timeout handler
        this.hideControlsRunnable = hideControlsRunnable; // Store the reference to the hide controls runnable
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus(); // Add this line to request focus
        setBackgroundColor(0x00000000);
        setPointerIcon(PointerIcon.load(getResources(), R.drawable.hidden_pointer_arrow));
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        preferences = new com.winlator.cmod.core.MmkvPreferences();
        customIconManager = new CustomIconManager(context);
        iconPackManager = new IconPackManager(context);
    }

    public InputControlsView(Context context, boolean focusOnStick) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus(); // Add this line to request focus
        setBackgroundColor(0x00000000);
        setPointerIcon(PointerIcon.load(getResources(), R.drawable.hidden_pointer_arrow));

        // If focusOnStick is true, adjust the layout params to match the stick element size
        if (focusOnStick) {
            setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        preferences = new com.winlator.cmod.core.MmkvPreferences();
        customIconManager = new CustomIconManager(context);
        iconPackManager = new IconPackManager(context);
    }


    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void saveSelectedElementStateForUndo() {
        if (selectedElement != null) {
            undoStack.push(new ElementSnapshot(selectedElement));
            redoStack.clear();
        }
    }

    public boolean undoLastEdit() {
        if (undoStack.isEmpty()) return false;
        ElementSnapshot snapshot = undoStack.pop();
        redoStack.push(new ElementSnapshot(snapshot.element));
        snapshot.restore();
        if (profile != null) profile.save();
        selectElement(snapshot.element);
        invalidate();
        return true;
    }

    public boolean redoLastEdit() {
        if (redoStack.isEmpty()) return false;
        ElementSnapshot snapshot = redoStack.pop();
        undoStack.push(new ElementSnapshot(snapshot.element));
        snapshot.restore();
        if (profile != null) profile.save();
        selectElement(snapshot.element);
        invalidate();
        return true;
    }

    public void setOverlayOpacity(float overlayOpacity) {
        this.overlayOpacity = overlayOpacity;
    }

    public float getOverlayOpacity() {
        return overlayOpacity;
    }

    public int getSnappingSize() {
        return snappingSize;
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        int width, height;

        if (stickElement != null && isFocusedOnStick()) {
            // If focusing on the stick, set width and height to the stick's bounding box size
            Rect boundingBox = stickElement.getBoundingBox();
            width = boundingBox.width();
            height = boundingBox.height();
        } else {
            // Default behavior for full screen
            width = getWidth();
            height = getHeight();
        }

        if (width == 0 || height == 0) {
            readyToDraw = false;
            return;
        }

        snappingSize = width / 100;
        readyToDraw = true;

        if (editMode) {
            drawGrid(canvas);
            drawCursor(canvas);
            drawAlignmentGuides(canvas);
        }

        if (stickElement != null) {
            // Draw only the stick element if focus mode is active
            stickElement.draw(canvas);
        }

        if (profile != null && showTouchscreenControls && !isFocusedOnStick()) {
            if (!profile.isElementsLoaded()) profile.loadElements(this);
            for (ControlElement element : profile.getElements()) {
                element.draw(canvas);
            }
        }

        super.onDraw(canvas);
    }


    public void resetStickPosition() {
        if (stickElement != null) {
            Rect boundingBox = stickElement.getBoundingBox();
            float centerX = boundingBox.centerX();
            float centerY = boundingBox.centerY();

            stickElement.setCurrentPosition(centerX, centerY); // Reset to the center of the bounding box
            invalidate(); // Redraw the stick in the centered position
        }
    }



    public void initializeStickElement(float x, float y, float scale) {
        stickElement = new ControlElement(this);
        stickElement.setType(ControlElement.Type.STICK); // Set type to STICK
        stickElement.setX((int) x);
        stickElement.setY((int) y);
        stickElement.setScale(scale);
        invalidate(); // Force the view to redraw with the stick
    }


    public void updateStickPosition(float x, float y) {
        if (stickElement != null) {
            stickElement.getCurrentPosition().x = x;  // Update the thumbstick's position
            stickElement.getCurrentPosition().y = y;  // Update the thumbstick's position
            invalidate(); // Redraw the view
        }
    }


    public ControlElement getStickElement() {
        return stickElement;
    }

    private void drawGrid(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(snappingSize * 0.0625f);
        paint.setColor(0xff000000);
        canvas.drawColor(Color.BLACK);

        paint.setAntiAlias(false);
        paint.setColor(0xff303030);

        int width = getMaxWidth();
        int height = getMaxHeight();

        for (int i = 0; i < width; i += snappingSize) {
            canvas.drawLine(i, 0, i, height, paint);
            canvas.drawLine(0, i, width, i, paint);
        }

        float cx = Mathf.roundTo(width * 0.5f, snappingSize);
        float cy = Mathf.roundTo(height * 0.5f, snappingSize);
        paint.setColor(0xff424242);

        for (int i = 0; i < width; i += snappingSize * 2) {
            canvas.drawLine(cx, i, cx, i + snappingSize, paint);
            canvas.drawLine(i, cy, i + snappingSize, cy, paint);
        }

        paint.setAntiAlias(true);
    }

    private void drawCursor(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(snappingSize * 0.0625f);
        paint.setColor(0xffc62828);

        paint.setAntiAlias(false);
        canvas.drawLine(0, cursor.y, getMaxWidth(), cursor.y, paint);
        canvas.drawLine(cursor.x, 0, cursor.x, getMaxHeight(), paint);

        paint.setAntiAlias(true);
    }

    private void drawAlignmentGuides(Canvas canvas) {
        if (Float.isNaN(guideX) && Float.isNaN(guideY)) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2.0f, snappingSize * 0.18f));
        paint.setColor(0xff00e5ff);
        paint.setAntiAlias(false);
        if (!Float.isNaN(guideX)) canvas.drawLine(guideX, 0, guideX, getMaxHeight(), paint);
        if (!Float.isNaN(guideY)) canvas.drawLine(0, guideY, getMaxWidth(), guideY, paint);
        paint.setAntiAlias(true);
    }

    public synchronized boolean addElement() {
        return addElement(ControlElement.Type.BUTTON, Binding.NONE);
    }

    public synchronized boolean addElement(ControlElement.Type type, Binding binding) {
        if (editMode && profile != null) {
            ControlElement element = new ControlElement(this);
            element.setType(type);
            element.setX(cursor.x);
            element.setY(cursor.y);
            if (binding != null) element.setBindingAt(0, binding);
            profile.addElement(element);
            profile.save();
            selectElement(element);
            return true;
        }
        else return false;
    }

    public synchronized boolean removeElement() {
        if (editMode && selectedElement != null && profile != null) {
            profile.removeElement(selectedElement);
            selectedElement = null;
            profile.save();
            invalidate();
            return true;
        }
        else return false;
    }

    public ControlElement getSelectedElement() {
        return selectedElement;
    }

    private synchronized void deselectAllElements() {
        selectedElement = null;
        if (profile != null) {
            for (ControlElement element : profile.getElements()) element.setSelected(false);
        }
    }

    private void selectElement(ControlElement element) {
        deselectAllElements();
        if (element != null) {
            selectedElement = element;
            selectedElement.setSelected(true);
        }
        invalidate();
    }

    public synchronized ControlsProfile getProfile() {
        return profile;
    }

    public VisualStyle getVisualStyle() {
        return profile != null ? profile.getVisualStyleEnum() : VisualStyle.GAMEHUB;
    }

    public void setVisualStyle(VisualStyle style) {
        if (profile != null) profile.setVisualStyle((style != null ? style : VisualStyle.GAMEHUB).code);
        invalidate();
    }

    public AccentTheme getAccentTheme() {
        return profile != null ? profile.getAccentThemeEnum() : AccentTheme.CYAN;
    }

    public void setAccentTheme(AccentTheme theme) {
        if (profile != null) profile.setAccentTheme((theme != null ? theme : AccentTheme.CYAN).code);
        invalidate();
    }

    public synchronized void setProfile(ControlsProfile profile) {
        iconCache.clear();
        if (profile != null) {
            this.profile = profile;
            deselectAllElements();
        }
        else this.profile = null;
    }

    public boolean isShowTouchscreenControls() {
        return showTouchscreenControls;
    }

    public void setShowTouchscreenControls(boolean showTouchscreenControls) {
        this.showTouchscreenControls = showTouchscreenControls;
    }

    public int getPrimaryColor() {
        return Color.argb((int)(overlayOpacity * 255), 255, 255, 255);
    }

    public int getSecondaryColor() {
        return Color.argb((int)(overlayOpacity * 255), 2, 119, 189);
    }

    private synchronized ControlElement intersectElement(float x, float y) {
        if (profile != null) {
            for (ControlElement element : profile.getElements()) {
                if (element.containsPoint(x, y)) return element;
            }
        }
        return null;
    }

    private PointF snapElementPosition(ControlElement movingElement, float x, float y) {
        float snappedX = Mathf.roundTo(x, snappingSize);
        float snappedY = Mathf.roundTo(y, snappingSize);
        guideX = Float.NaN;
        guideY = Float.NaN;

        float snapDistance = snappingSize * 1.5f;
        float centerX = getMaxWidth() * 0.5f;
        float centerY = getMaxHeight() * 0.5f;

        if (Math.abs(snappedX - centerX) <= snapDistance) {
            snappedX = centerX;
            guideX = centerX;
        }
        if (Math.abs(snappedY - centerY) <= snapDistance) {
            snappedY = centerY;
            guideY = centerY;
        }

        if (profile != null) {
            for (ControlElement element : profile.getElements()) {
                if (element == movingElement) continue;
                if (Math.abs(snappedX - element.getX()) <= snapDistance) {
                    snappedX = element.getX();
                    guideX = element.getX();
                }
                if (Math.abs(snappedY - element.getY()) <= snapDistance) {
                    snappedY = element.getY();
                    guideY = element.getY();
                }
            }
        }

        return new PointF(snappedX, snappedY);
    }

    public Paint getPaint() {
        return paint;
    }

    public Path getPath() {
        return path;
    }

    public ColorFilter getColorFilter() {
        return colorFilter;
    }

    public TouchpadView getTouchpadView() {
        return touchpadView;
    }

    public void setTouchpadView(TouchpadView touchpadView) {
        this.touchpadView = touchpadView;
    }

    public XServer getXServer() {
        return xServer;
    }

    public void setXServer(XServer xServer) {
        this.xServer = xServer;
        createMouseMoveTimer();
    }

    public int getMaxWidth() {
        return (int)Mathf.roundTo(getWidth(), snappingSize);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mouseMoveTimer != null)
            mouseMoveTimer.cancel();
        iconCache.clear();
        super.onDetachedFromWindow();
    }

    public int getMaxHeight() {
        return (int)Mathf.roundTo(getHeight(), snappingSize);
    }

    private void createMouseMoveTimer() {
        WinHandler winHandler = xServer.getWinHandler();
        if (mouseMoveTimer == null && profile != null) {
            final float cursorSpeed = profile.getCursorSpeed();
            mouseMoveTimer = new Timer();
            mouseMoveTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (mouseMoveOffset.x != 0 || mouseMoveOffset.y != 0) {// Only move if there's an offsete if there's an offset
                        if (xServer.isRelativeMouseMovement())
                            winHandler.mouseEvent(MouseEventFlags.MOVE, (int) (mouseMoveOffset.x * cursorSpeed * 10), (int) (mouseMoveOffset.y * cursorSpeed * 10), 0);
                        else
                            xServer.injectPointerMoveDelta(
                                (int) (mouseMoveOffset.x * cursorSpeed * 10),
                                (int) (mouseMoveOffset.y * cursorSpeed * 10)
                        );
                    }
                }
            }, 0, 1000 / 60); // 60 FPS
        }
    }


//    private void processJoystickInput(ExternalController controller) {
//        ExternalControllerBinding controllerBinding;
//        final int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
//        final float[] values = {controller.state.thumbLX, controller.state.thumbLY, controller.state.thumbRX, controller.state.thumbRY, controller.state.getDPadX(), controller.state.getDPadY()};
//
//        for (byte i = 0; i < axes.length; i++) {
//            if (Math.abs(values[i]) > ControlElement.STICK_DEAD_ZONE) {
//                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i], Mathf.sign(values[i])));
//                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(), true, values[i]);
//            }
//            else {
//                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i], (byte) 1));
//                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(), false, values[i]);
//                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i], (byte)-1));
//                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(), false, values[i]);
//            }
//        }
//    }

    private void processJoystickInput(ExternalController controller) {
        final int[] axes = {
                MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
        };
        final float[] values = {
                controller.state.thumbLX, controller.state.thumbLY,
                controller.state.thumbRX, controller.state.thumbRY,
                controller.state.getDPadX(), controller.state.getDPadY()
        };

        for (int i = 0; i < axes.length; i++) {
            float value = values[i];
            if (Math.abs(value) > ControlElement.STICK_DEAD_ZONE) {
                byte sign = Mathf.sign(value);
                int keyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], sign);
                ExternalControllerBinding controllerBinding = controller.getControllerBinding(keyCode);
                if (controllerBinding != null) {
                    handleInputEvent(controllerBinding.getBinding(), true, value);
                }
            } else {
                // Handle releasing the bindings when the axis returns to deadzone
                for (byte sign = -1; sign <= 1; sign += 2) {
                    int keyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], sign);
                    ExternalControllerBinding controllerBinding = controller.getControllerBinding(keyCode);
                    if (controllerBinding != null) {
                        handleInputEvent(controllerBinding.getBinding(), false, value);
                    }
                }
            }
        }
    }



//    @Override
//    public boolean onGenericMotionEvent(MotionEvent event) {
//        if (!editMode && profile != null) {
//            ExternalController controller = profile.getController(event.getDeviceId());
//            if (controller != null && controller.updateStateFromMotionEvent(event)) {
//                ExternalControllerBinding controllerBinding;
//                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_L2);
//                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(), controller.state.isPressed(ExternalController.IDX_BUTTON_L2));
//
//                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_R2);
//                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(), controller.state.isPressed(ExternalController.IDX_BUTTON_R2));
//
//                processJoystickInput(controller);
//                return true;
//            }
//        }
//        return super.onGenericMotionEvent(event);
//    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        Log.d("InputControlsView", "dispatchGenericMotionEvent called. Source: " + event.getSource());
        return super.dispatchGenericMotionEvent(event);
    }


    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {

        Log.d("InputControlsView", "Motion event received. Source: " + event.getSource());
        Log.d("InputControlsView", "Device ID: " + event.getDeviceId());
        Log.d("InputControlsView", "Profile is " + (profile != null ? "set" : "null"));


        if (!editMode && profile != null) {
            // Retrieve the associated controller for this event
            ExternalController controller = profile.getController(event.getDeviceId());

            if (controller != null && controller.updateStateFromMotionEvent(event)) {
                // Process L2 and R2 button bindings
                ExternalControllerBinding controllerBinding;

                // L2 button
                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_L2);
                if (controllerBinding != null) {
                    handleInputEvent(controllerBinding.getBinding(), controller.state.isPressed(ExternalController.IDX_BUTTON_L2));
                }

                // R2 button
                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_R2);
                if (controllerBinding != null) {
                    handleInputEvent(controllerBinding.getBinding(), controller.state.isPressed(ExternalController.IDX_BUTTON_R2));
                }

                Log.d("InputEvent", "Event source: " + event.getSource());
                Log.d("InputEvent", "Device ID: " + event.getDeviceId());
                Log.d("InputEvent", "Action: " + event.getAction());

                // Process joystick inputs for mouse movement and other bindings
                processJoystickInput(controller);

                // Return true to indicate the motion event was handled
                return true;
            }
        }

        // Pass the event to the super method if not handled
        return super.onGenericMotionEvent(event);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {

        boolean hapticsEnabled = preferences.getBoolean("touchscreen_haptics_enabled", true);

        // Reset the timeout when touch events occur within InputControlsView
        resetTouchscreenTimeout();

        if (editMode && readyToDraw) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    float x = event.getX();
                    float y = event.getY();

                    ControlElement element = intersectElement(x, y);
                    moveCursor = true;
                    editDragMode = EDIT_DRAG_NONE;
                    if (selectedElement != null && selectedElement.containsResizeHandle(x, y)) {
                        element = selectedElement;
                        resizeStartScale = selectedElement.getScale();
                        Rect box = selectedElement.getBoundingBox();
                        resizeStartDistance = Math.max(1.0f, (float)Math.sqrt(Mathf.lengthSq(box.centerX() - x, box.centerY() - y)));
                        editDragMode = EDIT_DRAG_RESIZE;
                        moveCursor = false;
                    }
                    else if (selectedElement != null && selectedElement.containsRotateHandle(x, y)) {
                        element = selectedElement;
                        editDragMode = EDIT_DRAG_ROTATE;
                        moveCursor = false;
                    }
                    else if (element != null) {
                        offsetX = x - element.getX();
                        offsetY = y - element.getY();
                        editDragMode = EDIT_DRAG_MOVE;
                        moveCursor = false;
                    }

                    selectElement(element);
                    dragStartSnapshot = selectedElement != null ? new ElementSnapshot(selectedElement) : null;
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (selectedElement != null) {
                        if (editDragMode == EDIT_DRAG_RESIZE) {
                            Rect box = selectedElement.getBoundingBox();
                            float distance = (float)Math.sqrt(Mathf.lengthSq(box.centerX() - event.getX(), box.centerY() - event.getY()));
                            float scale = Mathf.clamp(resizeStartScale * distance / resizeStartDistance, 0.35f, 2.5f);
                            selectedElement.setScale(Mathf.roundTo(scale * 100.0f, 5.0f) / 100.0f);
                        }
                        else if (editDragMode == EDIT_DRAG_ROTATE) {
                            float angle = (float)Math.toDegrees(Math.atan2(event.getY() - selectedElement.getY(), event.getX() - selectedElement.getX())) + 90.0f;
                            selectedElement.setRotation(Mathf.roundTo(angle, 5.0f));
                        }
                        else {
                            PointF snapped = snapElementPosition(selectedElement, event.getX() - offsetX, event.getY() - offsetY);
                            selectedElement.setX((int)snapped.x);
                            selectedElement.setY((int)snapped.y);
                        }
                        invalidate();
                    }
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    if (dragStartSnapshot != null && selectedElement != null && dragStartSnapshot.hasChanged()) {
                        undoStack.push(dragStartSnapshot);
                        redoStack.clear();
                    }
                    dragStartSnapshot = null;
                    if (selectedElement != null && profile != null) profile.save();
                    if (moveCursor) cursor.set((int)Mathf.roundTo(event.getX(), snappingSize), (int)Mathf.roundTo(event.getY(), snappingSize));
                    editDragMode = EDIT_DRAG_NONE;
                    guideX = Float.NaN;
                    guideY = Float.NaN;
                    invalidate();
                    break;
                }
            }
        }

        if (!editMode && profile != null) {
            int actionIndex = event.getActionIndex();
            int pointerId = event.getPointerId(actionIndex);
            int actionMasked = event.getActionMasked();
            boolean handled = false;

            switch (actionMasked) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    float x = event.getX(actionIndex);
                    float y = event.getY(actionIndex);

                    touchpadView.setPointerButtonLeftEnabled(true);
                    for (ControlElement element : profile.getElements()) {
                        if (element.handleTouchDown(pointerId, x, y)) {
                            handled = true;

                            // Trigger haptic feedback for input controls
                            if (hapticsEnabled) {
                                Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                                if (vibrator != null && vibrator.hasVibrator()) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                                    } else {
                                        vibrator.vibrate(50); // Legacy method for older Android versions
                                    }

                                }

                            }
                        }
                        if (element.getBindingAt(0) == Binding.MOUSE_LEFT_BUTTON) {
                            touchpadView.setPointerButtonLeftEnabled(false);
                        }
                    }
                    if (!handled) touchpadView.onTouchEvent(event);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    for (byte i = 0, count = (byte)event.getPointerCount(); i < count; i++) {
                        float x = event.getX(i);
                        float y = event.getY(i);

                        handled = false;
                        for (ControlElement element : profile.getElements()) {
                            if (element.handleTouchMove(i, x, y)) handled = true;
                        }
                        if (!handled) touchpadView.onTouchEvent(event);
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    for (ControlElement element : profile.getElements()) if (element.handleTouchUp(pointerId)) handled = true;
                    if (!handled) touchpadView.onTouchEvent(event);
                    break;
            }
        }
        return true;
    }





    private void resetTouchscreenTimeout() {
        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            if (isTimeoutEnabled) {
                timeoutHandler.postDelayed(hideControlsRunnable, 5000);
            }
        }
    }

    public boolean onKeyEvent(KeyEvent event) {
        if (profile != null && event.getRepeatCount() == 0) {
            ExternalController controller = profile.getController(event.getDeviceId());
            if (controller != null) {
                ExternalControllerBinding controllerBinding = controller.getControllerBinding(event.getKeyCode());
                if (controllerBinding != null) {
                    int action = event.getAction();

                    if (action == KeyEvent.ACTION_DOWN) {
                        handleInputEvent(controllerBinding.getBinding(), true);
                    }
                    else if (action == KeyEvent.ACTION_UP) {
                        handleInputEvent(controllerBinding.getBinding(), false);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void handleInputEvent(Binding binding, boolean isActionDown) {
        handleInputEvent(binding, isActionDown, 0);
    }

    public void handleInputEvent(Binding binding, boolean isActionDown, float offset) {
        WinHandler winHandler = xServer != null ? xServer.getWinHandler() : null;
        if (binding.isGamepad()) {
            GamepadState state = profile.getGamepadState();

            int buttonIdx = binding.ordinal() - Binding.GAMEPAD_BUTTON_A.ordinal();
            if (buttonIdx <= ExternalController.IDX_BUTTON_R2) {
                if (buttonIdx == ExternalController.IDX_BUTTON_L2)
                    state.triggerL = isActionDown ? 1.0f : 0f;
                else if (buttonIdx == ExternalController.IDX_BUTTON_R2)
                    state.triggerR = isActionDown ? 1.0f : 0f;
                else
                    state.setPressed(buttonIdx, isActionDown);
            }
            else if (binding == Binding.GAMEPAD_LEFT_THUMB_UP || binding == Binding.GAMEPAD_LEFT_THUMB_DOWN) {
                state.thumbLY = isActionDown ? offset : 0;
            }
            else if (binding == Binding.GAMEPAD_LEFT_THUMB_LEFT || binding == Binding.GAMEPAD_LEFT_THUMB_RIGHT) {
                state.thumbLX = isActionDown ? offset : 0;
            }
            else if (binding == Binding.GAMEPAD_RIGHT_THUMB_UP || binding == Binding.GAMEPAD_RIGHT_THUMB_DOWN) {
                state.thumbRY = isActionDown ? offset : 0;
            }
            else if (binding == Binding.GAMEPAD_RIGHT_THUMB_LEFT || binding == Binding.GAMEPAD_RIGHT_THUMB_RIGHT) {
                state.thumbRX = isActionDown ? offset : 0;
            }
            else if (binding == Binding.GAMEPAD_DPAD_UP || binding == Binding.GAMEPAD_DPAD_RIGHT ||
                     binding == Binding.GAMEPAD_DPAD_DOWN || binding == Binding.GAMEPAD_DPAD_LEFT) {
                state.dpad[binding.ordinal() - Binding.GAMEPAD_DPAD_UP.ordinal()] = isActionDown;
            }

            if (winHandler != null) {
                ExternalController controller = winHandler.getCurrentController();
                if (controller != null) controller.state.copy(state);
                winHandler.sendGamepadState();
            }
        }
        else {
            if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                mouseMoveOffset.x = isActionDown ? (offset != 0 ? offset : (binding == Binding.MOUSE_MOVE_LEFT ? -1 : 1)) : 0;
                if (isActionDown) createMouseMoveTimer();
            }
            else if (binding == Binding.MOUSE_MOVE_DOWN || binding == Binding.MOUSE_MOVE_UP) {
                mouseMoveOffset.y = isActionDown ? (offset != 0 ? offset : (binding == Binding.MOUSE_MOVE_UP ? -1 : 1)) : 0;
                if (isActionDown) createMouseMoveTimer();
            }
            else {
                Pointer.Button pointerButton = binding.getPointerButton();
                if (isActionDown) {
                    if (pointerButton != null) {
                        if (xServer.isRelativeMouseMovement()) {
                            int wheelDelta = pointerButton == Pointer.Button.BUTTON_SCROLL_UP ? MOUSE_WHEEL_DELTA : (pointerButton == Pointer.Button.BUTTON_SCROLL_DOWN ? -MOUSE_WHEEL_DELTA : 0);
                            winHandler.mouseEvent(MouseEventFlags.getFlagFor(pointerButton, true), 0, 0, wheelDelta);
                        } else {
                            xServer.injectPointerButtonPress(pointerButton);
                        }
                    }
                    else xServer.injectKeyPress(binding.keycode);
                }
                else {
                    if (pointerButton != null) {
                        if (xServer.isRelativeMouseMovement()) {
                            winHandler.mouseEvent(MouseEventFlags.getFlagFor(pointerButton, false), 0, 0, 0);
                        } else {
                            xServer.injectPointerButtonRelease(pointerButton);
                        }
                    }
                    else xServer.injectKeyRelease(binding.keycode);
                }
            }
        }
    }

    public Bitmap getIcon(int id) {
        Bitmap cached = iconCache.get(id);
        if (cached != null) return cached;

        Bitmap icon = null;
        // Handle pack icons (ID >= 1000)
        if (IconPackManager.isPackIcon(id)) {
            icon = iconPackManager.loadIconByGlobalId(id);
        }
        // Handle custom icons (ID >= 100)
        else if (CustomIconManager.isCustomIcon(id)) {
            icon = customIconManager.loadIcon(id);
        }

        if (icon != null) {
            iconCache.put(id, icon);
        }
        return icon;
    }

    private static class ElementSnapshot {
        private final ControlElement element;
        private final short x;
        private final short y;
        private final float scale;
        private final float rotation;
        private final float opacity;
        private final float fillOpacity;
        private final int borderColor;
        private final int fillColor;
        private final int textColor;

        private ElementSnapshot(ControlElement element) {
            this.element = element;
            this.x = element.getX();
            this.y = element.getY();
            this.scale = element.getScale();
            this.rotation = element.getRotation();
            this.opacity = element.getOpacity();
            this.fillOpacity = element.getFillOpacity();
            this.borderColor = element.getBorderColor();
            this.fillColor = element.getFillColor();
            this.textColor = element.getTextColor();
        }

        private boolean hasChanged() {
            return x != element.getX() ||
                   y != element.getY() ||
                   scale != element.getScale() ||
                   rotation != element.getRotation() ||
                   opacity != element.getOpacity() ||
                   fillOpacity != element.getFillOpacity() ||
                   borderColor != element.getBorderColor() ||
                   fillColor != element.getFillColor() ||
                   textColor != element.getTextColor();
        }

        private void restore() {
            element.setX(x);
            element.setY(y);
            element.setScale(scale);
            element.setRotation(rotation);
            element.setOpacity(opacity);
            element.setFillOpacity(fillOpacity);
            element.setBorderColor(borderColor);
            element.setFillColor(fillColor);
            element.setTextColor(textColor);
        }
    }
}
