package cc.opencar.assistant

import android.app.Application
import android.os.Build
import android.os.UserManager
import android.util.Log
import cc.opencar.assistant.api.DeviceFingerprint
import cc.opencar.assistant.feature.debug.LogRingBuffer
import cc.opencar.assistant.feature.debug.OaaLog

class OaaApp : Application() {
    lateinit var runtime: AssistantRuntime
        private set

    val log = OaaLog()

    override fun onCreate() {
        super.onCreate()
        instance = this
        LogRingBuffer.append("OaaApp onCreate fingerprint=${Build.FINGERPRINT}")
        runtime = AssistantRuntime(this)
        val unlocked = runCatching {
            getSystemService(UserManager::class.java)?.isUserUnlocked == true
        }.getOrDefault(true)
        if (unlocked) {
            runtime.startAsync()
        } else {
            // Credential-encrypted work waits for USER_UNLOCKED / BootReceiver.
            Log.i("OaaApp", "direct boot — deferring runtime until user unlock")
            LogRingBuffer.append("OaaApp deferred start (user locked)")
        }
    }

    companion object {
        lateinit var instance: OaaApp
            private set

        fun deviceFingerprint(): DeviceFingerprint =
            DeviceFingerprint(
                model = Build.MODEL.orEmpty(),
                device = Build.DEVICE.orEmpty(),
                hardware = Build.HARDWARE.orEmpty(),
                manufacturer = Build.MANUFACTURER.orEmpty(),
                fingerprint = Build.FINGERPRINT.orEmpty(),
                brand = Build.BRAND.orEmpty(),
            )
    }
}
