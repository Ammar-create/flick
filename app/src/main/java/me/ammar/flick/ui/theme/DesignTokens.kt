package me.ammar.flick.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale following the LastChat / MetroList design system.
 */
object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 28.dp
    val xxl = 32.dp
}

/**
 * Shared sizes for chrome, badges, buttons and edge gesture strips.
 */
object AppSize {
    val ChromePill = 48.dp
    val ChromeBar = 56.dp
    val SquircleBadge = 40.dp
    val SmallBadge = 32.dp
    val Icon = 24.dp
    val IconSmall = 18.dp
}

/**
 * Tonal surface and border tokens for AMOLED and dark Plum layering.
 */
object AppSurface {
    const val SoftEdgeAlpha = 0.55f
    val SoftEdgeWidth = 1.dp

    fun softEdgeColor(outline: Color): Color =
        outline.copy(alpha = SoftEdgeAlpha)
}

/**
 * Concentric shape hierarchy for squircle cards, badges, and grouped items.
 */
object AppShapes {
    val CardLarge = RoundedCornerShape(28.dp)
    val CardMedium = RoundedCornerShape(24.dp)
    val CardSmall = RoundedCornerShape(16.dp)

    val ButtonPill = RoundedCornerShape(50)
    val ButtonRounded = RoundedCornerShape(20.dp)
    val ButtonSquared = RoundedCornerShape(12.dp)

    val SquircleBadge = RoundedCornerShape(14.dp)
    val Chip = RoundedCornerShape(12.dp)

    // Grouped panel shapes (for clean grouped settings and lists)
    val ListItemSingle = RoundedCornerShape(24.dp)
    val ListItemFirst = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    val ListItemMiddle = RoundedCornerShape(8.dp)
    val ListItemLast = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

    fun concentric(outer: Dp, inset: Dp, stroke: Dp = 0.dp): Dp =
        (outer - inset - stroke).coerceAtLeast(0.dp)

    fun concentricShape(outer: Dp, inset: Dp, stroke: Dp = 0.dp): RoundedCornerShape =
        RoundedCornerShape(concentric(outer, inset, stroke))
}

val FlickShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
