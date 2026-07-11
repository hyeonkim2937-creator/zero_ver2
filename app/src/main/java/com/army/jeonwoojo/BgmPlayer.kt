package com.army.jeonwoojo

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * 앱 전역 BGM 플레이어 (싱글톤).
 *
 * - bgm1 → (5초 쉬고) → bgm2 → (5초 쉬고) → bgm3 → (5초 쉬고) → bgm1 ... 무한 순환
 * - 액티비티가 아니라 앱에 묶여 있어서 화면을 넘겨도 음악이 끊기지 않는다.
 *   (앱이 백그라운드로 가면 App.kt 의 생명주기 카운터가 stop()을 호출)
 * - 볼륨(0~100)과 "신고·상담 화면 자동 음소거" 설정은 SharedPreferences 에 저장.
 * - ducked(임시 음소거) 상태: 신고·상담 화면에 있는 동안 소리만 0으로 낮추고,
 *   저장된 볼륨 값은 건드리지 않는다. 화면을 나가면 원래 볼륨으로 복귀.
 */
object BgmPlayer {

    private const val PREFS = "bgm_prefs"
    private const val KEY_VOLUME = "volume"                    // 0 ~ 100
    private const val KEY_MUTE_ON_SUBMIT = "mute_on_submit"    // 신고·상담 화면 자동 음소거
    private const val GAP_MS = 5_000L                          // 트랙 간 간격 5초

    private val playlist = listOf(R.raw.bgm1, R.raw.bgm2, R.raw.bgm3)

    private var mediaPlayer: MediaPlayer? = null
    private var currentIndex = 0
    private var isRunning = false
    private var isDucked = false   // 신고·상담 화면 임시 음소거 상태
    private val handler = Handler(Looper.getMainLooper())
    private var pendingNext: Runnable? = null

    // ---------- 설정 값 ----------

    /** 현재 볼륨 (0~100) */
    fun getVolume(context: Context): Int =
        prefs(context).getInt(KEY_VOLUME, 70)

    /** 볼륨 저장 + 재생 중이면 즉시 반영 */
    fun setVolume(context: Context, volume: Int) {
        prefs(context).edit().putInt(KEY_VOLUME, volume.coerceIn(0, 100)).apply()
        applyEffectiveVolume(context)
    }

    /** 신고·상담 화면 자동 음소거 여부 (기본값: 켜짐) */
    fun isMuteOnSubmit(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MUTE_ON_SUBMIT, true)

    fun setMuteOnSubmit(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MUTE_ON_SUBMIT, enabled).apply()
        // 설정을 끄는 순간 이미 음소거 중이었다면 즉시 소리 복귀
        if (!enabled && isDucked) setDucked(context, false)
    }

    // ---------- 임시 음소거 (신고·상담 화면) ----------

    /**
     * true  : 소리만 0으로 (저장된 볼륨은 유지)
     * false : 저장된 볼륨으로 복귀
     */
    fun setDucked(context: Context, ducked: Boolean) {
        isDucked = ducked
        applyEffectiveVolume(context)
    }

    /** 저장 볼륨과 음소거 상태를 종합해 실제 소리 크기 적용 */
    private fun applyEffectiveVolume(context: Context) {
        val f = if (isDucked) 0f else getVolume(context) / 100f
        mediaPlayer?.setVolume(f, f)
    }

    // ---------- 재생 제어 ----------

    /** 재생 시작 (이미 재생 중이면 무시) */
    fun start(context: Context) {
        if (isRunning) return
        isRunning = true
        playTrack(context.applicationContext, currentIndex)
    }

    /** 완전 정지 (앱이 백그라운드로 갈 때) */
    fun stop() {
        isRunning = false
        pendingNext?.let { handler.removeCallbacks(it) }
        pendingNext = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun playTrack(appContext: Context, index: Int) {
        if (!isRunning) return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(appContext, playlist[index])?.apply {
            start()
            setOnCompletionListener {
                // 트랙이 끝나면 5초 쉬었다가 다음 곡
                currentIndex = (index + 1) % playlist.size
                val next = Runnable { playTrack(appContext, currentIndex) }
                pendingNext = next
                handler.postDelayed(next, GAP_MS)
            }
        }
        applyEffectiveVolume(appContext)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
