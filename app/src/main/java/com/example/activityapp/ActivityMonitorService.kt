package com.example.activityapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

class ActivityMonitorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var lastMovementTime = System.currentTimeMillis()
    private var lastRestTime = System.currentTimeMillis()
    private var isActive = false
    
    private val CHANNEL_ID = "ActivityMonitorChannel"
    private val NOTIFICATION_ID = 1

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        registerSensors()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Monitoring activity..."))
        
        startMonitoringLoop()
    }

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val magnitude = sqrt(x * x + y * y + z * z)
                // Simple threshold for movement (gravity is ~9.8)
                if (Math.abs(magnitude - 9.8) > 1.0) {
                    lastMovementTime = System.currentTimeMillis()
                    if (!isActive) {
                        isActive = true
                        lastRestTime = System.currentTimeMillis()
                    }
                } else {
                    if (isActive && (System.currentTimeMillis() - lastMovementTime > 5000)) {
                        isActive = false
                        lastMovementTime = System.currentTimeMillis()
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val rotationMagnitude = sqrt(x * x + y * y + z * z)
                // Unstable movement check (wobbly gait)
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
                
                // 1. Detect inactivity (e.g., 60 minutes)
                // For demo purposes, let's use shorter times (e.g., 1 minute for inactivity)
                val inactiveDuration = now - lastMovementTime
                if (!isActive && inactiveDuration > 60 * 60 * 1000) { // 60 minutes
                    sendAlert("Time to Move!", "You've been inactive for 60 minutes. Try stretching!")
                } else if (!isActive && inactiveDuration > 2 * 60 * 60 * 1000) { // 2 hours
                    sendAlert("Hydration Break", "You've been sitting for a while. Have some water!")
                }

                // 2. Detect prolonged activity (e.g., 60 minutes)
                if (isActive && (now - lastRestTime > 60 * 60 * 1000)) {
                    sendAlert("Time to Rest", "You've been active for 60 minutes. Please take a break.")
                }

                delay(60000) // Check every minute
            }
        }
    }

    private fun sendAlert(title: String, message: String) {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Activity Reminder")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        sensorManager.unregisterListener(this)
        serviceJob.cancel()
    }
}
