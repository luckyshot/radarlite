package com.radarlite.update

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.radarlite.BuildConfig
import com.radarlite.CountrySettings
import com.radarlite.db.CameraDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

// Downloads and applies the shared versions.json manifest, one entry per country.
// Each active country updates independently: one country failing to download does not
// block the others, and a country the user no longer has selected is only ever deleted
// here (never right when they change the selection), per CameraDbHelper.deleteInactive.
object DatabaseUpdater {

    private const val PREFS_NAME = "radarlite_prefs"
    private const val KEY_LAST_CHECK = "last_db_check_ms"
    private const val KEY_LAST_STALE_PROMPT = "last_stale_db_prompt_ms"
    private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L
    private const val STALE_INTERVAL = 7 * CHECK_INTERVAL

    private fun versionKey(code: String) = "db_version_$code"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun checkAndUpdate(context: Context): Result = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeCodes = CountrySettings.selectedCodes(context)
        val dbHelper = CameraDbHelper(context).apply { open(activeCodes) }
        try {
            val manifest = fetchManifest() ?: return@withContext Result.FAILED
            // Clear the deleted countries' remembered version too, so re-selecting one later
            // does not see a stale "up to date" version against its freshly emptied database.
            val editor = prefs.edit()
            dbHelper.deleteInactive(activeCodes).forEach { code -> editor.remove(versionKey(code)) }
            editor.apply()

            var anyUpdated = false
            var anyAvailable = false
            for (code in activeCodes) {
                val remote = manifest[code]
                val localVersion = prefs.getString(versionKey(code), null) ?: dbHelper.getVersion(code) ?: ""
                if (remote == null || remote.version <= localVersion) {
                    if (dbHelper.getVersion(code) != null) anyAvailable = true
                    continue
                }
                if (updateOne(context, dbHelper, code, remote)) {
                    prefs.edit().putString(versionKey(code), remote.version).apply()
                    anyUpdated = true
                }
                if (dbHelper.getVersion(code) != null) anyAvailable = true
            }

            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            when {
                anyUpdated   -> Result.UPDATED
                anyAvailable -> Result.UP_TO_DATE
                else         -> Result.FAILED
            }
        } catch (e: Exception) {
            Result.FAILED
        } finally {
            dbHelper.close()
        }
    }

    private suspend fun updateOne(
        context: Context,
        dbHelper: CameraDbHelper,
        code: String,
        remote: VersionInfo
    ): Boolean {
        val tmpGz = File(context.cacheDir, "cameras_update_$code.db.gz")
        val tmpDb = File(context.cacheDir, "cameras_update_$code.db")
        return try {
            download(remote.url, tmpGz)
            decompress(tmpGz, tmpDb)
            if (!validateDb(tmpDb)) return false
            dbHelper.replaceWith(code, tmpDb)
            true
        } catch (e: Exception) {
            false
        } finally {
            tmpGz.delete()
            tmpDb.delete()
        }
    }

    fun lastCheckMs(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0)

    private fun lastStalePromptMs(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_STALE_PROMPT, 0)

    fun shouldPromptForStale(context: Context): Boolean {
        val now = System.currentTimeMillis()
        return now - lastCheckMs(context) >= STALE_INTERVAL &&
            now - lastStalePromptMs(context) >= CHECK_INTERVAL
    }

    fun markStalePromptShown(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_STALE_PROMPT, System.currentTimeMillis())
            .apply()
    }

    // versions.json: { "ES": { "version": ..., "url": ... }, "FR": { ... }, ... }
    private fun fetchManifest(): Map<String, VersionInfo>? {
        val response = client.newCall(Request.Builder().url(BuildConfig.DB_VERSION_URL).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val body = response.use { it.body?.string() } ?: return null
        val json = JSONObject(body)
        val result = mutableMapOf<String, VersionInfo>()
        for (code in json.keys()) {
            val entry = json.optJSONObject(code) ?: continue
            val version = entry.optString("version").takeIf { it.isNotBlank() } ?: continue
            val url = entry.optString("url").takeIf { it.isNotBlank() } ?: continue
            result[code] = VersionInfo(version, url)
        }
        return result
    }

    private fun download(url: String, dest: File) {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("Download failed: HTTP ${response.code}")
        }
        response.use {
            val body = it.body ?: throw IllegalStateException("Download failed: empty body")
            body.byteStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun decompress(gz: File, dest: File) {
        GZIPInputStream(gz.inputStream()).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun validateDb(file: File): Boolean = try {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = db.rawQuery("SELECT COUNT(*) FROM cameras", null)
        val count = cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        db.close()
        count > 0
    } catch (e: Exception) { false }

    enum class Result { UPDATED, UP_TO_DATE, FAILED }
    private data class VersionInfo(val version: String, val url: String)
}
