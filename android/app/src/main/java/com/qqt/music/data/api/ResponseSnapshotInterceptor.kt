package com.qqt.music.data.api

import android.util.Base64
import com.google.gson.JsonParser
import com.qqt.music.data.local.NetworkMonitor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 响应快照拦截器（ADR 0012）：为只读接口留档成功响应，断网或请求失败时回放最后一次留档。
 *
 * 协议约束：业务请求是单一 POST api.php 且 data 含动态 salt，同一业务的两次请求字节永不相同，
 * HTTP 层缓存（OkHttp Cache / Cache-Control）原理性不可行——只能按业务语义 key
 * （method_name + 业务参数，剔除 package_name/salt/sign 三元组）在应用层留档响应。
 *
 * 回放口径（ADR 0012）：
 * - 白名单仅覆盖全部只读接口；app_details 与全部写接口（评分、访问上报、时长写回）不拦不写
 * - 断网（[NetworkMonitor] 判定，含已连网但未通过联网验证）时命中即回放；
 *   弱网发包失败（IOException）兜底回放，两个口径都通向回放
 * - 仅回写 ONLINE_MP3 非空有效的响应（GET 文本为非空白），空列表不覆盖既有快照
 * - get_recent_songs 的 key 含 ID 串（最近播放每多一首即变，整串缓存必然 miss），
 *   特殊化为按歌曲 ID 的实体级缓存；回放时按请求 ID 串顺序切片重组——调用方
 *   本就按传入顺序重排并合并全部页，合并结果与在线等价
 *
 * 服务器不可达（ADR 0017，本拦截器必须位于 failover 外层）：
 * - 入口（主用/回退，[ApiClient.entryHosts]）请求连接层失败（failover 已走完两次尝试）
 *   或收到 5xx 时进入粘性态；入口读操作秒回快照，5xx 与断网同口径回放，
 *   不入白名单的写请求仍维持失败（不排队重放）
 * - 粘性态中每个入口读操作在后台静默重发真实请求探测（在飞去重），
 *   任一入口请求获得非 5xx 应答即退出；恢复不打断当前页面，下次加载自然取新数据
 * - 探测请求带 [SnapshotProbe] 标记跳过回放（防递归），其成功应答照常回写快照
 */
class ResponseSnapshotInterceptor(
    /** 后台探测发包用：取主 client（含 failover 全链），构造期不调用 */
    private val clientProvider: () -> OkHttpClient,
) : Interceptor {

    /** 后台探测在飞去重：一个探测未决期间不再发新的，避免堆叠 */
    private val probeInFlight = AtomicBoolean(false)

    /** 探测请求标记类（挂在 [Request.tag] 上，不上线） */
    private class SnapshotProbe

    /** 解析成功且允许回放/回写的请求；null = 与快照机制无关，直连放过 */
    private data class Parsed(
        val key: String,
        /** POST 业务方法名；GET 文本（歌词）为 null */
        val method: String? = null,
        /** 非空 = get_recent_songs 的实体级回放：本页应包含的歌曲 ID（按传入顺序切片） */
        val entityIdSlice: List<String>? = null,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val parsed = parse(request)
        val isProbe = request.tag(SnapshotProbe::class.java) != null
        val isEntry = request.url.host in ApiClient.entryHosts

        if (!isProbe && parsed != null) {
            // 断网快回放：不发包直接回放，省去等超时的白等
            if (NetworkMonitor.isOffline) {
                replay(request, parsed)?.let { return it }
            }
            // 服务器不可达（ADR 0017）：入口读操作秒回快照并后台静默探测；
            // CDN 歌词等非入口请求不在此列（手机在线时照常回源，失败再由下方兜底回放）
            else if (NetworkMonitor.isServerUnreachable && isEntry) {
                replay(request, parsed)?.let { replayed ->
                    launchProbe(request)
                    return replayed
                }
            }
        }

        // 在线照常发出；成功即回写，失败（IOException）兜底回放
        return try {
            val response = chain.proceed(request)
            if (response.code >= 500) {
                // 5xx 视为服务端不可用：与连接失败同口径进粘性态，入口读操作回放；写请求维持原响应
                if (isEntry) NetworkMonitor.enterServerUnreachable()
                if (!isProbe && parsed != null) {
                    replay(request, parsed)?.let { replayed ->
                        response.close()
                        return replayed
                    }
                }
            } else {
                // 服务端有应答即视为可达（含 4xx）：任一入口请求成功即退出粘性态
                if (isEntry) NetworkMonitor.exitServerUnreachable()
                if (parsed != null && response.isSuccessful) record(parsed, response)
            }
            response
        } catch (e: IOException) {
            // 连接层失败（failover 已走完主用+回退）：进粘性态；读操作兜底回放，写请求维持失败
            if (isEntry && !NetworkMonitor.isOffline && e.isConnectFailure()) {
                NetworkMonitor.enterServerUnreachable()
            }
            if (!isProbe && parsed != null) {
                replay(request, parsed)?.let { return it }
            }
            throw e
        }
    }

    /** 后台静默探测（ADR 0017）：在飞期只保留一个；走完整 client 链，成败的进出态与回写由本拦截器统一判定 */
    private fun launchProbe(request: Request) {
        if (!probeInFlight.compareAndSet(false, true)) return
        val probe = request.newBuilder()
            .tag(SnapshotProbe::class.java, SnapshotProbe())
            .build()
        clientProvider().newCall(probe).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                probeInFlight.set(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                probeInFlight.set(false)
            }
        })
    }

    private fun replay(request: Request, parsed: Parsed): Response? =
        if (parsed.entityIdSlice != null) replayEntities(request, parsed.entityIdSlice)
        else replaySnapshot(request, parsed.key)

    // ── 请求解析 ──────────────────────────────────────────────

    private fun parse(request: Request): Parsed? {
        if (request.method == "GET") {
            // 经本 client 的 GET 目前只有歌词文本下载（fetchText），按 URL 留档
            return Parsed(key = "url:${request.url}")
        }
        if (request.url.encodedPath.substringAfterLast('/') != API_PATH) return null
        val data = (request.body as? FormBody)?.let { body ->
            (0 until body.size).firstOrNull { body.name(it) == "data" }?.let { body.value(it) }
        } ?: return null
        val params = try {
            JsonParser.parseString(
                URLDecoder.decode(String(Base64.decode(data, Base64.NO_WRAP), Charsets.UTF_8), "UTF-8"),
            ).asJsonObject
        } catch (e: Exception) {
            return null
        }
        val method = params.get("method_name")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { it in REPLAYABLE_METHODS } ?: return null
        if (method == METHOD_RECENT_SONGS) {
            val ids = params.get("songs_ids")?.takeIf { it.isJsonPrimitive }?.asString
                ?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
            val page = params.get("page")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: 1
            val slice = ids.drop((page - 1) * ENTITY_PAGE_SIZE).take(ENTITY_PAGE_SIZE)
            if (slice.isEmpty()) return null
            return Parsed(key = "entity:song", method = method, entityIdSlice = slice)
        }
        // 剔除鉴权三元组后按参数名排序，得到与时间戳 salt 无关的稳定业务 key
        val canonical = params.entrySet()
            .filter { it.key !in AUTH_FIELDS }
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        return Parsed(key = "api:$method:$canonical", method = method)
    }

    // ── 回写 ──────────────────────────────────────────────────

    /** 在线成功后回写；解析失败视为无效响应，不落盘 */
    private fun record(parsed: Parsed, response: Response) {
        val body = try {
            response.peekBody(MAX_PEEK_BYTES).string()
        } catch (e: Exception) {
            return
        }
        try {
            if (parsed.method == null) {
                // GET 文本（歌词）：非空白即有效
                if (body.isNotBlank()) ResponseSnapshotStore.write(parsed.key, body)
                return
            }
            if (parsed.entityIdSlice != null) {
                // 实体级留档：响应内每首歌曲按其 ID 独立落盘，不写请求级 key（回放只走实体级）
                JsonParser.parseString(body).asJsonObject
                    .get("ONLINE_MP3")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.forEach { el ->
                        val id = el.takeIf { it.isJsonObject }?.asJsonObject
                            ?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                        if (!id.isNullOrBlank()) ResponseSnapshotStore.write("entity:song:$id", el.toString())
                    }
                return
            }
            val mp3 = JsonParser.parseString(body).asJsonObject.get("ONLINE_MP3") ?: return
            val valid = when {
                mp3.isJsonArray -> mp3.asJsonArray.size() > 0
                mp3.isJsonObject -> mp3.asJsonObject.entrySet().isNotEmpty()
                else -> false
            }
            if (valid) ResponseSnapshotStore.write(parsed.key, body)
        } catch (e: Exception) {
            // 结构异常：不回写，保留既有快照
        }
    }

    // ── 回放 ──────────────────────────────────────────────────

    /** 请求级回放：命中留档即构造 200 响应 */
    private fun replaySnapshot(request: Request, key: String): Response? =
        ResponseSnapshotStore.read(key)?.let { respond(request, it, json = true) }

    /** 实体级回放：按请求 ID 串切片逐个取实体重组；一个都凑不到视为未命中（走真实请求） */
    private fun replayEntities(request: Request, slice: List<String>): Response? {
        val songs = slice.mapNotNull { id -> ResponseSnapshotStore.read("entity:song:$id") }
        if (songs.isEmpty()) return null
        return respond(request, "{\"ONLINE_MP3\":[${songs.joinToString(",")}]}", json = true)
    }

    private fun respond(request: Request, bodyText: String, json: Boolean): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("Response Snapshot (ADR 0012)")
            .body(
                bodyText.toResponseBody(
                    (if (json) "application/json" else "text/plain").toMediaType(),
                ),
            )
            .build()

    companion object {
        private const val API_PATH = "api.php"

        private const val METHOD_RECENT_SONGS = "get_recent_songs"

        /** get_recent_songs 每页条数（后端硬编码，对齐 MusicRepository.RECENT_PAGE_SIZE；此处不反向依赖 repository） */
        private const val ENTITY_PAGE_SIZE = 10

        /** 单个响应体最大预读字节数：API 响应均为小体量 JSON/文本，仅作防御上限 */
        private const val MAX_PEEK_BYTES = 16L * 1024 * 1024

        /** 回放白名单：全部只读接口（app_details 与全部写接口不拦不写，ADR 0012）；song_info 是唯一例外——
         *  详情请求在后端带「计一次播放」副作用，但回放走本地构造不触网，副作用不会重放（ADR 0014） */
        private val REPLAYABLE_METHODS = setOf(
            "home", "home_new",
            "album_list", "cat_albums", "cat_books", "book_chapters", "book_chapter",
            "cat_list", "latest", "all_songs", "album_songs",
            "get_recent_songs", "song_search", "artist_name_songs",
            "song_info",
        )

        /** 鉴权三元组：salt 每请求必变，不能参与 key（package_name/sign 恒定，一并剔除） */
        private val AUTH_FIELDS = setOf("package_name", "salt", "sign")
    }
}
