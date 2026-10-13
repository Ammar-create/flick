package me.ammar.flick.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import me.ammar.flick.ui.theme.AppShapes
import me.ammar.flick.ui.theme.AppSize

/**
 * Squircle Icon Badge grounding an icon inside a rounded squircle container.
 * Never leaves icons floating nakedly next to typography.
 */
@Composable
fun SquircleIconBadge(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = AppSize.SquircleBadge,
    iconSize: Dp = AppSize.Icon,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = containerColor,
                shape = AppShapes.SquircleBadge
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor
        )
    }
}
