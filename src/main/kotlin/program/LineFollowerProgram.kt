package program

import api.RobotApi
import api.RobotProgram
import command.SetTrackVelocitiesCommand
import observer.Observer

/**
 * Follows a floor line using three line sensors (left / center / right).
 *
 * The right-sensor observer is the sole control-loop heartbeat: it caches its reading and
 * calls [updateMovement]. The left and center observers only cache their readings so that
 * all three values are current when the heartbeat fires.
 */
class LineFollowerProgram : RobotProgram {

    override val name = "Line Follower"

    private val speed = 105.0
    private val turnSpeed = 80.0
    private val gentleFactor = 0.35

    private var robotApi: RobotApi? = null

    private var leftOnLine = false
    private var centerOnLine = false
    private var rightOnLine = false

    private var leftObserver: Observer<Boolean>? = null
    private var centerObserver: Observer<Boolean>? = null
    private var rightObserver: Observer<Boolean>? = null

    override fun startProgram(robot: RobotApi) {
        robotApi = robot

        val lo = Observer<Boolean> { leftOnLine = it }
        val co = Observer<Boolean> { centerOnLine = it }
        val ro = Observer<Boolean> { rightOnLine = it; updateMovement() }

        leftObserver = lo
        centerObserver = co
        rightObserver = ro

        robot.sensors.lineLeft.subscribe(lo)
        robot.sensors.lineCenter.subscribe(co)
        robot.sensors.lineRight.subscribe(ro)
    }

    override fun stopProgram(robot: RobotApi) {
        val lo = leftObserver
        val co = centerObserver
        val ro = rightObserver
        if (lo != null) robot.sensors.lineLeft.unsubscribe(lo)
        if (co != null) robot.sensors.lineCenter.unsubscribe(co)
        if (ro != null) robot.sensors.lineRight.unsubscribe(ro)

        val api = robotApi
        if (api != null) {
            api.perform(SetTrackVelocitiesCommand(api.actuator, 0.0, 0.0))
        }

        leftObserver = null
        centerObserver = null
        rightObserver = null
        robotApi = null
    }

    private fun updateMovement() {
        val api = robotApi ?: return
        when {
            !leftOnLine &&  centerOnLine && !rightOnLine -> drive(speed, speed)
            leftOnLine  &&  centerOnLine && !rightOnLine -> drive(speed, speed * gentleFactor)
            !leftOnLine &&  centerOnLine &&  rightOnLine -> drive(speed * gentleFactor, speed)
            leftOnLine  && !centerOnLine && !rightOnLine -> drive(turnSpeed, -turnSpeed)
            !leftOnLine && !centerOnLine &&  rightOnLine -> drive(-turnSpeed, turnSpeed)
            leftOnLine  &&  centerOnLine &&  rightOnLine -> drive(speed, speed)
            else -> {
                // All sensors off: continue the last inferred turn direction.
                val curLeft = api.actuator.leftTrackVelocity
                val curRight = api.actuator.rightTrackVelocity
                if (curRight > curLeft) drive(-turnSpeed, turnSpeed) else drive(turnSpeed, -turnSpeed)
            }
        }
    }

    private fun drive(left: Double, right: Double) {
        val api = robotApi ?: return
        if (api.actuator.leftTrackVelocity == left && api.actuator.rightTrackVelocity == right) return
        api.perform(SetTrackVelocitiesCommand(api.actuator, left, right))
    }
}
