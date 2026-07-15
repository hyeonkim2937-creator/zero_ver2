package com.army.jeonwoojo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 관리자 새 민원 알림.
 *
 * 이 기기에서 관리자 로그인을 한 적이 있는 부대를 기억해 두고,
 * 15분 주기로 서버를 확인해 "새로 접수된 미확인 민원"이 있으면
 * 시스템 알림을 띄운다. (민원 내용은 알림에 표시하지 않는다 — 프라이버시)
 *
 * ※ 실시간 푸시(FCM)는 별도 발송 서버가 필요해 현 구조(서버리스)에서는
 *   안전하게 구현할 수 없어, 주기 확인 방식을 사용한다.
 */
object AdminNotify {

    private const val PREFS = "admin_notify"
    private const val KEY_UNITS = "registered_units"
    private const val CHANNEL_ID = "admin_channel"

    /** 이 기기를 해당 부대의 관리자 알림 대상으로 등록 */
    fun register(context: Context, unit: String) {
        val set = registeredUnits(context).toMutableSet()
        set.add(unit)
        prefs(context).edit().putStringSet(KEY_UNITS, set).apply()
        schedule(context)
    }

    fun registeredUnits(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_UNITS, emptySet()) ?: emptySet()

    /** 이미 알림 보냈거나 관리자가 본 민원 ID 목록 */
    fun knownIds(context: Context, unit: String): Set<String> =
        prefs(context).getStringSet("known_$unit", emptySet()) ?: emptySet()

    fun saveKnownIds(context: Context, unit: String, ids: Set<String>) {
        prefs(context).edit().putStringSet("known_$unit", ids).apply()
    }

    /** 15분 주기 확인 예약 (이미 예약돼 있으면 유지) */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AdminNotifyWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("admin_notify", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** 시스템 알림 발송 */
    fun notifyNew(context: Context, unit: String, count: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "관리자 민원 알림", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = Intent(context, AdminLoginActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, unit.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("[$unit] 새 민원 알림")
            .setContentText("확인하지 않은 새 민원 ${count}건이 접수되었습니다.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(unit.hashCode(), notification)
        } catch (e: SecurityException) {
            // 알림 권한이 없으면 조용히 무시 (관리자 화면 진입 시 권한 요청됨)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** 15분마다 서버를 확인해 새 민원이 있으면 알림을 띄우는 워커 */
class AdminNotifyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        AdminNotify.registeredUnits(ctx).forEach { unit ->
            try {
                val subs = RemoteStore.listForUnitBlocking(unit)
                val unconfirmed = subs
                    .filter { it.status == RemoteStore.STATUS_SUBMITTED }
                    .map { it.id }.toSet()
                val known = AdminNotify.knownIds(ctx, unit)
                val fresh = unconfirmed - known
                if (fresh.isNotEmpty()) {
                    AdminNotify.notifyNew(ctx, unit, fresh.size)
                }
                AdminNotify.saveKnownIds(ctx, unit, known + unconfirmed)
            } catch (e: Exception) {
                // 네트워크 오류 등은 다음 주기에 다시 확인
            }
        }
        return Result.success()
    }
}
