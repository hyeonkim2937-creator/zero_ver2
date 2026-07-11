package com.army.jeonwoojo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // BGM 재생은 App.kt + BgmPlayer 가 앱 전역에서 관리한다.

        findViewById<Button>(R.id.btnInfo).setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java))
        }

        findViewById<Button>(R.id.btnReport).setOnClickListener {
            startActivity(Intent(this, SubmitActivity::class.java).apply {
                putExtra(SubmitActivity.EXTRA_MODE, SubmitActivity.MODE_REPORT)
            })
        }

        findViewById<Button>(R.id.btnCounsel).setOnClickListener {
            startActivity(Intent(this, SubmitActivity::class.java).apply {
                putExtra(SubmitActivity.EXTRA_MODE, SubmitActivity.MODE_COUNSEL)
            })
        }

        findViewById<Button>(R.id.btnEmergency).setOnClickListener {
            Emergency.showDialog(this)
        }

        // 설정 (BGM 볼륨 조절)
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 관리자 로그인
        findViewById<TextView>(R.id.btnAdmin).setOnClickListener {
            startActivity(Intent(this, AdminLoginActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 현재 부대 표시 (누르면 시작화면으로 돌아가 부대 변경)
        findViewById<TextView>(R.id.tvUnit).apply {
            text = "현재 부대: ${Units.selectedUnit(this@MainActivity)} (변경)"
            setOnClickListener { finish() }  // 뒤로가기 → 시작화면(부대선택)
        }
    }
}
