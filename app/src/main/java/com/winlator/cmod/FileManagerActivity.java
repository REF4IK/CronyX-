package com.winlator.cmod;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GameTitleParser;
import com.winlator.cmod.win32.PEParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FileManagerActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 123;

    public static final String EXTRA_START_PATH = "startPath";
    public static final String EXTRA_ROOT_PATH = "rootPath";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BROWSE_MODE = "browseMode";
    public static final String EXTRA_CONTAINER_ID = "containerId";

    private enum SortBy { NAME, DATE, SIZE }

    private enum EntryType { DRIVE, DIRECTORY, FILE }

    private static final class FileEntry {
        private final EntryType type;
        private final String title;
        private final String subtitle;
        private final File file;
        private final long modifiedAt;
        private final long size;

        private FileEntry(EntryType type, String title, String subtitle, File file, long modifiedAt, long size) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.file = file;
            this.modifiedAt = modifiedAt;
            this.size = size;
        }
    }

    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView currentPathView;
    private GridLayoutManager layoutManager;
    private ExplorerAdapter adapter;

    private final List<FileEntry> allEntries = new ArrayList<>();
    private final List<FileEntry> filteredEntries = new ArrayList<>();

    private File currentDir;
    private File currentRootDir;
    private String currentQuery = "";
    private String[] allowedExtensions;
    private boolean browseMode;
    private boolean gridMode;
    private SortBy sortBy = SortBy.NAME;

    private Container activeContainer;
    private boolean showingDriveRoot;

    private File clipboardFile;
    private boolean clipboardMove;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        allowedExtensions = getIntent().getStringArrayExtra("allowedExtensions");
        browseMode = getIntent().getBooleanExtra(EXTRA_BROWSE_MODE, false);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            getSupportActionBar().setTitle(title != null && !title.isEmpty() ? title : getString(R.string.fm_title));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentPathView = findViewById(R.id.TVCurrentPath);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);

        layoutManager = new GridLayoutManager(this, 1);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ExplorerAdapter();
        recyclerView.setAdapter(adapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!navigateUp()) finish();
            }
        });

        if (needsStoragePermission()) requestPermissions();
        else initializeExplorer();
    }

    private boolean needsStoragePermission() {
        if (getIntent().hasExtra(EXTRA_CONTAINER_ID)) return false;

        File initialPath = resolveInitialGeneralPath();
        if (initialPath == null) return false;

        boolean insideAppStorage = isInsideDirectory(getFilesDir(), initialPath) || isInsideDirectory(getCacheDir(), initialPath);
        if (insideAppStorage) return false;

        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) initializeExplorer();
            else {
                Toast.makeText(this, R.string.fm_permission_required, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeExplorer() {
        int containerId = getIntent().getIntExtra(EXTRA_CONTAINER_ID, -1);
        if (containerId >= 0) {
            activeContainer = new ContainerManager(this).getContainerById(containerId);
            if (activeContainer == null) {
                Toast.makeText(this, R.string.container_file_manager_unavailable, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            showDriveRoot();
            return;
        }

        File rootDir = resolveRootDirectory();
        File initialDir = resolveInitialGeneralPath();
        if (initialDir == null || !initialDir.isDirectory()) initialDir = rootDir != null ? rootDir : Environment.getExternalStorageDirectory();
        if (rootDir == null || !rootDir.isDirectory()) rootDir = null;

        loadDirectory(initialDir, rootDir);
    }

    private File resolveRootDirectory() {
        String rootPath = getIntent().getStringExtra(EXTRA_ROOT_PATH);
        if (rootPath == null || rootPath.isEmpty()) return null;
        File rootDir = new File(rootPath);
        return rootDir.exists() && rootDir.isDirectory() ? rootDir : null;
    }

    private File resolveInitialGeneralPath() {
        String startPath = getIntent().getStringExtra(EXTRA_START_PATH);
        if (startPath != null && !startPath.isEmpty()) {
            File startDir = new File(startPath);
            if (startDir.exists() && startDir.isDirectory()) return startDir;
        }
        return Environment.getExternalStorageDirectory();
    }

    private void showDriveRoot() {
        showingDriveRoot = true;
        currentDir = null;
        currentRootDir = null;
        allEntries.clear();

        LinkedHashMap<String, FileEntry> drives = new LinkedHashMap<>();
        addDrive(drives, "C:", new File(activeContainer.getRootDir(), ".wine/drive_c"));

        try {
            addDrive(drives, "Z:", new File(activeContainer.getRootDir(), "../..").getCanonicalFile());
        }
        catch (IOException ignored) {}

        for (String[] drive : activeContainer.drivesIterator()) {
            String driveLabel = drive[0].toUpperCase(Locale.ENGLISH) + ":";
            addDrive(drives, driveLabel, new File(drive[1]));
        }

        allEntries.addAll(drives.values());
        Collections.sort(allEntries, Comparator.comparing(entry -> entry.title.toLowerCase(Locale.ENGLISH)));
        applyFilter();
        updateCurrentPathLabel();
    }

    private void addDrive(Map<String, FileEntry> drives, String driveLabel, File target) {
        if (target == null || !target.exists()) return;
        String key = driveLabel.toUpperCase(Locale.ENGLISH);
        if (drives.containsKey(key)) return;
        drives.put(key, new FileEntry(EntryType.DRIVE, driveLabel, target.getAbsolutePath(), target, target.lastModified(), 0L));
    }

    private void loadDirectory(File directory, File rootLimit) {
        showingDriveRoot = false;
        currentDir = directory;
        currentRootDir = rootLimit;
        allEntries.clear();

        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!child.isDirectory() && !isAllowed(child)) continue;

                EntryType type = child.isDirectory() ? EntryType.DIRECTORY : EntryType.FILE;
                String subtitle = child.isDirectory()
                        ? getString(R.string.fm_subtitle_template, getString(R.string.fm_folder_label), DateFormat.getDateTimeInstance().format(child.lastModified()))
                        : getString(R.string.fm_subtitle_template, humanReadableSize(child.length()), DateFormat.getDateTimeInstance().format(child.lastModified()));
                allEntries.add(new FileEntry(type, child.getName(), subtitle, child, child.lastModified(), child.isDirectory() ? 0L : child.length()));
            }
        }

        sortEntries();
        applyFilter();
        updateCurrentPathLabel();
    }

    private void sortEntries() {
        Collections.sort(allEntries, (left, right) -> {
            if (left.type != right.type) {
                if (left.type == EntryType.DIRECTORY) return -1;
                if (right.type == EntryType.DIRECTORY) return 1;
            }

            switch (sortBy) {
                case DATE:
                    return Long.compare(right.modifiedAt, left.modifiedAt);
                case SIZE:
                    return Long.compare(right.size, left.size);
                case NAME:
                default:
                    return left.title.compareToIgnoreCase(right.title);
            }
        });
    }

    private boolean isAllowed(File file) {
        if (browseMode) return true;
        if (allowedExtensions == null || allowedExtensions.length == 0) return true;

        String lowerName = file.getName().toLowerCase(Locale.ENGLISH);
        for (String extension : allowedExtensions) {
            if (lowerName.endsWith(extension.toLowerCase(Locale.ENGLISH))) return true;
        }
        return false;
    }

    private void applyFilter() {
        filteredEntries.clear();
        String query = currentQuery == null ? "" : currentQuery.trim().toLowerCase(Locale.ENGLISH);
        for (FileEntry entry : allEntries) {
            if (query.isEmpty() || entry.title.toLowerCase(Locale.ENGLISH).contains(query)) filteredEntries.add(entry);
        }
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(filteredEntries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateCurrentPathLabel() {
        if (showingDriveRoot) {
            currentPathView.setText(getString(R.string.fm_drives_root));
            if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(activeContainer != null ? activeContainer.getName() : null);
            return;
        }

        if (currentDir != null) {
            currentPathView.setText(currentDir.getAbsolutePath());
            if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(currentDir.getAbsolutePath());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_manager_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.fm_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query;
                applyFilter();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText;
                applyFilter();
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean canModifyCurrentFolder = !showingDriveRoot && currentDir != null;
        MenuItem pasteItem = menu.findItem(R.id.action_paste);
        MenuItem cancelItem = menu.findItem(R.id.action_clipboard_cancel);
        MenuItem newFolderItem = menu.findItem(R.id.action_new_folder);

        if (pasteItem != null) pasteItem.setVisible(canModifyCurrentFolder && clipboardFile != null);
        if (cancelItem != null) cancelItem.setVisible(clipboardFile != null);
        if (newFolderItem != null) newFolderItem.setVisible(canModifyCurrentFolder);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            case R.id.action_up:
                if (!navigateUp()) finish();
                return true;
            case R.id.action_new_folder:
                createNewFolder();
                return true;
            case R.id.action_toggle_layout:
                gridMode = !gridMode;
                layoutManager.setSpanCount(gridMode ? 2 : 1);
                return true;
            case R.id.action_refresh:
                reloadCurrentLocation();
                return true;
            case R.id.action_paste:
                performPaste();
                return true;
            case R.id.action_clipboard_cancel:
                clearClipboard();
                return true;
            case R.id.sort_name:
                sortBy = SortBy.NAME;
                reloadCurrentLocation();
                return true;
            case R.id.sort_date:
                sortBy = SortBy.DATE;
                reloadCurrentLocation();
                return true;
            case R.id.sort_size:
                sortBy = SortBy.SIZE;
                reloadCurrentLocation();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean navigateUp() {
        if (showingDriveRoot) return false;
        if (currentDir == null) return false;

        if (activeContainer != null && currentRootDir != null && sameFile(currentDir, currentRootDir)) {
            showDriveRoot();
            return true;
        }

        File parentDir = currentDir.getParentFile();
        if (parentDir == null) return false;

        if (currentRootDir != null && !isSameOrChild(parentDir, currentRootDir)) {
            if (activeContainer != null) {
                showDriveRoot();
                return true;
            }
            return false;
        }

        loadDirectory(parentDir, currentRootDir);
        return true;
    }

    private void reloadCurrentLocation() {
        if (showingDriveRoot) {
            showDriveRoot();
        }
        else if (currentDir != null) {
            loadDirectory(currentDir, currentRootDir);
        }
    }

    private void createNewFolder() {
        if (currentDir == null) return;

        EditText input = new EditText(this);
        input.setHint(getString(R.string.fm_new_folder_hint));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.fm_new_folder_title))
                .setView(input)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (folderName.isEmpty()) return;

                    File folder = new File(currentDir, folderName);
                    if (!folder.exists() && folder.mkdirs()) reloadCurrentLocation();
                    else Toast.makeText(this, R.string.fm_new_folder_fail, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void onEntryClick(FileEntry entry) {
        if (entry.type == EntryType.DRIVE || entry.type == EntryType.DIRECTORY) {
            loadDirectory(entry.file, entry.type == EntryType.DRIVE ? entry.file : currentRootDir);
            return;
        }

        if (browseMode) {
            showEntryActions(entry);
            return;
        }

        Intent result = new Intent();
        result.putExtra("selectedFilePath", entry.file.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    private void showEntryActions(FileEntry entry) {
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.fm_ctx_open));
        if (entry.type == EntryType.FILE) options.add(getString(R.string.fm_ctx_share));
        if (entry.type == EntryType.FILE && entry.title.toLowerCase(Locale.ENGLISH).endsWith(".exe"))
            options.add(getString(R.string.fm_ctx_create_shortcut));
        if (entry.type != EntryType.DRIVE) {
            options.add(getString(R.string.fm_ctx_copy));
            options.add(getString(R.string.fm_ctx_move));
            options.add(getString(R.string.fm_ctx_rename));
            options.add(getString(R.string.fm_ctx_delete));
        }

        String[] items = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(entry.title)
                .setItems(items, (dialog, which) -> handleEntryAction(entry, items[which]))
                .show();
    }

    private void handleEntryAction(FileEntry entry, String action) {
        if (action.equals(getString(R.string.fm_ctx_open))) {
            onEntryClick(entry);
        }
        else if (action.equals(getString(R.string.fm_ctx_share))) {
            shareFile(entry.file);
        }
        else if (action.equals(getString(R.string.fm_ctx_copy))) {
            clipboardFile = entry.file;
            clipboardMove = false;
            Toast.makeText(this, getString(R.string.fm_clipboard_copy, entry.title), Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
        }
        else if (action.equals(getString(R.string.fm_ctx_move))) {
            clipboardFile = entry.file;
            clipboardMove = true;
            Toast.makeText(this, getString(R.string.fm_clipboard_move, entry.title), Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
        }
        else if (action.equals(getString(R.string.fm_ctx_rename))) {
            renameEntry(entry.file);
        }
        else if (action.equals(getString(R.string.fm_ctx_create_shortcut))) {
            createShortcutFromExe(entry.file);
        }
        else if (action.equals(getString(R.string.fm_ctx_delete))) {
            deleteEntry(entry.file);
        }
    }

    private void shareFile(File file) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/octet-stream");
        shareIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getPackageName() + ".tileprovider", file));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.fm_share_chooser)));
    }

    private void renameEntry(File file) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(file.getName());

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.fm_rename_title))
                .setView(input)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;

                    File renamedFile = new File(file.getParentFile(), newName);
                    if (!renamedFile.exists() && file.renameTo(renamedFile)) reloadCurrentLocation();
                    else Toast.makeText(this, R.string.fm_rename_fail, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteEntry(File file) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.fm_delete_title))
                .setMessage(file.getName())
                .setPositiveButton(R.string.fm_ctx_delete, (dialog, which) -> {
                    if (deleteRecursively(file)) {
                        Toast.makeText(this, R.string.fm_delete_success, Toast.LENGTH_SHORT).show();
                        reloadCurrentLocation();
                    }
                    else Toast.makeText(this, R.string.fm_delete_fail, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createShortcutFromExe(File exeFile) {
        ContainerManager containerManager = new ContainerManager(this);
        ArrayList<Container> containers = containerManager.getContainers();

        if (containers.isEmpty()) {
            Toast.makeText(this, "No containers available", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] containerNames = new String[containers.size()];
        for (int i = 0; i < containers.size(); i++) {
            containerNames[i] = containers.get(i).getName();
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.fm_ctx_create_shortcut))
                .setItems(containerNames, (dialog, which) -> {
                    Container container = containers.get(which);
                    createShortcutForContainer(exeFile, container);
                })
                .show();
    }

    private void createShortcutForContainer(File exeFile, Container container) {
        try {
            String absolutePath = exeFile.getAbsolutePath();
            String fileName = exeFile.getName();
            String fileNameWithoutExt = fileName.substring(0, fileName.length() - 4);
            String suggested = GameTitleParser.suggestName(exeFile);
            String displayName = GameTitleParser.normalizeName(
                    suggested != null && !suggested.trim().isEmpty() ? suggested : fileNameWithoutExt);
            if (displayName.trim().isEmpty()) displayName = fileNameWithoutExt;

            String driveLetter = null;
            String relativePath = absolutePath.toLowerCase(Locale.ENGLISH);
            String externalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath().toLowerCase(Locale.ENGLISH);

            if (relativePath.contains(externalStoragePath)) {
                driveLetter = "D:";
            } else if (relativePath.contains("/imagefs/")) {
                driveLetter = "Z:";
            } else if (relativePath.contains("/.wine/drive_c/")) {
                driveLetter = "C:";
            }

            if (driveLetter == null) {
                Toast.makeText(this, "Wrong path! Can't detect drive!", Toast.LENGTH_SHORT).show();
                return;
            }

            String pathWOutPrefix = absolutePath;

            if (driveLetter.equals("D:")) {
                pathWOutPrefix = pathWOutPrefix.replaceFirst(Environment.getExternalStorageDirectory().getAbsolutePath() + "/", "");
                if (pathWOutPrefix.toLowerCase(Locale.ENGLISH).startsWith("download/")) {
                    pathWOutPrefix = pathWOutPrefix.substring(9);
                }
            } else if (driveLetter.equals("Z:")) {
                int imagefsIndex = pathWOutPrefix.indexOf("/imagefs/");
                if (imagefsIndex != -1) {
                    pathWOutPrefix = pathWOutPrefix.substring(imagefsIndex + 9);
                }
            } else if (driveLetter.equals("C:")) {
                int driveCIndex = pathWOutPrefix.indexOf("/.wine/drive_c/");
                if (driveCIndex != -1) {
                    pathWOutPrefix = pathWOutPrefix.substring(driveCIndex + 15);
                }
            }

            String execPath = pathWOutPrefix;
            int lastSlash = pathWOutPrefix.lastIndexOf("/");
            String pathDir = (lastSlash > 0) ? pathWOutPrefix.substring(0, lastSlash) : "";

            int randomNum = (int)(Math.random() * 10000);
            String iconName = randomNum + "_" + displayName + ".0";

            try {
                Bitmap exeIcon = PEParser.extractIcon(exeFile);
                if (exeIcon != null) {
                    File iconDir = container.getIconsDir(64);
                    if (!iconDir.exists()) iconDir.mkdirs();
                    File iconFile = new File(iconDir, iconName + ".png");
                    FileUtils.saveBitmapToFile(exeIcon, iconFile);
                }
            } catch (Exception e) {
                Log.e("FileManager", "Error extracting icon", e);
            }

            String shortcutDesktop =
                "[Desktop Entry]\n" +
                "Name=" + displayName + "\n" +
                "Exec=env WINEPREFIX=\"/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.wine/dosdevices/z:/home/xuser/.wine\" wine " + driveLetter + "/" + execPath + "\n" +
                "Type=Application\n" +
                "StartupNotify=true\n" +
                "Path=/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.wine/dosdevices/" + driveLetter.toLowerCase(Locale.ENGLISH) + "/" + pathDir + "\n" +
                "Icon=" + iconName + "\n" +
                "StartupWMClass=" + fileName.toLowerCase(Locale.ENGLISH);

            File desktopFile = new File(container.getDesktopDir(), displayName + ".desktop");

            try (FileWriter writer = new FileWriter(desktopFile)) {
                writer.write(shortcutDesktop);
            }

            Toast.makeText(this, "Shortcut created for Container: " + container.getName(), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e("FileManager", "Error creating shortcut", e);
            Toast.makeText(this, "Error creating shortcut!", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteRecursively(File target) {
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return target.delete();
    }

    private void performPaste() {
        if (clipboardFile == null || currentDir == null) return;

        File target = uniqueName(new File(currentDir, clipboardFile.getName()));
        boolean success;

        if (clipboardMove) {
            success = clipboardFile.renameTo(target);
            if (!success) {
                success = copyRecursively(clipboardFile, target);
                if (success) deleteRecursively(clipboardFile);
            }
        }
        else {
            success = copyRecursively(clipboardFile, target);
        }

        if (success) {
            Toast.makeText(this, clipboardMove ? R.string.fm_paste_success_move : R.string.fm_paste_success_copy, Toast.LENGTH_SHORT).show();
            clearClipboard();
            reloadCurrentLocation();
        }
        else Toast.makeText(this, R.string.fm_paste_fail, Toast.LENGTH_SHORT).show();
    }

    private void clearClipboard() {
        clipboardFile = null;
        clipboardMove = false;
        invalidateOptionsMenu();
    }

    private File uniqueName(File target) {
        if (!target.exists()) return target;

        String name = target.getName();
        String base = name;
        String extension = "";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            base = name.substring(0, dotIndex);
            extension = name.substring(dotIndex);
        }

        int copyIndex = 1;
        File candidate;
        do {
            candidate = new File(target.getParentFile(), base + " (" + copyIndex++ + ")" + extension);
        }
        while (candidate.exists());
        return candidate;
    }

    private boolean copyRecursively(File source, File destination) {
        try {
            if (source.isDirectory()) {
                if (!destination.exists() && !destination.mkdirs()) return false;
                File[] children = source.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (!copyRecursively(child, new File(destination, child.getName()))) return false;
                    }
                }
                return true;
            }

            try (FileInputStream inputStream = new FileInputStream(source);
                 FileOutputStream outputStream = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
            return true;
        }
        catch (IOException ignored) {
            return false;
        }
    }

    private boolean isSameOrChild(File candidate, File parent) {
        try {
            String candidatePath = candidate.getCanonicalPath();
            String parentPath = parent.getCanonicalPath();
            return candidatePath.equals(parentPath) || candidatePath.startsWith(parentPath + File.separator);
        }
        catch (IOException e) {
            return false;
        }
    }

    private boolean isInsideDirectory(File directory, File file) {
        return directory != null && file != null && isSameOrChild(file, directory);
    }

    private boolean sameFile(File first, File second) {
        try {
            return first != null && second != null && first.getCanonicalPath().equals(second.getCanonicalPath());
        }
        catch (IOException e) {
            return false;
        }
    }

    private static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exponent = (int) (Math.log(bytes) / Math.log(1024));
        String prefix = String.valueOf("KMGTPE".charAt(exponent - 1));
        return String.format(Locale.ENGLISH, "%.1f %sB", bytes / Math.pow(1024, exponent), prefix);
    }

    private final class ExplorerAdapter extends RecyclerView.Adapter<ExplorerAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_manager_entry_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FileEntry entry = filteredEntries.get(position);
            holder.title.setText(entry.title);
            holder.subtitle.setText(entry.subtitle);

            switch (entry.type) {
                    case DRIVE:
                        holder.icon.setImageResource(R.drawable.diskk);
                        break;
                case DIRECTORY:
                    holder.icon.setImageResource(R.drawable.icon_open);
                    break;
                case FILE:
                default:
                    holder.icon.setImageResource(android.R.drawable.ic_menu_save);
                    if (entry.type == EntryType.FILE && entry.title.toLowerCase(Locale.ENGLISH).endsWith(".exe")) {
                        loadExeIcon(entry, holder);
                    }
                    break;
            }

            holder.itemView.setOnClickListener(v -> onEntryClick(entry));
            holder.itemView.setOnLongClickListener(v -> {
                if (entry.type != EntryType.DRIVE) showEntryActions(entry);
                return true;
            });
            holder.moreButton.setOnClickListener(v -> showEntryActions(entry));
            holder.moreButton.setVisibility(entry.type == EntryType.DRIVE ? View.INVISIBLE : View.VISIBLE);
        }

        private void loadExeIcon(FileEntry entry, ViewHolder holder) {
            new Thread(() -> {
                try {
                    Bitmap icon = PEParser.extractIcon(entry.file);
                    if (icon != null) {
                        runOnUiThread(() -> {
                            if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                                holder.icon.setImageBitmap(icon);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e("FileManager", "Error extracting exe icon", e);
                }
            }).start();
        }

        @Override
        public int getItemCount() {
            return filteredEntries.size();
        }

        private final class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView icon;
            private final TextView title;
            private final TextView subtitle;
            private final ImageButton moreButton;

            private ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.IVIcon);
                title = itemView.findViewById(R.id.TVTitle);
                subtitle = itemView.findViewById(R.id.TVSubtitle);
                moreButton = itemView.findViewById(R.id.BTMore);
            }
        }
    }
}
