package com.example.activityapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(event: ActivityEvent)

    @Query("SELECT * FROM activity_events ORDER BY timestamp DESC LIMIT 100")
    fun getAllEvents(): Flow<List<ActivityEvent>>

    @Query("DELETE FROM activity_events")
    suspend fun clearAll()
}
