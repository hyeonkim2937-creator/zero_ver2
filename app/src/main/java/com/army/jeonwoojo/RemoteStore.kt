package com.army.jeonwoojo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Firebase Firestore(구글 클라우드 DB)에 신고·상담·건의를 저장하는 원격 저장소.
 * 병사가 자기 폰에서 제출하면, 간부가 다른 폰에서 관리자 로그인으로 볼 수 있다.
 *
 * - 별도 라이브러리 없이 Firestore REST API + HttpURLConnection 사용 (빌드 안정성)
 * - 모든 함수는 백그라운드에서 통신하고, 결과 콜백은 메인(UI) 스레드로 전달된다.
 * - 관리자 비밀번호도 서버에 저장 → 한 기기에서 바꾸면 모든 기기에 적용
 * - 첨부 이미지는 자동 축소(최대 1280px, JPEG 70%) 후 업로드. 파일당 최대 약 700KB.
 */
object RemoteStore {

    const val STATUS_SUBMITTED = "submitted"
    const val STATUS_CONFIRMED = "confirmed"

    private const val MAX_ATTACHMENT_BYTES = 700_000

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Submission(
        val id: String,
        val unit: String,
        val type: String,          // "report" / "counsel" / "suggestion"
        val title: String,
        val content: String,
        val createdAt: Long,
        val status: String,
        val receiptCode: String,
        val confirmedAt: Long,
        val attachmentNames: List<String>
    ) {
        val typeLabel: String
            get() = when (type) {
                "report" -> "성군기 위반 신고"
                "counsel" -> "상담 신청"
                else -> "건의사항"
            }

        val dateLabel: String get() = format(createdAt)

        val statusLabel: String
            get() = if (status == STATUS_CONFIRMED)
                "관리자 확인 완료 (${format(confirmedAt)})"
            else
                "접수됨 · 관리자 확인 대기 중"

        private fun format(t: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(t))
    }

    // ==================== 공개 API (비동기) ====================

    /** 새 제출. 성공 시 접수번호가 담긴 Submission 반환, 실패 시 error 메시지 */
    fun add(
        context: Context, unit: String, type: String, title: String, content: String,
        attachments: List<Uri>,
        callback: (submission: Submission?, skippedFiles: List<String>, error: String?) -> Unit
    ) {
        runAsync({
            val id = UUID.randomUUID().toString()
            val skipped = ArrayList<String>()
            val uploadedNames = ArrayList<String>()

            // 첨부파일 업로드 (이미지는 압축)
            attachments.forEachIndexed { index, uri ->
                val prepared = readAndCompress(context, uri)
                if (prepared == null) {
                    skipped.add(displayName(context, uri))
                } else {
                    val (name, bytes) = prepared
                    val body = JSONObject().put("fields", JSONObject()
                        .put("name", strV(name))
                        .put("dataBase64", strV(Base64.encodeToString(bytes, Base64.NO_WRAP)))
                    )
                    val (code, _) = http("POST",
                        "${base()}/attachments?documentId=${id}_${uploadedNames.size}&key=${FirebaseConfig.API_KEY}",
                        body.toString())
                    if (code in 200..299) uploadedNames.add(name) else skipped.add(name)
                }
            }

            val submission = Submission(
                id = id, unit = unit, type = type, title = title, content = content,
                createdAt = System.currentTimeMillis(),
                status = STATUS_SUBMITTED,
                receiptCode = String.format(Locale.US, "%06d", Random.nextInt(0, 1_000_000)),
                confirmedAt = 0L,
                attachmentNames = uploadedNames
            )
            val body = JSONObject().put("fields", toFields(submission))
            val (code, resp) = http("POST",
                "${base()}/submissions?documentId=$id&key=${FirebaseConfig.API_KEY}",
                body.toString())
            if (code !in 200..299) throw Exception(httpError(code, resp))
            Triple(submission, skipped, null as String?)
        }, { result, error ->
            if (error != null) callback(null, emptyList(), error)
            else callback(result!!.first, result.second, null)
        })
    }

    /** 특정 부대의 접수 목록 (최신순) */
    fun listForUnit(unit: String, callback: (List<Submission>?, String?) -> Unit) {
        runAsync({
            listAll().filter { it.unit == unit }.sortedByDescending { it.createdAt }
        }, callback)
    }

    /** 관리자 열람 → 확인 완료 처리 */
    fun confirm(id: String, callback: (Boolean, String?) -> Unit) {
        runAsync({
            val body = JSONObject().put("fields", JSONObject()
                .put("status", strV(STATUS_CONFIRMED))
                .put("confirmedAt", intV(System.currentTimeMillis()))
            )
            val (code, resp) = http("PATCH",
                "${base()}/submissions/$id?updateMask.fieldPaths=status&updateMask.fieldPaths=confirmedAt&key=${FirebaseConfig.API_KEY}",
                body.toString())
            if (code !in 200..299) throw Exception(httpError(code, resp))
            true
        }, { ok, error -> callback(ok == true, error) })
    }

    /** 접수번호로 조회 (제출자 상태확인용) */
    fun findByReceipt(code: String, callback: (Submission?, String?) -> Unit) {
        runAsync({
            listAll().find { it.receiptCode == code }
        }, callback)
    }

    /** 접수 삭제 (첨부 포함) */
    fun delete(submission: Submission, callback: (Boolean, String?) -> Unit) {
        runAsync({
            for (i in submission.attachmentNames.indices) {
                http("DELETE", "${base()}/attachments/${submission.id}_$i?key=${FirebaseConfig.API_KEY}", null)
            }
            val (code, resp) = http("DELETE", "${base()}/submissions/${submission.id}?key=${FirebaseConfig.API_KEY}", null)
            if (code !in 200..299) throw Exception(httpError(code, resp))
            true
        }, { ok, error -> callback(ok == true, error) })
    }

    /** 첨부파일 내려받기 (index번째) */
    fun fetchAttachment(submissionId: String, index: Int, callback: (name: String?, bytes: ByteArray?, error: String?) -> Unit) {
        runAsync({
            val (code, resp) = http("GET", "${base()}/attachments/${submissionId}_$index?key=${FirebaseConfig.API_KEY}", null)
            if (code !in 200..299) throw Exception(httpError(code, resp))
            val fields = JSONObject(resp).getJSONObject("fields")
            val name = fields.getJSONObject("name").getString("stringValue")
            val data = Base64.decode(fields.getJSONObject("dataBase64").getString("stringValue"), Base64.NO_WRAP)
            Pair(name, data)
        }, { result, error ->
            if (error != null) callback(null, null, error)
            else callback(result!!.first, result.second, null)
        })
    }

    /** 부대 관리자 비밀번호 조회 (서버에 없으면 Units.kt 기본값) */
    fun getAdminPassword(unit: String, callback: (String?, String?) -> Unit) {
        runAsync({
            val (code, resp) = http("GET", "${base()}/admins/${enc(unit)}?key=${FirebaseConfig.API_KEY}", null)
            when {
                code == 404 -> Units.adminPassword(unit)  // 아직 변경한 적 없음 → 기본값
                code in 200..299 ->
                    JSONObject(resp).getJSONObject("fields").getJSONObject("password").getString("stringValue")
                else -> throw Exception(httpError(code, resp))
            }
        }, callback)
    }

    /** 부대 관리자 비밀번호 변경 (모든 기기에 즉시 적용) */
    fun changeAdminPassword(unit: String, newPassword: String, callback: (Boolean, String?) -> Unit) {
        runAsync({
            val body = JSONObject().put("fields", JSONObject().put("password", strV(newPassword)))
            val (code, resp) = http("PATCH", "${base()}/admins/${enc(unit)}?key=${FirebaseConfig.API_KEY}", body.toString())
            if (code !in 200..299) throw Exception(httpError(code, resp))
            true
        }, { ok, error -> callback(ok == true, error) })
    }

    // ==================== 내부 구현 ====================

    /** 백그라운드 실행 + 메인스레드 콜백 */
    private fun <T> runAsync(work: () -> T, callback: (T?, String?) -> Unit) {
        if (!FirebaseConfig.isConfigured()) {
            mainHandler.post { callback(null, "서버가 설정되지 않았습니다.\nFirebaseConfig.kt 에 프로젝트 정보를 입력하세요. (README 참고)") }
            return
        }
        executor.execute {
            try {
                val result = work()
                mainHandler.post { callback(result, null) }
            } catch (e: Exception) {
                mainHandler.post { callback(null, e.message ?: "네트워크 오류") }
            }
        }
    }

    private fun listAll(): List<Submission> {
        val result = ArrayList<Submission>()
        var pageToken: String? = null
        do {
            val tokenParam = if (pageToken != null) "&pageToken=$pageToken" else ""
            val (code, resp) = http("GET",
                "${base()}/submissions?pageSize=300$tokenParam&key=${FirebaseConfig.API_KEY}", null)
            if (code !in 200..299) throw Exception(httpError(code, resp))
            val json = JSONObject(resp)
            val docs = json.optJSONArray("documents") ?: JSONArray()
            for (i in 0 until docs.length()) {
                parseSubmission(docs.getJSONObject(i))?.let { result.add(it) }
            }
            pageToken = json.optString("nextPageToken", "").ifEmpty { null }
        } while (pageToken != null)
        return result
    }

    private fun parseSubmission(doc: JSONObject): Submission? {
        return try {
            val f = doc.getJSONObject("fields")
            val names = ArrayList<String>()
            val namesJson = JSONArray(f.getJSONObject("attachmentNames").getString("stringValue"))
            for (i in 0 until namesJson.length()) names.add(namesJson.getString(i))
            Submission(
                id = doc.getString("name").substringAfterLast('/'),
                unit = f.getJSONObject("unit").getString("stringValue"),
                type = f.getJSONObject("type").getString("stringValue"),
                title = f.getJSONObject("title").getString("stringValue"),
                content = f.getJSONObject("content").getString("stringValue"),
                createdAt = f.getJSONObject("createdAt").getString("integerValue").toLong(),
                status = f.getJSONObject("status").getString("stringValue"),
                receiptCode = f.getJSONObject("receiptCode").getString("stringValue"),
                confirmedAt = f.getJSONObject("confirmedAt").getString("integerValue").toLong(),
                attachmentNames = names
            )
        } catch (e: Exception) {
            null  // 형식이 안 맞는 문서는 무시
        }
    }

    private fun toFields(s: Submission): JSONObject {
        val namesJson = JSONArray()
        s.attachmentNames.forEach { namesJson.put(it) }
        return JSONObject()
            .put("unit", strV(s.unit))
            .put("type", strV(s.type))
            .put("title", strV(s.title))
            .put("content", strV(s.content))
            .put("createdAt", intV(s.createdAt))
            .put("status", strV(s.status))
            .put("receiptCode", strV(s.receiptCode))
            .put("confirmedAt", intV(s.confirmedAt))
            .put("attachmentNames", strV(namesJson.toString()))
    }

    private fun strV(v: String) = JSONObject().put("stringValue", v)
    private fun intV(v: Long) = JSONObject().put("integerValue", v.toString())

    private fun base() =
        "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpError(code: Int, resp: String): String = when (code) {
        403 -> "서버 접근이 거부되었습니다. Firestore 보안 규칙을 확인하세요. (README 참고)"
        404 -> "데이터를 찾을 수 없습니다."
        else -> "서버 오류 ($code)"
    }

    /** HTTP 요청 (블로킹, 백그라운드에서만 호출) */
    private fun http(method: String, urlStr: String, body: String?): Pair<Int, String> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            if (method == "PATCH") {
                // HttpURLConnection은 PATCH 미지원 → POST + 오버라이드 헤더
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                conn.requestMethod = method
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Pair(code, text)
        } finally {
            conn.disconnect()
        }
    }

    /** 첨부 준비: 이미지는 축소·압축, 그 외는 원본. 너무 크면 null(건너뜀) */
    private fun readAndCompress(context: Context, uri: Uri): Pair<String, ByteArray>? {
        return try {
            val name = displayName(context, uri)
            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            val bytes: ByteArray = if (bitmap != null) {
                // 이미지 → 최대 1280px로 축소 후 JPEG 70%
                val maxSide = 1280
                val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
                val scaled = if (scale < 1f)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                else bitmap
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                out.toByteArray()
            } else raw

            if (bytes.size > MAX_ATTACHMENT_BYTES) null else Pair(name, bytes)
        } catch (e: Exception) {
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        var name = "첨부파일"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
            }
        } catch (e: Exception) { /* 무시 */ }
        return name
    }
}
