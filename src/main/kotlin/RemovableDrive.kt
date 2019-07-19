package love.sola.copier

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT
import sun.misc.SharedSecrets
import sun.nio.ch.FileChannelImpl
import java.io.FileDescriptor
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import com.sun.jna.platform.win32.Kernel32 as kernel32

/**
 * @author Sola
 */
data class RemovableDrive(val name: String, val deviceId: String, val size: Long) {

    fun humanReadableByteCount() = humanReadableByteCount(size, true)

    fun openFileChannel(): FileChannel {
        val fd = FileDescriptor()
        val h = openHandle()
        SharedSecrets.getJavaIOFileDescriptorAccess().setHandle(fd, Pointer.nativeValue(h.pointer))
        return FileChannelImpl.open(fd, deviceId, true, true, null)
    }

    fun openHandle(): WinNT.HANDLE =
        kernel32.INSTANCE.CreateFile(
            deviceId,
            kernel32.GENERIC_READ or WinNT.GENERIC_WRITE,
            WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE,
            null,
            WinNT.OPEN_EXISTING,
            WinNT.FILE_FLAG_NO_BUFFERING or WinNT.FILE_FLAG_RANDOM_ACCESS,
            null
        )

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

