package mnstudio.apps.mnstreamx.data.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import mnstudio.apps.mnstreamx.domain.security.SecureStorageException
import mnstudio.apps.mnstreamx.domain.security.SecureStorageFailure
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CancellationException

/** UnrecoverableKeyException and generic provider/I/O failures do not prove permanent loss. */
internal fun storageFailure(error: Throwable): SecureStorageFailure {
    var current: Throwable? = error
    repeat(16) {
        when (val value = current) {
            is SecureStorageException -> return value.problem
            is KeyPermanentlyInvalidatedException -> return SecureStorageFailure.KEY_INVALIDATED
            is AEADBadTagException -> return SecureStorageFailure.AUTHENTICATION_FAILED
        }
        current = current?.cause ?: return SecureStorageFailure.TEMPORARILY_UNAVAILABLE
    }
    return SecureStorageFailure.TEMPORARILY_UNAVAILABLE
}

internal inline fun <T> secureStorage(block: () -> T): T = try {
    block()
} catch (error: CancellationException) { throw error }
catch (error: SecureStorageException) { throw error }
catch (error: Exception) { throw SecureStorageException(storageFailure(error), error) }

/** Only use for parsing bytes already read; storage/provider errors use secureStorage instead. */
internal inline fun <T> decodeSecureRecord(block: () -> T): T = try {
    block()
} catch (error: CancellationException) { throw error }
catch (error: SecureStorageException) { throw error }
catch (error: Exception) { throw SecureStorageException(SecureStorageFailure.INVALID_RECORD, error) }