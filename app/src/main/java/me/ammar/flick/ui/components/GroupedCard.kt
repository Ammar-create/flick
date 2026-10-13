package me.ammar.flick.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import me.ammar.flick.ui.theme.AppShapes
import me.ammar.flick.ui.theme.AppSpacing
import me.ammar.flick.ui.theme.AppSurface

enum class GroupedPosition {
    Single,
    First,
    Middle,
    Last
}

/**
 * Grouped squircle panel following the MetroList and LastChat design system.
 * Groups related options inside rounded squircle containers with subtle soft-edge borders.
 */
@Composable
fun GroupedCard(
    position: GroupedPosition = GroupedPosition.Single,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape: Shape = when (position) {
        GroupedPosition.Single -> AppShapes.ListItemSingle
        GroupedPosition.First -> AppShapes.ListItemFirst
        GroupedPosition.Middle -> AppShapes.ListItemMiddle
        GroupedPosition.Last -> AppShapes.ListItemLast
    }

    val border = BorderStroke(
        width = AppSurface.SoftEdgeWidth,
        color = AppSurface.softEdgeColor(MaterialTheme.colorScheme.outlineVariant)
    )

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                content = content
            )
        }
    }
}
