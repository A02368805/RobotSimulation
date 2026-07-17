package ui

import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import model.Robot
import observer.Observer

/**
 * A live readout of the sensor values — the *consumer* side of the Observer pattern.
 *
 * Seven stored [Observer] instances each update one label when their sensor fires.
 * [bindTo] first calls [unbind] to remove any previous subscriptions so old labels are
 * not updated after an environment switch, then subscribes all observers to the new robot.
 */
class TelemetryPanel : VBox(6.0) {

    private val title       = styledLabel("Telemetry", 15.0, bold = true)
    private val sonar       = valueLabel()
    private val temperature = valueLabel()
    private val vision      = valueLabel()
    private val line        = valueLabel()
    private val collision   = valueLabel()

    private var lineLeftOn   = false
    private var lineCenterOn = false
    private var lineRightOn  = false

    // Stored observer references — created once so they can be unsubscribed by identity.
    private val sonarObserver     = Observer<Double>  { sonar.text = "%.0f units".format(it) }
    private val tempObserver      = Observer<Double>  { temperature.text = "%.1f°".format(it) }
    private val visionObserver    = Observer<javafx.scene.paint.Color> { color ->
        vision.text = "#%02x%02x%02x".format(
            (color.red   * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue  * 255).toInt(),
        )
    }
    private val lineLeftObserver   = Observer<Boolean> { lineLeftOn   = it; updateLineLabel() }
    private val lineCenterObserver = Observer<Boolean> { lineCenterOn = it; updateLineLabel() }
    private val lineRightObserver  = Observer<Boolean> { lineRightOn  = it; updateLineLabel() }
    private val collisionObserver  = Observer<Boolean> { collision.text = if (it) "CONTACT" else "Clear" }

    private var boundRobot: Robot? = null

    init {
        padding   = Insets(12.0)
        prefWidth = 210.0
        style     = "-fx-background-color: #14171c;"
        children.addAll(
            title,
            captioned("Sonar (distance)", sonar),
            captioned("Temperature",      temperature),
            captioned("Vision (color)",   vision),
            captioned("Line L / C / R",   line),
            captioned("Collision",        collision),
        )
    }

    /**
     * Unsubscribes all observers from the previously bound robot, then subscribes them to
     * [robot]. Safe to call when no robot is currently bound.
     */
    fun bindTo(robot: Robot) {
        unbind()
        boundRobot = robot
        robot.sonar.subscribe(sonarObserver)
        robot.temperature.subscribe(tempObserver)
        robot.vision.subscribe(visionObserver)
        robot.lineLeft.subscribe(lineLeftObserver)
        robot.lineCenter.subscribe(lineCenterObserver)
        robot.lineRight.subscribe(lineRightObserver)
        robot.collision.subscribe(collisionObserver)
    }

    /** Removes all stored observer subscriptions from the currently bound robot. */
    private fun unbind() {
        val r = boundRobot ?: return
        r.sonar.unsubscribe(sonarObserver)
        r.temperature.unsubscribe(tempObserver)
        r.vision.unsubscribe(visionObserver)
        r.lineLeft.unsubscribe(lineLeftObserver)
        r.lineCenter.unsubscribe(lineCenterObserver)
        r.lineRight.unsubscribe(lineRightObserver)
        r.collision.unsubscribe(collisionObserver)
        boundRobot = null
    }

    private fun updateLineLabel() {
        line.text = "${lineSide(lineLeftOn)}  ${lineSide(lineCenterOn)}  ${lineSide(lineRightOn)}"
    }

    private fun lineSide(on: Boolean) = if (on) "ON" else "off"

    private fun captioned(caption: String, value: Label): VBox =
        VBox(2.0, styledLabel(caption, 11.0, color = "#8b949e"), value)

    private fun valueLabel() = styledLabel("—", 18.0, bold = true)

    private fun styledLabel(text: String, size: Double, bold: Boolean = false, color: String = "#e6edf3"): Label =
        Label(text).apply {
            style = "-fx-font-size: ${size}px; -fx-text-fill: $color;" +
                if (bold) " -fx-font-weight: bold;" else ""
        }
}
