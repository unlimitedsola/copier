package love.sola.copier

import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * @author Sola
 */
data class RemovableDrive(val name: String, val deviceId: String, val size: Long) {

    fun humanReadableByteCount() = humanReadableByteCount(size, true)

    fun openFileChannel(): FileChannel {
        return RandomAccessFile("""\\.\$deviceId""", "rw").channel
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemovableDrive) return false

        if (deviceId != other.deviceId) return false

        return true
    }

    override fun hashCode(): Int {
        return deviceId.hashCode()
    }

    override fun toString(): String {
        return "RemovableDrive(name='$name', deviceId='$deviceId', size=${humanReadableByteCount()})"
    }
}

