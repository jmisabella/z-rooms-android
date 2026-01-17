# z-rooms-android

Z Rooms app for Android operating system

## Overview

Z Rooms is a 100% offline, free, ad-free story and sleep app featuring:
- 35 looping audio files (white noise, dark ambient, bright ambient, classical)
- Guided story with Text-to-Speech narration
- 35 preset storys + 35 custom story slots
- Optional enhanced voice quality with downloadable TTS voices
- Closed captioning for story narration
- Wake-up alarm with personalized greeting
- Infinite or timed listening sessions
- Fully offline (no internet required)

## Features

### Audio Library
- 35 ambient audio files across 4 styles:
  - White noise
  - Dark ambient
  - Bright ambient
  - Classical compositions

### Guided Story
- Toggle story on/off via leaf button
- 35 preset storys with varied content and breathwork guidance
- 35 custom story slots (write your own)
- Pause markers: `(2s)` for 2 seconds, `(1.5m)` for 1.5 minutes
- Automatic pauses if no markers provided
- Closed captioning (optional, enabled by default)
- Random story selection from presets + custom storys

### Enhanced Voice Quality (NEW - December 2025)
- Optional high-quality TTS voices for story narration
- Accessible via gear icon in story view (leftmost button)
- Preview voices before selecting
- Friendly voice names (Samantha, Alex, Emily, Daniel, etc.)
- Dynamic speech rate based on voice quality
- Offline-only voices (no internet required)
- Persists across app restarts

### Wake-Up Alarm
- Set alarm time with wake-up audio
- Personalized greeting when story completed before alarm
- SILENCE option for ambient fade-out only
- Integration with story completion tracking

## Technical Details

### Technologies
- Kotlin
- Jetpack Compose UI
- Android TextToSpeech API
- SharedPreferences for persistence
- ExoPlayer for audio playback

### Key Components
- `VoiceManager.kt` - TTS voice discovery and management
- `TextToSpeechManager.kt` - Story narration engine
- `VoiceSettingsView.kt` - Voice selection UI
- `AudioService.kt` - Background audio playback and alarm
- `CustomStoryManager.kt` - Custom story persistence

### Build Configuration
- Min SDK: 21 (Android 5.0)
- Target SDK: 36
- Compile SDK: 36
- Kotlin: 1.9.20
- Compose BOM: 2024.09.00

## App Philosophy

- 100% offline functionality
- Free with no ads or subscriptions
- No user data collection
- Minimal app size (~110MB with all assets)
- Optional enhanced features without forced upgrades
