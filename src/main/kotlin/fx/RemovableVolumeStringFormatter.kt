package love.sola.copier.fx

import javafx.util.StringConverter
import love.sola.copier.RemovableVolume

/**
 * @author Sola
 */
object RemovableVolumeStringFormatter : StringConverter<RemovableVolume>() {
    override fun toString(volume: RemovableVolume): String =
        "${volume.letter} ${volume.name} [${volume.humanReadableSize()}]"

    override fun fromString(string: String?): RemovableVolume? {
        throw UnsupportedOperationException()
    }
}
