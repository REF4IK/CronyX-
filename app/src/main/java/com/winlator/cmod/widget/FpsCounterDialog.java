package com.winlator.cmod.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.slider.Slider;
import com.winlator.cmod.R;

public class FpsCounterDialog {
    private final FpsCounterConfig config;
    private final Context context;
    private CheckBox checkboxEnabled;
    private CheckBox checkboxShowFps;
    private CheckBox checkboxShowRam;
    private CheckBox checkboxShowGpu;
    private CheckBox checkboxShowGpuLoad;
    private CheckBox checkboxShowGpuTemp;
    private CheckBox checkboxShowFrameTimeGraph;
    private CheckBox checkboxShowRenderer;
    private CheckBox checkboxShowCpuLoad;
    private CheckBox checkboxShowCpuTemp;
    private CheckBox checkboxShowBatteryTemp;
    private CheckBox checkboxShowBatteryVoltage;
    private CheckBox checkboxShowCpuCores;
    private CheckBox checkboxShowGpuFreq;
    private CheckBox checkboxShowBatteryLevel;
    private CheckBox checkboxHorizontalLayout;
    private Spinner spinnerCounterStyle;
    private SeekBar seekbarBackgroundOpacity;
    private TextView textOpacityValue;
    private SeekBar seekbarCounterScale;
    private TextView textScaleValue;
    private Slider sliderFpsLimit;
    private TextView textFpsLimitValue;
    private View buttonOk;
    private View buttonCancel;
    private OnConfigChangedListener onConfigChangedListener;
    private AlertDialog dialog;

    public interface OnConfigChangedListener {
        void onConfigChanged();
    }

    public FpsCounterDialog(Context context) {
        this.context = context;
        this.config = new FpsCounterConfig(context);
        setupDialog(context);
    }

    private void setupDialog(Context context) {
        android.content.SharedPreferences prefs = new com.winlator.cmod.core.MmkvPreferences();
        boolean isDarkTheme = prefs.getBoolean("dark_mode", false);

        Context themedContext = isDarkTheme
            ? new android.view.ContextThemeWrapper(context, R.style.AppTheme_Dark)
            : new android.view.ContextThemeWrapper(context, R.style.AppTheme);

        LayoutInflater inflater = LayoutInflater.from(themedContext);
        View view = inflater.inflate(R.layout.fps_counter_dialog, null);

        checkboxEnabled = view.findViewById(R.id.checkbox_fps_enabled);
        checkboxShowFps = view.findViewById(R.id.checkbox_show_fps);
        checkboxShowRam = view.findViewById(R.id.checkbox_show_ram);
        checkboxShowGpu = view.findViewById(R.id.checkbox_show_gpu);
        checkboxShowGpuLoad = view.findViewById(R.id.checkbox_show_gpu_load);
        checkboxShowGpuTemp = view.findViewById(R.id.checkbox_show_gpu_temp);
        checkboxShowFrameTimeGraph = view.findViewById(R.id.checkbox_show_frame_time_graph);
        checkboxShowRenderer = view.findViewById(R.id.checkbox_show_renderer);
        checkboxShowCpuLoad = view.findViewById(R.id.checkbox_show_cpu_load);
        checkboxShowCpuTemp = view.findViewById(R.id.checkbox_show_cpu_temp);
        checkboxShowBatteryTemp = view.findViewById(R.id.checkbox_show_battery_temp);
        checkboxShowBatteryVoltage = view.findViewById(R.id.checkbox_show_battery_voltage);
        checkboxShowCpuCores = view.findViewById(R.id.checkbox_show_cpu_cores);
        checkboxShowGpuFreq = view.findViewById(R.id.checkbox_show_gpu_freq);
        checkboxShowBatteryLevel = view.findViewById(R.id.checkbox_show_battery_level);
        checkboxHorizontalLayout = view.findViewById(R.id.checkbox_horizontal_layout);
        spinnerCounterStyle = view.findViewById(R.id.spinner_fps_counter_style);
        seekbarBackgroundOpacity = view.findViewById(R.id.seekbar_background_opacity);
        textOpacityValue = view.findViewById(R.id.text_opacity_value);
        seekbarCounterScale = view.findViewById(R.id.seekbar_counter_scale);
        textScaleValue = view.findViewById(R.id.text_scale_value);
        sliderFpsLimit = view.findViewById(R.id.SBFpsLimit);
        textFpsLimitValue = view.findViewById(R.id.TVFpsLimitValue);
        buttonOk = view.findViewById(R.id.button_ok);
        buttonCancel = view.findViewById(R.id.button_cancel);

        try {
            android.content.SharedPreferences global = new com.winlator.cmod.core.MmkvPreferences();
            int accentColor = global.getInt("custom_theme_color", 0xFF1A6C59);
            if (buttonOk instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) buttonOk)
                    .setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
            }
            if (buttonCancel instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) buttonCancel).setTextColor(accentColor);
            }
        } catch (Exception ignored) {}

        loadCurrentSettings();
        setupListeners();
        updateModuleCheckboxes();

        dialog = new AlertDialog.Builder(context)
            .setView(view)
            .create();

        if (buttonCancel != null) {
            buttonCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (buttonOk != null) {
            buttonOk.setOnClickListener(v -> {
                saveSettings();
                if (onConfigChangedListener != null) {
                    onConfigChangedListener.onConfigChanged();
                }
                dialog.dismiss();
            });
        }
    }

    private void loadCurrentSettings() {
        checkboxEnabled.setChecked(config.isEnabled());
        checkboxShowFps.setChecked(config.isModuleVisible(FpsCounterConfig.Module.FPS));
        checkboxShowRam.setChecked(config.isModuleVisible(FpsCounterConfig.Module.RAM));
        checkboxShowGpu.setChecked(config.isModuleVisible(FpsCounterConfig.Module.GPU));
        checkboxShowGpuLoad.setChecked(config.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD));
        checkboxShowGpuTemp.setChecked(config.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP));
        checkboxShowFrameTimeGraph.setChecked(config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH));
        checkboxShowRenderer.setChecked(config.isModuleVisible(FpsCounterConfig.Module.RENDERER));
        checkboxShowCpuLoad.setChecked(config.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD));
        checkboxShowCpuTemp.setChecked(config.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP));
        checkboxShowBatteryTemp.setChecked(config.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP));
        checkboxShowBatteryVoltage.setChecked(config.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE));
        if (checkboxShowCpuCores != null) {
            checkboxShowCpuCores.setChecked(config.isModuleVisible(FpsCounterConfig.Module.CPU_CORES));
        }
        if (checkboxShowGpuFreq != null) {
            checkboxShowGpuFreq.setChecked(config.isModuleVisible(FpsCounterConfig.Module.GPU_FREQ));
        }
        if (checkboxShowBatteryLevel != null) {
            checkboxShowBatteryLevel.setChecked(config.isModuleVisible(FpsCounterConfig.Module.BATTERY_LEVEL));
        }
        checkboxHorizontalLayout.setChecked(config.isHorizontalLayout());

        if (spinnerCounterStyle != null) {
            String[] styleNames = {
                context.getString(R.string.fps_counter_style_default),
                context.getString(R.string.fps_counter_style_cyber),
                context.getString(R.string.fps_counter_style_retro),
                context.getString(R.string.fps_counter_style_glass),
                context.getString(R.string.fps_counter_style_winlator_ludashi),
                context.getString(R.string.fps_counter_style_gamenative),
                context.getString(R.string.fps_counter_style_mangohud)
            };
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                styleNames
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCounterStyle.setAdapter(adapter);
            int style = config.getCounterStyle();
            spinnerCounterStyle.setSelection(Math.max(0, Math.min(styleNames.length - 1, style)));
        }

        int currentOpacity = config.getBackgroundOpacity();
        seekbarBackgroundOpacity.setProgress(currentOpacity);
        updateOpacityLabel(currentOpacity);

        int currentScale = Math.max(60, config.getCounterScale());
        seekbarCounterScale.setProgress(currentScale);
        updateScaleLabel(currentScale);

        int currentFpsLimit = config.getFpsLimit();
        sliderFpsLimit.setValue(currentFpsLimit);
        updateFpsLimitLabel(currentFpsLimit);
    }

    private void setupListeners() {
        checkboxEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> updateModuleCheckboxes());

        seekbarBackgroundOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateOpacityLabel(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekbarCounterScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateScaleLabel(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        sliderFpsLimit.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                updateFpsLimitLabel((int) value);
            }
        });
    }

    private void updateModuleCheckboxes() {
        boolean enabled = checkboxEnabled.isChecked();
        checkboxShowFps.setEnabled(enabled);
        checkboxShowRam.setEnabled(enabled);
        checkboxShowGpu.setEnabled(enabled);
        checkboxShowGpuLoad.setEnabled(enabled);
        checkboxShowGpuTemp.setEnabled(enabled);
        checkboxShowFrameTimeGraph.setEnabled(enabled);
        checkboxShowRenderer.setEnabled(enabled);
        checkboxShowCpuLoad.setEnabled(enabled);
        checkboxShowCpuTemp.setEnabled(enabled);
        checkboxShowBatteryTemp.setEnabled(enabled);
        checkboxShowBatteryVoltage.setEnabled(enabled);
        if (checkboxShowCpuCores != null) checkboxShowCpuCores.setEnabled(enabled);
        if (checkboxShowGpuFreq != null) checkboxShowGpuFreq.setEnabled(enabled);
        if (checkboxShowBatteryLevel != null) checkboxShowBatteryLevel.setEnabled(enabled);
        if (spinnerCounterStyle != null) spinnerCounterStyle.setEnabled(enabled);
    }

    private void saveSettings() {
        config.setEnabled(checkboxEnabled.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.FPS, checkboxShowFps.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.RAM, checkboxShowRam.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.GPU, checkboxShowGpu.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.GPU_LOAD, checkboxShowGpuLoad.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.GPU_TEMP, checkboxShowGpuTemp.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH, checkboxShowFrameTimeGraph.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.RENDERER, checkboxShowRenderer.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.CPU_LOAD, checkboxShowCpuLoad.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.CPU_TEMP, checkboxShowCpuTemp.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP, checkboxShowBatteryTemp.isChecked());
        config.setModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE, checkboxShowBatteryVoltage.isChecked());
        if (checkboxShowCpuCores != null) {
            config.setModuleVisible(FpsCounterConfig.Module.CPU_CORES, checkboxShowCpuCores.isChecked());
        }
        if (checkboxShowGpuFreq != null) {
            config.setModuleVisible(FpsCounterConfig.Module.GPU_FREQ, checkboxShowGpuFreq.isChecked());
        }
        if (checkboxShowBatteryLevel != null) {
            config.setModuleVisible(FpsCounterConfig.Module.BATTERY_LEVEL, checkboxShowBatteryLevel.isChecked());
        }
        config.setHorizontalLayout(checkboxHorizontalLayout.isChecked());
        if (spinnerCounterStyle != null) {
            config.setCounterStyle(spinnerCounterStyle.getSelectedItemPosition());
        }
        config.setBackgroundOpacity(seekbarBackgroundOpacity.getProgress());
        config.setCounterScale(Math.max(60, seekbarCounterScale.getProgress()));
        config.setFpsLimit((int) sliderFpsLimit.getValue());
    }

    public void setOnConfigChangedListener(OnConfigChangedListener listener) {
        this.onConfigChangedListener = listener;
    }

    private void updateOpacityLabel(int opacity) {
        int percentage = Math.round((opacity / 255.0f) * 100);
        textOpacityValue.setText(percentage + "%");
    }

    private void updateScaleLabel(int scale) {
        textScaleValue.setText(scale + "%");
    }

    private void updateFpsLimitLabel(int limit) {
        if (limit == 0) {
            textFpsLimitValue.setText(R.string.fps_counter_no_limit);
        } else {
            textFpsLimitValue.setText(String.valueOf(limit));
        }
    }

    public void show() {
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.END | Gravity.TOP;
            params.x = 0;
            params.y = 0;
            params.horizontalMargin = 0f;
            params.verticalMargin = 0f;
            window.setAttributes(params);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

            View decorView = window.getDecorView();
            if (decorView != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false);
                decorView.setPadding(0, 0, 0, 0);

                WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, decorView);
                insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
                insetsController.hide(WindowInsetsCompat.Type.systemBars());
            }
        }
    }
}
