package com.winlator.cmod.steam.store.ui

import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.steam.store.SteamStoreViewModel
import com.winlator.cmod.steam.store.StoreApp

// Полнотекстовый каталог Steam: поиск + табы + фильтры + сортировка.
@Composable
fun StoreCatalogScreen(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    viewModel: SteamStoreViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val q = state.catalogQuery
    var termInput by remember(state.catalogOpen) { mutableStateOf(q.term) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.closeCatalog(); onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                stringResource(R.string.store_catalog_title) + if (state.catalogTotal > 0) " • ${state.catalogTotal}" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedTextField(
            value = termInput,
            onValueChange = { termInput = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.store_catalog_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.setCatalogQuery(q.copy(term = termInput))
            }),
            shape = GlassFieldShape,
            colors = glassFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        // Фильтры — сворачиваемая панель, чтобы список занимал весь экран
        var filtersExpanded by remember(state.catalogFiltersExpanded) { mutableStateOf(state.catalogFiltersExpanded) }
        val tabLabels = mapOf(
            "" to stringResource(R.string.store_tab_all),
            "topsellers" to stringResource(R.string.store_tab_leaders),
            "newreleases" to stringResource(R.string.store_tab_new),
            "specials" to stringResource(R.string.store_tab_sales),
            "comingsoon" to stringResource(R.string.store_tab_soon),
        )
        val typeLabels = mapOf(
            "" to stringResource(R.string.store_type_all),
            "game" to stringResource(R.string.store_type_games),
            "demo" to stringResource(R.string.store_type_demo),
            "dlc" to stringResource(R.string.store_type_dlc),
        )
        val sortLabels = mapOf(
            "" to stringResource(R.string.store_sort_relevance),
            "price_ASC" to stringResource(R.string.store_sort_cheap),
            "price_DESC" to stringResource(R.string.store_sort_expensive),
            "reviews_DESC" to stringResource(R.string.store_sort_rating),
            "released_DESC" to stringResource(R.string.store_sort_date),
        )
        val freeLabel = stringResource(R.string.store_tab_free)
        val discountLabel = stringResource(R.string.store_flag_discount)
        val winLabel = stringResource(R.string.store_flag_win)
        val activeSummary = remember(q) {
            buildList {
                if (q.freeOnly) add(freeLabel)
                else if (q.tab.isNotBlank()) add(tabLabels[q.tab] ?: q.tab)
                if (q.type.isNotBlank()) add(typeLabels[q.type] ?: q.type)
                if (q.onlySpecials) add(discountLabel)
                if (q.osWin) add(winLabel)
                if (q.sort.isNotBlank()) add(sortLabels[q.sort] ?: q.sort)
                if (q.term.isNotBlank()) add("«${q.term}»")
            }.joinToString(" • ")
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = glassCardFill()),
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { filtersExpanded = !filtersExpanded }.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.store_filters), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (activeSummary.isNotBlank()) {
                        Text(
                            activeSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Icon(
                        if (filtersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filtersExpanded) {
                    // Табы
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tabs = listOf(
                            "" to tabLabels.getValue(""),
                            "topsellers" to tabLabels.getValue("topsellers"),
                            "newreleases" to tabLabels.getValue("newreleases"),
                            "specials" to tabLabels.getValue("specials"),
                            "comingsoon" to tabLabels.getValue("comingsoon"),
                        )
                        items(tabs) { (key, label) ->
                            CatalogChip(
                                label = label,
                                selected = q.tab == key && !q.freeOnly,
                                onClick = { viewModel.setCatalogQuery(q.copy(tab = key, freeOnly = false)) },
                            )
                        }
                        item {
                            CatalogChip(
                                label = freeLabel,
                                selected = q.freeOnly,
                                onClick = { viewModel.setCatalogQuery(q.copy(tab = "", freeOnly = true)) },
                            )
                        }
                    }
                    // Тип + флаги
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val types = listOf(
                            "" to typeLabels.getValue(""),
                            "game" to typeLabels.getValue("game"),
                            "demo" to typeLabels.getValue("demo"),
                            "dlc" to typeLabels.getValue("dlc"),
                        )
                        items(types) { (key, label) ->
                            CatalogChip(label = label, selected = q.type == key, onClick = { viewModel.setCatalogQuery(q.copy(type = key)) })
                        }
                        item {
                            CatalogChip(label = discountLabel, selected = q.onlySpecials, onClick = { viewModel.setCatalogQuery(q.copy(onlySpecials = !q.onlySpecials)) })
                        }
                        item {
                            CatalogChip(label = winLabel, selected = q.osWin, onClick = { viewModel.setCatalogQuery(q.copy(osWin = !q.osWin)) })
                        }
                    }
                    // Сортировка
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sorts = listOf(
                            "" to sortLabels.getValue(""),
                            "price_ASC" to sortLabels.getValue("price_ASC"),
                            "price_DESC" to sortLabels.getValue("price_DESC"),
                            "reviews_DESC" to sortLabels.getValue("reviews_DESC"),
                            "released_DESC" to sortLabels.getValue("released_DESC"),
                        )
                        items(sorts) { (key, label) ->
                            CatalogChip(label = label, selected = q.sort == key, onClick = { viewModel.setCatalogQuery(q.copy(sort = key)) })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.catalogLoading && state.catalogItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.catalogError && state.catalogItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.store_network_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { viewModel.runCatalog() }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.store_retry), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (state.catalogItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.store_nothing_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.catalogItems, key = { it.id }) { app ->
                    CatalogRow(app = app, onClick = { onOpenDetail(app.id) })
                }
                if (state.catalogTotal == 0 || state.catalogItems.size < state.catalogTotal) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                            if (state.catalogLoadingMore || state.catalogLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Box(
                                    Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(glassCardFill())
                                        .clickable { viewModel.loadMoreCatalog() }
                                        .padding(horizontal = 18.dp, vertical = 10.dp),
                                ) {
                                    Text(stringResource(R.string.store_show_more), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else glassCardFill())
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun CatalogRow(app: StoreApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = app.bestCapsule,
                contentDescription = app.name,
                modifier = Modifier.width(120.dp).aspectRatio(460f / 215f).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(app.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (app.price.hasDiscount) {
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF4C6B22))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "-${app.price.discountPercent}%",
                                color = Color(0xFFBEEE62),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    val price = app.priceText.ifBlank { app.price.finalText }
                    if (price.isNotBlank()) {
                        Text(price, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}
