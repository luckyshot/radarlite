package com.radarlite.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AlertLogDao {

    @Query("SELECT * FROM alert_log ORDER BY timestamp DESC LIMIT 50")
    fun getRecent(): LiveData<List<AlertLogEntry>>

    @Insert
    suspend fun insert(entry: AlertLogEntry)

    @Query("DELETE FROM alert_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
