package com.s3arch.app.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.s3arch.app.MainActivity
import com.s3arch.app.R
import com.s3arch.app.util.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Service de premier plan des telechargements sur telephone.
 *
 * Sans lui, Android gele le processus des que l'ecran se verrouille : les sockets
 * restent ouvertes mais plus rien n'avance. La notification affiche la progression
 * globale et permet de revenir dans l'application.
 */
class PhoneDownloadsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collector: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val initial = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Préparation du téléchargement…")
            .setOngoing(true)
            .setSilent(true)
            .build()
        startInForeground(initial)

        collector = scope.launch {
            PhoneEngine.get(this@PhoneDownloadsService).downloads.collectLatest { items ->
                val active = items.filter { it.isActive }
                if (active.isEmpty()) {
                    stopSelf()
                    return@collectLatest
                }
                notify(active)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            PhoneEngine.get(this).downloads.value
                .filter { it.isActive }
                .forEach { PhoneEngine.get(this).pause(it.id) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (collector == null) onCreate()
        return START_STICKY
    }

    override fun onDestroy() {
        collector?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Téléchargements sur le téléphone",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progression des torrents téléchargés directement sur l'appareil"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(active: List<PhoneDownload>) {
        val totalBytes = active.sumOf { it.totalBytes }
        val doneBytes = active.sumOf { it.doneBytes }
        val rate = active.sumOf { it.rateBps }
        val seeding = active.count { it.state == PhoneDownload.STATE_SEEDING }
        val uploading = active.any { it.state != PhoneDownload.STATE_SEEDING }
        val uploaded = active.sumOf { it.uploadedBytes }
        val progress = if (totalBytes > 0) ((doneBytes * 100) / totalBytes).toInt() else 0

        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(
                when {
                    !uploading && seeding > 0 -> "Partage en cours"
                    active.size == 1 -> active.first().name
                    else -> "${active.size} téléchargements"
                },
            )
            .setContentText(
                buildString {
                    if (uploading) {
                        append(formatBytes(doneBytes))
                        if (totalBytes > 0) append(" / ").append(formatBytes(totalBytes))
                        if (rate > 0) append(" · ").append(formatBytes(rate)).append("/s")
                    }
                    if (seeding > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("partage ")
                        if (uploaded > 0) append(formatBytes(uploaded)).append(" envoyés")
                    }
                },
            )
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, totalBytes <= 0L || !uploading)

        if (active.size > 1) {
            val style = NotificationCompat.InboxStyle()
            active.take(4).forEach { item ->
                style.addLine(
                    "${(item.progress * 100).toInt()} % · ${item.name.take(48)}",
                )
            }
            builder.setStyle(style)
        }

        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            manager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    companion object {
        private const val CHANNEL_ID = "s3arch_phone_downloads"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "com.s3arch.app.STOP_PHONE_DOWNLOADS"

        fun start(context: Context) {
            val intent = Intent(context, PhoneDownloadsService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhoneDownloadsService::class.java))
        }
    }
}
