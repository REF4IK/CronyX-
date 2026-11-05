package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSwitch
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.GlassSolidPill
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import com.winlator.cmod.IconPackDetailActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.inputcontrols.CustomIconManager
import com.winlator.cmod.inputcontrols.IconPackManager
import com.winlator.cmod.inputcontrols.IconPackManager.IconPack

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IconManagerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val customIconManager = remember { CustomIconManager(ctx) }
    val iconPackManager = remember { IconPackManager(ctx) }

    var selectedTab by remember { mutableStateOf(0) }
    var refreshKey by remember { mutableStateOf(0) }

    // State for Custom Icons
    val myIconIdList = remember(refreshKey) {
        val ids = customIconManager.customIconIds ?: intArrayOf()
        val ordered = customIconManager.getIconIdsInOrder(ids) ?: intArrayOf()
        ordered.toList()
    }

    // State for Icon Packs
    val iconPacks = remember(refreshKey) {
        iconPackManager.iconPacks ?: emptyList()
    }

    // File pickers
    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val successId = customIconManager.importIcon(uri)
            if (successId >= 0) {
                AppUtils.showToast(ctx, R.string.icon_imported_successfully)
                refreshKey++
            } else {
                AppUtils.showToast(ctx, R.string.failed_to_import_icon)
            }
        }
    }

    val packPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = FileUtils.getUriFileName(ctx, uri) ?: ""
            val pack = if (fileName.lowercase().endsWith(".ipk")) {
                iconPackManager.importIconPackFromIpk(uri)
            } else {
                iconPackManager.importIconPackFromZip(uri)
            }

            if (pack != null) {
                AppUtils.showToast(ctx, R.string.icon_imported_successfully)
                refreshKey++
            } else {
                AppUtils.showToast(ctx, R.string.failed_to_import_icon)
            }
        }
    }

    // Edit/preview state
    var editingIconId by remember { mutableStateOf<Int?>(null) }
    var showPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // Context actions depending on active Tab
            if (selectedTab == 0) {
                IconButton(onClick = { iconPickerLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
                }
                IconButton(onClick = {
                    ContentDialog.confirm(ctx, R.string.confirm_delete_all_custom_icons) {
                        customIconManager.deleteAllIcons()
                        refreshKey++
                    }
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_all_custom_icons), tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = { packPickerLauncher.launch("*/*") }) {
                    Icon(Icons.Filled.FolderZip, contentDescription = stringResource(R.string.add_icon_pack_zip))
                }
            }
        }

        // Tabs switcher
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = glassAccent(),
            divider = { },
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("My Icons") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Icon Packs") }
            )
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedTab == 0) {
                // ---- Tab My Icons ----
                if (myIconIdList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No custom icons yet.\nTap + to add some.", color = Color.Gray, fontSize = 16.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(myIconIdList) { iconId ->
                            val bitmap = remember(iconId, refreshKey) {
                                customIconManager.loadIcon(iconId)
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .combinedClickable(
                                        onClick = { showPreviewBitmap = bitmap },
                                        onLongClick = { editingIconId = iconId }
                                    ),
                                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                                shape = RoundedCornerShape(GlassTokens.CardRadius),
                                border = glassCardBorder(),
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp)
                                        )
                                    } else {
                                        Text(text = "?", fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ---- Tab Icon Packs ----
                if (iconPacks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No icon packs imported.", color = Color.Gray, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        items(iconPacks) { pack ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        val intent = Intent(ctx, IconPackDetailActivity::class.java).apply {
                                            putExtra(IconPackDetailActivity.EXTRA_PACK_NAME, pack.name)
                                        }
                                        ctx.startActivity(intent)
                                    },
                                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                                shape = RoundedCornerShape(GlassTokens.CardRadius),
                                border = glassCardBorder(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(48.dp).background(Color(0xFF2D2D2D)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (pack.preview != null) {
                                            Image(
                                                bitmap = pack.preview.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        } else {
                                            Icon(Icons.Filled.FolderZip, null, tint = Color.Gray)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = pack.name, fontWeight = FontWeight.Bold)
                                        Text(text = "${pack.iconCount} icons", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    IconButton(
                                        onClick = {
                                            ContentDialog.confirm(ctx, R.string.confirm_delete_component) {
                                                iconPackManager.deleteIconPack(pack)
                                                refreshKey++
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit icon bottom dialog / sheet options
    if (editingIconId != null) {
        AlertDialog(
            onDismissRequest = { editingIconId = null },
            title = { Text("Edit Custom Icon") },
            text = {
                val iconId = editingIconId!!
                val bitmap = remember(iconId, refreshKey) { customIconManager.loadIcon(iconId) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).background(Color(0xFF1D1D1D)).padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Icon ID: $iconId", fontSize = 14.sp)
                }
            },
            confirmButton = {
                GlassSolidPill(
                    onClick = {
                    customIconManager.rotateIcon(editingIconId!!)
                    refreshKey++
                    editingIconId = null
                },
                ) {
                    Icon(Icons.Filled.RotateRight, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rotate 90°")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        customIconManager.deleteIcon(editingIconId!!)
                        refreshKey++
                        editingIconId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            }
        )
    }

    // Image preview dialog
    if (showPreviewBitmap != null) {
        AlertDialog(
            onDismissRequest = { showPreviewBitmap = null },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = showPreviewBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.8f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewBitmap = null }) {
                    Text("Close")
                }
            }
        )
    }
}
