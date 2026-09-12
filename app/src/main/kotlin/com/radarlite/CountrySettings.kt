package com.radarlite

import android.content.Context

// Which country databases are active on this device. Country 1 always has a value;
// country 2 is optional (Countries.NONE_CODE means no second country is active).
object CountrySettings {
    private const val PREFS = "radarlite_prefs"
    private const val KEY_COUNTRY_1 = "country_1"
    private const val KEY_COUNTRY_2 = "country_2"

    fun country1(context: Context): String =
        context.prefs().getString(KEY_COUNTRY_1, Countries.DEFAULT_CODE) ?: Countries.DEFAULT_CODE

    fun country2(context: Context): String =
        context.prefs().getString(KEY_COUNTRY_2, Countries.NONE_CODE) ?: Countries.NONE_CODE

    fun setCountry1(context: Context, code: String) = context.prefs().edit().putString(KEY_COUNTRY_1, code).apply()
    fun setCountry2(context: Context, code: String) = context.prefs().edit().putString(KEY_COUNTRY_2, code).apply()

    // The de-duplicated, non-empty codes that should have an open database right now.
    fun selectedCodes(context: Context): List<String> =
        listOf(country1(context), country2(context)).filter { it.isNotEmpty() }.distinct()

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
