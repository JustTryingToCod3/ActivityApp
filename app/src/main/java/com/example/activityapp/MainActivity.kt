package com.example.activityapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.activityapp.ui.theme.ActivityAppTheme

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ActivityAppTheme {
                ActivityReminderScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityReminderScreen() {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(ActivityMonitorService.isRunning) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    
    var hasActivityPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else true

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasActivityPermission = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: hasActivityPermission
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Activity Reminder", 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Bold 
                    ) 
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            // Permission Requests
            if ((!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) || 
                (!hasActivityPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
                
                Button(
                    onClick = { 
                        val perms = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
                        permissionLauncher.launch(perms.toTypedArray())
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                ) {
                    Text("Enable Permissions", fontSize = 18.sp)
                }
            }

            if (!isIgnoringBatteryOptimizations && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text("Allow Background Running", fontSize = 16.sp)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isServiceRunning) "Monitoring Active" else "Monitoring Stopped",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, ActivityMonitorService::class.java)
                            if (isServiceRunning) {
                                context.stopService(intent)
                                isServiceRunning = false
                            } else {
                                ContextCompat.startForegroundService(context, intent)
                                isServiceRunning = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServiceRunning) Color(0xFFC62828) else Color(0xFF2E7D32)
                        )
                    ) {
                        Text(
                            if (isServiceRunning) "STOP MONITORING" else "START MONITORING",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                "Activity Tips",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                item { TipCard("Stay Hydrated", "Drink water every 2 hours.") }
                item { TipCard("Move Around", "Stretch if you've been sitting for 60 minutes.") }
                item { TipCard("Rest Well", "Take a break after long activities.") }
                item { TipCard("Safety First", "If you feel unsteady, sit down immediately.") }
            }
        }
    }
}

@Composable
fun TipCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 16.sp)
        }
    }
}
