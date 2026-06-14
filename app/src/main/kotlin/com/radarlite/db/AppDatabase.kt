package com.radarlite.db

import android.content.Context
import androidx.room.*

@Database(entities = [AlertLogEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alertLogDao(): AlertLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "radarlite_app.db"
            ).build().also { instance = it }
        }
    }
}
