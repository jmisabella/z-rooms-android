package com.jmisabella.zrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun CustomMeditationListView(
    manager: CustomMeditationManager,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit
) {
    var editingMeditation by remember { mutableStateOf<CustomMeditation?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFF212121)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Meditations",
                        style = MaterialTheme.typography.h6,
                        color = Color.White
                    )

                    Row {
                        if (manager.canAddMore) {
                            IconButton(
                                onClick = {
                                    editingMeditation = CustomMeditation(title = "", text = "")
                                }
                            ) {
                                Icon(
                                    Icons.Filled.AddCircle,
                                    contentDescription = "Add Meditation",
                                    tint = Color(0xFF4CAF50)
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                Divider(color = Color(0xFF424242))

                // Meditation List
                if (manager.meditations.isEmpty()) {
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
                            text = "No Custom Meditations",
                            style = MaterialTheme.typography.h6,
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Create your own guided meditation\nwith custom pauses and pacing",
                            style = MaterialTheme.typography.body2,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                editingMeditation = CustomMeditation(title = "", text = "")
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Filled.AddCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create First Meditation", color = Color.White)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(manager.meditations) { meditation ->
                            MeditationRow(
                                meditation = meditation,
                                onPlay = {
                                    onPlay(meditation.text)
                                    onDismiss()
                                },
                                onEdit = {
                                    editingMeditation = meditation
                                },
                                onDuplicate = {
                                    manager.duplicateMeditation(meditation)
                                },
                                onDelete = {
                                    manager.deleteMeditation(meditation)
                                }
                            )
                            Divider(color = Color(0xFF424242))
                        }

                        if (manager.canAddMore) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editingMeditation = CustomMeditation(title = "", text = "")
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.AddCircle,
                                        contentDescription = "Add New Meditation",
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Add New Meditation",
                                        color = Color(0xFF4CAF50),
                                        style = MaterialTheme.typography.body1
                                    )
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = "Maximum ${manager.meditations.size} meditations reached",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.caption,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Editor dialog
    editingMeditation?.let { meditation ->
        CustomMeditationEditorView(
            manager = manager,
            meditation = meditation,
            onDismiss = { editingMeditation = null }
        )
    }
}

@Composable
fun MeditationRow(
    meditation: CustomMeditation,
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
                text = if (meditation.title.isEmpty()) "Untitled" else meditation.title,
                style = MaterialTheme.typography.body1,
                color = Color.White
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = meditation.text.take(60) + if (meditation.text.length > 60) "..." else "",
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
            title = { Text("Delete Meditation?") },
            text = { Text("Are you sure you want to delete this meditation?") },
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
