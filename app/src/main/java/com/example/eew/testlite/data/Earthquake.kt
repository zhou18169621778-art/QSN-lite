package com.example.eew.testlite.data

data class Earthquake(
    val id: String,
    val magnitude: Double,
    val place: String,
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double,
    val url: String,
    val source: DataSource = DataSource.USGS,
    val magnitudeType: String = "",
    val agency: String = "",
    val status: String = "",
    val reportTimeMillis: Long = 0
) : java.io.Serializable {

    fun determinationLabel(): String? {
        return when (status.lowercase()) {
            "automatic", "auto", "preliminary" -> "自动测定"
            "reviewed", "manual", "final" -> "正式测定"
            else -> null
        }
    }

    fun formattedMagnitude(): String {
        val type = normalizeMagnitudeType()
        return if (type.isEmpty()) {
            String.format(java.util.Locale.US, "M%.1f", magnitude)
        } else {
            String.format(java.util.Locale.US, "M%s%.1f", type, magnitude)
        }
    }

    fun formattedMagnitudeShort(): String {
        return String.format(java.util.Locale.US, "%.1f", magnitude)
    }

    fun sourceLabelRes(): Int {
        return when (source) {
            DataSource.USGS -> com.example.eew.testlite.R.string.source_usgs
            DataSource.WOLFX_CENC -> com.example.eew.testlite.R.string.source_wolfx
            DataSource.WOLFX_JMA -> com.example.eew.testlite.R.string.source_wolfx_jma
            DataSource.EMSC -> com.example.eew.testlite.R.string.source_emsc
            DataSource.HKO -> com.example.eew.testlite.R.string.source_hko
            DataSource.BMKG -> com.example.eew.testlite.R.string.source_bmkg
            DataSource.JMA_XML -> com.example.eew.testlite.R.string.source_jma_xml
            DataSource.CEA -> com.example.eew.testlite.R.string.source_cea
            DataSource.CEA_PR -> com.example.eew.testlite.R.string.source_cea_pr
        }
    }

    fun sourceShortLabelRes(): Int {
        return when (source) {
            DataSource.USGS -> com.example.eew.testlite.R.string.source_short_usgs
            DataSource.WOLFX_CENC -> com.example.eew.testlite.R.string.source_short_wolfx
            DataSource.WOLFX_JMA -> com.example.eew.testlite.R.string.source_short_wolfx_jma
            DataSource.EMSC -> com.example.eew.testlite.R.string.source_short_emsc
            DataSource.HKO -> com.example.eew.testlite.R.string.source_short_hko
            DataSource.BMKG -> com.example.eew.testlite.R.string.source_short_bmkg
            DataSource.JMA_XML -> com.example.eew.testlite.R.string.source_short_jma_xml
            DataSource.CEA -> com.example.eew.testlite.R.string.source_short_cea
            DataSource.CEA_PR -> com.example.eew.testlite.R.string.source_short_cea_pr
        }
    }

    fun viewOnLabelRes(): Int {
        return when (source) {
            DataSource.USGS -> com.example.eew.testlite.R.string.view_on_usgs
            DataSource.WOLFX_CENC -> com.example.eew.testlite.R.string.view_on_cenc
            DataSource.WOLFX_JMA -> com.example.eew.testlite.R.string.view_on_jma
            DataSource.EMSC -> com.example.eew.testlite.R.string.view_on_emsc
            DataSource.HKO -> com.example.eew.testlite.R.string.view_on_hko
            DataSource.BMKG -> com.example.eew.testlite.R.string.view_on_bmkg
            DataSource.JMA_XML -> com.example.eew.testlite.R.string.view_on_jma_xml
            DataSource.CEA -> com.example.eew.testlite.R.string.view_on_cenc
            DataSource.CEA_PR -> com.example.eew.testlite.R.string.view_on_cenc
        }
    }

    fun magnitudeColor(): Int {
        return when {
            magnitude >= 6.0 -> 0xFFB71C1C.toInt()
            magnitude >= 5.0 -> 0xFFD32F2F.toInt()
            magnitude >= 4.0 -> 0xFFF57C00.toInt()
            magnitude >= 3.0 -> 0xFFF9A825.toInt()
            else -> 0xFF388E3C.toInt()
        }
    }

    private fun normalizeMagnitudeType(): String {
        return when (magnitudeType.lowercase()) {
            "芮氏" -> "L"
            "md" -> "d"
            "ml" -> "L"
            "mlr" -> "L"
            "mww", "mwb", "mwc", "mwr", "mwp", "mw" -> "w"
            "ms", "ms_20", "ms20" -> "s"
            "mb", "mblg", "mb_lg" -> "b"
            "mj", "mjma" -> "J"
            "me", "mh" -> magnitudeType.lowercase()
            else -> ""
        }
    }
}
