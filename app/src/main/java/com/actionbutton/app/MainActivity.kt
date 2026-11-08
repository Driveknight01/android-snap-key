package com.actionbutton.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.actionbutton.app.actions.ActionExecutor
import com.actionbutton.app.data.PreferencesManager
import com.actionbutton.app.model.ActionType
import com.actionbutton.app.service.ActionButtonService
import com.actionbutton.app.ui.components.AppleActionWheel
import com.actionbutton.app.ui.components.RealmeOptimizationCard
import com.actionbutton.app.ui.components.ServiceControlCard
import com.actionbutton.app.ui.components.TriggerGesturePickerSheet
import com.actionbutton.app.ui.theme.ActionButtonTheme
import com.actionbutton.app.ui.theme.Black
import com.actionbutton.app.ui.theme.TextMuted
import com.actionbutton.app.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var actionExecutor: ActionExecutor

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Thông báo giúp duy trì dịch vụ chạy nền ổn định", Toast.LENGTH_SHORT).show()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Quyền Micro cần thiết cho tính năng Ghi âm nhanh", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        actionExecutor = ActionExecutor(this)

        requestNecessaryPermissions()
        startActionButtonServiceIfNeeded()

        setContent {
            ActionButtonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Black
                ) {
                    ActionButtonScreen(
                        prefs = prefs,
                        onTestTrigger = { action ->
                            actionExecutor.execute(action)
                        },
                        onServiceToggle = { enabled ->
                            prefs.isServiceEnabled = enabled
                            if (enabled) {
                                startActionButtonService()
                            } else {
                                stopActionButtonService()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startActionButtonServiceIfNeeded() {
        if (prefs.isServiceEnabled) {
            startActionButtonService()
        }
    }

    private fun startActionButtonService() {
        val intent = Intent(this, ActionButtonService::class.java).apply {
            action = ActionButtonService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopActionButtonService() {
        val intent = Intent(this, ActionButtonService::class.java).apply {
            action = ActionButtonService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun ActionButtonScreen(
    prefs: PreferencesManager,
    onTestTrigger: (ActionType) -> Unit,
    onServiceToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val nm = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager }

    val actions = remember { ActionType.entries.filter { it != ActionType.NONE } }
    val initialIndex = remember {
        val idx = actions.indexOf(prefs.selectedAction)
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { actions.size }
    )

    var currentAction by remember { mutableStateOf(actions[initialIndex]) }
    var isServiceEnabled by remember { mutableStateOf(prefs.isServiceEnabled) }
    var hapticEnabled by remember { mutableStateOf(prefs.hapticFeedbackEnabled) }
    var onlyScreenOff by remember { mutableStateOf(prefs.onlyWhenScreenOff) }
    var triggerGesture by remember { mutableStateOf(prefs.triggerGesture) }

    var showGesturePicker by remember { mutableStateOf(false) }

    val isDndGranted = remember(currentAction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm?.isNotificationPolicyAccessGranted ?: true
        } else {
            true
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Action Button",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        AppleActionWheel(
            pagerState = pagerState,
            actions = actions,
            isDndPermissionGranted = isDndGranted,
            onRequestDndPermission = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            },
            onActionSelected = { selected ->
                currentAction = selected
                prefs.selectedAction = selected
            }
        )

        Spacer(modifier = Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            ServiceControlCard(
                isServiceEnabled = isServiceEnabled,
                onServiceToggle = { enabled ->
                    isServiceEnabled = enabled
                    onServiceToggle(enabled)
                },
                onlyScreenOff = onlyScreenOff,
                onOnlyScreenOffToggle = { enabled ->
                    onlyScreenOff = enabled
                    prefs.onlyWhenScreenOff = enabled
                },
                hapticEnabled = hapticEnabled,
                onHapticToggle = { enabled ->
                    hapticEnabled = enabled
                    prefs.hapticFeedbackEnabled = enabled
                },
                triggerGesture = triggerGesture,
                onSelectGestureClick = {
                    showGesturePicker = true
                },
                currentAction = currentAction,
                onTestClick = {
                    onTestTrigger(currentAction)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            RealmeOptimizationCard()
        }
    }

    if (showGesturePicker) {
        TriggerGesturePickerSheet(
            currentGesture = triggerGesture,
            onDismiss = { showGesturePicker = false },
            onGestureSelected = { gesture ->
                triggerGesture = gesture
                prefs.triggerGesture = gesture
                showGesturePicker = false
            }
        )
    }
}
