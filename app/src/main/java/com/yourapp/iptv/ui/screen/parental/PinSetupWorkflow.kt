package com.yourapp.iptv.ui.screen.parental

internal enum class PinSetupMode { CREATE_NEW, CREATE_REPEAT, CHANGE_OLD, CHANGE_NEW, CHANGE_REPEAT, DISABLE }

/** Explicit ownership transfer to the controller; every abandoned buffer is wiped. */
internal class PinSetupWorkflow {
    private var old: CharArray? = null
    private var fresh: CharArray? = null
    fun setOld(value: CharArray) { old?.fill('\u0000'); old = value }
    fun setNew(value: CharArray) { clearNew(); fresh = value }
    fun matches(value: CharArray): Boolean = try { fresh?.contentEquals(value) == true } finally { value.fill('\u0000') }
    fun takeOld() = requireNotNull(old).also { old = null }
    fun takeNew() = requireNotNull(fresh).also { fresh = null }
    fun clearNew() { fresh?.fill('\u0000'); fresh = null }
    fun clear() { clearNew(); old?.fill('\u0000'); old = null }
}