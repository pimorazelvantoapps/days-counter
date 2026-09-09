package com.pimorazelvanto.dayscounter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeParseException

class DataStoreWidgetConfigRepository(
    private val dataStore: DataStore<Preferences>,
) : WidgetConfigRepository {
    override fun observe(appWidgetId: Int): Flow<WidgetConfig?> {
        val keys = Keys(appWidgetId)
        return dataStore.data.map { it.readConfig(keys) }.distinctUntilChanged()
    }

    override suspend fun save(
        appWidgetId: Int,
        config: WidgetConfig,
    ) {
        val keys = Keys(appWidgetId)
        dataStore.edit { preferences ->
            preferences[keys.title] = config.title
            preferences[keys.targetDate] = config.targetDate.toString()
            preferences[keys.color] = config.color.name
        }
    }

    override suspend fun delete(appWidgetId: Int) {
        val keys = Keys(appWidgetId)
        dataStore.edit { preferences ->
            preferences.remove(keys.title)
            preferences.remove(keys.targetDate)
            preferences.remove(keys.color)
        }
    }

    private fun Preferences.readConfig(keys: Keys): WidgetConfig? {
        val title = this[keys.title]
        val targetDate = this[keys.targetDate]?.let(::parseDateOrNull)
        val color = this[keys.color]?.let(HeaderColor::fromName)
        return if (title != null && targetDate != null && color != null) {
            WidgetConfig(title, targetDate, color)
        } else {
            null
        }
    }

    private fun parseDateOrNull(isoDate: String): LocalDate? =
        try {
            LocalDate.parse(isoDate)
        } catch (_: DateTimeParseException) {
            null
        }

    private class Keys(
        appWidgetId: Int,
    ) {
        val title = stringPreferencesKey("title_$appWidgetId")
        val targetDate = stringPreferencesKey("target_date_$appWidgetId")
        val color = stringPreferencesKey("color_$appWidgetId")
    }
}
