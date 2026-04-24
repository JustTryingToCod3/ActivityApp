package com.example.activityapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.activityapp.data.ActivityEvent
import com.example.activityapp.data.AppDatabase
import com.example.activityapp.ui.theme.ActivityAppTheme

import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            setContent {
                ActivityAppTheme {
                    MainNavigation()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in setContent", e)
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    var currentRoute by remember { mutableStateOf("home") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentRoute == "home",
                    onClick = {
                        currentRoute = "home"
                        navController.navigate("home")
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    selected = currentRoute == "history",
                    onClick = {
                        currentRoute = "history"
                        navController.navigate("history")
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { ActivityReminderScreen() }
            composable("history") { ActivityHistoryScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityReminderScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("activity_prefs", Context.MODE_PRIVATE) }
    var isServiceRunning by remember { 
        mutableStateOf(prefs.getBoolean("is_running", ActivityMonitorService.isRunning)) 
    }
    
    DisposableEffect(context) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "is_running") {
                isServiceRunning = p.getBoolean("is_running", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

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

    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val isIgnoringBatteryOptimizations = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isPreview) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } catch (e: Exception) {
                true
            }
        } else true
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        CenterAlignedTopAppBar(
            title = { 
                Text(
                    "Activity Reminder", 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Bold 
                ) 
            }
        )

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
                        } else {
                            ContextCompat.startForegroundService(context, intent)
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

@Composable
fun ActivityHistoryScreen() {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val events by database.activityDao().getAllEvents().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Activity History", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                scope.launch { database.activityDao().clearAll() }
            }) {
                Text("Clear All")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No activity recorded yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events) { event ->
                    EventCard(event)
                }
            }
        }
    }
}

@Composable
fun EventCard(event: ActivityEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.type,
                    fontWeight = FontWeight.Bold,
                    color = when (event.type) {
                        "Movement" -> Color(0xFF2E7D32)
                        "Alert" -> Color(0xFFC62828)
                        "Stationary" -> Color(0xFF1976D2)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(text = event.description, fontSize = 14.sp)
            }
            Text(
                text = event.getFormattedTime(),
                fontSize = 12.sp,
                color = Color.Gray
            )
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
