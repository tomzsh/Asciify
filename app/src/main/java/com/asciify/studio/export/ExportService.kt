@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.asciify.studio.export

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.media3.transformer.ExportException
import com.asciify.studio.*
import com.asciify.studio.data.*
import com.asciify.studio.engine.*
import com.asciify.studio.model.*
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null
    private val repository get() = (application as AsciifyApplication).exports
    private val notifications get() = getSystemService(NotificationManager::class.java)
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Video exports", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) {
            work?.cancel()
            if (work == null) stopSelf()
            return START_NOT_STICKY
        }
        if (work?.isActive == true) return START_NOT_STICKY
        startForeground(NOTIFICATION, notification("Preparing your video", 0))
        val request = runCatching { ExportRequest.parse(requireNotNull(intent?.getStringExtra(REQUEST))) }.getOrElse {
            repository.update(ExportState(error = "The export settings could not be restored. Please start again."))
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        work = scope.launch { render(request) }
        return START_NOT_STICKY
    }

    private suspend fun render(request: ExportRequest) {
        var temporary: File? = null
        var destination: Uri? = null
        var completed: ExportRecord? = null
        var finalState = ExportState()
        val wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Asciify:Export")
        try {
            wakeLock.acquire(6 * 60 * 60 * 1000L)
            val plan = ExportPlan.create(request.video, request.settings, request.startMs, request.endMs)
            val file = File.createTempFile("asciify-", ".mp4", cacheDir)
            temporary = file; check(file.delete())
            var processor = if (request.settings.mode == ProcessingMode.CPU) "CPU" else "GPU"
            var lastNotification = -5
            fun progress(value: Int) {
                val percent = (value * 0.97).toInt().coerceIn(0, 97)
                val stage = if (processor == "GPU") "Rendering with GPU" else "Rendering with CPU"
                repository.progress(percent, stage)
                if (percent - lastNotification >= 2) {
                    lastNotification = percent
                    runCatching { notifications.notify(NOTIFICATION, notification(stage, percent)) }
                }
            }
            repository.update(ExportState(running = true, stage = "Preparing $processor renderer"))
            if (processor == "CPU") {
                CpuExporter.export(this, request.video, request.ascii, plan, file, ::progress)
            } else {
                try {
                    GpuExporter.export(this, request.video, request.ascii, plan, file, ::progress)
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    // Input/network/destination/muxer errors are not solved by CPU rendering.
                    val code = (error as? ExportException)?.errorCode ?: 0
                    val isRenderFailure = code in 3000..5999
                    if (request.settings.mode != ProcessingMode.AUTO || !isRenderFailure) throw error
                    currentCoroutineContext().ensureActive()
                    file.delete(); processor = "CPU"; lastNotification = -5
                    repository.progress(0, "GPU unavailable · switching to CPU")
                    CpuExporter.export(this, request.video, request.ascii, plan, file, ::progress)
                }
            }
            currentCoroutineContext().ensureActive()
            check(file.length() > 0) { "The encoder did not produce a video." }
            repository.progress(98, "Saving to Movies / Asciify")
            val name = "Asciify_${request.ascii.styleId}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
            val uri = withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Asciify")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val inserted = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Android could not create the output file. Free up storage and retry.")
                destination = inserted
                requireNotNull(contentResolver.openOutputStream(inserted)).use { stream ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            stream.write(buffer, 0, count)
                        }
                    }
                }
                ensureActive()
                contentResolver.update(inserted, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                inserted
            }
            completed = ExportRecord(uri.toString(), name, Styles.find(request.ascii.styleId).name,
                plan.width, plan.height, plan.fps, file.length(), System.currentTimeMillis(), processor)
            destination = null // Published; the history is committed after worker cleanup.
        } catch (cancel: CancellationException) {
            finalState = ExportState(stage = "Export canceled. Your source video is unchanged.")
        } catch (error: Exception) {
            val detail = error.localizedMessage ?: "An unknown encoder error occurred."
            finalState = ExportState(error = "$detail Try a lower resolution, or select CPU processing in export settings.")
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                destination?.let { runCatching { contentResolver.delete(it, null, null) } }
                temporary?.delete()
            }
            if (wakeLock.isHeld) wakeLock.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            // Keep the UI busy until cleanup finishes, so an immediate retry cannot be dropped.
            completed?.let(repository::completed) ?: repository.update(finalState)
        }
    }

    private fun notification(message: String, progress: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 1, Intent(this, ExportService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_export).setContentTitle("Asciify · exporting")
            .setContentText(message).setProgress(100, progress, false).setOngoing(true).setContentIntent(open)
            .addAction(0, "Cancel", cancel).build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) { work?.cancel(); stopSelf() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val REQUEST = "request"
        const val CANCEL = "com.asciify.CANCEL_EXPORT"
        private const val CHANNEL = "asciify_exports"
        private const val NOTIFICATION = 10
    }
}
