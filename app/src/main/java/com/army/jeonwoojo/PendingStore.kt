package com.army.jeonwoojo

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * 전송 대기함: 네트워크가 안 될 때 제출 내용을 기기에 저장해 두고,
 * 연결이 복구되면 PendingWorker 가 자동으로 서버에 올린다.
 * 첨부파일은 저장 시점에 압축해 앱 내부저장소에 복사해 둔다.
 * (제출 화면을 닫은 뒤에도 재전송할 수 있도록)
 */
object PendingStore {

    private const val PREFS = "pending_queue"
    private const val KEY_LIST = "list"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Pending(
        val id: String,
        val unit: String,
        val type: String,
        val title: String,
        val content: String,
        val receiptCode: String,
        val createdAt: Long,
        val attachmentPaths: List<String>,   // 내부저장소에 복사된 파일 경로
        val attachmentNames: List<String>    // 표시용 원본 파일명
    )

    /** 대기함 저장 (첨부 압축 포함, 백그라운드에서 처리 후 메인스레드 콜백) */
    fun saveAsync(
        context: Context, unit: String, type: String, title: String, content: String,
        attachments: List<Uri>, receiptCode: String,
        callback: (ok: Boolean, skippedFiles: List<String>) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                val (prepared, skipped) = RemoteStore.prepareAttachmentsBlocking(appContext, attachments)
                val id = UUID.randomUUID().toString()
                val dir = File(appContext.filesDir, "pending").apply { mkdirs() }
                val paths = ArrayList<String>()
                val names = ArrayList<String>()
                prepared.forEachIndexed { i, (name, bytes) ->
                    val f = File(dir, "${id}_$i")
                    f.writeBytes(bytes)
                    paths.add(f.absolutePath)
                    names.add(name)
                }
                val pending = Pending(id, unit, type, title, content, receiptCode,
                    System.currentTimeMillis(), paths, names)
                val list = getAll(appContext).toMutableList()
                list.add(pending)
                save(appContext, list)
                mainHandler.post { callback(true, skipped) }
            } catch (e: Exception) {
                mainHandler.post { callback(false, emptyList()) }
            }
        }
    }

    fun getAll(context: Context): List<Pending> {
        val json = prefs(context).getString(KEY_LIST, "[]") ?: "[]"
        val arr = JSONArray(json)
        val result = ArrayList<Pending>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val paths = ArrayList<String>()
            val pArr = o.optJSONArray("paths") ?: JSONArray()
            for (j in 0 until pArr.length()) paths.add(pArr.getString(j))
            val names = ArrayList<String>()
            val nArr = o.optJSONArray("names") ?: JSONArray()
            for (j in 0 until nArr.length()) names.add(nArr.getString(j))
            result.add(Pending(
                id = o.getString("id"),
                unit = o.getString("unit"),
                type = o.getString("type"),
                title = o.getString("title"),
                content = o.getString("content"),
                receiptCode = o.getString("receiptCode"),
                createdAt = o.getLong("createdAt"),
                attachmentPaths = paths,
                attachmentNames = names
            ))
        }
        return result
    }

    fun count(context: Context): Int = getAll(context).size

    fun remove(context: Context, id: String) {
        val list = getAll(context).toMutableList()
        val target = list.find { it.id == id } ?: return
        target.attachmentPaths.forEach { File(it).delete() }
        list.remove(target)
        save(context, list)
    }

    private fun save(context: Context, list: List<Pending>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject()
                .put("id", p.id)
                .put("unit", p.unit)
                .put("type", p.type)
                .put("title", p.title)
                .put("content", p.content)
                .put("receiptCode", p.receiptCode)
                .put("createdAt", p.createdAt)
                .put("paths", JSONArray(p.attachmentPaths))
                .put("names", JSONArray(p.attachmentNames))
            )
        }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
