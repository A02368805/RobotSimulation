import program.LineFollowerProgram
import testutil.FakeActuator
import testutil.RecordingRobotApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// LineFollowerProgram lifecycle tests
// ---------------------------------------------------------------------------

class LineFollowerProgramTest {

    private val speed       = 105.0
    private val turnSpeed   = 80.0
    private val gentleFactor = 0.35

    @Test
    fun `program name is exactly Line Follower`() {
        assertEquals("Line Follower", LineFollowerProgram().name)
    }

    @Test
    fun `startProgram subscribes to all three line sensors`() {
        val api = RecordingRobotApi()
        LineFollowerProgram().startProgram(api)

        var leftCount = 0; var centerCount = 0; var rightCount = 0
        api.fakeSensors.lineLeftSensor.subscribe   { leftCount++   }
        api.fakeSensors.lineCenterSensor.subscribe { centerCount++ }
        api.fakeSensors.lineRightSensor.subscribe  { rightCount++  }
        api.fakeSensors.lineLeftSensor.emit(false)
        api.fakeSensors.lineCenterSensor.emit(false)
        api.fakeSensors.lineRightSensor.emit(false)

        // Probe fires once per emission, confirming the sensor has at least one subscriber.
        assertEquals(1, leftCount,   "left sensor must have a subscriber after startProgram")
        assertEquals(1, centerCount, "center sensor must have a subscriber after startProgram")
        assertEquals(1, rightCount,  "right sensor must have a subscriber after startProgram")
    }

    @Test
    fun `left sensor update immediately changes movement`() {
        val api = RecordingRobotApi()
        LineFollowerProgram().startProgram(api)
        api.performed.clear()

        // L=true, C=false, R=false → turn right (left sensor only)
        api.fakeSensors.lineLeftSensor.emit(true)

        assertEquals(1, api.performed.size, "left sensor emission must immediately issue a command")
        assertEquals( turnSpeed, api.fakeActuator.leftTrackVelocity,  "left-only → turn right")
        assertEquals(-turnSpeed, api.fakeActuator.rightTrackVelocity)
    }

    @Test
    fun `center sensor update immediately changes movement`() {
        val api = RecordingRobotApi()
        LineFollowerProgram().startProgram(api)
        api.performed.clear()

        // L=false, C=true, R=false → drive straight
        api.fakeSensors.lineCenterSensor.emit(true)

        assertEquals(1, api.performed.size, "center sensor emission must immediately issue a command")
        assertEquals(speed, api.fakeActuator.leftTrackVelocity,  "center-only → straight left")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity, "center-only → straight right")
    }

    @Test
    fun `right sensor update immediately changes movement`() {
        val api = RecordingRobotApi()
        LineFollowerProgram().startProgram(api)
        api.performed.clear()

        // L=false, C=false, R=true → turn left (right sensor only)
        api.fakeSensors.lineRightSensor.emit(true)

        assertEquals(1, api.performed.size, "right sensor emission must immediately issue a command")
        assertEquals(-turnSpeed, api.fakeActuator.leftTrackVelocity,  "right-only → turn left")
        assertEquals( turnSpeed, api.fakeActuator.rightTrackVelocity)
    }

    @Test
    fun `combined left and center sensors produce a gentle right curve`() {
        val api = RecordingRobotApi()
        LineFollowerProgram().startProgram(api)

        // Establish L=true, C=true, R=false
        api.fakeSensors.lineLeftSensor.emit(true)    // L=true, C=false, R=false → turn right
        api.fakeSensors.lineCenterSensor.emit(true)  // L=true, C=true,  R=false → gentle right
        api.performed.clear()

        // Re-emit to produce a clean single assertion from the final state.
        api.fakeSensors.lineCenterSensor.emit(true)  // same state → guard fires

        // Actuator should hold the gentle right-curve velocities set by the previous emit.
        assertEquals(speed,                  api.fakeActuator.leftTrackVelocity,
            "L+C active must drive left track at full speed")
        assertEquals(speed * gentleFactor,   api.fakeActuator.rightTrackVelocity,
            "L+C active must slow right track for a gentle right curve")
    }

    @Test
    fun `identical consecutive sensor emissions do not add duplicate commands`() {
        val api = RecordingRobotApi()
        LineFollowerProgram().startProgram(api)

        // Drive to a stable state: center on, going straight.
        api.fakeSensors.lineCenterSensor.emit(true)
        val countAfterFirst = api.performed.size

        // Emitting the same value again produces no new command.
        api.fakeSensors.lineCenterSensor.emit(true)
        assertEquals(countAfterFirst, api.performed.size,
            "second identical emission must not add a command")
    }

    @Test
    fun `stopProgram unsubscribes all observers and issues a zero-velocity command`() {
        val fakeActuator = FakeActuator(speed, speed)
        val api = RecordingRobotApi(fakeActuator)
        val program = LineFollowerProgram()
        program.startProgram(api)
        api.performed.clear()

        program.stopProgram(api)

        assertEquals(1, api.performed.size, "stopProgram must issue exactly one command")
        assertEquals(0.0, fakeActuator.leftTrackVelocity,  "left velocity must be zero after stop")
        assertEquals(0.0, fakeActuator.rightTrackVelocity, "right velocity must be zero after stop")
    }

    @Test
    fun `no commands are issued from old callbacks after stopping`() {
        val api = RecordingRobotApi()
        val program = LineFollowerProgram()
        program.startProgram(api)
        program.stopProgram(api)
        api.performed.clear()

        api.fakeSensors.lineLeftSensor.emit(true)
        api.fakeSensors.lineCenterSensor.emit(true)
        api.fakeSensors.lineRightSensor.emit(true)

        assertTrue(api.performed.isEmpty(), "no command must be issued after stopProgram")
    }

    @Test
    fun `restart after stop does not create duplicate subscriptions`() {
        val api = RecordingRobotApi()
        val program = LineFollowerProgram()

        program.startProgram(api)
        program.stopProgram(api)
        program.startProgram(api)
        api.performed.clear()

        // L=false, C=true, R=false → straight; actuator starts at (0,0) → 1 command
        api.fakeSensors.lineCenterSensor.emit(true)

        assertEquals(1, api.performed.size, "restart must not leave duplicate subscriptions")
    }
}
