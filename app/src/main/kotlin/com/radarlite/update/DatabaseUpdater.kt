package com.radarlite.update

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.radarlite.BuildConfig
import com.radarlite.db.CameraDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object DatabaseUpdater {

    private const val PREFS_NAME = "radarlite_prefs"
    private const val KEY_LAST_CHECK = "last_db_check_ms"
    private const val KEY_LAST_STALE_PROMPT = "last_stale_db_prompt_ms"
    private const val KEY_DB_VERSION = "db_version"
    private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L
    private const val STALE_INTERVAL = 7 * CHECK_INTERVAL

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun checkAndUpdate(context: Context): Result = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dbHelper = CameraDbHelper(context)
        try {
            val remote = fetchVersionInfo() ?: return@withContext Result.FAILED
            dbHelper.open()
            val localVersion = prefs.getString(KEY_DB_VERSION, null)
                ?: dbHelper.getVersion()
                ?: ""

            if (remote.version <= localVersion) {
                prefs.edit()
                    .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                    .putString(KEY_DB_VERSION, localVersion)
                    .apply()
                return@withContext Result.UP_TO_DATE
            }

            val tmpGz = File(context.cacheDir, "cameras_update.db.gz")
            val tmpDb = File(context.cacheDir, "cameras_update.db")

            download(remote.url, tmpGz)
            decompress(tmpGz, tmpDb)

            if (!validateDb(tmpDb)) {
                tmpGz.delete(); tmpDb.delete()
                return@withContext Result.FAILED
            }

            dbHelper.replaceWith(tmpDb)
            prefs.edit()
                .putString(KEY_DB_VERSION, remote.version)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()

            tmpGz.delete(); tmpDb.delete()
            Result.UPDATED
        } catch (e: Exception) {
            Result.FAILED
        } finally {
            dbHelper.close()
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

    private fun fetchVersionInfo(): VersionInfo? {
        val response = client.newCall(Request.Builder().url(BuildConfig.DB_VERSION_URL).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val body = response.use { it.body?.string() } ?: return null
        val json = JSONObject(body)
        val version = json.optString("version").takeIf { it.isNotBlank() } ?: return null
        val url = json.optString("url").takeIf { it.isNotBlank() } ?: return null
        return VersionInfo(version, url)
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
