package com.army.jeonwoojo

import android.content.Context

/**
 * 부대 관련 설정을 한 곳에서 관리.
 * - 부대 목록
 * - 현재 선택된 부대 (시작화면에서 선택, 기기에 저장)
 * - 부대별 관리자 비밀번호 (★ 배포 전 반드시 변경)
 */
object Units {

    val ALL = listOf("571대대", "572대대", "정비근무대", "장비중대")

    // ★ 부대별 관리자 비밀번호 - 배포 전 반드시 변경하세요
    private val adminPasswords = mapOf(
        "571대대" to "admin571",
        "572대대" to "admin572",
        "정비근무대" to "adminjbg",
        "장비중대" to "adminjbj"
    )

    private const val PREFS = "app_prefs"
    private const val KEY_UNIT = "selected_unit"

    fun adminPassword(unit: String): String = adminPasswords[unit] ?: "admin"

    fun setSelectedUnit(context: Context, unit: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_UNIT, unit).apply()
    }

    fun selectedUnit(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_UNIT, ALL[0]) ?: ALL[0]
}
