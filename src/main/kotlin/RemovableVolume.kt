package love.sola.copier

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE
import com.sun.jna.platform.win32.WinNT.*
import com.sun.jna.platform.win32.Winioctl.*
import com.sun.jna.platform.win32.WinioctlUtil.CTL_CODE
import com.sun.jna.ptr.IntByReference
import love.sola.copier.util.humanReadableByteCount
import sun.misc.SharedSecrets
import sun.nio.ch.FileChannelImpl
import java.io.FileDescriptor
import java.io.IOException
import java.nio.channels.FileChannel
import com.sun.jna.platform.win32.Kernel32.INSTANCE as kernel32

/**
 * @author Sola
 */
data class RemovableVolume(val name: String, val letter: String, val size: Long) {

    fun humanReadableSize(): String = humanReadableByteCount(size, true)

    fun openFileChannel(): FileChannel {
        val fd = FileDescriptor()
        // the logical volume handle has to be opened in a specific manner, so we implemented our own.
        val h = openHandle()
        if (h == INVALID_HANDLE_VALUE) {
            throw IOException("Failed to open $letter")
        }
        // locking the volume is required, otherwise we will be bothered by other apps.
        if (!h.lock()) {
            throw IOException("Failed to lock volume $letter")
        }
        // a hack to set the FileDescriptor.handle. probably won't work on more restrictive JPMS.
        SharedSecrets.getJavaIOFileDescriptorAccess().setHandle(fd, Pointer.nativeValue(h.pointer))
        // the FileChannelImpl.open will reuse the FileDescriptor we pass directly into it.
        return FileChannelImpl.open(fd, letter, true, true, null)
    }

    /**
     * Open a native handle for this volume with read & write accesses.
     */
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
        if (other !is RemovableVolume) return false

        if (letter != other.letter) return false

        return true
    }

    override fun hashCode(): Int {
        return letter.hashCode()
    }

    override fun toString(): String {
        return "RemovableVolume(name='$name', letter='$letter', size=${humanReadableSize()})"
    }
}

private val FSCTL_ALLOW_EXTENDED_DASD_IO = CTL_CODE(FILE_DEVICE_FILE_SYSTEM, 32, METHOD_NEITHER, FILE_ANY_ACCESS)
private val FSCTL_LOCK_VOLUME = CTL_CODE(FILE_DEVICE_FILE_SYSTEM, 6, METHOD_BUFFERED, FILE_ANY_ACCESS)
private val FSCTL_UNLOCK_VOLUME = CTL_CODE(FILE_DEVICE_FILE_SYSTEM, 7, METHOD_BUFFERED, FILE_ANY_ACCESS)
private const val DRIVE_ACCESS_RETRIES = 150
private const val DRIVE_ACCESS_TIMEOUT = 15000

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
    // a spin wait. just in case something is **accidentally** spying our volume.
    repeat(DRIVE_ACCESS_RETRIES) {
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
        Thread.sleep((DRIVE_ACCESS_TIMEOUT / DRIVE_ACCESS_RETRIES).toLong())
    }
    return false
}
