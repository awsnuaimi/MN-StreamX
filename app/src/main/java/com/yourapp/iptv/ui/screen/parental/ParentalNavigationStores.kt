package com.yourapp.iptv.ui.screen.parental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Activity-owned navigation survives configuration changes when PIN protection is disabled.
 * A security-session change clears the entire previous navigation graph's ViewModels. */
internal class ParentalNavigationStores : ViewModel() {
    private var id: Long? = null
    private var current: ViewModelStoreOwner? = null
    fun owner(session: Long): ViewModelStoreOwner {
        if (id != session || current == null) {
            clear(); id = session
            current = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        }
        return requireNotNull(current)
    }
    fun clear() { current?.viewModelStore?.clear(); current = null; id = null }
    override fun onCleared() = clear()
}