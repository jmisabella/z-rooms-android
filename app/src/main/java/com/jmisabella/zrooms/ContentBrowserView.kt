package com.jmisabella.zrooms

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

enum class ContentTab {
    STORIES,
    POEMS
}

@Composable
fun ContentBrowserView(
    storyManager: CustomStoryManager,
    poetryManager: CustomPoetryManager,
    onDismiss: () -> Unit,
    onPlayStory: (String) -> Unit,
    onPlayPoem: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(ContentTab.STORIES) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFF212121)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab buttons
                    Row {
                        TabButton(
                            text = "Stories",
                            isSelected = selectedTab == ContentTab.STORIES,
                            onClick = { selectedTab = ContentTab.STORIES }
                        )
                        Spacer(Modifier.width(8.dp))
                        TabButton(
                            text = "Poems",
                            isSelected = selectedTab == ContentTab.POEMS,
                            onClick = { selectedTab = ContentTab.POEMS }
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Divider(color = Color(0xFF424242))

                // Content area - show appropriate list
                when (selectedTab) {
                    ContentTab.STORIES -> {
                        CustomStoryListContent(
                            manager = storyManager,
                            onPlay = { text ->
                                onPlayStory(text)
                                onDismiss()
                            }
                        )
                    }
                    ContentTab.POEMS -> {
                        CustomPoemListContent(
                            manager = poetryManager,
                            onPlay = { text ->
                                onPlayPoem(text)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (isSelected) {
                Color(0xFF4CAF50)  // Green when selected
            } else {
                Color(0xFF424242)  // Dark gray when not selected
            },
            contentColor = Color.White
        ),
        modifier = Modifier.height(36.dp)
    ) {
        Text(text)
    }
}
