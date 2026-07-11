package com.army.jeonwoojo

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/**
 * 신고·상담·건의 내역을 기기 안(SharedPreferences + 내부저장소)에 저장하는 저장소.
 *
 * v6 확장:
 * - 부대(unit)별로 접수를 구분해 부대별 관리자만 자기 부대 것을 열람
 * - 접수번호(receiptCode) 발급: 제출자가 익명으로 처리상태를 조회하는 열쇠
 * - 상태(status): 접수됨 → 관리자 확인 완료
 *
 * ※ 이 방식은 "같은 기기 안"에서만 동작한다.
 *   기기 간 전달이 필요하면 Firebase 등 서버로 교체해야 한다.
 */
object SubmissionStore {

    private const val PREFS = "submissions"
    private const val KEY_LIST = "list"

    const val STATUS_SUBMITTED = "submitted"
    const val STATUS_CONFIRMED = "confirmed"

    data class Submission(
        val id: String,
        val unit: String,
        val type: String,          // "report" / "counsel" / "suggestion"
        val title: String,
        val content: String,
        val attachmentPaths: List<String>,
        val createdAt: Long,
        val status: String,        // STATUS_SUBMITTED / STATUS_CONFIRMED
        val receiptCode: String,   // 6자리 접수번호 (제출자 상태조회용)
        val confirmedAt: Long      // 관리자 확인 시각 (0이면 미확인)
    ) {
        val typeLabel: String
            get() = when (type) {
                "report" -> "성군기 위반 신고"
                "counsel" -> "상담 신청"
                else -> "건의사항"
            }

        val dateLabel: String
            get() = format(createdAt)

        val statusLabel: String
            get() = if (status == STATUS_CONFIRMED)
                "관리자 확인 완료 (${format(confirmedAt)})"
            else
                "접수됨 · 관리자 확인 대기 중"

        private fun format(t: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(t))
    }

    /** 새 제출 저장. 접수번호를 발급해 돌려준다. */
    fun add(context: Context, unit: String, type: String, title: String, content: String, attachments: List<Uri>): Submission {
        val copiedPaths = attachments.mapNotNull { copyToInternal(context, it) }
        val existing = getAll(context)
        val submission = Submission(
            id = UUID.randomUUID().toString(),
            unit = unit,
            type = type,
            title = title,
            content = content,
            attachmentPaths = copiedPaths,
            createdAt = System.currentTimeMillis(),
            status = STATUS_SUBMITTED,
            receiptCode = generateReceiptCode(existing),
            confirmedAt = 0L
        )
        val list = existing.toMutableList()
        list.add(0, submission)  // 최신이 위로
        save(context, list)
        return submission
    }

    /** 전체 목록 (최신순) */
    fun getAll(context: Context): List<Submission> {
        val json = prefs(context).getString(KEY_LIST, "[]") ?: "[]"
        val arr = JSONArray(json)
        val result = ArrayList<Submission>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val atts = ArrayList<String>()
            val attArr = o.optJSONArray("attachments") ?: JSONArray()
            for (j in 0 until attArr.length()) atts.add(attArr.getString(j))
            result.add(
                Submission(
                    id = o.getString("id"),
                    unit = o.optString("unit", Units.ALL[0]),
                    type = o.getString("type"),
                    title = o.getString("title"),
                    content = o.getString("content"),
                    attachmentPaths = atts,
                    createdAt = o.getLong("createdAt"),
                    status = o.optString("status", STATUS_SUBMITTED),
                    receiptCode = o.optString("receiptCode", ""),
                    confirmedAt = o.optLong("confirmedAt", 0L)
                )
            )
        }
        return result
    }

    /** 특정 부대의 접수만 (관리자 화면용) */
    fun getForUnit(context: Context, unit: String): List<Submission> =
        getAll(context).filter { it.unit == unit }

    /** 특정 부대의 미확인 건수 (관리자 알림용) */
    fun countUnconfirmed(context: Context, unit: String): Int =
        getForUnit(context, unit).count { it.status == STATUS_SUBMITTED }

    /** 관리자가 열람 → 확인 완료 처리 (제출자가 접수번호로 확인 가능해짐) */
    fun confirm(context: Context, id: String) {
        val list = getAll(context).map { s ->
            if (s.id == id && s.status == STATUS_SUBMITTED)
                s.copy(status = STATUS_CONFIRMED, confirmedAt = System.currentTimeMillis())
            else s
        }
        save(context, list)
    }

    /** 접수번호로 조회 (제출자 상태확인용) */
    fun findByReceipt(context: Context, code: String): Submission? =
        getAll(context).find { it.receiptCode == code }

    /** 처리 완료된 제출 삭제 (첨부파일도 함께 삭제) */
    fun delete(context: Context, id: String) {
        val list = getAll(context).toMutableList()
        val target = list.find { it.id == id } ?: return
        target.attachmentPaths.forEach { path -> File(path).delete() }
        list.remove(target)
        save(context, list)
    }

    // ---------- 내부 ----------

    private fun save(context: Context, list: List<Submission>) {
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("unit", s.unit)
            o.put("type", s.type)
            o.put("title", s.title)
            o.put("content", s.content)
            o.put("attachments", JSONArray(s.attachmentPaths))
            o.put("createdAt", s.createdAt)
            o.put("status", s.status)
            o.put("receiptCode", s.receiptCode)
            o.put("confirmedAt", s.confirmedAt)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /** 6자리 숫자 접수번호 생성 (기존과 중복되지 않게) */
    private fun generateReceiptCode(existing: List<Submission>): String {
        val used = existing.map { it.receiptCode }.toSet()
        var code: String
        do {
            code = String.format(Locale.US, "%06d", Random.nextInt(0, 1_000_000))
        } while (code in used)
        return code
    }

    /** content:// URI 파일을 앱 내부저장소로 복사 (원본이 지워져도 관리자가 볼 수 있도록) */
    private fun copyToInternal(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val name = queryFileName(context, uri) ?: "file"
            val dest = File(dir, "${UUID.randomUUID()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
        }
        return name
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
