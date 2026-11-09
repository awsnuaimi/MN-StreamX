#pragma once

// Whisper's 16-kHz frontend has a 160-sample (10-ms) mel hop and the
// audio encoder downsamples it by two. One context position spans 320 samples.
// Round up to blocks of 32 with a small guard for boundary frames.
// Keeping the context proportional to the actual voiced clip prevents
// unnecessary encoder work without dropping the end of longer clips.
constexpr int mnxCaptionAudioContext(int samples) {
    const int frames = (samples + 319) / 320;
    const int guarded = frames + 8;
    const int rounded = ((guarded + 31) / 32) * 32;
    return rounded < 64 ? 64 : (rounded > 384 ? 384 : rounded);
}

// A short live caption does not require 96 output tokens. Keep a generous
// budget for morphologically rich languages such as Polish.
constexpr int mnxCaptionMaxTokens(int samples) {
    const int seconds = (samples + 15999) / 16000;
    const int tokens = seconds * 16;
    return tokens < 32 ? 32 : (tokens > 96 ? 96 : tokens);
}