package com.example.activityapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = "activity_events")
data class ActivityEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "Movement", "Stationary", "Alert"
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
