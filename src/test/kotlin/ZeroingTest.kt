package love.sola.copier

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

/**
 * @author Sola
 */
fun main() {
    val devices = detectRemovableDrive()
    devices.forEach { it.clean() }
    ChannelMultiplexer(DevZero(), devices.map { it.openFileChannel() }, devices[0].size)
        .start()
}

class DevZero : ReadableByteChannel {
    override fun isOpen(): Boolean = true

    override fun close() {}

    override fun read(dst: ByteBuffer): Int = dst.position(dst.capacity()).capacity()
}
