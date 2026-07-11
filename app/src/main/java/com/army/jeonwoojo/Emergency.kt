package com.army.jeonwoojo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog

/**
 * 긴급 도움 연결.
 * 목록에서 선택하면 전화 다이얼러가 번호를 채운 채 열린다.
 * (ACTION_DIAL 은 전화 권한이 필요 없어 안전하다.)
 */
object Emergency {

    // ★ 부대 성고충전문상담관 직통번호로 변경하세요
    private const val COUNSELOR_PHONE = "000-0000-0000"

    // 표시이름 to 실제 전화번호
    private val contacts: List<Pair<String, String>> = listOf(
        "국방헬프콜 (24시간)   ☎ 1303" to "1303",
        "여성긴급전화       ☎ 1366" to "1366",
        "경찰 신고         ☎ 112" to "112",
        "성고충전문상담관    ☎ $COUNSELOR_PHONE" to COUNSELOR_PHONE
    )

    fun showDialog(context: Context) {
        val labels = contacts.map { it.first }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("긴급 도움 연결")
            .setItems(labels) { _, which ->
                val number = contacts[which].second
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                )
            }
            .setNegativeButton("닫기", null)
            .show()
    }
}
