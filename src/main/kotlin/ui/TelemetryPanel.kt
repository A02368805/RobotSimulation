package ui

import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import model.Robot

/**
 * A live readout of the sensor values — the *consumer* side of the Observer pattern.
 *
 * The layout (labels) is provided. Making it live is your job: in [bindTo] you subscribe an
 * observer to each sensor so the matching label updates when the sensor reports a reading.
 */
class TelemetryPanel : VBox(6.0) {

    private val title = styledLabel("Telemetry", 15.0, bold = true)
    private val sonar = valueLabel()
    private val temperature = valueLabel()
    private val vision = valueLabel()
    private val line = valueLabel()
    private val collision = valueLabel()

    private var lineLeftOn = false
    private var lineCenterOn = false
    private var lineRightOn = false

    init {
        padding = Insets(12.0)
        prefWidth = 210.0
        style = "-fx-background-color: #14171c;"
        children.addAll(
            title,
            captioned("Sonar (distance)", sonar),
            captioned("Temperature", temperature),
            captioned("Vision (color)", vision),
            captioned("Line L / C / R", line),
            captioned("Collision", collision),
        )
    }

    /**
     * Subscribe observers to the given robot's sensors so the labels update live. Called whenever
     * the robot is (re)created — on startup, environment change, and reset.
     */
    fun bindTo(robot: Robot) {
        robot.sonar.subscribe { sonar.text = "%.0f units".format(it) }
        robot.temperature.subscribe { temperature.text = "%.1f°".format(it) }
        robot.vision.subscribe { color ->
            vision.text = "#%02x%02x%02x".format(
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            )
        }
        robot.lineLeft.subscribe { lineLeftOn = it; updateLineLabel() }
        robot.lineCenter.subscribe { lineCenterOn = it; updateLineLabel() }
        robot.lineRight.subscribe { lineRightOn = it; updateLineLabel() }
        robot.collision.subscribe { collision.text = if (it) "CONTACT" else "Clear" }
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
