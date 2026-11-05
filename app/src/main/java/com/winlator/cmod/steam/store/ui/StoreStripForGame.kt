package com.winlator.cmod.steam.store.ui

import com.winlator.cmod.ui.components.glassCardFill
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.steam.store.SteamStoreApi
import com.winlator.cmod.steam.store.StoreDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Магазинная полоса внутри карточки игры библиотеки:
// цена/скидка, отзывы, кнопка страницы в Steam.
@Composable
fun StoreStripForGame(appId: Int) {
    val context = LocalContext.current
    var loading by remember(appId) { mutableStateOf(true) }
    var detail by remember(appId) { mutableStateOf<StoreDetail?>(null) }
    LaunchedEffect(appId) {
        loading = true
        detail = withContext(Dispatchers.IO) {
            runCatching { SteamStoreApi.loadDetail(context, appId) }.getOrNull()
        }
        loading = false
    }
    if (loading) {
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp))
        }
        return
    }
    val d = detail ?: return
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = glassCardFill()),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Страница в магазине", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (d.reviewSummary.totalReviews > 0) {
                Text(
                    "${d.reviewSummary.scoreDesc} (${d.reviewSummary.totalReviews})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (d.genres.isNotEmpty()) {
                Text(
                    d.genres.take(4).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.storeUrl)))
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Открыть в Steam", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
