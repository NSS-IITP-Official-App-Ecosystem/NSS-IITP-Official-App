package com.phad.chatapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phad.chatapp.utils.FcmSender

/**
 * WorkManager worker that sends a 1-hour-before reminder for mandatory events.
 * Scheduled at event creation time with a computed delay.
 */
class EventReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: return Result.failure()
        val body = inputData.getString("body") ?: return Result.failure()
        val topicsJson = inputData.getString("topics") ?: return Result.failure()

        // topics stored as comma-separated list
        val topics = topicsJson.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        Log.d("EventReminderWorker", "Firing 1-hr reminder: $title → topics=$topics")
        topics.forEach { topic ->
            FcmSender.sendToTopic(topic = topic, title = title, body = body)
        }
        return Result.success()
    }
}
