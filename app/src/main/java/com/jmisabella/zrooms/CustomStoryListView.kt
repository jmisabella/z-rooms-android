package com.jmisabella.zrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

@Composable
fun CustomStoryListView(
    manager: CustomStoryManager,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFF212121)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Stories",
                        style = MaterialTheme.typography.h6,
                        color = Color.White
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Divider(color = Color(0xFF424242))

                // Reuse content component
                CustomStoryListContent(
                    manager = manager,
                    onPlay = { text ->
                        onPlay(text)
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * Reusable content component for story list (used in both dialog and tab views)
 */
@Composable
fun CustomStoryListContent(
    manager: CustomStoryManager,
    onPlay: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    var editingStory by remember { mutableStateOf<CustomStory?>(null) }
    var showStoryText by remember {
        mutableStateOf(prefs.getBoolean("showStoryText", true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (manager.stories.isNotEmpty()) {
                IconButton(
                    onClick = {
                        manager.getRandomStory()?.let { story ->
                            onPlay(story.text)
                        }
                    }
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Play Random",
                        tint = Color(0xFFFFB74D)
                    )
                }
            }

            IconButton(
                onClick = {
                    showStoryText = !showStoryText
                    prefs.edit()
                        .putBoolean("showStoryText", showStoryText)
                        .apply()
                }
            ) {
                Icon(
                    Icons.Filled.ClosedCaption,
                    contentDescription = if (showStoryText) "Hide Story Text" else "Show Story Text",
                    tint = if (showStoryText) Color(0xFF64B5F6) else Color(0xFF757575)
                )
            }
        }

        Divider(color = Color(0xFF424242))

        // Add New Story button (always visible)
        if (manager.canAddMore) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        editingStory = CustomStory(title = "", text = "")
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = "Add New Story",
                    tint = Color(0xFF4CAF50)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Add New Story",
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.body1
                )
            }
            Divider(color = Color(0xFF424242))
        }

        // Story List
        if (manager.stories.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.FormatQuote,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = Color.Gray
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "No Custom Stories",
                    style = MaterialTheme.typography.h6,
                    color = Color.Gray
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Create your own guided story\nwith custom pauses and pacing",
                    style = MaterialTheme.typography.body2,
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        editingStory = CustomStory(title = "", text = "")
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create First Story", color = Color.White)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(manager.stories) { story ->
                    StoryRow(
                        story = story,
                        onPlay = {
                            onPlay(story.text)
                        },
                        onEdit = {
                            editingStory = story
                        },
                        onDuplicate = {
                            manager.duplicateStory(story)
                        },
                        onDelete = {
                            manager.deleteStory(story)
                        }
                    )
                    Divider(color = Color(0xFF424242))
                }
            }
        }
    }

    // Editor dialog
    editingStory?.let { story ->
        CustomStoryEditorView(
            manager = manager,
            story = story,
            onDismiss = { editingStory = null }
        )
    }
}

@Composable
fun StoryRow(
    story: CustomStory,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (story.title.isEmpty()) "Untitled" else story.title,
                style = MaterialTheme.typography.body1,
                color = Color.White
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = story.text.take(60) + if (story.text.length > 60) "..." else "",
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // Duplicate button
        IconButton(onClick = onDuplicate) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Duplicate",
                tint = Color(0xFF64B5F6)
            )
        }

        // Delete button
        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = Color(0xFFE57373)
            )
        }

        // Play button
        IconButton(onClick = onPlay) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "Play",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp)
            )
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Story?") },
            text = { Text("Are you sure you want to delete this story?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFE57373))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            backgroundColor = Color(0xFF303030)
        )
    }
}
