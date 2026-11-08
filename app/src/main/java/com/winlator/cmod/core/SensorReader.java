package com.winlator.cmod.core;

import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SensorReader {

    private static final String TAG = "SensorReader";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Set<String> loggedFailPaths = new HashSet<>();

    private long lastCpuTotal = -1L;
    private long lastCpuIdle = -1L;
    // Предыдущие дельты /proc/stat по ядрам (для per-core загрузки в HUD).
    private long[] lastCoreTotal = null;
    private long[] lastCoreIdle = null;
    private long lastMaliGpuInfoMs = -1L;
    private long lastMaliGpuInfoWallMs = 0L;

    // Кэш discovery путей (по образцу HudMetrics из Bannerlator) — GPU/thermal зоны не меняются на лету.
    private List<String> gpuLoadPathsCache = null;
    private List<String[]> thermalZonesCache = null; // каждая запись: {type, tempPath}

    // Сглаженный остаток времени от батареи (charge_counter / current_now).
    private Double smoothedBatteryRuntimeHours = null;
    private static final double MAX_RUNTIME_HOURS = 72.0;
    private static final double RUNTIME_SMOOTHING_OLD_WEIGHT = 0.65;
    private static final double RUNTIME_SMOOTHING_NEW_WEIGHT = 0.35;

    private interface ThermalZoneRanker { Integer rank(String type); }

    public String getCpuTemperature() {
        for (String path : SystemSensorPaths.CPU_TEMP_FILES) {
            String temperature = parseTemperature(readFirstLine(path));
            if (temperature != null) {
                return temperature;
            }
        }
        String dynamicTemperature = getDynamicCpuTemperature();
        if (dynamicTemperature != null) {
            return dynamicTemperature;
        }
        String hwmonTemp = getHwmonCpuTemperature();
        if (hwmonTemp != null) {
            return hwmonTemp;
        }
        return "N/A";
    }

    public String getGpuTemperature() {
        for (String path : SystemSensorPaths.GPU_TEMP_FILES) {
            String temperature = parseTemperature(readFirstLine(path));
            if (temperature != null) {
                return temperature;
            }
        }
        String dynamic = getDynamicGpuTemperature();
        if (!"N/A".equals(dynamic)) return dynamic;
        String hwmonTemp = getHwmonGpuTemperature();
        if (hwmonTemp != null) return hwmonTemp;
        return "N/A";
    }

    public String getGpuLoad() {
        for (String path : discoverGpuLoadPaths()) {
            String parsed = parseGpuLoadFile(path);
            if (parsed != null) return parsed;
        }
        return "N/A";
    }

    /**
     * Дискавери читаемых GPU-узлов с кэшем: статические пути → walk /sys/devices/platform
     * (vendor-узлы с хэшированными именами типа 13000000.mali, gpusysfs, panfrost, sgpu) →
     * walk /sys/class/devfreq + /sys/devices/virtual/devfreq. По образцу HudMetrics.
     */
    private List<String> discoverGpuLoadPaths() {
        if (gpuLoadPathsCache != null) return gpuLoadPathsCache;
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        for (String p : SystemSensorPaths.GPU_LOAD_FILES) {
            if (new File(p).canRead()) candidates.add(p);
        }

        File platformDir = new File(SystemSensorPaths.PLATFORM_DIR);
        File[] platformNodes = platformDir.listFiles(File::isDirectory);
        if (platformNodes != null) {
            for (File node : platformNodes) {
                String name = node.getName().toLowerCase(Locale.ENGLISH);
                boolean looksLikeGpu = false;
                for (String t : SystemSensorPaths.GPU_NODE_TOKENS) {
                    if (name.contains(t)) { looksLikeGpu = true; break; }
                }
                if (!looksLikeGpu) continue;
                for (String fileName : SystemSensorPaths.GPU_LOAD_FILE_NAMES) {
                    File f = new File(node, fileName);
                    if (f.canRead()) candidates.add(f.getPath());
                }
            }
        }

        File[] devfreqRoots = {
                new File(SystemSensorPaths.DEVFREQ_DIR),
                new File(SystemSensorPaths.VIRTUAL_DEVFREQ_DIR),
        };
        for (File root : devfreqRoots) {
            if (!root.isDirectory()) continue;
            File[] nodeDirs = root.listFiles(File::isDirectory);
            if (nodeDirs == null) continue;
            for (File node : nodeDirs) {
                String nodePath = node.getPath().toLowerCase(Locale.ENGLISH);
                boolean looksLikeGpu = false;
                for (String t : SystemSensorPaths.GPU_NODE_TOKENS) {
                    if (nodePath.contains(t)) { looksLikeGpu = true; break; }
                }
                for (String fileName : SystemSensorPaths.GPU_LOAD_FILE_NAMES) {
                    File f = new File(node, fileName);
                    if (!f.canRead()) continue;
                    if (looksLikeGpu || fileName.equals("gpu_busy_percentage")
                            || fileName.equals("gpu_busy_percent") || fileName.equals("gpuinfo")) {
                        candidates.add(f.getPath());
                    }
                }
            }
        }

        gpuLoadPathsCache = new ArrayList<>(candidates);
        return gpuLoadPathsCache;
    }

    /**
     * Парсер по имени файла: gpubusy — busy/total; gpuinfo (Mali) — delta ms против wall-clock;
     * остальные — процентное число.
     */
    private String parseGpuLoadFile(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        switch (fileName) {
            case "gpubusy": {
                String raw = readFirstLine(path);
                if (raw == null) return null;
                String[] parts = raw.trim().split("\\s+");
                if (parts.length < 2) return null;
                Long busy = parseLong(parts[0]);
                Long total = parseLong(parts[1]);
                if (busy == null || total == null || total <= 0L) return null;
                int pct = (int) Math.max(0L, Math.min(100L, (busy * 100L) / total));
                return String.format(Locale.ENGLISH, "%d%%", pct);
            }
            case "gpuinfo":
                return getMaliDeltaGpuLoad(path);
            default:
                return parseGpuLoad(readFirstLine(path));
        }
    }

    private String getMaliDeltaGpuLoad(String path) {
        String raw = readNthLine(path, 1);
        if (raw == null) return null;
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 0) return null;
        long gpuMs;
        try { gpuMs = Long.parseLong(parts[parts.length - 1]); } catch (NumberFormatException e) { return null; }
        long now = SystemClock.elapsedRealtime();
        long prevMs = lastMaliGpuInfoMs;
        long prevWall = lastMaliGpuInfoWallMs;
        lastMaliGpuInfoMs = gpuMs;
        lastMaliGpuInfoWallMs = now;
        if (prevMs < 0 || prevWall <= 0) return null;
        long wallDelta = now - prevWall;
        if (wallDelta <= 0) return null;
        long gpuDelta = Math.max(0L, gpuMs - prevMs);
        int pct = (int) Math.min(100L, (gpuDelta * 100L) / wallDelta);
        return String.format(Locale.ENGLISH, "%d%%", pct);
    }

    public String getCpuLoad() {
        CpuTimes cpuTimes = readCpuTimes();
        if (cpuTimes == null) {
            // /proc/stat может быть ограничен (некоторые вендоры) — fallback по частотам ядер.
            return getCpuLoadFromFrequency();
        }

        if (lastCpuTotal < 0L || lastCpuIdle < 0L) {
            lastCpuTotal = cpuTimes.total;
            lastCpuIdle = cpuTimes.idle;
            return "0%";
        }

        long totalDelta = cpuTimes.total - lastCpuTotal;
        long idleDelta = cpuTimes.idle - lastCpuIdle;
        lastCpuTotal = cpuTimes.total;
        lastCpuIdle = cpuTimes.idle;

        if (totalDelta <= 0L || idleDelta < 0L) {
            return "0%";
        }
        float usage = ((totalDelta - idleDelta) * 100f) / totalDelta;
        usage = Math.max(0f, Math.min(100f, usage));
        return String.format(Locale.ENGLISH, "%.0f%%", usage);
    }

    /**
     * Загрузка по ядрам: дельты строк cpu0..N из /proc/stat. Индекс берётся из имени строки,
     * поэтому выпавшее (offline) ядро не смещает остальные. -1 в слоте = данных нет
     * (первый сэмпл или offline-ядро). Fallback при недоступном /proc/stat — оценка по частотам.
     */
    public float[] getCpuCoreLoads() {
        File file = new File(SystemSensorPaths.CPU_PROC_STAT);
        if (!file.exists()) {
            return getCpuCoreLoadsFromFrequency();
        }
        long[] totals = null;
        long[] idles = null;
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("cpu") || line.length() < 4 || !Character.isDigit(line.charAt(3))) {
                    continue;
                }
                int digitsEnd = 3;
                while (digitsEnd < line.length() && Character.isDigit(line.charAt(digitsEnd))) {
                    digitsEnd++;
                }
                int coreIndex;
                try {
                    coreIndex = Integer.parseInt(line.substring(3, digitsEnd));
                } catch (NumberFormatException e) {
                    continue;
                }
                CpuTimes times = parseCpuTimes(line);
                if (times == null) continue;
                if (totals == null) {
                    int cores = Math.max(Runtime.getRuntime().availableProcessors(), coreIndex + 1);
                    totals = new long[cores];
                    idles = new long[cores];
                    Arrays.fill(totals, -1L);
                    Arrays.fill(idles, -1L);
                }
                if (coreIndex >= totals.length) {
                    long[] newTotals = new long[coreIndex + 1];
                    long[] newIdles = new long[coreIndex + 1];
                    Arrays.fill(newTotals, -1L);
                    Arrays.fill(newIdles, -1L);
                    System.arraycopy(totals, 0, newTotals, 0, totals.length);
                    System.arraycopy(idles, 0, newIdles, 0, idles.length);
                    totals = newTotals;
                    idles = newIdles;
                }
                totals[coreIndex] = times.total;
                idles[coreIndex] = times.idle;
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to read per-core CPU load: " + e.getMessage());
            return getCpuCoreLoadsFromFrequency();
        }
        if (totals == null) {
            return getCpuCoreLoadsFromFrequency();
        }

        if (lastCoreTotal == null || lastCoreTotal.length != totals.length) {
            lastCoreTotal = totals;
            lastCoreIdle = idles;
            float[] result = new float[totals.length];
            Arrays.fill(result, -1f);
            return result;
        }
        float[] result = new float[totals.length];
        for (int i = 0; i < totals.length; i++) {
            if (totals[i] < 0L || lastCoreTotal[i] < 0L) {
                result[i] = -1f;
                continue;
            }
            long totalDelta = totals[i] - lastCoreTotal[i];
            long idleDelta = idles[i] - lastCoreIdle[i];
            if (totalDelta <= 0L || idleDelta < 0L) {
                result[i] = 0f;
            } else {
                float usage = ((totalDelta - idleDelta) * 100f) / totalDelta;
                result[i] = Math.max(0f, Math.min(100f, usage));
            }
        }
        lastCoreTotal = totals;
        lastCoreIdle = idles;
        return result;
    }

    /** Оценка загрузки каждого ядра по частоте (fallback, когда /proc/stat недоступен). */
    private float[] getCpuCoreLoadsFromFrequency() {
        int cores = Runtime.getRuntime().availableProcessors();
        float[] result = new float[cores];
        for (int i = 0; i < cores; i++) {
            Long cur = readLong(SystemSensorPaths.cpuCurFreqPath(i));
            Long max = readLong(SystemSensorPaths.cpuMaxFreqPath(i));
            if (cur == null || max == null || max <= 0L) {
                result[i] = -1f;
            } else {
                result[i] = Math.max(0f, Math.min(100f, (cur * 100f) / max));
            }
        }
        return result;
    }

    /** Текущая частота GPU в МГц строкой, "" если недоступна. Частота в sysfs — в Гц (как в GPUPerformanceManager). */
    public String getGpuFrequencyMhz() {
        long khz = readGpuCurFreqKhz();
        if (khz <= 0L) return "";
        long mhz = khz / 1000000L;
        if (mhz <= 0L) return "";
        return String.format(Locale.ENGLISH, "%d", mhz);
    }

    private long readGpuCurFreqKhz() {
        Long value = readLong(SystemSensorPaths.GPU_CUR_FREQ_PATH);
        if (value == null || value <= 0L) {
            for (String path : SystemSensorPaths.GPU_CUR_FREQ_ALTERNATIVES) {
                value = readLong(path);
                if (value != null && value > 0L) break;
            }
        }
        return value != null ? value : 0L;
    }

    public float getBatteryPowerWatts(BatteryManager batteryManager, float batteryVoltage, float batteryCurrent) {
        float directPower = readPowerWattsFromSysfs();
        if (directPower != -1.0f) {
            return directPower;
        }

        float voltage = batteryVoltage != -1.0f ? batteryVoltage : readVoltageVoltsFromSysfs();
        float current = batteryCurrent != -1.0f ? batteryCurrent : readCurrentAmpsFromSysfs(batteryManager);
        if (voltage != -1.0f && current != -1.0f) {
            return voltage * current;
        }
        return -1.0f;
    }

    public float readCurrentAmpsFromSysfs(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (currentMicroAmps != Integer.MIN_VALUE) {
                return Math.abs(currentMicroAmps) / 1000000.0f;
            }
        }
        for (String path : SystemSensorPaths.BATTERY_CURRENT_FILES) {
            Long value = readLong(path);
            if (value != null && value != 0L) {
                long absValue = Math.abs(value);
                return absValue > 1000 ? absValue / 1000000.0f : absValue / 1000.0f;
            }
        }
        return -1.0f;
    }

    /** Заряд батареи 0..100, или -1 если недоступен. BatteryManager-property + sysfs fallback. */
    public int getBatteryPercent(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (capacity >= 0 && capacity <= 100) return capacity;
        }
        for (String path : new String[]{
                "/sys/class/power_supply/battery/capacity",
                "/sys/class/power_supply/bms/capacity",
                "/sys/class/power_supply/main/capacity"}) {
            Long v = readLong(path);
            if (v != null && v >= 0L && v <= 100L) return v.intValue();
        }
        return -1;
    }

    /** Статус батареи (BatteryManager.BATTERY_STATUS_*), или UNKNOWN. Property API 28+ + sysfs fallback. */
    public int getBatteryStatus(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            if (status != Integer.MIN_VALUE) return status;
        }
        String raw = readFirstLine("/sys/class/power_supply/battery/status");
        if (raw != null) {
            String s = raw.trim().toLowerCase(Locale.ENGLISH);
            if (s.contains("charging")) return BatteryManager.BATTERY_STATUS_CHARGING;
            if (s.contains("full")) return BatteryManager.BATTERY_STATUS_FULL;
            if (s.contains("discharging")) return BatteryManager.BATTERY_STATUS_DISCHARGING;
            if (s.contains("not charging")) return BatteryManager.BATTERY_STATUS_NOT_CHARGING;
        }
        return BatteryManager.BATTERY_STATUS_UNKNOWN;
    }

    public boolean isCharging(BatteryManager batteryManager) {
        int status = getBatteryStatus(batteryManager);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /**
     * Оценка остатка времени от батареи: charge_counter / current_now, сглаженная экспоненциально
     * (по образцу GameNative PerformanceHudView). "LEFT CHG" при зарядке, null если данных нет.
     */
    public String getBatteryRuntimeText(BatteryManager batteryManager) {
        if (batteryManager == null) return null;
        if (isCharging(batteryManager)) {
            smoothedBatteryRuntimeHours = null;
            return "LEFT CHG";
        }
        long chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long currentMicroAmps = Math.abs(batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        if (chargeCounter == Long.MIN_VALUE || chargeCounter <= 0L) chargeCounter = readSysfsChargeCounter();
        if (currentMicroAmps == Long.MIN_VALUE || currentMicroAmps <= 0L) {
            currentMicroAmps = Math.abs(readSysfsCurrentMicroAmps());
        }
        if (currentMicroAmps <= 0L || chargeCounter <= 0L) return null;
        double rawHours = (double) chargeCounter / (double) currentMicroAmps;
        if (!Double.isFinite(rawHours) || rawHours <= 0.0 || rawHours > MAX_RUNTIME_HOURS) return null;
        double smoothed = smoothedBatteryRuntimeHours != null
                ? (smoothedBatteryRuntimeHours * RUNTIME_SMOOTHING_OLD_WEIGHT) + (rawHours * RUNTIME_SMOOTHING_NEW_WEIGHT)
                : rawHours;
        smoothedBatteryRuntimeHours = smoothed;
        return "LEFT " + formatRuntimeHours(smoothed);
    }

    private long readSysfsChargeCounter() {
        for (String path : new String[]{
                "/sys/class/power_supply/battery/charge_counter",
                "/sys/class/power_supply/bms/charge_counter",
                "/sys/class/power_supply/main/charge_counter"}) {
            Long v = readLong(path);
            if (v != null && v > 0L) return v;
        }
        return -1L;
    }

    private long readSysfsCurrentMicroAmps() {
        for (String path : SystemSensorPaths.BATTERY_CURRENT_FILES) {
            Long v = readLong(path);
            if (v != null && v != 0L) return Math.abs(v);
        }
        return -1L;
    }

    private static String formatRuntimeHours(double hours) {
        int totalMinutes = (int) Math.round(hours * 60);
        if (totalMinutes < 60) return totalMinutes + "m";
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        return h + "h " + m + "m";
    }

    public void resetBatteryRuntimeState() {
        smoothedBatteryRuntimeHours = null;
    }

    public void resetCpuState() {
        lastCpuTotal = -1L;
        lastCpuIdle = -1L;
        lastCoreTotal = null;
        lastCoreIdle = null;
    }

    public void resetMaliState() {
        lastMaliGpuInfoMs = -1L;
        lastMaliGpuInfoWallMs = 0L;
    }

    public void reset() {
        resetCpuState();
        resetMaliState();
        resetBatteryRuntimeState();
    }

    public static Float parseTempCelsius(String formatted) {
        if (formatted == null || formatted.isEmpty() || "N/A".equals(formatted)) return null;
        try {
            String num = formatted.replace("C", "").replace("\u00B0", "").trim();
            return Float.parseFloat(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Float parsePercent(String formatted) {
        if (formatted == null || formatted.isEmpty() || "N/A".equals(formatted)) return null;
        try {
            return Float.parseFloat(formatted.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Thermal zones с приоритетом (по образцу HudMetrics): cpu-silicon > cpu-0 > cpuss/mtktscpu/cpu >
     * s5p-tmu > soc > cputop > tsens > cluster > big/little. GPU-зоны исключены.
     */
    private String getDynamicCpuTemperature() {
        return readTemperatureFromPaths(prioritizeThermalPaths(type -> {
            if (type.contains("gpu")) return null;             // никогда не путать GPU-зону с CPU
            if (type.contains("cpu-silicon")) return 0;
            if (type.contains("cpu-0")) return 1;
            if (type.contains("cpuss")) return 2;              // Qualcomm cpuss composite
            if (type.contains("mtktscpu")) return 2;           // MediaTek CPU thermal
            if (type.contains("cpu")) return 2;                // cpu, cpu-*-usr, cpu_thermal, cpu_center
            if (type.contains("s5p-tmu")) return 3;            // Exynos TMU
            if (type.contains("soc")) return 4;
            if (type.contains("cputop")) return 5;
            if (type.contains("tsens")) return 6;              // Qualcomm tsens aggregate
            if (type.contains("cluster")) return 7;
            if (type.contains("big") || type.contains("little")) return 8;
            return null;
        }));
    }

    /**
     * GPU-температура: прямые статические пути (kgsl/mali) → thermal zones с приоритетом
     * (gpu-silicon > gpuss/mtktsgpu > gpu-virt > gpu > g3d > kgsl > mali > xclipse) →
     * devfreq-узлы GPU (temp / temperature).
     */
    private String getDynamicGpuTemperature() {
        ArrayList<String> paths = new ArrayList<>();
        for (String p : SystemSensorPaths.GPU_TEMP_FILES) paths.add(p);
        paths.addAll(prioritizeThermalPaths(type -> {
            if (type.contains("gpu-silicon")) return 0;
            if (type.contains("gpuss")) return 1;              // Qualcomm gpuss
            if (type.contains("mtktsgpu")) return 1;           // MediaTek GPU thermal
            if (type.contains("gpu-virt")) return 2;
            if (type.contains("gpu")) return 2;
            if (type.contains("g3d")) return 3;                // Exynos Mali (g3d)
            if (type.contains("kgsl")) return 4;
            if (type.contains("mali")) return 5;
            if (type.contains("xclipse") || type.contains("sgpu")) return 6;
            return null;
        }));
        File[] devfreqRoots = {
                new File(SystemSensorPaths.DEVFREQ_DIR),
                new File(SystemSensorPaths.VIRTUAL_DEVFREQ_DIR),
        };
        for (File root : devfreqRoots) {
            File[] entries = root.listFiles();
            if (entries == null) continue;
            for (File entry : entries) {
                String name = entry.getName().toLowerCase(Locale.ENGLISH);
                boolean looksLikeGpu = false;
                for (String t : SystemSensorPaths.GPU_NODE_TOKENS) {
                    if (name.contains(t)) { looksLikeGpu = true; break; }
                }
                if (!looksLikeGpu) continue;
                paths.add(new File(entry, "temp").getAbsolutePath());
                paths.add(new File(entry, "temperature").getAbsolutePath());
            }
        }
        String result = readTemperatureFromPaths(paths);
        return result != null ? result : "N/A";
    }

    /**
     * Читает все thermal zones из обоих корней (/sys/class/thermal + /sys/devices/virtual/thermal),
     * кэширует пары {type, tempPath}.
     */
    private List<String[]> discoverAllThermalZones() {
        if (thermalZonesCache != null) return thermalZonesCache;
        ArrayList<String[]> zones = new ArrayList<>();
        LinkedHashSet<String> seenPaths = new LinkedHashSet<>();
        File[] thermalDirs = {
                new File(SystemSensorPaths.THERMAL_ZONE_DIR),
                new File(SystemSensorPaths.VIRTUAL_THERMAL_DIR),
        };
        for (File dir : thermalDirs) {
            File[] zoneDirs = dir.listFiles((d, name) -> name.startsWith("thermal_zone"));
            if (zoneDirs == null) continue;
            for (File zone : zoneDirs) {
                if (!zone.isDirectory()) continue;
                String type = readFirstLine(new File(zone, "type").getAbsolutePath());
                if (type == null) continue;
                type = type.trim().toLowerCase(Locale.ENGLISH);
                String tempPath = new File(zone, "temp").getAbsolutePath();
                if (seenPaths.add(tempPath)) zones.add(new String[]{type, tempPath});
            }
        }
        thermalZonesCache = zones;
        return zones;
    }

    /** Сортирует зоны по рангу (меньше = важнее), затем по пути для детерминизма. */
    private List<String> prioritizeThermalPaths(ThermalZoneRanker ranker) {
        List<String[]> zones = discoverAllThermalZones();
        ArrayList<int[]> order = new ArrayList<>(); // {index, rank}
        for (int i = 0; i < zones.size(); i++) {
            Integer r = ranker.rank(zones.get(i)[0]);
            if (r != null) order.add(new int[]{i, r});
        }
        order.sort((a, b) -> {
            if (a[1] != b[1]) return Integer.compare(a[1], b[1]);
            return zones.get(a[0])[1].compareTo(zones.get(b[0])[1]);
        });
        ArrayList<String> result = new ArrayList<>();
        for (int[] e : order) result.add(zones.get(e[0])[1]);
        return result;
    }

    private String readTemperatureFromPaths(List<String> paths) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String path : paths) {
            if (!seen.add(path)) continue;
            String temp = parseTemperature(readFirstLine(path));
            if (temp != null) return temp;
        }
        return null;
    }

    public int getMediaTekGpuFreq() {
        String raw = readFile(SystemSensorPaths.MEDIATEK_GPU_FREQ_DUMP);
        if (raw == null || raw.isEmpty()) return -1;
        Pattern p = Pattern.compile("(?:g_freq_new_init_keep|g_cur_gpu_freq)\\s+=\\s+(\\d+)");
        for (String line : raw.split("\n")) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { break; }
            }
        }
        return -1;
    }

    public String getCpuPresent() {
        return readFile(SystemSensorPaths.CPU_PRESENT_PATH);
    }

    public String getCpuCoreCtlState() {
        return readFile(SystemSensorPaths.CPU_CORE_CTL_GLOBAL_STATE);
    }

    private String getHwmonCpuTemperature() {
        File hwmonDir = new File(SystemSensorPaths.HWMON_DIR);
        File[] entries = hwmonDir.listFiles();
        if (entries == null) return null;
        for (File entry : entries) {
            if (!entry.getName().startsWith("hwmon")) continue;
            String name = readFirstLine(new File(entry, "name").getAbsolutePath());
            if (name != null) {
                String nameLower = name.toLowerCase(Locale.ENGLISH);
                if (nameLower.contains("cpu") || nameLower.contains("core") || nameLower.contains("temp") || nameLower.contains("soc")) {
                    String temp = parseHwmonTemp(entry);
                    if (temp != null) return temp;
                }
            }
        }
        return null;
    }

    private String getHwmonGpuTemperature() {
        File hwmonDir = new File(SystemSensorPaths.HWMON_DIR);
        File[] entries = hwmonDir.listFiles();
        if (entries == null) return null;
        for (File entry : entries) {
            if (!entry.getName().startsWith("hwmon")) continue;
            String name = readFirstLine(new File(entry, "name").getAbsolutePath());
            if (name != null) {
                String nameLower = name.toLowerCase(Locale.ENGLISH);
                if (nameLower.contains("gpu") || nameLower.contains("mali") || nameLower.contains("kgsl") || nameLower.contains("g3d")) {
                    String temp = parseHwmonTemp(entry);
                    if (temp != null) return temp;
                }
            }
        }
        return null;
    }

    private String parseHwmonTemp(File hwmonEntry) {
        String temp = parseTemperature(readFirstLine(new File(hwmonEntry, "temp1_input").getAbsolutePath()));
        if (temp != null) return temp;
        File deviceDir = new File(hwmonEntry, "device");
        if (deviceDir.exists()) {
            temp = parseTemperature(readFirstLine(new File(deviceDir, "temp1_input").getAbsolutePath()));
            if (temp != null) return temp;
        }
        return null;
    }

    private String readFile(String path) {
        File file = new File(path);
        if (!file.exists()) return null;
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Оценка CPU load по частотам ядер: сумма scaling_cur_freq / сумма cpuinfo_max_freq.
     * Используется как fallback, когда /proc/stat недоступен (по образцу HudMetrics).
     */
    private String getCpuLoadFromFrequency() {
        long currentTotal = 0L, maxTotal = 0L;
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cores; i++) {
            Long cur = readLong(SystemSensorPaths.cpuCurFreqPath(i));
            Long max = readLong(SystemSensorPaths.cpuMaxFreqPath(i));
            if (cur != null && max != null && max > 0L) {
                currentTotal += Math.max(0L, Math.min(cur, max));
                maxTotal += max;
            }
        }
        if (maxTotal <= 0L) return "N/A";
        int pct = (int) Math.max(0L, Math.min(100L, (currentTotal * 100L) / maxTotal));
        return String.format(Locale.ENGLISH, "%d%%", pct);
    }

    private CpuTimes readCpuTimes() {
        File file = new File(SystemSensorPaths.CPU_PROC_STAT);
        if (!file.exists()) {
            return null;
        }
        CpuTimes summedCores = null;
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("cpu ")) {
                    return parseCpuTimes(line);
                }
                if (line.startsWith("cpu") && line.length() > 3 && Character.isDigit(line.charAt(3))) {
                    CpuTimes coreTimes = parseCpuTimes(line);
                    if (coreTimes != null) {
                        summedCores = summedCores == null
                                ? coreTimes
                                : new CpuTimes(summedCores.total + coreTimes.total, summedCores.idle + coreTimes.idle);
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to read CPU load from /proc/stat: " + e.getMessage());
        }
        return summedCores;
    }

    private static CpuTimes parseCpuTimes(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 5) {
            return null;
        }
        try {
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0L;
            long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0L;
            long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0L;
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0L;

            long idleAll = idle + iowait;
            long total = user + nice + system + idle + iowait + irq + softirq + steal;
            return new CpuTimes(total, idleAll);
        } catch (NumberFormatException e) {
            Log.d(TAG, "Failed to parse CPU load: " + raw);
            return null;
        }
    }

    static String parseTemperature(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            int temperature = Integer.parseInt(rawValue.trim());
            if (temperature > 100 && temperature <= 1000) {
                return String.format(Locale.ENGLISH, "%.1fC", temperature / 10.0f);
            }
            if (temperature > 1000 && temperature <= 10000) {
                return String.format(Locale.ENGLISH, "%.1fC", temperature / 100.0f);
            }
            if (temperature > 10000) {
                return String.format(Locale.ENGLISH, "%.1fC", (temperature + 500) / 1000.0f);
            }
            if (temperature > 0 && temperature <= 100) {
                return String.format(Locale.ENGLISH, "%dC", temperature);
            }
        } catch (NumberFormatException e) {
            Log.d(TAG, "Failed to parse temperature: " + rawValue);
        }
        return null;
    }

    static String parseGpuLoad(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(rawValue);
        long[] numbers = new long[4];
        int count = 0;
        while (matcher.find() && count < numbers.length) {
            numbers[count++] = Long.parseLong(matcher.group(1));
        }

        if (count == 0) {
            return null;
        }

        if (count >= 2 && numbers[1] > 0 && numbers[0] <= numbers[1]) {
            float usage = (numbers[0] * 100f) / numbers[1];
            usage = Math.max(0f, Math.min(100f, usage));
            return String.format(Locale.ENGLISH, "%.0f%%", usage);
        }

        long value = numbers[0];
        if (value >= 0 && value <= 100) {
            return String.format(Locale.ENGLISH, "%d%%", value);
        }

        return null;
    }

    private float readPowerWattsFromSysfs() {
        for (String path : SystemSensorPaths.BATTERY_POWER_FILES) {
            Long value = readLong(path);
            if (value != null && value != 0L) {
                long absValue = Math.abs(value);
                return absValue > 1000 ? absValue / 1000000.0f : absValue;
            }
        }
        return -1.0f;
    }

    private float readVoltageVoltsFromSysfs() {
        for (String path : SystemSensorPaths.BATTERY_VOLTAGE_FILES) {
            Long value = readLong(path);
            if (value != null && value != 0L) {
                long absValue = Math.abs(value);
                return absValue > 10000 ? absValue / 1000000.0f : absValue / 1000.0f;
            }
        }
        return -1.0f;
    }

    private Long readLong(String path) {
        String raw = readFirstLine(path);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            Log.d(TAG, "Failed to parse long from " + path + ": " + raw);
            return null;
        }
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String readFirstLine(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            return reader.readLine();
        } catch (Exception e) {
            if (loggedFailPaths.add(path)) {
                Log.d(TAG, "Failed to read " + path + ": " + e.getMessage());
            }
            return null;
        }
    }

    static String readNthLine(String path, int lineIndex) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            String line = null;
            for (int i = 0; i <= lineIndex; i++) {
                line = br.readLine();
                if (line == null) break;
            }
            br.close();
            return line;
        } catch (Exception e) {
            return null;
        }
    }

    private static class CpuTimes {
        final long total;
        final long idle;

        CpuTimes(long total, long idle) {
            this.total = total;
            this.idle = idle;
        }
    }
}
