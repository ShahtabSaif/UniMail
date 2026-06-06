package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.CalendarEventDao
import com.example.data.local.EmailDao
import com.example.data.local.ReminderDao
import com.example.data.model.CalendarEventEntity
import com.example.data.model.EmailEntity
import com.example.data.model.ReminderEntity
import com.example.data.remote.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MailRepository(
    private val context: Context,
    private val emailDao: EmailDao,
    private val reminderDao: ReminderDao,
    private val calendarEventDao: CalendarEventDao,
    private val gmailService: GmailService = GoogleApisNetwork.gmail,
    private val calendarService: CalendarService = GoogleApisNetwork.calendar,
    private val geminiService: GeminiApiService = GeminiNetwork.service
) {
    val allEmails: Flow<List<EmailEntity>> = emailDao.getAllEmails()
    val highPriorityEmails: Flow<List<EmailEntity>> = emailDao.getHighPriorityEmails()
    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()
    val allCalendarEvents: Flow<List<CalendarEventEntity>> = calendarEventDao.getAllEvents()

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(EmailAnalysisResult::class.java)

    /**
     * Decode Gmail Base64 safe-url strings
     */
    private fun decodeBase64Url(input: String?): String {
        if (input == null) return ""
        return try {
            val decodedBytes = android.util.Base64.decode(input, android.util.Base64.URL_SAFE)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Extracts readable text body from GmailMessageDetail
     */
    private fun extractEmailBody(detail: GmailMessageDetail): String {
        val payload = detail.payload ?: return detail.snippet ?: ""
        
        val bodyText = StringBuilder()
        if (payload.parts != null) {
            for (part in payload.parts) {
                if (part.mimeType == "text/plain" && part.body?.data != null) {
                    bodyText.append(decodeBase64Url(part.body.data))
                }
            }
        } else if (payload.body?.data != null) {
            bodyText.append(decodeBase64Url(payload.body.data))
        }
        
        val result = bodyText.toString().trim()
        return if (result.isEmpty()) detail.snippet ?: "" else result
    }

    /**
     * Sync with actual Gmail & Google Calendar using an OAuth access token
     */
    suspend fun syncWithGoogle(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        val authHeader = "Bearer $token"
        try {
            // 1. Fetch Gmail message list
            val listResponse = gmailService.listMessages(authHeader, maxResults = 10)
            val messages = listResponse.messages ?: emptyList()
            
            val emailEntities = mutableListOf<EmailEntity>()
            for (msgRef in messages) {
                // Check if already in DB
                val existing = emailDao.getEmailById(msgRef.id)
                if (existing != null) {
                    emailEntities.add(existing)
                    continue
                }

                // Fetch details
                val detail = gmailService.getMessage(authHeader, msgRef.id)
                val body = extractEmailBody(detail)
                val sender = detail.payload?.headers?.find { it.name.lowercase() == "from" }?.value ?: "Unknown Sender"
                val subject = detail.payload?.headers?.find { it.name.lowercase() == "subject" }?.value ?: "(No Subject)"
                val internalDate = detail.internalDate?.toLongOrNull() ?: System.currentTimeMillis()

                // Run AI Analysis
                val analysis = analyzeEmailWithAI(sender, subject, body)
                
                val emailEntity = EmailEntity(
                    id = msgRef.id,
                    sender = sender,
                    subject = subject,
                    body = body.take(1500), // Limit storage size
                    snippet = detail.snippet ?: "",
                    internalDate = internalDate,
                    priority = analysis.priority,
                    priorityReason = analysis.priorityReason,
                    summary = analysis.summary,
                    needsReminder = analysis.needsReminder,
                    suggestedReminderTitle = analysis.reminderTitle,
                    suggestedReminderTime = if (analysis.needsReminder) {
                        System.currentTimeMillis() + (analysis.reminderDelayMinutes * 60000L)
                    } else 0L
                )
                emailDao.insertEmail(emailEntity)
                emailEntities.add(emailEntity)
            }

            // 2. Fetch Google Calendar events (to overlap timelines)
            val rfc3339Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val timeMinString = rfc3339Format.format(Date(System.currentTimeMillis() - 7 * 24 * 3600 * 1000L)) // 7 days ago
            val calendarResponse = calendarService.listEvents(authHeader, timeMin = timeMinString)
            val calendarItems = calendarResponse.items ?: emptyList()

            val calendarEventEntities = calendarItems.mapNotNull { item ->
                val id = item.id ?: return@mapNotNull null
                val summary = item.summary ?: "No Title"
                val description = item.description ?: ""
                val startStr = item.start?.dateTime ?: item.start?.date
                val endStr = item.end?.dateTime ?: item.end?.date
                
                val startTime = parseRfcOrDate(startStr) ?: System.currentTimeMillis()
                val endTime = parseRfcOrDate(endStr) ?: (startTime + 3600000L)

                CalendarEventEntity(id, summary, description, startTime, endTime)
            }

            calendarEventDao.clearAll()
            calendarEventDao.insertEvents(calendarEventEntities)

            // Trigger Widget Update Broadcast
            updateWidget()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MailRepository", "Error syncing with Google: ", e)
            Result.failure(e)
        }
    }

    private fun parseRfcOrDate(input: String?): Long? {
        if (input == null) return null
        return try {
            val format = if (input.contains("T")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            }
            format.parse(input)?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Run Gemini intelligence to categorize, prioritize and summarize email
     */
    suspend fun analyzeEmailWithAI(sender: String, subject: String, body: String): EmailAnalysisResult {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                Log.w("MailRepository", "Gemini API key is missing. Using static analysis heuristics.")
                return@withContext runLocalHeuristics(sender, subject, body)
            }

            val prompt = """
                You are a premium, professional personal email assistant.
                Analyze the following email details:
                Sender: $sender
                Subject: $subject
                Body: $body

                Task: You MUST output a clean, valid visual JSON block matching exactly this schema, with no markdown wrappers or enclosing blocks other than the raw JSON itself:
                {
                  "priority": "HIGH" or "NORMAL" or "LOW",
                  "priorityReason": "1 short sentence explaining why this priority was chosen",
                  "summary": "1-2 sentence concise summary of the email",
                  "needsReminder": true or false,
                  "reminderTitle": "A concise actionable reminder title (e.g., 'Submit Project Report')",
                  "reminderDelayMinutes": 120
                }
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                generationConfig = GeminiGenerationConfig(responseMimeType = "application/json", temperature = 0.2f)
            )

            try {
                val response = geminiService.analyzeEmail(apiKey, request)
                val jsonString = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                Log.d("MailRepository", "AI response: $jsonString")
                if (jsonString.isNotEmpty()) {
                    adapter.fromJson(jsonString) ?: runLocalHeuristics(sender, subject, body)
                } else {
                    runLocalHeuristics(sender, subject, body)
                }
            } catch (e: Exception) {
                Log.e("MailRepository", "Gemini API failed, falling back to heuristics.", e)
                runLocalHeuristics(sender, subject, body)
            }
        }
    }

    private fun runLocalHeuristics(sender: String, subject: String, body: String): EmailAnalysisResult {
        val lowercaseText = "$subject $body".lowercase()
        val priority: String
        val reason: String
        var needsReminder = false
        var reminderTitle = "Follow-up on: $subject"
        var delay = 180

        if (lowercaseText.contains("urgent") || lowercaseText.contains("immediate") || lowercaseText.contains("asap") || lowercaseText.contains("action required")) {
            priority = "HIGH"
            reason = "Detected urgency signal words in the email content."
            needsReminder = true
            reminderTitle = "URGENT Follow-up: $subject"
            delay = 60
        } else if (lowercaseText.contains("tomorrow") || lowercaseText.contains("deadline") || lowercaseText.contains("schedule") || lowercaseText.contains("meeting")) {
            priority = "HIGH"
            reason = "Context references scheduling or upcoming deadliness."
            needsReminder = true
            reminderTitle = "Schedule Event: $subject"
            delay = 120
        } else if (lowercaseText.contains("discount") || lowercaseText.contains("newsletter") || lowercaseText.contains("off today") || lowercaseText.contains("unsubscribe")) {
            priority = "LOW"
            reason = "Heuristic classified this as a promotional newsletter."
        } else {
            priority = "NORMAL"
            reason = "Standard correspondence, no immediate action keywords found."
            if (sender.lowercase().contains("boss") || sender.lowercase().contains("manager")) {
                needsReminder = true
                reminderTitle = "Review email from management"
                delay = 240
            }
        }

        return EmailAnalysisResult(
            priority = priority,
            priorityReason = reason,
            summary = "Summary: $subject - Brief overview of details from $sender.",
            needsReminder = needsReminder,
            reminderTitle = reminderTitle,
            reminderDelayMinutes = delay
        )
    }

    /**
     * Add/Insert custom local reminder, optionally push to calendar
     */
    suspend fun addReminder(reminder: ReminderEntity, syncToGoogle: Boolean = false, token: String? = null): Long = withContext(Dispatchers.IO) {
        var finalReminder = reminder
        if (syncToGoogle && token != null) {
            try {
                val calendarRequest = CreateEventRequest(
                    summary = reminder.title,
                    description = reminder.notes,
                    start = CalendarTime(rfcString(reminder.dueTime), null),
                    end = CalendarTime(rfcString(reminder.dueTime + 3600000L), null) // 1 Hour
                )
                val createdItem = calendarService.createEvent("Bearer $token", calendarRequest)
                if (createdItem.id != null) {
                    finalReminder = reminder.copy(calendarEventId = createdItem.id)
                }
            } catch (e: Exception) {
                Log.e("MailRepository", "Failed to sync event to Google Calendar: ", e)
            }
        }
        val id = reminderDao.insertReminder(finalReminder)
        updateWidget()
        id
    }

    private fun rfcString(milli: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(milli))
    }

    suspend fun updateReminder(reminder: ReminderEntity) = withContext(Dispatchers.IO) {
        reminderDao.updateReminder(reminder)
        updateWidget()
    }

    suspend fun deleteReminder(reminder: ReminderEntity) = withContext(Dispatchers.IO) {
        reminderDao.deleteReminder(reminder)
        updateWidget()
    }

    suspend fun clearAllCachedData() = withContext(Dispatchers.IO) {
        emailDao.clearAll()
        calendarEventDao.clearAll()
        reminderDao.clearAll()
        updateWidget()
    }

    /**
     * Advanced Mode for Android Emulators: Offline Mock Generator combined with Real Gemini API
     */
    suspend fun generateOfflineMockData() = withContext(Dispatchers.IO) {
        emailDao.clearAll()
        calendarEventDao.clearAll()
        reminderDao.clearAll()

        val mockEmailsRaw = listOf(
            Triple(
                "DevOps Team <infra@company.com>",
                "CRITICAL: Production DB migration scheduling tomorrow at 8:00 AM",
                "Hi Shahtab, we have finalize the timeline for migrating our main PostgreSQL cluster in AWS. We require your explicit sign-off by tonight or we will have to delay the maintenance window. Please review the schema specs."
            ),
            Triple(
                "Booking.com <confirm@booking.com>",
                "Your hotel reservation for tomorrow is confirmed!",
                "Check-in details: Room 102. Check-in time starts at 3:00 PM. Address: 15 Grand Boulevard, Tokyo."
            ),
            Triple(
                "Google Flights <flights-noreply@google.com>",
                "Update: Flight delay notice SQ-212",
                "Hi! Your flight SQ-212 scheduled tomorrow has been delayed by 45 minutes. New departure time: 11:30 AM."
            ),
            Triple(
                "SunnyDeals <promo@sunnydeals.com>",
                "Last Chance! 75% OFF Beach Blankets and Swimming Gear",
                "Don't miss our summer clearances! Buy two get three free. Ends tonight. Go to our website to buy now."
            ),
            Triple(
                "Sarah (Design) <sarah@agency.io>",
                "Design draft ready: Client homepage redesign proposal",
                "Hey! I finished the mockups for the homepage interface. I used Material 3 Slate colors. Let's Sync up tomorrow afternoon around 2 PM to refine details before demonstrating to the executives."
            )
        )

        val calendarMockItems = listOf(
            CalendarEventEntity("c1", "Weekly Standup", "Company sync-up", System.currentTimeMillis() - 3600000L, System.currentTimeMillis()),
            CalendarEventEntity("c2", "Lunch with Product", "Discuss redesign details", System.currentTimeMillis() + 4 * 3600000L, System.currentTimeMillis() + 5 * 3600000L),
            CalendarEventEntity("c3", "Doctor Appointment", "Standard checkup", System.currentTimeMillis() + 24 * 3600000L, System.currentTimeMillis() + 25 * 3600000L)
        )
        calendarEventDao.insertEvents(calendarMockItems)

        val processedEmails = mutableListOf<EmailEntity>()
        val baseTime = System.currentTimeMillis()
        for ((idx, email) in mockEmailsRaw.withIndex()) {
            val (sender, subject, body) = email
            val analysis = analyzeEmailWithAI(sender, subject, body)
            
            val emailEntity = EmailEntity(
                id = "mock_msg_$idx",
                sender = sender,
                subject = subject,
                body = body,
                snippet = body.take(80),
                internalDate = baseTime - (idx * 30 * 60 * 1000L), // offset by 30 mins each
                priority = analysis.priority,
                priorityReason = analysis.priorityReason,
                summary = analysis.summary,
                needsReminder = analysis.needsReminder,
                suggestedReminderTitle = analysis.reminderTitle,
                suggestedReminderTime = if (analysis.needsReminder) {
                    baseTime + (analysis.reminderDelayMinutes * 60000L)
                } else 0L
            )
            emailDao.insertEmail(emailEntity)
            processedEmails.add(emailEntity)

            // Auto-schedule important mock reminders to show calendar integration
            if (analysis.needsReminder) {
                val mockReminder = ReminderEntity(
                    emailId = emailEntity.id,
                    title = analysis.reminderTitle,
                    notes = "Automatically scheduled from email: ${emailEntity.subject}",
                    dueTime = baseTime + (analysis.reminderDelayMinutes * 60000L),
                    isCompleted = false
                )
                reminderDao.insertReminder(mockReminder)
            }
        }

        updateWidget()
    }

    /**
     * Notify Widget to update elements
     */
    private fun updateWidget() {
        val intent = android.content.Intent("com.example.widget.UPDATE_WIDGET")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
        Log.d("MailRepository", "Sent widget update broadcast!")
    }
}
