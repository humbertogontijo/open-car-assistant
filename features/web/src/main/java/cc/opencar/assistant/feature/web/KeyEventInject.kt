package cc.opencar.assistant.feature.web

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import java.util.concurrent.TimeUnit

/**
 * Unprivileged key injection — the path that actually drives media transport and
 * cabin volume on this HU (MediaController / CarAudio / STREAM_MUSIC do not).
 */
internal object KeyEventInject {
    private const val TAG = "KeyEventInject"

    fun inject(context: Context, keyCode: Int): Boolean {
        return try {
            val im = context.getSystemService(InputManager::class.java) ?: return false
            val method = InputManager::class.java.methods.firstOrNull {
                it.name == "injectInputEvent" && it.parameterTypes.size == 2
            } ?: return false
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD,
            )
            val up = KeyEvent(
                now, now + 20, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD,
            )
            val r1 = method.invoke(im, down, 0) as? Boolean ?: false
            val r2 = method.invoke(im, up, 0) as? Boolean ?: false
            r1 && r2
        } catch (t: Throwable) {
            Log.d(TAG, "inject($keyCode): ${t.message}")
            false
        }
    }

    fun execInput(keyCode: Int): Boolean {
        return try {
            val p = ProcessBuilder("input", "keyevent", keyCode.toString())
                .redirectErrorStream(true)
                .start()
            if (!p.waitFor(1_200, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly()
                return false
            }
            p.exitValue() == 0
        } catch (t: Throwable) {
            Log.d(TAG, "exec($keyCode): ${t.message}")
            false
        }
    }

    /** Prefer [inject]; fall back to shell `input keyevent`. */
    fun send(context: Context, keyCode: Int): Boolean =
        inject(context, keyCode) || execInput(keyCode)
}
