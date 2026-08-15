package com.paperweight.os.broadcast

object AdtsHeaderWriter {
    const val ADTS_HEADER_LENGTH = 7

    private val SAMPLE_RATE_INDEX = mapOf(
        96_000 to 0,
        88_200 to 1,
        64_000 to 2,
        48_000 to 3,
        44_100 to 4,
        32_000 to 5,
        24_000 to 6,
        22_050 to 7,
        16_000 to 8,
        12_000 to 9,
        11_025 to 10,
        8_000 to 11,
    )

    fun header(packetLength: Int, sampleRate: Int, channelCount: Int): ByteArray {
        require(packetLength in 7..0x1FFF) { "ADTS packet length must include header and fit in 13 bits." }
        val frequencyIndex = SAMPLE_RATE_INDEX[sampleRate] ?: error("Unsupported AAC sample rate: $sampleRate")
        val profile = 2 // AAC LC, expressed as profile-1 in the header below.
        return ByteArray(7).also { h ->
            h[0] = 0xFF.toByte()
            h[1] = 0xF1.toByte()
            h[2] = (((profile - 1) shl 6) or (frequencyIndex shl 2) or (channelCount shr 2)).toByte()
            h[3] = (((channelCount and 3) shl 6) or (packetLength shr 11)).toByte()
            h[4] = ((packetLength and 0x7FF) shr 3).toByte()
            h[5] = (((packetLength and 7) shl 5) or 0x1F).toByte()
            h[6] = 0xFC.toByte()
        }
    }

    fun packetLength(header: ByteArray): Int {
        require(header.size >= 7) { "ADTS header must be at least 7 bytes." }
        return ((header[3].toInt() and 0x03) shl 11) or
            ((header[4].toInt() and 0xFF) shl 3) or
            ((header[5].toInt() and 0xE0) shr 5)
    }
}
