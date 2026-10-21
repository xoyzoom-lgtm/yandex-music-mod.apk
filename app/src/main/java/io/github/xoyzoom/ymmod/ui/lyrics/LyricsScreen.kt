package io.github.xoyzoom.ymmod.ui.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xoyzoom.ymmod.R
import io.github.xoyzoom.ymmod.lyrics.Lyrics
import io.github.xoyzoom.ymmod.lyrics.LrclibClient
import io.github.xoyzoom.ymmod.player.PlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data class Loaded(val lyrics: Lyrics) : LyricsLoad
    data class Failed(val message: String) : LyricsLoad
}

/** Текст текущего трека из LRCLIB; синхронизированные строки подсвечиваются и перематывают по нажатию. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    playerState: PlayerState,
    client: LrclibClient,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val track = playerState.track
    var attempt by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf<LyricsLoad>(LyricsLoad.Loading) }

    // Длительность в ключе не участвует: она приходит чуть позже метаданных.
    LaunchedEffect(track?.title, track?.artist, attempt) {
        if (track == null) return@LaunchedEffect
        load = LyricsLoad.Loading
        load = try {
            LyricsLoad.Loaded(client.find(track.title, track.artist, track.album, playerState.durationMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LyricsLoad.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            track?.title ?: stringResource(R.string.lyrics_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (track != null) {
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                track == null -> Message(stringResource(R.string.lyrics_nothing_playing))
                else -> when (val current = load) {
                    LyricsLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is LyricsLoad.Failed -> Message(stringResource(R.string.lyrics_error, current.message)) {
                        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.action_retry)) }
                    }
                    is LyricsLoad.Loaded -> when (val lyrics = current.lyrics) {
                        is Lyrics.Synced -> SyncedLyrics(lyrics, playerState, onSeek)
                        is Lyrics.Plain -> Text(
                            lyrics.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        )
                        Lyrics.Instrumental -> Message(stringResource(R.string.lyrics_instrumental))
                        Lyrics.NotFound -> Message(stringResource(R.string.lyrics_not_found))
                    }
                }
            }
            Text(
                stringResource(R.string.lyrics_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics.Synced, playerState: PlayerState, onSeek: (Long) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val position = playerState.estimatedPositionMs(now)
    val active = if (playerState.hasPosition) lyrics.indexAt(position) else -1
    val listState = rememberLazyListState()

    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem((active - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            Text(
                text = line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                color = if (index == active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (index < active) 0.45f else 0.75f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = playerState.hasPosition) { onSeek(line.timeMs) },
            )
        }
    }
}

@Composable
private fun Message(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
