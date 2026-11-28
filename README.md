# Voice Story Maker

An Android app that turns typed text into an audio file (WAV) using the phone's
built-in text-to-speech engine. It works offline.

## Features
- Type or paste a story, then create a WAV audio file
- Choose a voice from the voices installed on your phone
- Adjust pitch (lower = deeper voice) and speed
- Preview the voice before creating the file
- Long stories are split into parts automatically and joined into one file
- Share the finished file from the app

## Building
Pushing to any branch starts a GitHub Actions build automatically.
The arm64-v8a APK is published under the **Releases** section of the repository.

## Tips for a deeper male voice
Voice quality depends on the TTS engine on your phone (usually Google's Text-to-speech).
Install more English voices from your phone's Settings > Accessibility > Text-to-speech,
then pick one in the app's Voice list.
