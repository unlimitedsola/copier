package love.sola.copier

/**
 * @author Sola
 */
val WMIC_CMD =
    """wmic DISKDRIVE WHERE "InterfaceType='USB' AND MediaType='Removable Media'" GET CAPTION,DEVICEID,SIZE /format:list"""

fun detectRemovableDrive(): List<RemovableDrive> {
    val process = Runtime.getRuntime().exec(WMIC_CMD)
    try {
        process.waitFor()
        val result = arrayListOf<RemovableDrive>()
        process.inputStream.reader().useLines { lines ->
            val iter = lines.filter { it.isNotEmpty() }.iterator()
            while (iter.hasNext()) {
                val paramMap = hashMapOf<String, String>()
                repeat(3) {
                    val (k, v) = iter.next().split('=', limit = 2)
                    paramMap[k] = v
                }
                result += RemovableDrive(paramMap["Caption"]!!, paramMap["DeviceID"]!!, paramMap["Size"]!!.toLong())
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
