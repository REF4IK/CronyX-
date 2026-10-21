package com.winlator.cmod;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.winlator.cmod.core.GameTitleParser;
import com.winlator.cmod.core.ShortcutCoverFetcher;
import com.winlator.cmod.win32.PEParser;

import java.io.File;

/**
 * Full-screen confirmation dialog for importing a game.
 * Styled like {@link GameImportPickerActivity}.
 * Shows: game icon preview, editable name, optional description,
 * path to .exe (with "Change" button), Confirm / Cancel buttons.
 */
public class GameImportConfirmActivity extends AppCompatActivity {
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_CONTAINER_ID = "container_id";
    public static final String EXTRA_RESULT_NAME = "result_name";
    public static final String EXTRA_RESULT_DESC = "result_desc";

    private File exeFile;
    private int containerId;
    private Bitmap exeIcon;

    private ImageView iconView;
    private TextInputEditText nameEdit;
    private TextInputEditText descEdit;
    private TextView pathView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_import_confirm);

        // Dark status/nav bars like GameImportPickerActivity
        android.view.Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(0xFF0E1F26);
            window.setNavigationBarColor(0xFF1A0B22);
            android.view.View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        // Read extras
        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        containerId = getIntent().getIntExtra(EXTRA_CONTAINER_ID, -1);
        if (filePath == null) {
            finish();
            return;
        }
        exeFile = new File(filePath);

        // Extract icon
        try {
            exeIcon = PEParser.extractIcon(exeFile);
        } catch (Exception ignored) {}

        // Bind views
        iconView = findViewById(R.id.IVImportIcon);
        nameEdit = findViewById(R.id.ETImportName);
        descEdit = findViewById(R.id.ETImportDescription);
        pathView = findViewById(R.id.TVImportExePath);

        MaterialButton backBtn = findViewById(R.id.BTBack);
        MaterialButton cancelBtn = findViewById(R.id.BTCancelImport);
        MaterialButton confirmBtn = findViewById(R.id.BTConfirmImport);
        MaterialButton changePathBtn = findViewById(R.id.BTChangeExePath);

        // Set values
        String fileName = exeFile.getName();
        String nameNoExt = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
        String suggested = GameTitleParser.suggestName(exeFile);
        nameEdit.setText(suggested != null && !suggested.trim().isEmpty() ? suggested : nameNoExt);
        pathView.setText(exeFile.getAbsolutePath());

        if (exeIcon != null) {
            iconView.setImageBitmap(exeIcon);
        } else {
            iconView.setImageResource(R.drawable.icon_shortcut);
        }

        // Back / Cancel
        backBtn.setOnClickListener(v -> finish());
        cancelBtn.setOnClickListener(v -> finish());

        // Change path — re-open file picker
        changePathBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, GameImportPickerActivity.class);
            File parent = exeFile.getParentFile();
            if (parent != null) {
                intent.putExtra(GameImportPickerActivity.EXTRA_INITIAL_DIR, parent.getAbsolutePath());
            }
            startActivityForResult(intent, 1001);
        });

        // Confirm
        confirmBtn.setOnClickListener(v -> {
            String finalName = GameTitleParser.normalizeName(nameEdit.getText().toString().trim());
            if (finalName.isEmpty()) finalName = nameNoExt;
            ShortcutCoverFetcher.clearCoverCache(this, finalName);
            String desc = descEdit.getText().toString().trim();

            Intent result = new Intent();
            result.putExtra(EXTRA_FILE_PATH, exeFile.getAbsolutePath());
            result.putExtra(EXTRA_CONTAINER_ID, containerId);
            result.putExtra(EXTRA_RESULT_NAME, finalName);
            result.putExtra(EXTRA_RESULT_DESC, desc);
            setResult(Activity.RESULT_OK, result);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            String newPath = data.getStringExtra(GameImportPickerActivity.EXTRA_RESULT_PATH);
            if (newPath != null) {
                exeFile = new File(newPath);
                pathView.setText(exeFile.getAbsolutePath());

                // Re-extract icon
                try {
                    exeIcon = PEParser.extractIcon(exeFile);
                    if (exeIcon != null) {
                        iconView.setImageBitmap(exeIcon);
                    }
                } catch (Exception ignored) {}

                String fileName = exeFile.getName();
                String nameNoExt = fileName.contains(".")
                        ? fileName.substring(0, fileName.lastIndexOf('.'))
                        : fileName;
                if (nameEdit.getText().toString().trim().isEmpty()) {
                    String suggested = GameTitleParser.suggestName(exeFile);
                    nameEdit.setText(suggested != null && !suggested.trim().isEmpty() ? suggested : nameNoExt);
                }
            }
        }
    }
}
