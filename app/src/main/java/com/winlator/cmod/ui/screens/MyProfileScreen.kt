package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSectionHeader
import com.winlator.cmod.ui.components.GlassBottomNavClearance

import com.winlator.cmod.ui.components.glassBadgeFill
import com.winlator.cmod.ui.components.GlassSwitch
import com.winlator.cmod.ui.components.glassAccent
import androidx.compose.ui.graphics.Color
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.community.AccountManager
import com.winlator.cmod.core.ImageUtils
import com.winlator.cmod.ui.components.AccountAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyProfileScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var account by remember { mutableStateOf(AccountManager.current(context)) }
    var justCreated by remember { mutableStateOf<AccountManager.CreateData?>(null) }

    var tab by remember { mutableStateOf(0) }
    var showReset by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var recoveryKeyInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revealRecovery by remember { mutableStateOf(false) }
    var uploads by remember { mutableStateOf(AccountManager.cachedUploads(context)) }

    var avatarBusy by remember { mutableStateOf(false) }
    var showUsers by remember { mutableStateOf(false) }
    var deleteMyArmed by remember { mutableStateOf(false) }

    // Номер могли выдать уже после логина — подтягиваем молча по живой сессии
    LaunchedEffect(account?.session) {
        if (account != null && account!!.num < 0) {
            val fresh = withContext(Dispatchers.IO) { AccountManager.refreshMe(context) }
            if (fresh != null) account = fresh
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        avatarBusy = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { compressAvatar(context, uri) }
            if (bytes == null) {
                avatarBusy = false
                Toast.makeText(context, context.getString(R.string.profile_avatar_read_fail), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                AccountManager.uploadAvatar(context, bytes, "image/jpeg")
            }
            avatarBusy = false
            when (result) {
                is AccountManager.AccountResult.Success -> {
                    account = AccountManager.current(context)
                    Toast.makeText(context, context.getString(R.string.profile_avatar_updated), Toast.LENGTH_SHORT).show()
                }
                is AccountManager.AccountResult.Error ->
                    Toast.makeText(context, avatarErrorMessage(context, result.code), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun doCreate() {
        busy = true; error = null
        val u = username.trim(); val p = password
        scope.launch {
            val result = withContext(Dispatchers.IO) { AccountManager.createAccount(context, u, p) }
            busy = false
            when (result) {
                is AccountManager.AccountResult.Success -> {
                    justCreated = result.data
                    password = ""
                }
                is AccountManager.AccountResult.Error ->
                    error = accountErrorMessage(context, result, false)
            }
        }
    }

    fun doLogin() {
        busy = true; error = null
        val u = username.trim(); val p = password
        scope.launch {
            val result = withContext(Dispatchers.IO) { AccountManager.login(context, u, p) }
            busy = false
            when (result) {
                is AccountManager.AccountResult.Success -> {
                    account = AccountManager.current(context)
                    uploads = AccountManager.cachedUploads(context)
                    password = ""
                    Toast.makeText(context, context.getString(R.string.profile_signed_in), Toast.LENGTH_SHORT).show()
                }
                is AccountManager.AccountResult.Error ->
                    error = accountErrorMessage(context, result, false)
            }
        }
    }

    fun doReset() {
        busy = true; error = null
        val u = username.trim(); val rk = recoveryKeyInput.trim(); val np = newPassword
        scope.launch {
            val result = withContext(Dispatchers.IO) { AccountManager.resetPassword(context, u, rk, np) }
            busy = false
            when (result) {
                is AccountManager.AccountResult.Success -> {
                    account = AccountManager.current(context)
                    showReset = false
                    password = ""; newPassword = ""; recoveryKeyInput = ""
                    Toast.makeText(context, context.getString(R.string.profile_reset_done), Toast.LENGTH_SHORT).show()
                }
                is AccountManager.AccountResult.Error ->
                    error = accountErrorMessage(context, result, true)
            }
        }
    }

    fun deleteUpload(entry: AccountManager.AccountUpload) {
        // Оптимистично убираем из списка сразу (удаление бандла на сервере
        // перебирает весь репозиторий и может занять минуту+).
        uploads = uploads.filterNot { it.sha == entry.sha }
        Toast.makeText(context, context.getString(R.string.profile_upload_deleting), Toast.LENGTH_SHORT).show()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val latch = java.util.concurrent.CountDownLatch(1)
                var success = false
                val cb = object : com.winlator.cmod.core.gameconfig.BundleRepoClient.BundleDeleteCallback {
                    override fun onComplete(s: Boolean, e: String?) { success = s; latch.countDown() }
                }
                if (entry.kind == "bundle") {
                    com.winlator.cmod.core.gameconfig.BundleRepoClient.deleteBundle(entry.sha, entry.token, cb)
                } else {
                    com.winlator.cmod.core.gameconfig.CloudConfigRepoV2.deleteConfig(
                        entry.sha, entry.game, entry.filename, entry.token,
                        object : com.winlator.cmod.core.gameconfig.CloudConfigRepoV2.DeleteCallback {
                            override fun onResult(s: Boolean, e: String?) { success = s; latch.countDown() }
                        })
                }
                latch.await(120, java.util.concurrent.TimeUnit.SECONDS)
                success
            }
            if (ok) {
                AccountManager.removeCachedUpload(context, entry.sha)
                uploads = AccountManager.cachedUploads(context)
                Toast.makeText(context, context.getString(R.string.profile_upload_deleted), Toast.LENGTH_SHORT).show()
            } else {
                uploads = (uploads + entry).sortedByDescending { it.ts }
                Toast.makeText(context, context.getString(R.string.profile_upload_delete_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = GlassBottomNavClearance),
        // промежуток между блоками — как в остальных списках
        verticalArrangement = Arrangement.spacedBy(GlassTokens.ListGap),
    ) {
        when {
            justCreated != null -> {
                val data = justCreated!!
                SectionCard(title = stringResource(R.string.profile_created_title, data.username)) {
                    Text(stringResource(R.string.profile_recovery_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Surface(color = glassBadgeFill(),
                shape = RoundedCornerShape(GlassTokens.InnerRadius),
                modifier = Modifier.fillMaxWidth(),
            ) {
                        Text(data.recoveryKey, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
                    }
                    Text(stringResource(R.string.profile_recovery_warn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassPill(
                            onClick = {
                            clipboard.setText(AnnotatedString(data.recoveryKey))
                            Toast.makeText(context, context.getString(R.string.profile_key_copied), Toast.LENGTH_SHORT).show()
                        },
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.profile_copy))
                        }
                        Spacer(Modifier.weight(1f))
                        GlassSolidPill(
                            onClick = {
                            justCreated = null
                            account = AccountManager.current(context)
                        },
                        ) { Text(stringResource(R.string.profile_saved_it)) }
                    }
                }
            }
            account != null -> {
                val acc = account!!
                SectionCard(title = stringResource(R.string.my_profile)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            AccountAvatar(
                                avatarUrl = acc.displayAvatarUrl,
                                size = 56.dp,
                                modifier = Modifier.clickable(enabled = !avatarBusy) { avatarPicker.launch("image/*") },
                            )
                            if (avatarBusy) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(acc.username, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (acc.admin) com.winlator.cmod.ui.components.OwnerBadge()
                            }
                            TextButton(
                                onClick = { avatarPicker.launch("image/*") },
                                enabled = !avatarBusy,
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) { Text(stringResource(R.string.profile_change_picture), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    GlassPill(
                        onClick = { revealRecovery = !revealRecovery },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (revealRecovery) R.string.profile_hide_key else R.string.profile_show_key)) }
                    if (revealRecovery) {
                        val key = AccountManager.recoveryKey(context)
                        if (key != null) {
                            Surface(color = glassBadgeFill(),
                shape = RoundedCornerShape(GlassTokens.InnerRadius),
                modifier = Modifier.fillMaxWidth(),
            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(key, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(key))
                                        Toast.makeText(context, context.getString(R.string.profile_key_copied), Toast.LENGTH_SHORT).show()
                                    }) { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        } else {
                            Text(stringResource(R.string.profile_no_key), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    GlassPill(
                        onClick = {
                            AccountManager.logout(context)
                            account = null
                            uploads = emptyList()
                            revealRecovery = false
                            deleteMyArmed = false
                            username = ""; password = ""; error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.profile_logout)) }
                    if (account!!.admin) {
                        GlassPill(
                            onClick = { showUsers = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Group, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.profile_admin_users), fontSize = 13.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (deleteMyArmed) {
                            GlassSolidPill(
                                onClick = {
                                    deleteMyArmed = false
                                    busy = true
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { AccountManager.deleteMe(context) }
                                        busy = false
                                        if (ok) {
                                            AccountManager.logout(context)
                                            account = null
                                            uploads = emptyList()
                                            revealRecovery = false
                                            username = ""; password = ""; error = null
                                            Toast.makeText(context, context.getString(R.string.profile_deleted), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.admin_action_fail), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.profile_delete_confirm), fontSize = 12.sp) }
                        } else {
                            GlassPill(
                                onClick = { deleteMyArmed = true },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.profile_delete_me), fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                SectionCard(title = stringResource(R.string.profile_uploads_title, uploads.size)) {
                    if (uploads.isEmpty()) {
                        Text(stringResource(R.string.profile_no_uploads), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val dateFmt = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
                        uploads.forEach { up ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(up.game.ifBlank { up.filename }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = listOfNotNull(
                                        up.deviceHint(),
                                        if (up.ts > 0) dateFmt.format(Date(up.ts * 1000L)) else null,
                                    ).joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { deleteUpload(up) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
            showReset -> {
                SectionCard(title = stringResource(R.string.profile_reset_title)) {
                    Text(stringResource(R.string.profile_reset_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = username, onValueChange = { username = it; error = null }, label = { Text(stringResource(R.string.profile_username)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    OutlinedTextField(value = recoveryKeyInput, onValueChange = { recoveryKeyInput = it; error = null }, label = { Text(stringResource(R.string.profile_recovery_key)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    OutlinedTextField(value = newPassword, onValueChange = { newPassword = it; error = null }, label = { Text(stringResource(R.string.profile_new_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showReset = false; error = null }, enabled = !busy) { Text(stringResource(R.string.cancel)) }
                        Spacer(Modifier.weight(1f))
                        GlassSolidPill(
                            enabled = !busy && username.isNotBlank() && recoveryKeyInput.isNotBlank() && newPassword.isNotBlank(),
                            onClick = { doReset() },
                        ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.profile_reset_action)) }
                    }
                }
            }
            else -> {
                SectionCard(title = stringResource(R.string.my_profile)) {
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        contentColor = glassAccent(),
                        divider = { },
                    ) {
                        Tab(selected = tab == 0, onClick = { tab = 0; error = null }, text = { Text(stringResource(R.string.profile_tab_create)) })
                        Tab(selected = tab == 1, onClick = { tab = 1; error = null }, text = { Text(stringResource(R.string.profile_tab_login)) })
                    }
                    OutlinedTextField(value = username, onValueChange = { username = it; error = null }, label = { Text(stringResource(R.string.profile_username)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    OutlinedTextField(value = password, onValueChange = { password = it; error = null }, label = { Text(stringResource(R.string.profile_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    if (tab == 0) {
                        Text(stringResource(R.string.profile_throwaway_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    GlassSolidPill(
                        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (tab == 0) doCreate() else doLogin() },
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (tab == 0) R.string.profile_create_action else R.string.profile_login_action))
                    }
                    TextButton(onClick = { showReset = true; error = null }, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.profile_forgot))
                    }
                }
            }
        }
    }

    if (showUsers) {
        AdminUsersScreen(onDismiss = { showUsers = false })
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    // Референс: название секции стоит НАД карточкой, а не внутри неё.
    GlassSectionHeader(title = title)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        border = glassCardBorder(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Короткая подсказка из имени файла бандла для списка загрузок. */
private fun AccountManager.AccountUpload.deviceHint(): String? {
    if (kind != "bundle") return filename.ifBlank { null }
    return null
}

private fun accountErrorMessage(context: android.content.Context, result: AccountManager.AccountResult.Error, isReset: Boolean): String {
    if (result.httpCode == 404) return context.getString(R.string.profile_err_outdated)
    return when (result.code) {
    "bad_username" -> context.getString(R.string.profile_err_username)
    "username_taken" -> context.getString(R.string.profile_err_taken)
    "username_reserved" -> context.getString(R.string.profile_err_reserved)
    "weak_password" -> context.getString(R.string.profile_err_weak)
    "rate_limited" -> context.getString(R.string.profile_err_rate)
    "banned" -> context.getString(R.string.profile_err_banned)
    "forbidden" -> context.getString(R.string.profile_err_forbidden)
    "invalid" -> context.getString(if (isReset) R.string.profile_err_reset_key else R.string.profile_err_invalid)
    else -> context.getString(R.string.profile_err_network)
    }
}

private fun avatarErrorMessage(context: android.content.Context, code: String): String = when (code) {
    "bad_type" -> context.getString(R.string.profile_err_avatar_type)
    "bad_image" -> context.getString(R.string.profile_err_avatar_image)
    "not_signed_in" -> context.getString(R.string.profile_err_not_signed)
    else -> context.getString(R.string.profile_err_network)
}

private fun compressAvatar(context: android.content.Context, uri: Uri): ByteArray? {
    return try {
        val bitmap = ImageUtils.getBitmapFromUri(context, uri, 512) ?: return null
        val maxBytes = 100 * 1024
        var quality = 85
        var bytes: ByteArray
        do {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality >= 35)
        if (bytes.size > maxBytes) null else bytes
    } catch (e: Exception) {
        null
    }
}
