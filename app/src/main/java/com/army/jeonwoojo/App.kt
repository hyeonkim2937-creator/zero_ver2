package com.army.jeonwoojo

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 앱 전체 생명주기 관리.
 *
 * 화면(액티비티)이 몇 개 켜져 있는지 세어서
 * - 0 → 1 : 앱이 포그라운드로 왔다 → BGM 시작
 * - 1 → 0 : 앱이 백그라운드로 갔다 → BGM 정지
 *
 * 화면 간 이동은 (다음 화면 start → 이전 화면 stop 순서라)
 * 카운트가 0으로 떨어지지 않으므로 BGM이 끊기지 않는다.
 */
class App : Application() {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()

        // 전송 대기 중인 민원이 있으면 네트워크 연결 시 자동 전송 예약
        if (PendingStore.count(this) > 0) PendingWorker.schedule(this)
        // 관리자 알림 대상 부대가 있으면 주기 확인 유지
        if (AdminNotify.registeredUnits(this).isNotEmpty()) AdminNotify.schedule(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) BgmPlayer.start(this@App)
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0) BgmPlayer.stop()
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
