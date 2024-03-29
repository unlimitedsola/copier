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
import javafx.scene.control.Alert.AlertType.ERROR
import javafx.scene.control.Alert.AlertType.WARNING
import javafx.scene.control.ButtonType.CANCEL
import javafx.scene.control.ButtonType.OK
import javafx.scene.layout.Priority
import love.sola.copier.ChannelMultiplexer
import love.sola.copier.RemovableVolume
import love.sola.copier.detectRemovableVolumes
import love.sola.copier.util.humanReadableByteCount
import tornadofx.*
import java.text.MessageFormat
import java.text.MessageFormat.*
import java.text.NumberFormat
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * @author Sola
 */
class MainView : View() {

    val availableVolumesProperty = observableSetOf<RemovableVolume>()
    val sourceVolumeProperty = SimpleObjectProperty<RemovableVolume>()
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
            .map { detectRemovableVolumes() }
            .observeOnFx()
            .subscribe {
                availableVolumesProperty.retainAll(it)
                availableVolumesProperty.addAll(it)
            }
    }

    data class TargetVolumeItem(val volume: RemovableVolume) {
        val selectedProperty = SimpleBooleanProperty(false)
        var selected: Boolean by selectedProperty
        val volumeLetter get() = volume.letter
        val volumeName get() = volume.name
        val volumeSize get() = volume.humanReadableSize()
    }

    override val root: Parent = vbox {
        padding = insets(10)
        spacing = 10.0
        val availableVolumesList = observableListOf<RemovableVolume>()
        availableVolumesProperty.addListener { e: SetChangeListener.Change<out RemovableVolume> ->
            when {
                e.wasAdded() -> availableVolumesList.add(e.elementAdded)
                e.wasRemoved() -> availableVolumesList.remove(e.elementRemoved)
            }
        }
        label(messages["from"])
        combobox(sourceVolumeProperty, availableVolumesList) {
            disableWhen { currentTaskProperty.isNotNull }
            maxWidth = Double.MAX_VALUE
            converter = RemovableVolumeStringFormatter
        }
        val targetVolumeItems = observableListOf<TargetVolumeItem>()
        availableVolumesProperty.addListener { e: SetChangeListener.Change<out RemovableVolume> ->
            when {
                e.wasAdded() -> targetVolumeItems.add(TargetVolumeItem(e.elementAdded))
                e.wasRemoved() -> targetVolumeItems.removeIf { it.volume == e.elementRemoved }
            }
        }
        sourceVolumeProperty.addListener { _, old: RemovableVolume?, new: RemovableVolume? ->
            if (old != null) {
                targetVolumeItems.add(TargetVolumeItem(old))
            }
            if (new != null) {
                targetVolumeItems.removeIf { it.volume == new }
            }
        }
        label(messages["to"])
        tableview(targetVolumeItems) {
            maxHeight = Double.MAX_VALUE
            vgrow = Priority.ALWAYS
            disableWhen { currentTaskProperty.isNotNull }
            readonlyColumn(messages["volume.letter"], TargetVolumeItem::volumeLetter)
            readonlyColumn(messages["volume.name"], TargetVolumeItem::volumeName).remainingWidth()
            readonlyColumn(messages["volume.size"], TargetVolumeItem::volumeSize)
            column(messages["volume.selected"], TargetVolumeItem::selectedProperty) { useCheckbox() }
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
                    currentTask.cancel()
                } else {
                    val sourceVolume = sourceVolumeProperty.get()
                    val targetVolumes = targetVolumeItems.filter { it.selected }.map { it.volume }
                    val newTask = createTask(sourceVolume, targetVolumes) ?: return@action
                    currentTaskProperty.set(newTask)
                    thread(isDaemon = true, name = "Copier-Dispatcher") {
                        log.info("Task started: $sourceVolume => $targetVolumes, length=${newTask.length}")
                        newTask.start()
                    }
                    runAsync(daemon = true) {
                        var lastCheckedReadBytes = 0L
                        while (!newTask.isCancelled && !newTask.isDone) {
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

    private fun createTask(source: RemovableVolume?, target: List<RemovableVolume>): ChannelMultiplexer? {
        if (source == null) {
            alert(ERROR, messages["alert.no-source.title"], messages["alert.no-source.message"])
            return null
        }
        if (target.isEmpty()) {
            alert(ERROR, messages["alert.no-target.title"], messages["alert.no-target.message"])
            return null
        }
        val size = determineSize(source, target)
        if (size < source.size * 0.99) { // 1% tolerance
            alert(
                WARNING,
                messages["alert.size-mismatch.title"],
                format(messages["alert.size-mismatch.message"], humanReadableByteCount(size, true)),
                OK,
                CANCEL
            ) {
                if (it == CANCEL) return null
            }
        }
        return ChannelMultiplexer(
            source.openFileChannel(),
            target.map { it.openFileChannel() },
            size
        )
    }

    private fun determineSize(source: RemovableVolume, target: List<RemovableVolume>): Long =
        (target + source).minBy { it.size }!!.size
}
