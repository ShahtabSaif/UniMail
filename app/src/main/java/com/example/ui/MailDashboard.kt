package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.model.CalendarEventEntity
import com.example.data.model.EmailEntity
import com.example.data.model.ReminderEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDashboard(
    viewModel: MailViewModel,
    modifier: Modifier = Modifier
) {
    val emails by viewModel.emails.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val calendarEvents by viewModel.calendarEvents.collectAsState()

    val activeTab by viewModel.activeTab.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val connectedEmail by viewModel.connectedEmail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.syncStatusMessage.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state controllers
    var selectedEmailForReminder by remember { mutableStateOf<EmailEntity?>(null) }
    var viewingEmailDetail by remember { mutableStateOf<EmailEntity?>(null) }
    var showManualReminderDialog by remember { mutableStateOf(false) }

    // Custom Computed Initials for the User Avatar
    val userInitials = remember(connectedEmail) {
        val emailCopy = connectedEmail
        if (!emailCopy.isNullOrEmpty() && emailCopy.contains("@")) {
            emailCopy.substringBefore("@").take(2).uppercase()
        } else if (!emailCopy.isNullOrEmpty()) {
            emailCopy.take(2).uppercase()
        } else {
            "ME"
        }
    }

    val highPriorityCount = remember(emails) {
        emails.count { it.priority.uppercase() == "HIGH" }
    }

    // Handle incoming status messages cleanly
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearStatusMessage()
        }
    }

    if (!isAuthenticated) {
        GoogleSignInScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            onSignInSuccess = { token, email ->
                viewModel.performLogin(token, email)
            }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                // Bento Styled Header Bar
                Surface(
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left profile avatar + titles combo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // JD / ME custom generated initials badge inside Lavender bubble
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .shadow(8.dp, CircleShape, clip = false)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD0BCFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userInitials,
                                    color = Color(0xFF381E72),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Text(
                                    text = "UniMail",
                                    color = Color(0xFFE6E1E5),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(
                                    text = connectedEmail ?: "Signed In",
                                    color = Color(0xFF938F99),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Rounded search/refresh button config (Bento-style button bg #2B2930)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B2930))
                                    .clickable {
                                        viewModel.triggerSync(null)
                                    }
                                    .testTag("refresh_action_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFD0BCFF)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Manual sync feed",
                                        tint = Color(0xFFE6E1E5),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Sign Out Button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B2930))
                                    .clickable {
                                        viewModel.performLogout()
                                    }
                                    .testTag("sign_out_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Sign out",
                                    tint = Color(0xFFF2B8B5),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (activeTab == 1) {
                    FloatingActionButton(
                        onClick = { showManualReminderDialog = true },
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF381E72),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("fab_add_reminder")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Schedule custom alarm task",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            containerColor = Color(0xFF1C1B1F),
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(Color(0xFF1C1B1F))
            ) {
                // Tabs Selector styled like bottom nav in Bento design
                TabSelector(
                    selectedTab = activeTab,
                    onTabSelected = { viewModel.activeTab.value = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                // Central pane rendering depending on active selections using Bento styled pages
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                    // Remove top bento banners entirely so user interacts purely with emails and timeline
                ) {
                    if (activeTab == 0) {
                        // TAB 0: Priority emails in Bento Layout
                        EmailFeedScreen(
                            emails = emails,
                            onEmailClick = { viewingEmailDetail = it },
                            onScheduleReminder = { selectedEmailForReminder = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // TAB 1: Timelines & Reminders planner in Bento Layout
                        TimelineScreen(
                            reminders = reminders,
                            calendarEvents = calendarEvents,
                            onToggleReminder = { viewModel.toggleReminderCompleted(it) },
                            onDeleteReminder = { viewModel.deleteReminder(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS CONTROLLERS ---

    // 1. Email Full Viewer Dialog
    viewingEmailDetail?.let { email ->
        EmailDetailDialog(
            email = email,
            onDismiss = { viewingEmailDetail = null },
            onScheduleReminder = {
                viewingEmailDetail = null
                selectedEmailForReminder = email
            }
        )
    }

    // 2. Email-triggered Custom Reminder dialog
    selectedEmailForReminder?.let { email ->
        ReminderSchedulerDialog(
            suggestedTitle = email.suggestedReminderTitle.ifEmpty { "Follow-up: ${email.subject}" },
            suggestedDelayMinutes = if (email.suggestedReminderTime > System.currentTimeMillis()) {
                ((email.suggestedReminderTime - System.currentTimeMillis()) / 60000).toInt()
            } else 120,
            onDismiss = { selectedEmailForReminder = null },
            onSave = { title, notes, epochMs, syncToCalendar ->
                val token = viewModel.accessToken.value
                viewModel.addCustomReminder(title, notes, epochMs, syncToCalendar, token)
                selectedEmailForReminder = null
            }
        )
    }

    // 3. Independent FAB-triggered Custom Reminder dialog
    if (showManualReminderDialog) {
        ReminderSchedulerDialog(
            suggestedTitle = "",
            suggestedDelayMinutes = 120,
            onDismiss = { showManualReminderDialog = false },
            onSave = { title, notes, epochMs, syncToCalendar ->
                val token = viewModel.accessToken.value
                viewModel.addCustomReminder(title, notes, epochMs, syncToCalendar, token)
                showManualReminderDialog = false
            }
        )
    }
}

// --- SUBVIEWS COMPONENTS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSignInScreen(
    viewModel: MailViewModel,
    isLoading: Boolean,
    onSignInSuccess: (token: String, email: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAppInfoDialog by remember { mutableStateOf(false) }
    var showRegistrySteps by remember { mutableStateOf(false) }

    // Standard high-fidelity Google Sign-In setup
    // We request email so that Google Sign-In succeeds first-time without requiring custom pre-registration of scopes on the client ID.
    // We will then perform incremental scopes authorization dynamically.
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // Launcher to resolve scope authorization dynamically when UserRecoverableAuthException is thrown
    val recoverableAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                val email = account.email ?: "shahtabhossain05@gmail.com"
                coroutineScope.launch {
                    viewModel.isLoading.value = true
                    viewModel.syncStatusMessage.value = "Retrieving authorized workspace token..."
                    try {
                        val scopes = "oauth2:https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/calendar"
                        val token = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val authAccount = android.accounts.Account(email, "com.google")
                            com.google.android.gms.auth.GoogleAuthUtil.getToken(context, authAccount, scopes)
                        }
                        if (!token.isNullOrEmpty()) {
                            onSignInSuccess(token, email)
                        } else {
                            errorMessage = "Token extraction unsuccessful after approval."
                        }
                    } catch (e: Exception) {
                        errorMessage = "Token extraction error: ${e.localizedMessage}"
                    } finally {
                        viewModel.isLoading.value = false
                    }
                }
            } else {
                errorMessage = "Could not identify signed in account to extract token."
            }
        } else {
            errorMessage = "Google Workspace scopes authorization was declined."
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val email = account.email ?: "shahtabhossain05@gmail.com"
                coroutineScope.launch {
                    viewModel.isLoading.value = true
                    viewModel.syncStatusMessage.value = "Retrieving Google auth token..."
                    try {
                        val scopes = "oauth2:https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/calendar"
                        val token = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val authAccount = android.accounts.Account(email, "com.google")
                            com.google.android.gms.auth.GoogleAuthUtil.getToken(context, authAccount, scopes)
                        }
                        if (!token.isNullOrEmpty()) {
                            onSignInSuccess(token, email)
                        } else {
                            errorMessage = "Could not retrieve secure access token automatically."
                        }
                    } catch (recoverable: com.google.android.gms.auth.UserRecoverableAuthException) {
                        recoverable.intent?.let { recoverableAuthLauncher.launch(it) }
                    } catch (e: Exception) {
                        errorMessage = "Credential extractor failed: ${e.localizedMessage}."
                    } finally {
                        viewModel.isLoading.value = false
                    }
                }
            } else {
                errorMessage = "Authentication request draft was dismissed."
            }
        } catch (e: Exception) {
            val status = if (e is ApiException) e.statusCode else -1
            var msg = "Google Sign-In failed: ${e.localizedMessage} (Status Code: $status)."
            if (status == 10) {
                msg += " (DEVELOPER_ERROR: Usually indicates the app's package name or debug SHA-1 signing fingerprint is not registered in Google Cloud Console. Ensure your key is registered.)"
                showRegistrySteps = true
            } else {
                showRegistrySteps = true
            }
            errorMessage = msg
        }
    }

    // Info Dialog explaining standard client limitations
    if (showAppInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAppInfoDialog = false },
            title = {
                Text(
                    text = "Google Workspace Connection",
                    color = Color(0xFFE6E1E5),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "UniMail connects securely to your personal Google account. It synchronizes your Gmail messages and Google Calendar events locally using direct on-device Google Play Services security, so your data never leaves your device.",
                    color = Color(0xFFCAC4D0),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showAppInfoDialog = false }) {
                    Text("Got It", color = Color(0xFFD0BCFF))
                }
            },
            containerColor = Color(0xFF2B2930),
            shape = RoundedCornerShape(28.dp)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Spacer(modifier = Modifier.height(40.dp))

            // Icon branding indicator
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF846ECA))
                    .border(1.5.dp, Color(0xFFE6E1E5).copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "UniMail Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(90.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "UniMail",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6E1E5)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sync secure Gmail workspace content and Active timelines with high-fidelity semantic AI prioritization.",
                fontSize = 13.sp,
                color = Color(0xFF938F99),
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Show error message elegantly if any
            if (errorMessage != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF8C1D18).copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, Color(0xFFF2B8B5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning Info",
                            tint = Color(0xFFF2B8B5),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Verification Restrictions Detected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF2B8B5)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = Color(0xFFCAC4D0)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFD0BCFF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Configuring security context...",
                            fontSize = 12.sp,
                            color = Color(0xFFCAC4D0),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Focus ONLY on the premium Sign In Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                    border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sign In",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6E1E5)
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "OAuth Info",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { showAppInfoDialog = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Play Services Button (Google Account Login)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color.White)
                                .clickable {
                                    errorMessage = null
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google Logo",
                                tint = Color(0xFF1C1B1F),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
                                color = Color(0xFF1C1B1F),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF211F26)),
                    border = BorderStroke(1.dp, Color(0xFF49454F).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("google_cloud_credentials_wizard")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showRegistrySteps = !showRegistrySteps },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Keys",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google Cloud Console Registration",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE6E1E5)
                                )
                            }
                            Icon(
                                imageVector = if (showRegistrySteps) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (showRegistrySteps) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "To enable secure Google Sign-In on your physical phone, you must register this app signature under your Google Cloud console OAuth Client credentials. Copy these keys precisely and follow the steps below:",
                                fontSize = 11.sp,
                                color = Color(0xFFCAC4D0),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            CredentialRow(
                                label = "Package Name",
                                value = "com.aistudio.mailreminders.kfwptf",
                                clipboardManager = clipboardManager,
                                context = context
                            )
                            CredentialRow(
                                label = "SHA-1 Fingerprint",
                                value = "B0:04:94:7E:CF:44:DD:A7:C8:4C:B2:80:CC:25:98:61:CF:2F:27:67",
                                clipboardManager = clipboardManager,
                                context = context
                            )
                            CredentialRow(
                                label = "SHA-256 Fingerprint",
                                value = "94:53:68:22:74:38:9E:EB:D2:16:11:4D:80:D5:AE:58:54:5E:6E:B3:D1:9F:E3:9A:AC:F2:65:07:A3:5C:D4:6A",
                                clipboardManager = clipboardManager,
                                context = context
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Steps to Register:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Go to Google Cloud Console > APIs & Services > Credentials.\n" +
                                        "2. Click '+ Create Credentials' > select 'OAuth client ID'.\n" +
                                        "3. Choose Application Type: 'Android'.\n" +
                                        "4. Enter the Package Name and SHA-1 certificate fingerprint copied above.\n" +
                                        "5. Click 'Create'.\n" +
                                        "6. CRITICAL: Go to 'OAuth consent screen' tab, and under 'Test users' click '+ ADD USERS' and add your email (e.g. \"shahtabhossain05@gmail.com\") to bypass the 'Access Blocked' restriction while in developer sandbox mode!\n" +
                                        "7. CRITICAL (Fixes HTTP 403 Sync Error): Use the top search bar in Google Cloud Console, search for 'Gmail API', and click 'Enable'. Then search for 'Google Calendar API' and click 'Enable'!",
                                fontSize = 11.sp,
                                color = Color(0xFFCAC4D0),
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        uriHandler.openUri("https://console.cloud.google.com/apis/credentials")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.2f).height(36.dp)
                                ) {
                                    Text("Open Google Console", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                                }

                                OutlinedButton(
                                    onClick = {
                                        val fullConfig = "Package Name: com.aistudio.mailreminders.kfwptf\nSHA-1 Fingerprint: B0:04:94:7E:CF:44:DD:A7:C8:4C:B2:80:CC:25:98:61:CF:2F:27:67\nSHA-256 Fingerprint: 94:53:68:22:74:38:9E:EB:D2:16:11:4D:80:D5:AE:58:54:5E:6E:B3:D1:9F:E3:9A:AC:F2:65:07:A3:5C:D4:6A"
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullConfig))
                                        android.widget.Toast.makeText(context, "Full configurations copied!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF49454F)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE6E1E5)),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Text("Copy All info", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun CredentialRow(
    label: String,
    value: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD0BCFF)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1D1B20))
                .border(1.dp, Color(0xFF49454F), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE6E1E5),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(value))
                    android.widget.Toast.makeText(context, "$label Copied!", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun TabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF2B2930)) // Bento bottom nav style
            .border(1.dp, Color(0xFF49454F).copy(alpha = 0.4f), RoundedCornerShape(28.dp))
            .padding(6.dp)
    ) {
        val tabConfigs = listOf(
            Pair("Emails & AI Summary", Icons.Default.Mail),
            Pair("Planner & Calendar", Icons.Default.DateRange)
        )

        tabConfigs.forEachIndexed { idx, pair ->
            val isSelected = selectedTab == idx
            val btnColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFFD0BCFF) else Color.Transparent
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF381E72) else Color(0xFFCAC4D0)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(btnColor)
                    .clickable { onTabSelected(idx) }
                    .padding(vertical = 12.dp)
                    .testTag("dashboard_tab_$idx")
            ) {
                Icon(
                    imageVector = pair.second,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pair.first,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun EmailFeedScreen(
    emails: List<EmailEntity>,
    onEmailClick: (EmailEntity) -> Unit,
    onScheduleReminder: (EmailEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (emails.isEmpty()) {
        Box(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                border = BorderStroke(1.dp, Color(0xFF49454F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AllInbox,
                        contentDescription = null,
                        tint = Color(0xFFD0BCFF).copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Focus Space is Empty",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Establish your workspace connection or press the sync icon at the top to retrieve live messages and organize your active schedule.",
                        fontSize = 12.sp,
                        color = Color(0xFF938F99),
                        lineHeight = 18.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val highPriority = emails.filter { it.priority.uppercase() == "HIGH" }
            val otherPriority = emails.filter { it.priority.uppercase() != "HIGH" }

            // Bento Priority Highlight Card (Featured Wide Bento Card)
            if (highPriority.isNotEmpty()) {
                item {
                    val featuredEmail = highPriority.find { it.needsReminder } ?: highPriority.first()
                    BentoPrioritySummaryCard(
                        email = featuredEmail,
                        onReadDetail = { onEmailClick(featuredEmail) },
                        onSchedule = { onScheduleReminder(featuredEmail) }
                    )
                }
            }

            // Recent Mail List Card Container (The Bento Mail Grid Container bg-[#1D1B20])
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
                    border = BorderStroke(1.dp, Color(0xFF49454F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mail,
                                    contentDescription = null,
                                    tint = Color(0xFF938F99),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Prioritized Correspondence",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF938F99)
                                )
                            }
                            // Synced badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4ADE80))
                                )
                                Text(
                                    text = "Synced",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF938F99)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val allSortedEmails = (highPriority + otherPriority).distinctBy { it.id }
                        allSortedEmails.forEachIndexed { index, email ->
                            if (index > 0 || highPriority.isEmpty()) { // Avoid repeating featured item if possible
                                BentoListRowItem(
                                    email = email,
                                    onClick = { onEmailClick(email) }
                                )
                                if (index < allSortedEmails.lastIndex) {
                                    HorizontalDivider(
                                        color = Color(0xFF49454F).copy(alpha = 0.3f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoPrioritySummaryCard(
    email: EmailEntity,
    onReadDetail: () -> Unit,
    onSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF49454F) // Featured Bento summary card
        ),
        border = BorderStroke(1.5.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onReadDetail() }
            .testTag("email_item_${email.id}")
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row with pill + sparkle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFD0BCFF))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AI Priority Summary",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF381E72)
                    )
                }

                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Email Subject/Sender Line
            Text(
                text = email.subject,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE6E1E5),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            val cleanSender = email.sender.substringBefore("<").trim()
            Text(
                text = "From: " + if (cleanSender.isEmpty()) email.sender else cleanSender,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD0BCFF)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // AI Generated body summary block
            Text(
                text = email.summary,
                fontSize = 14.sp,
                color = Color(0xFFCAC4D0),
                lineHeight = 19.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reason/Timeline trigger info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C1B1F).copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFEFB8C8),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "High priority: " + email.priorityReason,
                    fontSize = 10.sp,
                    color = Color(0xFFCAC4D0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons matching HTML theme
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (email.needsReminder) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFD0BCFF))
                            .clickable { onSchedule() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("action_schedule_reminder_${email.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = Color(0xFF381E72),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Set Reminder",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF381E72)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF313033))
                        .clickable { onReadDetail() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChromeReaderMode,
                            contentDescription = null,
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Read Full",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoListRowItem(
    email: EmailEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHigh = email.priority.uppercase() == "HIGH"
    val dotColor = if (isHigh) Color(0xFFD0BCFF) else Color(0xFF938F99)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .testTag("email_item_${email.id}")
    ) {
        // Simple visual indicator dot
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val cleanSender = email.sender.substringBefore("<").trim()
            Text(
                text = if (cleanSender.isEmpty()) email.sender else cleanSender,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6E1E5)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = email.subject,
                fontSize = 11.sp,
                color = Color(0xFFCAC4D0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = email.summary,
                fontSize = 10.sp,
                color = Color(0xFF938F99),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val formattedTime = remember(email.internalDate) {
            try {
                val now = System.currentTimeMillis()
                val delta = now - email.internalDate
                when {
                    delta < 0 -> "Now"
                    delta < 3600000 -> "${delta / 60000}m"
                    delta < 86400000 -> "${delta / 3600000}h"
                    else -> {
                        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                        sdf.format(Date(email.internalDate))
                    }
                }
            } catch (e: Exception) {
                "1h"
            }
        }

        Text(
            text = formattedTime,
            fontSize = 10.sp,
            color = Color(0xFF938F99),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

@Composable
fun TimelineScreen(
    reminders: List<ReminderEntity>,
    calendarEvents: List<CalendarEventEntity>,
    onToggleReminder: (ReminderEntity) -> Unit,
    onDeleteReminder: (ReminderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var nextCalendarEvent by remember(calendarEvents) {
        mutableStateOf(calendarEvents.firstOrNull())
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bento - Grid Row: Calendar Event Bento Card
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)), // Calendar bento item bg
                border = BorderStroke(1.dp, Color(0xFF49454F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFFEFB8C8), // Soft pink
                            modifier = Modifier.size(20.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEFB8C8).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Calendar Events",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = Color(0xFFEFB8C8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (nextCalendarEvent != null) {
                        val event = nextCalendarEvent!!
                        val formattedTime = remember(event.startTime) {
                            try {
                                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                sdf.format(Date(event.startTime))
                            } catch (e: Exception) {
                                "14:00"
                            }
                        }

                        Text(
                            text = formattedTime,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFFEFB8C8),
                            letterSpacing = (-1).sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = event.summary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6E1E5)
                        )

                        Text(
                            text = if (event.description.isNotEmpty()) event.description else "No event instructions verified",
                            fontSize = 11.sp,
                            color = Color(0xFF938F99),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        // Empty mock representation inside Bento card
                        Text(
                            text = "No upcoming events indexed offline",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF938F99)
                        )
                    }

                    if (calendarEvents.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFF49454F).copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Overlay Timelines agenda:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF938F99)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        calendarEvents.drop(1).take(2).forEach { ev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFEFB8C8)))
                                    Text(
                                        text = ev.summary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCAC4D0),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 180.dp)
                                    )
                                }
                                val evTime = try {
                                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ev.startTime))
                                } catch (e: Exception) { "" }
                                Text(
                                    text = evTime,
                                    fontSize = 10.sp,
                                    color = Color(0xFF938F99)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bento - Grid Row: Live Reminders Card (Pending tasks)
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF313033)), // Reminders card background
                border = BorderStroke(1.dp, Color(0xFF49454F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val pendingCount = reminders.count { !it.isCompleted }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Action Tasks",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = Color(0xFF938F99)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFD0BCFF).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$pendingCount Pending",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFD0BCFF)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (reminders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No active task alarms configured",
                                fontSize = 11.sp,
                                color = Color(0xFF938F99)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            reminders.forEach { reminder ->
                                BentoReminderItemRow(
                                    reminder = reminder,
                                    onToggle = { onToggleReminder(reminder) },
                                    onDelete = { onDeleteReminder(reminder) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoReminderItemRow(
    reminder: ReminderEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = if (reminder.isCompleted) Color(0xFF49454F) else Color(0xFFD0BCFF)
    val checkOpacity = if (reminder.isCompleted) 0.5f else 1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1B1F).copy(alpha = 0.2f))
            .testTag("reminder_item_${reminder.id}")
    ) {
        // Vertical indicator band (corresponds to border-l-2 pl-2 inside HTML)
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Checkbox aligned beautifully with targets >= 48dp
        Checkbox(
            checked = reminder.isCompleted,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFD0BCFF),
                uncheckedColor = Color(0xFF938F99),
                checkmarkColor = Color(0xFF381E72)
            ),
            modifier = Modifier
                .scale(0.85f)
                .testTag("checkbox_reminder_${reminder.id}")
        )

        Spacer(modifier = Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = reminder.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = if (reminder.isCompleted) Color(0xFF938F99) else Color(0xFFE6E1E5)
            )

            // Dynamic remaining / info time
            val dueText = remember(reminder.dueTime) {
                try {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val diff = reminder.dueTime - System.currentTimeMillis()
                    if (diff > 0 && diff < 3600000) {
                        "due in ${diff / 60000}m"
                    } else {
                        sdf.format(Date(reminder.dueTime))
                    }
                } catch (e: Exception) {
                    "due"
                }
            }

            Text(
                text = dueText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF938F99)
            )
        }

        // Mini Delete Sweep Button integrated safely
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(36.dp)
                .testTag("delete_reminder_button_${reminder.id}")
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = "Delete alarm reminder",
                tint = Color(0xFFEFB8C8).copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Dialog Component for Reading Email details
@Composable
fun EmailDetailDialog(
    email: EmailEntity,
    onDismiss: () -> Unit,
    onScheduleReminder: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = BorderStroke(1.dp, Color(0xFF49454F)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .testTag("dialog_email_detail")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Area
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val cleanSender = email.sender.substringBefore("<").trim()
                        Text(
                            text = if (cleanSender.isEmpty()) email.sender else cleanSender,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF)
                        )
                        val formattedTime = remember(email.internalDate) {
                            try {
                                val sdf = SimpleDateFormat("EEEE, MMM dd, yyyy - h:mm a", Locale.getDefault())
                                sdf.format(Date(email.internalDate))
                            } catch (e: Exception) {
                                ""
                            }
                        }
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            color = Color(0xFFCAC4D0)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close description dialog", tint = Color(0xFFE6E1E5))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF49454F).copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                // Subject Line
                Text(
                    text = email.subject,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic Summarizer strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFD0BCFF).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GEMINI SUMMARY CO-PILOT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD0BCFF))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = email.summary,
                            fontSize = 12.sp,
                            color = Color(0xFFE6E1E5),
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Body
                Text(
                    text = "ORIGINAL EMAIL CONTENT:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF938F99)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                        .background(Color(0xFF1D1B20).copy(alpha = 0.4f))
                        .padding(12.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = email.body,
                                fontSize = 12.sp,
                                color = Color(0xFFCAC4D0),
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD0BCFF)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Close Reader", fontSize = 12.sp)
                    }

                    if (email.needsReminder) {
                        Button(
                            onClick = onScheduleReminder,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF), contentColor = Color(0xFF381E72)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                                .testTag("email_details_schedule_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Schedule Task", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialog Component for Scheduling Custom Reminders
@Composable
fun ReminderSchedulerDialog(
    suggestedTitle: String,
    suggestedDelayMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, epochMs: Long, syncToCalendar: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(suggestedTitle) }
    var notes by remember { mutableStateOf("") }
    var delayMinutes by remember { mutableStateOf(suggestedDelayMinutes.toFloat()) }
    var syncToCalendar by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            border = BorderStroke(1.dp, Color(0xFF49454F)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("dialog_reminder_scheduler")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Schedule alert task",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close planner", tint = Color(0xFFE6E1E5))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title field input
                Text("Task Title", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF938F99))
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("e.g. Schedule meeting database follow-up...", fontSize = 12.sp, color = Color(0xFF938F99)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFFE6E1E5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reminder_title"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F),
                        focusedContainerColor = Color(0xFF1D1B20),
                        unfocusedContainerColor = Color(0xFF1D1B20)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notes / Description input
                Text("Notes / Instructions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF938F99))
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Add instructions or email reference notes...", fontSize = 12.sp, color = Color(0xFF938F99)) },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFFE6E1E5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag("input_reminder_notes"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD0BCFF),
                        unfocusedBorderColor = Color(0xFF49454F),
                        focusedContainerColor = Color(0xFF1D1B20),
                        unfocusedContainerColor = Color(0xFF1D1B20)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Delay Slider
                val finalMinutes = delayMinutes.toInt()
                val sliderLabel = when {
                    finalMinutes < 60 -> "In $finalMinutes minutes"
                    finalMinutes < 1440 -> "In ${finalMinutes / 60} hour(s)"
                    else -> "In ${finalMinutes / 1440} day(s)"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Trigger Delta", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF938F99))
                    Text(
                        text = sliderLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF)
                    )
                }

                Slider(
                    value = delayMinutes,
                    onValueChange = { delayMinutes = it },
                    valueRange = 10f..4320f, // 10 minutes to 3 days
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD0BCFF),
                        activeTrackColor = Color(0xFFD0BCFF),
                        inactiveTrackColor = Color(0xFF49454F)
                    ),
                    modifier = Modifier.testTag("slider_reminder_delay")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Sync Google calendar events checkbox toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = syncToCalendar,
                        onCheckedChange = { syncToCalendar = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFD0BCFF),
                            uncheckedColor = Color(0xFF938F99),
                            checkmarkColor = Color(0xFF381E72)
                        ),
                        modifier = Modifier.testTag("checkbox_sync_google_calendar")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Sync to Google Calendar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6E1E5)
                        )
                        Text(
                            text = "Inserts a live event block on primary timeline",
                            fontSize = 9.sp,
                            color = Color(0xFF938F99)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD0BCFF)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val epochMs = System.currentTimeMillis() + (finalMinutes * 60000L)
                            onSave(title, notes, epochMs, syncToCalendar)
                        },
                        enabled = title.trim().isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .testTag("button_save_reminder")
                    ) {
                        Text("Save Task", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
