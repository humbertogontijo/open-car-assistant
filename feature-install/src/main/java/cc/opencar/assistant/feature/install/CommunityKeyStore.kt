package cc.opencar.assistant.feature.install

import android.content.Context
import cc.opencar.assistant.signing.ApkReSigner
import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate

/** Loads the community testkey packaged in app assets for on-device re-sign. */
class CommunityKeyStore(context: Context) {
    private val appContext = context.applicationContext

    val privateKey: PrivateKey by lazy {
        appContext.assets.open(KEY_ASSET).use { ApkReSigner.loadPrivateKey(it) }
    }

    val certificate: X509Certificate by lazy {
        appContext.assets.open(CERT_ASSET).use { ApkReSigner.loadCertificate(it) }
    }

    fun resign(input: File, output: File = File(input.parentFile, input.nameWithoutExtension + "_signed.apk")): ApkReSigner.Result =
        ApkReSigner.sign(input, output, privateKey, certificate)

    companion object {
        const val KEY_ASSET = "signing/community.pk8"
        const val CERT_ASSET = "signing/community.pem"
    }
}
