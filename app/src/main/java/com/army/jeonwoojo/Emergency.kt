package com.army.jeonwoojo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * 긴급 도움 연결.
 * - 목록에서 선택하면 전화 다이얼러가 번호를 채운 채 열린다 (ACTION_DIAL: 권한 불필요)
 * - 부대 상담관 번호는 관리자가 앱에서 등록/변경하며 서버에 저장된다.
 *   긴급 화면은 즉시 떠야 하므로: 기기에 캐시된 번호를 바로 보여주고,
 *   백그라운드로 서버 최신 번호를 받아 캐시를 갱신한다.
 */
object Emergency {

    private const val PREFS = "emergency_prefs"

    fun cachedCounselorPhone(context: Context, unit: String): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("counselor_$unit", "") ?: ""

    fun setCachedCounselorPhone(context: Context, unit: String, phone: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("counselor_$unit", phone).apply()
    }

    fun showDialog(context: Context) {
        val unit = Units.selectedUnit(context)

        // 백그라운드로 서버 최신 번호 캐시 갱신 (다음에 열 때 반영)
        RemoteStore.getCounselorPhone(unit) { phone, _ ->
            if (phone != null) setCachedCounselorPhone(context, unit, phone)
        }

        val counselor = cachedCounselorPhone(context, unit)
        val counselorLabel =
            if (counselor.isEmpty()) "성고충전문상담관 ($unit)   (번호 미등록)"
            else "성고충전문상담관 ($unit)   ☎ $counselor"

        val contacts = listOf(
            "국방헬프콜 (24시간)   ☎ 1303" to "1303",
            "여성긴급전화       ☎ 1366" to "1366",
            "경찰 신고         ☎ 112" to "112",
            counselorLabel to counselor
        )

        val labels = contacts.map { it.first }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("긴급 도움 연결")
            .setItems(labels) { _, which ->
                val number = contacts[which].second
                if (number.isEmpty()) {
                    Toast.makeText(context,
                        "아직 상담관 번호가 등록되지 않았습니다.\n관리자 화면에서 등록할 수 있습니다.",
                        Toast.LENGTH_LONG).show()
                } else {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    )
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }
}
