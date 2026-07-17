package program

import api.RobotApi
import api.RobotProgram
import command.SetTrackVelocitiesCommand
import observer.Observer

/**
 * Climbs the temperature gradient toward the heat source.
 *
 * Both the temperature and collision observers update their cached state and immediately
 * call [applyMovement] through their respective handlers:
 *
 * - [respondToTemperature]: shifts the temperature readings, recalculates the desired
 *   temperature-based velocities via [updateTemperatureDecision], then applies movement.
 * - [respondToCollision]: caches the collision flag, then applies movement.
 *
 * [applyMovement] uses collision-avoidance velocities while colliding and resumes the most
 * recently calculated temperature-based velocities the moment collision clears, without
 * re-evaluating the temperature trend.
 */
class TemperatureSeekerProgram : RobotProgram {

    override val name = "Temperature Seeker"

    private val speed       = 100.0
    private val turnSpeed   = 75.0
    private val hotThreshold = 92.0
    private val epsilon     = 0.05
    private val curveFactor = 0.25

    private var robotApi: RobotApi? = null

    private var currentTemperature: Double? = null
    private var previousTemperature: Double? = null
    private var colliding = false
    private var searchDirection = 1   // 1 = right arc/pivot, -1 = left arc/pivot

    /** Most recently calculated temperature-based desired velocities. */
    private var desiredLeft  = speed
    private var desiredRight = speed

    private var temperatureObserver: Observer<Double>? = null
    private var collisionObserver: Observer<Boolean>? = null

    override fun startProgram(robot: RobotApi) {
        robotApi = robot
        currentTemperature  = null
        previousTemperature = null
        colliding       = false
        searchDirection = 1
        desiredLeft     = speed
        desiredRight    = speed

        val to = Observer<Double>  { respondToTemperature(it) }
        val co = Observer<Boolean> { respondToCollision(it) }

        temperatureObserver = to
        collisionObserver   = co

        robot.sensors.temperature.subscribe(to)
        robot.sensors.collision.subscribe(co)
    }

    override fun stopProgram(robot: RobotApi) {
        val to = temperatureObserver
        val co = collisionObserver
        if (to != null) robot.sensors.temperature.unsubscribe(to)
        if (co != null) robot.sensors.collision.unsubscribe(co)

        val api = robotApi
        if (api != null) {
            api.perform(SetTrackVelocitiesCommand(api.actuator, 0.0, 0.0))
        }

        temperatureObserver = null
        collisionObserver   = null
        robotApi            = null
    }

    private fun respondToTemperature(value: Double) {
        previousTemperature = currentTemperature
        currentTemperature  = value
        updateTemperatureDecision()
        applyMovement()
    }

    private fun respondToCollision(value: Boolean) {
        colliding = value
        applyMovement()
    }

    /**
     * Recalculates [desiredLeft] and [desiredRight] from the latest temperature readings.
     * Only called from [respondToTemperature], so [searchDirection] is never altered by
     * collision callbacks.
     */
    private fun updateTemperatureDecision() {
        val curr = currentTemperature ?: run { setDesiredMovement(speed, speed); return }

        if (curr >= hotThreshold) {
            setDesiredMovement(0.0, 0.0)
            return
        }

        val prev = previousTemperature ?: run { setDesiredMovement(speed, speed); return }

        val delta = curr - prev
        when {
            delta > epsilon  -> setDesiredMovement(speed, speed)
            delta < -epsilon -> {
                if (searchDirection > 0) setDesiredMovement(speed * curveFactor, speed)
                else                     setDesiredMovement(speed, speed * curveFactor)
                searchDirection *= -1
            }
            // Effectively equal: retain the most recently stored desired velocity.
        }
    }

    /**
     * Issues a movement command for the current state: collision avoidance when colliding,
     * or the most recently calculated temperature-based velocities otherwise.
     */
    private fun applyMovement() {
        if (colliding) {
            if (searchDirection > 0) drive(-turnSpeed, turnSpeed) else drive(turnSpeed, -turnSpeed)
        } else {
            drive(desiredLeft, desiredRight)
        }
    }

    private fun setDesiredMovement(left: Double, right: Double) {
        desiredLeft  = left
        desiredRight = right
    }

    private fun drive(left: Double, right: Double) {
        val api = robotApi ?: return
        if (api.actuator.leftTrackVelocity == left && api.actuator.rightTrackVelocity == right) return
        api.perform(SetTrackVelocitiesCommand(api.actuator, left, right))
    }
}
