package com.army.jeonwoojo

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * 관리자 전용: 현재 부대의 접수된 신고·상담·건의 목록.
 * - 입장 시 미확인 민원 건수 알림
 * - 미확인 건은 🔴 NEW 표시
 * - 항목 클릭 → 상세 열람과 동시에 "확인 완료" 처리
 *   (제출자는 접수번호로 확인 여부를 조회할 수 있게 됨)
 * - "처리 완료(삭제)" 시 해당 접수 삭제
 */
class AdminListActivity : AppCompatActivity() {

    private lateinit var llList: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var unit: String
    private var alertShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        unit = Units.selectedUnit(this)
        title = "$unit 접수 내역 (관리자)"

        llList = findViewById(R.id.llSubmissionList)
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    override fun onResume() {
        super.onResume()
        refreshList()

        // 새로 접수된 민원 알림 (입장 시 1회)
        if (!alertShown) {
            alertShown = true
            val unconfirmed = SubmissionStore.countUnconfirmed(this, unit)
            if (unconfirmed > 0) {
                AlertDialog.Builder(this)
                    .setTitle("🔔 새 민원 알림")
                    .setMessage("확인하지 않은 민원이 ${unconfirmed}건 있습니다.\n항목을 열람하면 '확인 완료'로 처리되고, 제출자가 접수번호로 확인 여부를 조회할 수 있습니다.")
                    .setPositiveButton("확인", null)
                    .show()
            }
        }
    }

    private fun refreshList() {
        llList.removeAllViews()
        val submissions = SubmissionStore.getForUnit(this, unit)

        tvEmpty.visibility = if (submissions.isEmpty()) TextView.VISIBLE else TextView.GONE

        val inflater = LayoutInflater.from(this)
        submissions.forEach { s ->
            val row = inflater.inflate(R.layout.item_submission, llList, false)
            val tvType = row.findViewById<TextView>(R.id.tvType)
            tvType.text = s.typeLabel
            tvType.setBackgroundColor(
                when (s.type) {
                    "report" -> 0xFFD32F2F.toInt()      // 빨강
                    "counsel" -> 0xFF1976D2.toInt()     // 파랑
                    else -> 0xFF2E7D32.toInt()          // 초록 (건의)
                }
            )
            val newTag = if (s.status == SubmissionStore.STATUS_SUBMITTED) "🔴 NEW  " else ""
            val attTag = if (s.attachmentPaths.isNotEmpty()) "  📎${s.attachmentPaths.size}" else ""
            row.findViewById<TextView>(R.id.tvTitle).text = s.title
            row.findViewById<TextView>(R.id.tvDate).text = newTag + s.dateLabel + attTag
            row.setOnClickListener { showDetail(s) }
            llList.addView(row)
        }
    }

    private fun showDetail(s: SubmissionStore.Submission) {
        // 열람과 동시에 확인 완료 처리 → 제출자가 접수번호로 조회 가능
        if (s.status == SubmissionStore.STATUS_SUBMITTED) {
            SubmissionStore.confirm(this, s.id)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        // 접수 정보
        container.addView(TextView(this).apply {
            text = "접수번호: ${s.receiptCode}  ·  ${s.dateLabel}\n"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
        })

        // 본문
        container.addView(TextView(this).apply {
            text = s.content
            textSize = 15f
            setTextColor(0xFF212121.toInt())
        })

        // 첨부파일
        if (s.attachmentPaths.isNotEmpty()) {
            container.addView(TextView(this).apply {
                text = "\n📎 첨부파일 (${s.attachmentPaths.size})"
                textSize = 14f
                setTextColor(0xFF555555.toInt())
            })
            s.attachmentPaths.forEach { path ->
                val file = File(path)
                val displayName = file.name.substringAfter("_")
                val tv = TextView(this).apply {
                    text = "• $displayName"
                    textSize = 14f
                    setTextColor(0xFF1976D2.toInt())
                    setPadding(0, 8, 0, 8)
                    setOnClickListener { openAttachment(file, displayName) }
                }
                container.addView(tv)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("[${s.typeLabel}] ${s.title}")
            .setView(android.widget.ScrollView(this).apply { addView(container) })
            .setPositiveButton("닫기") { _, _ -> refreshList() }
            .setNegativeButton("처리 완료 (삭제)") { _, _ -> confirmDelete(s) }
            .setOnCancelListener { refreshList() }
            .show()
    }

    /** 이미지면 미리보기, 아니면 안내 */
    private fun openAttachment(file: File, displayName: String) {
        if (!file.exists()) {
            AlertDialog.Builder(this).setMessage("파일을 찾을 수 없습니다.").setPositiveButton("확인", null).show()
            return
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap != null) {
            val iv = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                setPadding(16, 16, 16, 16)
            }
            AlertDialog.Builder(this)
                .setTitle(displayName)
                .setView(iv)
                .setPositiveButton("닫기", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(displayName)
                .setMessage("이미지가 아닌 파일은 앱 내 미리보기가 지원되지 않습니다.\n저장 위치: ${file.absolutePath}")
                .setPositiveButton("확인", null)
                .show()
        }
    }

    private fun confirmDelete(s: SubmissionStore.Submission) {
        AlertDialog.Builder(this)
            .setTitle("처리 완료")
            .setMessage("이 접수 건을 삭제할까요?\n삭제하면 복구할 수 없습니다.\n(삭제 후에는 제출자가 접수번호로 조회 시 '처리 완료'로 안내됩니다)")
            .setPositiveButton("삭제") { _, _ ->
                SubmissionStore.delete(this, s.id)
                refreshList()
            }
            .setNegativeButton("취소") { _, _ -> refreshList() }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
