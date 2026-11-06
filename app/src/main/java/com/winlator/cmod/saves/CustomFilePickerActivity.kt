package com.winlator.cmod.saves

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.ui.screens.FolderPickerScreen
import com.winlator.cmod.ui.theme.WinlatorTheme
import java.io.File

/**
 * Пикер каталога на Compose-стекле. Контракт прежний (используют
 * SaveSettingsDialog, SaveEditDialog и перенос ярлыков):
 * extra `initialDirectory` → результат `selectedDirectory` + RESULT_OK.
 */
class CustomFilePickerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INITIAL_DIRECTORY = "initialDirectory"
        const val EXTRA_IS_EDITING = "isEditing"
        const val EXTRA_EDITING_PATH = "editingPath"
        const val EXTRA_SELECTED_DIRECTORY = "selectedDirectory"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Системные бары в цвет темы вместо зелёного из AppTheme
        val darkMode = MmkvPreferences().getBoolean("dark_mode", false)
        window?.apply {
            statusBarColor = if (darkMode) 0xFF191C1A.toInt() else 0xFFFBFDF9.toInt()
            navigationBarColor = statusBarColor
            val decor = decorView
            var flags = decor.systemUiVisibility
            if (darkMode) {
                flags = flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                flags = flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
            decor.systemUiVisibility = flags
        }

        // Совместимость со старыми вызовами: isEditing/editingPath задают стартовую папку
        val isEditing = intent.getBooleanExtra(EXTRA_IS_EDITING, false)
        val editingPath = intent.getStringExtra(EXTRA_EDITING_PATH)
        val initialDirectoryPath = when {
            isEditing && editingPath != null -> editingPath
            else -> intent.getStringExtra(EXTRA_INITIAL_DIRECTORY)
        } ?: run {
            finish()
            return
        }
        // editingPath может указывать на файл сейва — стартуем с его папки
        val initialDir = File(initialDirectoryPath).let { dir ->
            if (dir.isDirectory) dir else dir.parentFile ?: dir
        }

        setContent {
            val prefs = MmkvPreferences()
            WinlatorTheme(darkTheme = prefs.getBoolean("dark_mode", false)) {
                FolderPickerScreen(
                    initialDir = initialDir,
                    onBack = { finish() },
                    onPick = { dir ->
                        setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_SELECTED_DIRECTORY, dir.absolutePath))
                        finish()
                    },
                )
            }
        }
    }
}
