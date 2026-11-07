package com.nuvio.app.features.updater

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import org.jetbrains.compose.resources.stringResource

private const val AUTO_DISMISS_MS = 20_000

/** knuvio: the "updated" banner's icon, the same success green in every theme with a white check on top. */
@Composable
internal fun InstalledUpdateIcon() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(MaterialTheme.nuvio.colors.success, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * knuvio: closes the "updated" banner by itself. A ring around the X empties over the time left; it pauses while the
 * release notes are open, so the banner never closes under the user.
 */
@Composable
internal fun AutoDismissCloseButton(paused: Boolean, onDismiss: () -> Unit) {
    val remaining = remember { Animatable(1f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(paused) {
        // Once the time ran out the banner is already leaving, so a late change must not dismiss it again.
        if (!paused && remaining.value > 0f) {
            remaining.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = (remaining.value * AUTO_DISMISS_MS).toInt(),
                    easing = LinearEasing,
                ),
            )
            currentOnDismiss()
        }
    }
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.action_close),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 2.5.dp.toPx()
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * remaining.value,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
