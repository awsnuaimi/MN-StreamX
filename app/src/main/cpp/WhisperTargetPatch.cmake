# Resolve automatic same-language captions inside the existing encoder pass.
function(mnx_patch_whisper_target source_directory)
    set(source "${source_directory}/src/whisper.cpp")
    file(READ "${source}" text)
    set(marker "// MNX v30: target-aware transcription after language detection.")
    string(FIND "${text}" "${marker}" patched)
    if(NOT patched EQUAL -1)
        string(FIND "${text}" "mnx_whisper_full_with_target" wrapper)
        if(wrapper EQUAL -1)
            message(FATAL_ERROR "Incomplete target-aware Whisper patch")
        endif()
        return()
    endif()
    set(declaration "int whisper_full_with_state(")
    string(FIND "${text}" "${declaration}" begin)
    if(begin EQUAL -1)
        message(FATAL_ERROR "Unsupported Whisper full-state layout")
    endif()
    string(SUBSTRING "${text}" 0 ${begin} before)
    string(SUBSTRING "${text}" ${begin} -1 after)
    set(text "${before}static thread_local const char * mnx_caption_target = nullptr;\n${after}")
    set(needle "    if (params.token_timestamps) {\n        state->t_beg    = 0;")
    string(FIND "${text}" "${needle}" position)
    if(position EQUAL -1)
        message(FATAL_ERROR "Unsupported Whisper post-language layout")
    endif()
    set(replacement "    ${marker}\n    if (mnx_caption_target && params.language && strcmp(mnx_caption_target, params.language) == 0) params.translate = false;\n\n${needle}")
    string(REPLACE "${needle}" "${replacement}" text "${text}")
    string(APPEND text [=[

extern "C" WHISPER_API int mnx_whisper_full_with_target(whisper_context * ctx, whisper_state * state,
        whisper_full_params params, const float * samples, int count, const char * target) {
    struct TargetScope {
        const char * previous;
        explicit TargetScope(const char * value) : previous(mnx_caption_target) { mnx_caption_target = value; }
        ~TargetScope() { mnx_caption_target = previous; }
    } scope(target);
    return whisper_full_with_state(ctx, state, params, samples, count);
}
]=])
    file(WRITE "${source}" "${text}")
endfunction()