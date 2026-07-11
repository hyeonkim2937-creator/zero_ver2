package com.army.jeonwoojo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * 시작화면: 배경 이미지 위에서 부대를 선택하면 메인화면으로 이동.
 * 선택된 부대는 저장되어 신고/상담/건의가 그 부대 관리자에게 접수된다.
 */
class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val buttons = listOf(
            R.id.btnUnit1 to Units.ALL[0],   // 571대대
            R.id.btnUnit2 to Units.ALL[1],   // 572대대
            R.id.btnUnit3 to Units.ALL[2],   // 정비근무대
            R.id.btnUnit4 to Units.ALL[3]    // 장비중대
        )

        buttons.forEach { (btnId, unit) ->
            findViewById<Button>(btnId).setOnClickListener {
                Units.setSelectedUnit(this, unit)
                startActivity(Intent(this, MainActivity::class.java))
            }
        }
    }
}
