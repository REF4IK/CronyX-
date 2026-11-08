package com.winlator.cmod;



import static com.winlator.cmod.core.AppUtils.showToast;



import android.annotation.SuppressLint;

import android.app.Activity;

import android.graphics.Bitmap;

import android.graphics.BitmapFactory;

import android.content.ClipData;

import android.content.ClipboardManager;

import android.content.Context;

import android.content.Intent;

import android.content.SharedPreferences;

import android.content.pm.ActivityInfo;

import android.content.pm.PackageManager;

import android.content.res.Configuration;

import android.hardware.Sensor;

import android.hardware.SensorEvent;

import android.hardware.SensorEventListener;

import android.hardware.SensorManager;

import android.media.AudioDeviceCallback;

import android.media.AudioDeviceInfo;

import android.media.AudioManager;

import android.net.Uri;

import android.os.Bundle;

import android.os.FileObserver;

import android.os.Handler;

import android.os.Looper;

import android.util.Log;

import android.view.KeyEvent;

import android.view.Menu;

import android.view.MenuItem;

import android.view.MotionEvent;

import android.view.PointerIcon;

import android.view.View;

import android.view.ViewGroup;

import android.widget.ArrayAdapter;

import android.widget.CheckBox;

import android.widget.FrameLayout;

import android.widget.Spinner;

import android.widget.TextView;

import android.widget.Toast;



import androidx.activity.result.ActivityResultLauncher;

import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.ContextCompat;

import androidx.core.view.GravityCompat;

import androidx.drawerlayout.widget.DrawerLayout;

import androidx.preference.PreferenceManager;



import com.google.android.material.navigation.NavigationView;

import com.winlator.cmod.R;

import com.winlator.cmod.box86_64.rc.RCFile;

import com.winlator.cmod.box86_64.rc.RCManager;

import com.winlator.cmod.container.Container;

import com.winlator.cmod.container.ContainerManager;

import com.winlator.cmod.container.Shortcut;

import com.winlator.cmod.contentdialog.ContentDialog;

import com.winlator.cmod.contentdialog.DXVKConfigDialog;

import com.winlator.cmod.contentdialog.DebugDialog;

import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;

import com.winlator.cmod.contentdialog.ScreenEffectDialog;

import com.winlator.cmod.contentdialog.VKD3DConfigDialog;

import com.winlator.cmod.contentdialog.ActiveWindowsDialog;

import com.winlator.cmod.contents.ContentProfile;

import com.winlator.cmod.contents.ContentsManager;

import com.winlator.cmod.contents.AdrenotoolsManager;

import com.winlator.cmod.core.AppUtils;

import com.winlator.cmod.core.DefaultVersion;

import com.winlator.cmod.core.EnvVars;

import com.winlator.cmod.core.EnvironmentManager;

import com.winlator.cmod.core.FileUtils;

import com.winlator.cmod.core.GPUInformation;

import com.winlator.cmod.core.KeyValueSet;

import com.winlator.cmod.core.OnExtractFileListener;

import com.winlator.cmod.core.PreloaderDialog;

import com.winlator.cmod.core.ProcessHelper;

import com.winlator.cmod.core.StringUtils;

import com.winlator.cmod.core.TarCompressorUtils;

import com.winlator.cmod.core.Win32AppWorkarounds;

import com.winlator.cmod.core.WineInfo;

import com.winlator.cmod.core.WineRegistryEditor;

import com.winlator.cmod.core.WineRequestHandler;

import com.winlator.cmod.core.WineStartMenuCreator;

import com.winlator.cmod.core.WineThemeManager;

import com.winlator.cmod.core.WineUtils;

import com.winlator.cmod.fexcore.FEXCoreManager;

import com.winlator.cmod.inputcontrols.ControlsProfile;

import com.winlator.cmod.inputcontrols.ExternalController;

import com.winlator.cmod.inputcontrols.InputControlsManager;

import com.winlator.cmod.math.Mathf;

import com.winlator.cmod.math.XForm;

import com.winlator.cmod.midi.MidiHandler;

import com.winlator.cmod.midi.MidiManager;

import com.winlator.cmod.renderer.EffectComposer;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.HostRenderer;
import com.winlator.cmod.renderer.VulkanRenderer;
import com.winlator.cmod.renderer.XServerRenderer;
import com.winlator.cmod.renderer.ASurfaceRenderer;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.widget.XServerView;

import com.winlator.cmod.steam.SteamLibraryActivity;

import com.winlator.cmod.steam.service.SteamService;


import com.winlator.cmod.widget.FrameRating;

import com.winlator.cmod.widget.FpsCounterDialog;

import com.winlator.cmod.widget.FpsCounterConfig;

import com.winlator.cmod.widget.InputControlsView;

import com.winlator.cmod.widget.QuickAccessPanel;

import com.winlator.cmod.widget.LogView;



import com.winlator.cmod.widget.TouchpadView;

import com.winlator.cmod.widget.WinetricksFloatingView;

import com.winlator.cmod.widget.XServerView;

import com.winlator.cmod.winhandler.MouseEventFlags;

import com.winlator.cmod.winhandler.TaskManagerDialog;

import com.winlator.cmod.winhandler.WinHandler;

import com.winlator.cmod.xconnector.UnixSocketConfig;

import com.winlator.cmod.xenvironment.ImageFs;

import com.winlator.cmod.xenvironment.XEnvironment;

import com.winlator.cmod.xenvironment.components.ALSAServerComponent;

import com.winlator.cmod.xenvironment.components.BionicProgramLauncherComponent;

import com.winlator.cmod.xenvironment.components.GlibcProgramLauncherComponent;

import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;

import com.winlator.cmod.xenvironment.components.NetworkInfoUpdateComponent;

import com.winlator.cmod.xenvironment.components.PulseAudioComponent;

import com.winlator.cmod.xenvironment.components.SysVSharedMemoryComponent;

import com.winlator.cmod.xenvironment.components.XServerComponent;

import com.winlator.cmod.xserver.Atom;

import com.winlator.cmod.xserver.Pointer;

import com.winlator.cmod.xserver.Property;

import com.winlator.cmod.xserver.ScreenInfo;

import com.winlator.cmod.xserver.Window;

import com.winlator.cmod.xserver.WindowManager;

import com.winlator.cmod.xserver.XServer;



import org.json.JSONArray;

import org.json.JSONException;

import org.json.JSONObject;



import java.io.BufferedReader;

import java.io.DataInputStream;

import java.io.DataOutputStream;

import java.io.File;

import java.io.FileInputStream;

import java.io.FileOutputStream;

import java.io.FileReader;

import java.io.IOException;

import java.io.InputStream;

import java.io.InputStreamReader;

import java.io.OutputStream;

import java.lang.reflect.Field;

import java.net.ServerSocket;

import java.net.Socket;

import java.nio.charset.StandardCharsets;

import java.util.ArrayDeque;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.Iterator;

import java.util.Map;

import java.util.concurrent.Executor;

import java.util.concurrent.Executors;



import cn.sherlock.com.sun.media.sound.SF2Soundbank;



public class XServerDisplayActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private XServerView xServerView;

    private InputControlsView inputControlsView;

    private TouchpadView touchpadView;

    private XEnvironment environment;

    private DrawerLayout drawerLayout;

    private com.winlator.cmod.ui.XServerMenuController xserverMenuController;

    private ContainerManager containerManager;

    protected Container container;

    private XServer xServer;

    private InputControlsManager inputControlsManager;

    private ImageFs imageFs;

    private FrameRating frameRating = null;

    private FpsCounterConfig fpsCounterConfig;

    private boolean appWindowStarted = false;

    public Runnable editInputControlsCallback;

    private Shortcut shortcut;

    private String graphicsDriver = Container.DEFAULT_GRAPHICS_DRIVER;

    private HashMap<String, String> graphicsDriverConfig;
    private String graphicsDriverConfigRaw;

    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;

    private String emulator = Container.DEFAULT_EMULATOR;

    private String dxwrapper = Container.DEFAULT_DXWRAPPER;

    private String ddrawrapper = Container.DEFAULT_DDRAWRAPPER;

    private KeyValueSet dxwrapperConfig;

    private String startupSelection;

    private WineInfo wineInfo;

    private final EnvVars envVars = new EnvVars();

    private boolean firstTimeBoot = false;

    private SharedPreferences preferences;

    private OnExtractFileListener onExtractFileListener;

    private WinHandler winHandler;

    private WineRequestHandler wineRequestHandler;

    private float globalCursorSpeed = 1.0f;



    private DebugDialog debugDialog;

    private short taskAffinityMask = 0;

    private short taskAffinityMaskWoW64 = 0;

    private int frameRatingWindowId = -1;

    private final float[] xform = XForm.getInstance();

    private ContentsManager contentsManager;

    private boolean navigationFocused = false;

    private MidiHandler midiHandler;

    private String midiSoundFont = "";

    private String lc_all = "";

    private String vkbasaltConfig = "";

    PreloaderDialog preloaderDialog = new PreloaderDialog(this);

    private Runnable configChangedCallback = null;

    private boolean isPaused = false;

    private boolean isRelativeMouseMovement;



    // Inside the XServerDisplayActivity class

    private SensorManager sensorManager;

    private Sensor gyroSensor;

    private ExternalController controller;



    // Playtime stats tracking

    private long startTime;

    private SharedPreferences playtimePrefs;

    private String shortcutName;

    private Handler handler;

    private Runnable savePlaytimeRunnable;

    private static final long SAVE_INTERVAL_MS = 1000;

    private Integer steamAppIdForCloudSync = null;

    private boolean steamCloudSyncDispatched = false;

    private boolean steamGameSessionReported = false;



    private Handler  timeoutHandler = new Handler(Looper.getMainLooper());

    private Runnable hideControlsRunnable;



    private boolean isDarkMode;



    private String screenEffectProfile;



    private GlibcProgramLauncherComponent glibcLauncher; // Reference to GlibcProgramLauncherComponent

    private BionicProgramLauncherComponent bionicLauncher; // Reference to BionicProgramLauncherComponent

    private FileObserver restartTriggerObserver;

    private FrameLayout xServerRootView;



    private Win32AppWorkarounds win32AppWorkarounds;

    private EnvVars overrideEnvVars;



    private WinetricksFloatingView winetricksFloatingView;


    private QuickAccessPanel quickAccessPanel;



    @Override

    public void onConfigurationChanged(@NonNull Configuration newConfig) {

        super.onConfigurationChanged(newConfig);

        if (configChangedCallback != null) {

            configChangedCallback.run();

            configChangedCallback = null;

        }

    }





    private final SensorEventListener gyroListener = new SensorEventListener() {

        @Override

        public void onSensorChanged(SensorEvent event) {

            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

                float gyroX = event.values[0]; // Rotation around the X-axis

                float gyroY = event.values[1]; // Rotation around the Y-axis



                winHandler.updateGyroData(gyroX, gyroY); // Send gyro data to WinHandler

            }

        }



        @Override

        public void onAccuracyChanged(Sensor sensor, int accuracy) {

            // No action needed

        }

    };



    private void setupAudioDeviceCallback(ALSAServerComponent alsaComponent) {

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (audioManager != null) {

            AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {

                @Override

                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {

                    Log.d("XServerDisplayActivity", "Audio device added, triggering ALSA-Reflector recovery");

                    alsaComponent.onAudioDeviceChange();

                }



                @Override

                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {

                    Log.d("XServerDisplayActivity", "Audio device removed, triggering ALSA-Reflector recovery");

                    alsaComponent.onAudioDeviceChange();

                }

            };

            

            audioManager.registerAudioDeviceCallback(audioDeviceCallback, new Handler(Looper.getMainLooper()));

            alsaComponent.setAudioDeviceCallback(audioDeviceCallback);

            Log.d("XServerDisplayActivity", "Audio device callback registered for ALSA-Reflector");

        }

    }





    @Override

    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        AppUtils.hideSystemUI(this);

        AppUtils.keepScreenOn(this);

        setContentView(R.layout.xserver_display_activity);

        xserverMenuController = new com.winlator.cmod.ui.XServerMenuController(this);



        Intent serviceIntent = new Intent(this, WinlatorForegroundService.class);

        try {

            ContextCompat.startForegroundService(this, serviceIntent);

        } catch (Exception ignored) {

            // Fallback for older devices/edge cases

            startService(serviceIntent);

        }



//        String RESTART_TRIGGER_PATH = "/data/user/0/com.winlator/files/imagefs/tmp/winlator_restart_trigger";

//        String RESTART_TRIGGER_DIR = "/data/user/0/com.winlator/files/imagefs/tmp/";

//

//        restartTriggerObserver = new FileObserver(RESTART_TRIGGER_DIR, FileObserver.CLOSE_WRITE | FileObserver.CREATE) {

//            @Override

//            public void onEvent(int event, String path) {

//                if (path != null && path.equals("winlator_restart_trigger") && event == FileObserver.CLOSE_WRITE) {

//                    Log.d("XServerDisplayActivity", "Detected trigger file creation/modification, restarting wineserver.");

//                    if (glibcLauncher != null) {

//                        glibcLauncher.restartWineServer();

//                        //setupWineSystemFiles();

//                        setupXEnvironment();

//                    } else {

//                        Log.e("XServerDisplayActivity", "glibcLauncher is null; cannot restart wineserver.");

//                    }

//                }

//            }

//        };

//        restartTriggerObserver.startWatching();





//        // Test file creation right after starting the observer

//        try {

//            File testFile = new File(RESTART_TRIGGER_PATH);

//            testFile.createNewFile();

//            Log.d("XServerDisplayActivity", "Test file created to trigger observer.");

//        } catch (IOException e) {

//            Log.e("XServerDisplayActivity", "Failed to create test file: " + e.getMessage());

//        }







        final PreloaderDialog preloaderDialog = new PreloaderDialog(this);

        preferences = new com.winlator.cmod.core.MmkvPreferences();

        fpsCounterConfig = new FpsCounterConfig(this);





        // Check for Dark Mode

        isDarkMode = preferences.getBoolean("dark_mode", false);



        boolean isOpenWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);

        boolean isShareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);



        // Initialize the WinHandler after context is set up

        winHandler = new WinHandler(this);

        winHandler.initializeController();

        controller = winHandler.getCurrentController();



        if (isOpenWithAndroidBrowser || isShareAndroidClipboard)

            wineRequestHandler = new WineRequestHandler(this);



        if (controller != null) {

            int triggerType = preferences.getInt("trigger_type", ExternalController.TRIGGER_IS_AXIS); // Default to TRIGGER_IS_AXIS

            controller.setTriggerType((byte) triggerType); // Cast to byte if needed

        }







        // Check if xinputDisabled extra is passed

        boolean xinputDisabledFromShortcut = false;









        // Initialize SensorManager

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);



        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);



        if (gyroEnabled) {

            // Register the sensor event listener

            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);

        }







        // Record the start time

        startTime = System.currentTimeMillis();



        // Initialize handler for periodic saving

        handler = new Handler(Looper.getMainLooper());

        savePlaytimeRunnable = new Runnable() {

            @Override

            public void run() {

                savePlaytimeData();

                handler.postDelayed(this, SAVE_INTERVAL_MS);

            }

        };

        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);





        // Handler and Runnable to manage timeout for hiding controls



        hideControlsRunnable = () -> {

            boolean currentTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);

            if (currentTimeoutEnabled

                    && inputControlsView != null

                    && !inputControlsView.isEditMode()

                    && !inputControlsView.isShowTouchscreenControls()) {

                inputControlsView.setVisibility(View.GONE);

                Log.d("XServerDisplayActivity", "Touchscreen controls hidden after timeout.");

            }

        };





        contentsManager = new ContentsManager(this);

        contentsManager.syncContents();



        drawerLayout = findViewById(R.id.DrawerLayout);

        drawerLayout.setOnApplyWindowInsetsListener((view, windowInsets) -> windowInsets.replaceSystemWindowInsets(0, 0, 0, 0));

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);



        NavigationView navigationView = findViewById(R.id.NavigationView);



        boolean enableLogs = preferences.getBoolean("enable_wine_debug", false) || preferences.getBoolean("enable_box86_64_logs", false);

        Menu menu = navigationView.getMenu();

        menu.findItem(R.id.main_menu_logs).setVisible(enableLogs);



        navigationView.setNavigationItemSelectedListener(this);

        navigationView.setPointerIcon(PointerIcon.getSystemIcon(this, PointerIcon.TYPE_ARROW));

        navigationView.setOnFocusChangeListener((v, hasFocus) -> navigationFocused = hasFocus);

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {

            @Override

            public void onDrawerOpened(View drawerView) {

                super.onDrawerOpened(drawerView);

                navigationView.requestFocus();

            }

        });



        imageFs = ImageFs.find(this);



        String screenSize = Container.DEFAULT_SCREEN_SIZE;

        if (!isGenerateWineprefix()) {

            containerManager = new ContainerManager(this);

            container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));

//            containerManager.activateContainer(container);



            // Log shortcut_path

            String shortcutPath = getIntent().getStringExtra("shortcut_path");

            Log.d("XServerDisplayActivity", "Shortcut Path: " + shortcutPath);





            // Determine container ID

            int containerId = getIntent().getIntExtra("container_id", 0);

            Log.d("XServerDisplayActivity", "Container ID from Intent: " + containerId);

            if (containerId == 0) {

                Log.d("XServerDisplayActivity", "Container ID is 0, attempting to parse from .desktop file");

                // Proceed with .desktop file parsing

            }





            // If container_id is 0, read from the .desktop file

            if (containerId == 0 && shortcutPath != null && !shortcutPath.isEmpty()) {

                File shortcutFile = new File(shortcutPath);

                containerId = parseContainerIdFromDesktopFile(shortcutFile);

                Log.d("XServerDisplayActivity", "Parsed Container ID from .desktop file: " + containerId);

            }





            // Initialize playtime tracking

            playtimePrefs = new com.winlator.cmod.core.MmkvPreferences("playtime_stats");

            shortcutName = getIntent().getStringExtra("shortcut_name");



            // Ensure shortcutPath is not null before proceeding

            if (shortcutPath != null && !shortcutPath.isEmpty()) {

                if (shortcutName == null || shortcutName.isEmpty()) {

                    shortcutName = parseShortcutNameFromDesktopFile(new File(shortcutPath));

                    Log.d("XServerDisplayActivity", "Parsed Shortcut Name from .desktop file: " + shortcutName);

                }

            } else {

                Log.d("XServerDisplayActivity", "No shortcut path provided, skipping shortcut parsing.");

            }





            // Increment play count at the start of a session

            incrementPlayCount();



            // Log the final container_id

            Log.d("XServerDisplayActivity", "Final Container ID: " + containerId);



            // Retrieve the container and check if it's null

            container = containerManager.getContainerById(containerId);



            if (container == null) {

                Log.e("XServerDisplayActivity", "Failed to retrieve container with ID: " + containerId);

                finish();  // Gracefully exit the activity to avoid crashing

                return;

            }



            containerManager.activateContainer(container);



            if (shortcutPath != null && !shortcutPath.isEmpty()) {

                shortcut = new Shortcut(container, new File(shortcutPath));

                if (shortcut != null) {

                    Log.d("XServerDisplayActivity", "Shortcut loaded: name=" + shortcut.name + ", path=" + shortcut.path);

                    maybeBindSteamCloudSync(shortcut);

                    // Restore per-game runtime settings (FPS counter, input controls, etc.)

                    loadRuntimeSettingsFromShortcut();

                } else {

                    Log.d("XServerDisplayActivity", "Shortcut is NULL after creation");

                }

            } else {

                Log.d("XServerDisplayActivity", "No shortcut path provided");

            }



            // Initialize Win32AppWorkarounds

            win32AppWorkarounds = new Win32AppWorkarounds(this);



            taskAffinityMask = (short) ProcessHelper.getAffinityMask(container.getCPUList(true));

            taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));



            if (shortcut != null) {

                taskAffinityMask = (short) ProcessHelper.getAffinityMask(shortcut.getExtra("cpuList", container.getCPUList(true)));

                taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(shortcut.getExtra("cpuListWoW64", container.getCPUListWoW64(true)));

            }



            win32AppWorkarounds.setTaskAffinityMask(taskAffinityMask);

            win32AppWorkarounds.setTaskAffinityMaskWoW64(taskAffinityMaskWoW64);



            // Determine the class name for the startup workarounds

            String wmClass = shortcut != null ? shortcut.getExtra("wmClass", "") : "";

            Log.d("XServerDisplayActivity", "Startup wmClass: " + wmClass);



            if (!wmClass.isEmpty()) {

                // Apply startup workarounds based on wmClass

                win32AppWorkarounds.applyStartupWorkarounds(wmClass);

            } else {

                // Fallback: Use the executable name for workarounds

                String execPath = getIntent().getStringExtra("exec_path");

                Log.d("XServerDisplayActivity", "Startup execPath: " + execPath);



                if (execPath != null && !execPath.isEmpty()) {

                    String execName = FileUtils.getName(execPath);

                    Log.d("XServerDisplayActivity", "Startup execName: " + execName);



                    win32AppWorkarounds.applyStartupWorkarounds(execName);

                } else {

                    Log.w("XServerDisplayActivity", "No wmClass or execPath provided for startup workarounds.");

                }

            }



            firstTimeBoot = container.getExtra("appVersion").isEmpty();



            String wineVersion = container.getWineVersion();

            wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);



            imageFs.setWinePath(wineInfo.path);



            ProcessHelper.removeAllDebugCallbacks();

            if (enableLogs) {

                LogView.setFilename(getExecutable());

                ProcessHelper.addDebugCallback(debugDialog = new DebugDialog(this));

            }



            // Retrieve secondary executable and delay

            String secondaryExec = shortcut != null ? shortcut.getExtra("secondaryExec") : null;

            int execDelay = shortcut != null ? Integer.parseInt(shortcut.getExtra("execDelay", "0")) : 0;



            // Debug logging for secondaryExec and execDelay

            Log.d("XServerDisplayActivity", "Secondary Exec: " + secondaryExec);

            Log.d("XServerDisplayActivity", "Execution Delay: " + execDelay);



            // If a secondary executable is specified, schedule it

            if (secondaryExec != null && !secondaryExec.isEmpty() && execDelay > 0) {

                scheduleSecondaryExecution(secondaryExec, execDelay);

                Log.d("XServerDisplayActivity", "Scheduling secondary execution: " + secondaryExec + " with delay: " + execDelay);

            } else {

                Log.d("XServerDisplayActivity", "No valid secondary executable or delay is zero, skipping scheduling.");

            }



            graphicsDriver = container.getGraphicsDriver();

            String graphicsDriverConfig = container.getGraphicsDriverConfig();

            audioDriver = container.getAudioDriver();

            emulator = container.getEmulator();

            midiSoundFont = container.getMIDISoundFont();

            dxwrapper = container.getDXWrapper();

            ddrawrapper = container.getDDrawWrapper();

            String dxwrapperConfig = container.getDXWrapperConfig();

            screenSize = container.getScreenSize();

            winHandler.setInputType((byte) container.getInputType());

            lc_all = container.getLC_ALL();

            isRelativeMouseMovement = preferences.getBoolean("relative_mouse_movement", false);



            // Log the entire intent to verify the extras

            Intent intent = getIntent();

            Log.d("XServerDisplayActivity", "Intent Extras: " + intent.getExtras());



            if (shortcut != null) {

                graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());

                graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());

                audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());

                emulator = shortcut.getExtra("emulator", container.getEmulator());

                dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());

                ddrawrapper = shortcut.getExtra("ddrawrapper", container.getDDrawWrapper());

                dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());

                screenSize = shortcut.getExtra("screenSize", container.getScreenSize());

                lc_all = shortcut.getExtra("lc_all", container.getLC_ALL());

                String inputType = shortcut.getExtra("inputType");

                if (!inputType.isEmpty()) winHandler.setInputType(Byte.parseByte(inputType));

                String xinputDisabledString = shortcut.getExtra("disableXinput", "false");

                isRelativeMouseMovement = shortcut.getExtra("relativeMouseMovement", preferences.getBoolean("relative_mouse_movement", false) ? "1" : "0").equals("1");

                xinputDisabledFromShortcut = parseBoolean(xinputDisabledString);

                // Pass the value to WinHandler

                winHandler.setXInputDisabled(xinputDisabledFromShortcut);

                String sharpnessEffect = shortcut.getExtra("sharpnessEffect", "None");

                if (!sharpnessEffect.equals("None")) {

                    double sharpnessLevel = Double.parseDouble(shortcut.getExtra("sharpnessLevel", "100"));

                    double sharpnessDenoise = Double.parseDouble(shortcut.getExtra("sharpnessDenoise", "100"));

                    vkbasaltConfig = "effects=" + sharpnessEffect.toLowerCase() + ";" + "casSharpness=" + sharpnessLevel / 100 + ";" + "dlsSharpness=" + sharpnessLevel / 100  + ";" + "dlsDenoise=" + sharpnessDenoise / 100 + ";" + "enableOnLaunch=True";

                }

                Log.d("XServerDisplayActivity", "XInput Disabled from Shortcut: " + xinputDisabledFromShortcut);

            }



            this.graphicsDriverConfigRaw = graphicsDriverConfig;
            this.graphicsDriverConfig = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);



            if (dxwrapper.equals("dxvk") || dxwrapper.equals("vkd3d")) {

                this.dxwrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);

            }







            if (!wineInfo.isWin64()) {

                onExtractFileListener = (file, size) -> {

                    String path = file.getPath();

                    if (path.contains("system32/")) return null;

                    return new File(path.replace("syswow64/", "system32/"));

                };

            }

        }



        String preloaderTitle = null;

        if (shortcut != null && shortcut.name != null && !shortcut.name.isEmpty()) {

            preloaderTitle = shortcut.name;

        } else if (container != null && container.getName() != null && !container.getName().isEmpty()) {

            preloaderTitle = container.getName();

        }

        String preloaderSubtitle = null;

        if (wineInfo != null && wineInfo.identifier() != null && !wineInfo.identifier().isEmpty()) {

            preloaderSubtitle = wineInfo.identifier();

        } else if (container != null && container.getWineVersion() != null && !container.getWineVersion().isEmpty()) {

            preloaderSubtitle = container.getWineVersion();

        }

        if (preloaderTitle != null) preloaderDialog.setTitle(preloaderTitle);

        if (preloaderSubtitle != null) preloaderDialog.setSubtitle(preloaderSubtitle);

        if (shortcut != null) {
            preloaderDialog.setSteamAppId(shortcut.getExtra("steamAppId"));
        }

        preloaderDialog.show(R.string.starting_up);



        if (shortcut != null) {

            final String shortcutNameForCover = shortcut.name;

            final String customCoverPath = shortcut.getCustomCoverArtPath();

            Executors.newSingleThreadExecutor().execute(() -> {

                Bitmap coverBitmap = null;

                // 1. Landscape cover (grid card вЂ” 920x430)
                if (coverBitmap == null && shortcutNameForCover != null) {
                    java.io.File landscapeFile = new java.io.File(
                            getFilesDir(), "coverArtCache/" + shortcutNameForCover + "_l.png");
                    if (landscapeFile.exists()) {
                        coverBitmap = BitmapFactory.decodeFile(landscapeFile.getAbsolutePath());
                    }
                }

                // 2. Custom cover
                if (coverBitmap == null && customCoverPath != null && !customCoverPath.isEmpty()) {
                    coverBitmap = BitmapFactory.decodeFile(customCoverPath);
                }

                // 3. Portrait cover (game info card вЂ” 600x900)
                if (coverBitmap == null && shortcutNameForCover != null) {
                    java.io.File portraitFile = new java.io.File(
                            getFilesDir(), "coverArtCache/" + shortcutNameForCover + ".png");
                    if (portraitFile.exists()) {
                        coverBitmap = BitmapFactory.decodeFile(portraitFile.getAbsolutePath());
                    }
                }

                // 4. Container cover arts directory
                if (coverBitmap == null && shortcutNameForCover != null) {
                    java.io.File containerCover = new java.io.File(
                            shortcut.container.getRootDir(),
                            "app_data/cover_arts/" + shortcutNameForCover + ".png");
                    if (containerCover.exists()) {
                        coverBitmap = BitmapFactory.decodeFile(containerCover.getAbsolutePath());
                    }
                }

                if (coverBitmap != null) {
                    preloaderDialog.setCoverArtOnUiThread(coverBitmap);
                }

            });

        }





        inputControlsManager = new InputControlsManager(this);

        xServer = new XServer(new ScreenInfo(screenSize));

        xServer.setWinHandler(winHandler);



        boolean[] winStarted = {false};



        // Add the OnWindowModificationListener for dynamic workarounds

        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {

            @Override

            public void onUpdateWindowContent(Window window) {

                if (!winStarted[0] && window.isApplicationWindow()) {

                    xServerView.getRenderer().setCursorVisible(true);

                    preloaderDialog.closeOnUiThread();

                    winStarted[0] = true;

					appWindowStarted = true;

					updateFpsCounterVisibility();

                    notifySteamGameSessionStarted();

                }

                    

                boolean profiling = com.winlator.cmod.widget.ProfilingSession.getInstance().isActive();

                if (frameRating != null

                        && (frameRating.getVisibility() == View.VISIBLE

                                ? frameRatingWindowId == window.id

                                : profiling)) {

                    frameRating.update();

                } else if (profiling) {

                    com.winlator.cmod.widget.ProfilingSession.getInstance().collectSample(com.winlator.cmod.XServerDisplayActivity.this);

                }

            }

           

            @Override

            public void onMapWindow(Window window) {

                // Log the class name of the mapped window

                Log.d("XServerDisplayActivity", "onMapWindow: Detected window className: " + window.getClassName());



                // Apply task affinity and other workarounds

                if (win32AppWorkarounds != null) {

                    // Apply dynamic workarounds based on the window class name

                    win32AppWorkarounds.applyStartupWorkarounds(window.getClassName());



                    // Assign CPU affinity for the process

                    win32AppWorkarounds.assignTaskAffinity(window);

                } else {

                    Log.e("XServerDisplayActivity", "win32AppWorkarounds is null in onMapWindow.");

                }

            }



            @Override

            public void onModifyWindowProperty(Window window, Property property) {

                changeFrameRatingVisibility(window, property);

            }    



            @Override

            public void onUnmapWindow(Window window) {

                changeFrameRatingVisibility(window, null);

            }

        });



        if (!midiSoundFont.equals("")) {

            InputStream in = null;

            InputStream finalIn = in;

            MidiManager.OnMidiLoadedCallback callback = new MidiManager.OnMidiLoadedCallback() {

                @Override

                public void onSuccess(SF2Soundbank soundbank) {

                    midiHandler = new MidiHandler();

                    midiHandler.setSoundBank(soundbank);

                    midiHandler.start();

                }



                @Override

                public void onFailed(Exception e) {

                    try {

                        finalIn.close();

                    } catch (Exception e2) {
                        Log.e("XServerDisplayActivity", "Failed to close MIDI input stream", e2);
                    }

                }

            };

            try {

                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {

                    in = getAssets().open(MidiManager.SF2_ASSETS_DIR + "/" + midiSoundFont);

                    MidiManager.load(in, callback);

                } else

                    MidiManager.load(new File(MidiManager.getSoundFontDir(this), midiSoundFont), callback);

            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "Failed to load MIDI soundfont: " + midiSoundFont, e);
            }

        }



        // Check if a profile is defined by the shortcut

        String controlsProfile = shortcut != null ? shortcut.getExtra("controlsProfile", "") : "";



        Runnable runnable = () -> {

            setupUI();

            if (controlsProfile.isEmpty()) {

                // No profile defined, run the simulated dialog confirmation for input controls

                simulateConfirmInputControlsDialog();

            }

            Executors.newSingleThreadExecutor().execute(() -> {

                    

                if (!isGenerateWineprefix()) {

                    preloaderDialog.setStageOnUiThread(getString(R.string.stage_preparing_wine));

                    setupWineSystemFiles();

                    preloaderDialog.setStageOnUiThread(getString(R.string.stage_extracting_driver));

                    extractGraphicsDriverFiles();

//                    container.setGraphicsDriverVersion(originalContainerDriverVersion);

//                    container.saveData();

                    preloaderDialog.setStageOnUiThread(getString(R.string.stage_configuring_audio));

                    changeWineAudioDriver();

//                    runWinetricksAfterSetup();

                    // Run winetricks before setting up the X environment

//                    runWinetricks("--force vcrun2010");  // Replace with the desired winetricks arguments

                }

                try {

                    preloaderDialog.setStageOnUiThread(getString(R.string.stage_starting_x_server));

                    setupXEnvironment();

                    preloaderDialog.setStageOnUiThread(getString(R.string.stage_waiting_launch));

                } catch (PackageManager.NameNotFoundException e) {

                    throw new RuntimeException(e);

                }



//                runWinetricksAfterSetup();

                // Run winetricks after setting up the X environment

//                runWinetricks("--force vcrun2010");  // Replace with the desired winetricks arguments

            });

        };



        if (xServer.screenInfo.height > xServer.screenInfo.width) {

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            configChangedCallback = runnable;

        } else

              runnable.run();

    }



    // Method to parse container_id from .desktop file

    private int parseContainerIdFromDesktopFile(File desktopFile) {

        int containerId = 0;

        if (desktopFile.exists()) {

            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {

                String line;

                while ((line = reader.readLine()) != null) {

                    if (line.startsWith("container_id:")) {

                        containerId = Integer.parseInt(line.split(":")[1].trim());

                        break;

                    }

                }

            } catch (IOException | NumberFormatException e) {

                Log.e("XServerDisplayActivity", "Error parsing container_id from .desktop file", e);

            }

        }

        return containerId;

    }



    private boolean parseBoolean(String value) {

        // Return true for "true", "1", "yes" (case-insensitive)

        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {

            return true;

        }

        // Return false for any other value, including "false", "0", "no"

        return false;

    }











    // Pointer capture handling lives in TouchpadView.onCapturedPointer (raw deltas, no XForm)








    //    private void setCustomCursor() {

//        View decorView = getWindow().getDecorView();

//        Bitmap transparentCursorBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.transparent_cursor);

//        PointerIcon transparentCursorIcon = PointerIcon.create(transparentCursorBitmap, 0, 0);

//        decorView.setPointerIcon(transparentCursorIcon);

//    }

    @Override

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {

            if (editInputControlsCallback != null) {

                editInputControlsCallback.run();

                editInputControlsCallback = null;

            }

        }

    }





    @Override

    public void onResume() {

        super.onResume();

        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);



        if (gyroEnabled) {

            // Re-register the sensor listener when the activity is resumed

            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);

        }



        if (environment != null) {

            xServerView.onResume();

            environment.onResume();

        }

        startTime = System.currentTimeMillis();

        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);

        ProcessHelper.resumeAllWineProcesses();

    }



    @Override

    public void onPause() {

        super.onPause();

        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);



        if (gyroEnabled) {

            // Unregister the sensor listener when the activity is paused

            sensorManager.unregisterListener(gyroListener);

        }



        // Check if we are entering Picture-in-Picture mode

        if (!isInPictureInPictureMode()) {

            // Keep the container alive in background, but pause surface rendering.

            if (xServerView != null) xServerView.onPause();

        }



        savePlaytimeData();

        handler.removeCallbacks(savePlaytimeRunnable);

    }





    private void savePlaytimeData() {

        long endTime = System.currentTimeMillis();

        long playtime = endTime - startTime;



        // Ensure that playtime is not negative

        if (playtime < 0) {

            playtime = 0;

        }



        SharedPreferences.Editor editor = playtimePrefs.edit();

        String playtimeKey = shortcutName + "_playtime";



        // Accumulate the playtime into totalPlaytime

        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0) + playtime;

        editor.putLong(playtimeKey, totalPlaytime);

        editor.apply();



        // Reset startTime to the current time for the next interval

        startTime = System.currentTimeMillis();

    }





    private void incrementPlayCount() {

        SharedPreferences.Editor editor = playtimePrefs.edit();

        String playCountKey = shortcutName + "_play_count";

        int playCount = playtimePrefs.getInt(playCountKey, 0) + 1;

        editor.putInt(playCountKey, playCount);

        editor.apply();

    }



    private boolean isExiting = false;

    private void exit() {
        if (isExiting) return;
        isExiting = true;

        // РџРѕРєР°Р·С‹РІР°РµРј СЌРєСЂР°РЅ РІС‹С…РѕРґР° (РѕР±Р»РѕР¶РєР° в†’ СЃС‚СЂРµР»РєР° РІР»РµРІРѕ в†’ Windows)
        runOnUiThread(() -> {
            preloaderDialog.setExitModeEnabled(true);
            preloaderDialog.show(R.string.exiting_container);
            preloaderDialog.setStageOnUiThread(getString(R.string.cleaning_up_wine));
        });

        // РўСЏР¶С‘Р»СѓСЋ СЂР°Р±РѕС‚Сѓ (СЃРѕС…СЂР°РЅРµРЅРёРµ, СѓР±РёР№СЃС‚РІРѕ wine-РїСЂРѕС†РµСЃСЃРѕРІ, РѕС‡РёСЃС‚РєСѓ СЂРµРЅРґРµСЂРµСЂР°)
        // РІС‹РїРѕР»РЅСЏРµРј РІ С„РѕРЅРѕРІРѕРј РїРѕС‚РѕРєРµ, С‡С‚РѕР±С‹ UI РЅРµ В«Р·Р°РІРёСЃР°Р»В» РїСЂРё РІС‹С…РѕРґРµ РёР· РєРѕРЅС‚РµР№РЅРµСЂР°
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                saveRuntimeSettingsToShortcut();

                ProcessHelper.terminateAllWineProcesses();

                long start = System.currentTimeMillis();
                while (!ProcessHelper.listRunningWineProcesses().isEmpty()) {
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed >= 1500) break;
                    try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
                }

                ProcessHelper.killAllWineProcesses();

                if (xServerView != null) {
                    try {
                        xServerView.getRenderer().forceCleanup();
                    } catch (Exception e) {
                        Log.w("XServerDisplayActivity", "forceCleanup failed", e);
                    }
                }

                if (midiHandler != null) midiHandler.stop();

                if (sensorManager != null) sensorManager.unregisterListener(gyroListener);

                if (environment != null) environment.stopEnvironmentComponents();

                if (winHandler != null) winHandler.stop();

                if (wineRequestHandler != null) wineRequestHandler.stop();
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "exit cleanup failed", e);
            }

            // UI-РґРµР№СЃС‚РІРёСЏ вЂ” С‚РѕР»СЊРєРѕ РЅР° РіР»Р°РІРЅРѕРј РїРѕС‚РѕРєРµ
            runOnUiThread(() -> {
                if (xServerView != null) xServerView.setVisibility(View.GONE);
                // Р—Р°РєСЂС‹РІР°РµРј РґРёР°Р»РѕРі РІС‹С…РѕРґР° Р”Рћ finish вЂ” РёРЅР°С‡Рµ WindowLeaked
                // (РѕРєРЅРѕ РґРёР°Р»РѕРіР° РѕСЃС‚Р°С‘С‚СЃСЏ РІ WindowManager РїРѕСЃР»Рµ Р·Р°РєСЂС‹С‚РёСЏ Р°РєС‚РёРІРёС‚Рё)
                if (preloaderDialog != null && preloaderDialog.isShowing()) preloaderDialog.close();

                if (returnToSteamLibraryIfNeeded()) {
                    super.finish();
                    return;
                }

                // Р’РѕР·РІСЂР°С‚ РІ РіР»Р°РІРЅРѕРµ РјРµРЅСЋ Р‘Р•Р— РїРµСЂРµР·Р°РїСѓСЃРєР° РїСЂРѕС†РµСЃСЃР°:
                // AppUtils.restartApplication РґРµР»Р°РµС‚ Runtime.exit(0) в†’ С‡С‘СЂРЅС‹Р№ СЌРєСЂР°РЅ + С…РѕР»РѕРґРЅС‹Р№ СЃС‚Р°СЂС‚.
                // MainActivity СѓР¶Рµ РІ Р±СЌРєСЃС‚РµРєРµ вЂ” РїРѕРґРЅРёРјР°РµРј РµС‘ РЅР°РІРµСЂС… Рё РїР»Р°РІРЅРѕ Р·Р°РєСЂС‹РІР°РµРј РёРіСЂРѕРІРѕР№ СЌРєСЂР°РЅ.
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(mainIntent);
                finish();
            });
        });
    }



    @Override

    protected void onDestroy() {

        // Save per-game runtime settings before destroy

        saveRuntimeSettingsToShortcut();

        savePlaytimeData(); // Save on destroy

        handler.removeCallbacks(savePlaytimeRunnable);

        if (restartTriggerObserver != null) restartTriggerObserver.stopWatching();

        dispatchSteamCloudSyncIfNeeded();



        try {

            stopService(new Intent(this, WinlatorForegroundService.class));

        } catch (Exception ignored) {

        }

        super.onDestroy();

    }

    /**
     * Saves current runtime settings (FPS counter, input controls, screen effects)
     * back to the shortcut or container so they persist per-game across sessions.
     */
    private void saveRuntimeSettingsToShortcut() {
        if (shortcut == null && container == null) return;
        try {
            JSONObject settings = new JSONObject();

            // --- FPS Counter settings ---
            JSONObject fps = new JSONObject();
            fps.put("enabled", fpsCounterConfig.isEnabled());
            fps.put("show_fps", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.FPS));
            fps.put("show_ram", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.RAM));
            fps.put("show_gpu", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.GPU));
            fps.put("show_gpu_load", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD));
            fps.put("show_gpu_temp", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP));
            fps.put("show_frame_time_graph", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH));
            fps.put("show_renderer", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.RENDERER));
            fps.put("show_cpu_load", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD));
            fps.put("show_cpu_temp", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP));
            fps.put("show_battery_temp", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP));
            fps.put("show_battery_voltage", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE));
            fps.put("show_cpu_cores", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.CPU_CORES));
            fps.put("show_gpu_freq", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.GPU_FREQ));
            fps.put("show_battery_percent", fpsCounterConfig.isModuleVisible(FpsCounterConfig.Module.BATTERY_LEVEL));
            fps.put("horizontal_layout", fpsCounterConfig.isHorizontalLayout());
            fps.put("background_opacity", fpsCounterConfig.getBackgroundOpacity());
            fps.put("scale", fpsCounterConfig.getCounterScale());
            fps.put("fps_limit", fpsCounterConfig.getFpsLimit());
            fps.put("style", fpsCounterConfig.getCounterStyle());
            fps.put("white_fonts", fpsCounterConfig.isWhiteFonts());
            settings.put("fps_counter", fps);

            // --- Input controls settings ---
            JSONObject input = new JSONObject();
            input.put("show_touchscreen_controls", inputControlsView != null && inputControlsView.isShowTouchscreenControls());
            input.put("touchscreen_timeout_enabled", preferences.getBoolean("touchscreen_timeout_enabled", false));
            input.put("touchscreen_haptics_enabled", preferences.getBoolean("touchscreen_haptics_enabled", false));
            input.put("gyro_enabled", preferences.getBoolean("gyro_enabled", false));
            input.put("gyro_sensitivity", preferences.getFloat("gyro_sensitivity", 1.0f));
            input.put("quick_access_panel_enabled", preferences.getBoolean("quick_access_panel_enabled", false));
            input.put("relative_mouse_movement", preferences.getBoolean("relative_mouse_movement", false));
            if (inputControlsView != null && inputControlsView.getProfile() != null) {
                input.put("controls_profile_id", inputControlsView.getProfile().id);
            }
            settings.put("input_controls", input);

            // --- Screen effects settings ---
            JSONObject effects = new JSONObject();
            effects.put("sharpen", preferences.getBoolean("effect_sharpen", false));
            settings.put("screen_effects", effects);

            String json = settings.toString();
            String name;
            if (shortcut != null) {
                shortcut.putExtra("runtimeSettings", json);
                shortcut.saveData();
                name = shortcut.name;
            } else {
                container.putExtra("runtimeSettings", json);
                container.saveData();
                name = container.getName();
            }
            Log.d("XServerDisplayActivity", "Runtime settings saved: " + name);
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to save runtime settings", e);
        }
    }

    /**
     * Restores previously saved runtime settings from the shortcut or container
     * and applies them to the global preference stores so they take effect.
     */
    private void loadRuntimeSettingsFromShortcut() {
        if (shortcut == null && container == null) return;
        try {
            String json;
            String name;
            if (shortcut != null) {
                json = shortcut.getExtra("runtimeSettings");
                name = shortcut.name;
            } else {
                json = container.getExtra("runtimeSettings");
                name = container.getName();
            }
            if (json == null || json.isEmpty()) return;

            JSONObject settings = new JSONObject(json);
            Log.d("XServerDisplayActivity", "Restoring runtime settings: " + name);

            // --- Restore FPS Counter settings ---
            if (settings.has("fps_counter")) {
                JSONObject fps = settings.getJSONObject("fps_counter");
                if (fps.has("enabled")) fpsCounterConfig.setEnabled(fps.getBoolean("enabled"));
                if (fps.has("show_fps")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.FPS, fps.getBoolean("show_fps"));
                if (fps.has("show_ram")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.RAM, fps.getBoolean("show_ram"));
                if (fps.has("show_gpu")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.GPU, fps.getBoolean("show_gpu"));
                if (fps.has("show_gpu_load")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.GPU_LOAD, fps.getBoolean("show_gpu_load"));
                if (fps.has("show_gpu_temp")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.GPU_TEMP, fps.getBoolean("show_gpu_temp"));
                if (fps.has("show_frame_time_graph")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH, fps.getBoolean("show_frame_time_graph"));
                if (fps.has("show_renderer")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.RENDERER, fps.getBoolean("show_renderer"));
                if (fps.has("show_cpu_load")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.CPU_LOAD, fps.getBoolean("show_cpu_load"));
                if (fps.has("show_cpu_temp")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.CPU_TEMP, fps.getBoolean("show_cpu_temp"));
                if (fps.has("show_battery_temp")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP, fps.getBoolean("show_battery_temp"));
                if (fps.has("show_battery_voltage")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE, fps.getBoolean("show_battery_voltage"));
                if (fps.has("show_cpu_cores")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.CPU_CORES, fps.getBoolean("show_cpu_cores"));
                if (fps.has("show_gpu_freq")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.GPU_FREQ, fps.getBoolean("show_gpu_freq"));
                if (fps.has("show_battery_percent")) fpsCounterConfig.setModuleVisible(FpsCounterConfig.Module.BATTERY_LEVEL, fps.getBoolean("show_battery_percent"));
                if (fps.has("horizontal_layout")) fpsCounterConfig.setHorizontalLayout(fps.getBoolean("horizontal_layout"));
                if (fps.has("background_opacity")) fpsCounterConfig.setBackgroundOpacity(fps.getInt("background_opacity"));
                if (fps.has("scale")) fpsCounterConfig.setCounterScale(fps.getInt("scale"));
                if (fps.has("fps_limit")) fpsCounterConfig.setFpsLimit(fps.getInt("fps_limit"));
                if (fps.has("style")) fpsCounterConfig.setCounterStyle(fps.getInt("style"));
                if (fps.has("white_fonts")) fpsCounterConfig.setWhiteFonts(fps.getBoolean("white_fonts"));
            }

            // --- Restore Input controls settings ---
            if (settings.has("input_controls")) {
                JSONObject input = settings.getJSONObject("input_controls");
                SharedPreferences.Editor editor = preferences.edit();
                if (input.has("show_touchscreen_controls")) {
                    editor.putBoolean("show_touchscreen_controls_enabled", input.getBoolean("show_touchscreen_controls"));
                }
                if (input.has("touchscreen_timeout_enabled")) {
                    editor.putBoolean("touchscreen_timeout_enabled", input.getBoolean("touchscreen_timeout_enabled"));
                }
                if (input.has("touchscreen_haptics_enabled")) {
                    editor.putBoolean("touchscreen_haptics_enabled", input.getBoolean("touchscreen_haptics_enabled"));
                }
                if (input.has("gyro_enabled")) {
                    editor.putBoolean("gyro_enabled", input.getBoolean("gyro_enabled"));
                }
                if (input.has("gyro_sensitivity")) {
                    editor.putFloat("gyro_sensitivity", (float) input.getDouble("gyro_sensitivity"));
                }
                if (input.has("quick_access_panel_enabled")) {
                    editor.putBoolean("quick_access_panel_enabled", input.getBoolean("quick_access_panel_enabled"));
                }
                if (input.has("relative_mouse_movement")) {
                    editor.putBoolean("relative_mouse_movement", input.getBoolean("relative_mouse_movement"));
                }
                editor.apply();
            }

            // --- Restore Screen effects ---
            if (settings.has("screen_effects")) {
                JSONObject effects = settings.getJSONObject("screen_effects");
                if (effects.has("sharpen")) {
                    preferences.edit().putBoolean("effect_sharpen", effects.getBoolean("sharpen")).apply();
                }
            }

            Log.d("XServerDisplayActivity", "Runtime settings restored: " + name);
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "Failed to restore runtime settings", e);
        }
    }

    @Override

    protected void onStop() {

        super.onStop();

        savePlaytimeData();

        handler.removeCallbacks(savePlaytimeRunnable);







    }



    private void maybeBindSteamCloudSync(Shortcut shortcut) {

        if (shortcut == null) return;



        String source = shortcut.getExtra("game_source");

        if (source == null || !source.equalsIgnoreCase("STEAM")) {

            source = shortcut.getExtra("gameSource");

        }

        if (source == null || !source.equalsIgnoreCase("STEAM")) {

            return;

        }



        String appIdValue = shortcut.getExtra("app_id");

        if (appIdValue == null || appIdValue.isEmpty()) {

            appIdValue = shortcut.getExtra("steamAppId");

        }

        if (appIdValue == null || appIdValue.isEmpty()) {

            return;

        }



        try {

            int parsedAppId = Integer.parseInt(appIdValue);

            if (parsedAppId > 0) {

                steamAppIdForCloudSync = parsedAppId;

                Log.d("XServerDisplayActivity", "Steam cloud sync bound to appId=" + parsedAppId);

            }

        } catch (NumberFormatException ignored) {

        }

    }



    private void dispatchSteamCloudSyncIfNeeded() {

        if (steamCloudSyncDispatched) return;

        if (!isFinishing()) return;

        if (steamAppIdForCloudSync == null || steamAppIdForCloudSync <= 0) return;



        steamCloudSyncDispatched = true;

        SteamService.handleAppExitCloudSync(getApplicationContext(), steamAppIdForCloudSync);

    }



    private void notifySteamGameSessionStarted() {

        if (steamGameSessionReported) return;

        if (steamAppIdForCloudSync == null || steamAppIdForCloudSync <= 0) return;



        steamGameSessionReported = true;

        String launchExePath = shortcut != null ? shortcut.getExtra("launch_exe_path") : null;

        new Handler(Looper.getMainLooper()).postDelayed(() ->

                SteamService.notifyGameRunningFromWineProcesses(

                        steamAppIdForCloudSync,

                        launchExePath,

                        shortcutName

                ),

                1500L

        );

    }



    private boolean returnToSteamLibraryIfNeeded() {

        Intent sourceIntent = getIntent();

        if (sourceIntent == null || !sourceIntent.getBooleanExtra("return_to_steam_library", false)) {

            return false;

        }



        Intent intent = new Intent(this, SteamLibraryActivity.class);

        int appId = sourceIntent.getIntExtra("steam_library_app_id", 0);

        if (appId > 0) {

            intent.putExtra("steam_library_selected_app_id", appId);

        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        startActivity(intent);

        return true;

    }





    @Override
    public void onBackPressed() {
        if (environment != null) {
            if (xserverMenuController != null) {
                if (xserverMenuController.isMenuVisible()) {
                    xserverMenuController.hideMenu();
                } else {
                    xserverMenuController.showMenu();
                }
            }
        }
    }



    @Override

    public void finish() {

        if (!isFinishing()) {

            returnToSteamLibraryIfNeeded();

        }

        super.finish();

    }



    public boolean isPaused() {
        return isPaused;
    }

    public void closeXServerMenu() {
        if (xserverMenuController != null) {
            runOnUiThread(() -> xserverMenuController.hideMenu());
        }
    }

    private void openXServerDrawer() {
        if (environment != null) {
            if (xserverMenuController != null) {
                runOnUiThread(() -> xserverMenuController.toggleMenu());
            }
        }
    }



    /**
     * Растяжение экрана ползунком из «Эффектов экрана»: 0 — как есть (aspect-fit),
     * 1 — во весь экран (прежний фиксированный stretch), промежуточные — частичное
     * растяжение по бокам. Живое применение + пересчёт маппинга ввода.
     */
    public void applyScreenStretch(float amount) {
        final float v = Mathf.clamp(amount, 0f, 1f);
        HostRenderer r = xServerView != null ? xServerView.getRenderer() : null;
        if (r instanceof VulkanRenderer) {
            VulkanRenderer vr = (VulkanRenderer) r;
            boolean needOn = v > 0f;
            if (vr.isFullscreen() != needOn) vr.toggleFullscreen();
            vr.setStretchAmount(v);
        } else if (r instanceof GLRenderer) {
            GLRenderer gl = (GLRenderer) r;
            gl.setFullscreenMode(v > 0f ? Container.FULLSCREEN_STRETCH : Container.FULLSCREEN_OFF);
            gl.setStretchAmount(v);
        } else if (r instanceof ASurfaceRenderer) {
            // Surfacer не имеет fullscreen-режимов — растяжение применяется напрямую.
            ((ASurfaceRenderer) r).setStretchAmount(v);
        }
        if (touchpadView != null) touchpadView.toggleFullscreen();
    }

    public void handleXServerMenuAction(int itemId) {
        final HostRenderer renderer = xServerView.getRenderer();

        switch (itemId) {
            case R.id.main_menu_keyboard:
                AppUtils.showKeyboard(this);
                closeXServerMenu();
                break;

            case R.id.main_menu_input_controls:
                showInputControlsDialog();
                closeXServerMenu();
                break;

            case R.id.main_menu_active_windows:
                showActiveWindowsDialog();
                closeXServerMenu();
                break;

            case R.id.main_menu_toggle_fullscreen:
                if (renderer instanceof VulkanRenderer) {
                    ((VulkanRenderer) renderer).toggleFullscreen();
                } else if (renderer instanceof GLRenderer) {
                    ((GLRenderer) renderer).toggleFullscreen();
                }
                closeXServerMenu();
                touchpadView.toggleFullscreen();
                // Авто-включение резкости при полноэкранном режиме убрано:
                // эффекты управляются только из меню эффектов.
                break;

            case R.id.main_menu_pause:
                if (isPaused) {
                    ProcessHelper.resumeAllWineProcesses();
                } else {
                    ProcessHelper.pauseAllWineProcesses();
                }
                isPaused = !isPaused;
                closeXServerMenu();
                break;

            case R.id.main_menu_pip_mode:
                enterPictureInPictureMode();
                closeXServerMenu();
                break;

            case R.id.main_menu_task_manager:
                new TaskManagerDialog(this).show();
                closeXServerMenu();
                break;

            case R.id.main_menu_screen_effects:
                ScreenEffectDialog screenEffectDialog = new ScreenEffectDialog(this);
                screenEffectDialog.setOnConfirmCallback(() -> {});
                screenEffectDialog.show();
                closeXServerMenu();
                break;

            case R.id.main_menu_logs:
                debugDialog.show();
                closeXServerMenu();
                break;

            case R.id.main_menu_terminal:
                openTerminal();
                break;

            case R.id.main_menu_fps_counter:
                showFpsCounterDialog();
                closeXServerMenu();
                break;

            case R.id.main_menu_frame_generation:
                showFrameGenerationDialog();
                closeXServerMenu();
                break;

            case R.id.main_menu_winetricks:
                if (winetricksFloatingView == null) {
                    FrameLayout frameLayout = findViewById(R.id.FLXServerDisplay);
                    winetricksFloatingView = new WinetricksFloatingView(this);
                    winetricksFloatingView.setWinetricksListener(new WinetricksFloatingView.WinetricksListener() {
                        @Override
                        public void onWinetricksStableClick(String verb, TextView outputView) {
                            if (!verb.isEmpty()) {
                                runWinetricksWithVerb(container, contentsManager, verb, outputView);
                            } else {
                                Toast.makeText(XServerDisplayActivity.this, "Please enter a Winetricks verb", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onWinetricksLatestClick(String verb, TextView outputView) {
                            if (!verb.isEmpty()) {
                                runWinetricksLatestWithVerb(container, contentsManager, verb, outputView);
                            } else {
                                Toast.makeText(XServerDisplayActivity.this, "Please enter a Winetricks verb", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onOpenWinetricksFolder(TextView outputView) {
                            runWinetricksFolder(container, contentsManager, outputView);
                        }

                        @Override
                        public void onToggleTransparency(View floatingView) {
                            if (floatingView.getAlpha() < 1.0f) {
                                floatingView.setAlpha(1.0f);
                            } else {
                                floatingView.setAlpha(0.5f);
                            }
                        }

                        @Override
                        public void onRestartWineserverClick(TextView outputView) {
                            try {
                                environment.setWinetricksRunning(true);
                                if (bionicLauncher != null) {
                                    bionicLauncher.restartWineServer();
                                } else {
                                    runOnUiThread(() -> {
                                        outputView.append("No valid launcher found; cannot restart Wineserver.\n");
                                    });
                                    return;
                                }
                                setupXEnvironment();
                                runOnUiThread(() -> {
                                    outputView.append("Wineserver restarted.\n");
                                });
                            } catch (Exception e) {
                                Log.e("XServerDisplayActivity", "Failed to restart wineserver", e);
                            }
                            environment.setWinetricksRunning(false);
                        }
                    });
                    frameLayout.addView(winetricksFloatingView);
                } else {
                    winetricksFloatingView.setVisibility(View.VISIBLE);
                }
                closeXServerMenu();
                break;

            case R.id.main_menu_exit:
                exit();
                break;
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        handleXServerMenuAction(item.getItemId());
        return true;
    }



    private void openTerminal() {

        Intent intent = new Intent(this, TerminalActivity.class);

        startActivity(intent);

    }



    private void showFrameGenerationDialog() {
        if (container == null) {
            Toast.makeText(this, "Container is not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Engine: the in-house chain runs without Lossless.dll; LSFG does not.
        final String[] engineValues = {"cronyfg", "lsfg-native"};
        final String[] engineLabels = {"CronyFG", "LSFG"};
        final String[] selectedEngine = {container.isCronyfg() || !container.isLsfgNative() ? "cronyfg" : "lsfg-native"};

        final int[] mults = {0, 2, 3, 4};
        final String[] labels = {"Off", "2x", "3x", "4x"};
        // Live armed state (Off shows Off); the saved preset is kept separately
        // and is never overwritten with 0, so re-enabling is one tap.
        int curMult = getLastFgMult();
        int checked = 0;
        for (int i = 0; i < mults.length; i++) if (mults[i] == curMult) checked = i;

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        // Секции переключаются по выбранному движку: у CronyFG — пресеты качества
        // вместо крутилки, у LSFG — прежний ползунок flowScale.
        final Runnable[] engineSync = new Runnable[1];

        android.widget.TextView engineLabel = new android.widget.TextView(this);
        engineLabel.setText("Engine");
        layout.addView(engineLabel);
        android.widget.RadioGroup engineGroup = new android.widget.RadioGroup(this);
        engineGroup.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        for (int i = 0; i < engineValues.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(engineLabels[i]);
            rb.setChecked(engineValues[i].equals(selectedEngine[0]));
            final String value = engineValues[i];
            rb.setOnClickListener(v -> {
                selectedEngine[0] = value;
                if (engineSync[0] != null) engineSync[0].run();
            });
            engineGroup.addView(rb);
        }
        layout.addView(engineGroup);

        android.widget.TextView status = new android.widget.TextView(this);
        status.setText(getFgStatusText());
        layout.addView(status);

        final int[] selected = {checked};
        final int[] selectedTarget = {0};
        final java.util.ArrayList<android.widget.RadioButton> targetRbs = new java.util.ArrayList<>();
        final Runnable selectTarget = () -> {
            for (int j = 0; j < targetRbs.size(); j++) targetRbs.get(j).setChecked(j == selectedTarget[0]);
        };
        for (int i = 0; i < mults.length; i++) {
            final int idx = i;
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(labels[i]);
            rb.setChecked(i == checked);
            rb.setOnClickListener(v -> {
                selected[0] = idx;
                for (int j = 0; j < layout.getChildCount(); j++) {
                    android.view.View ch = layout.getChildAt(j);
                    if (ch instanceof android.widget.RadioButton) {
                        ((android.widget.RadioButton) ch).setChecked(layout.getChildAt(j) == v);
                    }
                }
                // Picking a multiplier returns to Fixed mode.
                selectedTarget[0] = 0;
                selectTarget.run();
            });
            layout.addView(rb);
        }

        // Adaptive output-FPS target (WinNative parity): 0 = fixed multiplier.
        // Both rows stay visible; picking a multiplier resets target to Fixed.
        android.widget.TextView targetLabel = new android.widget.TextView(this);
        targetLabel.setText(R.string.lsfg_target);
        layout.addView(targetLabel);
        final int[] targets = {0, 60, 90, 120, 144, 165};
        final String[] targetLabels = {"Fixed", "60 fps", "90 fps", "120 fps", "144 fps", "165 fps"};
        int curTarget = container.getFrameGenTargetRate();
        for (int i = 0; i < targets.length; i++) if (targets[i] == curTarget) selectedTarget[0] = i;
        final android.widget.LinearLayout targetGroup = new android.widget.LinearLayout(this);
        targetGroup.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        for (int i = 0; i < targets.length; i++) {
            final int idx = i;
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(targetLabels[i]);
            rb.setChecked(i == selectedTarget[0]);
            rb.setOnClickListener(v -> {
                selectedTarget[0] = idx;
                selectTarget.run();
            });
            targetRbs.add(rb);
            targetGroup.addView(rb);
        }
        layout.addView(targetGroup);
        android.widget.TextView targetNote = new android.widget.TextView(this);
        targetNote.setText(R.string.lsfg_target_note);
        layout.addView(targetNote);

        // --- LSFG: ползунок flowScale (только для LSFG) ---
        android.widget.LinearLayout flowSection = new android.widget.LinearLayout(this);
        flowSection.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.TextView flowLabel = new android.widget.TextView(this);
        final float[] flow = {container.getFrameGenFlowScale()};
        flowLabel.setText(getString(R.string.lsfg_flow_scale) + ": " + String.format(java.util.Locale.US, "%.2f", flow[0]));
        flowSection.addView(flowLabel);
        android.widget.SeekBar flowBar = new android.widget.SeekBar(this);
        flowBar.setMax(75);
        flowBar.setProgress(Math.round((flow[0] - 0.25f) * 100));
        flowBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar b, int p, boolean fromUser) {
                flow[0] = 0.25f + p / 100f;
                flowLabel.setText(getString(R.string.lsfg_flow_scale) + ": " + String.format(java.util.Locale.US, "%.2f", flow[0]));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar b) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar b) {}
        });
        flowSection.addView(flowBar);
        layout.addView(flowSection);

        // --- CronyFG: пресеты качества (только для CronyFG) ---
        android.widget.RadioGroup qualitySection = new android.widget.RadioGroup(this);
        qualitySection.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.widget.TextView qualityLabel = new android.widget.TextView(this);
        qualityLabel.setText("Quality");
        qualitySection.addView(qualityLabel);
        final String[] qualityLabels = {"Эконом", "Плавно", "Баланс", "Высокий", "Усиленный", "Максимальный"};
        final int[] selectedQuality = {container.getFrameGenQuality()};
        for (int i = 0; i < qualityLabels.length; i++) {
            final int idx = i;
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(i + 1);   // RadioGroup опознает соседей по уникальному id
            rb.setText(qualityLabels[i]);
            rb.setChecked(i == selectedQuality[0]);
            // RadioGroup сам снимает выделение с соседей — остаётся лишь запомнить индекс.
            rb.setOnClickListener(v -> selectedQuality[0] = idx);
            qualitySection.addView(rb);
        }
        layout.addView(qualitySection);

        engineSync[0] = () -> {
            boolean cronyfg = "cronyfg".equals(selectedEngine[0]);
            flowSection.setVisibility(cronyfg ? android.view.View.GONE : android.view.View.VISIBLE);
            qualitySection.setVisibility(cronyfg ? android.view.View.VISIBLE : android.view.View.GONE);
        };
        engineSync[0].run();

        new AlertDialog.Builder(this)
                .setTitle(R.string.lsfg_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int m = mults[selected[0]];
                    int target = targets[selectedTarget[0]];
                    if (m >= 2) container.setFrameGenMultiplier(m);
                    container.setFrameGenFlowScale(flow[0]);
                    container.setFrameGenQuality(selectedQuality[0]);
                    container.setFrameGenTargetRate(target);
                    container.setFrameGenEngine(m > 0 ? selectedEngine[0] : "off");
                    container.saveData();
                    prepareLsfgNative();
                    applyLsfgNative(m, flow[0], selectedQuality[0]);
                    Toast.makeText(this, getString(R.string.lsfg_applied, m > 0 ? m + "x" : "Off"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getFgStatusText() {
        com.winlator.cmod.renderer.HostRenderer r = xServerView != null ? xServerView.getRenderer() : null;
        if (r instanceof VulkanRenderer) {
            VulkanRenderer vr = (VulkanRenderer) r;
            int kind = (container != null && container.isCronyfg())
                    ? VulkanRenderer.FG_ENGINE_CRONYFG : VulkanRenderer.FG_ENGINE_LSFG;
            if (!vr.isFrameGenSupported(kind)) return vr.getFrameGenSupportReason(kind);
            float[] st = vr.getFrameGenStats();
            if (st != null && st[3] > 0f) {
                return String.format(java.util.Locale.US, "%.0f real -> %.0f shown", st[2], st[3]);
            }
            return vr.isFrameGenArmed() ? "Armed, warming up..." : getString(R.string.lsfg_description);
        }
        return getString(R.string.lsfg_not_supported);
    }

    private VulkanRenderer vulkanRendererOrNull() {
        com.winlator.cmod.renderer.HostRenderer r = xServerView != null ? xServerView.getRenderer() : null;
        return (r instanceof VulkanRenderer) ? (VulkanRenderer) r : null;
    }

    private float currentDisplayRefreshHz() {
        try {
            if (xServerView != null && xServerView.getDisplay() != null) {
                return xServerView.getDisplay().getRefreshRate();
            }
        } catch (Throwable ignored) {}
        return 60f;
    }

    /**
     * Build the SPIR-V cache from the user's Lossless.dll if needed.
     * Slow on first run (DXBC translated on device) — off the main thread.
     * Every launch starts disarmed; the user arms from the in-game menu.
     * Public: the Compose FG dialog calls it alongside apply (same as legacy).
     */
    public void prepareLsfgNative() {
        if (container == null || !container.isLsfgNative()) return;
        if (!com.winlator.cmod.core.LsfgNative.isDllAvailable(this)) {
            Log.w("XServerDisplayActivity", "LSFG Native selected but no Lossless.dll imported - leaving frame gen off");
            return;
        }
        final float flow = container.getFrameGenFlowScale();
        new Thread(() -> {
            final int status = com.winlator.cmod.core.LsfgNative.ensureCache(
                    XServerDisplayActivity.this, false);
            runOnUiThread(() -> {
                if (status != com.winlator.cmod.core.LsfgNative.STATUS_OK) {
                    Log.e("XServerDisplayActivity", "LSFG Native unavailable: "
                            + com.winlator.cmod.core.LsfgNative.explain(status));
                    return;
                }
                VulkanRenderer vkr = vulkanRendererOrNull();
                if (vkr != null) {
                    vkr.setLsfgCachePath(
                            com.winlator.cmod.core.LsfgNative.cacheFile(XServerDisplayActivity.this).getAbsolutePath());
                }
            });
        }, "lsfg-native-cache").start();
    }

    /** Point the renderer at the cache and arm it at `multiplier` (0 = off). */
    public void applyLsfgNative(int multiplier, float flowScale) {
        // Пресет качества берём из контейнера (для CronyFG); для LSFG не значим.
        applyLsfgNative(multiplier, flowScale,
                container != null ? container.getFrameGenQuality() : 2);
    }

    public void applyLsfgNative(int multiplier, float flowScale, int quality) {
        VulkanRenderer vkr = vulkanRendererOrNull();
        if (vkr == null) {
            Log.w("XServerDisplayActivity", "applyLsfgNative(" + multiplier + ") ignored - no Vulkan renderer");
            lastFgMult = 0;
            return;
        }
        Log.i("XServerDisplayActivity", "applyLsfgNative: multiplier=" + multiplier
                + " flow=" + flowScale + " quality=" + quality + " refresh=" + currentDisplayRefreshHz());
        final boolean cronyfg = container != null && container.isCronyfg();
        if (multiplier >= 2 && !cronyfg) {
            java.io.File cache = com.winlator.cmod.core.LsfgNative.cacheFile(this);
            if (cache == null || !cache.isFile()) {
                android.widget.Toast.makeText(this, R.string.lsfg_building_cache, android.widget.Toast.LENGTH_LONG).show();
            }
        }
        vkr.setFrameGenEngine(cronyfg ? VulkanRenderer.FG_ENGINE_CRONYFG
                                      : VulkanRenderer.FG_ENGINE_LSFG);
        if (!cronyfg) {
            java.io.File cache = com.winlator.cmod.core.LsfgNative.cacheFile(this);
            if (cache != null) vkr.setLsfgCachePath(cache.getAbsolutePath());
        }
        vkr.setFrameGenTuning(flowScale, currentDisplayRefreshHz(), quality);
        vkr.setFrameGenTargetRate(container != null ? container.getFrameGenTargetRate() : 0);
        vkr.setFrameGenArmed(multiplier >= 2, multiplier);
        lastFgMult = multiplier >= 2 ? multiplier : 0;
        applyEffectivePresentMode();
        if (xServerView != null) {
            xServerView.setDisplayFrameRate(multiplier >= 2 ? currentDisplayRefreshHz() : 0f, 0);
        }
        if (multiplier >= 2) startLsfgStatsReadout(); else stopLsfgStatsReadout();
    }

    /** FIFO while multiplying (mailbox would collapse the burst), else the user's choice. */
    private void applyEffectivePresentMode() {
        VulkanRenderer vkr = vulkanRendererOrNull();
        if (vkr == null) return;
        boolean armed = vkr.isFrameGenArmed();
        int mode = 2;
        if (!armed && graphicsDriverConfig != null) {
            String pm = graphicsDriverConfig.get("presentMode");
            if (pm != null) {
                String n = pm.trim().toLowerCase();
                if (n.equals("mailbox")) mode = 1;
                else if (n.equals("immediate")) mode = 0;
            }
        }
        vkr.setVkPresentMode(mode);
    }

    private android.os.Handler lsfgStatsHandler;
    private Runnable lsfgStatsTick;
    private volatile String fgReadout = "";
    // Live armed multiplier (0 = off). Dialogs show THIS, not the saved
    // container preset: launch always starts disarmed.
    private volatile int lastFgMult = 0;

    /** Latest readout line for the in-game frame-gen menu (polled by Compose). */
    public String getFgReadout() { return fgReadout; }

    /** Live armed multiplier for menus (0 = currently off). */
    public int getLastFgMult() { return lastFgMult; }

    private void startLsfgStatsReadout() {
        if (lsfgStatsHandler != null) return;
        lsfgStatsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        lsfgStatsTick = new Runnable() {
            @Override public void run() {
                VulkanRenderer vkr = vulkanRendererOrNull();
                float[] st = (vkr != null) ? vkr.getFrameGenStats() : null;
                if (st != null && st.length >= 5) {
                    final int trusted = (int) st[1];
                    final float realFps = st[2], shownFps = st[3];
                    String text;
                    if (shownFps <= 0f) {
                        text = "measuring...";
                    } else {
                        text = String.format(java.util.Locale.US,
                                "%.0f real -> %.0f shown (%dx", realFps, shownFps, trusted + 1);
                        if (st[4] >= 3f) text += ", throttling";
                        text += ")";
                        if (st.length >= 6 && st[5] > 0f)
                            text += String.format(java.util.Locale.US, "  %.1f ms/frame GPU", st[5]);
                        // CronyFG: the effective flow short side, so the Flow
                        // Scale slider's real effect is visible in the menu.
                        if (st.length >= 7 && st[6] > 0f)
                            text += String.format(java.util.Locale.US, "  flow %dpx", (int) st[6]);
                    }
                    fgReadout = text;
                    // HUD shows the same engine numbers as the FG menu.
                    if (frameRating != null) frameRating.setFrameGenFps(realFps, shownFps);
                }
                if (lsfgStatsHandler != null) lsfgStatsHandler.postDelayed(this, 1000);
            }
        };
        lsfgStatsHandler.postDelayed(lsfgStatsTick, 1000);
    }

    private void stopLsfgStatsReadout() {
        if (frameRating != null) frameRating.setFrameGenFps(0f, 0f);
        fgReadout = "";
        if (lsfgStatsHandler != null && lsfgStatsTick != null)
            lsfgStatsHandler.removeCallbacks(lsfgStatsTick);
        lsfgStatsHandler = null;
        lsfgStatsTick = null;
    }


    

    /**

     * Р СџР С•Р С”Р В°Р В·РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ Р Т‘Р С‘Р В°Р В»Р С•Р С– Р Р…Р В°РЎРѓРЎвЂљРЎР‚Р С•Р ВµР С” FPS РЎРѓРЎвЂЎР ВµРЎвЂљРЎвЂЎР С‘Р С”Р В°

     */

    private void showFpsCounterDialog() {

        android.util.Log.d("FpsCounter", "Opening FPS Counter dialog");

        FpsCounterDialog dialog = new FpsCounterDialog(this);

        dialog.setOnConfigChangedListener(() -> {

            android.util.Log.d("FpsCounter", "FPS Counter config changed");

            // Р С›Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµР С Р Р†Р С‘Р Т‘Р С‘Р СР С•РЎРѓРЎвЂљРЎРЉ FPS РЎРѓРЎвЂЎР ВµРЎвЂљРЎвЂЎР С‘Р С”Р В°

            updateFpsCounterVisibility();

            

            // Р вЂўРЎРѓР В»Р С‘ FPS РЎРѓРЎвЂЎР ВµРЎвЂљРЎвЂЎР С‘Р С” Р Р†Р С‘Р Т‘Р С‘Р С, Р С•Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµР С Р Р†Р С‘Р Т‘Р С‘Р СР С•РЎРѓРЎвЂљРЎРЉ Р СР С•Р Т‘РЎС“Р В»Р ВµР в„–, Р С•РЎР‚Р С‘Р ВµР Р…РЎвЂљР В°РЎвЂ Р С‘РЎР‹, Р СР В°РЎРѓРЎв‚¬РЎвЂљР В°Р В± Р С‘ РЎР‚Р В°Р В·Р СР ВµРЎР‚ РЎвЂљР ВµР С”РЎРѓРЎвЂљР В°

            if (frameRating != null) {

                frameRating.updateModuleVisibility();

                frameRating.updateOrientation();

                frameRating.updateScaleAndTextSize();

            }

            applyFpsLimitToRenderer();

        });

        dialog.show();

    }

    /**
     * Р вЂ™РЎвЂ№Р В·РЎвЂ№Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р С‘Р В· Compose-Р Т‘Р С‘Р В°Р В»Р С•Р С–Р В° FPS РЎРѓРЎвЂЎРЎвЂРЎвЂљРЎвЂЎР С‘Р С”Р В° Р С—Р С•РЎРѓР В»Р Вµ РЎРѓР С•РЎвЂ¦РЎР‚Р В°Р Р…Р ВµР Р…Р С‘РЎРЏ Р Р…Р В°РЎРѓРЎвЂљРЎР‚Р С•Р ВµР С”.
     * Р С›Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµРЎвЂљ Р Р†Р С‘Р Т‘Р С‘Р СР С•РЎРѓРЎвЂљРЎРЉ, Р СР С•Р Т‘РЎС“Р В»Р С‘, Р С•РЎР‚Р С‘Р ВµР Р…РЎвЂљР В°РЎвЂ Р С‘РЎР‹ Р С‘ Р СР В°РЎРѓРЎв‚¬РЎвЂљР В°Р В± Р С•Р Р†Р ВµРЎР‚Р В»Р ВµРЎРЏ.
     */
    public void onFpsCounterConfigChangedFromCompose() {
        android.util.Log.d("FpsCounter", "FPS Counter config changed from Compose dialog");
        updateFpsCounterVisibility();
        if (frameRating != null) {
            frameRating.updateModuleVisibility();
            frameRating.updateOrientation();
            frameRating.updateScaleAndTextSize();
        }
        applyFpsLimitToRenderer();
    }

    /**
     * Р СџРЎР‚Р С‘Р СР ВµР Р…РЎРЏР ВµРЎвЂљ Р В»Р С‘Р СР С‘РЎвЂљ FPS Р С” РЎР‚Р ВµР Р…Р Т‘Р ВµРЎР‚Р ВµРЎР‚РЎС“: pacer Р Р† PresentExtension РЎвЂљР С•РЎР‚Р СР С•Р В·Р С‘РЎвЂљ
     * Р С‘Р С–РЎР‚РЎС“ РЎвЂЎР ВµРЎР‚Р ВµР В· Р В·Р В°Р Т‘Р ВµРЎР‚Р В¶Р С”РЎС“ Р С•РЎРѓР Р†Р С•Р В±Р С•Р В¶Р Т‘Р ВµР Р…Р С‘РЎРЏ Р В±РЎС“РЎвЂћР ВµРЎР‚Р В° (PresentIdleNotify).
     */
    private void applyFpsLimitToRenderer() {
        if (xServerView != null && xServerView.getRenderer() != null) {
            int fpsLimit = fpsCounterConfig.getFpsLimit();
            xServerView.getRenderer().setFpsLimit(fpsLimit);
            android.util.Log.d("FpsCounter", "FPS limit applied to renderer: " + fpsLimit);
        }
    }



    /**

     * Р С›Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµРЎвЂљ Р Р†Р С‘Р Т‘Р С‘Р СР С•РЎРѓРЎвЂљРЎРЉ FPS РЎРѓРЎвЂЎР ВµРЎвЂљРЎвЂЎР С‘Р С”Р В° Р Р…Р В° Р С•РЎРѓР Р…Р С•Р Р†Р Вµ Р Р…Р В°РЎРѓРЎвЂљРЎР‚Р С•Р ВµР С”

     */

    private void updateFpsCounterVisibility() {

        boolean enabled = fpsCounterConfig.isEnabled();

        android.util.Log.d("FpsCounter", "FPS Counter enabled: " + enabled);



        runOnUiThread(() -> {

            if (!enabled) {

                removeFrameRatingView();

                return;

            }



            ensureFrameRatingView();

            if (frameRating == null) return;

            syncFrameRatingWithExistingWindows();



            boolean shouldShow = frameRatingWindowId != -1;

            if (shouldShow) {

                frameRating.setVisibility(View.VISIBLE);

                frameRating.updateModuleVisibility();

                frameRating.updateOrientation();

            } else {

                frameRating.setVisibility(View.GONE);

            }

        });

    }



    @Override

    public void onWindowFocusChanged(boolean hasFocus) {

        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            if (preferences.getBoolean("cursor_lock", true)) requestPointerCaptureForCurrentView();
        }
        else if (touchpadView != null && touchpadView.hasPointerCapture()) {
            touchpadView.releasePointerCapture();
        }

    }



    private void requestPointerCaptureForCurrentView() {
        View captureView = (inputControlsView != null && inputControlsView.getVisibility() == View.VISIBLE)
                ? inputControlsView : touchpadView;
        if (captureView == null) return;

        captureView.setFocusableInTouchMode(true);
        if (!captureView.requestFocus()) return;
        if (captureView.hasPointerCapture()) return;

        try {
            captureView.requestPointerCapture();
        }
        catch (IllegalStateException ignored) {}
    }

    private void extractInputDLLs(boolean isLegacyInput) {

        String inputAsset = isLegacyInput ? "legacy_input_dlls.tzst" : "input_dlls.tzst";

        File wineFolder = new File(imageFs.getWinePath() + "/lib/wine/");



        Log.d("XServerDisplayActivity", "Extracting input dlls to " + wineFolder.getPath());



        boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, inputAsset, wineFolder);

        if (!success)

            Log.d("XServerDisplayActivity", "Failed to extract input dlls");

    }



    private void setupWineSystemFiles() {

        String appVersion = String.valueOf(AppUtils.getVersionCode(this));

        String imgVersion = String.valueOf(imageFs.getVersion());

        boolean containerDataChanged = false;



        if (!container.getExtra("appVersion").equals(appVersion) || !container.getExtra("imgVersion").equals(imgVersion)) {

            applyGeneralPatches(container);

            container.putExtra("appVersion", appVersion);

            container.putExtra("imgVersion", imgVersion);

            containerDataChanged = true;

        }



        String dxwrapper = this.dxwrapper;

        if (dxwrapper.equals("dxvk"))

            dxwrapper = "dxvk-"+dxwrapperConfig.get("version");

        else if (dxwrapper.equals("vkd3d"))

            dxwrapper = "vkd3d-"+dxwrapperConfig.get("vkd3dVersion");



        if (!dxwrapper.equals(container.getExtra("dxwrapper"))) {

            extractDXWrapperFiles(dxwrapper);

            container.putExtra("dxwrapper", dxwrapper);

            containerDataChanged = true;

        }



        String ddrawrapper = this.ddrawrapper;



        if (!ddrawrapper.equals(container.getExtra("ddrawrapper"))) {

            extractDDrawrapperFiles(ddrawrapper);

            container.putExtra("ddrawrapper", ddrawrapper);

            containerDataChanged = true;

        }



        if (ddrawrapper.equals("cnc-ddraw")) envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\windows\\syswow64\\ddraw.ini");



        String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents()) : container.getWinComponents();

        if (!wincomponents.equals(container.getExtra("wincomponents"))) {

            extractWinComponentFiles();

            container.putExtra("wincomponents", wincomponents);

            containerDataChanged = true;

        }



        String desktopTheme = container.getDesktopTheme();

        if (!(desktopTheme+","+xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {

            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);

            container.putExtra("desktopTheme", desktopTheme+","+xServer.screenInfo);

            containerDataChanged = true;

        }



        WineStartMenuCreator.create(this, container);

        WineUtils.createDosdevicesSymlinks(container);



        if (shortcut != null)

            startupSelection = shortcut.getExtra("startupSelection", String.valueOf(container.getStartupSelection()));

        else

            startupSelection = String.valueOf(container.getStartupSelection());



        if (!startupSelection.equals(container.getExtra("startupSelection"))) {

            WineUtils.changeServicesStatus(container, Byte.parseByte(startupSelection) != Container.STARTUP_SELECTION_NORMAL);

            container.putExtra("startupSelection", startupSelection);

            containerDataChanged = true;

        }



        boolean isLegacyInput = preferences.getBoolean("legacy_mode_enabled", false);

        extractInputDLLs(isLegacyInput);



        if (containerDataChanged) container.saveData();

    }



    private void setupXEnvironment() throws PackageManager.NameNotFoundException {

        // Optional: Example logging or debugging code

        // try {

        //     // Execute busybox whoami to log the UID

        //     Process whoamiProcess = new ProcessBuilder("/data/data/com.winlator/files/imagefs/usr/bin/busybox", "whoami").start();

        //     BufferedReader reader = new BufferedReader(new InputStreamReader(whoamiProcess.getInputStream()));

        //     String uid = reader.readLine();

        //     whoamiProcess.waitFor();

        //

        //     // Log the UID to understand what user is being used

        //     Log.d("Winetricks", "Current UID executing Wine: " + (uid != null ? uid : "unknown"));

        // } catch (Exception e) {

        //     Log.e("Winetricks", "Error executing busybox whoami: " + e.getMessage(), e);

        // }



        // ... (Other optional debugging code omitted) ...



        // Set environment variables

        envVars.put("LC_ALL", lc_all);

        envVars.put("MESA_DEBUG", "silent");

        envVars.put("MESA_NO_ERROR", "1");

        envVars.put("WINEPREFIX", imageFs.wineprefix);

        Log.d("Winetricks", "WINEPREFIX: " + imageFs.wineprefix);



        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);

        String wineDebugChannels = preferences.getString("wine_debug_channels", SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS);

        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty()

                ? "+" + wineDebugChannels.replace(",", ",+")

                : "-all"

        );



        // Clear any temporary directory

        String rootPath = imageFs.getRootDir().getPath();

        FileUtils.clear(imageFs.getTmpDir());





        // Create the appropriate launcher based on the container type

        GuestProgramLauncherComponent guestProgramLauncherComponent;



        bionicLauncher = new BionicProgramLauncherComponent(

                contentsManager,

                contentsManager.getProfileByEntryName(container.getWineVersion()),

                shortcut

        );

        guestProgramLauncherComponent = bionicLauncher;

        glibcLauncher = null; // We're not using glibc in this case



        // Additional container checks and environment configuration

        if (container != null) {

            if (Byte.parseByte(startupSelection) == Container.STARTUP_SELECTION_AGGRESSIVE) {

                winHandler.killProcess("services.exe");

            }

            bionicLauncher.setContainer(this.container);

            bionicLauncher.setWineInfo(this.wineInfo);

            boolean wow64Mode = container.isWoW64Mode();

            // Construct the guest executable command

            String guestExecutable = "wine explorer /desktop=shell," + xServer.screenInfo + " " + getWineStartCommand();

            // (Alternatively: "wine wineboot -u" or anything else you want)



            Log.d("Winetricks", "Guest executable: " + guestExecutable);



            // Set up the guest program parameters

            guestProgramLauncherComponent.setWoW64Mode(wow64Mode);

            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);



            // Merge in containerРІР‚в„ўs environment variables

            envVars.putAll(container.getEnvVars());

            

            // Merge in shortcut environment variables if present

            if (shortcut != null) envVars.putAll(shortcut.getExtra("envVars"));

            // GPU Name spoofing
            {
                String gpuConfig = graphicsDriverConfigRaw != null ? graphicsDriverConfigRaw : container.getGraphicsDriverConfig();
                if (gpuConfig != null) {
                    String gpuName = "";
                    String customName = null, customDevId = null, customVenId = null;
                    for (String pair : gpuConfig.split(";")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2) {
                            switch (kv[0]) {
                                case "gpuName": gpuName = kv[1]; break;
                                case "gpuCustomName": customName = kv[1]; break;
                                case "gpuCustomDeviceId": customDevId = kv[1]; break;
                                case "gpuCustomVendorId": customVenId = kv[1]; break;
                            }
                        }
                    }
                    if (!gpuName.isEmpty() && !gpuName.equals("Device")) {
                        if (gpuName.equals("Custom") && customName != null && !customName.isEmpty()) {
                            envVars.put("WRAPPER_DEVICE_NAME", customName);
                            if (customDevId != null && !customDevId.isEmpty())
                                envVars.put("WRAPPER_DEVICE_ID", customDevId);
                            if (customVenId != null && !customVenId.isEmpty())
                                envVars.put("WRAPPER_VENDOR_ID", customVenId);
                        } else {
                            envVars.put("WRAPPER_DEVICE_NAME", gpuName);
                            try {
                                String json = com.winlator.cmod.core.FileUtils.readString(XServerDisplayActivity.this, "gpu_cards.json");
                                org.json.JSONArray arr = new org.json.JSONArray(json);
                                for (int i = 0; i < arr.length(); i++) {
                                    org.json.JSONObject obj = arr.getJSONObject(i);
                                    if (gpuName.equals(obj.getString("name"))) {
                                        envVars.put("WRAPPER_DEVICE_ID", String.valueOf(obj.getInt("deviceID")));
                                        envVars.put("WRAPPER_VENDOR_ID", String.valueOf(obj.getInt("vendorID")));
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                Log.e("XServerDisplayActivity", "Failed to apply GPU name from gpu_cards.json: " + gpuName, e);
                            }
                        }
                    }
                }
            }

            // If WINEESYNC is not defined, default to "1"

            if (!envVars.has("WINEESYNC")) {

                envVars.put("WINEESYNC", "1");

            }



            // Bind any drive paths the container defines

            ArrayList<String> bindingPaths = new ArrayList<>();

            for (String[] drive : container.drivesIterator()) {

                bindingPaths.add(drive[1]);

            }

            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));



            // Box86/64 presets from container or shortcut

            guestProgramLauncherComponent.setBox64Preset(

                    shortcut != null

                            ? shortcut.getExtra("box64Preset", container.getBox64Preset())

                            : container.getBox64Preset()

            );



            guestProgramLauncherComponent.setFEXCorePreset(

                    shortcut != null

                            ? shortcut.getExtra("fexcorePreset", container.getFEXCorePreset())

                            : container.getFEXCorePreset()

            );

        }



        // Merge overrideEnvVars if present

        if (overrideEnvVars != null) {

            envVars.putAll(overrideEnvVars);

            overrideEnvVars.clear(); // Clear overrideEnvVars as per smali logic

        }



        // Create our overall XEnvironment with various components

        environment = new XEnvironment(this, imageFs);

        environment.addComponent(

                new SysVSharedMemoryComponent(

                        xServer,

                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)

                )

        );

        environment.addComponent(

                new XServerComponent(

                        xServer,

                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH)

                )

        );





        environment.addComponent(new NetworkInfoUpdateComponent());



        // Parse audio driver config

        KeyValueSet audioConfig = new KeyValueSet(container.getAudioDriverConfig());

        int performanceMode = audioConfig.getInt("performanceMode", 1);

        float volume = audioConfig.getFloat("volume", 1.0f);

        int latencyMillis = audioConfig.getInt("latencyMillis", 16);

        

        // Apply audio config to environment

        envVars.put("PULSE_LATENCY_MSEC", String.valueOf(latencyMillis));

        if (volume != 1.0f) {

            envVars.put("ALSA_VOLUME", String.valueOf((int)(volume * 100)));

        }



        // Audio driver logic

        if (audioDriver.equals("alsa")) {

            envVars.put("ANDROID_ALSA_SERVER", rootPath + UnixSocketConfig.ALSA_SERVER_PATH);

            envVars.put("ANDROID_ASERVER_USE_SHM", "true");

            environment.addComponent(

                    new ALSAServerComponent(

                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH)

                    )

            );

        } else if (audioDriver.equals("alsa-reflector")) {

            envVars.put("ANDROID_ALSA_SERVER", rootPath + UnixSocketConfig.ALSA_SERVER_PATH);

            envVars.put("ANDROID_ASERVER_USE_SHM", "true");

            envVars.put("ANDROID_ALSA_REFLECTOR", "1");

            

            ALSAServerComponent alsaComponent = new ALSAServerComponent(

                    UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH),

                    true // Enable reflector mode

            );

            

            // Set up audio device change callback for reflector mode

            setupAudioDeviceCallback(alsaComponent);

            

            environment.addComponent(alsaComponent);

        } else if (audioDriver.equals("pulseaudio")) {

            envVars.put("PULSE_SERVER", rootPath + UnixSocketConfig.PULSE_SERVER_PATH);

            environment.addComponent(

                    new PulseAudioComponent(

                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.PULSE_SERVER_PATH)

                    )

            );

        }



        // RC (box86_64rc) file handling

        RCManager manager = new RCManager(this);

        manager.loadRCFiles();

        int rcfileId = shortcut == null

                ? container.getRCFileId()

                : Integer.parseInt(shortcut.getExtra("rcfileId", String.valueOf(container.getRCFileId())));

        RCFile rcfile = manager.getRcfile(rcfileId);



        File file = new File(container.getRootDir(), ".box64rc");

        String str = rcfile == null ? "" : rcfile.generateBox86_64rc();

        FileUtils.writeString(file, str);



        // Let Box64 inside Wine see this config

        envVars.put("BOX64_RCFILE", file.getAbsolutePath());



        // Pass final envVars to the launcher

        guestProgramLauncherComponent.setEnvVars(envVars);

        guestProgramLauncherComponent.setTerminationCallback((status) -> exit());



        // Add the launcher to our environment

        environment.addComponent(guestProgramLauncherComponent);



        // If we need to auto-generate a wineprefix

        if (isGenerateWineprefix()) {

            generateWineprefix();

        }



        // Generate fexcore per app settings

        FEXCoreManager.createAppConfigFiles(this, container);



        // Start all environment components (XServer, Audio, etc.)

        environment.startEnvironmentComponents();



        // (Optionally) run Winetricks after setup, if you wish

        // runWinetricksAfterSetup();



        // Start the WinHandler

        winHandler.start();



        if (wineRequestHandler != null) wineRequestHandler.start();



        // Clear envVars if needed

        // envVars.clear();



        // Reset dxwrapper config

        dxwrapperConfig = null;



        // Workaround: on every container boot the GL surface ends up in a state

        // where rendering produces a black screen until the user manually

        // minimizes and restores the app. Force a surface recreate by

        // pausing+resuming the GLSurfaceView shortly after Wine starts.

        if (xServerView != null) {

            handler.postDelayed(() -> {

                if (xServerView != null && !isFinishing()) {

                    xServerView.onPause();

                    xServerView.onResume();

                    xServerView.requestRender();

                }

            }, 1000);

        }

    }







    private void createWineWrappers(Container container, ContentsManager contentsManager) {

        String wineBinPath;

        String wineLibPath;

        String box64Path = imageFs.getRootDir().getPath() + "/usr/local/bin/box64";

        String usrLocalBin = imageFs.getRootDir().getPath() + "/usr/local/bin";



        // Determine if the container is using a contents profile Wine version

        ContentProfile profile = contentsManager.getProfileByEntryName(container.getWineVersion());

        if (profile != null && profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE) {

            File profileInstallDir = contentsManager.getInstallDir(this, profile);

            wineBinPath = profileInstallDir.getPath() + "/" + profile.wineBinPath;

            wineLibPath = profileInstallDir.getPath() + "/" + profile.wineLibPath;

        } else {

            wineBinPath = imageFs.getWinePath() + "/bin";

            wineLibPath = imageFs.getWinePath() + "/lib/wine";

        }



        // Fetch stored environment variables

        Map<String, String> envVars = EnvironmentManager.getEnvVars();



        // Build environment export section dynamically

        StringBuilder dynamicEnvExports = new StringBuilder("#!" + imageFs.getRootDir() + "/usr/bin/dash\n");

        for (Map.Entry<String, String> entry : envVars.entrySet()) {

            dynamicEnvExports.append("export ").append(entry.getKey()).append("=\"")

                    .append(entry.getValue().replace("\"", "\\\"")).append("\"\n");

        }



        // Define the wine and wine64 wrappers to open explorer.exe with the desktop shell

        String wineExecContent = dynamicEnvExports.toString() +

                "exec \"" + box64Path + "\" \"" + wineBinPath + "/wine\" explorer.exe /desktop=shell," + xServer.screenInfo + " \"$@\"";

        createWrapperScript(usrLocalBin + "/wine", wineExecContent);

        createWrapperScript(usrLocalBin + "/wine64", wineExecContent);



        // Define the wineserver wrapper with a different exec command

        String wineserverContent = dynamicEnvExports.toString() +

                "exec \"" + box64Path + "\" \"" + wineBinPath + "/wineserver\" \"$@\"";

        createWrapperScript(usrLocalBin + "/wineserver", wineserverContent);

    }



    private void createWrapperScript(String path, String content) {

        File scriptFile = new File(path);

        FileUtils.writeString(scriptFile, content);

        scriptFile.setExecutable(true);

    }





    // Compose side-panel wrappers: same actions as WinetricksFloatingView listener
    public void composeRunWinetricksStable(String verb, TextView outputView) {
        if (verb == null) verb = "";
        if (!verb.isEmpty()) {
            runWinetricksWithVerb(container, contentsManager, verb, outputView);
        } else {
            Toast.makeText(this, "Please enter a Winetricks verb", Toast.LENGTH_SHORT).show();
        }
    }

    public void composeRunWinetricksFolder(TextView outputView) {
        runWinetricksFolder(container, contentsManager, outputView);
    }

    public com.winlator.cmod.contentdialog.DebugDialog getDebugDialog() {
        return debugDialog;
    }

    public void composeRestartWineserver(TextView outputView) {
        try {
            environment.setWinetricksRunning(true);
            if (bionicLauncher != null) {
                bionicLauncher.restartWineServer();
            } else {
                runOnUiThread(() -> {
                    outputView.append("No valid launcher found; cannot restart Wineserver.\n");
                });
                return;
            }
            setupXEnvironment();
            runOnUiThread(() -> {
                outputView.append("Wineserver restarted.\n");
            });
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Failed to restart wineserver", e);
        }
        environment.setWinetricksRunning(false);
    }

    private static final int MAX_LOG_LINES = 1000;

    private static final int BATCH_SIZE = 10;



    private void runWinetricksWithVerb(Container container,

                                       ContentsManager contentsManager,

                                       String verb,

                                       TextView outputView) {



        // 1. Tell the environment that Winetricks is about to run

        environment.setWinetricksRunning(true);



        // Example: create wrappers, etc.

        createWineWrappers(container, contentsManager);



        Map<String, String> envVars = EnvironmentManager.getEnvVars();

        String usrLocalBin = imageFs.getRootDir().getPath() + "/usr/local/bin";

        String wineBinPath = imageFs.getWinePath() + "/bin";

        String defaultPath = usrLocalBin + ":" + wineBinPath + ":" + imageFs.getRootDir().getPath() + "/usr/bin";

        envVars.put("PATH", defaultPath);



        File winetricksFile = new File(imageFs.getRootDir(), "/usr/bin/winetricks");

        if (!winetricksFile.exists()) {

            Log.e("Winetricks", "winetricks script not found at " + winetricksFile.getAbsolutePath());

            // IMPORTANT: re-enable the callback if we fail early

            environment.setWinetricksRunning(false);

            return;

        }

        winetricksFile.setExecutable(true);



        String[] command = {

                winetricksFile.getAbsolutePath(),

                "--force",

                verb

        };



        Executor executor = Executors.newSingleThreadExecutor();



        final Process[] process = {null};



        executor.execute(new Runnable() {

            @Override

            public void run() {

                try {

                    ProcessBuilder processBuilder = new ProcessBuilder(command);

                    processBuilder.directory(new File(imageFs.home_path));

                    Map<String, String> environmentVars = processBuilder.environment();

                    for (Map.Entry<String, String> entry : envVars.entrySet()) {

                        environmentVars.put(entry.getKey(), entry.getValue());

                    }

                    processBuilder.redirectErrorStream(true);

                    process[0] = processBuilder.start();

                    runOnUiThread(() -> outputView.setText(""));

                    BufferedReader reader = new BufferedReader(new InputStreamReader(process[0].getInputStream()));

                    String outputLine = null;

                    while (((outputLine = reader.readLine()) != null)) {

                        if (outputLine != null) {

                            String finalOutputLine = outputLine;

                            runOnUiThread(() -> outputView.append(finalOutputLine + "\n"));

                        }

                    }

                    int exitCode = process[0].waitFor();

                    runOnUiThread(() -> outputView.append("Winetricks exited with code " + exitCode + "\n"));



                } catch (Exception e) {

                    String msg = "Error executing winetricks: " + e.getMessage();

                    runOnUiThread(() -> outputView.setText(msg));

                } finally {

                    // 2. Once Winetricks finishes or fails, kill eventual stale processes

                    environment.setWinetricksRunning(false);

                }

            }

        });

    }



    private void runWinetricksLatestWithVerb(Container container,

                                             ContentsManager contentsManager,

                                             String verb,

                                             TextView outputView) {



        // 1. Mark Winetricks as running

        environment.setWinetricksRunning(true);



        // 2. Create wrappers, etc. (same as normal)

        createWineWrappers(container, contentsManager);



        Map<String, String> envVars = EnvironmentManager.getEnvVars();

        String usrLocalBin = imageFs.getRootDir().getPath() + "/usr/local/bin";

        String wineBinPath = imageFs.getWinePath() + "/bin";

        String defaultPath = usrLocalBin + ":" + wineBinPath + ":" + imageFs.getRootDir().getPath() + "/usr/bin";

        envVars.put("PATH", defaultPath);



        // 3. **Use winetricks.latest** instead of winetricks

        File winetricksFile = new File(imageFs.getRootDir(), "/usr/bin/winetricks.latest");

        if (!winetricksFile.exists()) {

            Log.e("WinetricksLatest", "winetricks.latest script not found at " + winetricksFile.getAbsolutePath());

            // Re-enable callback if we fail early

            environment.setWinetricksRunning(false);

            return;

        }

        winetricksFile.setExecutable(true);



        // 4. Build the command array for the script + verb

        String[] command = {

                winetricksFile.getAbsolutePath(),

                "--force",

                verb

        };



        new Thread(() -> {

            Process process = null;

            try {

                ProcessBuilder processBuilder = new ProcessBuilder(command);

                processBuilder.directory(new File(imageFs.home_path));



                Map<String, String> environmentVars = processBuilder.environment();

                for (Map.Entry<String, String> entry : envVars.entrySet()) {

                    environmentVars.put(entry.getKey(), entry.getValue());

                }



                process = processBuilder.start();

                process.getOutputStream().close();



                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));



                appendBufferedLog(reader, outputView, false);

                appendBufferedLog(errorReader, outputView, true);



                int exitCode = process.waitFor();

                final int finalExitCode = exitCode;

                runOnUiThread(() -> outputView.append("Winetricks Latest exited with code " + finalExitCode + "\n"));



            } catch (Exception e) {

                String msg = "Error executing winetricks.latest: " + e.getMessage();

                runOnUiThread(() -> outputView.setText(msg));

            } finally {

                // Re-enable normal callback

                environment.setWinetricksRunning(false);

            }

        }).start();

    }



    private void appendBufferedLog(BufferedReader reader, TextView outputView, boolean isError) throws IOException {

        ArrayDeque<String> logBuffer = new ArrayDeque<>(MAX_LOG_LINES);

        StringBuilder batchBuffer = new StringBuilder();

        String line;

        int batchCount = 0;



        while ((line = reader.readLine()) != null) {

            String finalLine = (isError ? "Error: " : "") + line;



            if (logBuffer.size() >= MAX_LOG_LINES) {

                logBuffer.pollFirst(); // Remove the oldest line

            }

            logBuffer.addLast(finalLine);



            batchBuffer.append(finalLine).append("\n");

            batchCount++;



            if (batchCount >= BATCH_SIZE) {

                String logContent = String.join("\n", logBuffer);

                runOnUiThread(() -> outputView.setText(logContent));

                batchBuffer.setLength(0); // Clear batch buffer

                batchCount = 0;

            }

        }



        // Final update if there are remaining lines

        if (batchBuffer.length() > 0) {

            String logContent = String.join("\n", logBuffer);

            runOnUiThread(() -> outputView.setText(logContent));

        }

    }



    private void runWinetricksFolder(Container container, ContentsManager contentsManager, TextView outputView) {

        // The path to where you'd like to store your dynamic script

        String scriptPath = imageFs.getRootDir() + "/usr/bin/winetricksfolder";



        // 1. Generate (or overwrite) the script

        createWinetricksFolderScript(container, contentsManager, scriptPath);



        // 2. Verify it got created

        File winetricksFolderFile = new File(scriptPath);

        if (!winetricksFolderFile.exists()) {

            Log.e("WinetricksFolder", "winetricksfolder script not found after creation.");

            outputView.setText("Error: winetricksfolder script not found.");

            return;

        }

        winetricksFolderFile.setExecutable(true);



        // 3. Execute the newly created script

        String[] command = { winetricksFolderFile.getAbsolutePath() };



        new Thread(() -> {

            try {

                ProcessBuilder processBuilder = new ProcessBuilder(command);

                Map<String, String> environment = processBuilder.environment();



                // Optionally set additional environment variables if needed

                // environment.put("HOME", imageFs.home_path);

                // environment.put("DISPLAY", ":0");

                // etc.



                // Start process

                Process process = processBuilder.start();

                process.getOutputStream().close();



                // Capture output

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                StringBuilder output = new StringBuilder();

                String line;

                while ((line = reader.readLine()) != null) {

                    output.append(line).append("\n");

                }

                while ((line = errorReader.readLine()) != null) {

                    output.append("Error: ").append(line).append("\n");

                }



                int exitCode = process.waitFor();

                output.append("winetricksfolder script exited with code ").append(exitCode).append("\n");



                runOnUiThread(() -> outputView.setText(output.toString()));



            } catch (Exception e) {

                runOnUiThread(() -> outputView.setText("Error executing winetricksfolder: " + e.getMessage()));

            }

        }).start();

    }





    private void createWinetricksFolderScript(

            Container container,

            ContentsManager contentsManager,

            String scriptPath

    ) {

        // 1. Figure out which Wine bin path to use based on the container/profile

        ContentProfile profile = contentsManager.getProfileByEntryName(container.getWineVersion());

        String wineBinPath;

        if (profile != null && profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE) {

            File profileInstallDir = contentsManager.getInstallDir(this, profile);

            wineBinPath = profileInstallDir.getPath() + "/" + profile.wineBinPath;

        } else {

            wineBinPath = imageFs.getWinePath() + "/bin";

        }



        // 2. Construct environment-variable exports similarly to createWineWrappers()

        //    (fetch the env vars from EnvironmentManager)

        Map<String, String> envVars = EnvironmentManager.getEnvVars();

        StringBuilder dynamicEnvExports = new StringBuilder("#!" + imageFs.getRootDir().getPath() + "/usr/bin/dash\n");



        for (Map.Entry<String, String> entry : envVars.entrySet()) {

            // Properly escape quotes

            String escapedValue = entry.getValue().replace("\"", "\\\"");

            dynamicEnvExports

                    .append("export ")

                    .append(entry.getKey())

                    .append("=\"")

                    .append(escapedValue)

                    .append("\"\n");

        }



        // 3. Add your final РІР‚СљexecРІР‚Сњ line

        //    For example, run wine explorer.exe /desktop=shell wfm ...

        //    Also note if you need box64 or notРІР‚вЂќdepends on your environment.

        String box64Path = imageFs.getRootDir().getPath() + "/usr/local/bin/box64";



        dynamicEnvExports.append("exec \"")

                .append(box64Path).append("\" \"")

                .append(wineBinPath).append("/wine\" ")

                .append("explorer.exe /desktop=shell wfm ")

                .append("\"" + imageFs.getRootDir().getPath() +  "/home/xuser/.cache/winetricks\"")

                .append("\n");



        // 4. Write the script to disk

        File scriptFile = new File(scriptPath);

        FileUtils.writeString(scriptFile, dynamicEnvExports.toString());

        scriptFile.setExecutable(true);

    }





    private void setupUI() {

        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);

        xServerRootView = rootView;

        String displayRenderer = container != null ? container.getDisplayRenderer() : "vulkan";

        xServerView = new XServerView(this, xServer);
        xServerView.initRenderer(displayRenderer);

        final com.winlator.cmod.renderer.HostRenderer renderer = xServerView.getRenderer();
        // Vulkan/ASurface/GL РІСЃРµ СЂРµР°Р»РёР·СѓСЋС‚ XServerRenderer вЂ” СЃС‚Р°РІРёРј РЅР°РїСЂСЏРјСѓСЋ,
        // С‡С‚РѕР±С‹ DrawableManager РјРѕРі РІС‹Р·РІР°С‚СЊ getXServerView().
        if (renderer instanceof XServerRenderer) {
            xServer.setRenderer((XServerRenderer) renderer);
        }

        renderer.setCursorVisible(false);

        applyFpsLimitToRenderer();

        // РџРµСЂРµРґР°С‘Рј РёРЅС„РѕСЂРјР°С†РёСЋ Рѕ РІС‹Р±СЂР°РЅРЅРѕРј РґСЂР°Р№РІРµСЂРµ РІ VulkanRenderer, С‡С‚РѕР±С‹
        // СЂРµРЅРґРµСЂРµСЂ Р·Р°РіСЂСѓР·РёР» РєР°СЃС‚РѕРјРЅС‹Р№ Vulkan-РґСЂР°Р№РІРµСЂ С‡РµСЂРµР· adrenotools.
        // Р”СЂР°Р№РІРµСЂ РѕРїСЂРµРґРµР»СЏРµС‚СЃСЏ РёР· graphicsDriverConfig ("version") РєРѕРЅС‚РµР№РЅРµСЂР°/shortcut.
        if (renderer instanceof VulkanRenderer) {
            try {
                String rendererDriverId = "";
                if (graphicsDriverConfig != null) {
                    rendererDriverId = graphicsDriverConfig.get("version");
                }
                if (shortcut != null) {
                    String shortcutDriver = shortcut.getExtra("wrapperGraphicsDriverVersion",
                            graphicsDriverConfig != null ? graphicsDriverConfig.get("version") : "");
                    if (shortcutDriver != null && !shortcutDriver.isEmpty()) {
                        rendererDriverId = shortcutDriver;
                    }
                }
                if (rendererDriverId != null && !rendererDriverId.isEmpty()
                        && !rendererDriverId.equalsIgnoreCase("System")
                        && !rendererDriverId.contains(DefaultVersion.WRAPPER)) {
                    AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
                    String libraryName = adrenotoolsManager.getLibraryName(rendererDriverId);
                    String nativeLibDir = AppUtils.getNativeLibDir(this);
                    String driverPath = getFilesDir().getAbsolutePath()
                            + "/imagefs/contents/adrenotools/" + rendererDriverId + "/";
                    if (libraryName != null && !libraryName.isEmpty()) {
                        ((VulkanRenderer) renderer).setDriverInfo(driverPath, libraryName, nativeLibDir);
                        Log.d("XServerDisplayActivity", "setDriverInfo for renderer: driverId=" + rendererDriverId
                                + " lib=" + libraryName + " path=" + driverPath);
                    }
                }
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Failed to set driver info for renderer", e);
            }
        }

        if (shortcut != null) {

            if (shortcut.getExtra("forceFullscreen", "0").equals("1") && renderer instanceof VulkanRenderer) {
                ((VulkanRenderer) renderer).toggleFullscreen();
            }

            if (renderer != null) {
                renderer.setUnviewableWMClasses("explorer.exe");
            }

        }



        if (renderer instanceof ASurfaceRenderer) {
            ((ASurfaceRenderer) renderer).setSfCompatMode(container != null && container.getSfCompatMode());
        }

        // Native LSFG: build the shader cache in background (starts disarmed).
        prepareLsfgNative();

        rootView.addView(xServerView);



        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);

        boolean capturePointer = preferences.getBoolean("cursor_lock", true);

        touchpadView = new TouchpadView(this, xServer, timeoutHandler, hideControlsRunnable);

        touchpadView.setCapturePointerOnExternalMouse(capturePointer);

        touchpadView.setSensitivity(globalCursorSpeed);

        touchpadView.setFourFingersTapCallback(() -> {
            if (xserverMenuController != null) {
                runOnUiThread(() -> xserverMenuController.showMenu());
            }
        });

        rootView.addView(touchpadView);



        inputControlsView = new InputControlsView(this, timeoutHandler, hideControlsRunnable);

        inputControlsView.setOverlayOpacity(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));

        inputControlsView.setTouchpadView(touchpadView);

        if (capturePointer) {
            inputControlsView.setOnCapturedPointerListener((capturedView, capturedEvent) ->
                    touchpadView.isEnabled() && touchpadView.onCapturedPointer(capturedView, capturedEvent));
        }

        inputControlsView.setXServer(xServer);

        inputControlsView.setVisibility(View.GONE);

        rootView.addView(inputControlsView);





        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);

        if (isTimeoutEnabled && !inputControlsView.isShowTouchscreenControls()) {

            startTouchscreenTimeout();

        } else {

            timeoutHandler.removeCallbacks(hideControlsRunnable);

        }



        // Р ВР Р…Р С‘РЎвЂ Р С‘Р В°Р В»Р С‘Р В·Р С‘РЎР‚РЎС“Р ВµР С FPS РЎРѓРЎвЂЎР ВµРЎвЂљРЎвЂЎР С‘Р С” РЎвЂљР С•Р В»РЎРЉР С”Р С• Р ВµРЎРѓР В»Р С‘ Р С”Р С•Р Р…РЎвЂљР ВµР в„–Р Р…Р ВµРЎР‚ Р С—Р С•Р Т‘Р Т‘Р ВµРЎР‚Р В¶Р С‘Р Р†Р В°Р ВµРЎвЂљ FPS

        if (container != null) {

            frameRating = new FrameRating(this, container);

            frameRating.setVisibility(View.GONE);  // Р ВР В·Р Р…Р В°РЎвЂЎР В°Р В»РЎРЉР Р…Р С• РЎРѓР С”РЎР‚РЎвЂ№РЎвЂљ

            if (xServerView != null) xServerView.getRenderer().setFrameRating(frameRating);

            FrameLayout.LayoutParams fpsLp = new FrameLayout.LayoutParams(

                    FrameLayout.LayoutParams.WRAP_CONTENT,

                    FrameLayout.LayoutParams.WRAP_CONTENT

            );

            fpsLp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;

            rootView.addView(frameRating, fpsLp);

            

            // Р С›Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµР С Р Р†Р С‘Р Т‘Р С‘Р СР С•РЎРѓРЎвЂљРЎРЉ Р Р…Р В° Р С•РЎРѓР Р…Р С•Р Р†Р Вµ Р Р…Р В°РЎРѓРЎвЂљРЎР‚Р С•Р ВµР С”

            updateFpsCounterVisibility();

        }



        // Р вЂ™Р С•РЎРѓРЎРѓРЎвЂљР В°Р Р…Р В°Р Р†Р В»Р С‘Р Р†Р В°Р ВµР С Р С—Р В°Р Р…Р ВµР В»РЎРЉ Р В±РЎвЂ№РЎРѓРЎвЂљРЎР‚Р С•Р С–Р С• Р Т‘Р С•РЎРѓРЎвЂљРЎС“Р С—Р В° Р ВµРЎРѓР В»Р С‘ Р В±РЎвЂ№Р В»Р В° Р Р†Р С”Р В»РЎР‹РЎвЂЎР ВµР Р…Р В°

        boolean quickAccessEnabled = preferences.getBoolean("quick_access_panel_enabled", false);

        if (quickAccessEnabled) {

            updateQuickAccessPanel(true);

        }



        // Get the fullscreen stretched extra from the shortcut if available

        String shortcutFullscreenStretched = shortcut != null ? shortcut.getExtra("fullscreenStretched") : null;



        // Proceed based on container and shortcut settings

        boolean shouldStretch = false;



        if (shortcut != null && shortcutFullscreenStretched != null) {

            // Shortcut exists and has a valid setting

            shouldStretch = shortcutFullscreenStretched.equals("1");

        } else if (container != null && container.isFullscreenStretched()) {

            // No shortcut or shortcut doesn't override, use the container's setting

            shouldStretch = true;

        }



        // Растяжение экрана: ползунок «Растяжение экрана» из меню эффектов (pref stretch_amount)
        // главнее фиксированного fullscreenStretched; pref < 0 — ползунок ещё не трогали,
        // работает старое поведение контейнера/ярлыка (полное растяжение).

        float stretchToApply = -1f;

        float prefStretch = preferences.getFloat("stretch_amount", -1f);

        if (prefStretch >= 0f) {

            stretchToApply = prefStretch;

        } else if (shouldStretch) {

            stretchToApply = 1f;

        }

        if (stretchToApply >= 0f) {

            applyScreenStretch(stretchToApply);

            // Авто-включение резкости при растяжении убрано: эффекты — только из меню эффектов.

        }



        if (shortcut != null) {

            String controlsProfile = shortcut.getExtra("controlsProfile");

            if (!controlsProfile.isEmpty()) {

                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));

                if (profile != null) showInputControls(profile);

            }



            String simTouchScreen = shortcut.getExtra("simTouchScreen");

            touchpadView.setSimTouchScreen(simTouchScreen.equals("1"));

        }



        xServer.setRelativeMouseMovement(isRelativeMouseMovement);



        AppUtils.observeSoftKeyboardVisibility(drawerLayout, visible -> renderer.setScreenOffsetYRelativeToCursor(visible));

    }







    private ActivityResultLauncher<Intent> controlsEitorActivityResultLauncher = registerForActivityResult(

            new ActivityResultContracts.StartActivityForResult(),

            result -> {

                if (editInputControlsCallback != null) {

                    editInputControlsCallback.run();

                    editInputControlsCallback = null;

                }

            }

    );



    private String parseShortcutNameFromDesktopFile(File desktopFile) {

        String shortcutName = "";

        if (desktopFile.exists()) {

            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {

                String line;

                while ((line = reader.readLine()) != null) {

                    if (line.startsWith("Name=")) {

                        shortcutName = line.split("=")[1].trim();

                        break;

                    }

                }

            } catch (IOException e) {

                Log.e("XServerDisplayActivity", "Error reading shortcut name from .desktop file", e);

            }

        }

        return shortcutName;

    }



    private void setTextColorForDialog(ViewGroup viewGroup, int color) {

        for (int i = 0; i < viewGroup.getChildCount(); i++) {

            View child = viewGroup.getChildAt(i);

            if (child instanceof ViewGroup) {

                // If the child is a ViewGroup, recursively apply the color

                setTextColorForDialog((ViewGroup) child, color);

            } else if (child instanceof TextView) {

                // If the child is a TextView, set its text color

                ((TextView) child).setTextColor(color);

            }

        }

    }



    private void showInputControlsDialog() {

        final ContentDialog dialog = new ContentDialog(this, R.layout.input_controls_dialog);

        dialog.setTitle(R.string.input_controls);

        dialog.setIcon(R.drawable.icon_input_controls);



        final Spinner sProfile = dialog.findViewById(R.id.SProfile);



        dialog.getWindow().setBackgroundDrawableResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        sProfile.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);



        // Set text color for all TextViews in the dialog to white or black based on dark mode

        int textColor = ContextCompat.getColor(this, isDarkMode ? R.color.white : R.color.black);

        ViewGroup dialogViewGroup = (ViewGroup) dialog.getWindow().getDecorView().findViewById(android.R.id.content);

        setTextColorForDialog(dialogViewGroup, textColor);



        Runnable loadProfileSpinner = () -> {

            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);

            ArrayList<String> profileItems = new ArrayList<>();

            int selectedPosition = 0;

            profileItems.add("-- "+getString(R.string.disabled)+" --");

            for (int i = 0; i < profiles.size(); i++) {

                ControlsProfile profile = profiles.get(i);

                if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)

                    selectedPosition = i + 1;

                profileItems.add(profile.getName());

            }



            sProfile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profileItems));

            sProfile.setSelection(selectedPosition);

        };

        loadProfileSpinner.run();



        final CheckBox cbShowTouchscreenControls = dialog.findViewById(R.id.CBShowTouchscreenControls);

        cbShowTouchscreenControls.setChecked(inputControlsView.isShowTouchscreenControls());



        final CheckBox cbEnableTimeout = dialog.findViewById(R.id.CBEnableTimeout);

        cbEnableTimeout.setChecked(preferences.getBoolean("touchscreen_timeout_enabled", false));



        final CheckBox cbEnableHaptics = dialog.findViewById(R.id.CBEnableHaptics);

        cbEnableHaptics.setChecked(preferences.getBoolean("touchscreen_haptics_enabled", false));



        final CheckBox cbEnableGyroscope = dialog.findViewById(R.id.CBEnableGyroscope);

        cbEnableGyroscope.setChecked(preferences.getBoolean("gyro_enabled", false));



        final CheckBox cbEnableQuickAccess = dialog.findViewById(R.id.CBEnableQuickAccessPanel);

        cbEnableQuickAccess.setChecked(preferences.getBoolean("quick_access_panel_enabled", false));



        final CheckBox cbRelativeMouseMovement = dialog.findViewById(R.id.CBRelativeMouseMovement);

        cbRelativeMouseMovement.setChecked(preferences.getBoolean("relative_mouse_movement", false));



        final com.google.android.material.slider.Slider sliderGyroscopeSensitivity = dialog.findViewById(R.id.SGyroscopeSensitivity);

        sliderGyroscopeSensitivity.setValue(preferences.getFloat("gyro_sensitivity", 1.0f));



        // Show/hide sensitivity slider based on gyroscope checkbox

        final View gyroscopeSensitivityLayout = dialog.findViewById(R.id.GyroscopeSensitivityLayout);

        gyroscopeSensitivityLayout.setVisibility(cbEnableGyroscope.isChecked() ? View.VISIBLE : View.GONE);



        cbEnableGyroscope.setOnCheckedChangeListener((buttonView, isChecked) -> {

            gyroscopeSensitivityLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);

        });



        final Runnable updateProfile = () -> {

            int position = sProfile.getSelectedItemPosition();

            if (position > 0) {

                showInputControls(inputControlsManager.getProfiles().get(position - 1));

            }

            else hideInputControls();

        };



        dialog.findViewById(R.id.BTSettings).setOnClickListener((v) -> {

            int position = sProfile.getSelectedItemPosition();

            Intent intent = new Intent(this, MainActivity.class);

            intent.putExtra("edit_input_controls", true);

            intent.putExtra("selected_profile_id", position > 0 ? inputControlsManager.getProfiles().get(position - 1).id : 0);

            editInputControlsCallback = () -> {

                hideInputControls();

                inputControlsManager.loadProfiles(true);

                loadProfileSpinner.run();

                updateProfile.run();

            };

            controlsEitorActivityResultLauncher.launch(intent);

        });



        dialog.setOnConfirmCallback(() -> {

            inputControlsView.setShowTouchscreenControls(cbShowTouchscreenControls.isChecked());

            boolean isTimeoutEnabled = cbEnableTimeout.isChecked();

            boolean isHapticsEnabled = cbEnableHaptics.isChecked();

            boolean isGyroscopeEnabled = cbEnableGyroscope.isChecked();

            float gyroSensitivity = sliderGyroscopeSensitivity.getValue();

            SharedPreferences.Editor editor = preferences.edit();

            editor.putBoolean("show_touchscreen_controls_enabled", cbShowTouchscreenControls.isChecked());

            editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);

            editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);

            editor.putBoolean("gyro_enabled", isGyroscopeEnabled);

            editor.putFloat("gyro_sensitivity", gyroSensitivity);

            boolean isQuickAccessEnabled = cbEnableQuickAccess.isChecked();

            editor.putBoolean("quick_access_panel_enabled", isQuickAccessEnabled);



            boolean isRelativeMouseMovementEnabled = cbRelativeMouseMovement.isChecked();

            editor.putBoolean("relative_mouse_movement", isRelativeMouseMovementEnabled);

            isRelativeMouseMovement = isRelativeMouseMovementEnabled;

            xServer.setRelativeMouseMovement(isRelativeMouseMovement);



            editor.apply();



            updateQuickAccessPanel(isQuickAccessEnabled);



            if (isTimeoutEnabled && !cbShowTouchscreenControls.isChecked()) {

                startTouchscreenTimeout(); // Start the timeout functionality if enabled

            } else {

                timeoutHandler.removeCallbacks(hideControlsRunnable);

                touchpadView.setOnTouchListener(null); // Disable the listener if timeout is disabled

            }

            

            int position = sProfile.getSelectedItemPosition();

            if (position > 0) {

                showInputControls(inputControlsManager.getProfiles().get(position - 1));

            }

            else hideInputControls();

            updateProfile.run();

        });



        dialog.setOnCancelCallback(updateProfile::run);



        dialog.setCanceledOnTouchOutside(false);

        dialog.show();

    }



    private void simulateConfirmInputControlsDialog() {

        // Simulate setting the relative mouse movement and touchscreen controls from preferences



        boolean isShowTouchscreenControls = preferences.getBoolean("show_touchscreen_controls_enabled", true);

        inputControlsView.setShowTouchscreenControls(isShowTouchscreenControls);



        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);

        boolean isHapticsEnabled = preferences.getBoolean("touchscreen_haptics_enabled", false);

        boolean isGyroscopeEnabled = preferences.getBoolean("gyro_enabled", false);



        // Apply these settings as if the user confirmed the dialog

        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);

        editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);

        editor.apply();



        // If no profile is selected, hide the controls

        int selectedProfileIndex = preferences.getInt("selected_profile_index", -1); // Default to -1 for no profile



        if (selectedProfileIndex >= 0 && selectedProfileIndex < inputControlsManager.getProfiles().size()) {

            // A profile is selected, show the controls

            ControlsProfile profile = inputControlsManager.getProfiles().get(selectedProfileIndex);

            showInputControls(profile);

        } else {

            // No profile selected, ensure the controls are hidden

            hideInputControls();

        }



        // Timeout logic should only apply if the controls are visible

        if (isTimeoutEnabled && inputControlsView.getVisibility() == View.VISIBLE && !inputControlsView.isShowTouchscreenControls()) {

            startTouchscreenTimeout(); // Start timeout if enabled and controls are visible

        } else {

            timeoutHandler.removeCallbacks(hideControlsRunnable);

            touchpadView.setOnTouchListener(null); // Disable the timeout listener if not needed

        }



        Log.d("XServerDisplayActivity", "Input controls simulated confirmation executed.");

    }



    private void showActiveWindowsDialog() {

        ActiveWindowsDialog dialog = new ActiveWindowsDialog(this);

        dialog.show();

    }



    private void startTouchscreenTimeout() {

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);



        if (inputControlsView != null && inputControlsView.isShowTouchscreenControls()) {

            inputControlsView.setVisibility(View.VISIBLE);

            timeoutHandler.removeCallbacks(hideControlsRunnable);

            touchpadView.setOnTouchListener(null);

            return;

        }



        if (isTimeoutEnabled) {

            // Show controls initially and set up touch event listeners

            inputControlsView.setVisibility(View.VISIBLE);

            Log.d("XServerDisplayActivity", "Timeout is enabled, setting up timeout logic.");



            // Attach the OnTouchListener to reset the timeout on touch events

            touchpadView.setOnTouchListener((v, event) -> {

                int action = event.getAction();

                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {

                    // Reset the timeout on any touch event

                    //Log.d("XServerDisplayActivity", "Touch detected, resetting timeout.");



                    // Keep the controls visible

                    inputControlsView.setVisibility(View.VISIBLE);



                    // Remove any pending hide callbacks and reset the timeout

                    timeoutHandler.removeCallbacks(hideControlsRunnable);

                    timeoutHandler.postDelayed(hideControlsRunnable, 5000); // Reset timeout

                }



                return false; // Allow the touch event to propagate

            });



            // Reset the timeout when the controls are initially displayed

            timeoutHandler.removeCallbacks(hideControlsRunnable);

            timeoutHandler.postDelayed(hideControlsRunnable, 5000); // Hide after 5 seconds of inactivity

        } else {

            // If timeout is disabled, keep the controls always visible

            Log.d("XServerDisplayActivity", "Timeout is disabled, controls will stay visible.");



            inputControlsView.setVisibility(View.VISIBLE); // Ensure controls are visible

            timeoutHandler.removeCallbacks(hideControlsRunnable); // Remove any existing hide callbacks

            touchpadView.setOnTouchListener(null); // Remove the touch listener

        }

    }



    public void showInputControls(ControlsProfile profile) {

        inputControlsView.setVisibility(View.VISIBLE);

        inputControlsView.requestFocus();

        inputControlsView.setProfile(profile);



        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);

        touchpadView.setPointerButtonRightEnabled(false);



        inputControlsView.invalidate();



        if (preferences.getBoolean("touchscreen_timeout_enabled", false)

                && !inputControlsView.isShowTouchscreenControls()) {

            startTouchscreenTimeout();

        } else {

            timeoutHandler.removeCallbacks(hideControlsRunnable);

            touchpadView.setOnTouchListener(null);

        }

    }



    public void hideInputControls() {

        inputControlsView.setShowTouchscreenControls(true);

        inputControlsView.setVisibility(View.GONE);

        inputControlsView.setProfile(null);



        touchpadView.setSensitivity(globalCursorSpeed);

        touchpadView.setPointerButtonLeftEnabled(true);

        touchpadView.setPointerButtonRightEnabled(true);



        inputControlsView.invalidate();

        timeoutHandler.removeCallbacks(hideControlsRunnable);

    }



    private void updateQuickAccessPanel(boolean enabled) {

        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);

        if (enabled) {

            if (quickAccessPanel == null) {

                quickAccessPanel = new QuickAccessPanel(this);

                quickAccessPanel.setQuickAccessListener(new QuickAccessPanel.QuickAccessListener() {

                    @Override

                    public void onTaskManagerClick() {

                        new TaskManagerDialog(XServerDisplayActivity.this).show();

                    }

                    @Override

                    public void onFpsCounterClick() {

                        showFpsCounterDialog();

                    }

                    @Override

                    public void onInputControlsClick() {

                        showInputControlsDialog();

                    }

                });

                rootView.addView(quickAccessPanel);

            } else {

                quickAccessPanel.setVisibility(View.VISIBLE);

            }

        } else {

            if (quickAccessPanel != null) {

                quickAccessPanel.setVisibility(View.GONE);

            }

        }

    }



    private void extractGraphicsAsset(String assetFileName, File rootDir) {
        java.io.File override = new java.io.File(getFilesDir(), "graphics_driver/" + assetFileName);
        if (override.isFile()) {
            Log.d("GraphicsDriverExtraction", "using user override for " + assetFileName);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, override, rootDir);
        } else {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/" + assetFileName, rootDir);
        }
    }

    private void extractGraphicsDriverFiles() {

        String adrenoToolsDriverId = "";

        String selectedDriverVersion;



        String currentWrapperVersion = graphicsDriverConfig.get("version");

        String isAdrenotoolsTurnip = graphicsDriverConfig.get("adrenotoolsTurnip");



        selectedDriverVersion = currentWrapperVersion;



        if (shortcut != null) {

            currentWrapperVersion = shortcut.getExtra("wrapperGraphicsDriverVersion", graphicsDriverConfig.get("version"));

            selectedDriverVersion = currentWrapperVersion;

        }



        adrenoToolsDriverId = (selectedDriverVersion.contains(DefaultVersion.WRAPPER)) ? DefaultVersion.WRAPPER : selectedDriverVersion;

        Log.d("GraphicsDriverExtraction", "Adrenotools DriverID: " + adrenoToolsDriverId);



        File rootDir = imageFs.getRootDir();



        if (dxwrapper.equals("dxvk")) {

            DXVKConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);

        } else if (dxwrapper.equals("vkd3d")) {

            VKD3DConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);

        }



        boolean useDRI3 = preferences.getBoolean("use_dri3", true);

        if (!useDRI3) {

            envVars.put("MESA_VK_WSI_DEBUG", "sw");

        }



        if (currentWrapperVersion.toLowerCase().contains("turnip") && isAdrenotoolsTurnip.equals("0"))

            envVars.put("VK_ICD_FILENAMES", imageFs.getShareDir() + "/vulkan/icd.d/freedreno_icd.aarch64.json");

        else

            envVars.put("VK_ICD_FILENAMES", imageFs.getShareDir() + "/vulkan/icd.d/wrapper_icd.aarch64.json");

        envVars.put("GALLIUM_DRIVER", "zink");

        envVars.put("LIBGL_KOPPER_DISABLE", "true");



        if (firstTimeBoot) {

            Log.d("XServerDisplayActivity", "First time container boot, re-extracting wrapper");

            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/wrapper" + ".tzst", rootDir);

            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/extra_libs" + ".tzst", rootDir);

        }

        // Wrapper slots + imports (Banner #132): точный override/import побеждает,
        // иначе слот-цепочка, иначе поведение по умолчанию выше.
        try {
            String selDriver = graphicsDriver;
            if (shortcut != null) {
                String sd = shortcut.getExtra("graphicsDriver", null);
                if (sd != null && !sd.isEmpty()) selDriver = sd;
            }
            if (selDriver != null && !selDriver.isEmpty()) {
                String norm = com.winlator.cmod.core.StringUtils.parseIdentifier(selDriver);
                java.io.File userWrapper = new java.io.File(getFilesDir(), "graphics_driver/" + selDriver + ".tzst");
                java.io.File normWrapper = new java.io.File(getFilesDir(), "graphics_driver/" + norm + ".tzst");
                java.io.File customFile = com.winlator.cmod.contents.WrapperManager.getWrapperFile(this, selDriver);
                if (userWrapper.isFile()) {
                    Log.d("GraphicsDriverExtraction", "Extracting user wrapper (override/import): " + userWrapper.getAbsolutePath());
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, userWrapper, rootDir);
                } else if (normWrapper.isFile() && !norm.equals(selDriver)) {
                    Log.d("GraphicsDriverExtraction", "Extracting user wrapper (override/import): " + normWrapper.getAbsolutePath());
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, normWrapper, rootDir);
                } else if (customFile != null && customFile.exists()) {
                    android.util.Log.i("WrapperManager", "Extracting custom wrapper: " + customFile.getName() + " for driver " + selDriver);
                    com.winlator.cmod.core.TarCompressorUtils.extract(com.winlator.cmod.core.TarCompressorUtils.Type.ZSTD, customFile, rootDir);
                    com.winlator.cmod.core.TarCompressorUtils.extract(com.winlator.cmod.core.TarCompressorUtils.Type.ZSTD, this, "graphics_driver/extra_libs.tzst", rootDir);
                } else if (norm.startsWith("wrapper-original")) {
                    extractGraphicsAsset("wrapper-original.tzst", rootDir);
                } else if (norm.startsWith("wrapper-leegao")) {
                    extractGraphicsAsset("wrapper-leegao.tzst", rootDir);
                } else if (norm.startsWith("wrapper-legacy")) {
                    extractGraphicsAsset("wrapper-legacy.tzst", rootDir);
                } else if (norm.startsWith("wrapper-gamenative")) {
                    extractGraphicsAsset("wrapper-gamenative.tzst", rootDir);
                }
            }
        } catch (Exception e) { android.util.Log.w("WrapperManager", "custom wrapper extract failed", e); }

        // BCn layer override поверх extra_libs — каждый запуск, если стоит.
        try {
            java.io.File bcnOverride = new java.io.File(getFilesDir(), "graphics_driver/leegao_bcn.tzst");
            if (bcnOverride.isFile()) {
                Log.d("GraphicsDriverExtraction", "applying user BCn layer override (leegao_bcn.tzst)");
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, bcnOverride, rootDir);
            }
        } catch (Exception e) { android.util.Log.w("WrapperManager", "bcn override extract failed", e); }



        if (adrenoToolsDriverId != "System") {

            AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);

            adrenotoolsManager.setDriverById(envVars, imageFs, adrenoToolsDriverId);

        }

        String vulkanVersion = graphicsDriverConfig.get("vulkanVersion");
        envVars.put("WRAPPER_VK_VERSION", vulkanVersion != null ? vulkanVersion : "1.3");

        String blacklistedExtensions = graphicsDriverConfig.get("blacklistedExtensions");

        envVars.put("WRAPPER_EXTENSION_BLACKLIST", blacklistedExtensions);



        String maxDeviceMemory = graphicsDriverConfig.get("maxDeviceMemory");

        if (maxDeviceMemory != null && Integer.parseInt(maxDeviceMemory) > 0)

            envVars.put("UTIL_LAYER_VMEM_MAX_SIZE", maxDeviceMemory);



        String frameSync = graphicsDriverConfig.get("frameSync");

        String presentMode = resolvePresentModeForFrameSync(frameSync, graphicsDriverConfig.get("presentMode"));

        if ("Always".equals(frameSync)) {

            if (useDRI3) {

                envVars.put("MESA_VK_WSI_DEBUG", "forcesync");

            }

            envVars.put("MESA_VK_WSI_PRESENT_MODE", "fifo");

        }

        else {

            if ("Never".equals(frameSync)) {

                envVars.put("WRAPPER_DISABLE_PRESENT_WAIT", "1");

            }

            envVars.put("MESA_VK_WSI_PRESENT_MODE", presentMode);

        }

        

        String resourceType = graphicsDriverConfig.get("resourceType");

        if (resourceType != null && !resourceType.isEmpty())

            envVars.put("WRAPPER_RESOURCE_TYPE", resourceType);

        String bcnEmulation = graphicsDriverConfig.get("bcnEmulation");
        String bcnEmulationType = graphicsDriverConfig.get("bcnEmulationType");

        if (bcnEmulation != null) {
            switch (bcnEmulation) {
                case "auto" -> {
                    if (bcnEmulationType != null && bcnEmulationType.equals("compute") && GPUInformation.getVendorID() != 0x5143) {
                        envVars.put("ENABLE_BCN_COMPUTE", "1");
                        envVars.put("BCN_COMPUTE_AUTO", "1");
                    }
                    envVars.put("WRAPPER_EMULATE_BCN", "3");
                }
                case "full" -> {
                    if (bcnEmulationType != null && bcnEmulationType.equals("compute") && GPUInformation.getVendorID() != 0x5143) {
                        envVars.put("ENABLE_BCN_COMPUTE", "1");
                        envVars.put("BCN_COMPUTE_AUTO", "0");
                    }
                    envVars.put("WRAPPER_EMULATE_BCN", "2");
                }
                case "none" -> envVars.put("WRAPPER_EMULATE_BCN", "0");
                default -> envVars.put("WRAPPER_EMULATE_BCN", "1");
            }

            String bcnEmulationCache = graphicsDriverConfig.get("bcnEmulationCache");
            envVars.put("WRAPPER_USE_BCN_CACHE", bcnEmulationCache != null ? bcnEmulationCache : "0");
        }

        String blit = graphicsDriverConfig.get("blit");

        if (blit != null && !blit.isEmpty())

            envVars.put("WRAPPER_BLIT", blit);

        if (!vkbasaltConfig.isEmpty()) {

            envVars.put("ENABLE_VKBASALT", "1");

            envVars.put("VKBASALT_CONFIG", vkbasaltConfig);

        }

    }



    private String normalizeFrameSyncMode(String frameSync) {

        if ("Never".equals(frameSync) || "Always".equals(frameSync)) {

            return frameSync;

        }

        return "Normal";

    }



    private String resolvePresentModeForFrameSync(String frameSync, String presentMode) {

        String normalizedFrameSync = normalizeFrameSyncMode(frameSync);

        if (presentMode == null || presentMode.trim().isEmpty()) {

            if ("Never".equals(normalizedFrameSync)) {

                return "immediate";

            }

            if ("Always".equals(normalizedFrameSync)) {

                return "fifo";

            }

            return "mailbox";

        }



        String normalizedPresentMode = presentMode.trim().toLowerCase();

        if ("Always".equals(normalizedFrameSync)) {

            return "fifo";

        }

        if ("Normal".equals(normalizedFrameSync) && "relaxed".equals(normalizedPresentMode)) {

            return "mailbox";

        }

        return normalizedPresentMode;

    }



    private void copyFile(File sourceFile, File destFile) throws IOException {

        try (InputStream inputStream = new FileInputStream(sourceFile);

             OutputStream outputStream = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[1024];

            int length;

            while ((length = inputStream.read(buffer)) > 0) {

                outputStream.write(buffer, 0, length);

            }

        }

    }







//    @Override

//    public boolean dispatchGenericMotionEvent(MotionEvent event) {

//        return !winHandler.onGenericMotionEvent(event) && !touchpadView.onExternalMouseEvent(event) && super.dispatchGenericMotionEvent(event);

//    }



    @Override

    public boolean dispatchGenericMotionEvent(MotionEvent event) {

        boolean handledByWinHandler = false;

        boolean handledByTouchpadView = false;



        // Let winHandler process the event if available

        if (winHandler != null) {

            handledByWinHandler = winHandler.onGenericMotionEvent(event);

            if (handledByWinHandler) {

                //Log.d("XServerDisplayActivity", "Event handled by winHandler");

            }

        }



        // Let touchpadView process the event if available

        if (touchpadView != null) {

            handledByTouchpadView = touchpadView.onExternalMouseEvent(event);

            if (handledByTouchpadView) {

                //Log.d("XServerDisplayActivity", "Event handled by touchpadView");

            }

        }



        // Pass the event to the super method to ensure system-level handling

        boolean handledBySuper = super.dispatchGenericMotionEvent(event);

        if (!handledBySuper) {

            //Log.d("XServerDisplayActivity", "Event not handled by super");

        }



        // Combine the results: any handler consuming the event indicates it was handled

        return handledByWinHandler || handledByTouchpadView || handledBySuper;

    }





    


    @Override

    public boolean dispatchKeyEvent(KeyEvent event) {



        // Handle the PlayStation or Xbox Home button to open the drawer

        if (event.getAction() == KeyEvent.ACTION_DOWN) {

            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE || event.getKeyCode() == KeyEvent.KEYCODE_HOME || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT) {

                boolean handled = inputControlsView.onKeyEvent(event) || (winHandler != null && winHandler.onKeyEvent(event)) && (xServer != null && xServer.keyboard.onKeyEvent(event));

                return true;

            }

        }



        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE

                && touchpadView != null && touchpadView.hasPointerCapture()) {

            touchpadView.releasePointerCapture();

        }



        // **NEW: Check if the floating view is visible and forward the key event to it**

        if (winetricksFloatingView != null && winetricksFloatingView.getVisibility() == View.VISIBLE) {

            if (winetricksFloatingView.dispatchKeyEvent(event)) {

                return true; // Indicate the floating view handled the event

            }

        }



        // Fallback to existing input handling

        return (!inputControlsView.onKeyEvent(event) && !winHandler.onKeyEvent(event) && xServer.keyboard.onKeyEvent(event)) ||

                (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));

    }







    public InputControlsView getInputControlsView() {
        return inputControlsView;
    }

    public InputControlsManager getInputControlsManager() {
        return inputControlsManager;
    }

    public void applyInputControlsSettings(boolean showTouchscreenControls, boolean isTimeoutEnabled, boolean isHapticsEnabled,
                                           boolean isGyroscopeEnabled, float gyroSensitivity, boolean isQuickAccessEnabled,
                                           boolean isRelativeMouseMovementEnabled, int profileId) {
        inputControlsView.setShowTouchscreenControls(showTouchscreenControls);

        SharedPreferences prefs = new com.winlator.cmod.core.MmkvPreferences();
        prefs.edit()
            .putBoolean("show_touchscreen_controls_enabled", showTouchscreenControls)
            .putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled)
            .putBoolean("touchscreen_haptics_enabled", isHapticsEnabled)
            .putBoolean("gyro_enabled", isGyroscopeEnabled)
            .putFloat("gyro_sensitivity", gyroSensitivity)
            .putBoolean("quick_access_panel_enabled", isQuickAccessEnabled)
            .putBoolean("relative_mouse_movement", isRelativeMouseMovementEnabled)
            .apply();

        isRelativeMouseMovement = isRelativeMouseMovementEnabled;
        if (xServer != null) {
            xServer.setRelativeMouseMovement(isRelativeMouseMovementEnabled);
        }

        updateQuickAccessPanel(isQuickAccessEnabled);

        if (isTimeoutEnabled && !showTouchscreenControls) {
            startTouchscreenTimeout();
        } else {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            if (touchpadView != null) touchpadView.setOnTouchListener(null);
        }

        com.winlator.cmod.inputcontrols.ControlsProfile profile = null;
        if (profileId > 0) {
            for (com.winlator.cmod.inputcontrols.ControlsProfile p : inputControlsManager.getProfiles()) {
                if (p.id == profileId) {
                    profile = p;
                    break;
                }
            }
        }

        if (profile != null) {
            showInputControls(profile);
        } else {
            hideInputControls();
        }
    }

    public void launchInputControlsEditor(int selectedProfileId, Runnable onResultCallback) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("edit_input_controls", true);
        intent.putExtra("selected_profile_id", selectedProfileId);
        editInputControlsCallback = () -> {
            hideInputControls();
            inputControlsManager.loadProfiles(true);
            if (onResultCallback != null) onResultCallback.run();
        };
        controlsEitorActivityResultLauncher.launch(intent);
    }

    /** Показать выбранный профиль на живом оверлее перед входом в редактор. */
    public void showProfileForEditing(int profileId) {
        if (inputControlsView == null) return;
        ControlsProfile profile = inputControlsManager.getProfile(profileId);
        if (profile != null) showInputControls(profile);
    }

    /** Редактор кнопок прямо поверх игры (edit-mode живого InputControlsView). */
    public void enterControlsEditMode() {
        if (inputControlsView == null || xserverMenuController == null) return;
        // Профиль должен быть показан, иначе edit-mode молча ничего не делает
        if (inputControlsView.getProfile() == null) {
            java.util.ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles();
            if (profiles == null || profiles.isEmpty()) {
                AppUtils.showToast(this, R.string.no_profiles);
                return;
            }
            showInputControls(profiles.get(0));
        }
        // Авто-скрытие кнопок не должно срабатывать во время редактирования
        timeoutHandler.removeCallbacks(hideControlsRunnable);
        inputControlsView.setEditMode(true);
        inputControlsView.invalidate();
        xserverMenuController.showControlsEditor();
    }

    /** Выход из редактора поверх игры: кнопки снова работают вручную. */
    public void exitControlsEditMode() {
        if (inputControlsView == null) return;
        inputControlsView.setEditMode(false);
        inputControlsView.deselectAllElements();
        inputControlsView.invalidate();
        if (xserverMenuController != null) xserverMenuController.hideControlsEditor();
        // Вернуть авто-скрытие, если оно включено в настройках
        startTouchscreenTimeout();
    }



    private void generateWineprefix() {

        Intent intent = getIntent();



        final File rootDir = imageFs.getRootDir();

        final File installedWineDir = imageFs.getInstalledWineDir();

        wineInfo = intent.getParcelableExtra("wine_info");

        envVars.put("WINEARCH", wineInfo.isWin64() ? "win64" : "win32");

        imageFs.setWinePath(wineInfo.path);



        final File containerPatternDir = new File(installedWineDir, "/preinstall/container-pattern");

        if (containerPatternDir.isDirectory()) FileUtils.delete(containerPatternDir);

        containerPatternDir.mkdirs();



        File linkFile = new File(rootDir, ImageFs.HOME_PATH);

        linkFile.delete();

        FileUtils.symlink(".."+FileUtils.toRelativePath(rootDir.getPath(), containerPatternDir.getPath()), linkFile.getPath());



        GuestProgramLauncherComponent guestProgramLauncherComponent = environment.getComponent(GuestProgramLauncherComponent.class);

//        guestProgramLauncherComponent.setGuestExecutable(wineInfo.getExecutable(this, false)+" explorer /desktop=shell,"+Container.DEFAULT_SCREEN_SIZE+" winecfg");

        guestProgramLauncherComponent.setGuestExecutable("wineboot -u explorer /desktop=shell,"+Container.DEFAULT_SCREEN_SIZE+" winecfg");



        final PreloaderDialog preloaderDialog = new PreloaderDialog(this);

        guestProgramLauncherComponent.setTerminationCallback((status) -> Executors.newSingleThreadExecutor().execute(() -> {

            if (status > 0) {

                showToast(this, R.string.unable_to_install_wine);

                FileUtils.delete(new File(installedWineDir, "/preinstall"));

                AppUtils.restartApplication(this);

                return;

            }



            preloaderDialog.showOnUiThread(R.string.finishing_installation);

            FileUtils.writeString(new File(rootDir, ImageFs.WINEPREFIX+"/.update-timestamp"), "disable\n");



            File userDir = new File(rootDir, ImageFs.WINEPREFIX+"/drive_c/users/xuser");

            File[] userFiles = userDir.listFiles();

            if (userFiles != null) {

                for (File userFile : userFiles) {

                    if (FileUtils.isSymlink(userFile)) {

                        String path = userFile.getPath();

                        userFile.delete();

                        (new File(path)).mkdirs();

                    }

                }

            }



            String suffix = wineInfo.fullVersion()+"-"+wineInfo.getArch();

            File containerPatternFile = new File(installedWineDir, "/preinstall/container-pattern-"+suffix+".tzst");

            TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD, new File(rootDir, ImageFs.WINEPREFIX), containerPatternFile, MainActivity.CONTAINER_PATTERN_COMPRESSION_LEVEL);



            if (!containerPatternFile.renameTo(new File(installedWineDir, containerPatternFile.getName())) ||

                    !(new File(wineInfo.path)).renameTo(new File(installedWineDir, wineInfo.identifier()))) {

                containerPatternFile.delete();

            }



            FileUtils.delete(new File(installedWineDir, "/preinstall"));



            preloaderDialog.closeOnUiThread();

            AppUtils.restartApplication(this, R.id.main_menu_settings);

        }));

    }



    private static final String TAG = "DXWrapperExtraction";



    private void extractDXWrapperFiles(String dxwrapper) {

        final String[] dlls = {"d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll", "d3d8.dll", "d3d9.dll", "dxgi.dll"};



        File rootDir = imageFs.getRootDir();

        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");



        if (dxwrapper.contains("vkd3d")) {

            ContentProfile profile = contentsManager.getProfileByEntryName(dxwrapper);

            Log.d(TAG, "Extracting DXVK 2.4.1");

            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/dxvk-2.4.1" + ".tzst", windowsDir, onExtractFileListener);

            if (profile != null) {

                Log.d(TAG, "Applying user-defined VKD3D content profile: " + dxwrapper);

                contentsManager.applyContent(profile);

            } else {

                Log.d(TAG, "Extracting fallback VKD3D .tzst archive: " + dxwrapper);

                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + dxwrapper + ".tzst", windowsDir, onExtractFileListener);

            }

            Log.d(TAG, "Finished VKD3D extraction for " + dxwrapper);

        } else if (dxwrapper.contains("dxvk")) {

            Log.d(TAG, "Extracting DXVK wrapper files, version: " + dxwrapper);



            ContentProfile profile = contentsManager.getProfileByEntryName(dxwrapper);

            if (profile != null) {

                Log.d(TAG, "Applying user-defined DXVK content profile: " + dxwrapper);

                contentsManager.applyContent(profile);

            } else {

                Log.d(TAG, "Extracting fallback DXVK .tzst archive: " + dxwrapper);

                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + dxwrapper + ".tzst", windowsDir, onExtractFileListener);



                if (compareVersion(StringUtils.parseNumber(dxwrapper), "2.4") < 0) {

                    Log.d(TAG, "Extracting d8vk as part of DXVK version " + dxwrapper);

                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/d8vk-" + DefaultVersion.D8VK + ".tzst", windowsDir, onExtractFileListener);

                }

            }

        } else if (dxwrapper.contains("wined3d")) {

            Log.d(TAG, "Restoring original DLL files for wined3d.");

            restoreOriginalDllFiles(dlls);

        }

    }



    private void extractDDrawrapperFiles(String ddrawrapper) {

        final String[] dlls = {"ddraw.dll","d3dimm.dll"};

        final String[] glideDlls = {"glide.dll", "glide2x.dll", "glide3x.dll", "3DfxSpl.dll", "3DfxSpl2.dll", "3DfxSpl3.dll"};



        File rootDir = imageFs.getRootDir();

        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");



        Log.d("XServerDisplayActivity", "Deleting glide dlls before extraction");

        for (String glideDLL : glideDlls) {

            FileUtils.delete(new File(windowsDir + "/syswow64/" + glideDLL));

        }



        if (ddrawrapper.equals("wined3d")) {

            Log.d("XserverDisplayActivity", "Restoring original dlls for WineD3D");

            restoreOriginalDllFiles(dlls);

        }

        else {

            Log.d("XServerDisplayActivity", "Extracting ddrawrapper " + ddrawrapper);

            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/" + ddrawrapper + ".tzst", windowsDir, onExtractFileListener);

        }



        Log.d("XServerDisplayActivity", "Extracting nglide wrapper");

        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/nglide.tzst", windowsDir, onExtractFileListener);

    }





    private static int compareVersion(String varA, String varB) {

        final String[] levelsA = varA.split("\\.");

        final String[] levelsB = varB.split("\\.");

        int minLen = Math.min(levelsA.length, levelsB.length);

        int numA, numB;



        for (int i = 0; i < minLen; i++) {

            numA = Integer.parseInt(levelsA[i]);

            numB = Integer.parseInt(levelsB[i]);

            if (numA != numB)

                return numA - numB;

        }



        if (levelsA.length != levelsB.length)

            return levelsA.length - levelsB.length;



        return 0;

    }



    private void extractWinComponentFiles() {

        Log.d("XServerDisplayActivity", "Extracting WinComponents");

        File rootDir = imageFs.getRootDir();

        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX+"/drive_c/windows");

        File systemRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/system.reg");



        try {

            JSONObject wincomponentsJSONObject = new JSONObject(FileUtils.readString(this, "wincomponents/wincomponents.json"));

            ArrayList<String> dlls = new ArrayList<>();

            String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents()) : container.getWinComponents();



            Iterator<String[]> oldWinComponentsIter = new KeyValueSet(container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS)).iterator();



            for (String[] wincomponent : new KeyValueSet(wincomponents)) {

                if (wincomponent[1].equals(oldWinComponentsIter.next()[1]) && !firstTimeBoot) continue;

                String identifier = wincomponent[0];

                boolean useNative = wincomponent[1].equals("1");



                if (!wineInfo.isArm64EC() && identifier.contains("opengl") && useNative)

                    continue;



                if (useNative) {

                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "wincomponents/"+identifier+".tzst", windowsDir, onExtractFileListener);

                }

                else {

                    JSONArray dlnames = wincomponentsJSONObject.getJSONArray(identifier);

                    for (int i = 0; i < dlnames.length(); i++) {

                        String dlname = dlnames.getString(i);

                        dlls.add(!dlname.endsWith(".exe") ? dlname+".dll" : dlname);

                    }

                }

                Log.d("XServerDisplayActivity", "Setting wincomponent " + identifier + " to " + String.valueOf(useNative));

                WineUtils.overrideWinComponentDlls(this, container, identifier, useNative);

                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative, this);

            }



            if (!dlls.isEmpty()) restoreOriginalDllFiles(dlls.toArray(new String[0]));

        }

        catch (JSONException e) {
            Log.e("XServerDisplayActivity", "Failed to restore original DLL files", e);
        }

    }



    private void restoreOriginalDllFiles(final String... dlls) {

        File rootDir = imageFs.getRootDir();

        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX+"/drive_c/windows");

        File system32dlls = null;

        File syswow64dlls = null;



        if (wineInfo.isArm64EC())

            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/aarch64-windows");

        else

            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/x86_64-windows");



        syswow64dlls = new File(imageFs.getWinePath() + "/lib/wine/i386-windows");





        for (String dll : dlls) {

            File srcFile = new File(system32dlls, dll);

            File dstFile = new File(windowsDir, "system32/" + dll);

            FileUtils.copy(srcFile, dstFile);

            srcFile = new File(syswow64dlls, dll);

            dstFile = new File(windowsDir, "syswow64/" + dll);

            FileUtils.copy(srcFile, dstFile);

        }

   }



    private boolean isGenerateWineprefix() {

        return getIntent().getBooleanExtra("generate_wineprefix", false);

    }



    private String getWineStartCommand() {

        // Initialize overrideEnvVars if not already done

        EnvVars envVars = getOverrideEnvVars();



        // Define default arguments

        String args = "";



        if (shortcut != null) {

            String execArgs = shortcut.getExtra("execArgs");

            execArgs = !execArgs.isEmpty() ? " " + execArgs : "";



            if (shortcut.path.endsWith(".lnk")) {

                args += "\"" + shortcut.path + "\"" + execArgs;

            } else {

                String exeDir = FileUtils.getDirname(shortcut.path);

                String filename = FileUtils.getName(shortcut.path);



                int dotIndex = filename.lastIndexOf(".");

                int spaceIndex = (dotIndex != -1) ? filename.indexOf(" ", dotIndex) : -1;



                if (spaceIndex != -1) {

                    execArgs = filename.substring(spaceIndex + 1) + execArgs;

                    filename = filename.substring(0, spaceIndex);

                }



                args += "/dir " + StringUtils.escapeDOSPath(exeDir) + " \"" + filename + "\"" + execArgs;

            }

        } else {

            // Append EXTRA_EXEC_ARGS from overrideEnvVars if it exists

            if (envVars.has("EXTRA_EXEC_ARGS")) {

                args += " " + envVars.get("EXTRA_EXEC_ARGS");

                envVars.remove("EXTRA_EXEC_ARGS"); // Remove the key after use

            } else {

                args += "\"wfm.exe\"";

            }

        }

        // Construct the final command

        String command = "winhandler.exe " + args;

        Log.d("Winetricks", "Wine Start Command: " + command);



        return command;

    }



    private String getExecutable() {

        String filename = "";

        if (shortcut != null) {

            filename = FileUtils.getName(shortcut.path);

        }

        else if (isGenerateWineprefix()) {

            filename = "wineboot.exe";

        }

        else

            filename = "wfm.exe";

        return filename;

    }





    public XServer getXServer() {

        return xServer;

    }



    public WinHandler getWinHandler() {

        return winHandler;

    }



    public XServerView getXServerView() {

        return xServerView;

    }



    public Container getContainer() {

        return container;

    }



    public void setDXWrapper(String dxwrapper) {

        this.dxwrapper = dxwrapper;

    }



    public EnvVars getOverrideEnvVars() {

        if (overrideEnvVars == null) {

            overrideEnvVars = new EnvVars();

        }

        return overrideEnvVars;

    }



    private void changeWineAudioDriver() {

        if (!audioDriver.equals(container.getExtra("audioDriver"))) {

            File rootDir = imageFs.getRootDir();

            File userRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/user.reg");

            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {

                if (audioDriver.equals("alsa") || audioDriver.equals("alsa-reflector")) {

                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");

                }

                else if (audioDriver.equals("pulseaudio")) {

                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");

                }

            }

            container.putExtra("audioDriver", audioDriver);

            container.saveData();

        }

    }



    private void applyGeneralPatches(Container container) {

        File rootDir = imageFs.getRootDir();

        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "container_pattern_common.tzst", rootDir);

        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "pulseaudio.tzst", new File(getFilesDir(), "pulseaudio"));

        WineUtils.applySystemTweaks(this, wineInfo);

        container.putExtra("graphicsDriver", null);

        container.putExtra("desktopTheme", null);

    }



//    private void assignTaskAffinity(Window window) {

//        if (taskAffinityMask == 0) return;

//        int processId = window.getProcessId();

//        String className = window.getClassName();

//        int processAffinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;

//

//        if (processId > 0) {

//            winHandler.setProcessAffinity(processId, processAffinity);

//        }

//        else if (!className.isEmpty()) {

//            winHandler.setProcessAffinity(window.getClassName(), processAffinity);

//        }

//    }



    private void changeFrameRatingVisibility(Window window, Property property) {

        if (property != null) {

            String propertyName = property.nameAsString();

            String propertyValue = property.toString();

            if (frameRatingWindowId == -1 && isFrameRatingHudProperty(propertyName)) {

                frameRatingWindowId = window.id;

                if (xServerView != null) xServerView.getRenderer().setFpsWindowId(window.id);

                Log.d("XServerDisplayActivity", "Showing hud for Window " + window.getName());

                updateFpsCounterVisibility();

                // Р С›Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµР С РЎРѓРЎвЂЎР ВµРЎвЂљРЎвЂЎР С‘Р С” РЎвЂљР С•Р В»РЎРЉР С”Р С• Р ВµРЎРѓР В»Р С‘ Р С•Р Р… Р Р†Р С”Р В»РЎР‹РЎвЂЎР ВµР Р…

                if (fpsCounterConfig.isEnabled() && frameRating != null) {

                    frameRating.update();

                }

            }

            if (frameRating != null && isFrameRatingRendererProperty(propertyName)) {

                frameRating.setRenderer(propertyValue);

            }

            if (frameRating != null && isFrameRatingGpuProperty(propertyName)) {

                frameRating.setGpuName(propertyValue);

            }

        }

        else if (frameRatingWindowId != -1) {

            frameRatingWindowId = -1;

            if (xServerView != null) xServerView.getRenderer().setFpsWindowId(-1);

            Log.d("XServerDisplayActivity", "Hiding hud for Window " + window.getName());

            // Р СњР Вµ Р С—РЎР‚Р С‘Р Р…РЎС“Р Т‘Р С‘РЎвЂљР ВµР В»РЎРЉР Р…Р С• РЎРѓР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С - Р С•РЎРѓРЎвЂљР В°Р Р†Р В»РЎРЏР ВµР С РЎС“Р С—РЎР‚Р В°Р Р†Р В»Р ВµР Р…Р С‘Р Вµ Р В·Р В° updateFpsCounterVisibility

            if (frameRating != null) frameRating.reset();

			updateFpsCounterVisibility();

        }

    }



    public void enableProfilingHook() {
    }



    private void ensureFrameRatingView() {

        if (container == null || xServerRootView == null || frameRating != null) return;



        frameRating = new FrameRating(this, container);

        frameRating.setVisibility(View.GONE);

        if (xServerView != null) xServerView.getRenderer().setFrameRating(frameRating);

        FrameLayout.LayoutParams fpsLp = new FrameLayout.LayoutParams(

                FrameLayout.LayoutParams.WRAP_CONTENT,

                FrameLayout.LayoutParams.WRAP_CONTENT

        );

        fpsLp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;

        xServerRootView.addView(frameRating, fpsLp);

    }



    private void syncFrameRatingWithExistingWindows() {

        if (xServer == null || frameRating == null) return;



        FrameRatingSnapshot best = findBestFrameRatingSnapshot(xServer.windowManager.rootWindow, null);

        if (best == null) {

            return;

        }



        frameRatingWindowId = best.window.id;

        if (best.renderer != null) {

            frameRating.setRenderer(best.renderer);

        }

        if (best.gpuName != null) {

            frameRating.setGpuName(best.gpuName);

        }

    }



    private FrameRatingSnapshot findBestFrameRatingSnapshot(Window window, FrameRatingSnapshot best) {

        if (window == null) return best;



        if (window != xServer.windowManager.rootWindow) {

            FrameRatingSnapshot snapshot = createFrameRatingSnapshot(window);

            if (snapshot != null && (best == null || snapshot.score > best.score)) {

                best = snapshot;

            }

        }



        // Window updates can arrive while the HUD scans the tree; do not iterate the live list.
        for (Window child : new ArrayList<>(window.getChildren())) {

            best = findBestFrameRatingSnapshot(child, best);

        }

        return best;

    }



    private FrameRatingSnapshot createFrameRatingSnapshot(Window window) {

        Property rendererProperty = getFirstWindowProperty(window,

                "_UTIL_LAYER_ENGINE_NAME",

                "_MESA_DRV_ENGINE_NAME",

                "_MESA_DRV_RENDERER");

        Property gpuProperty = getFirstWindowProperty(window,

                "_UTIL_LAYER_GPU_NAME",

                "_MESA_DRV_GPU_NAME");

        Property hudProperty = rendererProperty != null ? rendererProperty : getFirstWindowProperty(window,

                "_UTIL_LAYER",

                "_MESA_DRV");



        if (hudProperty == null && gpuProperty == null) {

            return null;

        }



        int score = 0;

        if (window.isApplicationWindow()) score += 100000000;

        if (window.attributes.isMapped()) score += 10000000;

        score += Math.min(window.getWidth() * window.getHeight(), 3000000);



        String renderer = rendererProperty != null ? rendererProperty.toString() : null;

        if (renderer != null) {

            String rendererLower = renderer.toLowerCase(java.util.Locale.ENGLISH);

            if (rendererLower.contains("vkd3d")) score += 6000000;

            else if (rendererLower.contains("dxvk")) score += 5000000;

            else if (rendererLower.contains("vulkan") || rendererLower.contains("turnip")) score += 4000000;

            else if (rendererLower.contains("opengl")) score += 1000000;

        }



        return new FrameRatingSnapshot(window, renderer, gpuProperty != null ? gpuProperty.toString() : null, score);

    }



    private Property getFirstWindowProperty(Window window, String... names) {

        for (String name : names) {

            Property property = window.getProperty(Atom.getId(name));

            if (property != null) {

                return property;

            }

        }

        return null;

    }



    private static class FrameRatingSnapshot {

        final Window window;

        final String renderer;

        final String gpuName;

        final int score;



        FrameRatingSnapshot(Window window, String renderer, String gpuName, int score) {

            this.window = window;

            this.renderer = renderer;

            this.gpuName = gpuName;

            this.score = score;

        }

    }



    private boolean isFrameRatingHudProperty(String propertyName) {

        return propertyName.contains("_UTIL_LAYER") || propertyName.contains("_MESA_DRV");

    }



    private boolean isFrameRatingRendererProperty(String propertyName) {

        return propertyName.contains("_UTIL_LAYER_ENGINE_NAME")

                || propertyName.contains("_MESA_DRV_ENGINE_NAME")

                || propertyName.contains("_MESA_DRV_RENDERER");

    }



    private boolean isFrameRatingGpuProperty(String propertyName) {

        return propertyName.contains("_UTIL_LAYER_GPU_NAME")

                || propertyName.contains("_MESA_DRV_GPU_NAME");

    }



    private void removeFrameRatingView() {

        if (frameRating == null) return;

        if (frameRating.getParent() instanceof ViewGroup) {

            ((ViewGroup) frameRating.getParent()).removeView(frameRating);

        }

        frameRating = null;

    }



    private void scheduleSecondaryExecution(String secondaryExec, int delaySeconds) {

        if (winHandler != null) {

            winHandler.execWithDelay(secondaryExec, delaySeconds);

            Log.d("XServerDisplayActivity", "Scheduled secondary execution: " + secondaryExec + " with delay: " + delaySeconds);

        } else {

            Log.e("XServerDisplayActivity", "WinHandler is null, cannot schedule secondary execution.");

        }

    }



    public String getScreenEffectProfile() {

        return screenEffectProfile;

    }



    public void setScreenEffectProfile(String screenEffectProfile) {

        this.screenEffectProfile = screenEffectProfile;

    }



    // maybe we can remove this or maybe i will create it...

//    public void clearContainerCache(Container container){//        File rootDir = container.getRootDir();

//        final File cacheDir = new File(rootDir, ".cache");

//        FileUtils.clear(cacheDir);

//    }



}

