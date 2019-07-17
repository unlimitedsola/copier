package love.sola.copier.fx

import com.github.thomasnield.rxkotlinfx.observeOnFx
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.SetChangeListener
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.layout.Priority
import love.sola.copier.ChannelMultiplexer
import love.sola.copier.RemovableDrive
import love.sola.copier.detectRemovableDrive
import love.sola.copier.humanReadableByteCount
import tornadofx.*
import java.text.NumberFormat
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * @author Sola
 */
class MainView : View() {

    val availableDrivesProperty = observableSetOf<RemovableDrive>()
    val sourceDriveProperty = SimpleObjectProperty<RemovableDrive>()
    val currentTaskProperty = SimpleObjectProperty<ChannelMultiplexer>()
    val progressProperty = SimpleDoubleProperty(0.0).apply {
        currentTaskProperty.addListener { _, _, new -> if (new == null) this.set(0.0) }
    }
    val speedProperty = SimpleLongProperty().apply {
        currentTaskProperty.addListener { _, _, new -> if (new == null) this.set(0) }
    }

    init {
        this.title = messages["title"]
        Observable.interval(2, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .map { detectRemovableDrive() }
            .observeOnFx()
            .subscribe {
                availableDrivesProperty.retainAll(it)
                availableDrivesProperty.addAll(it)
            }
    }

    data class TargetDriveItem(val drive: RemovableDrive) {
        val selectedProperty = SimpleBooleanProperty(false)
        var selected: Boolean by selectedProperty
        val driveIndex get() = drive.driveIndex
        val driveTitle get() = drive.caption
        val driveSize get() = drive.humanReadableByteCount()
    }

    override val root: Parent = vbox {
        padding = insets(10)
        spacing = 10.0
        val availableDrivesList = observableListOf<RemovableDrive>()
        availableDrivesProperty.addListener { e: SetChangeListener.Change<out RemovableDrive> ->
            when {
                e.wasAdded() -> availableDrivesList.add(e.elementAdded)
                e.wasRemoved() -> availableDrivesList.remove(e.elementRemoved)
            }
        }
        label(messages["from"])
        combobox(sourceDriveProperty, availableDrivesList) {
            disableWhen { currentTaskProperty.isNotNull }
            maxWidth = Double.MAX_VALUE
            converter = RemovableDriveStringFormatter
        }
        val targetDriveItems = observableListOf<TargetDriveItem>()
        availableDrivesProperty.addListener { e: SetChangeListener.Change<out RemovableDrive> ->
            when {
                e.wasAdded() -> targetDriveItems.add(TargetDriveItem(e.elementAdded))
                e.wasRemoved() -> targetDriveItems.removeIf { it.drive == e.elementRemoved }
            }
        }
        sourceDriveProperty.addListener { _, old: RemovableDrive?, new: RemovableDrive? ->
            if (old != null) {
                targetDriveItems.add(TargetDriveItem(old))
            }
            if (new != null) {
                targetDriveItems.removeIf { it.drive == new }
            }
        }
        label(messages["to"])
        tableview(targetDriveItems) {
            disableWhen { currentTaskProperty.isNotNull }
            readonlyColumn(messages["drive.index"], TargetDriveItem::driveIndex)
            readonlyColumn(messages["drive.title"], TargetDriveItem::driveTitle)
            readonlyColumn(messages["drive.size"], TargetDriveItem::driveSize)
            column(messages["drive.selected"], TargetDriveItem::selectedProperty) { useCheckbox() }
            smartResize()
        }
        progressbar(progressProperty) {
            maxWidth = Double.MAX_VALUE
        }
        hbox {
            label(messages["ready"]) {
                currentTaskProperty.addListener { _, _, new ->
                    when (new) {
                        null -> text = messages["ready"]
                    }
                }
                val percentFormat = NumberFormat.getPercentInstance().apply { minimumFractionDigits = 2 }
                progressProperty.addListener { _, _, new ->
                    if (new != 0.0) {
                        text = percentFormat.format(new)
                    }
                }
                hgrow = Priority.ALWAYS
                maxWidth = Double.MAX_VALUE
                alignment = Pos.CENTER_LEFT
            }
            label("0B/s") {
                speedProperty.addListener { _, _, new ->
                    text = "${humanReadableByteCount(new as Long, true)}/s"
                }
                alignment = Pos.CENTER_RIGHT
            }
        }
        button(messages["start"]) {
            currentTaskProperty.addListener { _, _, new ->
                text = if (new != null) messages["cancel"] else messages["start"]
                graphic = if (new != null) progressindicator { setPrefSize(16.0, 16.0) } else null
            }
            maxWidth = Double.MAX_VALUE
            action {
                val currentTask = currentTaskProperty.get()
                if (currentTask != null) {
                    currentTask.isCancelled = true
                } else {
                    val sourceDrive = sourceDriveProperty.get()
                    val targetDrives = targetDriveItems.filter { it.selected }.map { it.drive }
                    log.info("Task started: $sourceDrive => $targetDrives")
                    val newTask = ChannelMultiplexer(
                        sourceDrive.openFileChannel(),
                        targetDrives.map { it.openFileChannel() },
                        sourceDrive.size
                    )
                    currentTaskProperty.set(newTask)
                    thread(isDaemon = true, name = "Copier-Dispatcher") { newTask.start() }
                    runAsync(daemon = true) {
                        var lastCheckedReadBytes = 0L
                        while (!newTask.isCancelled) {
                            val readBytes = newTask.readBytes
                            if (readBytes > lastCheckedReadBytes) {
                                val speedPerSec = (readBytes - lastCheckedReadBytes) * 2
                                runLater {
                                    speedProperty.set(speedPerSec)
                                    progressProperty.set(readBytes.toDouble() / newTask.length)
                                }
                                lastCheckedReadBytes = readBytes
                            }
                            Thread.sleep(500)
                        }
                    } ui {
                        currentTaskProperty.set(null)
                    }
                }
            }
        }
    }
}
