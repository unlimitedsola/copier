package love.sola.copier

/**
 * @author Sola
 */
val WMIC_CMD =
    """wmic LOGICALDISK WHERE DriveType=2 GET VolumeName,DEVICEID,SIZE /format:list"""

/**
 * query all USB volumes by using wmic command.
 */
fun detectRemovableVolumes(): List<RemovableVolume> {
    val process = Runtime.getRuntime().exec(WMIC_CMD)
    try {
        process.waitFor()
        val result = arrayListOf<RemovableVolume>()
        process.inputStream.reader().useLines { lines ->
            val iter = lines.filter { it.isNotEmpty() }.iterator()
            while (iter.hasNext()) {
                val paramMap = hashMapOf<String, String>()
                repeat(3) {
                    val (k, v) = iter.next().split('=', limit = 2)
                    paramMap[k] = v
                }
                result += RemovableVolume(
                    paramMap["VolumeName"]!!,
                    paramMap["DeviceID"]!!,
                    paramMap["Size"]!!.toLongOrNull() ?: 0
                )
            }
        }
        return result
    } catch (e: Exception) {
        throw e
    } finally {
        process.destroy()
        process.inputStream.close()
        process.errorStream.close()
        process.outputStream.close()
    }
}
