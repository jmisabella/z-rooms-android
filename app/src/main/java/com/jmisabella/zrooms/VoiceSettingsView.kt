package com.jmisabella.zrooms

import android.content.Intent
import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings screen for selecting enhanced TTS voices
 * Allows users to preview and select high-quality story voices
 */
@Composable
fun VoiceSettingsView(
    voiceManager: VoiceManager,
    onDismiss: () -> Unit
) {
    val selectedVoice by voiceManager.selectedVoice
    val availableVoices by voiceManager.availableVoices

    var previewingVoice by remember { mutableStateOf<Voice?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1C1C1E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Voice Settings",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Info Section at the top (scrollable)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFF2C2C2E),
                        elevation = 0.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "ℹ️ About Voices",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Voices are provided by your device's Text-to-Speech engine. The voices shown here are currently available on your device.",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Voice Selection header
                item {
                    Text(
                        text = "Voice Selection",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(availableVoices) { voice ->
                    val isThisVoicePreviewing = voice == previewingVoice
                    VoiceListItem(
                        voice = voice,
                        voiceManager = voiceManager,
                        isSelected = voice == selectedVoice,
                        isPreviewing = isThisVoicePreviewing,
                        onSelect = {
                            voiceManager.setPreferredVoice(voice)
                        },
                        onPreview = {
                            if (isThisVoicePreviewing) {
                                // Stop current preview
                                voiceManager.stopPreview()
                                previewingVoice = null
                            } else {
                                // Stop any previous preview and start new one
                                voiceManager.stopPreview()
                                previewingVoice = voice
                                voiceManager.previewVoice(
                                    voice = voice,
                                    text = "Close your eyes and listen as words become worlds, stories unfold.",
                                    onComplete = {
                                        if (previewingVoice == voice) {
                                            previewingVoice = null
                                        }
                                    }
                                )
                            }
                        },
                        onStopPreview = {
                            voiceManager.stopPreview()
                            previewingVoice = null
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun VoiceListItem(
    voice: Voice,
    voiceManager: VoiceManager,
    isSelected: Boolean,
    isPreviewing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit
) {
    val needsDownload = voiceManager.needsDownload(voice)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        backgroundColor = if (isSelected) Color(0xFF3C3C3E) else Color(0xFF2C2C2E),
        elevation = if (isSelected) 4.dp else 0.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Voice name (no quality label since context already indicates Enhanced Voice)
                Text(
                    text = voiceManager.getFriendlyVoiceName(voice),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Locale and download status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = voice.locale.displayName,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    if (needsDownload) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Needs Download",
                            tint = Color(0xFFFFA726),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Needs Download",
                            color = Color(0xFFFFA726),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Preview button
                IconButton(
                    onClick = {
                        if (isPreviewing) {
                            onStopPreview()
                        } else {
                            onPreview()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPreviewing) "Stop Preview" else "Preview Voice",
                        tint = if (isPreviewing) Color(0xFFFF5722) else Color(0xFF4CAF50)
                    )
                }

                // Selected indicator
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
