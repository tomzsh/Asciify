package com.asciify.studio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.asciify.studio.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.asciify.studio.engine.CpuAsciiRenderer
import com.asciify.studio.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun StyleTile(style: AsciiStyle, selected: Boolean, favorite: Boolean, sample: Bitmap,
    modifier: Modifier = Modifier, large: Boolean = false, onSelect: () -> Unit, onFavorite: () -> Unit) {
    val preview by produceState<Bitmap?>(null, style.id, sample) {
        value = withContext(Dispatchers.Default) {
            CpuAsciiRenderer(AsciiSettings(styleId = style.id, columns = 48)).use { it.render(sample, 280, 210) }
        }
    }
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Panel)
        .border(if (selected) 2.dp else 1.dp, if (selected) Mint else Line, RoundedCornerShape(14.dp))
        .clickable(onClick = onSelect).semantics { this.selected = selected; contentDescription = "${style.name} style" }) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (large) 1.4f else 1.25f).background(Color(style.background))) {
            preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = Mint,
                modifier = Modifier.padding(10.dp).background(Ink, CircleShape).size(20.dp))
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                Box(Modifier.size(28.dp).background(Ink.copy(alpha = 0.85f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        if (favorite) "Unfavorite ${style.name}" else "Favorite ${style.name}",
                        tint = if (favorite) Mint else Color(0xFFE8EDE4), modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(style.name, style = MaterialTheme.typography.labelLarge, color = if (selected) Mint else MaterialTheme.colorScheme.onSurface)
            if (large) Text(style.category, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable fun StyleStrip(selected: String, favorites: Set<String>, sample: Bitmap, onSelect: (String) -> Unit, onFavorite: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Styles.all, key = { it.id }) { style ->
            StyleTile(style, style.id == selected, style.id in favorites, sample, Modifier.width(128.dp),
                onSelect = { onSelect(style.id) }, onFavorite = { onFavorite(style.id) })
        }
    }
}

@Composable fun StyleGallery(selected: String, favorites: Set<String>, sample: Bitmap, onSelect: (String) -> Unit, onFavorite: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("THE STYLE COLLECTION", Mint)
            Text("Find your signature.", style = MaterialTheme.typography.headlineLarge)
            Text("${Styles.all.size} looks. ASCII, pixels, and printed textures.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
            label = { Text("Search styles") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Styles.categories) { name -> FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) }) }
        }
        val styles = Styles.all.filter {
            (category == "All" || it.category == category || category == "Favorites" && it.id in favorites) &&
                (search.isBlank() || "${it.name} ${it.description} ${it.category}".contains(search.trim(), ignoreCase = true))
        }
        if (styles.isEmpty()) StudioCard {
            Icon(Icons.Outlined.StarBorder, null, tint = Mint)
            Text(if (category == "Favorites" && search.isBlank()) "Your favorites start here" else "No matching styles", style = MaterialTheme.typography.titleMedium)
            Text(if (category == "Favorites" && search.isBlank()) "Tap the star on any style to keep it close." else "Try another search or choose a different category.", color = Muted)
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.weight(1f)) {
            items(styles, key = { it.id }) { style ->
                StyleTile(style, style.id == selected, style.id in favorites, sample, large = true,
                    onSelect = { onSelect(style.id) }, onFavorite = { onFavorite(style.id) })
            }
        }
    }
}
