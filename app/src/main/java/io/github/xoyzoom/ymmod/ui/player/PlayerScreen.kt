package io.github.xoyzoom.ymmod.ui.player

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.xoyzoom.ymmod.R
import kotlin.math.roundToInt

/**
 * Сайт на весь экран, как в официальном приложении: без своей панели сверху.
 * Под системными панелями — цвет фона страницы, поэтому их не видно.
 */
@Composable
fun PlayerScreen(
    webView: WebView,
    progress: Int,
    pageBackground: Color?,
    showFloatingButton: Boolean,
    onOpenModMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(pageBackground ?: MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
        ) {
            AndroidView(
                factory = {
                    // WebView создаётся один раз в активити и переживает пересоздание композиции.
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Тонкая полоска загрузки поверх страницы — пропадает, как только страница загрузилась.
            AnimatedVisibility(
                visible = progress in 1..99,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }

            if (showFloatingButton) FloatingModButton(onClick = onOpenModMenu)
        }
    }
}

/** Маленькая полупрозрачная кнопка меню мода; её можно перетащить в удобное место. */
@Composable
private fun FloatingModButton(onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sizePx = with(density) { BUTTON_SIZE.toPx() }
        val maxX = constraints.maxWidth - sizePx
        val maxY = constraints.maxHeight - sizePx
        // Доли экрана, чтобы позиция не терялась при повороте.
        var fractionX by rememberSaveable { mutableFloatStateOf(1f) }
        var fractionY by rememberSaveable { mutableFloatStateOf(0.35f) }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 4.dp,
            modifier = Modifier
                .offset { IntOffset((fractionX * maxX).roundToInt(), (fractionY * maxY).roundToInt()) }
                .size(BUTTON_SIZE)
                .alpha(0.75f)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (maxX > 0) fractionX = ((fractionX * maxX + drag.x) / maxX).coerceIn(0f, 1f)
                        if (maxY > 0) fractionY = ((fractionY * maxY + drag.y) / maxY).coerceIn(0f, 1f)
                    }
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = stringResource(R.string.action_mod_menu),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private val BUTTON_SIZE = 40.dp
