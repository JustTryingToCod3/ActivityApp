package com.example.activityapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.activityapp.data.ActivityEvent
import com.example.activityapp.data.AppDatabase
import kotlinx.coroutines.*
import kotlin.math.sqrt

class ActivityMonitorService : Service(), SensorEventListener {

    companion object {
        var isRunning = false
        private const val MOVEMENT_THRESHOLD = 2.0 // Increased from 1.0 to reduce sensitivity
        private const val SUSTAINED_MOVEMENT_REQUIRED = 5 // Readings needed to confirm real activity
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var lastMovementTime = System.currentTimeMillis()
    private var lastRestTime = System.currentTimeMillis()
    private var isActive = false
    private var sustainedMovementCount = 0
    
    private val CHANNEL_ID = "ActivityMonitorChannel"
    private val NOTIFICATION_ID = 1

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    private var isGyroRegistered = false
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        saveServiceState(true)
        isRunning = true
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification("Monitoring activity..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Monitoring activity..."))
        }

        serviceScope.launch {
            logEvent("System", "Monitoring Started")
            registerAccelerometer()
            startMonitoringLoop()
        }
    }

    private suspend fun logEvent(type: String, description: String) {
        database.activityDao().insert(ActivityEvent(type = type, description = description))
    }

    private fun saveServiceState(running: Boolean) {
        val prefs = getSharedPreferences("activity_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_running", running).apply()
    }

    private fun registerAccelerometer() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun registerGyroscope() {
        if (!isGyroRegistered) {
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                isGyroRegistered = true
            }
        }
    }

    private fun unregisterGyroscope() {
        if (isGyroRegistered) {
            gyroscope?.let {
                sensorManager.unregisterListener(this, it)
                isGyroRegistered = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        serviceScope.launch {
            handleSensorData(event)
        }
    }

    private suspend fun handleSensorData(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val magnitude = sqrt(x * x + y * y + z * z)
                val deviation = Math.abs(magnitude - 9.8)

                if (deviation > MOVEMENT_THRESHOLD) {
                    sustainedMovementCount++
                    
                    // Only consider it "Movement" if it lasts for a few readings (approx 1 second)
                    if (sustainedMovementCount >= SUSTAINED_MOVEMENT_REQUIRED) {
                        lastMovementTime = System.currentTimeMillis()
                        if (!isActive) {
                            isActive = true
                            lastRestTime = System.currentTimeMillis()
                            logEvent("Movement", "Significant movement detected")
                            registerGyroscope()
                        }
                    }
                } else {
                    sustainedMovementCount = 0 // Reset if movement stops
                    
                    if (isActive && (System.currentTimeMillis() - lastMovementTime > 5000)) {
                        isActive = false
                        logEvent("Stationary", "User is now stationary")
                        unregisterGyroscope()
                    }
                }

                if (gyroscope == null) {
                    if (deviation > 5.0) {
                        sendAlert("Unsteady Movement Detected", "Please take care and consider sitting down.")
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val rotationMagnitude = sqrt(x * x + y * y + z * z)
                if (rotationMagnitude > 3.0) {
                    sendAlert("Unsteady Movement Detected", "Please consider sitting down and resting.")
                }
            }
        }
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (coroutineContext.isActive) {
                val now = System.currentTimeMillis()
                
                val inactiveDuration = now - lastMovementTime
                if (!isActive && inactiveDuration > 60 * 60 * 1000) { 
                    sendAlert("Time to Move!", "You've been inactive for 60 minutes. Try stretching!")
                } else if (!isActive && inactiveDuration > 2 * 60 * 60 * 1000) {
                    sendAlert("Hydration Break", "You've been sitting for a while. Have some water!")
                }

                if (isActive && (now - lastRestTime > 60 * 60 * 1000)) {
                    sendAlert("Time to Rest", "You've been active for 60 minutes. Please take a break.")
                }

                delay(60000) // Check every minute
            }
        }
    }

    private fun sendAlert(title: String, message: String) {
        serviceScope.launch {
            logEvent("Alert", "$title: $message")
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Activity Reminder")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Activity Monitor Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            logEvent("System", "Monitoring Stopped")
        }
        saveServiceState(false)
        isRunning = false
        sensorManager.unregisterListener(this)
        serviceJob.cancel()
    }
}
