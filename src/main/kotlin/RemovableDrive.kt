package love.sola.copier

import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * @author Sola
 */
data class RemovableDrive(val caption: String, val deviceId: String, val size: Long) {

    val driveIndex = deviceId.substringAfter("PHYSICALDRIVE").toInt()

    fun openFileChannel(): FileChannel {
        val path = Paths.get("""\\.\GLOBALROOT\Device\Harddisk$driveIndex\Partition0""")
        return FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)
    }

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
}

