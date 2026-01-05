package com.jmisabella.zrooms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Displays meditation text in a semi-transparent dark modal window in the lower portion of the screen.
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
                .wrapContentHeight()
                .padding(horizontal = 24.dp) // Margins from screen edges
                .background(
                    color = Color.Black.copy(alpha = 0.55f), // Semi-transparent dark modal
                    shape = RoundedCornerShape(16.dp)        // Rounded corners
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp) // Internal padding for modal content
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
