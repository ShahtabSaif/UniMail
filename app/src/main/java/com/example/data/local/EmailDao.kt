package com.example.data.local

import androidx.room.*
import com.example.data.model.EmailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails ORDER BY internalDate DESC")
    fun getAllEmails(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :id")
    suspend fun getEmailById(id: String): EmailEntity?

    @Query("SELECT * FROM emails WHERE priority = 'HIGH' ORDER BY internalDate DESC")
    fun getHighPriorityEmails(): Flow<List<EmailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailEntity)

    @Query("DELETE FROM emails")
    suspend fun clearAll()
}
