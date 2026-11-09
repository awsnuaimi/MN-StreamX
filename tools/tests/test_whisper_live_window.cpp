#include <cassert>
#include "../../app/src/main/cpp/WhisperLiveWindow.h"

int main() {
    static_assert(mnxCaptionAudioContext(16000) == 64, "1-second clip");
    static_assert(mnxCaptionAudioContext(40000) == 160, "2.5-second clip");
    static_assert(mnxCaptionAudioContext(56000) == 192, "3.5-second clip");
    static_assert(mnxCaptionAudioContext(80000) == 288, "5-second clip");
    static_assert(mnxCaptionAudioContext(96000) == 320, "6-second clip");
    static_assert(mnxCaptionMaxTokens(40000) == 48, "2.5-second tokens");
    static_assert(mnxCaptionMaxTokens(56000) == 64, "3.5-second tokens");
    static_assert(mnxCaptionMaxTokens(96000) == 96, "6-second tokens");
    for (int samples = 16000; samples <= 96000; ++samples) {
        assert(mnxCaptionAudioContext(samples) >= (samples + 319) / 320);
        assert(mnxCaptionAudioContext(samples) <= 384);
        assert(mnxCaptionMaxTokens(samples) >= 32);
    }
}