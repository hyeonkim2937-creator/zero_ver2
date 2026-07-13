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
 * 비밀번호는 서버(Firebase)에 저장되어 모든 기기에서 동일하게 적용된다.
 * (서버에 저장된 적 없으면 Units.kt 의 기본 비밀번호 사용)
 */
class AdminLoginActivity : AppCompatActivity() {

    private var failCount = 0
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val unit = Units.selectedUnit(this)
        title = "$unit 관리자 로그인"
        findViewById<TextView>(R.id.tvLoginUnit).text = "$unit 관리자 로그인"

        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            if (checking) return@setOnClickListener
            val entered = etPassword.text.toString()
            if (entered.isEmpty()) {
                Toast.makeText(this, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checking = true
            btnLogin.text = "확인 중..."

            RemoteStore.getAdminPassword(unit) { serverPassword, error ->
                checking = false
                btnLogin.text = "로그인"
                if (error != null || serverPassword == null) {
                    Toast.makeText(this, error ?: "서버 연결 실패", Toast.LENGTH_LONG).show()
                    return@getAdminPassword
                }
                if (entered == serverPassword) {
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
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
