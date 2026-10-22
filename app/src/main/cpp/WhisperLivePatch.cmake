# MN StreamX's private derivative of pinned whisper.cpp v1.9.5.
# The existing window patch must run first. Changes are checked before publication.
function(mnx_patch_whisper_live source_directory)
    set(source "${source_directory}/src/whisper.cpp")
    file(READ "${source}" text)
    set(marker "// MNX v29.4: bounded live state and reusable language encoder.")
    string(FIND "${text}" "${marker}" patched)
    if(NOT patched EQUAL -1)
        foreach(required "mnx_whisper_init_live_state" "mnx_encoder_ready" "fn_set_abort")
            string(FIND "${text}" "${required}" position)
            if(position EQUAL -1)
                message(FATAL_ERROR "Incomplete MN StreamX live Whisper patch")
            endif()
        endforeach()
        return()
    endif()
    string(FIND "${text}" "// MNX: bound the first language-detection encoder to the live window." window_patch)
    if(window_patch EQUAL -1)
        message(FATAL_ERROR "Apply WhisperWindowPatch before WhisperLivePatch")
    endif()

    set(init_begin "struct whisper_state * whisper_init_state(whisper_context * ctx) {")
    set(init_end "int whisper_ctx_init_openvino_encoder_with_state(")
    string(FIND "${text}" "${init_begin}" begin)
    string(FIND "${text}" "${init_end}" end)
    if(begin EQUAL -1 OR end EQUAL -1 OR end LESS begin)
        message(FATAL_ERROR "Unsupported Whisper state-initialization layout")
    endif()
    math(EXPR length "${end} - ${begin}")
    string(SUBSTRING "${text}" ${begin} ${length} original_init)
    set(init "${original_init}")
    set(replacement [=[extern "C" WHISPER_API struct whisper_state * mnx_whisper_init_live_state(whisper_context *, int);
struct whisper_state * whisper_init_state(whisper_context * ctx) {
    return mnx_whisper_init_live_state(ctx, 0);
}
extern "C" WHISPER_API struct whisper_state * mnx_whisper_init_live_state(whisper_context * ctx, int mnx_audio_ctx) {
    // MNX v29.4: bounded live state and reusable language encoder.
    if (mnx_audio_ctx < 0 || mnx_audio_ctx > ctx->model.hparams.n_audio_ctx) return nullptr;]=])
    string(REPLACE "${init_begin}" "${replacement}" init "${init}")
    string(REPLACE "whisper_state * state = new whisper_state;"
        "whisper_state * state = new whisper_state;\n    state->exp_n_audio_ctx = mnx_audio_ctx;" init "${init}")
    foreach(needle
            "GGML_PAD(ctx->model.hparams.n_audio_ctx, 256)"
            "state->logits.reserve(ctx->vocab.n_vocab * ctx->model.hparams.n_text_ctx);"
            "const int n_tokens = hparams.n_text_ctx;")
        string(FIND "${init}" "${needle}" position)
        if(position EQUAL -1)
            message(FATAL_ERROR "Unsupported Whisper live-allocation layout")
        endif()
    endforeach()
    string(REPLACE "GGML_PAD(ctx->model.hparams.n_audio_ctx, 256)"
        "GGML_PAD(mnx_audio_ctx > 0 ? mnx_audio_ctx : ctx->model.hparams.n_audio_ctx, 256)" init "${init}")
    # MN StreamX uses no prompt/history and greedy decoding: at most four prompt tokens,
    # followed by one token per step. Keep a 16-token allocator margin, not 448 tokens.
    string(REPLACE "state->logits.reserve(ctx->vocab.n_vocab * ctx->model.hparams.n_text_ctx);"
        "state->logits.reserve(ctx->vocab.n_vocab * (mnx_audio_ctx > 0 ? 16 : ctx->model.hparams.n_text_ctx));" init "${init}")
    string(REPLACE "const int n_tokens = hparams.n_text_ctx;"
        "const int n_tokens = mnx_audio_ctx > 0 ? std::min(16, hparams.n_text_ctx) : hparams.n_text_ctx;" init "${init}")
    string(REPLACE "${original_init}" "${init}" text "${text}")

    set(needle "    // clear old results\n    auto & result_all = state->result_all;")
    set(replacement [=[    // Install the real cancellation/deadline hook inside CPU graphs as well.
    for (auto backend : state->backends) {
        auto dev = ggml_backend_get_device(backend);
        auto reg = dev ? ggml_backend_dev_backend_reg(dev) : nullptr;
        if (!reg) continue;
        auto fn_set_abort = (ggml_backend_set_abort_callback_t) ggml_backend_reg_get_proc_address(reg, "ggml_backend_set_abort_callback");
        if (fn_set_abort) fn_set_abort(backend, params.abort_callback, params.abort_callback_user_data);
    }
    bool mnx_encoder_ready = false;
    // clear old results
    auto & result_all = state->result_all;]=])
    string(FIND "${text}" "${needle}" position)
    if(position EQUAL -1)
        message(FATAL_ERROR "Unsupported Whisper full-inference layout")
    endif()
    string(REPLACE "${needle}" "${replacement}" text "${text}")

    set(needle "        state->lang_id = lang_id;\n        params.language = whisper_lang_str(lang_id);")
    set(replacement "        state->lang_id = lang_id;\n        mnx_encoder_ready = params.audio_ctx > 0 && params.offset_ms == 0 && !params.encoder_begin_callback;\n        params.language = whisper_lang_str(lang_id);")
    string(FIND "${text}" "${needle}" position)
    if(position EQUAL -1)
        message(FATAL_ERROR "Unsupported Whisper language-result layout")
    endif()
    string(REPLACE "${needle}" "${replacement}" text "${text}")

    set(needle [=[        // encode audio features starting at offset seek
        if (!whisper_encode_internal(*ctx, *state, seek, params.n_threads, params.abort_callback, params.abort_callback_user_data)) {
            WHISPER_LOG_ERROR("%s: failed to encode\n", __func__);
            return -6;
        }]=])
    set(replacement [=[        // Language detection already encoded this same first live window.
        if (!mnx_encoder_ready || seek != 0) {
            if (!whisper_encode_internal(*ctx, *state, seek, params.n_threads, params.abort_callback, params.abort_callback_user_data)) {
                WHISPER_LOG_ERROR("%s: failed to encode\n", __func__);
                return -6;
            }
        }
        mnx_encoder_ready = false;]=])
    string(FIND "${text}" "${needle}" position)
    if(position EQUAL -1)
        message(FATAL_ERROR "Unsupported Whisper encoder-loop layout")
    endif()
    string(REPLACE "${needle}" "${replacement}" text "${text}")
    file(WRITE "${source}" "${text}")
endfunction()