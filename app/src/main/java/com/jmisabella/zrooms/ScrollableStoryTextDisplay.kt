package com.jmisabella.zrooms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import kotlinx.coroutines.launch

/**
 * Data class representing a paragraph for display
 */
private data class ParagraphItem(
    val sentences: List<String>,
    val isCurrentParagraph: Boolean
)

/**
 * Groups phrase history into paragraphs based on <<PB>> markers
 */
private fun groupIntoParagraphs(phraseHistory: List<String>, currentPhrase: String): List<ParagraphItem> {
    if (phraseHistory.isEmpty()) return emptyList()

    val paragraphs = mutableListOf<ParagraphItem>()
    var currentParagraphSentences = mutableListOf<String>()

    for ((index, phrase) in phraseHistory.withIndex()) {
        val isCurrentPhrase = phrase == currentPhrase && index == phraseHistory.size - 1
        val cleanPhrase = phrase.replace("<<PB>>", "")

        currentParagraphSentences.add(cleanPhrase)

        // Check if this phrase ends a paragraph
        if (phrase.contains("<<PB>>")) {
            // This is the last sentence in a paragraph
            val isCurrentParagraph = isCurrentPhrase ||
                (currentParagraphSentences.any { it.replace("<<PB>>", "") == currentPhrase })

            paragraphs.add(ParagraphItem(
                sentences = currentParagraphSentences.toList(),
                isCurrentParagraph = isCurrentParagraph
            ))
            currentParagraphSentences = mutableListOf()
        }
    }

    // Add any remaining sentences as the current paragraph
    if (currentParagraphSentences.isNotEmpty()) {
        paragraphs.add(ParagraphItem(
            sentences = currentParagraphSentences.toList(),
            isCurrentParagraph = true
        ))
    }

    return paragraphs
}

/**
 * Scrollable story text display with phrase history.
 * Shows all spoken phrases with the ability to scroll back through history.
 * Auto-scrolls to new phrases when user is at the bottom.
 * Shows "New text" indicator when scrolled up and new content arrives.
 * Historical paragraphs are grouped and displayed as blocks.
 */
@Composable
fun ScrollableStoryTextDisplay(
    phraseHistory: List<String>,
    currentPhrase: String,
    hasNewContent: Boolean,
    onHasNewContentChange: (Boolean) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var userHasScrolledUp by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Determine caption box height based on orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val captionHeight = if (isLandscape) 100.dp else 300.dp

    // Group phrases into paragraphs
    val paragraphs = remember(phraseHistory, currentPhrase) {
        groupIntoParagraphs(phraseHistory, currentPhrase)
    }

    // Derive whether we're at the bottom from scroll state
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = layoutInfo.totalItemsCount

            // We're at bottom if the last item is visible
            lastVisibleItem?.index == totalItems - 1 || totalItems <= 1
        }
    }

    // Reset userHasScrolledUp when we reach the bottom
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            userHasScrolledUp = false
            onHasNewContentChange(false)
        }
    }

    // Auto-scroll to bottom when new phrase arrives (unless user has scrolled up)
    LaunchedEffect(phraseHistory.size) {
        if (phraseHistory.isNotEmpty() && !userHasScrolledUp) {
            // Scroll to the last item (paragraphs.size for the spacer item)
            scrollState.scrollToItem(paragraphs.size)
        } else if (!isAtBottom && phraseHistory.isNotEmpty()) {
            // User is scrolled up and new content arrived
            onHasNewContentChange(true)
        }
    }

    AnimatedVisibility(
        visible = isVisible && phraseHistory.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .height(captionHeight)
                .padding(horizontal = 24.dp)
        ) {
            // Semi-transparent dark background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            // Scrollable text content
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Detect when user manually scrolls
                        detectDragGestures(
                            onDragStart = {
                                // User started dragging - mark as scrolled up
                                userHasScrolledUp = true
                            },
                            onDrag = { _, _ -> }
                        )
                    }
            ) {
                itemsIndexed(paragraphs) { _, paragraph ->
                    if (paragraph.isCurrentParagraph) {
                        // Current paragraph: show each sentence individually
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            paragraph.sentences.forEachIndexed { _, sentence ->
                                val isCurrentSentence = sentence == currentPhrase
                                Text(
                                    text = sentence,
                                    fontSize = if (isCurrentSentence) 18.sp else 16.sp,
                                    fontWeight = if (isCurrentSentence) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isCurrentSentence) Color.White else Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Left,
                                    lineHeight = if (isCurrentSentence) 26.sp else 22.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // Historical paragraph: combine sentences into a block
                        Text(
                            text = paragraph.sentences.joinToString(" "),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Left,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Bottom anchor for scrolling
                item {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }

            // "New text" indicator button
            if (hasNewContent && userHasScrolledUp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                // Scroll to bottom and reset state
                                scrollState.animateScrollToItem(paragraphs.size)
                                userHasScrolledUp = false
                                onHasNewContentChange(false)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.9f),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "Scroll to new text",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "New text",
                                color = Color.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
