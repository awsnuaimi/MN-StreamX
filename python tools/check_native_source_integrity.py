#!/usr/bin/env python3
"""Verify native C++ sources are C++ (not Kotlin) and contain required JNI symbols.

Runs without external dependencies. Exits non-zero on failure so it can be
called from GitHub Actions or a pre-commit hook.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CPP_DIR = ROOT / "app" / "src" / "main" / "cpp"

# C++ extensions we compile for mnx-whisper.
CPP_EXTENSIONS = {".cpp", ".cc", ".cxx", ".c", ".h", ".hpp"}

# Kotlin package headers that must never appear inside a C++ file.
KOTLIN_PACKAGE_PREFIXES = (
    "package com.yourapp",
    "package com.example",
    "import androidx.",
    "import kotlinx.",
    "import com.google.",
)

# Required JNI entry points for the Whisper native library.
REQUIRED_WHISPER_SYMBOLS = (
    "Java_com_yourapp_iptv_data_caption_NativeWhisper_create",
    "Java_com_yourapp_iptv_data_caption_NativeWhisper_decode",
    "Java_com_yourapp_iptv_data_caption_NativeWhisper_cancel",
    "Java_com_yourapp_iptv_data_caption_NativeWhisper_park",
    "Java_com_yourapp_iptv_data_caption_NativeWhisper_resetState",
    "Java_com_yourapp_iptv_data_caption_NativeWhisper_trimMemory",
    "Java_com_yourapp_iptv_data_caption_NativeWhisper_release",
)

# Required JNI entry points for the SentencePiece native bridge.
REQUIRED_SENTENCEPIECE_SYMBOLS = (
    "Java_com_yourapp_iptv_data_caption_NativePieces_create",
    "Java_com_yourapp_iptv_data_caption_NativePieces_encode",
    "Java_com_yourapp_iptv_data_caption_NativePieces_decode",
    "Java_com_yourapp_iptv_data_caption_NativePieces_release",
)


def fail(message: str) -> None:
    print(f"[FAIL] {message}", file=sys.stderr)


def ok(message: str) -> None:
    print(f"[ OK ] {message}")


def check_not_kotlin(path: Path) -> bool:
    """A C++ source must not start with or contain Kotlin package declarations."""
    text = path.read_text(encoding="utf-8", errors="replace")
    for prefix in KOTLIN_PACKAGE_PREFIXES:
        for line in text.splitlines():
            stripped = line.strip()
            if stripped.startswith(prefix):
                fail(f"{path.relative_to(ROOT)} contains Kotlin declaration: {stripped[:80]!r}")
                return False
    first_line = next((ln.strip() for ln in text.splitlines() if ln.strip()), "")
    if not first_line.startswith(("#include", "//", "/*", "extern", "static", "namespace",
                                   "template", "using", "struct", "class", "typedef", "#pragma")):
        fail(f"{path.relative_to(ROOT)} does not start with a C/C++ directive: {first_line[:80]!r}")
        return False
    return True


def check_jni_symbols(path: Path, required: tuple[str, ...]) -> bool:
    """Every required JNI entry point must be defined in this file."""
    text = path.read_text(encoding="utf-8", errors="replace")
    missing = [symbol for symbol in required if symbol not in text]
    if missing:
        fail(f"{path.relative_to(ROOT)} is missing JNI symbols: {', '.join(missing)}")
        return False
    return True


def check_include_guard_or_includes(path: Path) -> bool:
    """Headers must have include guards; sources must include jni.h."""
    text = path.read_text(encoding="utf-8", errors="replace")
    if path.suffix in {".h", ".hpp"}:
        if "#pragma once" not in text and "#ifndef" not in text:
            fail(f"{path.relative_to(ROOT)} header has no include guard")
            return False
    else:
        if "#include <jni.h>" not in text:
            fail(f"{path.relative_to(ROOT)} is missing #include <jni.h>")
            return False
    return True


def check_file_size(path: Path, max_bytes: int = 512 * 1024) -> bool:
    size = path.stat().st_size
    if size > max_bytes:
        fail(f"{path.relative_to(ROOT)} exceeds {max_bytes} bytes ({size})")
        return False
    if size == 0:
        fail(f"{path.relative_to(ROOT)} is empty")
        return False
    return True


def main() -> int:
    if not CPP_DIR.is_dir():
        fail(f"C++ directory not found: {CPP_DIR}")
        return 1

    failures = 0
    checked = 0
    for path in sorted(CPP_DIR.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in CPP_EXTENSIONS:
            continue
        checked += 1
        if not check_file_size(path):
            failures += 1
            continue
        if not check_not_kotlin(path):
            failures += 1
            continue
        if not check_include_guard_or_includes(path):
            failures += 1
            continue

    whisper = CPP_DIR / "WhisperBridge.cpp"
    if whisper.is_file():
        if check_jni_symbols(whisper, REQUIRED_WHISPER_SYMBOLS):
            ok(f"WhisperBridge.cpp defines all {len(REQUIRED_WHISPER_SYMBOLS)} JNI symbols")
        else:
            failures += 1
    else:
        fail("WhisperBridge.cpp is missing")
        failures += 1

    pieces = CPP_DIR / "SentencePieceBridge.cpp"
    if pieces.is_file():
        if check_jni_symbols(pieces, REQUIRED_SENTENCEPIECE_SYMBOLS):
            ok(f"SentencePieceBridge.cpp defines all {len(REQUIRED_SENTENCEPIECE_SYMBOLS)} JNI symbols")
        else:
            failures += 1
    else:
        fail("SentencePieceBridge.cpp is missing")
        failures += 1

    if failures:
        print(f"\n{failures} native source problem(s) detected.", file=sys.stderr)
        return 1

    ok(f"All {checked} native files passed structural checks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())