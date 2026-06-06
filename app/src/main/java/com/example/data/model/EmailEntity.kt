package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val subject: String,
    val body: String,
    val snippet: String,
    val internalDate: Long,
    val priority: String, // "HIGH", "NORMAL", "LOW"
    val priorityReason: String,
    val summary: String,
    val needsReminder: Boolean,
    val suggestedReminderTitle: String,
    val suggestedReminderTime: Long
)
