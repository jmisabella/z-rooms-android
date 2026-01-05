package com.jmisabella.zrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun CustomPoemEditorView(
    manager: CustomPoetryManager,
    poem: CustomPoem,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(poem.title) }
    var text by remember { mutableStateOf(poem.text) }
    val isNewPoem = poem.title.isEmpty() && poem.text.isEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f),
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
                        text = if (isNewPoem) "New Poem" else "Edit Poem",
                        style = MaterialTheme.typography.h6,
                        color = Color.White
                    )

                    Row {
                        IconButton(
                            onClick = {
                                if (title.isNotEmpty() || text.isNotEmpty()) {
                                    val updated = poem.copy(
                                        title = title,
                                        text = text
                                    )

                                    if (isNewPoem) {
                                        manager.addPoem(updated)
                                    } else {
                                        manager.updatePoem(updated)
                                    }
                                }
                                onDismiss()
                            }
                        ) {
                            Icon(
                                Icons.Filled.Done,
                                contentDescription = "Save",
                                tint = Color(0xFFAB47BC)
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = Color.White
                            )
                        }
                    }
                }

                Divider(color = Color(0xFF424242))

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Title field
                    Text(
                        text = "Title",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF303030)),
                        placeholder = { Text("My Poem", color = Color.Gray) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFFAB47BC),
                            unfocusedBorderColor = Color(0xFF424242)
                        ),
                        singleLine = true
                    )

                    Spacer(Modifier.height(16.dp))

                    // Text field
                    Text(
                        text = "Poem Text",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF303030)),
                        placeholder = {
                            Text(
                                "Enter your poem text here...\n\nUse pause markers like (3s) for 3 seconds or (1.5m) for 1.5 minutes.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFFAB47BC),
                            unfocusedBorderColor = Color(0xFF424242)
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    // Help text
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFF303030)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Pause Markers",
                                style = MaterialTheme.typography.subtitle2,
                                color = Color.White
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "• (3s) = 3 second pause\n" +
                                        "• (1.5m) = 1.5 minute pause\n" +
                                        "• If no pauses are added, automatic pauses will be inserted at line breaks",
                                style = MaterialTheme.typography.caption,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
