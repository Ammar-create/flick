package me.ammar.flick.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ammar.flick.ui.theme.AppShapes
import me.ammar.flick.ui.theme.AppSize
import me.ammar.flick.ui.theme.AppSpacing

/**
 * Reusable Morphing Pill Button that smoothly stretches horizontally
 * when touched or held down, with sibling compression and physics-based spring recovery.
 */
@Composable
fun RowScope.MorphingPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleWeight: Float = 1.0f,
    pressedWeight: Float = 2.4f,
    containerColor: Color,
    contentColor: Color,
    height: Dp = AppSize.ChromePill,
    shape: Shape = AppShapes.ButtonPill,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentWeight by animateFloatAsState(
        targetValue = if (isPressed) pressedWeight else idleWeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillMorphWeight"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .weight(currentWeight)
            .height(height),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
