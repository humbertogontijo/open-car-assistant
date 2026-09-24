package cc.opencar.assistant.api

data class DeviceFingerprint(
    val model: String,
    val device: String,
    val hardware: String,
    val manufacturer: String,
    val fingerprint: String,
    val brand: String,
) {
    fun containsAny(vararg needles: String): Boolean {
        val haystack = listOf(model, device, hardware, manufacturer, fingerprint, brand)
            .joinToString(" ")
            .lowercase()
        return needles.any { haystack.contains(it.lowercase()) }
    }

    companion object {
        fun fromSystemProperties(props: Map<String, String>): DeviceFingerprint =
            DeviceFingerprint(
                model = props["ro.product.model"].orEmpty(),
                device = props["ro.product.device"].orEmpty(),
                hardware = props["ro.hardware"].orEmpty().ifEmpty {
                    props["ro.boot.hardware"].orEmpty()
                },
                manufacturer = props["ro.product.manufacturer"].orEmpty(),
                fingerprint = props["ro.build.fingerprint"].orEmpty(),
                brand = props["ro.product.brand"].orEmpty(),
            )
    }
}
