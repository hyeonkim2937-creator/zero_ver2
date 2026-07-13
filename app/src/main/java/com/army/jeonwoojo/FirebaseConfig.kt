package com.army.jeonwoojo

/**
 * ★★★ Firebase 서버 연결 정보 ★★★
 *
 * README.md 의 "Firebase 서버 설정 방법"을 따라 프로젝트를 만든 뒤,
 * 아래 두 값을 본인 프로젝트의 값으로 교체하세요.
 *
 * - PROJECT_ID: Firebase 콘솔 > 프로젝트 설정 > 일반 > "프로젝트 ID"
 * - API_KEY:    Firebase 콘솔 > 프로젝트 설정 > 일반 > "웹 API 키"
 */
object FirebaseConfig {
    const val PROJECT_ID = "zeroproject-93846"
    const val API_KEY = "AIzaSyBGe4pPTuvbQe6pgAx_rbejODHesRm3CBU"

    /** 설정이 완료되었는지 확인 */
    fun isConfigured(): Boolean =
        PROJECT_ID != "your-project-id" && API_KEY != "your-api-key"
}
