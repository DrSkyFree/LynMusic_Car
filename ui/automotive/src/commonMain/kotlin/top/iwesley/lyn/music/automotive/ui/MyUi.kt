package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RecentActors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.my.MyIntent
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.automotive.ui.platform.PlatformBackHandler
import top.iwesley.lyn.music.ui.mainShellColors

@Composable
internal fun MyTab(
    platform: PlatformDescriptor,
    state: MyState,
    onMyIntent: (MyIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenLibraryRoot: (LibraryBrowserRootView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMobile = false
    var detailPage by rememberSaveable { mutableStateOf<MyDetailPage?>(null) }
    val mainListState = rememberLazyListState()
    PlatformBackHandler(enabled = detailPage != null) {
        detailPage = null
    }
    when (detailPage) {
        MyDetailPage.RecentTracks -> {
            RecentTracksDetail(
                recentTracks = state.recentTracks,
                isLoading = state.isLoadingContent,
                showDuration = !isMobile,
                onBack = { detailPage = null },
                onPlayTrack = { index ->
                    buildRecentTracksPlayIntent(state.recentTracks, index)?.let(onPlayerIntent)
                },
                modifier = modifier,
            )
            return
        }

        null -> Unit
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = mainListState,
        contentPadding = dockContentPadding(PaddingValues(
            horizontal = if (isMobile) 16.dp else 32.dp,
            vertical = if (isMobile) 16.dp else 28.dp,
        )),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            // 页面右上角的完全退出入口:48dp+ 触控区,主驾可视。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = { onSettingsIntent(SettingsIntent.RequestCompleteExit) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = uiString(Res.string.my_exit_app_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        state.message?.let { message ->
            item {
                BannerCard(
                    message = message,
                    onDismiss = { onMyIntent(MyIntent.ClearMessage) },
                )
            }
        }
        item {
            MyEntryCard(
                title = uiString(Res.string.playlists_title),
                subtitle = uiString(Res.string.my_entry_playlists_hint),
                icon = Icons.Rounded.QueueMusic,
                onClick = onOpenPlaylists,
            )
        }
        item {
            MyEntryCard(
                title = uiString(Res.string.favorites_likes_title),
                subtitle = uiString(Res.string.my_entry_favorites_hint),
                icon = Icons.Rounded.FavoriteBorder,
                onClick = onOpenFavorites,
            )
        }
        item {
            MyEntryCard(
                title = uiString(Res.string.library_albums_title),
                subtitle = uiString(Res.string.my_entry_albums_hint),
                icon = Icons.Rounded.Album,
                onClick = { onOpenLibraryRoot(LibraryBrowserRootView.Albums) },
            )
        }
        item {
            MyEntryCard(
                title = uiString(Res.string.library_artists_title),
                subtitle = uiString(Res.string.my_entry_artists_hint),
                icon = Icons.Rounded.RecentActors,
                onClick = { onOpenLibraryRoot(LibraryBrowserRootView.Artists) },
            )
        }
        item {
            RecentTracksSection(
                recentTracks = state.recentTracks,
                isLoading = state.isLoadingContent,
                isMobile = isMobile,
                showDuration = !isMobile,
                onPlayTrack = { index ->
                    buildRecentTracksPlayIntent(state.recentTracks, index)?.let(onPlayerIntent)
                },
                onViewAll = { detailPage = MyDetailPage.RecentTracks },
            )
        }
    }
}

@Composable
private fun MyEntryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    MainShellElevatedCard(shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = shellColors.secondaryText,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = shellColors.secondaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private enum class MyDetailPage {
    RecentTracks,
}







@Composable
private fun DailyRecommendationArtwork(
    artworkLocator: String?,
    artworkCacheKey: String? = null,
    modifier: Modifier,
    shape: RoundedCornerShape,
    accentColor: Color,
) {
    Box(
        modifier = modifier
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        LynArtworkImage(
            artworkLocator = artworkLocator,
            contentDescription = null,
            artworkCacheKey = artworkCacheKey,
            maxDecodeSizePx = ArtworkDecodeSize.Card,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}


@Composable
private fun RecentTracksDetail(
    recentTracks: List<RecentTrack>,
    isLoading: Boolean,
    showDuration: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val indexColumnWidth = rememberListIndexColumnWidth(recentTracks.size)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = dockContentPadding(PaddingValues(
            horizontal = if (showDuration) 32.dp else 16.dp,
            vertical = if (showDuration) 28.dp else 18.dp,
        )),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MyDetailHeader(
                title = uiString(Res.string.recent_tracks_title),
                subtitle = if (recentTracks.isEmpty()) "" else uiString(Res.plurals.common_track_count_short, (recentTracks.size).toInt(), recentTracks.size),
                onBack = onBack,
            )
        }
        when {
            isLoading -> item { RecentLoadingRow() }
            recentTracks.isEmpty() -> item {
                InlineEmptyState(
                    title = uiString(Res.string.recent_tracks_empty_title),
                    body = uiString(Res.string.recent_tracks_empty_hint),
                )
            }
            else -> itemsIndexed(recentTracks, key = { _, recentTrack -> recentTrack.track.id }) { index, recentTrack ->
                RecentTrackRow(
                    recentTrack = recentTrack,
                    index = index,
                    indexColumnWidth = indexColumnWidth,
                    showDuration = showDuration,
                    onClick = { onPlayTrack(index) },
                )
            }
        }
    }
}


@Composable
private fun MyDetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MyDetailBackButton(onBack = onBack)
        Box(modifier = Modifier.weight(1f)) {
            SectionTitle(
                title = title,
                subtitle = subtitle,
            )
        }
    }
}

@Composable
private fun MyDetailBackButton(
    onBack: () -> Unit,
) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = uiString(Res.string.common_back),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RecentTracksSection(
    recentTracks: List<RecentTrack>,
    isLoading: Boolean,
    isMobile: Boolean,
    showDuration: Boolean,
    onPlayTrack: (Int) -> Unit,
    onViewAll: () -> Unit,
) {
    val previewTracks = recentPreviewItems(recentTracks, isMobile)
    val indexColumnWidth = rememberListIndexColumnWidth(previewTracks.size)
    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecentSectionHeader(
            title = uiString(Res.string.recent_tracks_title),
            subtitle = if (recentTracks.isEmpty()) "" else uiString(Res.plurals.common_track_count_short, (recentTracks.size).toInt(), recentTracks.size),
            showViewAll = !isLoading && recentTracks.isNotEmpty(),
            onViewAll = onViewAll,
        )
        when {
            isLoading -> RecentLoadingRow()
            recentTracks.isEmpty() -> InlineEmptyState(
                title = uiString(Res.string.recent_tracks_empty_title),
                body = uiString(Res.string.recent_tracks_empty_hint),
            )
            isMobile -> previewTracks.forEachIndexed { index, recentTrack ->
                RecentTrackRow(
                    recentTrack = recentTrack,
                    index = index,
                    indexColumnWidth = indexColumnWidth,
                    showDuration = showDuration,
                    onClick = { onPlayTrack(index) },
                )
            }
            else -> RecentTrackPreviewRow(
                recentTracks = previewTracks,
                onPlayTrack = { index -> onPlayTrack(index) },
            )
        }
    }
}

@Composable
private fun RecentTrackPreviewRow(
    recentTracks: List<RecentTrack>,
    onPlayTrack: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        recentTracks.forEachIndexed { index, recentTrack ->
            RecentTrackPreviewCard(
                recentTrack = recentTrack,
                modifier = Modifier.weight(1f),
                onClick = { onPlayTrack(index) },
            )
        }
        repeat((recentPreviewLimit(isMobile = false) - recentTracks.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecentTrackPreviewCard(
    recentTrack: RecentTrack,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val track = recentTrack.track
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            DailyRecommendationArtwork(
                artworkLocator = track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(track),
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                accentColor = accentColor,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = track.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = buildTrackMetadataSubtitle(track),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}


@Composable
private fun RecentSectionHeader(
    title: String,
    subtitle: String,
    showViewAll: Boolean,
    onViewAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SectionTitle(
                title = title,
                subtitle = subtitle,
            )
        }
        if (showViewAll) {
            Text(
                text = uiString(Res.string.recent_view_all_action),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RecentTrackRow(
    recentTrack: RecentTrack,
    index: Int,
    indexColumnWidth: Dp,
    showDuration: Boolean,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    val track = recentTrack.track
    Column(modifier = Modifier.fillMaxWidth()) {
        TrackActionContainer(
            track = track,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .padding(start = 0.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ListIndexColumn(
                index = index,
                width = indexColumnWidth,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TrackArtworkThumbnail(
                artworkLocator = track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(track),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildRecentTrackSubtitle(track, recentTrack),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (showDuration) {
                Text(
                    text = formatDuration(track.durationMs),
                    modifier = Modifier.width(56.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 88.dp)
                .height(1.dp)
                .background(shellColors.cardBorder),
        )
    }
}





internal fun recentPreviewLimit(isMobile: Boolean): Int {
    return if (isMobile) 3 else 6
}

internal fun recentPreviewUsesScrolling(isMobile: Boolean): Boolean {
    return false
}

internal fun <T> recentPreviewItems(
    items: List<T>,
    isMobile: Boolean,
): List<T> {
    return items.take(recentPreviewLimit(isMobile))
}

internal fun buildRecentTracksPlayIntent(
    recentTracks: List<RecentTrack>,
    startIndex: Int,
): PlayerIntent.PlayTracks? {
    if (recentTracks.isEmpty() || startIndex !in recentTracks.indices) return null
    return PlayerIntent.PlayTracks(
        tracks = recentTracks.map { it.track },
        startIndex = startIndex,
    )
}


@Composable
private fun RecentLoadingRow(text: String = uiString(Res.string.recent_plays_loading)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineEmptyState(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun buildRecentTrackSubtitle(
    track: Track,
    recentTrack: RecentTrack,
): String {
    return "${buildTrackMetadataSubtitle(track)} · ${formatRecentStats(recentTrack.playCount, recentTrack.lastPlayedAt)}"
}

@Composable
private fun buildTrackMetadataSubtitle(track: Track): String {
    val artist = track.artistName ?: uiString(Res.string.common_unknown_artist)
    val album = track.albumTitle?.takeIf { it.isNotBlank() }
    return if (album == null) artist else "$artist · $album"
}


internal fun dailyRecommendationCarouselItems(
    tracks: List<Track>,
): List<Track> = tracks.take(DAILY_RECOMMENDATION_CAROUSEL_LIMIT)


@Composable
private fun formatRecentStats(
    playCount: Int,
    lastPlayedAt: Long,
): String {
    return uiString(Res.plurals.recent_track_play_summary, (playCount.coerceAtLeast(1)).toInt(), playCount.coerceAtLeast(1), formatRecentPlayedAt(lastPlayedAt))
}

private fun formatRecentPlayedAt(lastPlayedAt: Long): String {
    return runCatching {
        Instant.fromEpochMilliseconds(lastPlayedAt)
            .toString()
            .replace('T', ' ')
            .substringBefore('.')
            .removeSuffix("Z")
            .take(16)
    }.getOrDefault("")
}

private const val DAILY_RECOMMENDATION_CAROUSEL_LIMIT = 10
