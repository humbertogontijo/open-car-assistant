package cc.opencar.assistant.integrations.ihu629g

/**
 * EX2-specific VHAL value codecs.
 */
object Ihu629gCodecs {
    /** Zoned temp: °C = 17 + raw / 2 (1 °C steps). */
    fun tempRawToC(raw: Float): Float = 17f + raw / 2f

    fun tempCToRaw(celsius: Float): Float {
        val stepped = kotlin.math.round(celsius)
        return ((stepped - 17f) * 2f).coerceIn(0f, 30f)
    }

    /** Plug: 3 = connected, 0 = disconnected. */
    fun plugConnected(raw: Int?): Boolean? = when (raw) {
        null -> null
        3 -> true
        0 -> false
        else -> raw != 0
    }

    fun parkModeOn(unlimited: Boolean = true): Int =
        if (unlimited) Ihu629gVhalIds.PARK_ON_BASE or Ihu629gVhalIds.PARK_DURATION_UNLIMITED
        else 0

    fun parkModeIsOn(raw: Int?): Boolean = raw != null && raw != 0
}
