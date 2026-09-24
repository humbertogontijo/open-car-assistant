package cc.opencar.assistant.feature.install

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.ZipFile

/**
 * Curated extras catalog (assets/store/extras.json): github_release, fdroid_repo, direct.
 */
class ExtrasCatalog(private val context: Context) {
    data class ExtraApp(
        val id: String,
        val name: String,
        val packageName: String,
        val summary: String,
        val keywords: List<String>,
        val featured: Boolean,
        val source: String,
        val repo: String?,
        val repoUrl: String?,
        val assetRegex: String?,
        val apkUrl: String?,
        val iconUrl: String?,
    ) {
        fun matches(query: String): Boolean {
            if (query.isBlank()) return featured
            val q = query.lowercase()
            if (name.lowercase().contains(q)) return true
            if (packageName.lowercase().contains(q)) return true
            if (summary.lowercase().contains(q)) return true
            if (id.lowercase().contains(q)) return true
            return keywords.any { it.lowercase().contains(q) }
        }

        fun installReady(): Boolean = when (source) {
            "direct" -> !apkUrl.isNullOrBlank()
            "github_release" -> !repo.isNullOrBlank()
            "fdroid_repo" -> !repoUrl.isNullOrBlank()
            else -> false
        }
    }

    data class ResolvedApk(
        val url: String,
        val versionName: String,
        val versionCode: Long,
        val hash: String? = null,
        val hashType: String? = null,
        val apkName: String? = null,
        val repoBase: String? = null,
    )

    private val apps: List<ExtraApp> by lazy { loadApps() }

    fun all(): List<ExtraApp> = apps

    fun featured(): List<ExtraApp> = apps.filter { it.featured }

    fun search(query: String): List<ExtraApp> =
        apps.filter { it.matches(query.trim()) }

    fun findByPackage(packageName: String): ExtraApp? =
        apps.firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }

    fun resolveLatest(app: ExtraApp): ResolvedApk {
        if (!app.installReady()) {
            throw IllegalStateException(
                when (app.source) {
                    "direct" -> "No apkUrl configured for ${app.name}. Host a mirror and set store/extras.json apkUrl."
                    else -> "Incomplete extras entry for ${app.id}"
                },
            )
        }
        return when (app.source) {
            "direct" -> ResolvedApk(
                url = app.apkUrl!!.trim(),
                versionName = "latest",
                versionCode = 1L,
            )
            "github_release" -> resolveGithub(app)
            "fdroid_repo" -> resolveFdroidRepo(app)
            else -> throw IllegalStateException("Unknown source: ${app.source}")
        }
    }

    private fun resolveGithub(app: ExtraApp): ResolvedApk {
        val repo = app.repo!!
        val json = JSONObject(httpGet("https://api.github.com/repos/$repo/releases/latest"))
        val tag = json.optString("tag_name").ifBlank { "latest" }
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val regex = app.assetRegex?.let { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }
        var match: JSONObject? = null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (regex != null) {
                if (regex.matcher(name).find()) {
                    match = a
                    break
                }
            } else if (name.endsWith(".apk", ignoreCase = true)) {
                match = a
                break
            }
        }
        val asset = match ?: throw IllegalStateException("No matching APK asset in $repo@$tag")
        val url = asset.optString("browser_download_url")
        if (url.isBlank()) throw IllegalStateException("Missing download URL for $repo@$tag")
        val versionCode = tag.filter { it.isDigit() }.takeLast(9).toLongOrNull() ?: 1L
        return ResolvedApk(
            url = url,
            versionName = tag,
            versionCode = versionCode,
            apkName = asset.optString("name"),
        )
    }

    private fun resolveFdroidRepo(app: ExtraApp): ResolvedApk {
        val base = app.repoUrl!!.trimEnd('/')
        val index = loadRepoIndex(base)
        val packages = index.optJSONObject("packages")
            ?: throw IllegalStateException("No packages in $base")
        val apks = packages.optJSONArray(app.packageName)
            ?: throw IllegalStateException("${app.packageName} not in $base")
        var best: JSONObject? = null
        var bestCode = -1L
        for (i in 0 until apks.length()) {
            val apk = apks.optJSONObject(i) ?: continue
            val vc = apk.optLong("versionCode", -1)
            if (vc > bestCode) {
                bestCode = vc
                best = apk
            }
        }
        val apk = best ?: throw IllegalStateException("No APKs for ${app.packageName}")
        val apkName = apk.optString("apkName")
        if (apkName.isBlank()) throw IllegalStateException("Missing apkName for ${app.packageName}")
        return ResolvedApk(
            url = "$base/$apkName",
            versionName = apk.optString("versionName").ifBlank { bestCode.toString() },
            versionCode = bestCode,
            hash = apk.optString("hash").takeIf { it.isNotBlank() },
            hashType = apk.optString("hashType").ifBlank { "sha256" },
            apkName = apkName,
            repoBase = base,
        )
    }

    private val indexCache = HashMap<String, Pair<Long, JSONObject>>()

    private fun loadRepoIndex(repoBase: String): JSONObject {
        val now = System.currentTimeMillis()
        val cached = indexCache[repoBase]
        if (cached != null && now - cached.first < INDEX_TTL_MS) return cached.second
        val jar = File(context.cacheDir, "extras-index-${repoBase.hashCode()}.jar")
        httpDownload("$repoBase/index-v1.jar", jar)
        val json = ZipFile(jar).use { zip ->
            val entry = zip.getEntry("index-v1.json")
                ?: throw IllegalStateException("index-v1.json missing in $repoBase")
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        val obj = JSONObject(json)
        indexCache[repoBase] = now to obj
        return obj
    }

    private fun loadApps(): List<ExtraApp> {
        return try {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val arr = root.optJSONArray("apps") ?: JSONArray()
            val out = ArrayList<ExtraApp>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val keywords = ArrayList<String>()
                val kw = o.optJSONArray("keywords")
                if (kw != null) {
                    for (k in 0 until kw.length()) {
                        keywords += kw.optString(k)
                    }
                }
                out += ExtraApp(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    packageName = o.optString("packageName"),
                    summary = o.optString("summary"),
                    keywords = keywords,
                    featured = o.optBoolean("featured", true),
                    source = o.optString("source"),
                    repo = o.optString("repo").takeIf { it.isNotBlank() },
                    repoUrl = o.optString("repoUrl").takeIf { it.isNotBlank() },
                    assetRegex = o.optString("assetRegex").takeIf { it.isNotBlank() },
                    apkUrl = o.optString("apkUrl").takeIf { it.isNotBlank() },
                    iconUrl = o.optString("iconUrl").takeIf { it.isNotBlank() },
                )
            }
            out
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load $ASSET", t)
            emptyList()
        }
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code for $url: ${body.take(200)}")
            }
            body
        } finally {
            conn.disconnect()
        }
    }

    private fun httpDownload(url: String, dest: File) {
        dest.parentFile?.mkdirs()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = DOWNLOAD_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code downloading $url")
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "ExtrasCatalog"
        private const val ASSET = "store/extras.json"
        private const val USER_AGENT = "OpenCarAssistant/0.1"
        private const val TIMEOUT_MS = 20_000
        private const val DOWNLOAD_TIMEOUT_MS = 120_000
        private val INDEX_TTL_MS = TimeUnit.HOURS.toMillis(6)
    }
}
