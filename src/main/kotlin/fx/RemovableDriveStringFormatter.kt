package love.sola.copier.fx

import javafx.util.StringConverter
import love.sola.copier.RemovableDrive

/**
 * @author Sola
 */
object RemovableDriveStringFormatter : StringConverter<RemovableDrive>() {
    override fun toString(drive: RemovableDrive): String =
        "${drive.driveIndex}: ${drive.caption} [${drive.humanReadableByteCount()}]"

    override fun fromString(string: String?): RemovableDrive? {
        throw UnsupportedOperationException()
    }
}
