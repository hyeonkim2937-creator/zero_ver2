package com.army.jeonwoojo

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "설정"

        val tvValue = findViewById<TextView>(R.id.tvVolumeValue)
        val seekBar = findViewById<SeekBar>(R.id.seekVolume)

        val saved = BgmPlayer.getVolume(this)
        seekBar.progress = saved
        tvValue.text = "$saved%"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvValue.text = "$progress%"
                // 움직이는 동안 실시간으로 소리에 반영
                BgmPlayer.setVolume(this@SettingsActivity, progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 신고·상담 화면 자동 음소거 스위치
        val switchMute = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchMuteOnSubmit)
        switchMute.isChecked = BgmPlayer.isMuteOnSubmit(this)
        switchMute.setOnCheckedChangeListener { _, checked ->
            BgmPlayer.setMuteOnSubmit(this, checked)
        }

        // 민원 처리상태 확인 (접수번호 조회)
        findViewById<android.widget.Button>(R.id.btnCheckStatus).setOnClickListener {
            showStatusLookupDialog()
        }

        // 관리자에게 건의사항 보내기
        findViewById<android.widget.Button>(R.id.btnSuggestion).setOnClickListener {
            startActivity(android.content.Intent(this, SubmitActivity::class.java).apply {
                putExtra(SubmitActivity.EXTRA_MODE, SubmitActivity.MODE_SUGGESTION)
            })
        }

        // 설정 저장 (볼륨·음소거 설정은 변경 즉시 자동 저장되지만,
        // 저장 완료를 확실히 확인시켜 주기 위한 버튼)
        findViewById<android.widget.Button>(R.id.btnSaveSettings).setOnClickListener {
            BgmPlayer.setVolume(this, seekBar.progress)
            BgmPlayer.setMuteOnSubmit(this, switchMute.isChecked)
            android.widget.Toast.makeText(this, "✓ 설정이 저장되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** 접수번호를 입력받아 민원 처리상태를 보여준다 */
    private fun showStatusLookupDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "접수번호 6자리"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("민원 처리상태 확인")
            .setMessage("제출 완료 시 안내받은 접수번호를 입력하세요.")
            .setView(input)
            .setPositiveButton("조회") { _, _ ->
                val code = input.text.toString().trim()
                val found = SubmissionStore.findByReceipt(this, code)
                val message = when {
                    code.length != 6 -> "접수번호는 6자리 숫자입니다."
                    found == null -> "해당 접수번호의 민원이 처리 완료되었거나, 번호가 올바르지 않습니다."
                    else -> "[${found.typeLabel}] ${found.title}\n접수일시: ${found.dateLabel}\n\n상태: ${found.statusLabel}"
                }
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("조회 결과")
                    .setMessage(message)
                    .setPositiveButton("확인", null)
                    .show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
