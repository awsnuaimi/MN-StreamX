import sys
import os

def check_files():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    cpp_dir = os.path.join(root_dir, "app", "src", "main", "cpp")
    
    if not os.path.isdir(cpp_dir):
        print("[FAIL] C++ directory not found.")
        return 1
        
    failures = 0
    checked = 0
    
    for root, dirs, files in os.walk(cpp_dir):
        for file in files:
            if file.endswith((".cpp", ".cc", ".cxx", ".c", ".h", ".hpp")):
                checked += 1
                path = os.path.join(root, file)
                
                with open(path, "r", encoding="utf-8", errors="replace") as f:
                    text = f.read()
                
                if len(text) == 0:
                    print("[FAIL] " + file + " is empty.")
                    failures += 1
                    continue
                    
                if "package com.yourapp" in text or "package com.example" in text:
                    print("[FAIL] " + file + " contains Kotlin code!")
                    failures += 1
                    continue
                    
                if not file.endswith((".h", ".hpp")):
                    if "#include <jni.h>" not in text:
                        print("[FAIL] " + file + " is missing jni.h")
                        failures += 1
                        continue

    whisper_path = os.path.join(cpp_dir, "WhisperBridge.cpp")
    if os.path.isfile(whisper_path):
        with open(whisper_path, "r", encoding="utf-8") as f:
            whisper_text = f.read()
            if "NativeWhisper_decode" not in whisper_text or "NativeWhisper_create" not in whisper_text:
                print("[FAIL] WhisperBridge.cpp is missing JNI symbols.")
                failures += 1
            else:
                print("[ OK ] WhisperBridge.cpp looks good.")
    else:
        print("[FAIL] WhisperBridge.cpp is missing.")
        failures += 1

    if failures > 0:
        print("\n" + str(failures) + " problem(s) detected.")
        return 1
        
    print("[ OK ] All " + str(checked) + " files passed.")
    return 0

if __name__ == "__main__":
    sys.exit(check_files())