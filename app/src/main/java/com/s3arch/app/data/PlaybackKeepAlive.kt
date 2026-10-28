package com.s3arch.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.s3arch.app.R
import com.s3arch.app.util.AppLog

/**
 * Garde le processus vivant pendant une lecture externe (VLC).
 *
 * Le proxy HTTP local tourne dans le processus de S3ARCH. Quand VLC passe au premier
 * plan, S3ARCH devient une application en arriere-plan et Android peut geler ce
 * processus : le socket reste ouvert mais plus aucun octet n'est envoye, et VLC tourne
 * alors indefiniment sur son indicateur de chargement. Un service de premier plan, du
 * type `mediaPlayback`, empeche ce gel le temps de la lecture.
 */
class PlaybackKeepAlive : Service() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Filet de securite : le service ne doit pas rester indefiniment si VLC n'est
        // jamais revenu au premier plan.
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, MAX_LIFETIME_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lecture vidéo",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Maintient le flux local ouvert pendant la lecture dans VLC."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("Lecture en cours")
            .setContentText("Le flux local reste disponible pour votre lecteur.")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL_ID = "s3arch-playback"
        private const val NOTIFICATION_ID = 42
        private const val MAX_LIFETIME_MS = 4 * 60 * 60 * 1000L

        /** Demarre le service sans faire planter l'application s'il est refuse. */
        fun start(context: Context) {
            val intent = Intent(context, PlaybackKeepAlive::class.java)
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                AppLog.warn("Lecture", "Service de lecture indisponible : ${it.message}")
            }
        }
    }
}
