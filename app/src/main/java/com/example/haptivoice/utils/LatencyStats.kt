package com.example.haptivoice.utils

import kotlin.math.ceil

// =============================================================
// RESUMEN DE LATENCIA PARA LOS KPI (CAPÍTULO 11, RNF-08)
// =============================================================

/*
 * Resumen de las latencias registradas, desde que se completó
 * la postura hasta que el texto aparece en pantalla.
 * Meta del informe: 250 ms (RNF-08).
 *
 * No depende de Android, así que se prueba con JUnit.
 */
data class LatencySummary(
    val count: Int,
    val meanMillis: Double,
    val medianMillis: Long,
    val p95Millis: Long,
    val maxMillis: Long,
    val withinTargetPercent: Double,
    val recognizedPercent: Double
)

data class LatencyRecord(
    val timestampMillis: Long,
    val result: String,
    val glosa: String,
    val latencyMillis: Long,
    val distanceDeg: Float?,
    val language: String
) {

    // Una línea del CSV que se exporta.
    fun toCsvLine(): String =
        listOf(
            timestampMillis.toString(),
            result,
            "\"" + glosa.replace("\"", "\"\"") + "\"",
            latencyMillis.toString(),
            distanceDeg?.let { "%.1f".format(java.util.Locale.ROOT, it) }.orEmpty(),
            language
        ).joinToString(",")

    companion object {

        const val CSV_HEADER = "timestamp_ms,resultado,glosa,latencia_ms,distancia_grados,idioma"

        const val RESULT_RECOGNIZED = "reconocida"
        const val RESULT_UNRECOGNIZED = "no_reconocida"

        // Lee una línea del CSV; null si está dañada.
        fun fromCsvLine(line: String): LatencyRecord? {

            val parts = splitCsv(line)

            if (parts.size != 6) return null

            return LatencyRecord(
                timestampMillis = parts[0].toLongOrNull() ?: return null,
                result = parts[1],
                glosa = parts[2],
                latencyMillis = parts[3].toLongOrNull() ?: return null,
                distanceDeg = parts[4].toFloatOrNull(),
                language = parts[5]
            )
        }

        private fun splitCsv(line: String): List<String> {

            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0

            while (i < line.length) {

                val c = line[i]

                when {
                    c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                        current.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = !inQuotes
                    c == ',' && !inQuotes -> {
                        fields += current.toString()
                        current.clear()
                    }
                    else -> current.append(c)
                }

                i++
            }

            fields += current.toString()

            return fields
        }
    }
}

object LatencyStats {

    const val TARGET_MILLIS = 250L

    fun summarize(
        records: List<LatencyRecord>,
        targetMillis: Long = TARGET_MILLIS
    ): LatencySummary? {

        if (records.isEmpty()) return null

        val sorted = records.map { it.latencyMillis }.sorted()

        return LatencySummary(
            count = sorted.size,
            meanMillis = sorted.average(),
            medianMillis = percentile(sorted, 50.0),
            p95Millis = percentile(sorted, 95.0),
            maxMillis = sorted.last(),
            withinTargetPercent =
                100.0 * sorted.count { it <= targetMillis } / sorted.size,
            recognizedPercent =
                100.0 * records.count { it.result == LatencyRecord.RESULT_RECOGNIZED } /
                        records.size
        )
    }

    // Percentil por rango más cercano, sobre una lista ya ordenada.
    fun percentile(sorted: List<Long>, percent: Double): Long {

        require(sorted.isNotEmpty())

        val rank = ceil(percent / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)

        return sorted[rank - 1]
    }
}