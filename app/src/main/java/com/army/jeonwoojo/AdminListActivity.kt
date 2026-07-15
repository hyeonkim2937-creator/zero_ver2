package com.army.jeonwoojo

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var showArchive = false   // false=접수 목록, true=보관함

    // ===== 자동 로그아웃 (5분 무조작 시) =====
    private val logoutHandler = Handler(Looper.getMainLooper())
    private val logoutRunnable = Runnable {
        Toast.makeText(this, "장시간 사용이 없어 자동 로그아웃되었습니다.", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun resetLogoutTimer() {
        logoutHandler.removeCallbacks(logoutRunnable)
        logoutHandler.postDelayed(logoutRunnable, 5 * 60 * 1000L)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetLogoutTimer()
    }

    override fun onStop() {
        super.onStop()
        logoutHandler.removeCallbacks(logoutRunnable)
        // 공용 기기 보안: 홈 버튼·화면 꺼짐 등으로 벗어나면 즉시 로그아웃
        if (!isFinishing) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        unit = Units.selectedUnit(this)
        title = "$unit 접수 내역 (관리자)"

        llList = findViewById(R.id.llSubmissionList)
        tvEmpty = findViewById(R.id.tvEmpty)

        findViewById<Button>(R.id.btnChangePw).setOnClickListener { showChangePasswordDialog() }
        findViewById<Button>(R.id.btnChangePhone).setOnClickListener { showChangePhoneDialog() }

        findViewById<Button>(R.id.btnArchive).setOnClickListener {
            showArchive = !showArchive
            findViewById<Button>(R.id.btnArchive).text =
                if (showArchive) "📋 접수 목록으로 돌아가기" else "🗂 보관함 보기"
            refreshList()
        }

        // 이 기기를 해당 부대 관리자 알림 대상으로 등록 (15분 주기 새 민원 확인)
        AdminNotify.register(this, unit)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val requestNotifPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onResume() {
        super.onResume()
        resetLogoutTimer()
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

    private fun renderList(all: List<RemoteStore.Submission>) {
        llList.removeAllViews()
        val submissions =
            if (showArchive) all.filter { it.status == RemoteStore.STATUS_ARCHIVED }
            else all.filter { it.status != RemoteStore.STATUS_ARCHIVED }

        // 지금 화면에서 본 미확인 민원은 알림 대상에서 제외 (중복 알림 방지)
        val unconfirmedIds = all.filter { it.status == RemoteStore.STATUS_SUBMITTED }.map { it.id }.toSet()
        AdminNotify.saveKnownIds(this, unit, AdminNotify.knownIds(this, unit) + unconfirmedIds)

        if (submissions.isEmpty()) {
            tvEmpty.text = if (showArchive) "보관함이 비어 있습니다." else "접수된 신고·상담이 없습니다."
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

        // 기존 답변 표시
        if (s.reply.isNotEmpty()) {
            container.addView(TextView(this).apply {
                text = "\n💬 내 답변:\n${s.reply}"
                textSize = 14f
                setTextColor(0xFF2E7D32.toInt())
            })
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("[${s.typeLabel}] ${s.title}")
            .setView(android.widget.ScrollView(this).apply { addView(container) })
            .setPositiveButton("닫기") { _, _ -> refreshList() }
            .setOnCancelListener { refreshList() }

        if (showArchive) {
            builder.setNegativeButton("완전 삭제") { _, _ -> confirmDelete(s) }
            builder.setNeutralButton("복원") { _, _ ->
                RemoteStore.restore(s.id) { ok, error ->
                    if (ok) Toast.makeText(this, "✓ 접수 목록으로 복원되었습니다.", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this, error ?: "복원 실패", Toast.LENGTH_LONG).show()
                    refreshList()
                }
            }
        } else {
            builder.setNegativeButton("처리 완료 (보관)") { _, _ ->
                RemoteStore.archive(s.id) { ok, error ->
                    if (ok) Toast.makeText(this, "✓ 보관함으로 이동했습니다. (보관함에서 복원/완전삭제 가능)", Toast.LENGTH_LONG).show()
                    else Toast.makeText(this, error ?: "보관 실패", Toast.LENGTH_LONG).show()
                    refreshList()
                }
            }
            builder.setNeutralButton("💬 답변 작성") { _, _ -> showReplyDialog(s) }
        }
        builder.show()
    }

    /** 답변 작성: 제출자가 접수번호 조회 시 볼 수 있다 */
    private fun showReplyDialog(s: RemoteStore.Submission) {
        val input = EditText(this).apply {
            hint = "제출자에게 전할 답변을 입력하세요"
            setText(s.reply)
            minLines = 4
            gravity = android.view.Gravity.TOP
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("답변 작성")
            .setMessage("제출자가 [설정 > 민원 처리상태 확인]에서 접수번호로 조회하면 이 답변을 볼 수 있습니다.")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val reply = input.text.toString().trim()
                val progress = AlertDialog.Builder(this).setMessage("저장 중...").setCancelable(false).create()
                progress.show()
                RemoteStore.setReply(s.id, reply) { ok, error ->
                    progress.dismiss()
                    if (ok) Toast.makeText(this, "✓ 답변이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this, error ?: "저장 실패", Toast.LENGTH_LONG).show()
                    refreshList()
                }
            }
            .setNegativeButton("취소") { _, _ -> refreshList() }
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
            val fileName = name ?: displayName
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val audioExts = listOf(".m4a", ".mp3", ".aac", ".wav", ".ogg", ".amr", ".3gp")
            when {
                bitmap != null -> {
                    val iv = ImageView(this).apply {
                        setImageBitmap(bitmap)
                        adjustViewBounds = true
                        setPadding(16, 16, 16, 16)
                    }
                    AlertDialog.Builder(this)
                        .setTitle(fileName)
                        .setView(iv)
                        .setPositiveButton("닫기", null)
                        .show()
                }
                audioExts.any { fileName.lowercase().endsWith(it) } -> playAudio(fileName, bytes)
                else -> AlertDialog.Builder(this)
                    .setTitle(fileName)
                    .setMessage("이미지·음성이 아닌 파일은 앱 내 미리보기가 지원되지 않습니다.")
                    .setPositiveButton("확인", null)
                    .show()
            }
        }
    }

    /** 음성 첨부 재생 (재생 중 BGM 자동 음소거) */
    private fun playAudio(fileName: String, bytes: ByteArray) {
        try {
            val temp = java.io.File(cacheDir, "play_temp_audio")
            temp.writeBytes(bytes)
            val player = MediaPlayer()
            player.setDataSource(temp.absolutePath)
            player.prepare()

            BgmPlayer.setDucked(this, true)
            player.start()

            val dialog = AlertDialog.Builder(this)
                .setTitle("🔊 $fileName")
                .setMessage("음성을 재생 중입니다...")
                .setPositiveButton("정지 / 닫기", null)
                .setCancelable(true)
                .create()
            val stopAll = {
                try { if (player.isPlaying) player.stop() } catch (e: Exception) { }
                player.release()
                BgmPlayer.setDucked(this, false)
            }
            dialog.setOnDismissListener { stopAll() }
            player.setOnCompletionListener { dialog.setMessage("재생이 끝났습니다.") }
            dialog.show()
        } catch (e: Exception) {
            BgmPlayer.setDucked(this, false)
            Toast.makeText(this, "음성 파일을 재생할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(s: RemoteStore.Submission) {
        AlertDialog.Builder(this)
            .setTitle("완전 삭제")
            .setMessage("이 접수 건을 서버에서 완전히 삭제할까요?\n삭제하면 복구할 수 없습니다.")
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

    /** 부대 상담관 전화번호 변경: 긴급 도움 연결 목록에 표시된다 (모든 기기 적용) */
    private fun showChangePhoneDialog() {
        val progress = AlertDialog.Builder(this).setMessage("현재 번호 확인 중...").setCancelable(false).create()
        progress.show()
        RemoteStore.getCounselorPhone(unit) { current, error ->
            progress.dismiss()
            if (error != null || current == null) {
                Toast.makeText(this, error ?: "서버 연결 실패", Toast.LENGTH_LONG).show()
                return@getCounselorPhone
            }
            val input = EditText(this).apply {
                hint = "예: 010-1234-5678 또는 군 내선번호"
                inputType = InputType.TYPE_CLASS_PHONE
                setText(current)
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(this)
                .setTitle("$unit 상담관 전화번호")
                .setMessage("긴급 도움 연결 목록의 '성고충전문상담관' 항목에 표시되는 번호입니다. 모든 기기에 적용됩니다.")
                .setView(input)
                .setPositiveButton("저장") { _, _ ->
                    val phone = input.text.toString().trim()
                    if (phone.isEmpty()) {
                        Toast.makeText(this, "번호를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val saving = AlertDialog.Builder(this).setMessage("저장 중...").setCancelable(false).create()
                    saving.show()
                    RemoteStore.setCounselorPhone(unit, phone) { ok, err2 ->
                        saving.dismiss()
                        if (ok) {
                            Emergency.setCachedCounselorPhone(this, unit, phone)
                            Toast.makeText(this, "✓ 상담관 번호가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, err2 ?: "저장 실패", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

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
