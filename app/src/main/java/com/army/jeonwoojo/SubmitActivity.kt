package com.army.jeonwoojo

import android.media.MediaRecorder
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

    // ===== 음성 녹음 =====
    private var recorder: MediaRecorder? = null
    private var recordFile: java.io.File? = null
    private lateinit var btnRecord: Button

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording()
            else Toast.makeText(this, "녹음을 하려면 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show()
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

        // 음성 녹음 버튼 (누르면 녹음 시작 ↔ 다시 누르면 중지 후 첨부)
        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.setOnClickListener {
            if (recorder != null) {
                stopRecording()
            } else {
                requestMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
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
        restoreDraft()
    }

    /** 서버(Firebase)로 전송 → 다른 기기의 관리자도 열람 가능.
     *  전송 실패 시 전송 대기함에 자동 저장되어 네트워크 연결 시 자동 재전송된다. */
    private fun submit(titleText: String, contentText: String) {
        val unit = Units.selectedUnit(this)
        val receiptCode = RemoteStore.generateReceiptCode()
        val progress = AlertDialog.Builder(this)
            .setMessage("제출 중입니다...")
            .setCancelable(false)
            .create()
        progress.show()

        RemoteStore.add(this, unit, mode, titleText, contentText, attachments, receiptCode) { submission, skipped, error ->
            progress.dismiss()
            if (error == null && submission != null) {
                clearDraft()
                val skippedMsg = if (skipped.isNotEmpty())
                    "\n\n⚠ 용량 초과로 첨부되지 못한 파일:\n" + skipped.joinToString("\n") { "- $it" }
                else ""
                AlertDialog.Builder(this)
                    .setTitle("제출 완료")
                    .setMessage(
                        "정상적으로 접수되었습니다.\n$unit 관리자가 확인 후 처리할 예정입니다.\n\n" +
                        "📌 접수번호: ${submission.receiptCode}\n\n" +
                        "이 번호를 꼭 기억해 두세요!\n" +
                        "설정 > 민원 처리상태 확인 에서 이 번호로\n관리자 확인 여부를 조회할 수 있습니다." + skippedMsg
                    )
                    .setPositiveButton("확인") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } else {
                // 전송 실패 → 대기함에 자동 저장 (네트워크 연결 시 자동 재전송)
                val saving = AlertDialog.Builder(this)
                    .setMessage("네트워크가 불안정하여 기기에 저장 중...")
                    .setCancelable(false)
                    .create()
                saving.show()
                PendingStore.saveAsync(this, unit, mode, titleText, contentText, attachments, receiptCode) { ok, skipped2 ->
                    saving.dismiss()
                    if (ok) {
                        clearDraft()
                        PendingWorker.schedule(this)
                        val skippedMsg = if (skipped2.isNotEmpty())
                            "\n\n⚠ 용량 초과로 제외된 파일:\n" + skipped2.joinToString("\n") { "- $it" }
                        else ""
                        AlertDialog.Builder(this)
                            .setTitle("전송 대기함에 저장됨")
                            .setMessage(
                                "지금은 서버에 연결할 수 없어 내용을 기기에 안전하게 저장했습니다.\n" +
                                "네트워크가 연결되면 자동으로 전송됩니다.\n\n" +
                                "📌 접수번호: $receiptCode\n" +
                                "(전송이 완료된 뒤 이 번호로 조회할 수 있습니다)" + skippedMsg
                            )
                            .setPositiveButton("확인") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("제출 실패")
                            .setMessage(error ?: "알 수 없는 오류")
                            .setPositiveButton("확인", null)
                            .show()
                    }
                }
            }
        }
    }

    // ===== 임시저장 (작성 중 튕겨도 내용 유지) =====

    private fun draftPrefs() = getSharedPreferences("drafts", MODE_PRIVATE)

    private fun restoreDraft() {
        val t = draftPrefs().getString("t_$mode", "") ?: ""
        val c = draftPrefs().getString("c_$mode", "") ?: ""
        if (t.isNotEmpty() || c.isNotEmpty()) {
            findViewById<EditText>(R.id.etTitle).setText(t)
            findViewById<EditText>(R.id.etContent).setText(c)
            Toast.makeText(this, "작성 중이던 내용을 불러왔습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDraft() {
        val t = findViewById<EditText>(R.id.etTitle).text.toString()
        val c = findViewById<EditText>(R.id.etContent).text.toString()
        if (t.isBlank() && c.isBlank()) {
            clearDraft()
        } else {
            draftPrefs().edit().putString("t_$mode", t).putString("c_$mode", c).apply()
        }
    }

    private fun clearDraft() {
        draftPrefs().edit().remove("t_$mode").remove("c_$mode").apply()
    }

    /** 첨부 목록 UI 다시 그리기 */    /** 첨부 목록 UI 다시 그리기 */
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

    // ===== 음성 녹음 =====

    @Suppress("DEPRECATION")
    private fun startRecording() {
        try {
            val time = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.KOREA)
                .format(java.util.Date())
            val file = java.io.File(cacheDir, "음성녹음_$time.m4a")
            val r = MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(32000)
            r.setAudioSamplingRate(22050)
            r.setMaxDuration(3 * 60 * 1000)  // 최대 3분 (업로드 용량 제한 때문)
            r.setOutputFile(file.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    Toast.makeText(this, "최대 녹음 시간(3분)에 도달했습니다.", Toast.LENGTH_SHORT).show()
                    stopRecording()
                }
            }
            r.prepare()
            r.start()
            recorder = r
            recordFile = file
            btnRecord.text = "⏹ 녹음 중지 (녹음 중...)"
            Toast.makeText(this, "녹음을 시작했습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            recorder = null
            recordFile = null
            Toast.makeText(this, "녹음을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        btnRecord.text = "🎤 음성 녹음으로 증거 남기기 (최대 3분)"
        try {
            r.stop()
            r.release()
            recordFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    attachments.add(Uri.fromFile(file))
                    refreshAttachments()
                    Toast.makeText(this, "✓ 녹음이 첨부되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            try { r.release() } catch (e2: Exception) { }
            Toast.makeText(this, "녹음이 너무 짧아 저장되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
        recordFile = null
    }

    /** content URI 에서 파일명 얻기 */
    private fun fileName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "첨부파일"
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
        if (recorder != null) stopRecording()
        saveDraft()   // 작성 중 화면을 벗어나거나 앱이 꺼져도 내용 유지
        BgmPlayer.setDucked(this, false)
    }
}
