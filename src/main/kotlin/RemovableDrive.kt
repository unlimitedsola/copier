package love.sola.copier

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32.GENERIC_READ
import com.sun.jna.platform.win32.WinNT.*
import sun.misc.SharedSecrets
import sun.nio.ch.FileChannelImpl
import java.io.FileDescriptor
import java.nio.channels.FileChannel
import com.sun.jna.platform.win32.Kernel32.INSTANCE as kernel32

/**
 * @author Sola
 */
data class RemovableDrive(val caption: String, val deviceId: String, val size: Long) {

    val driveIndex = deviceId.substringAfter("PHYSICALDRIVE").toInt()

    fun humanReadableByteCount() = humanReadableByteCount(size, true)

    fun openFileChannel(): FileChannel {
        val fd = FileDescriptor()
        val h = openHandle()
        SharedSecrets.getJavaIOFileDescriptorAccess().setHandle(fd, Pointer.nativeValue(h.pointer))
        return FileChannelImpl.open(fd, deviceId, true, true, null)
    }

    fun openHandle(): HANDLE =
        kernel32.CreateFile(
            deviceId,
            GENERIC_READ or GENERIC_WRITE,
            FILE_SHARE_READ or FILE_SHARE_WRITE,
            null,
            OPEN_EXISTING,
            FILE_FLAG_NO_BUFFERING or FILE_FLAG_RANDOM_ACCESS,
            null
        )

    /**
     * Delete all partitions on this drive.
     */
    fun clean(): Boolean {
        val process = Runtime.getRuntime().exec("diskpart")
        try {
            process.outputStream.write(
                ("select disk $driveIndex\n" +
                        "clean\n" +
                        "exit\n")
                    .toByteArray()
            )
            process.outputStream.flush()
            process.waitFor()
            process.inputStream.reader().useLines { lines ->
                return lines.any { it == "DiskPart succeeded in cleaning the disk." }
            }
        } catch (e: Exception) {
            throw e
        } finally {
            process.destroy()
            process.inputStream.close()
            process.errorStream.close()
            process.outputStream.close()
        }
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
        return "RemovableDrive(caption='$caption', deviceId='$deviceId', size=${humanReadableByteCount()})"
    }
}

