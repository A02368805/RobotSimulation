package program

import api.RobotApi
import api.RobotProgram
import command.SetTrackVelocitiesCommand
import javafx.scene.paint.Color
import observer.Observer

/**
 * Navigates toward the red ball using vision, sonar, and collision sensors.
 *
 * Sonar and vision observers only cache their readings. The collision observer is the sole
 * control-loop heartbeat: it caches its reading and calls [updateMovement] with all three
 * cached values current.
 */
class BallFinderProgram : RobotProgram {

    override val name = "Ball Finder"

    private val speed = 105.0
    private val turnSpeed = 85.0
    private val safeDistance = 70.0

    private var robotApi: RobotApi? = null

    private var latestVision: Color? = null
    private var latestSonar = safeDistance + 1.0  // safe default: no obstacle assumed
    private var colliding = false

    private var visionObserver: Observer<Color>? = null
    private var sonarObserver: Observer<Double>? = null
    private var collisionObserver: Observer<Boolean>? = null

    override fun startProgram(robot: RobotApi) {
        robotApi = robot
        latestVision = null
        latestSonar = safeDistance + 1.0
        colliding = false

        val vo = Observer<Color>  { latestVision = it }
        val so = Observer<Double> { latestSonar  = it }
        val co = Observer<Boolean> { colliding = it; updateMovement() }

        visionObserver   = vo
        sonarObserver    = so
        collisionObserver = co

        robot.sensors.vision.subscribe(vo)
        robot.sensors.sonar.subscribe(so)
        robot.sensors.collision.subscribe(co)
    }

    override fun stopProgram(robot: RobotApi) {
        val vo = visionObserver
        val so = sonarObserver
        val co = collisionObserver
        if (vo != null) robot.sensors.vision.unsubscribe(vo)
        if (so != null) robot.sensors.sonar.unsubscribe(so)
        if (co != null) robot.sensors.collision.unsubscribe(co)

        val api = robotApi
        if (api != null) {
            api.perform(SetTrackVelocitiesCommand(api.actuator, 0.0, 0.0))
        }

        visionObserver    = null
        sonarObserver     = null
        collisionObserver = null
        robotApi          = null
    }

    private fun updateMovement() {
        val vision = latestVision
        when {
            colliding || latestSonar <= safeDistance      -> drive(-turnSpeed, turnSpeed)
            vision != null && isTargetRed(vision)         -> drive(speed, speed)
            vision != null && isBlockingSurface(vision)   -> drive(-turnSpeed, turnSpeed)
            else                                          -> drive(speed, speed)
        }
    }

    private fun isTargetRed(color: Color): Boolean =
        color.red > 0.7 &&
        color.red > color.green * 1.5 &&
        color.red > color.blue  * 1.5

    private fun isBlockingSurface(color: Color): Boolean =
        !isTargetRed(color) &&
        color.brightness > 0.22 &&
        color.brightness < 0.55 &&
        color.saturation < 0.40

    private fun drive(left: Double, right: Double) {
        val api = robotApi ?: return
        if (api.actuator.leftTrackVelocity == left && api.actuator.rightTrackVelocity == right) return
        api.perform(SetTrackVelocitiesCommand(api.actuator, left, right))
    }
}
