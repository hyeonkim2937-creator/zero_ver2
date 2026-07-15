package com.army.jeonwoojo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 전송 대기함 자동 재전송 워커.
 * "네트워크 연결됨" 조건이 충족될 때 실행되어, 대기 중인 민원을 서버에 올린다.
 * 실패하면 시스템이 점점 간격을 늘려가며 자동 재시도한다.
 */
class PendingWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val list = PendingStore.getAll(ctx)
        if (list.isEmpty()) return Result.success()

        var anyFailed = false
        list.forEach { p ->
            try {
                val prepared = ArrayList<Pair<String, ByteArray>>()
                p.attachmentPaths.forEachIndexed { i, path ->
                    val f = File(path)
                    if (f.exists()) {
                        val name = p.attachmentNames.getOrElse(i) { "첨부파일" }
                        prepared.add(Pair(name, f.readBytes()))
                    }
                }
                RemoteStore.addBlocking(p.unit, p.type, p.title, p.content, prepared, p.receiptCode)
                PendingStore.remove(ctx, p.id)
            } catch (e: Exception) {
                anyFailed = true   // 이 건은 다음 시도에서 재전송
            }
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        /** 네트워크 연결 시 1회 전송 시도 예약 (이미 예약돼 있으면 유지) */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("pending_upload", ExistingWorkPolicy.KEEP, request)
        }
    }
}
