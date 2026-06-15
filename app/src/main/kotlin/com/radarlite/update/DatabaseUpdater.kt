package com.radarlite.update

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    private const val PREFS_NAME  = "radarlite_prefs"
    private const val KEY_LAST_CHECK = "last_db_check_ms"
    private const val KEY_DB_VERSION = "db_version"
    private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun checkAndUpdate(
        context: Context,
        dbHelper: CameraDbHelper,
        requireWifi: Boolean = true
    ): Result {
        if (requireWifi && !isOnWifi(context)) return Result.NOT_ON_WIFI

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL) return Result.UP_TO_DATE

        return withContext(Dispatchers.IO) {
            try {
                val remote = fetchVersionInfo() ?: return@withContext Result.FAILED
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
            }
        }
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

    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.activeNetwork?.let {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: false
    }

    enum class Result { UPDATED, UP_TO_DATE, NOT_ON_WIFI, FAILED }
    private data class VersionInfo(val version: String, val url: String)
}
