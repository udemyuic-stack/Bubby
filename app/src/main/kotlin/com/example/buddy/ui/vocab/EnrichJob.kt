package com.example.buddy.ui.vocab

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log

class EnrichJob : JobService() {
    @Volatile private var stopped = false

    override fun onStartJob(params: JobParameters): Boolean {
        stopped = false
        val app = applicationContext
        Thread {
            try {
                for (id in VocabDb.due(app, System.currentTimeMillis())) {
                    if (stopped) break
                    if (VocabEnrich.runOnce(app, id, force = true, language = null, manual = false) == EnrichOutcome.UNAVAILABLE) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "enrich job failed", e)
            } finally {
                if (!stopped) {
                    jobFinished(params, false)
                    schedule(app)
                }
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped = true
        return true
    }

    companion object {
        private const val TAG = "EnrichJob"
        const val JOB_ID = 7301

        fun schedule(ctx: Context) {
            val app = ctx.applicationContext
            val js = app.getSystemService(JobScheduler::class.java) ?: return
            try {
                val due = VocabDb.nextDue(app)
                if (due == null) { js.cancel(JOB_ID); return }
                val info = JobInfo.Builder(JOB_ID, ComponentName(app, EnrichJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(maxOf(0L, due - System.currentTimeMillis()))
                    .setPersisted(true)
                    .build()
                js.schedule(info)
            } catch (e: Exception) {
                Log.w(TAG, "schedule failed", e)
            }
        }
    }
}
