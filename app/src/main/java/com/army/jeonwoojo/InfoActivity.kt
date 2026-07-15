package com.army.jeonwoojo

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class InfoActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var btnPlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        videoView = findViewById(R.id.videoCase)
        btnPlay = findViewById(R.id.btnPlayVideo)

        // 앱에 내장된 가상 사례 영상
        videoView.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.case_video}"))

        // 재생/일시정지/탐색 컨트롤
        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)

        // ▶ 버튼: BGM 끄고 재생 시작
        btnPlay.setOnClickListener {
            BgmPlayer.setDucked(this, true)   // 영상 재생 중 BGM 자동 음소거
            btnPlay.visibility = Button.GONE
            videoView.start()
        }

        // 영상이 끝나면 BGM 복귀 + 재생 버튼 다시 표시
        videoView.setOnCompletionListener {
            BgmPlayer.setDucked(this, false)
            btnPlay.visibility = Button.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        // 화면을 벗어나면 영상 정지 + BGM 복귀
        if (videoView.isPlaying) videoView.pause()
        BgmPlayer.setDucked(this, false)
        btnPlay.visibility = Button.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
