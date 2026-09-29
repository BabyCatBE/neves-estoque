package com.babycatbe.nevesestoque.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/** Instant navigation; only existing grouped content changes size, without fading or scaling.
 * Compose's duration scale honors the Android animator-duration preference, including zero.
 * Interaction is never delayed until animation completes.
 */
object NevesMotion {
    val navigationEnter = EnterTransition.None
    val navigationExit = ExitTransition.None
    private const val ExpansionMillis = 120
    val expand = expandVertically(animationSpec = tween(ExpansionMillis, easing = FastOutSlowInEasing), expandFrom = Alignment.Top)
    val collapse = shrinkVertically(animationSpec = tween(ExpansionMillis, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
}

@Composable
fun NevesExpandedContent(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = NevesMotion.expand, exit = NevesMotion.collapse) { content() }
}
