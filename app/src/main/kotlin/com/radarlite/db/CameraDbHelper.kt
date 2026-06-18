package com.radarlite.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.radarlite.util.GeoUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class CameraDbHelper(private val context: Context) {
    private var db: SQLiteDatabase? = null
    private val mutex = Mutex()

    fun open() {
        db = SQLiteDatabase.openDatabase(ensureDbFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    suspend fun reopen() = mutex.withLock {
        db?.close()
        db = null
        open()
    }

    private fun ensureDbFile(): File {
        val file = context.getDatabasePath("cameras.db")
        if (file.exists()) return file

        file.parentFile?.mkdirs()
        try {
            context.assets.open("cameras.db").use { it.copyTo(file.outputStream()) }
        } catch (e: Exception) {
            createEmptySchema(file)
        }
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
        val db = db ?: return@withLock emptyList()
        val box = GeoUtils.boundingBox(lat, lon, radiusM)
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
                            id          = c.getLong(0),
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

    fun getVersion(): String? = try {
        db?.rawQuery("SELECT value FROM meta WHERE key='version'", null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) { null }

    fun getCameraCount(): Int = try {
        db?.rawQuery("SELECT COUNT(*) FROM cameras", null)?.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        } ?: 0
    } catch (e: Exception) { 0 }

    suspend fun replaceWith(newFile: File) = mutex.withLock {
        db?.close()
        db = null
        val dest = context.getDatabasePath("cameras.db")
        val swap = File(dest.parentFile, "cameras.db.new")
        // Replace by rename so any already-open reader keeps the old file handle until it reopens.
        newFile.copyTo(swap, overwrite = true)
        if (dest.exists() && !dest.delete()) throw IllegalStateException("Could not replace camera database")
        if (!swap.renameTo(dest)) {
            swap.copyTo(dest, overwrite = true)
            swap.delete()
        }
        db = SQLiteDatabase.openDatabase(dest.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun close() {
        db?.close()
        db = null
    }
}
