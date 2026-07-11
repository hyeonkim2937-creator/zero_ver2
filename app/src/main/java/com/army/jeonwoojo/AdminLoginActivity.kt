package com.army.jeonwoojo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 부대별 관리자 로그인.
 * 시작화면에서 선택한 부대의 관리자 비밀번호(Units.adminPassword)로만 로그인된다.
 * 비밀번호는 Units.kt 에서 부대별로 관리한다. (★ 배포 전 변경 필수)
 */
class AdminLoginActivity : AppCompatActivity() {

    private var failCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val unit = Units.selectedUnit(this)
        title = "$unit 관리자 로그인"
        findViewById<TextView>(R.id.tvLoginUnit).text = "$unit 관리자 로그인"

        val etPassword = findViewById<EditText>(R.id.etPassword)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            if (etPassword.text.toString() == Units.adminPassword(unit)) {
                failCount = 0
                startActivity(Intent(this, AdminListActivity::class.java))
                finish()
            } else {
                failCount++
                etPassword.text.clear()
                if (failCount >= 5) {
                    Toast.makeText(this, "비밀번호를 5회 틀렸습니다. 잠시 후 다시 시도하세요.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this, "비밀번호가 올바르지 않습니다. ($failCount/5)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
