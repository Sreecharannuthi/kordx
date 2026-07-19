package com.android.rockages.kordx

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.rockages.kordx.services.Permissions
import com.android.rockages.kordx.services.Settings
import com.android.rockages.kordx.infra.database.Database
import com.android.rockages.kordx.services.groove.Groove
import com.android.rockages.kordx.services.i18n.Translator
import com.android.rockages.kordx.services.radio.Radio
import kotlinx.coroutines.launch

class KordX(application: Application) : AndroidViewModel(application), KordX.Hooks {
    interface Hooks {
        fun onKordXReady() {}
        fun onKordXDestroy() {}
        fun onKordXActivityReady() {}
        fun onKordXActivityPause() {}
        fun onKordXActivityDestroy() {}
    }

    init {
        instance = this
    }

    val permission = Permissions(this)
    val settings = Settings(this)
    val database = Database(applicationContext)
    val groove = Groove(this)
    val radio = Radio(this)
    val translator = Translator(this)

    var t by mutableStateOf(translator.getCurrentTranslation())

    val applicationContext get() = getApplication<Application>().applicationContext
    var closeApp: (() -> Unit)? = null
    private var isReady = false
    private var hooks = listOf(this, radio, groove)

    internal fun emitReady() {
        if (isReady) {
            return
        }
        isReady = true
        notifyHooks { onKordXReady() }
    }

    internal fun emitDestroy() {
        notifyHooks { onKordXDestroy() }
    }

    internal fun emitActivityReady() {
        emitReady()
        notifyHooks { onKordXActivityReady() }
    }

    internal fun emitActivityPause() {
        notifyHooks { onKordXActivityPause() }
    }

    internal fun emitActivityDestroy() {
        notifyHooks { onKordXActivityDestroy() }
    }

    override fun onKordXReady() {
        viewModelScope.launch {
            translator.onChange { nTranslation ->
                t = nTranslation
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // The KordX graph is process-owned by [KordXApplication]
        // and lives for the application lifetime. Do NOT clear
        // [instance] or emit destroy here — a ViewModelStoreOwner
        // (e.g. an Activity) clearing the graph would break the
        // Android Auto service that outlives the Activity.
        // Cleanup is handled by [KordXApplication.onTerminate].
    }

    private fun notifyHooks(fn: Hooks.() -> Unit) {
        hooks.forEach { fn.invoke(it) }
    }



    companion object {
        /**
         * Static reference to the live [KordX] instance, set in [init].
         * Used by [com.android.rockages.kordx.services.radio.KordXMediaLibraryService]
         * to reach the existing player/session when Android Auto binds to the
         * media browser service (which has no ViewModelStore of its own).
         * The graph is owned by [KordXApplication] for the process lifetime.
         */
        @Volatile
        var instance: KordX? = null
            private set
    }
}
