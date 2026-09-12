package com.radarlite

// Countries with an alert database the app can download. Mirrors pipeline/countries.js —
// keep both lists in sync.
object Countries {
    val ALL = listOf(
        "Spain" to "ES",
        "Portugal" to "PT",
        "France" to "FR",
        "Germany" to "DE",
        "Italy" to "IT",
        "Andorra" to "AD",
        "United Kingdom" to "GB",
        "United States" to "US",
        "Canada" to "CA",
        "Australia" to "AU",
        "New Zealand" to "NZ"
    )
    const val DEFAULT_CODE = "ES"
    // The second country slot can be left inactive.
    const val NONE_CODE = ""
    const val NONE_LABEL = "None"

    fun nameFor(code: String): String = ALL.firstOrNull { it.second == code }?.first ?: code
    fun codeFor(name: String): String? = ALL.firstOrNull { it.first == name }?.second
}
