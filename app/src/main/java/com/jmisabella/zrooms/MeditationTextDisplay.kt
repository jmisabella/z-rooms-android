package com.jmisabella.zrooms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Displays meditation text with a semi-transparent gradient overlay at the bottom of the screen.
 * Shows current and previous phrases with fade animations, similar to Apple Music lyrics.
 */
@Composable
fun MeditationTextDisplay(
    currentPhrase: String,
    previousPhrase: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && (currentPhrase.isNotEmpty() || previousPhrase.isNotEmpty()),
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    // Semi-transparent gradient overlay (dark at bottom, fading to transparent at top)
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // Add padding to text content, not the gradient box
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
            ) {
                // Previous phrase (faded out)
                AnimatedVisibility(
                    visible = previousPhrase.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(400)
                    ),
                    exit = fadeOut(animationSpec = tween(400)) + slideOutVertically(
                        targetOffsetY = { -it / 2 },
                        animationSpec = tween(400)
                    )
                ) {
                    Text(
                        text = previousPhrase,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }

                // Current phrase (fully visible)
                AnimatedVisibility(
                    visible = currentPhrase.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(500)
                    ),
                    exit = fadeOut(animationSpec = tween(400)) + slideOutVertically(
                        targetOffsetY = { -it / 2 },
                        animationSpec = tween(400)
                    )
                ) {
                    Text(
                        text = currentPhrase,
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
