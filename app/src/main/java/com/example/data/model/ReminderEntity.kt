package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val emailId: String? = null,
    val title: String,
    val notes: String,
    val dueTime: Long,
    val isCompleted: Boolean = false,
    val calendarEventId: String? = null
)
