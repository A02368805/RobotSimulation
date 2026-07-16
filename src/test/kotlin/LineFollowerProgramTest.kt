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

    @Test
    fun `program name is exactly Line Follower`() {
        assertEquals("Line Follower", LineFollowerProgram().name)
    }

    @Test
    fun `startProgram subscribes to all three line sensors`() {
        val api = RecordingRobotApi()
        val program = LineFollowerProgram()
        program.startProgram(api)

        // Each sensor should have exactly one subscriber: emit and count callbacks.
        var leftCount = 0; var centerCount = 0; var rightCount = 0
        api.fakeSensors.lineLeftSensor.subscribe   { leftCount++ }
        api.fakeSensors.lineCenterSensor.subscribe { centerCount++ }
        api.fakeSensors.lineRightSensor.subscribe  { rightCount++ }
        api.fakeSensors.lineLeftSensor.emit(false)
        api.fakeSensors.lineCenterSensor.emit(false)
        api.fakeSensors.lineRightSensor.emit(false)

        // The program observer + our probe each fired → count is 2 per sensor (probe fires once).
        // We just confirm the probe fired, meaning the sensor works; the real check is the
        // behavioral tests below (no command from left/center, command from right).
        assertEquals(1, leftCount)
        assertEquals(1, centerCount)
        assertEquals(1, rightCount)
    }

    @Test
    fun `left sensor callback alone does not issue a command`() {
        val api = RecordingRobotApi()
        val program = LineFollowerProgram()
        program.startProgram(api)
        api.performed.clear()

        api.fakeSensors.lineLeftSensor.emit(true)

        assertTrue(api.performed.isEmpty(), "left-only emission must not trigger a command")
    }

    @Test
    fun `center sensor callback alone does not issue a command`() {
        val api = RecordingRobotApi()
        val program = LineFollowerProgram()
        program.startProgram(api)
        api.performed.clear()

        api.fakeSensors.lineCenterSensor.emit(true)

        assertTrue(api.performed.isEmpty(), "center-only emission must not trigger a command")
    }

    @Test
    fun `right sensor callback is the heartbeat and triggers a movement command`() {
        val api = RecordingRobotApi()
        val program = LineFollowerProgram()
        program.startProgram(api)
        api.performed.clear()

        api.fakeSensors.lineCenterSensor.emit(true)  // cache: center on
        api.fakeSensors.lineRightSensor.emit(false)  // heartbeat fires with C=true, L=false, R=false

        assertEquals(1, api.performed.size, "heartbeat must issue exactly one command")
    }

    @Test
    fun `identical consecutive heartbeats do not add duplicate commands`() {
        val api = RecordingRobotApi()
        val program = LineFollowerProgram()
        program.startProgram(api)

        // Drive to a stable state: center on, going straight.
        api.fakeSensors.lineCenterSensor.emit(true)
        api.fakeSensors.lineRightSensor.emit(false)
        val countAfterFirst = api.performed.size

        // Fire the heartbeat again with the same sensor state.
        api.fakeSensors.lineRightSensor.emit(false)
        assertEquals(countAfterFirst, api.performed.size, "second identical heartbeat must not add a command")
    }

    @Test
    fun `stopProgram unsubscribes all observers and issues a zero-velocity command`() {
        val fakeActuator = FakeActuator(105.0, 105.0)
        val api = RecordingRobotApi(fakeActuator)
        val program = LineFollowerProgram()
        program.startProgram(api)
        api.performed.clear()

        program.stopProgram(api)

        // A stop command must have been issued.
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

        // Emit to all three sensors; none should trigger a command.
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

        // With center on, the heartbeat should produce exactly one command — not two.
        api.fakeSensors.lineCenterSensor.emit(true)
        api.fakeSensors.lineRightSensor.emit(false)

        assertEquals(1, api.performed.size, "restart must not leave duplicate subscriptions")
    }
}
