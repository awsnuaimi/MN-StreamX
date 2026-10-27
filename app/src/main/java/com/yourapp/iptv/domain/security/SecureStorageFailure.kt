package com.yourapp.iptv.domain.security

import java.io.IOException

/** A diagnosis is not permission to delete data, replace a key, or bypass protection. */
enum class SecureStorageFailure {
    TEMPORARILY_UNAVAILABLE, KEY_MISSING, KEY_INVALIDATED, INVALID_RECORD, AUTHENTICATION_FAILED
}

class SecureStorageException(val problem: SecureStorageFailure, cause: Throwable? = null) :
    IOException("Encrypted storage unavailable: ${problem.name}", cause)