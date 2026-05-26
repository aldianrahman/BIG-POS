package com.berdikariintigemilang.pos.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.printerDataStore by preferencesDataStore(name = "pos_printer")

data class SavedPrinter(val name: String, val address: String)

@Singleton
class PrinterStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ADDRESS = stringPreferencesKey("printer_address")
        val NAME = stringPreferencesKey("printer_name")
    }

    val printerFlow: Flow<SavedPrinter?> = context.printerDataStore.data.map { prefs ->
        val address = prefs[Keys.ADDRESS] ?: return@map null
        SavedPrinter(name = prefs[Keys.NAME] ?: address, address = address)
    }

    suspend fun get(): SavedPrinter? = printerFlow.first()

    suspend fun save(printer: SavedPrinter) {
        context.printerDataStore.edit {
            it[Keys.ADDRESS] = printer.address
            it[Keys.NAME] = printer.name
        }
    }

    suspend fun clear() {
        context.printerDataStore.edit { it.clear() }
    }
}
