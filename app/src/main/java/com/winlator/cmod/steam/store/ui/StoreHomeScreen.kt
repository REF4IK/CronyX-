package com.winlator.cmod.steam.store.ui

import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassCardFill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.steam.store.StoreApp
import com.winlator.cmod.steam.store.SteamStoreViewModel
import kotlinx.coroutines.delay

// Главная витрины: hero-карусель + полки категорий.
@Composable
fun StoreHomeScreen(
    onOpenDetail: (Int) -> Unit,
    viewModel: SteamStoreViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsState()
    LaunchedEffect(Unit) {
        if (!state.homeLoading && state.home.hero.isEmpty() && state.home.categories.isEmpty()) {
            viewModel.refreshHome()
        }
    }
    if (state.homeLoading && state.home.hero.isEmpty() && state.home.categories.isEmpty()) {
        StoreLoadingSkeleton()
        return
    }
    if (state.homeError != null && state.home.hero.isEmpty() && state.home.categories.isEmpty()) {
        StoreErrorBlock(message = state.homeError ?: "", onRetry = viewModel::refreshHome)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (state.home.hero.isNotEmpty()) {
            item(key = "hero") {
                StoreHeroCarousel(hero = state.home.hero, onOpenDetail = onOpenDetail)
            }
        }
        state.home.dailyDeal?.let { deal ->
            item(key = "daily") {
                StoreDailyDeal(deal = deal, onOpenDetail = onOpenDetail)
            }
        }
        state.home.categories.forEach { cat ->
            if (cat.key == "genres") {
                item(key = "genres") {
                    StoreGenreChips(names = cat.items.map { it.name })
                }
            } else {
                item(key = "shelf_${cat.key}") {
                    StoreShelf(title = cat.title, items = cat.items, onOpenDetail = onOpenDetail)
                }
            }
        }
    }
}

@Composable
private fun StoreHeroCarousel(hero: List<StoreApp>, onOpenDetail: (Int) -> Unit) {
    val pager = rememberPagerState(pageCount = { hero.size })
    LaunchedEffect(hero.size) {
        if (hero.size < 2) return@LaunchedEffect
        while (true) {
            delay(5000)
            val next = (pager.currentPage + 1) % hero.size
            runCatching { pager.animateScrollToPage(next) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.store_home_popular),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            val app = hero[page]
            StoreHeroCard(app = app, onClick = { onOpenDetail(app.id) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(hero.size.coerceAtMost(12)) { i ->
                Box(
                    modifier = Modifier.padding(horizontal = 3.dp).size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pager.currentPage % hero.size.coerceAtMost(12)) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun StoreHeroCard(app: StoreApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = app.bestCapsule,
                contentDescription = app.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
                ),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(app.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (app.headline.isNotBlank()) {
                        Text(app.headline, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    StorePriceTag(price = app.price, onDark = true)
                }
            }
        }
    }
}

@Composable
fun StorePriceTag(price: com.winlator.cmod.steam.store.StorePrice, onDark: Boolean = false) {
    // onDark = поверх тёмной картинки (hero), иначе цвета под тему (видно и в светлой, и в чёрной)
    val mainColor = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val subColor = if (onDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (price.hasDiscount) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF4C6B22)).padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text("-${price.discountPercent}%", color = Color(0xFFBEEE62), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Text(price.initialText, color = subColor, style = MaterialTheme.typography.labelSmall)
        }
        Text(price.finalText, color = mainColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StoreDailyDeal(deal: StoreApp, onOpenDetail: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).clickable { onOpenDetail(deal.id) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = glassCardFill()),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = deal.bestCapsule,
                contentDescription = deal.name,
                modifier = Modifier.width(140.dp).aspectRatio(460f / 215f).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.store_home_daily), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(deal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StorePriceTag(price = deal.price)
            }
        }
    }
}

@Composable
private fun StoreShelf(title: String, items: List<StoreApp>, onOpenDetail: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items.distinctBy { it.id }, key = { it.id }) { app ->
                StoreShelfCard(app = app, onClick = { onOpenDetail(app.id) })
            }
        }
    }
}

@Composable
private fun StoreShelfCard(app: StoreApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(172.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = app.bestCapsule,
                    contentDescription = app.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f),
                    contentScale = ContentScale.Crop,
                )
                if (app.price.hasDiscount) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomStart).clip(RoundedCornerShape(topEnd = 6.dp)).background(Color(0xFF4C6B22)).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("-${app.price.discountPercent}%", color = Color(0xFFBEEE62), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text(app.price.finalText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StoreGenreChips(names: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.store_home_genres), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(names) { n ->
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(glassCardFill()).border(glassCardBorder(), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(n, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun StoreLoadingSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(glassCardFill()).border(glassCardBorder(), RoundedCornerShape(14.dp)))
        }
    }
}

@Composable
private fun StoreErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.store_home_failed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message.take(200), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
        }
    }
}
