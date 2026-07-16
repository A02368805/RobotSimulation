import javafx.scene.paint.Color
import program.BallFinderProgram
import testutil.FakeActuator
import testutil.RecordingRobotApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Color constants for readable test scenarios
// ---------------------------------------------------------------------------

private val TARGET_RED      = Color.color(0.9, 0.1, 0.1)   // strongly red
private val BLOCKING_GRAY   = Color.color(0.4, 0.4, 0.4)   // neutral mid-brightness
private val FLOOR_DARK      = Color.BLACK                   // low brightness → explore

// ---------------------------------------------------------------------------
// BallFinderProgram lifecycle tests
// ---------------------------------------------------------------------------

class BallFinderProgramTest {

    private val speed       = 105.0
    private val turnSpeed   = 85.0
    private val safeDistance = 70.0

    // Helpers -----------------------------------------------------------------

    /** Bring the program to a stable forward-driving state so the actuator is not at rest. */
    private fun RecordingRobotApi.driveForwardTick() {
        fakeSensors.sonarSensor.emit(safeDistance + 50.0)
        fakeSensors.visionSensor.emit(FLOOR_DARK)
        fakeSensors.collisionSensor.emit(false)
    }

    // Tests -------------------------------------------------------------------

    @Test
    fun `program name is exactly Ball Finder`() {
        assertEquals("Ball Finder", BallFinderProgram().name)
    }

    @Test
    fun `startProgram subscribes to vision, sonar, and collision`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)

        var visionFired = 0; var sonarFired = 0; var collFired = 0
        api.fakeSensors.visionSensor.subscribe  { visionFired++ }
        api.fakeSensors.sonarSensor.subscribe   { sonarFired++  }
        api.fakeSensors.collisionSensor.subscribe { collFired++ }
        api.fakeSensors.visionSensor.emit(FLOOR_DARK)
        api.fakeSensors.sonarSensor.emit(200.0)
        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, visionFired, "vision sensor must have a subscriber")
        assertEquals(1, sonarFired,  "sonar sensor must have a subscriber")
        assertEquals(1, collFired,   "collision sensor must have a subscriber")
    }

    @Test
    fun `vision callback alone does not issue a command`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.performed.clear()

        api.fakeSensors.visionSensor.emit(TARGET_RED)

        assertTrue(api.performed.isEmpty(), "vision-only emission must not trigger a command")
    }

    @Test
    fun `sonar callback alone does not issue a command`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.performed.clear()

        api.fakeSensors.sonarSensor.emit(10.0)

        assertTrue(api.performed.isEmpty(), "sonar-only emission must not trigger a command")
    }

    @Test
    fun `collision callback is the heartbeat and triggers a movement decision`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, api.performed.size, "collision callback must issue exactly one command")
    }

    @Test
    fun `collision state causes a right pivot`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(true)

        assertEquals(1, api.performed.size)
        assertEquals(-turnSpeed, api.fakeActuator.leftTrackVelocity,  "left track must be backward for pivot right")
        assertEquals( turnSpeed, api.fakeActuator.rightTrackVelocity, "right track must be forward for pivot right")
    }

    @Test
    fun `sonar at or below safe distance causes a right pivot`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.fakeSensors.sonarSensor.emit(safeDistance)   // exactly at threshold
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)      // not colliding, but sonar ≤ safeDistance

        assertEquals(1, api.performed.size)
        assertEquals(-turnSpeed, api.fakeActuator.leftTrackVelocity)
        assertEquals( turnSpeed, api.fakeActuator.rightTrackVelocity)
    }

    @Test
    fun `collision avoidance has priority over red vision`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.fakeSensors.visionSensor.emit(TARGET_RED)        // sees ball
        api.fakeSensors.sonarSensor.emit(safeDistance - 1.0) // too close
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)          // not colliding but sonar blocked

        assertEquals(-turnSpeed, api.fakeActuator.leftTrackVelocity,
            "obstacle avoidance must override ball-red vision")
        assertEquals( turnSpeed, api.fakeActuator.rightTrackVelocity)
    }

    @Test
    fun `red target with clear path drives forward`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.fakeSensors.sonarSensor.emit(safeDistance + 50.0)
        api.fakeSensors.visionSensor.emit(TARGET_RED)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(speed, api.fakeActuator.leftTrackVelocity,  "red target must drive forward left")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity, "red target must drive forward right")
    }

    @Test
    fun `blocking surface causes a right pivot`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.fakeSensors.sonarSensor.emit(safeDistance + 50.0)
        api.fakeSensors.visionSensor.emit(BLOCKING_GRAY)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(-turnSpeed, api.fakeActuator.leftTrackVelocity,
            "blocking surface must pivot right")
        assertEquals( turnSpeed, api.fakeActuator.rightTrackVelocity)
    }

    @Test
    fun `open floor view drives forward to explore`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.fakeSensors.sonarSensor.emit(safeDistance + 50.0)
        api.fakeSensors.visionSensor.emit(FLOOR_DARK)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(speed, api.fakeActuator.leftTrackVelocity,  "open view must explore forward left")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity, "open view must explore forward right")
    }

    @Test
    fun `identical consecutive heartbeats do not add duplicate commands`() {
        val api = RecordingRobotApi()
        BallFinderProgram().startProgram(api)
        api.driveForwardTick()                          // actuator now at (speed, speed)
        val countAfterFirst = api.performed.size

        api.fakeSensors.collisionSensor.emit(false)     // same state, same desired velocity
        assertEquals(countAfterFirst, api.performed.size,
            "second identical heartbeat must not add a command")
    }

    @Test
    fun `stopProgram unsubscribes all observers and issues a zero-velocity command`() {
        val fakeActuator = FakeActuator(speed, speed)   // robot is moving
        val api = RecordingRobotApi(fakeActuator)
        val program = BallFinderProgram()
        program.startProgram(api)
        api.performed.clear()

        program.stopProgram(api)

        assertEquals(1, api.performed.size,          "stopProgram must issue exactly one command")
        assertEquals(0.0, fakeActuator.leftTrackVelocity,  "left must be zero after stop")
        assertEquals(0.0, fakeActuator.rightTrackVelocity, "right must be zero after stop")
    }

    @Test
    fun `no commands are issued from old callbacks after stopping`() {
        val api = RecordingRobotApi()
        val program = BallFinderProgram()
        program.startProgram(api)
        program.stopProgram(api)
        api.performed.clear()

        api.fakeSensors.visionSensor.emit(TARGET_RED)
        api.fakeSensors.sonarSensor.emit(10.0)
        api.fakeSensors.collisionSensor.emit(true)

        assertTrue(api.performed.isEmpty(), "no command must be issued after stopProgram")
    }

    @Test
    fun `restart after stop does not create duplicate subscriptions`() {
        val api = RecordingRobotApi()
        val program = BallFinderProgram()
        program.startProgram(api)
        program.stopProgram(api)
        program.startProgram(api)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, api.performed.size, "restart must not leave duplicate subscriptions")
    }
}
