package com.winlator.cmod.steam.ui

import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassCardFill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.steam.data.SteamFriend
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.getAvatarURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamFriendsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val friends by SteamService.friendsList.collectAsState(emptyList())
    var chatFriend by remember { mutableStateOf<SteamFriend?>(null) }

    LaunchedEffect(Unit) {
        SteamService.requestFriendsInfo()
    }

    if (chatFriend != null) {
        ChatScreen(friend = chatFriend!!, onBack = { chatFriend = null })
        return
    }

    val onlineFriends = friends.filter { it.isOnline && !it.isPlayingGame }
    val playingFriends = friends.filter { it.isPlayingGame }
    val offlineFriends = friends.filter { !it.isOnline }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_friends_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        if (friends.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.steam_friends_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { SteamService.requestFriendsInfo() }
                        }
                    }) {
                        Text(stringResource(R.string.steam_friends_refresh))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (playingFriends.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.steam_friends_section_playing))
                    }
                    items(playingFriends, key = { it.steamId64 }) { friend ->
                        FriendRow(friend = friend, onClick = { chatFriend = friend })
                    }
                }

                if (onlineFriends.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.steam_friends_section_online))
                    }
                    items(onlineFriends, key = { it.steamId64 }) { friend ->
                        FriendRow(friend = friend, onClick = { chatFriend = friend })
                    }
                }

                if (offlineFriends.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.steam_friends_section_offline))
                    }
                    items(offlineFriends, key = { it.steamId64 }) { friend ->
                        FriendRow(friend = friend, onClick = { chatFriend = friend })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FriendRow(friend: SteamFriend, onClick: () -> Unit = {}) {
    val avatarUrl = friend.avatarHash.getAvatarURL()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(glassCardFill()).border(glassCardBorder(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = friend.name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (friend.isPlayingGame && friend.gameName.isNotBlank()) {
                    Text(
                        text = friend.gameName,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = when (friend.state.code()) {
                            1 -> "Online"
                            2 -> "Busy"
                            3 -> "Away"
                            4 -> "Snooze"
                            5 -> "Looking to Trade"
                            6 -> "Looking to Play"
                            else -> "Offline"
                        },
                        color = if (friend.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            friend.isPlayingGame -> MaterialTheme.colorScheme.tertiary
                            friend.isOnline -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
            )
        }
    }
}
