package love.sola.copier

import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * @author Sola
 */
data class RemovableDrive(val caption: String, val deviceId: String, val size: Long) {

    fun openFileChannel(): FileChannel {
        val driveIndex = deviceId.substringAfter("PHYSICALDRIVE").toInt()
        val path = Paths.get("""\\.\GLOBALROOT\Device\Harddisk$driveIndex\Partition0""")
        return FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)
    }
}

