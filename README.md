# 안심e군 (구 온라인 전우조) (성군기 위반 ZERO 프로젝트)

## 실행 방법
1. Android Studio에서 이 폴더를 열기 (File > Open)
2. Gradle 동기화가 끝나면 에뮬레이터 또는 실제 기기에서 Run ▶

## 화면 구성
- MainActivity : 메인화면 (버튼 3개 + BGM 순환 + 긴급연결 버튼)
- InfoActivity : "성군기 위반이란?" — 법령·사례 안내
- SubmitActivity : "성군기 위반 신고" / "상담 신청" 공용 제출 화면
  (제목·내용 + 증거 첨부 + 긴급연결)
- Emergency.kt : 긴급 도움 연결 다이얼로그 (전화 다이얼러 연동)

## 새로 추가된 기능
### 1) 증거 첨부
- 신고/상담 화면에서 "📎 파일 첨부하기"로 사진·캡처·음성·문서를 여러 개 첨부
- 첨부한 파일은 목록에 표시되며 ✕로 개별 삭제 가능
- 제출 시 첨부가 있으면 메일에 파일이 함께 전송됨(ACTION_SEND_MULTIPLE)

### 2) 긴급연결 버튼
- 메인화면 상단과 신고/상담 화면 상단에 빨간 긴급 버튼 노출
- 누르면 국방헬프콜 1303 / 여성긴급전화 1366 / 경찰 112 / 성고충전문상담관
  목록이 뜨고, 선택하면 전화 다이얼러가 번호를 채운 채 열림
- 전화 권한(CALL_PHONE) 불필요 — ACTION_DIAL 방식이라 안전

## 반드시 수정할 것
- SubmitActivity.kt 의 ADMIN_EMAIL → 실제 관리자 이메일
- Emergency.kt 의 COUNSELOR_PHONE → 부대 성고충전문상담관 직통번호
- res/values/strings.xml 의 info_body 법령 내용을 최신 조문(law.go.kr)과 대조

## Android Studio 없이 테스트하는 방법

### 방법 A — GitHub Actions로 자동 빌드 (추천, 설치 필요 없음)
1. GitHub에 새 저장소(Repository)를 만들고 이 프로젝트 폴더 전체를 업로드합니다.
   (웹에서 "Add file > Upload files"로 드래그해도 됩니다. gradlew 실행권한이
   깨질 수 있으니 되도록 `git push` 방식을 권장합니다.)
2. 저장소의 "Actions" 탭으로 이동하면 `Build Debug APK` 워크플로우가 자동으로
   실행됩니다(코드가 push되는 순간 시작). 수동으로 돌리려면 Actions 탭에서
   "Run workflow" 버튼을 누르면 됩니다.
3. 빌드가 끝나면(2~3분) 해당 실행 결과 페이지 하단 "Artifacts"에서
   `online-jeonwoojo-debug-apk`를 다운로드합니다. 안에 app-debug.apk가 있습니다.
4. APK를 휴대폰으로 옮기고(카카오톡 '나에게 보내기', 이메일, USB 등) 실행하면
   설치됩니다. 처음 설치할 때 "출처를 알 수 없는 앱 설치 허용"을 눌러줘야 합니다.

### 방법 B — 커맨드라인 빌드 (컴퓨터에 Android SDK만 설치)
Android Studio "IDE"는 필요 없고, SDK 커맨드라인 도구만 있으면 됩니다.
1. https://developer.android.com/studio#command-tools 에서
   "Command line tools only"를 받아 설치
2. `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
3. 프로젝트 루트에서 `./gradlew assembleDebug` (Windows는 `gradlew.bat`)
4. `app/build/outputs/apk/debug/app-debug.apk` 생성됨 → 휴대폰에 옮겨 설치
5. 컴퓨터에 USB로 연결한 뒤 `adb install app-debug.apk`로 바로 설치도 가능

### 방법 C — 온라인 APK 테스트 서비스 (설치 없이 브라우저에서 실행)
Appetize.io 같은 사이트에 APK 파일만 업로드하면, 휴대폰을 사지 않아도
브라우저 안에서 가상 기기로 앱을 눌러볼 수 있습니다. 방법 A로 만든
app-debug.apk를 그대로 업로드하면 됩니다. 다만 무료 플랜은 사용 시간 제한이 있습니다.

### 방법 D — 실제 기기에 바로 설치 (가장 단순, 빌드는 누군가 한 번 해줘야 함)
위 방법으로 한 번 APK가 만들어지면, 그 파일 자체를 여러 사람에게 카카오톡이나
이메일로 그대로 뿌려서 각자 폰에 설치해보게 할 수 있습니다. 이후 코드가 바뀔
때마다 방법 A(Actions)로 새 APK를 받아서 다시 공유하면 됩니다.

이 프로젝트에는 이미 Gradle Wrapper(`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`)와 GitHub Actions 워크플로우
(`.github/workflows/build.yml`)가 포함되어 있어서, 방법 A와 B를 바로 시도할 수 있습니다.

현재는 이메일 앱 연동입니다. 익명 신고·접수번호 조회가 필요하면
SubmitActivity.submit() 내부를 Firebase(Firestore) 전송으로 교체하면 됩니다.

## BGM 구조 (v3 변경)
- bgm1 → (5초 간격) → bgm2 → (5초) → bgm3 → (5초) → bgm1 ... 무한 순환
- 재생 주체가 MainActivity가 아닌 앱 전역(App.kt + BgmPlayer.kt)이라
  화면을 넘겨도 음악이 끊기지 않음
- 앱이 백그라운드로 가면 자동 정지, 돌아오면 재개

## 설정 (⚙ 버튼)
- 메인화면 우측 상단 ⚙ → BGM 볼륨 슬라이더 (0~100%, 0%=음소거)
- 볼륨은 저장되어 앱을 껐다 켜도 유지됨

## 신고·상담 화면 자동 음소거 (v4)
- 설정 > "신고·상담 화면 자동 음소거" 스위치 (기본: 켜짐)
- 켜져 있으면 신고/상담 작성 화면에 들어가는 순간 BGM 소리가 0으로,
  화면을 나가면 원래 볼륨으로 자동 복귀 (저장된 볼륨 값은 유지)

## v5 — 앱 내 접수 시스템 + 관리자 로그인
- 앱 아이콘: 7공병여단 마크로 교체
- 메인화면 상단: 7공병여단(좌)·7군단(우) 마크 배치
- 신고/상담 제출: 외부 메일 앱 없이 앱 내부에 저장 → "제출 완료" 안내
- 메인화면 하단 "관리자 로그인" → 비밀번호 입력 → 접수 목록 열람
  - 항목 클릭: 상세 내용 + 첨부파일 (이미지는 미리보기 지원)
  - "처리 완료(삭제)"로 접수 건 정리
- ★ AdminLoginActivity.kt 의 ADMIN_PASSWORD = "jeonwoojo7" 을 반드시 변경할 것

### ⚠ 중요 제약: 같은 기기 안에서만 동작
현재 저장 방식은 "그 기기 내부"에만 저장됩니다.
- 가능: 공용 기기(사이버지식정보방 태블릿, 생활관 공용폰 등)에 앱을 설치하고,
  병사가 그 기기에서 제출 → 간부가 같은 기기에서 관리자 로그인으로 열람
- 불가능: 병사 개인폰에서 제출한 내용이 간부 폰으로 자동 전달 X
기기 간 전달이 필요하면 Firebase 등 서버 연동으로 확장해야 합니다
(SubmissionStore만 교체하면 되는 구조로 만들어 둠).

## v6 — 부대선택·알림·건의사항
- 시작화면: 배경이미지 + 부대 선택 (571대대/572대대/정비근무대/장비중대)
- 부대별 관리자 계정 분리 (Units.kt 의 adminPasswords ★배포 전 변경 필수)
- 제출 시 6자리 접수번호 발급 → 설정 > "민원 처리상태 확인"에서 조회
- 관리자 로그인 시 미확인 민원 건수 알림 + 목록에 🔴 NEW 표시
- 관리자가 열람하면 "확인 완료" 처리 → 제출자가 접수번호로 확인 여부 조회 가능
- 설정 > "관리자에게 건의사항 보내기" 추가 (신고/상담과 동일한 접수 흐름)

## v7 — 안심e군 리브랜딩 + 설정 저장
- 앱 이름/아이콘/헤더 로고: "안심e군"으로 전면 교체
- 시작화면·메인화면 상단에 안심e군 로고(원본 글씨체·색상) 표시
- 설정 화면에 "설정 저장" 버튼 추가 (저장 확인 토스트 + 화면 닫기)

## v8 — 서버 연동 (기기 간 공유) + 비밀번호 변경
- 신고·상담·건의가 Firebase 서버에 저장됨 → 병사 폰에서 제출, 간부 폰에서 열람 가능
- 관리자 비밀번호도 서버 저장 → 한 기기에서 바꾸면 모든 기기 적용
- 관리자 화면에 "🔑 비밀번호 변경" (기존 확인 → 새 비밀번호 → 확인)
- 설정의 "설정 저장" 버튼 제거 (변경 즉시 자동 저장됨)
- 첨부 이미지는 자동 압축(최대 1280px) 후 업로드, 파일당 최대 약 700KB

## ★★★ Firebase 서버 설정 방법 (최초 1회, 필수) ★★★
앱이 동작하려면 아래를 꼭 해야 합니다. 안 하면 제출 시 "서버가 설정되지 않았습니다" 오류가 납니다.

1. https://console.firebase.google.com 접속 → 구글 계정 로그인
2. "프로젝트 만들기" → 이름 아무거나(예: ansim-e-gun) → 애널리틱스는 꺼도 됨 → 만들기
3. 왼쪽 메뉴 "빌드 > Firestore Database" → "데이터베이스 만들기"
   - 위치: asia-northeast3 (서울) 권장
   - 모드: "테스트 모드에서 시작" 선택 → 만들기
4. 만들어지면 상단 "규칙" 탭 → 아래로 교체 후 "게시":
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /{document=**} {
         allow read, write: if true;
       }
     }
   }
   (테스트 모드는 30일 후 만료되므로 위처럼 바꿔야 계속 동작합니다)
5. 왼쪽 위 톱니바퀴 ⚙ > "프로젝트 설정" > "일반" 탭에서
   - "프로젝트 ID" 복사
   - "웹 API 키" 복사
6. 앱 코드의 FirebaseConfig.kt 파일을 열어 두 값을 붙여넣기:
   const val PROJECT_ID = "복사한 프로젝트 ID"
   const val API_KEY = "복사한 웹 API 키"
7. 커밋/푸시 → 새 APK 빌드 → 설치

### ⚠ 보안 참고
위 규칙은 "누구나 읽고 쓸 수 있음"이라 API 주소를 아는 사람은 데이터에 접근할 수
있습니다. 부대 내부용 소규모 운영에는 현실적인 절충안이지만, 민감한 실명 정보를
다루게 되면 Firebase Authentication 도입 등 보안 강화가 필요합니다.
