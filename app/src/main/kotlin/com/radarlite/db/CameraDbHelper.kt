package com.radarlite.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.radarlite.Countries
import com.radarlite.util.GeoUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

// Each active country keeps its own SQLite file and its own auto-increment row ids.
// This offset keeps merged Camera.id values unique across up to a few active countries.
private const val COUNTRY_ID_SPACE = 1_000_000_000L

class CameraDbHelper(private val context: Context) {
    private var dbs: Map<String, SQLiteDatabase> = emptyMap()
    private val mutex = Mutex()

    fun open(codes: List<String>) {
        dbs = codes.associateWith { code ->
            SQLiteDatabase.openDatabase(ensureDbFile(code).absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }
    }

    suspend fun reopen(codes: List<String>) = mutex.withLock {
        closeLocked()
        open(codes)
    }

    private fun dbFile(code: String): File = context.getDatabasePath("cameras-$code.db")

    private fun ensureDbFile(code: String): File {
        val file = dbFile(code)
        if (file.exists()) return file

        file.parentFile?.mkdirs()
        if (code == Countries.DEFAULT_CODE) {
            // One-time migration from the pre-multi-country filename, so an already
            // downloaded default-country database is not silently replaced.
            val legacy = context.getDatabasePath("cameras.db")
            if (legacy.exists() && legacy.renameTo(file)) return file
            try {
                context.assets.open("cameras.db").use { it.copyTo(file.outputStream()) }
                return file
            } catch (e: Exception) { /* no bundled asset: fall through to an empty schema */ }
        }
        createEmptySchema(file)
        return file
    }

    private fun createEmptySchema(file: File) {
        val tmp = SQLiteDatabase.openOrCreateDatabase(file, null)
        tmp.execSQL("""
            CREATE TABLE IF NOT EXISTS cameras (
                id INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL,
                speed_limit INTEGER, type TEXT DEFAULT 'speed',
                direction INTEGER, sources TEXT
            )
        """.trimIndent())
        tmp.execSQL("CREATE INDEX IF NOT EXISTS idx_lat ON cameras(lat)")
        tmp.execSQL("CREATE INDEX IF NOT EXISTS idx_lon ON cameras(lon)")
        tmp.execSQL("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)")
        tmp.close()
    }

    suspend fun getCamerasNear(lat: Double, lon: Double, radiusM: Float): List<Camera> = mutex.withLock {
        val box = GeoUtils.boundingBox(lat, lon, radiusM)
        dbs.values.toList().flatMapIndexed { slot, db ->
            val cursor = db.rawQuery(
                "SELECT id,lat,lon,speed_limit,type,direction,sources FROM cameras WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                arrayOf(box[0].toString(), box[1].toString(), box[2].toString(), box[3].toString())
            )
            buildList {
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val camLat = c.getDouble(1)
                        val camLon = c.getDouble(2)
                        if (GeoUtils.haversine(lat, lon, camLat, camLon) <= radiusM) {
                            add(Camera(
                                id          = c.getLong(0) + slot * COUNTRY_ID_SPACE,
                                lat         = camLat,
                                lon         = camLon,
                                speedLimit  = if (c.isNull(3)) null else c.getInt(3),
                                type        = c.getString(4) ?: "speed",
                                direction   = if (c.isNull(5)) null else c.getInt(5),
                                sources     = c.getString(6)
                            ))
                        }
                    }
                }
            }
        }
    }

    fun getVersion(code: String): String? = try {
        dbs[code]?.rawQuery("SELECT value FROM meta WHERE key='version'", null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) { null }

    fun getCameraCount(): Int = dbs.values.sumOf { db ->
        try {
            db.rawQuery("SELECT COUNT(*) FROM cameras", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (e: Exception) { 0 }
    }

    // "Country A 2026-09-12 · Country B —" for the active codes, in order.
    fun versionSummary(codes: List<String>): String =
        codes.joinToString(" · ") { code -> "${Countries.nameFor(code)} ${getVersion(code) ?: "—"}" }

    suspend fun replaceWith(code: String, newFile: File) = mutex.withLock {
        dbs[code]?.close()
        val dest = dbFile(code)
        val swap = File(dest.parentFile, "cameras-$code.db.new")
        // Replace by rename so any already-open reader keeps the old file handle until it reopens.
        newFile.copyTo(swap, overwrite = true)
        if (dest.exists() && !dest.delete()) throw IllegalStateException("Could not replace $code alert database")
        if (!swap.renameTo(dest)) {
            swap.copyTo(dest, overwrite = true)
            swap.delete()
        }
        dbs = dbs + (code to SQLiteDatabase.openDatabase(dest.absolutePath, null, SQLiteDatabase.OPEN_READONLY))
    }

    // Removes on-device country databases that are not one of the given active codes,
    // returning which codes were actually deleted so their version prefs can be cleared too.
    // Only called from an update check, so switching countries never deletes data right away.
    fun deleteInactive(activeCodes: List<String>): List<String> {
        val pattern = Regex("cameras-([A-Z]{2})\\.db")
        val deleted = mutableListOf<String>()
        dbFile("XX").parentFile?.listFiles()?.forEach { f ->
            val code = pattern.matchEntire(f.name)?.groupValues?.get(1) ?: return@forEach
            if (code !in activeCodes && f.delete()) deleted += code
        }
        return deleted
    }

    fun close() = closeLocked()

    private fun closeLocked() {
        dbs.values.forEach { it.close() }
        dbs = emptyMap()
    }
}
