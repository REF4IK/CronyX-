    package com.winlator.cmod.container;

    import static com.winlator.cmod.MainActivity.PACKAGE_NAME;

    import android.graphics.Bitmap;
    import android.graphics.BitmapFactory;
    import android.os.Environment;
    import android.util.Log;

    import com.winlator.cmod.MainActivity;
    import com.winlator.cmod.core.FileUtils;
    import com.winlator.cmod.core.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
    import org.json.JSONException;
    import org.json.JSONObject;

    import java.io.File;
    import java.util.ArrayList;
    import java.util.Iterator;
    import java.util.List;
    import java.util.UUID;

    public class Shortcut {
        public final Container container;
        public final String name;
        public final String path;
        public final Bitmap icon;
        public final File file;
        public final File iconFile;
        public final String wmClass;
        private JSONObject extraData = new JSONObject();
        private Bitmap coverArt; // Changed to private to use getter method
        private String customCoverArtPath; // Path to custom cover art
        private Bitmap customIcon;
        private String customIconPath;

        private static final String COVER_ART_DIR = "app_data/cover_arts/"; // Removed leading "/" to keep it relative
        private static final String ICON_DIR = "app_data/custom_icons/";

        public Shortcut(Container container, File file) {
            this.container = container;
            this.file = file;

            String execArgs = "";
            Bitmap icon = null;
            File iconFile = null;
            String wmClass = "";

            File[] iconDirs = {container.getIconsDir(64), container.getIconsDir(48), container.getIconsDir(32), container.getIconsDir(16)};
            String section = "";

            int index;
            for (String line : FileUtils.readLines(file)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue; // Skip empty lines and comments
                if (line.startsWith("[")) {
                    section = line.substring(1, line.indexOf("]"));
                }
                else {
                    index = line.indexOf("=");
                    if (index == -1) continue;
                    String key = line.substring(0, index);
                    String value = line.substring(index+1);

                    if (section.equals("Desktop Entry")) {
                        if (key.equals("Exec")) execArgs = value;
                        if (key.equals("Icon")) {
                            for (File iconDir : iconDirs) {
                                iconFile = new File(iconDir, value+".png");
                                if (iconFile.isFile()){
                                    icon = BitmapFactory.decodeFile(iconFile.getPath());
                                    break;
                                } else {
                                    File iconIfNotFound = new File("/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.cache/wallpaper.bmp");
                                    if (iconIfNotFound.isFile()) {
                                        icon = BitmapFactory.decodeFile(iconIfNotFound.getPath());
                                    } else {
                                        icon = null;
                                    }
                                }
                            }
                        }
                        if (key.equals("StartupWMClass")) wmClass = value;
                    }
                    else if (section.equals("Extra Data")) {
                        try {
                            extraData.put(key, value);
                        }
                        catch (JSONException e) {}
                    }
                }
            }

            this.name = FileUtils.getBasename(file.getPath());
            this.icon = icon;
            this.iconFile = iconFile;
            this.path = StringUtils.unescape(execArgs.substring(execArgs.lastIndexOf("wine ") + 4));
            this.wmClass = wmClass;

            this.customCoverArtPath = getExtra("customCoverArtPath");
            this.customIconPath = getExtra("customIconPath");

            // Load cover art if available
            loadCoverArt();
            loadCustomIcon();

            Container.checkObsoleteOrMissingProperties(extraData);
        }

        private void loadCoverArt() {
            if (customCoverArtPath != null && !customCoverArtPath.isEmpty()) {
                File customCoverArtFile = new File(customCoverArtPath);
                if (customCoverArtFile.isFile()) {
                    this.coverArt = BitmapFactory.decodeFile(customCoverArtFile.getPath());
                    return;
                }
            }

            File defaultCoverArtFile = new File(new File(container.getRootDir(), COVER_ART_DIR), this.name + ".png");
            if (defaultCoverArtFile.isFile()) {
                this.coverArt = BitmapFactory.decodeFile(defaultCoverArtFile.getPath());
            }
        }

        private void loadCustomIcon() {
            if (customIconPath != null && !customIconPath.isEmpty()) {
                File customIconFile = new File(customIconPath);
                if (customIconFile.isFile()) {
                    this.customIcon = BitmapFactory.decodeFile(customIconFile.getPath());
                }
            }
        }

        // Getters and setters for coverArt and customCoverArtPath
        public Bitmap getCoverArt() {
            return coverArt;
        }

        public void setCoverArt(Bitmap coverArt) {
            this.coverArt = coverArt;
        }

        public String getCustomCoverArtPath() {
            return customCoverArtPath;
        }

        public void setCustomCoverArtPath(String customCoverArtPath) {
            this.customCoverArtPath = customCoverArtPath;
            putExtra("customCoverArtPath", customCoverArtPath); // Save the custom cover art path to extra data
            saveData(); // Save immediately to ensure persistence
            Log.d("Shortcut", "Set and saved custom cover art path: " + customCoverArtPath); // Add a log for debugging
        }

        public Bitmap getCustomIcon() {
            return customIcon;
        }

        public Bitmap getDisplayIcon() {
            return customIcon != null ? customIcon : icon;
        }

        public void saveCustomIcon(Bitmap iconBitmap) {
            try {
                File iconDir = new File(container.getRootDir(), ICON_DIR);
                if (!iconDir.exists() && !iconDir.mkdirs()) {
                    Log.e("Shortcut", "Failed to create custom icon directory: " + iconDir.getAbsolutePath());
                    return;
                }

                File iconFile = new File(iconDir, this.name + ".png");
                if (FileUtils.saveBitmapToFile(iconBitmap, iconFile)) {
                    this.customIcon = iconBitmap;
                    this.customIconPath = iconFile.getPath();
                    putExtra("customIconPath", customIconPath);
                    saveData();
                }
            } catch (Exception e) {
                Log.e("Shortcut", "Failed to save custom icon", e);
            }
        }

        public void removeCustomIcon() {
            if (customIconPath != null && !customIconPath.isEmpty()) {
                File customIconFile = new File(customIconPath);
                if (customIconFile.exists() && !customIconFile.delete()) {
                    Log.e("Shortcut", "Failed to delete custom icon file: " + customIconPath);
                }
            }
            customIcon = null;
            customIconPath = null;
            putExtra("customIconPath", null);
            saveData();
        }

        public String getExtra(String name) {
            return getExtra(name, "");
        }

        public String getExtra(String name, String fallback) {
            try {
                return extraData.has(name) ? extraData.getString(name) : fallback;
            }
            catch (JSONException e) {
                return fallback;
            }
        }

        public void putExtra(String name, String value) {
            try {
                if (value != null) {
                    extraData.put(name, value);
                }
                else extraData.remove(name);
            }
            catch (JSONException e) {}
        }

        public java.util.Iterator<String> getExtraKeys() {
            if (extraData == null) return null;
            return extraData.keys();
        }

        public void loadExtraData(JSONObject data) {
            extraData = new JSONObject();
            if (data != null) {
                java.util.Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        this.putExtra(key, data.getString(key));
                    } catch (Exception e) {}
                }
            }
        }

        public void saveData() {
            String content = "[Desktop Entry]\n";
            for (String line : FileUtils.readLines(file)) {
                if (line.contains("[Extra Data]")) break;
                if (!line.contains("[Desktop Entry]") && !line.isEmpty()) content += line + "\n";
            }

            if (extraData.length() > 0) {
                content += "\n[Extra Data]\n";
                Iterator<String> keys = extraData.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        content += key + "=" + extraData.getString(key) + "\n";
                    } catch (JSONException e) {}
                }
            }

            // Verify that the file reference is correct
            if (!file.getName().endsWith(".desktop")) {
                Log.e("Shortcut", "Incorrect file reference before saving: " + file.getPath());
                return; // Prevent saving to an incorrect file
            }

            FileUtils.writeString(file, content);
        }


        public void genUUID() {
            if (getExtra("uuid").equals("")) {
                putExtra("uuid", UUID.randomUUID().toString());
                saveData();
            }
        }

        // Save the custom cover art to the default cover art directory
        public void saveCustomCoverArt(Bitmap coverArt) {
            try {
                File coverArtDir = new File(container.getRootDir(), COVER_ART_DIR); // Ensure the path is relative to the container's root directory
                if (!coverArtDir.exists()) {
                    boolean created = coverArtDir.mkdirs();
                    if (!created) {
                        Log.e("Shortcut", "Failed to create cover art directory: " + coverArtDir.getAbsolutePath());
                    }
                }


                File coverFile = new File(coverArtDir, this.name + ".png");
                if (FileUtils.saveBitmapToFile(coverArt, coverFile)) {
                    this.coverArt = coverArt; // Update the cover art
                    setCustomCoverArtPath(coverFile.getPath()); // Update the path and save data
                    Log.d("Shortcut", "Custom cover art saved at: " + coverFile.getPath());
                } else {
                    Log.e("Shortcut", "Failed to save custom cover art.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



        public void removeCustomCoverArt() {
            if (customCoverArtPath != null && !customCoverArtPath.isEmpty()) {
                File customCoverArtFile = new File(customCoverArtPath);

                // Log the path to be deleted
                Log.d("Shortcut", "Removing custom cover art file at: " + customCoverArtPath);

                // Delete the file if it exists
                if (customCoverArtFile.exists() && customCoverArtFile.delete()) {
                    Log.d("Shortcut", "Custom cover art file deleted successfully.");
                } else {
                    Log.e("Shortcut", "Failed to delete custom cover art file or it doesn't exist.");
                }
            }

            // Reset the custom cover art path and cover art object
            this.customCoverArtPath = null;
            this.coverArt = null;

            // Remove it from extra data and save the state
            putExtra("customCoverArtPath", null);
            saveData();

            // Log the state after removal
            Log.d("Shortcut", "Shortcut state saved after removing custom cover art. Current path: " + customCoverArtPath);
        }

        public boolean cloneToContainer(Container newContainer) {
            try {
                // Define the path for the new .desktop file in the new container
                File newShortcutFile = new File(newContainer.getDesktopDir(), this.file.getName());

                // Read the existing .desktop file
                ArrayList<String> lines = FileUtils.readLines(this.file);

                // Prepare the content for the new .desktop file with updated container_id
                StringBuilder updatedContent = new StringBuilder();
                boolean containerIdFound = false;

                for (String line : lines) {
                    if (line.startsWith("container_id:")) {
                        // Update the container_id to the new container
                        updatedContent.append("container_id:").append(newContainer.id).append("\n");
                        containerIdFound = true;
                    } else {
                        updatedContent.append(line).append("\n");
                    }
                }

                // If the container_id wasn't found in the original file, add it
                if (!containerIdFound) {
                    updatedContent.append("container_id:").append(newContainer.id).append("\n");
                }

                // Write the updated content to the new .desktop file
                FileUtils.writeString(newShortcutFile, updatedContent.toString());

                // Optionally copy the icon if it exists
                if (this.iconFile != null && this.iconFile.isFile()) {
                    File newIconFile = new File(newContainer.getIconsDir(64), this.iconFile.getName());
                    FileUtils.copy(this.iconFile, newIconFile);
                }

                if (this.customIconPath != null && !this.customIconPath.isEmpty()) {
                    File customIconFile = new File(this.customIconPath);
                    if (customIconFile.isFile()) {
                        File newCustomIconDir = new File(newContainer.getRootDir(), ICON_DIR);
                        if (!newCustomIconDir.exists()) newCustomIconDir.mkdirs();
                        FileUtils.copy(customIconFile, new File(newCustomIconDir, this.name + ".png"));
                    }
                }

                return true;
            } catch (Exception e) {
                Log.e("Shortcut", "Failed to clone shortcut to new container", e);
                return false;
            }
        }


        public int getContainerId() {
            return container.id;
        }

        /** Игровые данные ярлыка, которые не зависят от контейнера и переживают перенос. */
        private static final java.util.Set<String> MOVE_KEEP_EXTRAS = new java.util.HashSet<>(java.util.Arrays.asList(
                "uuid", "customIconPath", "customCoverArtPath", "execArgs", "secondaryExec", "execDelay",
                "simTouchScreen", "forceFullscreen", "fullscreenStretched", "controlsProfile",
                "sharpnessEffect", "sharpnessLevel", "sharpnessDenoise", "savesDir"
        ));

        /**
         * Перенос ярлыка в другой контейнер: копия с новым container_id + иконки
         * и обложки, затем удаление исходного .desktop (и рядом лежащего .lnk).
         * Ключи из dropExtras выбрасываются из [Extra Data] — так чистятся
         * переопределения, несовместимые с wine другой архитектуры.
         * resetToContainerDefaults выбрасывает и все остальные переопределения —
         * ярлык полностью наследует настройки целевого контейнера.
         * @return ярлык, привязанный к новому контейнеру, или null при ошибке.
         */
        public Shortcut moveToContainer(Container newContainer, java.util.Set<String> dropExtras, boolean resetToContainerDefaults) {
            try {
                if (newContainer == null || newContainer.id == container.id) return null;
                File oldRoot = container.getRootDir();
                File newRoot = newContainer.getRootDir();

                File newShortcutFile = new File(newContainer.getDesktopDir(), this.file.getName());
                // В свежем контейнере префикса ещё нет — папку Desktop создаём
                File desktopDir = newShortcutFile.getParentFile();
                if (desktopDir != null && !desktopDir.exists() && !desktopDir.mkdirs()) {
                    Log.e("Shortcut", "Failed to create desktop dir: " + desktopDir.getPath());
                    return null;
                }
                StringBuilder updatedContent = new StringBuilder();
                boolean containerIdFound = false;
                String section = "";

                for (String line : FileUtils.readLines(this.file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("[")) {
                        section = trimmed.substring(1, trimmed.indexOf("]"));
                        updatedContent.append(line).append("\n");
                        continue;
                    }
                    if (trimmed.startsWith("container_id:")) {
                        updatedContent.append("container_id:").append(newContainer.id).append("\n");
                        containerIdFound = true;
                        continue;
                    }

                    int index = trimmed.indexOf("=");
                    if (index != -1) {
                        String key = trimmed.substring(0, index);
                        String value = trimmed.substring(index + 1);
                        boolean isExtra = section.equals("Extra Data");
                        if (isExtra && dropExtras != null && dropExtras.contains(key)) continue;
                        if (isExtra && resetToContainerDefaults && !MOVE_KEEP_EXTRAS.contains(key)) continue;

                        // Абсолютные пути внутрь старого контейнера переписываем на новый
                        if (value.startsWith(oldRoot.getAbsolutePath())) {
                            String newPath = newRoot.getAbsolutePath() + value.substring(oldRoot.getAbsolutePath().length());
                            // Файл рядом с путём ещё не скопирован — скопируем ниже
                            updatedContent.append(key).append("=").append(newPath).append("\n");
                            continue;
                        }
                    }
                    updatedContent.append(line).append("\n");
                }

                if (!containerIdFound) {
                    updatedContent.append("container_id:").append(newContainer.id).append("\n");
                }

                // Пока запись не удалась — исходник не трогаем
                if (!FileUtils.writeString(newShortcutFile, updatedContent.toString())) {
                    Log.e("Shortcut", "Failed to write shortcut to: " + newShortcutFile.getPath());
                    return null;
                }

                // Иконки и обложки
                if (this.iconFile != null && this.iconFile.isFile()) {
                    FileUtils.copy(this.iconFile, new File(newContainer.getIconsDir(64), this.iconFile.getName()));
                }

                File customIconSrc = this.customIconPath != null && !this.customIconPath.isEmpty() ? new File(this.customIconPath) : null;
                if (customIconSrc != null && customIconSrc.isFile()) {
                    File newIconDir = new File(newRoot, ICON_DIR);
                    if (!newIconDir.exists()) newIconDir.mkdirs();
                    FileUtils.copy(customIconSrc, new File(newIconDir, this.name + ".png"));
                }

                File coverArtSrc = new File(new File(oldRoot, COVER_ART_DIR), this.name + ".png");
                if (coverArtSrc.isFile()) {
                    File newCoverDir = new File(newRoot, COVER_ART_DIR);
                    if (!newCoverDir.exists()) newCoverDir.mkdirs();
                    FileUtils.copy(coverArtSrc, new File(newCoverDir, this.name + ".png"));
                }
                if (this.customCoverArtPath != null && !this.customCoverArtPath.isEmpty()) {
                    File customCoverSrc = new File(this.customCoverArtPath);
                    if (customCoverSrc.isFile() && customCoverSrc.getAbsolutePath().startsWith(oldRoot.getAbsolutePath())) {
                        File newCoverDir = new File(newRoot, COVER_ART_DIR);
                        if (!newCoverDir.exists()) newCoverDir.mkdirs();
                        FileUtils.copy(customCoverSrc, new File(newCoverDir, customCoverSrc.getName()));
                    }
                }

                // Исходник: .desktop и рядом .lnk с тем же базовым именем
                if (!this.file.delete()) {
                    Log.e("Shortcut", "Failed to delete source shortcut: " + this.file.getPath());
                }
                String baseName = this.file.getName();
                if (baseName.endsWith(".desktop")) {
                    File lnk = new File(this.file.getParentFile(), baseName.substring(0, baseName.length() - ".desktop".length()) + ".lnk");
                    if (lnk.isFile() && !lnk.delete()) {
                        Log.e("Shortcut", "Failed to delete source lnk: " + lnk.getPath());
                    }
                }

                return new Shortcut(newContainer, newShortcutFile);
            } catch (Exception e) {
                Log.e("Shortcut", "Failed to move shortcut to new container", e);
                return null;
            }
        }
         
        public String getExecutable() {
            String exe = "";
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                for (String line : lines) {
                    if (line.startsWith("Exec")) {
                        exe = line.substring(line.lastIndexOf("\\") + 1, line.length()).replaceAll("\\s+$", "");
                        break;
                    }
                }
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        
            return exe;
        }
        
        /**
         * Проверить, является ли ярлык временным (для установки компонентов)
         */
        public boolean isTemporary() {
            // Временные ярлыки компонентов начинаются с "_winlator_component_"
            return file != null && file.getName().startsWith("_winlator_component_");
        }

        /**
         * Resolves a Wine path (e.g., C:/Program Files/Game/game.exe) or an Android path to an Android File.
         */
        private File resolveWinePath(String winePath) {
            try {
                winePath = winePath.replace("\\", "/").trim();

                // If it is already an absolute Android path, return it directly
                if (winePath.startsWith("/")) {
                    File candidate = new File(winePath);
                    if (candidate.isFile()) return candidate;
                }

                if (winePath.length() >= 2 && winePath.charAt(1) == ':') {
                    String driveLetter = winePath.substring(0, 2).toLowerCase();
                    String rest = winePath.substring(2);
                    if (rest.startsWith("/")) rest = rest.substring(1);

                    // If C:, map to drive_c in container root directory
                    if (driveLetter.equals("c:")) {
                        File driveC = new File(container.getRootDir(), ".wine/drive_c");
                        File candidate = new File(driveC, rest);
                        if (candidate.isFile()) return candidate;
                    } else {
                        // Map other drive letters by checking container drives configurations
                        String driveChar = driveLetter.substring(0, 1).toUpperCase();
                        for (String[] driveInfo : container.drivesIterator()) {
                            if (driveInfo[0].equalsIgnoreCase(driveChar)) {
                                File driveDir = new File(driveInfo[1]);
                                File candidate = new File(driveDir, rest);
                                if (candidate.isFile()) return candidate;
                            }
                        }

                        // Fallback: imports write paths like D:/Games/Game.exe relative to the
                        // external storage root (/storage/emulated/0), regardless of the drive config
                        File externalRoot = Environment.getExternalStorageDirectory();
                        if (externalRoot != null) {
                            File candidate = new File(externalRoot, rest);
                            if (candidate.isFile()) return candidate;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("Shortcut", "Error resolving wine path: " + winePath, e);
            }
            return null;
        }

        /**
         * Resolve the EXE file on the Android filesystem from the .desktop Path line
         * and the shortcut's Wine path.
         */
        public File resolveExeFile() {
            try {
                String pathLine = null;
                String execLine = null;
                for (String line : FileUtils.readLines(file)) {
                    line = line.trim();
                    if (line.startsWith("Path=")) {
                        pathLine = line.substring(5).trim();
                    }
                    if (line.startsWith("Exec=")) {
                        execLine = line.substring(5).trim();
                    }
                }

                // 1. Try resolving using Exec line (most accurate)
                if (execLine != null && !execLine.isEmpty()) {
                    int wineIdx = execLine.lastIndexOf("wine ");
                    if (wineIdx != -1) {
                        String rawPath = execLine.substring(wineIdx + 5).trim();
                        if (rawPath.startsWith("\"") && rawPath.endsWith("\"") && rawPath.length() > 2) {
                            rawPath = rawPath.substring(1, rawPath.length() - 1);
                        }
                        rawPath = StringUtils.unescape(rawPath).trim();
                        File resolved = resolveWinePath(rawPath);
                        if (resolved != null && resolved.isFile()) return resolved;
                    }
                }

                // 2. Try using Path + executable filename from this.path
                if (pathLine != null && !pathLine.isEmpty()) {
                    String rootPath = container.getRootDir().getAbsolutePath();
                    if (pathLine.contains("/home/xuser")) {
                        pathLine = pathLine.replace("/home/xuser", rootPath);
                    }

                    String exeName = this.path != null ? this.path.trim() : "";
                    int lastSlash = exeName.lastIndexOf("/");
                    if (lastSlash >= 0) exeName = exeName.substring(lastSlash + 1);
                    int lastBackslash = exeName.lastIndexOf("\\");
                    if (lastBackslash >= 0) exeName = exeName.substring(lastBackslash + 1);

                    File candidate = new File(pathLine, exeName);
                    if (candidate.isFile()) return candidate;
                }

                // 3. Fallback to parsing this.path directly
                if (this.path != null && !this.path.isEmpty()) {
                    File resolved = resolveWinePath(this.path);
                    if (resolved != null && resolved.isFile()) return resolved;
                }
            } catch (Exception e) {
                Log.e("Shortcut", "Failed to resolve exe file", e);
            }
            return null;
        }

        /**
         * Extract icon from EXE file via PEParser and save it to the icons directory.
         * Returns the extracted Bitmap, or null on failure.
         */
        public Bitmap extractAndSaveIcon() {
            try {
                File exeFile = resolveExeFile();
                if (exeFile == null || !exeFile.isFile()) {
                    Log.e("Shortcut", "Could not resolve executable file to extract icon");
                    return null;
                }

                Bitmap extracted = com.winlator.cmod.win32.PEParser.extractIcon(exeFile);
                if (extracted == null) {
                    Log.e("Shortcut", "PEParser failed to extract icon from: " + exeFile.getAbsolutePath());
                    return null;
                }

                // Save to icons dir so it loads next time
                int randomNum = (int)(Math.random() * 10000);
                String iconName = randomNum + "_" + this.name + ".0";
                File iconDir = container.getIconsDir(64);
                if (!iconDir.exists()) iconDir.mkdirs();
                File savedIconFile = new File(iconDir, iconName + ".png");
                FileUtils.saveBitmapToFile(extracted, savedIconFile);

                // Update the in-memory variables immediately so they are available instantly
                this.customIcon = extracted;
                this.customIconPath = savedIconFile.getPath();
                putExtra("customIconPath", customIconPath);

                // Update the .desktop file Icon= line
                StringBuilder newContent = new StringBuilder();
                for (String line : FileUtils.readLines(file)) {
                    if (line.trim().startsWith("Icon=")) {
                        newContent.append("Icon=").append(iconName).append("\n");
                    } else {
                        newContent.append(line).append("\n");
                    }
                }
                FileUtils.writeString(file, newContent.toString());

                Log.d("Shortcut", "Extracted and saved icon from EXE: " + savedIconFile.getAbsolutePath());
                return extracted;
            } catch (Exception e) {
                Log.e("Shortcut", "Failed to extract icon from EXE", e);
                return null;
            }
        }

    }
