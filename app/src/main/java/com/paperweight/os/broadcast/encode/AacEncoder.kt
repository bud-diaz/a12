package com.paperweight.os.broadcast

object AacEncoder {
    private val SILENT_AAC_LC_FRAME = byteArrayOf(
        0x21, 0x10, 0x04, 0x60, 0x8C.toByte(), 0x1C, 0x00, 0x00,
    )

    fun silentAdtsFrame(sampleRate: Int = 44_100, channelCount: Int = 2): ByteArray {
        val packetLength = 7 + SILENT_AAC_LC_FRAME.size
        return AdtsHeaderWriter.header(packetLength, sampleRate, channelCount) + SILENT_AAC_LC_FRAME
    }

    fun silentSegment(frameCount: Int = 96, sampleRate: Int = 44_100, channelCount: Int = 2): ByteArray =
        buildList {
            repeat(frameCount.coerceAtLeast(1)) { add(silentAdtsFrame(sampleRate, channelCount)) }
        }.fold(ByteArray(0)) { acc, frame -> acc + frame }
}
