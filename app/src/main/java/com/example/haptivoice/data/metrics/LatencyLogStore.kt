package com.example.haptivoice.data.metrics

import android.content.Context
import android.content.Intent
import com.example.haptivoice.utils.LatencyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

// =============================================================
// REGISTRO DE LATENCIA POR SEÑA (ETAPA 9, KPI DEL CAPÍTULO 11)
// =============================================================

/*
 * Guarda cada seña traducida en un CSV dentro del almacenamiento
 * privado de la app (no se sube a ningún lado):
 *
 *   timestamp_ms,resultado,glosa,latencia_ms,distancia_grados,idioma
 *
 * "Exportar" lo comparte como texto (correo, Drive, WhatsApp…)
 * para calcular los KPI en una planilla. Se guardan como máximo
 * MAX_RECORDS filas; las más antiguas se descartan.
 *
 * Una sola instancia por app (get), así el panel y cualquier
 * otra pantalla ven los mismos datos.
 */
class LatencyLogStore private constructor(
    context: Context
) {

    private val file =
        File(File(context.filesDir, "metrics").apply { mkdirs() }, FILE_NAME)

    private val _records = MutableStateFlow<List<LatencyRecord>>(emptyList())
    val records: StateFlow<List<LatencyRecord>> = _records.asStateFlow()

    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {

        if (loaded) return@withContext

        _records.value =
            if (file.exists()) {
                file.readLines()
                    .drop(1)
                    .mapNotNull { LatencyRecord.fromCsvLine(it) }
                    .takeLast(MAX_RECORDS)
            } else {
                emptyList()
            }

        loaded = true
    }

    suspend fun add(record: LatencyRecord) = withContext(Dispatchers.IO) {

        load()

        val updated = (_records.value + record).takeLast(MAX_RECORDS)
        _records.value = updated

        if (updated.size == MAX_RECORDS || !file.exists()) {
            // Reescribe el archivo cuando se recorta o no existe.
            writeAll(updated)
        } else {
            file.appendText(record.toCsvLine() + "\n")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        _records.value = emptyList()
        writeAll(emptyList())
    }

    // Comparte el CSV como texto con otra app.
    fun shareIntent(): Intent {

        val csv =
            buildString {
                appendLine(LatencyRecord.CSV_HEADER)
                _records.value.forEach { appendLine(it.toCsvLine()) }
            }

        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "HaptiVoice: latencias por seña")
                putExtra(Intent.EXTRA_TEXT, csv)
            }

        return Intent.createChooser(send, "Exportar métricas")
    }

    private fun writeAll(items: List<LatencyRecord>) {
        file.writeText(
            buildString {
                appendLine(LatencyRecord.CSV_HEADER)
                items.forEach { appendLine(it.toCsvLine()) }
            }
        )
    }

    companion object {

        private const val FILE_NAME = "latencias.csv"
        const val MAX_RECORDS = 2000

        @Volatile
        private var instance: LatencyLogStore? = null

        fun get(context: Context): LatencyLogStore =
            instance ?: synchronized(this) {
                instance ?: LatencyLogStore(context.applicationContext)
                    .also { instance = it }
            }
    }
}