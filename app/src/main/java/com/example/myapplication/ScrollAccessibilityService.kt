import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScrollAccessibilityService : AccessibilityService() {

    // 직접 생성하는 DataStore (Service에서는 delegate 사용 불가)
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { applicationContext.preferencesDataStoreFile("scroll_data") }
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            Log.d("ScrollService", "외부 스크롤 감지됨 - ${event.packageName}")
            addScrollDistanceToDataStore(100)
        }
    }

    private fun addScrollDistanceToDataStore(amount: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val key = intPreferencesKey("external_scroll_distance")
            val prefs = dataStore.data.first()
            val current = prefs[key] ?: 0
            dataStore.edit { it[key] = current + amount }
        }
    }

    override fun onInterrupt() {}
}