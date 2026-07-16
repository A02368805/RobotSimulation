package program

import api.RobotApi
import api.RobotProgram
import command.SetTrackVelocitiesCommand
import observer.Observer

/**
 * Climbs the temperature gradient by tracking sensor readings through the collision observer,
 * which is the sole control-loop heartbeat.
 *
 * The temperature observer only shifts and caches readings; no movement decision is made
 * there. The collision observer caches its reading and then calls [updateMovement], which
 * applies the full priority chain once per tick with all cached values current.
 */
class TemperatureSeekerProgram : RobotProgram {

    override val name = "Temperature Seeker"

    private val speed = 100.0
    private val turnSpeed = 75.0
    private val hotThreshold = 92.0
    private val epsilon = 0.05
    private val curveFactor = 0.25

    private var robotApi: RobotApi? = null

    private var currentTemperature: Double? = null
    private var previousTemperature: Double? = null
    private var colliding = false
    private var searchDirection = 1  // 1 = right arc/pivot, -1 = left arc/pivot

    private var temperatureObserver: Observer<Double>? = null
    private var collisionObserver: Observer<Boolean>? = null

    override fun startProgram(robot: RobotApi) {
        robotApi = robot
        currentTemperature = null
        previousTemperature = null
        colliding = false
        searchDirection = 1

        val to = Observer<Double> { value ->
            previousTemperature = currentTemperature
            currentTemperature = value
        }
        val co = Observer<Boolean> { value ->
            colliding = value
            updateMovement()
        }

        temperatureObserver = to
        collisionObserver = co

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
        collisionObserver = null
        robotApi = null
    }

    private fun updateMovement() {
        if (colliding) {
            if (searchDirection > 0) drive(-turnSpeed, turnSpeed) else drive(turnSpeed, -turnSpeed)
            return
        }

        val curr = currentTemperature ?: run { drive(speed, speed); return }

        if (curr >= hotThreshold) {
            drive(0.0, 0.0)
            return
        }

        val prev = previousTemperature ?: run { drive(speed, speed); return }

        val delta = curr - prev
        when {
            delta > epsilon  -> drive(speed, speed)
            delta < -epsilon -> {
                if (searchDirection > 0) drive(speed * curveFactor, speed)
                else                     drive(speed, speed * curveFactor)
                searchDirection *= -1
            }
            // Effectively equal: retain current velocity — no duplicate command issued.
        }
    }

    private fun drive(left: Double, right: Double) {
        val api = robotApi ?: return
        if (api.actuator.leftTrackVelocity == left && api.actuator.rightTrackVelocity == right) return
        api.perform(SetTrackVelocitiesCommand(api.actuator, left, right))
    }
}
