package cc.opencar.assistant.signing

import com.android.apksig.ApkSigner
import java.io.File
import java.io.InputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Re-signs an APK with the community (AOSP) testkey.
 * Strips prior signature blocks via apksig when writing a new signed APK.
 */
object ApkReSigner {
    data class Result(val ok: Boolean, val message: String, val output: File? = null)

    fun loadPrivateKey(pk8: ByteArray): PrivateKey {
        val spec = PKCS8EncodedKeySpec(pk8)
        val algorithms = listOf("RSA", "EC", "DSA")
        var last: Exception? = null
        for (alg in algorithms) {
            try {
                return KeyFactory.getInstance(alg).generatePrivate(spec)
            } catch (e: Exception) {
                last = e
            }
        }
        throw IllegalArgumentException("Unsupported PKCS#8 key", last)
    }

    fun loadPrivateKey(pk8: File): PrivateKey = loadPrivateKey(pk8.readBytes())

    fun loadPrivateKey(stream: InputStream): PrivateKey =
        stream.use { loadPrivateKey(it.readBytes()) }

    fun loadCertificate(pemOrDer: ByteArray): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return pemOrDer.inputStream().use {
            cf.generateCertificate(it) as X509Certificate
        }
    }

    fun loadCertificate(pem: File): X509Certificate = loadCertificate(pem.readBytes())

    fun loadCertificate(stream: InputStream): X509Certificate =
        stream.use { loadCertificate(it.readBytes()) }

    fun sign(
        input: File,
        output: File,
        privateKey: PrivateKey,
        certificate: X509Certificate,
    ): Result {
        if (!input.exists() || !input.isFile) {
            return Result(false, "APK not found: ${input.path}")
        }
        return try {
            output.parentFile?.mkdirs()
            val config = ApkSigner.SignerConfig.Builder(
                "COMMUNITY",
                privateKey,
                listOf(certificate),
            ).build()
            ApkSigner.Builder(listOf(config))
                .setInputApk(input)
                .setOutputApk(output)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setOtherSignersSignaturesPreserved(false)
                .build()
                .sign()
            Result(true, "Signed ${output.name}", output)
        } catch (t: Throwable) {
            Result(false, t.message ?: "sign failed")
        }
    }

    fun sign(
        input: File,
        output: File,
        keyFile: File,
        certFile: File,
    ): Result = sign(input, output, loadPrivateKey(keyFile), loadCertificate(certFile))

    /** Default output path: same dir, `<name>_signed.apk`. */
    fun defaultSignedPath(input: File): File {
        val name = input.name
        val base = if (name.endsWith(".apk", ignoreCase = true)) {
            name.dropLast(4)
        } else {
            name
        }
        return File(input.parentFile, "${base}_signed.apk")
    }
}
