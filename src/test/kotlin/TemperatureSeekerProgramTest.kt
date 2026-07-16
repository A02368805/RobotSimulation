import program.TemperatureSeekerProgram
import testutil.FakeActuator
import testutil.RecordingRobotApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemperatureSeekerProgramTest {

    // Helpers
    private val speed = 100.0
    private val turnSpeed = 75.0
    private val curveFactor = 0.25

    /** Emit two temperatures so previousTemperature and currentTemperature are both set. */
    private fun RecordingRobotApi.warmUp(prev: Double, curr: Double) {
        fakeSensors.temperature.emit(prev)
        fakeSensors.temperature.emit(curr)
    }

    // -----------------------------------------------------------------------

    @Test
    fun `program name is exactly Temperature Seeker`() {
        assertEquals("Temperature Seeker", TemperatureSeekerProgram().name)
    }

    @Test
    fun `startProgram subscribes to temperature and collision sensors`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)

        var tempFired = 0
        var collFired = 0
        api.fakeSensors.temperature.subscribe { tempFired++ }
        api.fakeSensors.collisionSensor.subscribe { collFired++ }
        api.fakeSensors.temperature.emit(20.0)
        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, tempFired,  "temperature sensor must have subscribers after startProgram")
        assertEquals(1, collFired,  "collision sensor must have subscribers after startProgram")
    }

    @Test
    fun `temperature callback alone does not issue a command`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.performed.clear()

        api.fakeSensors.temperature.emit(25.0)

        assertTrue(api.performed.isEmpty(), "temperature-only emission must not trigger a command")
    }

    @Test
    fun `collision callback is the heartbeat and triggers a movement decision`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, api.performed.size, "first collision callback must issue exactly one command")
    }

    @Test
    fun `warming drives forward`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.warmUp(20.0, 21.0)   // delta = +1.0 > epsilon → warming
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, api.performed.size)
        assertEquals(speed,  api.fakeActuator.leftTrackVelocity,  "warming must set left to speed")
        assertEquals(speed,  api.fakeActuator.rightTrackVelocity, "warming must set right to speed")
    }

    @Test
    fun `cooling produces the search arc`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.warmUp(21.0, 20.0)   // delta = -1.0 < -epsilon → cooling; searchDirection=1 → right arc
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, api.performed.size)
        assertEquals(speed * curveFactor, api.fakeActuator.leftTrackVelocity,
            "cooling with searchDirection=1 must slow the left track")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity,
            "cooling with searchDirection=1 must maintain right track at speed")
    }

    @Test
    fun `collision takes priority over warming`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.warmUp(20.0, 21.0)   // warming scenario — would normally drive forward
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(true)   // colliding; searchDirection=1 → pivot right

        assertEquals(1, api.performed.size)
        assertEquals(-turnSpeed, api.fakeActuator.leftTrackVelocity,
            "collision must pivot right (left track backward)")
        assertEquals(turnSpeed,  api.fakeActuator.rightTrackVelocity,
            "collision must pivot right (right track forward)")
    }

    @Test
    fun `hot threshold stops the robot`() {
        // Pre-set actuator to a moving state so the stop command is a real state change and
        // is not silently dropped by the identical-velocity guard.
        val fakeActuator = FakeActuator(speed, speed)
        val api = RecordingRobotApi(fakeActuator)
        TemperatureSeekerProgram().startProgram(api)
        api.warmUp(88.0, 93.0)   // curr=93 >= 92 → hot threshold
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, api.performed.size)
        assertEquals(0.0, fakeActuator.leftTrackVelocity,  "hot threshold must stop left track")
        assertEquals(0.0, fakeActuator.rightTrackVelocity, "hot threshold must stop right track")
    }

    @Test
    fun `identical consecutive heartbeats do not add duplicate commands`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.warmUp(20.0, 21.0)

        api.fakeSensors.collisionSensor.emit(false)  // issues forward command
        val countAfterFirst = api.performed.size

        api.fakeSensors.collisionSensor.emit(false)  // same state → guard must skip
        assertEquals(countAfterFirst, api.performed.size,
            "second identical heartbeat must not add a command")
    }

    @Test
    fun `stopProgram unsubscribes observers and issues a zero-velocity command`() {
        val fakeActuator = FakeActuator(speed, speed)
        val api = RecordingRobotApi(fakeActuator)
        val program = TemperatureSeekerProgram()
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
        val program = TemperatureSeekerProgram()
        program.startProgram(api)
        program.stopProgram(api)
        api.performed.clear()

        api.fakeSensors.temperature.emit(30.0)
        api.fakeSensors.collisionSensor.emit(false)

        assertTrue(api.performed.isEmpty(), "no command must be issued after stopProgram")
    }

    @Test
    fun `restart after stop does not create duplicate subscriptions`() {
        val api = RecordingRobotApi()
        val program = TemperatureSeekerProgram()

        program.startProgram(api)
        program.stopProgram(api)
        program.startProgram(api)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, api.performed.size, "restart must not leave duplicate subscriptions")
    }
}
