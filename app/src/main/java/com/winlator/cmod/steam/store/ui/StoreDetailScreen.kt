package com.winlator.cmod.steam.store.ui

import com.winlator.cmod.ui.components.glassCardFill
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.steam.store.SteamStoreViewModel
import com.winlator.cmod.steam.store.StoreCatalogQuery
import com.winlator.cmod.steam.store.StoreDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Страница игры магазина в стиле десктоп-Steam: медиа, описание,
// цена/издания, отзывы, языки, требования.
@Composable
fun StoreDetailScreen(
    appId: Int,
    onBack: () -> Unit,
    onFindInLibrary: ((Int) -> Unit)? = null,
    viewModel: SteamStoreViewModel = viewModel(),
    onOpenDetail: (Int) -> Unit = {},
) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(appId) { viewModel.loadDetail(appId) }
    val detail = state.detail?.takeIf { it.appId == appId }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.clearDetail(); onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(detail?.name ?: stringResource(R.string.store_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (detail != null) {
                val wished = state.wishlist.contains(appId)
                IconButton(onClick = { viewModel.toggleWishlist(appId) }) {
                    Icon(if (wished) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = if (wished) Color(0xFFE35D7C) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when {
            state.detailLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            detail == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(state.detailError ?: stringResource(R.string.store_load_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> StoreDetailBody(
                detail = detail,
                reviewType = state.reviewType,
                reviewsLoadingMore = state.reviewsLoadingMore,
                dlcApps = if (state.detail?.appId == appId) state.dlcApps else emptyList(),
                dlcLoading = state.dlcLoading && state.detail?.appId == appId,
                similarApps = if (state.similarForAppId == appId) state.similarApps else emptyList(),
                similarLoading = state.similarLoading && state.similarForAppId == appId,
                onReviewType = viewModel::setReviewType,
                onMoreReviews = viewModel::loadMoreReviews,
                onGenreClick = { genre -> viewModel.openCatalog(StoreCatalogQuery(term = genre, sort = "reviews_DESC")) },
                onOpenStore = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(detail.storeUrl)))
                },
                onFindInLibrary = { onFindInLibrary?.invoke(appId) },
                onOpenDetail = onOpenDetail,
            )
        }
    }
}

@Composable
private fun StoreDetailBody(
    detail: StoreDetail,
    reviewType: String,
    reviewsLoadingMore: Boolean,
    dlcApps: List<com.winlator.cmod.steam.store.StoreApp>,
    dlcLoading: Boolean,
    similarApps: List<com.winlator.cmod.steam.store.StoreApp>,
    similarLoading: Boolean,
    onReviewType: (String) -> Unit,
    onMoreReviews: () -> Unit,
    onGenreClick: (String) -> Unit,
    onOpenStore: () -> Unit,
    onFindInLibrary: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    var galleryIndex by remember(detail.appId) { mutableStateOf(0) }
    val galleryCount = detail.screenshots.size + detail.movies.size
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Медиа-галерея — компактно: высота capped, не весь экран
        if (galleryCount > 0) {
            item(key = "media") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                        val movie = detail.movies.getOrNull(galleryIndex)
                        val shot = if (movie == null) detail.screenshots.getOrNull(galleryIndex - detail.movies.size) else null
                        AsyncImage(
                            model = movie?.thumbnail ?: shot?.full ?: detail.headerImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
                    }
                    if (galleryCount > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(galleryCount) { i ->
                                val m = detail.movies.getOrNull(i)
                                val s = if (m == null) detail.screenshots.getOrNull(i - detail.movies.size) else null
                                AsyncImage(
                                    model = m?.thumbnail ?: s?.thumbnail ?: detail.headerImage,
                                    contentDescription = null,
                                    modifier = Modifier.width(72.dp).aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { galleryIndex = i },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Заголовок + отзывы
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(detail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (detail.shortDescription.isNotBlank()) {
                    Text(detail.shortDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (detail.reviewSummary.totalReviews > 0) {
                        ReviewBadge(scoreDesc = detail.reviewSummary.scoreDesc, percent = detail.reviewSummary.positivePercent, total = detail.reviewSummary.totalReviews)
                    }
                    if (detail.releaseDate.isNotBlank()) {
                        Text(detail.releaseDate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (detail.metacriticScore > 0) {
                        Text("MC ${detail.metacriticScore}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF66CC33))
                    }
                }
                if (detail.developers.isNotEmpty() || detail.publishers.isNotEmpty()) {
                    val devs = detail.developers.joinToString(", ")
                    val pubs = detail.publishers.joinToString(", ")
                    Text(
                        when {
                            devs.isNotEmpty() && pubs.isNotEmpty() -> stringResource(R.string.store_dev_pub, devs, pubs)
                            devs.isNotEmpty() -> stringResource(R.string.store_dev, devs)
                            else -> stringResource(R.string.store_pub, pubs)
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (detail.genres.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(detail.genres) { g ->
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(glassCardFill())
                                    .clickable { onGenreClick(g) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(g, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        // Цена / издания
        item(key = "buy") {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = glassCardFill())) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (detail.comingSoon) stringResource(R.string.store_coming_soon)
                        else stringResource(R.string.store_buy, detail.name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        StorePriceTag(price = detail.price)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onFindInLibrary != null) {
                                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onFindInLibrary() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(stringResource(R.string.store_in_library), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary).clickable { onOpenStore() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(stringResource(R.string.store_in_steam), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    detail.editions.take(4).forEach { ed ->
                        if (ed.title.isNotBlank()) {
                            val priceText = if (ed.price.initial > 0 || ed.price.final > 0) " — ${ed.price.finalText}" else ""
                            Text("• ${ed.title}$priceText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (detail.hasDemo) {
                        if (detail.demoAppId > 0) {
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onOpenDetail(detail.demoAppId) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.store_demo_open), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        } else {
                            Text(stringResource(R.string.store_demo_exists), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        // DLC ряд
        if (detail.dlcAppIds.isNotEmpty()) {
            item(key = "dlc") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.store_addons_count, detail.dlcAppIds.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (dlcLoading && dlcApps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (dlcApps.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(dlcApps, key = { it.id }) { app ->
                                Card(
                                    modifier = Modifier.width(172.dp).clickable { onOpenDetail(app.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                ) {
                                    Column {
                                        coil.compose.AsyncImage(
                                            model = app.bestCapsule,
                                            contentDescription = app.name,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(app.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(app.price.finalText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Похожие игры
        if (similarApps.isNotEmpty() || similarLoading) {
            item(key = "similar") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.store_similar), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (similarLoading && similarApps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(similarApps, key = { it.id }) { app ->
                                Card(
                                    modifier = Modifier.width(172.dp).clickable { onOpenDetail(app.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                ) {
                                    Column {
                                        coil.compose.AsyncImage(
                                            model = app.bestCapsule,
                                            contentDescription = app.name,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(app.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                app.priceText.ifBlank { app.price.finalText },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Фичи
        if (detail.categories.isNotEmpty() || detail.fullController || detail.hasCloud || detail.hasAchievements) {
            item(key = "feat") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.store_features), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    (detail.categories.take(8) + listOfNotNull(
                        stringResource(R.string.store_feat_controller).takeIf { detail.fullController },
                        stringResource(R.string.store_feat_cloud).takeIf { detail.hasCloud },
                        stringResource(R.string.store_feat_ach).takeIf { detail.hasAchievements },
                    )).distinct().take(10).forEach { f ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(f, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        // Об игре
        if (detail.aboutText.isNotBlank()) {
            item(key = "about") {
                var expanded by remember(detail.appId) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.store_about_game), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        detail.aboutText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp)) {
                        Text(if (expanded) stringResource(R.string.store_collapse) else stringResource(R.string.store_expand), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Отзывы
        if (detail.reviews.isNotEmpty() || detail.reviewSummary.totalReviews > 0) {
            item(key = "revtitle") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.store_reviews), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val chips = listOf(
                        "all" to stringResource(R.string.store_review_all),
                        "positive" to stringResource(R.string.store_review_positive),
                        "negative" to stringResource(R.string.store_review_negative),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chips) { (key, label) ->
                            val sel = reviewType == key
                            Box(
                                Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primary else glassCardFill())
                                    .clickable { if (!sel) onReviewType(key) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            detail.reviews.forEachIndexed { i, r ->
                item(key = "rev_${reviewType}_$i") { StoreReviewCard(text = r.text, votedUp = r.votedUp, votesUp = r.votesUp, hours = r.playtimeHours, ts = r.timestamp) }
            }
            if (detail.reviewsCursor.isNotBlank()) {
                item(key = "revmore") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        if (reviewsLoadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(glassCardFill())
                                    .clickable { onMoreReviews() }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                            ) {
                                Text(stringResource(R.string.store_show_more), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        // Языки
        if (detail.supportedLanguages.isNotBlank()) {
            item(key = "lang") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.store_languages), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(detail.supportedLanguages.take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Требования
        if (detail.minRequirements.isNotBlank() || detail.recRequirements.isNotBlank()) {
            item(key = "req") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.store_requirements), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (detail.minRequirements.isNotBlank()) {
                        Text(stringResource(R.string.store_req_min, detail.minRequirements.take(900)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (detail.recRequirements.isNotBlank()) {
                        Text(stringResource(R.string.store_req_rec, detail.recRequirements.take(900)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "pad") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ReviewBadge(scoreDesc: String, percent: Int, total: Int) {
    val color = when {
        percent >= 80 -> Color(0xFF66CC33)
        percent >= 60 -> Color(0xFFA3C53A)
        percent >= 40 -> Color(0xFFC5A53A)
        else -> Color(0xFFCC5533)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(scoreDesc.ifBlank { "$percent%" }, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text("($total)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoreReviewCard(text: String, votedUp: Boolean, votesUp: Int, hours: Float, ts: Long) {
    var expanded by remember(text, ts) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = glassCardFill())) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (votedUp) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown, contentDescription = null, tint = if (votedUp) Color(0xFF66CC33) else Color(0xFFCC5533), modifier = Modifier.width(18.dp))
                Text(if (votedUp) stringResource(R.string.store_recommend_yes) else stringResource(R.string.store_recommend_no), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (votesUp > 0) Text("+ $votesUp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hours > 0) Text(stringResource(R.string.store_hours, hours), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ts > 0) {
                    val d = remember(ts) {
                        runCatching { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts * 1000)) }.getOrDefault("")
                    }
                    if (d.isNotBlank()) Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text.take(if (expanded) 4000 else 400),
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.length > 400) {
                Text(
                    if (expanded) stringResource(R.string.store_collapse) else stringResource(R.string.store_expand),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 2.dp),
                )
            }
        }
    }
}
