package com.winlator.cmod.container;

import android.os.Environment;
import android.util.Log;

import com.winlator.cmod.XrActivity;
import com.winlator.cmod.box86_64.Box86_64Preset;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xenvironment.ImageFs;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;

public class Container {
    public enum XrControllerMapping {
        BUTTON_A, BUTTON_B, BUTTON_X, BUTTON_Y, BUTTON_GRIP, BUTTON_TRIGGER,
        THUMBSTICK_UP, THUMBSTICK_DOWN, THUMBSTICK_LEFT, THUMBSTICK_RIGHT
    }
    public static final String DEFAULT_ENV_VARS = "WRAPPER_MAX_IMAGE_COUNT=0 ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=1 TU_DEBUG=noconform,sysmem DXVK_HUD=devinfo,fps,frametimes,gpuload,version,api VKD3D_SHADER_MODEL=6_6";
    public static final String DEFAULT_SCREEN_SIZE = "1280x720";
    public static final String DEFAULT_GRAPHICS_DRIVER = "wrapper";
    public static final String DEFAULT_AUDIO_DRIVER = "pulseaudio";
    public static final String DEFAULT_EMULATOR = "FEXCore";
    public static final String DEFAULT_DXWRAPPER = "dxvk";
    public static final String DEFAULT_DISPLAY_RENDERER = "vulkan";
    public static final String DEFAULT_DXWRAPPERCONFIG = "version=" + DefaultVersion.DXVK + ",framerate=0,maxDeviceMemory=0,async=0,asyncCache=0" + ",vkd3dVersion=" + DefaultVersion.VKD3D + ",vkd3dLevel=12_1";
    public static final String DEFAULT_GRAPHICSDRIVERCONFIG = "version=" + DefaultVersion.WRAPPER + ";blacklistedExtensions=" + ";maxDeviceMemory=0" + ";adrenotoolsTurnip=1" + ";frameSync=Normal" + ";presentMode=mailbox" + ";resourceType=auto" + ";bcnEmulation=auto" + ";bcnEmulationType=compute" + ";bcnEmulationCache=0" + ";blit=0" + ";vulkanVersion=1.3" + ";gpuName=Device";
    public static final String DEFAULT_DDRAWRAPPER = "wined3d";
    public static final String DEFAULT_WINCOMPONENTS = "direct3d=1,directsound=0,directmusic=0,directshow=0,directplay=0,xaudio=0,vcrun2010=1,opengl=0";
    public static final String FALLBACK_WINCOMPONENTS = "direct3d=1,directsound=1,directmusic=1,directshow=1,directplay=1,xaudio=1,vcrun2010=1,opengl=0";
    private static String defaultDrives() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String downloadPath = downloads != null ? downloads.getPath() : "/storage/emulated/0/Download";
        return "D:" + downloadPath + "E:/data/data/com.winlator.cmod/storage";
    }
    public static final String DEFAULT_DRIVES = defaultDrives();
    public static final byte STARTUP_SELECTION_NORMAL = 0;
    public static final byte STARTUP_SELECTION_ESSENTIAL = 1;
    public static final byte STARTUP_SELECTION_AGGRESSIVE = 2;
    public static final byte MAX_DRIVE_LETTERS = 26;
    // Fullscreen aspect-ratio mode (issue #71). Replaces the old fullscreenStretched boolean.
    public static final int FULLSCREEN_OFF = 0;
    public static final int FULLSCREEN_FIT = 1;
    public static final int FULLSCREEN_STRETCH = 2;
    public static final int FULLSCREEN_FILL = 3;
    public static final int FULLSCREEN_INTEGER = 4;
    private int fullscreenMode = FULLSCREEN_OFF;
    public int getFullscreenMode() { return fullscreenMode; }
    public void setFullscreenMode(int mode) { this.fullscreenMode = mode; }
    public static int nextFullscreenMode(int mode) {
        switch (mode) {
            case FULLSCREEN_OFF: return FULLSCREEN_FIT;
            case FULLSCREEN_FIT: return FULLSCREEN_STRETCH;
            case FULLSCREEN_STRETCH: return FULLSCREEN_FILL;
            case FULLSCREEN_FILL: return FULLSCREEN_INTEGER;
            case FULLSCREEN_INTEGER: return FULLSCREEN_OFF;
            default: return FULLSCREEN_OFF;
        }
    }
    public final int id;
    private String name;
    private String screenSize = DEFAULT_SCREEN_SIZE;
    private String envVars = DEFAULT_ENV_VARS;
    private String graphicsDriver = DEFAULT_GRAPHICS_DRIVER;
    private String graphicsDriverConfig = DEFAULT_GRAPHICSDRIVERCONFIG;
    private String displayRenderer = DEFAULT_DISPLAY_RENDERER;
    private boolean sfCompatMode = false;
    private String dxwrapper = DEFAULT_DXWRAPPER;
    private String ddrawrapper = DEFAULT_DDRAWRAPPER;
    private String dxwrapperConfig = "";
    private String wincomponents = DEFAULT_WINCOMPONENTS;
    private String audioDriver = DEFAULT_AUDIO_DRIVER;
    private String audioDriverConfig = "performanceMode=1,volume=1.0,latencyMillis=20";
    private String drives = DEFAULT_DRIVES;
    private String wineVersion = WineInfo.MAIN_WINE_VERSION.identifier();
    private boolean showFPS;
    private boolean fullscreenStretched;
    private boolean useUnixLibs = true;
    private boolean wow64Mode = true;
    private byte startupSelection = STARTUP_SELECTION_ESSENTIAL;
    private String cpuList;
    private String cpuListWoW64;
    private String desktopTheme = WineThemeManager.DEFAULT_DESKTOP_THEME;
    private String box64Preset = Box86_64Preset.COMPATIBILITY;
    private String fexcorePreset = FEXCorePreset.INTERMEDIATE;
    private File rootDir;
    private JSONObject extraData;
    private int rcfileId = 0;
    private String midiSoundFont = "";
    private int inputType = WinHandler.DEFAULT_INPUT_TYPE;
    private String lc_all = "";
    private int primaryController = 1;
    private String controllerMapping = new String(new char[XrControllerMapping.values().length]);
    private String fexcoreVersion = DefaultVersion.FEXCORE;
    private String box64Version = DefaultVersion.BOX64;
    private String emulator;
    private boolean isRelativeMouseMovement;

    private ContainerManager containerManager;



    public Container(int id) {
        this.id = id;
        this.name = "Container-"+id;
    }

    public Container(int id, ContainerManager containerManager) {
        this.id = id;
        this.name = "Container-"+id;
        this.containerManager = containerManager;
    }

    public ContainerManager getManager() {
        return containerManager;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScreenSize() {
        return screenSize;
    }

    public void setScreenSize(String screenSize) {
        this.screenSize = screenSize;
    }

    public String getEnvVars() {
        return envVars;
    }

    public void setEnvVars(String envVars) {
        this.envVars = envVars != null ? envVars : "";
    }

    public String getGraphicsDriver() {
        return graphicsDriver;
    }

    public void setGraphicsDriver(String graphicsDriver) {
        this.graphicsDriver = graphicsDriver;
    }

    public String getGraphicsDriverConfig() { return this.graphicsDriverConfig; }

    public void setGraphicsDriverConfig(String graphicsDriverConfig) { this.graphicsDriverConfig = graphicsDriverConfig; }

    public String getDisplayRenderer() { return displayRenderer; }

    public void setDisplayRenderer(String displayRenderer) { this.displayRenderer = displayRenderer != null ? displayRenderer : DEFAULT_DISPLAY_RENDERER; }

    public boolean getSfCompatMode() { return sfCompatMode; }

    public void setSfCompatMode(boolean sfCompatMode) { this.sfCompatMode = sfCompatMode; }

    public String getDXWrapper() {
        return dxwrapper;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public String getDDrawWrapper() { return ddrawrapper; }

    public void setDDrawWrapper(String ddrawrapper) { this.ddrawrapper = ddrawrapper; }

    public String getDXWrapperConfig() {
        return dxwrapperConfig;
    }

    public void setDXWrapperConfig(String dxwrapperConfig) {
        this.dxwrapperConfig = dxwrapperConfig != null ? dxwrapperConfig : "";
    }

    public String getAudioDriver() {
        return audioDriver;
    }

    public void setAudioDriver(String audioDriver) {
        this.audioDriver = audioDriver;
    }

    public String getAudioDriverConfig() {
        // Автоматическая миграция старого формата при чтении
        String result = audioDriverConfig.replace(';', ',');
        Log.d("Container", "getAudioDriverConfig возвращает: " + result + " (исходная строка: " + audioDriverConfig + ")");
        return result;
    }

    public void setAudioDriverConfig(String audioDriverConfig) {
        Log.d("Container", "setAudioDriverConfig вызван с: " + audioDriverConfig);
        // Автоматическая миграция старого формата с ';' на новый формат с ','
        if (audioDriverConfig != null) {
            audioDriverConfig = audioDriverConfig.replace(';', ',');
        }
        this.audioDriverConfig = audioDriverConfig != null ? audioDriverConfig : "performanceMode=1,volume=1.0,latencyMillis=20";
        Log.d("Container", "setAudioDriverConfig сохранено как: " + this.audioDriverConfig);
    }

    public String getWinComponents() {
        return wincomponents;
    }

    public void setWinComponents(String wincomponents) {
        this.wincomponents = wincomponents;
    }

    public String getDrives() {
        return drives;
    }

    public void setDrives(String drives) {
        this.drives = drives;
    }

    public String getLC_ALL() {
        return lc_all;
    }

    public void setLC_ALL(String lc_all) {
        this.lc_all = lc_all;
    }

    public int getPrimaryController() {
        return primaryController;
    }

    public void setPrimaryController(int primaryController) {
        this.primaryController = primaryController;
    }

    public byte getControllerMapping(XrControllerMapping input) {
        return (byte) controllerMapping.charAt(input.ordinal());
    }

    public void setControllerMapping(String controllerMapping) {
        this.controllerMapping = controllerMapping;
    }

    public boolean isRelativeMouseMovement() {
        return this.isRelativeMouseMovement;
    }

    public void setRelativeMouseMovement(boolean isRelativeMouseMovement) {
        this.isRelativeMouseMovement = isRelativeMouseMovement;
    }

    public boolean isFullscreenStretched() { return fullscreenMode == FULLSCREEN_STRETCH || fullscreenStretched; }

    public boolean isUseUnixLibs() { return useUnixLibs; }

    public void setUseUnixLibs(boolean useUnixLibs) { this.useUnixLibs = useUnixLibs; }

    public boolean isShowFPS() {
        return showFPS;
    }

    public void setFullscreenStretched(boolean fullscreenStretched) {
        this.fullscreenStretched = fullscreenStretched;
        this.fullscreenMode = fullscreenStretched ? FULLSCREEN_STRETCH : FULLSCREEN_OFF;
    }

    public void setShowFPS(boolean showFPS) {
        this.showFPS = showFPS;
    }

    public boolean isWoW64Mode() {
        return wow64Mode;
    }

    public void setWoW64Mode(boolean wow64Mode) {
        this.wow64Mode = wow64Mode;
    }

    public byte getStartupSelection() {
        return startupSelection;
    }

    public void setStartupSelection(byte startupSelection) {
        this.startupSelection = startupSelection;
    }

    public String getCPUList() {
        return getCPUList(false);
    }

    public String getCPUList(boolean allowFallback) {
        return cpuList != null ? cpuList : (allowFallback ? getFallbackCPUList() : null);
    }

    public void setCPUList(String cpuList) {
        this.cpuList = cpuList != null && !cpuList.isEmpty() ? cpuList : null;
    }

    public String getCPUListWoW64() {
        return getCPUListWoW64(false);
    }

    public String getCPUListWoW64(boolean allowFallback) {
        return cpuListWoW64 != null ? cpuListWoW64 : (allowFallback ? getFallbackCPUListWoW64() : null);
    }

    public void setCPUListWoW64(String cpuListWoW64) {
        this.cpuListWoW64 = cpuListWoW64 != null && !cpuListWoW64.isEmpty() ? cpuListWoW64 : null;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    public String getFEXCorePreset() {
        return fexcorePreset;
    }

    public void setFEXCorePreset(String preset) {
        this.fexcorePreset = preset;
    }

    public String getFEXCoreVersion() { return this.fexcoreVersion; }

    public void setFEXCoreVersion(String version) { this.fexcoreVersion = version; }

    public String getBox64Version() { return box64Version; }

    public void setBox64Version(String version) { this.box64Version = version; }

    public void setEmulator(String emulator) {
        this.emulator = emulator;
    }

    public String getEmulator() {
        return this.emulator;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public void setExtraData(JSONObject extraData) {
        this.extraData = extraData;
    }

    public String getExtra(String name) {
        return getExtra(name, "");
    }

    public String getExtra(String name, String fallback) {
        try {
            return extraData != null && extraData.has(name) ? extraData.getString(name) : fallback;
        }
        catch (JSONException e) {
            return fallback;
        }
    }

    public void putExtra(String name, Object value) {
        if (extraData == null) extraData = new JSONObject();
        try {
            if (value != null) {
                extraData.put(name, value);
            }
            else extraData.remove(name);
        }
        catch (JSONException e) {
            Log.e("Container", "Failed to put extra data: " + name, e);
        }
    }

    public boolean hasExtra(String name) {
        return extraData != null && extraData.has(name);
    }

    // --- Native LSFG frame generation (host-side, ported from Bannerlator
    // lsfg-native). Only engine is "off" | "lsfg-native"; the old guest-layer
    // (lsfg-vk Vulkan layer) is removed — a stale "lsfg" value migrates to it.
    public static final int FRAMEGEN_DEFAULT_MULTIPLIER = 2;
    public static final float FRAMEGEN_DEFAULT_FLOW_SCALE = 0.8f;
    // Пресет качества CronyFG: 0 эконом .. 5 максимальный (см. cronyfg_quality.h).
    public static final int FRAMEGEN_DEFAULT_QUALITY = 2;

    public boolean isFrameGenEnabled() {
        return getExtra("frameGenEnabled", "0").equals("1");
    }

    public void setFrameGenEnabled(boolean enabled) {
        putExtra("frameGenEnabled", enabled ? "1" : "0");
    }

    public String getFrameGenEngine() {
        String e = getExtra("frameGenEngine", "");
        if (e.isEmpty()) return isFrameGenEnabled() ? "lsfg-native" : "off";
        if (e.equals("lsfg") || e.equals("bionic")) return "lsfg-native";
        if (!e.equals("lsfg-native") && !e.equals("cronyfg")) return "off";
        return e;
    }

    public void setFrameGenEngine(String engine) {
        if (!"lsfg-native".equals(engine) && !"cronyfg".equals(engine)) engine = "off";
        putExtra("frameGenEngine", engine);
        setFrameGenEnabled(!engine.equals("off"));
    }

    public boolean isLsfgNative() {
        return getFrameGenEngine().equals("lsfg-native");
    }

    public boolean isCronyfg() {
        return getFrameGenEngine().equals("cronyfg");
    }

    // Allowed values: 0 (Off, set live from the in-game menu) or 2-4.
    public int getFrameGenMultiplier() {
        try {
            int m = Integer.parseInt(getExtra("frameGenMultiplier", String.valueOf(FRAMEGEN_DEFAULT_MULTIPLIER)));
            if (m == 0) return 0;
            return (m < 2 || m > 4) ? FRAMEGEN_DEFAULT_MULTIPLIER : m;
        }
        catch (NumberFormatException e) {
            return FRAMEGEN_DEFAULT_MULTIPLIER;
        }
    }

    public void setFrameGenMultiplier(int multiplier) {
        putExtra("frameGenMultiplier", String.valueOf(multiplier));
    }

    /**
     * Adaptive output-FPS target (WinNative parity). 0 = Fixed multiplier
     * mode; otherwise the engine varies generations per source frame to hit
     * min(target, panel refresh). Allowed: 0, 60, 90, 120, 144, 165.
     */
    public int getFrameGenTargetRate() {
        try {
            int t = Integer.parseInt(getExtra("frameGenTargetRate", "0"));
            if (t == 0 || t == 60 || t == 90 || t == 120 || t == 144 || t == 165) return t;
            return 0;
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setFrameGenTargetRate(int targetRate) {
        putExtra("frameGenTargetRate", String.valueOf(targetRate));
    }

    public float getFrameGenFlowScale() {
        if (!hasExtra("frameGenFlowScale")) return FRAMEGEN_DEFAULT_FLOW_SCALE;
        try {
            float f = Float.parseFloat(getExtra("frameGenFlowScale"));
            return (f < 0.25f || f > 1.0f) ? FRAMEGEN_DEFAULT_FLOW_SCALE : f;
        }
        catch (NumberFormatException e) {
            return FRAMEGEN_DEFAULT_FLOW_SCALE;
        }
    }

    public void setFrameGenFlowScale(float flowScale) {
        putExtra("frameGenFlowScale", String.valueOf(flowScale));
    }

    /** Пресет качества CronyFG (0..5). LSFG не использует — ей нужен flowScale. */
    public int getFrameGenQuality() {
        try {
            int q = Integer.parseInt(getExtra("frameGenQuality", String.valueOf(FRAMEGEN_DEFAULT_QUALITY)));
            return (q < 0 || q > 5) ? FRAMEGEN_DEFAULT_QUALITY : q;
        }
        catch (NumberFormatException e) {
            return FRAMEGEN_DEFAULT_QUALITY;
        }
    }

    public void setFrameGenQuality(int quality) {
        if (quality < 0) quality = 0;
        if (quality > 5) quality = 5;
        putExtra("frameGenQuality", String.valueOf(quality));
    }

    public String getWineVersion() {
        return wineVersion;
    }

    public void setWineVersion(String wineVersion) {
        this.wineVersion = wineVersion;
    }

    public File getConfigFile() {
        return new File(rootDir, ".container");
    }

    public File getDesktopDir() {
        return new File(rootDir, ".wine/drive_c/users/"+ImageFs.USER+"/Desktop/");
    }

    public File getStartMenuDir() {
        return new File(rootDir, ".wine/drive_c/ProgramData/Microsoft/Windows/Start Menu/");
    }

    public File getIconsDir(int size) {
        return new File(rootDir, ".local/share/icons/hicolor/"+size+"x"+size+"/apps/");
    }

    public String getDesktopTheme() {
        return desktopTheme;
    }

    public void setDesktopTheme(String desktopTheme) {
        this.desktopTheme = desktopTheme;
    }

    public int getRCFileId() {
        return rcfileId;
    }

    public void setRcfileId(int id) {
        rcfileId = id;
    }

    public String getMIDISoundFont() {
        return midiSoundFont;
    }

    public void setMidiSoundFont(String fileName) {
        midiSoundFont = fileName;
    }

    public int getInputType() {
        return inputType;
    }

    public void setInputType(int inputType) {
        this.inputType = inputType;
    }

    public Iterable<String[]> drivesIterator() {
        return drivesIterator(drives);
    }

    public static Iterable<String[]> drivesIterator(final String drives) {
        final int[] index = {drives.indexOf(":")};
        final String[] item = new String[2];
        return () -> new Iterator<String[]>() {
            @Override
            public boolean hasNext() {
                return index[0] != -1;
            }

            @Override
            public String[] next() {
                item[0] = String.valueOf(drives.charAt(index[0]-1));
                int nextIndex = drives.indexOf(":", index[0]+1);
                item[1] = drives.substring(index[0]+1, nextIndex != -1 ? nextIndex-1 : drives.length());
                index[0] = nextIndex;
                return item;
            }
        };
    }

    public void saveData() {
        try {
            JSONObject data = new JSONObject();
            data.put("id", id);
            data.put("name", name);
            data.put("screenSize", screenSize);
            data.put("envVars", envVars);
            data.put("cpuList", cpuList);
            data.put("cpuListWoW64", cpuListWoW64);
            data.put("graphicsDriver", graphicsDriver);
            data.put("graphicsDriverConfig", graphicsDriverConfig);
            data.put("displayRenderer", displayRenderer);
            data.put("sfCompatMode", sfCompatMode);
            data.put("emulator", emulator);
            data.put("dxwrapper", dxwrapper);
            data.put("ddrawrapper", ddrawrapper);
            if (!dxwrapperConfig.isEmpty()) data.put("dxwrapperConfig", dxwrapperConfig);
            data.put("audioDriver", audioDriver);
            if (!audioDriverConfig.isEmpty()) data.put("audioDriverConfig", audioDriverConfig);
            data.put("wincomponents", wincomponents);
            data.put("drives", drives);
            data.put("showFPS", showFPS);
            data.put("relativeMouseMovement", isRelativeMouseMovement);
            data.put("fullscreenMode", fullscreenMode);
            data.put("fullscreenStretched", fullscreenStretched);
            data.put("useUnixLibs", useUnixLibs);
            data.put("inputType", inputType);
            data.put("wow64Mode", wow64Mode);
            data.put("startupSelection", startupSelection);
            data.put("box64Version", box64Version);
            data.put("box64Preset", box64Preset);
            data.put("fexcoreVersion", fexcoreVersion);
            data.put("fexcorePreset", fexcorePreset);
            data.put("desktopTheme", desktopTheme);
            data.put("extraData", extraData);
            data.put("rcfileId", rcfileId);
            data.put("midiSoundFont", midiSoundFont);
            data.put("lc_all", lc_all);
            data.put("primaryController", primaryController);
            data.put("controllerMapping", controllerMapping);
            if (!WineInfo.isMainWineVersion(wineVersion)) data.put("wineVersion", wineVersion);
            FileUtils.writeString(getConfigFile(), data.toString());
        }
        catch (JSONException e) {
            Log.e("Container", "Failed to save container data to " + getConfigFile().getPath(), e);
        }
    }


    public void loadData(JSONObject data) throws JSONException {
        wineVersion = WineInfo.MAIN_WINE_VERSION.identifier();
        dxwrapperConfig = "";
        checkObsoleteOrMissingProperties(data);

        for (Iterator<String> it = data.keys(); it.hasNext(); ) {
            String key = it.next();
            switch (key) {
                case "name" :
                    setName(data.getString(key));
                    break;
                case "screenSize" :
                    setScreenSize(data.getString(key));
                    break;
                case "envVars" :
                    setEnvVars(data.getString(key));
                    break;
                case "cpuList" :
                    setCPUList(data.getString(key));
                    break;
                case "cpuListWoW64" :
                    setCPUListWoW64(data.getString(key));
                    break;
                case "graphicsDriver" :
                    setGraphicsDriver(data.getString(key));
                    break;
                case "graphicsDriverConfig" :
                    setGraphicsDriverConfig(data.getString(key));
                    break;                case "displayRenderer" :
                    setDisplayRenderer(data.getString(key));
                    break;
                case "sfCompatMode" :
                    setSfCompatMode(data.getBoolean(key));
                    break;                case "emulator":
                    setEmulator(data.getString(key));
                    break;
                case "wincomponents" :
                    setWinComponents(data.getString(key));
                    break;
                case "dxwrapper" :
                    setDXWrapper(data.getString(key));
                    break;
                case "ddrawrapper":
                    setDDrawWrapper(data.getString(key));
                case "dxwrapperConfig" :
                    setDXWrapperConfig(data.getString(key));
                    break;
                case "drives" :
                    setDrives(data.getString(key));
                    break;
                case "showFPS" :
                    setShowFPS(data.getBoolean(key));
                    break;
                case "relativeMouseMovement":
                    setRelativeMouseMovement(data.getBoolean(key));
                    break;
                case "fullscreenMode" :
                    setFullscreenMode(data.getInt(key));
                    break;
                case "fullscreenStretched" :
                    if (!data.has("fullscreenMode")) setFullscreenStretched(data.getBoolean(key));
                    break;
                case "useUnixLibs" :
                    setUseUnixLibs(data.getBoolean(key));
                    break;
                case "inputType" :
                    setInputType(data.getInt(key));
                    break;
                case "wow64Mode" :
                    setWoW64Mode(data.getBoolean(key));
                    break;
                case "startupSelection" :
                    setStartupSelection((byte)data.getInt(key));
                    break;
                case "extraData" : {
                    JSONObject extraData = data.getJSONObject(key);
                    checkObsoleteOrMissingProperties(extraData);
                    setExtraData(extraData);
                    break;
                }
                case "wineVersion" :
                    setWineVersion(data.getString(key));
                    break;
                case "box64Version":
                    setBox64Version(data.getString(key));
                    break;
                case "box64Preset" :
                    setBox64Preset(data.getString(key));
                    break;
                case "fexcoreVersion":
                    setFEXCoreVersion(data.getString(key));
                    break;
                case "fexcorePreset":
                    setFEXCorePreset(data.getString(key));
                    break;
                case "audioDriver" :
                    setAudioDriver(data.getString(key));
                    break;
                case "audioDriverConfig" :
                    setAudioDriverConfig(data.getString(key));
                    break;
                case "desktopTheme" :
                    setDesktopTheme(data.getString(key));
                    break;
                case "rcfileId" :
                    setRcfileId(data.getInt(key));
                    break;
                case "midiSoundFont" :
                    setMidiSoundFont(data.getString(key));
                    break;
                case "lc_all" :
                    setLC_ALL(data.getString(key));
                    break;
                case "primaryController" :
                    setPrimaryController(data.getInt(key));
                    break;
                case "controllerMapping" :
                    controllerMapping = data.getString(key);
                    break;
            }
        }
    }

    public static void checkObsoleteOrMissingProperties(JSONObject data) {
        try {
            if (data.has("dxcomponents")) {
                data.put("wincomponents", data.getString("dxcomponents"));
                data.remove("dxcomponents");
            }

            if (data.has("dxwrapper")) {
                String dxwrapper = data.getString("dxwrapper");
                if (dxwrapper.equals("original-wined3d")) {
                    data.put("dxwrapper", DEFAULT_DXWRAPPER);
                }
                else if (dxwrapper.startsWith("d8vk-") || dxwrapper.startsWith("dxvk-")) {
                    data.put("dxwrapper", dxwrapper);
                }
            }

            if (data.has("graphicsDriver")) {
                String graphicsDriver = data.getString("graphicsDriver");
                if (graphicsDriver.equals("turnip-zink") || graphicsDriver.equals("turnip")) {
                    data.put("graphicsDriver", "wrapper");
                }
                else if (graphicsDriver.equals("llvmpipe")) {
                    data.put("graphicsDriver", "wrapper");
                }
            }

            if (data.has("envVars") && data.has("extraData")) {
                JSONObject extraData = data.getJSONObject("extraData");
                int appVersion = Integer.parseInt(extraData.optString("appVersion", "0"));
                if (appVersion < 16) {
                    EnvVars defaultEnvVars = new EnvVars(DEFAULT_ENV_VARS);
                    EnvVars envVars = new EnvVars(data.getString("envVars"));
                    for (String name : defaultEnvVars) if (!envVars.has(name)) envVars.put(name, defaultEnvVars.get(name));
                    data.put("envVars", envVars.toString());
                }
            }

            KeyValueSet wincomponents1 = new KeyValueSet(DEFAULT_WINCOMPONENTS);
            KeyValueSet wincomponents2 = new KeyValueSet(data.getString("wincomponents"));
            String result = "";

            for (String[] wincomponent1 : wincomponents1) {
                String value = wincomponent1[1];

                for (String[] wincomponent2 : wincomponents2) {
                    if (wincomponent1[0].equals(wincomponent2[0])) {
                        value = wincomponent2[1];
                        break;
                    }
                }

                result += (!result.isEmpty() ? "," : "")+wincomponent1[0]+"="+value;
            }

            data.put("wincomponents", result);
        }
        catch (JSONException e) {
            Log.e("Container", "Failed to migrate obsolete container properties", e);
        }
    }

    public static String getFallbackCPUList() {
        int numProcessors = Runtime.getRuntime().availableProcessors();
        StringBuilder cpuList = new StringBuilder();
        for (int i = 0; i < numProcessors; i++) {
            if (i > 0) cpuList.append(",");
            cpuList.append(i);
        }
        return cpuList.toString();
    }

    public static String getFallbackCPUListWoW64() {
//        String cpuList = "";
//        int numProcessors = Runtime.getRuntime().availableProcessors();
//        for (int i = numProcessors / 2; i < numProcessors; i++) cpuList += (!cpuList.isEmpty() ? "," : "")+i;
//        return cpuList;
        return getFallbackCPUList();
    }

    // Check if a specific environment variable exists
    public boolean hasEnvVar(String keyValue) {
        if (envVars == null || envVars.isEmpty()) return false;
        String[] vars = envVars.split(",");
        for (String var : vars) {
            if (var.trim().equalsIgnoreCase(keyValue.trim())) {
                return true; // Found the variable
            }
        }
        return false;
    }

}
