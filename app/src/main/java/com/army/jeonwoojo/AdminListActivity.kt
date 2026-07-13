package com.army.jeonwoojo

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 관리자 전용: 현재 부대의 접수 목록 (서버에서 불러옴).
 * - 입장 시 미확인 민원 건수 알림
 * - 미확인 건은 🔴 NEW 표시, 열람 시 "확인 완료" 처리
 * - 비밀번호 변경 (기존 확인 → 새 비밀번호 → 확인, 모든 기기 적용)
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

        findViewById<Button>(R.id.btnChangePw).setOnClickListener { showChangePasswordDialog() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    // ==================== 목록 ====================

    private fun refreshList() {
        tvEmpty.text = "불러오는 중..."
        tvEmpty.visibility = TextView.VISIBLE
        llList.removeAllViews()

        RemoteStore.listForUnit(unit) { submissions, error ->
            if (error != null || submissions == null) {
                tvEmpty.text = "목록을 불러오지 못했습니다.\n$error"
                return@listForUnit
            }
            renderList(submissions)

            // 새로 접수된 민원 알림 (입장 시 1회)
            if (!alertShown) {
                alertShown = true
                val unconfirmed = submissions.count { it.status == RemoteStore.STATUS_SUBMITTED }
                if (unconfirmed > 0) {
                    AlertDialog.Builder(this)
                        .setTitle("🔔 새 민원 알림")
                        .setMessage("확인하지 않은 민원이 ${unconfirmed}건 있습니다.\n항목을 열람하면 '확인 완료'로 처리되고, 제출자가 접수번호로 확인 여부를 조회할 수 있습니다.")
                        .setPositiveButton("확인", null)
                        .show()
                }
            }
        }
    }

    private fun renderList(submissions: List<RemoteStore.Submission>) {
        llList.removeAllViews()
        if (submissions.isEmpty()) {
            tvEmpty.text = "접수된 신고·상담이 없습니다."
            tvEmpty.visibility = TextView.VISIBLE
            return
        }
        tvEmpty.visibility = TextView.GONE

        val inflater = LayoutInflater.from(this)
        submissions.forEach { s ->
            val row = inflater.inflate(R.layout.item_submission, llList, false)
            val tvType = row.findViewById<TextView>(R.id.tvType)
            tvType.text = s.typeLabel
            tvType.setBackgroundColor(
                when (s.type) {
                    "report" -> 0xFFD32F2F.toInt()
                    "counsel" -> 0xFF1976D2.toInt()
                    else -> 0xFF2E7D32.toInt()
                }
            )
            val newTag = if (s.status == RemoteStore.STATUS_SUBMITTED) "🔴 NEW  " else ""
            val attTag = if (s.attachmentNames.isNotEmpty()) "  📎${s.attachmentNames.size}" else ""
            row.findViewById<TextView>(R.id.tvTitle).text = s.title
            row.findViewById<TextView>(R.id.tvDate).text = newTag + s.dateLabel + attTag
            row.setOnClickListener { showDetail(s) }
            llList.addView(row)
        }
    }

    // ==================== 상세 ====================

    private fun showDetail(s: RemoteStore.Submission) {
        // 열람과 동시에 확인 완료 처리 (서버 반영)
        if (s.status == RemoteStore.STATUS_SUBMITTED) {
            RemoteStore.confirm(s.id) { _, _ -> }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        container.addView(TextView(this).apply {
            text = "접수번호: ${s.receiptCode}  ·  ${s.dateLabel}\n"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
        })

        container.addView(TextView(this).apply {
            text = s.content
            textSize = 15f
            setTextColor(0xFF212121.toInt())
        })

        if (s.attachmentNames.isNotEmpty()) {
            container.addView(TextView(this).apply {
                text = "\n📎 첨부파일 (${s.attachmentNames.size}) — 누르면 열립니다"
                textSize = 14f
                setTextColor(0xFF555555.toInt())
            })
            s.attachmentNames.forEachIndexed { index, name ->
                container.addView(TextView(this).apply {
                    text = "• $name"
                    textSize = 14f
                    setTextColor(0xFF1976D2.toInt())
                    setPadding(0, 8, 0, 8)
                    setOnClickListener { openAttachment(s.id, index, name) }
                })
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

    private fun openAttachment(submissionId: String, index: Int, displayName: String) {
        val progress = AlertDialog.Builder(this).setMessage("첨부파일 여는 중...").setCancelable(false).create()
        progress.show()
        RemoteStore.fetchAttachment(submissionId, index) { name, bytes, error ->
            progress.dismiss()
            if (error != null || bytes == null) {
                Toast.makeText(this, error ?: "첨부파일을 열 수 없습니다.", Toast.LENGTH_LONG).show()
                return@fetchAttachment
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                val iv = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    setPadding(16, 16, 16, 16)
                }
                AlertDialog.Builder(this)
                    .setTitle(name ?: displayName)
                    .setView(iv)
                    .setPositiveButton("닫기", null)
                    .show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(name ?: displayName)
                    .setMessage("이미지가 아닌 파일은 앱 내 미리보기가 지원되지 않습니다.")
                    .setPositiveButton("확인", null)
                    .show()
            }
        }
    }

    private fun confirmDelete(s: RemoteStore.Submission) {
        AlertDialog.Builder(this)
            .setTitle("처리 완료")
            .setMessage("이 접수 건을 삭제할까요?\n삭제하면 복구할 수 없습니다.\n(삭제 후에는 제출자가 접수번호로 조회 시 '처리 완료'로 안내됩니다)")
            .setPositiveButton("삭제") { _, _ ->
                val progress = AlertDialog.Builder(this).setMessage("삭제 중...").setCancelable(false).create()
                progress.show()
                RemoteStore.delete(s) { ok, error ->
                    progress.dismiss()
                    if (!ok) Toast.makeText(this, error ?: "삭제 실패", Toast.LENGTH_LONG).show()
                    refreshList()
                }
            }
            .setNegativeButton("취소") { _, _ -> refreshList() }
            .show()
    }

    // ==================== 비밀번호 변경 ====================

    private fun showChangePasswordDialog() {
        fun pwField(hintText: String) = EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etOld = pwField("기존 비밀번호")
        val etNew = pwField("새로운 비밀번호 (4자 이상)")
        val etConfirm = pwField("새로운 비밀번호 확인")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(etOld)
            addView(etNew)
            addView(etConfirm)
        }

        AlertDialog.Builder(this)
            .setTitle("$unit 관리자 비밀번호 변경")
            .setView(container)
            .setPositiveButton("변경") { _, _ ->
                val old = etOld.text.toString()
                val new = etNew.text.toString()
                val confirm = etConfirm.text.toString()
                when {
                    old.isEmpty() || new.isEmpty() || confirm.isEmpty() ->
                        Toast.makeText(this, "모든 칸을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    new.length < 4 ->
                        Toast.makeText(this, "새 비밀번호는 4자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                    new != confirm ->
                        Toast.makeText(this, "새 비밀번호가 서로 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                    else -> performPasswordChange(old, new)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performPasswordChange(old: String, new: String) {
        val progress = AlertDialog.Builder(this).setMessage("변경 중...").setCancelable(false).create()
        progress.show()
        // 1) 기존 비밀번호 확인
        RemoteStore.getAdminPassword(unit) { current, error ->
            if (error != null || current == null) {
                progress.dismiss()
                Toast.makeText(this, error ?: "서버 연결 실패", Toast.LENGTH_LONG).show()
                return@getAdminPassword
            }
            if (old != current) {
                progress.dismiss()
                Toast.makeText(this, "기존 비밀번호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                return@getAdminPassword
            }
            // 2) 새 비밀번호로 변경 (모든 기기에 적용)
            RemoteStore.changeAdminPassword(unit, new) { ok, err2 ->
                progress.dismiss()
                if (ok) {
                    AlertDialog.Builder(this)
                        .setTitle("변경 완료")
                        .setMessage("비밀번호가 변경되었습니다.\n지금부터 모든 기기에서 새 비밀번호로 로그인해야 합니다.")
                        .setPositiveButton("확인", null)
                        .show()
                } else {
                    Toast.makeText(this, err2 ?: "변경 실패", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
