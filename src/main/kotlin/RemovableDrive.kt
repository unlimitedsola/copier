package love.sola.copier

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE
import com.sun.jna.platform.win32.WinNT.*
import com.sun.jna.platform.win32.Winioctl.*
import com.sun.jna.platform.win32.WinioctlUtil.CTL_CODE
import com.sun.jna.ptr.IntByReference
import sun.misc.SharedSecrets
import sun.nio.ch.FileChannelImpl
import java.io.FileDescriptor
import java.io.IOException
import java.nio.channels.FileChannel
import com.sun.jna.platform.win32.Kernel32.INSTANCE as kernel32

/**
 * @author Sola
 */
data class RemovableDrive(val name: String, val letter: String, val size: Long) {

    fun humanReadableByteCount() = humanReadableByteCount(size, true)

    fun openFileChannel(): FileChannel {
        val fd = FileDescriptor()
        val h = openHandle()
        if (h == INVALID_HANDLE_VALUE) {
            throw IOException("Failed to open $letter")
        }
        if (!h.lock()) {
            throw IOException("Failed to lock volume $letter")
        }
        SharedSecrets.getJavaIOFileDescriptorAccess().setHandle(fd, Pointer.nativeValue(h.pointer))
        return FileChannelImpl.open(fd, letter, true, true, null)
    }

    fun openHandle(): HANDLE =
        kernel32.CreateFile(
            """\\.\$letter""",
            GENERIC_READ or GENERIC_WRITE,
            FILE_SHARE_READ or FILE_SHARE_WRITE,
            null,
            OPEN_EXISTING,
            FILE_FLAG_NO_BUFFERING or FILE_FLAG_RANDOM_ACCESS,
            null
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemovableDrive) return false

        if (letter != other.letter) return false

        return true
    }

    override fun hashCode(): Int {
        return letter.hashCode()
    }

    override fun toString(): String {
        return "RemovableDrive(name='$name', letter='$letter', size=${humanReadableByteCount()})"
    }
}

private val FSCTL_ALLOW_EXTENDED_DASD_IO = CTL_CODE(FILE_DEVICE_FILE_SYSTEM, 32, METHOD_NEITHER, FILE_ANY_ACCESS)
private val FSCTL_LOCK_VOLUME = CTL_CODE(FILE_DEVICE_FILE_SYSTEM, 6, METHOD_BUFFERED, FILE_ANY_ACCESS)
private val FSCTL_UNLOCK_VOLUME = CTL_CODE(FILE_DEVICE_FILE_SYSTEM, 7, METHOD_BUFFERED, FILE_ANY_ACCESS)

fun HANDLE.lock(): Boolean {
    val sizePtr = IntByReference()
    val res1 = kernel32.DeviceIoControl(
        this,
        FSCTL_ALLOW_EXTENDED_DASD_IO,
        null,
        0,
        null,
        0,
        sizePtr,
        null
    )
    if (!res1) return false
    repeat(150) {
        val res2 = kernel32.DeviceIoControl(
            this,
            FSCTL_LOCK_VOLUME,
            null,
            0,
            null,
            0,
            sizePtr,
            null
        )
        if (res2) return true
        Thread.sleep(100)
    }
    return false
}
