package cc.opencar.assistant.feature.web

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared, timed `su` probe for userdebug HUs.
 *
 * Antora's `/system/xbin/su` is shell-group only and uses AOSP `su 0 <cmd>`
 * syntax — Magisk-style `su -c` fails (and can hang waiting on stdin).
 * Callers must not run this on a hot polling path without the cache.
 */
object SuProbe {
    private val lock = Any()
    private val cached = AtomicReference<Boolean?>(null)

    /** Cached result; probes at most once until [invalidate]. */
    fun available(): Boolean {
        cached.get()?.let { return it }
        synchronized(lock) {
            cached.get()?.let { return it }
            val v = probe()
            cached.set(v)
            return v
        }
    }

    fun invalidate() {
        cached.set(null)
    }

    private fun probe(): Boolean {
        for (argv in PROBE_ARGV) {
            if (runTimed(argv)?.contains("uid=0") == true) return true
        }
        return false
    }

    /**
     * Run [argv], wait up to [TIMEOUT_MS], then read stdout/stderr.
     * Never blocks forever — hung `su` is destroyed.
     */
    fun runTimed(argv: Array<String>, timeoutMs: Long = TIMEOUT_MS): String? {
        var p: Process? = null
        return try {
            p = ProcessBuilder(*argv)
                .redirectErrorStream(true)
                .start()
            val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                p.destroyForcibly()
                p.waitFor(200, TimeUnit.MILLISECONDS)
                return null
            }
            p.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (_: Throwable) {
            runCatching { p?.destroyForcibly() }
            null
        }
    }

    /** AOSP-first prefixes for `sh -c <script>` (never Magisk `-c` alone as script). */
    fun shPrefixes(): List<Array<String>> = listOf(
        arrayOf("su", "0", "sh", "-c"),
        arrayOf("su", "root", "sh", "-c"),
    )

    /**
     * Run [script] under su via [shPrefixes].
     * When [accept] is set, keeps trying prefixes until output matches; otherwise
     * returns the first successful stdout (or empty if all fail/timeout).
     */
    fun runScript(
        script: String,
        timeoutMs: Long = 8_000L,
        accept: ((String) -> Boolean)? = null,
    ): String {
        var last = ""
        for (prefix in shPrefixes()) {
            val out = runTimed(prefix + arrayOf(script), timeoutMs = timeoutMs) ?: continue
            last = out
            if (accept == null || accept(out)) return out
        }
        return last
    }

    private val PROBE_ARGV = listOf(
        arrayOf("su", "0", "id"),
        arrayOf("su", "root", "id"),
    )

    private const val TIMEOUT_MS = 800L
}
