package com.example.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- GMAIL API MODELS ---

@JsonClass(generateAdapter = true)
data class GmailListResponse(
    val messages: List<GmailMessageRef>?,
    val nextPageToken: String?,
    val resultSizeEstimate: Int?
)

@JsonClass(generateAdapter = true)
data class GmailMessageRef(
    val id: String,
    val threadId: String
)

@JsonClass(generateAdapter = true)
data class GmailMessageDetail(
    val id: String,
    val threadId: String,
    val labelIds: List<String>?,
    val snippet: String?,
    val historyId: String?,
    val internalDate: String?,
    val payload: GmailPayload?
)

@JsonClass(generateAdapter = true)
data class GmailPayload(
    val mimeType: String?,
    val headers: List<GmailHeader>?,
    val body: GmailBody?,
    val parts: List<GmailPart>?
)

@JsonClass(generateAdapter = true)
data class GmailHeader(
    val name: String,
    val value: String
)

@JsonClass(generateAdapter = true)
data class GmailBody(
    val size: Int?,
    val data: String?
)

@JsonClass(generateAdapter = true)
data class GmailPart(
    val partId: String?,
    val mimeType: String?,
    val body: GmailBody?
)

// --- GOOGLE CALENDAR API MODELS ---

@JsonClass(generateAdapter = true)
data class CalendarEventsResponse(
    val items: List<CalendarEventItem>?
)

@JsonClass(generateAdapter = true)
data class CalendarEventItem(
    val id: String?,
    val summary: String?,
    val description: String?,
    val start: CalendarTime?,
    val end: CalendarTime?
)

@JsonClass(generateAdapter = true)
data class CalendarTime(
    val dateTime: String?,
    val date: String?
)

@JsonClass(generateAdapter = true)
data class CreateEventRequest(
    val summary: String,
    val description: String?,
    val start: CalendarTime,
    val end: CalendarTime
)

// --- RETROFIT INTERFACES ---

interface GmailService {
    @GET("gmail/v1/users/me/messages")
    suspend fun listMessages(
        @Header("Authorization") authHeader: String,
        @Query("maxResults") maxResults: Int = 10,
        @Query("q") query: String? = null
    ): GmailListResponse

    @GET("gmail/v1/users/me/messages/{id}")
    suspend fun getMessage(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String,
        @Query("format") format: String = "full"
    ): GmailMessageDetail
}

interface CalendarService {
    @GET("calendar/v3/calendars/primary/events")
    suspend fun listEvents(
        @Header("Authorization") authHeader: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("timeMin") timeMin: String? = null,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime"
    ): CalendarEventsResponse

    @POST("calendar/v3/calendars/primary/events")
    suspend fun createEvent(
        @Header("Authorization") authHeader: String,
        @Body event: CreateEventRequest
    ): CalendarEventItem
}

object GoogleApisNetwork {
    private const val BASE_URL = "https://www.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val gmail: GmailService by lazy { retrofit.create(GmailService::class.java) }
    val calendar: CalendarService by lazy { retrofit.create(CalendarService::class.java) }
}
