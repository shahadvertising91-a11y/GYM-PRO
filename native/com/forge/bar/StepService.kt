package com.forge.bar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

/**
 * Foreground service (type "health") that owns the hardware step counter and keeps an
 * ongoing notification with a real progress bar:   "6,789 steps"  [=======>   ]  "229 kcal · goal 8,000"
 * Must be started while the app is in the foreground, after ACTIVITY_RECOGNITION has been granted.
 */
class StepService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL = "forge_bar"
        const val NOTIF_ID = 4101
        const val ACTION_STOP = "com.forge.bar.STOP"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_KPS = "kps"
        @Volatile var instance: StepService? = null
    }

    private lateinit var store: StepStore
    private lateinit var sm: SensorManager
    private var sensor: Sensor? = null
    private var steps = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val midnight = Runnable { onNewDay() }

    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { onNewDay() }   // DATE_CHANGED / TIME_SET / TIMEZONE_CHANGED
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = StepStore(this)
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        createChannel()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, dateReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopFromApp(); return START_NOT_STICKY }
        intent?.let {
            if (it.hasExtra(EXTRA_GOAL)) store.goal = it.getIntExtra(EXTRA_GOAL, store.goal)
            if (it.hasExtra(EXTRA_KPS)) store.kps = it.getFloatExtra(EXTRA_KPS, store.kps)
        }
        steps = store.todaySteps()

        // Android 12+: FGS can only be started from the foreground; Android 14+: the type is mandatory
        // and ACTIVITY_RECOGNITION must already be granted (otherwise SecurityException).
        try {
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            else
                startForeground(NOTIF_ID, n)
        } catch (e: Exception) {            // ForegroundServiceStartNotAllowedException / SecurityException
            stopSelf(); return START_NOT_STICKY
        }

        store.running = true
        sensor?.let { sm.unregisterListener(this); sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        scheduleMidnight()
        ForgeBarPlugin.emitState(true)
        ForgeBarPlugin.emitSteps(steps, store.today())
        return START_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) {
        steps = store.onCounter(e.values[0].toLong())
        refreshNotification()
        ForgeBarPlugin.emitSteps(steps, store.today())
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun onNewDay() {
        store.rollover()
        steps = store.todaySteps()
        refreshNotification()
        ForgeBarPlugin.emitSteps(steps, store.today())
        scheduleMidnight()
    }

    private fun scheduleMidnight() {
        handler.removeCallbacks(midnight)
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 2); set(Calendar.MILLISECOND, 0)
        }
        handler.postDelayed(midnight, c.timeInMillis - System.currentTimeMillis())
    }

    /** goal / kcal-per-step changed from JS: redraw the notification right away */
    fun refresh() { steps = store.todaySteps(); refreshNotification() }

    fun stopFromApp() {
        store.running = false
        try { sm.unregisterListener(this) } catch (_: Exception) {}
        handler.removeCallbacks(midnight)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        ForgeBarPlugin.emitState(false)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        try { sm.unregisterListener(this) } catch (_: Exception) {}
        try { unregisterReceiver(dateReceiver) } catch (_: Exception) {}
        handler.removeCallbacks(midnight)
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    // ---------------- notification ----------------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Step counter", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Live steps and calories"; setShowBadge(false); enableVibration(false); setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    private fun smallIcon(): Int {
        val id = resources.getIdentifier("ic_stat_forge", "drawable", packageName)
        return if (id != 0) id else applicationInfo.icon
    }

    private fun buildNotification(): Notification {
        val goal = store.goal
        val kcal = Math.round(steps * store.kps)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, StepService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(smallIcon())
            .setContentTitle(String.format(Locale.getDefault(), "%,d steps", steps))
            .setContentText(String.format(Locale.getDefault(), "%,d kcal · goal %,d", kcal, goal))
            .setProgress(goal, minOf(steps, goal.toLong()).toInt(), false)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }
}
