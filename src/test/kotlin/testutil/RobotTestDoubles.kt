package testutil

import api.RobotApi
import command.Command
import command.RobotActuator
import environment.Environment
import geometry.Pose
import javafx.scene.paint.Color
import sensor.RobotSensors
import sensor.Sensor

/**
 * Reusable program test doubles shared across all robot-program lifecycle tests.
 *
 * Kept in a dedicated package so the default test package stays free of shared state,
 * and so future program tests only need `import testutil.*`.
 */

internal class FakeActuator(
    override var leftTrackVelocity: Double = 0.0,
    override var rightTrackVelocity: Double = 0.0,
) : RobotActuator {
    override fun setTrackVelocities(left: Double, right: Double) {
        leftTrackVelocity = left
        rightTrackVelocity = right
    }
}

internal class FakeSensor<T>(private val defaultValue: T) : Sensor<T>("fake", 0.0) {
    override fun measure(env: Environment, sensorPose: Pose): T = defaultValue
    fun emit(v: T) = notifyObservers(v)
}

internal class FakeRobotSensors : RobotSensors {
    val lineLeftSensor   = FakeSensor(false)
    val lineCenterSensor = FakeSensor(false)
    val lineRightSensor  = FakeSensor(false)
    val visionSensor     = FakeSensor(Color.BLACK)
    val sonarSensor      = FakeSensor(999.0)
    val collisionSensor  = FakeSensor(false)
    override val sonar:       Sensor<Double>  get() = sonarSensor
    override val vision:      Sensor<Color>   get() = visionSensor
    override val temperature              = FakeSensor(20.0)
    override val lineLeft:    Sensor<Boolean> get() = lineLeftSensor
    override val lineCenter:  Sensor<Boolean> get() = lineCenterSensor
    override val lineRight:   Sensor<Boolean> get() = lineRightSensor
    override val collision:   Sensor<Boolean> get() = collisionSensor
}

internal class RecordingRobotApi(
    val fakeActuator: FakeActuator = FakeActuator(),
    val fakeSensors: FakeRobotSensors = FakeRobotSensors(),
) : RobotApi {
    val performed = mutableListOf<Command>()
    override val actuator: RobotActuator get() = fakeActuator
    override val sensors:  RobotSensors  get() = fakeSensors
    override fun perform(command: Command) { command.execute(); performed.add(command) }
    override fun perform(commands: List<Command>) = commands.forEach { perform(it) }
    override fun undo() {}
    override fun redo() {}
}
