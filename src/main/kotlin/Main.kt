package love.sola.copier

import javafx.application.Platform
import love.sola.copier.fx.CopierApp
import tornadofx.launch

/**
 * @author Sola
 */
fun main(args: Array<String>) {
    Platform.setImplicitExit(true)
    launch<CopierApp>(args)
}
