package hivens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.compose.LocalPlatformContext

import hivens.core.data.NewsItem
import hivens.ui.components.GlassCard
import hivens.ui.theme.CelestiaTheme

@Composable
fun NewsScreen( // TODO: НЕ ЗАБЫТЬ РЕАЛИЗОВАТЬ ЭТУ ЧУДЕСНУЮ ФУНКЦИЮ.
    news: List<NewsItem>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Хедер
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CelestiaTheme.colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "НОВОСТИ ПРОЕКТА",
                style = MaterialTheme.typography.h4,
                color = CelestiaTheme.colors.textPrimary
            )
        }

        // Список новостей
        GlassCard(modifier = Modifier.fillMaxSize()) {
            if (news.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Загрузка новостей...",
                        color = CelestiaTheme.colors.textSecondary,
                        style = MaterialTheme.typography.h6
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(news) { item ->
                        NewsCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun NewsCard(item: NewsItem) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        backgroundColor = CelestiaTheme.colors.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Картинка новости через Coil
            if (item.imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CelestiaTheme.colors.background.copy(alpha = 0.5f))
                )
            } else {
                // Заглушка, если картинки нет
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CelestiaTheme.colors.surface.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("NO IMG", color = CelestiaTheme.colors.textSecondary)
                }
            }

            // Текст новости
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.h6,
                            color = CelestiaTheme.colors.textPrimary,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = item.date,
                            style = MaterialTheme.typography.caption,
                            color = CelestiaTheme.colors.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.body2,
                    color = CelestiaTheme.colors.textSecondary,
                    maxLines = 2
                )
            }
        }
    }
}
