package mnstudio.apps.mnstreamx.ui.screen.parental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

internal class ParentalNavigationStores : ViewModel() {
    private var id: Any? = null
    private var current: ViewModelStoreOwner? = null
    fun owner(session: Any): ViewModelStoreOwner {
        if (id != session || current == null) {
            clear(); id = session
            current = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        }
        return requireNotNull(current)
    }
    fun clear() { current?.viewModelStore?.clear(); current = null; id = null }
    override fun onCleared() = clear()
}