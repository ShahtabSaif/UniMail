package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.EmailEntity
import com.example.data.model.ReminderEntity
import com.example.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MailViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = MailRepository(
        context = application,
        emailDao = db.emailDao(),
        reminderDao = db.reminderDao(),
        calendarEventDao = db.calendarEventDao()
    )

    private val prefs = application.getSharedPreferences("google_auth_prefs", Context.MODE_PRIVATE)

    // UI States
    val activeTab = MutableStateFlow(0) // 0 = Inbox, 1 = Calendar & Reminders
    val isAuthenticated = MutableStateFlow(false)
    val accessToken = MutableStateFlow<String?>(null)
    val connectedEmail = MutableStateFlow<String?>(null)
    val isLoading = MutableStateFlow(false)
    val syncStatusMessage = MutableStateFlow<String?>(null)

    // Main Flows (Offline Access to synchronized client DB)
    val emails: StateFlow<List<EmailEntity>> = repository.allEmails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calendarEvents: StateFlow<List<com.example.data.model.CalendarEventEntity>> = repository.allCalendarEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load persistent authorization state if existing
        val savedToken = prefs.getString("access_token", null)
        val savedEmail = prefs.getString("connected_email", null)
        if (!savedToken.isNullOrEmpty() && !savedEmail.isNullOrEmpty()) {
            accessToken.value = savedToken
            connectedEmail.value = savedEmail
            isAuthenticated.value = true
            // Run automatic data background synchronization
            triggerSync(savedToken)
        }
    }

    /**
     * Trigger refresh/sync using Google API access token
     */
    fun triggerSync(token: String?) {
        val activeToken = token ?: accessToken.value
        if (activeToken.isNullOrEmpty()) {
            syncStatusMessage.value = "Auth token is missing. Please sign in."
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            syncStatusMessage.value = "Syncing Gmail & Calendar..."
            try {
                val result = repository.syncWithGoogle(activeToken)
                if (result.isSuccess) {
                    syncStatusMessage.value = "Gmail & Google Calendar synced successfully!"
                } else {
                    val exception = result.exceptionOrNull()
                    val isUnauthorized = (exception is retrofit2.HttpException && exception.code() == 401) ||
                            (exception?.message?.contains("401") == true)
                    val isForbidden = (exception is retrofit2.HttpException && exception.code() == 403) ||
                            (exception?.message?.contains("403") == true)
                    
                    if (isUnauthorized) {
                        syncStatusMessage.value = "Sync failed: HTTP 401 Unauthorized (Credentials Expired). Please logout and re-link a new secure Token!"
                    } else if (isForbidden) {
                        syncStatusMessage.value = "Sync failed: HTTP 403 (Forbidden). " +
                                "Please make sure 'Gmail API' and 'Google Calendar API' are ENABLED in your Google Cloud Project library tab, and your email is added under 'OAuth Consent Screen > Test Users'!"
                    } else {
                        val error = exception?.message ?: "Unknown error"
                        syncStatusMessage.value = "Sync failed: $error"
                    }
                }
            } catch (e: Exception) {
                syncStatusMessage.value = "Sync error: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun performLogin(token: String, email: String) {
        viewModelScope.launch {
            prefs.edit()
                .putString("access_token", token)
                .putString("connected_email", email)
                .apply()

            accessToken.value = token
            connectedEmail.value = email
            isAuthenticated.value = true

            triggerSync(token)
        }
    }

    fun performDemoLogin() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                // Seed local DB with simulated mail and timeline items
                repository.generateOfflineMockData()

                prefs.edit()
                    .putString("access_token", "DEMO_TOKEN")
                    .putString("connected_email", "demo.user@focus-simulation.local")
                    .apply()

                accessToken.value = "DEMO_TOKEN"
                connectedEmail.value = "demo.user@focus-simulation.local"
                isAuthenticated.value = true
                syncStatusMessage.value = "Welcome to UniMail Simulation Mode!"
            } catch (e: Exception) {
                syncStatusMessage.value = "Simulation Error: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun performLogout() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                // Remove OAuth keys
                prefs.edit().clear().apply()

                // Purge synchronized local database entries for security
                repository.clearAllCachedData()

                accessToken.value = null
                connectedEmail.value = null
                isAuthenticated.value = false
                syncStatusMessage.value = "Signed out successfully."
            } catch (e: Exception) {
                syncStatusMessage.value = "Sign out error: ${e.localizedMessage}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun toggleReminderCompleted(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
        }
    }

    fun addCustomReminder(title: String, notes: String, epochMs: Long, syncToCalendar: Boolean, googleToken: String?) {
        viewModelScope.launch {
            val reminder = ReminderEntity(
                title = title,
                notes = notes,
                dueTime = epochMs,
                isCompleted = false
            )
            repository.addReminder(reminder, syncToCalendar, googleToken)
            syncStatusMessage.value = "Custom reminder '$title' scheduled successfully!"
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
        }
    }

    fun clearStatusMessage() {
        syncStatusMessage.value = null
    }

    // Factory Provider
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MailViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MailViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
