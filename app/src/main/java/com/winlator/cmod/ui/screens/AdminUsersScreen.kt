package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors

import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.GlassFieldShape
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.community.AdminManager
import com.winlator.cmod.ui.components.OwnerBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Админ-панель: все аккаунты — бан/разбан, удаление, себе правка ID/даты. Только для админа. */
@Composable
fun AdminUsersScreen(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<AdminManager.AdminUser>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var deleteArmed by remember { mutableStateOf<String?>(null) }
    var busyUser by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<AdminManager.AdminUser?>(null) }
    var editNum by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }

    fun load() {
        loading = true
        scope.launch(Dispatchers.IO) {
            val res = AdminManager.users(ctx)
            withContext(Dispatchers.Main) {
                loading = false
                users = res
                if (res == null) {
                    Toast.makeText(ctx, ctx.getString(R.string.profile_err_network), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setBan(u: AdminManager.AdminUser, ban: Boolean) {
        busyUser = u.username
        scope.launch(Dispatchers.IO) {
            val err = AdminManager.setBan(ctx, u.username, ban)
            withContext(Dispatchers.Main) {
                busyUser = null
                if (err == null) {
                    users = users?.map { if (it.username.equals(u.username, ignoreCase = true)) it.copy(banned = ban) else it }
                    Toast.makeText(
                        ctx,
                        if (ban) ctx.getString(R.string.admin_banned, u.username) else ctx.getString(R.string.admin_unbanned, u.username),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(ctx, ctx.getString(R.string.admin_action_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxHeight(0.9f).fillMaxWidth(0.94f),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassSheetFill(),
                border = glassCardBorder(),
            ) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp).padding(end = 8.dp))
                    Text(
                        stringResource(R.string.admin_users_title, users?.size ?: 0),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { load() }, enabled = !loading) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val list = users
                when {
                    loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    list == null -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.profile_err_network), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    list.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.admin_users_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        val dateFmt = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(list, key = { it.username.lowercase() }) { u ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                                    shape = RoundedCornerShape(10.dp),
                                    border = glassCardBorder(),
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Text(u.username, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                                    if (u.admin) OwnerBadge()
                                                    if (u.banned) {
                                                        Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(4.dp)) {
                                                            Text(stringResource(R.string.admin_banned_badge), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                                        }
                                                    }
                                                }
                                                val sub = listOfNotNull(
                                                    "ID " + u.displayNum,
                                                    if (u.created > 0) dateFmt.format(Date(u.created * 1000L)) else null,
                                                ).joinToString(" · ")
                                                Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (u.admin) {
                                                GlassPill(
                                                    onClick = {
                                                        editTarget = u
                                                        editNum = if (u.num >= 0) String.format(Locale.US, "%07d", u.num) else ""
                                                        editDate = if (u.created > 0) SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(u.created * 1000L)) else ""
                                                    },
                                                    enabled = busyUser == null,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                ) { Text(stringResource(R.string.profile_own_edit), fontSize = 12.sp) }
                                            } else {
                                                GlassPill(
                                                    onClick = { setBan(u, !u.banned) },
                                                    enabled = busyUser == null,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                ) {
                                                    if (busyUser == u.username) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                                    else {
                                                        Icon(Icons.Default.Gavel, null, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            stringResource(if (u.banned) R.string.admin_unban_everywhere else R.string.admin_ban_everywhere),
                                                            fontSize = 12.sp,
                                                        )                                                    }
                                                }
                                                Spacer(Modifier.weight(1f))
                                                if (deleteArmed == u.username) {
                                                    TextButton(
                                                        onClick = {
                                                            deleteArmed = null
                                                            busyUser = u.username
                                                            scope.launch(Dispatchers.IO) {
                                                                val ok = AdminManager.userDelete(ctx, u.username)
                                                                withContext(Dispatchers.Main) {
                                                                    busyUser = null
                                                                    if (ok) {
                                                                        users = users?.filterNot { it.username.equals(u.username, ignoreCase = true) }
                                                                        Toast.makeText(ctx, ctx.getString(R.string.admin_deleted), Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        Toast.makeText(ctx, ctx.getString(R.string.admin_action_fail), Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                                    ) { Text("×", fontSize = 16.sp, color = MaterialTheme.colorScheme.error) }
                                                } else {
                                                    IconButton(onClick = { deleteArmed = u.username }, modifier = Modifier.size(28.dp), enabled = busyUser == null) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val target = editTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(stringResource(R.string.profile_own_data)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editNum,
                        onValueChange = { editNum = it.filter { c -> c.isDigit() }.take(7) },
                        label = { Text(stringResource(R.string.profile_own_num), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors(),
                    )
                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it.take(10) },
                        label = { Text(stringResource(R.string.profile_own_date), fontSize = 12.sp) },
                        singleLine = true,
                        placeholder = { Text("08.09.2026", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uname = target.username
                        scope.launch(Dispatchers.IO) {
                            val num = editNum.toLongOrNull()
                            var sec: Long? = null
                            try {
                                if (editDate.isNotBlank()) {
                                    sec = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(editDate)?.time?.div(1000)
                                }
                            } catch (e: Exception) { sec = null }
                            val err = if (num == null && sec == null && editDate.isNotBlank()) "bad_date"
                            else AdminManager.setProfile(ctx, uname, num, sec)
                            withContext(Dispatchers.Main) {
                                if (err == null) {
                                    editTarget = null
                                    load()
                                } else {
                                    Toast.makeText(
                                        ctx,
                                        when (err) {
                                            "num_taken" -> ctx.getString(R.string.profile_err_num_taken)
                                            "bad_num" -> ctx.getString(R.string.profile_err_bad_num)
                                            "bad_date" -> ctx.getString(R.string.profile_err_bad_date)
                                            else -> ctx.getString(R.string.admin_action_fail)
                                        },
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.admin_save_desc)) }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
