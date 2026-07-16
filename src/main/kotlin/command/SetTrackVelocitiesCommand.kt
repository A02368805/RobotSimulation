package command

/**
 * Sets both track velocities on the robot (the receiver). Captures the velocities that were
 * in effect immediately before [execute] runs so that [undo] can restore them exactly.
 *
 * Depends only on [RobotActuator] — never on the concrete [model.Robot].
 */
class SetTrackVelocitiesCommand(
    private val actuator: RobotActuator,
    private val targetLeft: Double,
    private val targetRight: Double,
) : Command {

    private var previousLeft = 0.0
    private var previousRight = 0.0

    override fun execute() {
        previousLeft = actuator.leftTrackVelocity
        previousRight = actuator.rightTrackVelocity
        actuator.setTrackVelocities(targetLeft, targetRight)
    }

    override fun undo() {
        actuator.setTrackVelocities(previousLeft, previousRight)
    }
}
