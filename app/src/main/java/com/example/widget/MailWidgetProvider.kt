package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.EmailEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MailWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform update for each active widget
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.widget.UPDATE_WIDGET") {
            Log.d("MailWidgetProvider", "Received update request broadcast!")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MailWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Run database queries on IO coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                // Fetch high priority emails
                val emails = db.emailDao().getAllEmails().first()
                    .filter { it.priority.uppercase() == "HIGH" }
                    .take(3)

                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                
                // Show dynamic sync time
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                views.setTextViewText(R.id.widget_sync_time, "Synced: ${sdf.format(Date())}")

                if (emails.isEmpty()) {
                    views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_items_container, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_empty_text, View.GONE)
                    views.setViewVisibility(R.id.widget_items_container, View.VISIBLE)

                    // Bind items
                    bindCard(views, R.id.widget_card_1, R.id.widget_email_1_sender, R.id.widget_email_1_subject, R.id.widget_email_1_summary, R.id.widget_email_1_time, emails.getOrNull(0))
                    bindCard(views, R.id.widget_card_2, R.id.widget_email_2_sender, R.id.widget_email_2_subject, R.id.widget_email_2_summary, R.id.widget_email_2_time, emails.getOrNull(1))
                    bindCard(views, R.id.widget_card_3, R.id.widget_email_3_sender, R.id.widget_email_3_subject, R.id.widget_email_3_summary, R.id.widget_email_3_time, emails.getOrNull(2))
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("MailWidgetProvider", "Error updating widget state: ", e)
            }
        }
    }

    private fun bindCard(
        views: RemoteViews,
        cardId: Int,
        senderId: Int,
        subjectId: Int,
        summaryId: Int,
        timeId: Int,
        email: EmailEntity?
    ) {
        if (email == null) {
            views.setViewVisibility(cardId, View.GONE)
        } else {
            views.setViewVisibility(cardId, View.VISIBLE)
            // Parse clean name out of email sender
            val cleanSender = email.sender.substringBefore("<").trim()
            views.setTextViewText(senderId, if (cleanSender.isEmpty()) email.sender else cleanSender)
            views.setTextViewText(subjectId, email.subject)
            views.setTextViewText(summaryId, email.summary)
            
            // Format time delta
            val diffMs = System.currentTimeMillis() - email.internalDate
            val minutes = diffMs / 60000
            val timeText = when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                minutes < 1440 -> "${minutes / 60}h ago"
                else -> "${minutes / 1440}d ago"
            }
            views.setTextViewText(timeId, timeText)
        }
    }
}
