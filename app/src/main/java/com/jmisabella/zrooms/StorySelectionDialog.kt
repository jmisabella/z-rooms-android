package com.jmisabella.zrooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun StorySelectionDialog(
    collections: List<StoryCollection>,
    selectedCollectionId: String?,
    onDismiss: () -> Unit,
    onSelectCollection: (StoryCollection) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Color(0xFF212121), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Select Story",
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(collections) { collection ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectCollection(collection)
                                onDismiss()
                            }
                            .background(
                                if (selectedCollectionId == collection.id)
                                    Color(0xFF424242)
                                else
                                    Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = collection.displayName,
                                fontSize = 16.sp,
                                color = Color.White
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Eco,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${collection.chapterCount} chapters",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }

                                if (collection.poemCount > 0) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.FormatQuote,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${collection.poemCount} poems",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedCollectionId == collection.id) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}
