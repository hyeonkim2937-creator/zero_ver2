package com.army.jeonwoojo

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * "성군기 위반 신고"와 "상담 신청" 두 화면이 구조가 같으므로
 * 하나의 액티비티를 모드만 바꿔서 재사용한다.
 *
 * 제출 방식: 외부 메일 앱 없이 앱 내부 저장소에 저장되며,
 * 관리자 로그인(AdminLoginActivity)을 통해서만 열람할 수 있다.
 */
class SubmitActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_REPORT = "report"
        const val MODE_COUNSEL = "counsel"
        const val MODE_SUGGESTION = "suggestion"
    }

    private lateinit var mode: String

    /** 사용자가 첨부한 증거파일들 */
    private val attachments = ArrayList<Uri>()
    private lateinit var llAttachments: LinearLayout

    // 사진·문서·음성 등 아무 파일이나 여러 개 선택
    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            uris?.forEach { if (!attachments.contains(it)) attachments.add(it) }
            refreshAttachments()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_REPORT
        val unit = Units.selectedUnit(this)

        val headerText = when (mode) {
            MODE_REPORT -> "성군기 위반 신고"
            MODE_COUNSEL -> "상담 신청"
            else -> "건의사항"
        }
        title = headerText
        findViewById<TextView>(R.id.tvHeader).text = headerText
        findViewById<TextView>(R.id.tvGuide).text = when (mode) {
            MODE_REPORT -> "신고 내용은 앱 안에 안전하게 저장되며, $unit 관리자만 열람할 수 있습니다. 신고자 보호 규정에 따라 처리됩니다."
            MODE_COUNSEL -> "상담 내용은 앱 안에 안전하게 저장되며, $unit 관리자(성고충전문상담관)만 열람할 수 있습니다. 편하게 작성하세요."
            else -> "건의사항은 $unit 관리자에게 전달됩니다. 앱·부대 생활 관련 의견을 자유롭게 남겨주세요."
        }

        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etContent = findViewById<EditText>(R.id.etContent)
        llAttachments = findViewById(R.id.llAttachments)

        // 증거 첨부 버튼
        findViewById<Button>(R.id.btnAttach).setOnClickListener {
            pickFiles.launch("*/*")
        }

        // 긴급연결 버튼 (작성 중에도 바로 도움 받기)
        findViewById<Button>(R.id.btnEmergencySubmit).setOnClickListener {
            Emergency.showDialog(this)
        }

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            val titleText = etTitle.text.toString().trim()
            val contentText = etContent.text.toString().trim()

            when {
                titleText.isEmpty() ->
                    Toast.makeText(this, "제목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                contentText.isEmpty() ->
                    Toast.makeText(this, "내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                else -> submit(titleText, contentText)
            }
        }

        refreshAttachments()
    }

    /** 앱 내부 저장소에 저장 → 관리자 로그인으로만 열람 가능 */
    private fun submit(titleText: String, contentText: String) {
        val unit = Units.selectedUnit(this)
        val submission = SubmissionStore.add(this, unit, mode, titleText, contentText, attachments)
        AlertDialog.Builder(this)
            .setTitle("제출 완료")
            .setMessage(
                "정상적으로 접수되었습니다.\n$unit 관리자가 확인 후 처리할 예정입니다.\n\n" +
                "📌 접수번호: ${submission.receiptCode}\n\n" +
                "이 번호를 꼭 기억해 두세요!\n" +
                "설정 > 민원 처리상태 확인 에서 이 번호로\n관리자 확인 여부를 조회할 수 있습니다."
            )
            .setPositiveButton("확인") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    /** 첨부 목록 UI 다시 그리기 */
    private fun refreshAttachments() {
        llAttachments.removeAllViews()
        if (attachments.isEmpty()) {
            llAttachments.visibility = LinearLayout.GONE
            return
        }
        llAttachments.visibility = LinearLayout.VISIBLE
        val inflater = LayoutInflater.from(this)
        attachments.forEach { uri ->
            val row = inflater.inflate(R.layout.item_attachment, llAttachments, false)
            row.findViewById<TextView>(R.id.tvFileName).text = fileName(uri)
            row.findViewById<ImageView>(R.id.btnRemove).setOnClickListener {
                attachments.remove(uri)
                refreshAttachments()
            }
            llAttachments.addView(row)
        }
    }

    /** content URI 에서 파일명 얻기 */
    private fun fileName(uri: Uri): String {
        var name = "첨부파일"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
        }
        return name
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** 신고·상담 화면에 있는 동안 BGM 자동 음소거 (설정에서 끌 수 있음) */
    override fun onResume() {
        super.onResume()
        if (BgmPlayer.isMuteOnSubmit(this)) {
            BgmPlayer.setDucked(this, true)
        }
    }

    override fun onPause() {
        super.onPause()
        BgmPlayer.setDucked(this, false)
    }
}
