import program.TemperatureSeekerProgram
import testutil.FakeActuator
import testutil.RecordingRobotApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemperatureSeekerProgramTest {

    private val speed        = 100.0
    private val turnSpeed    = 75.0
    private val curveFactor  = 0.25

    /**
     * Emit two consecutive temperature readings so both previousTemperature and
     * currentTemperature are set, giving a meaningful delta on the second reading.
     */
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

        var tempFired = 0; var collFired = 0
        api.fakeSensors.temperature.subscribe        { tempFired++ }
        api.fakeSensors.collisionSensor.subscribe    { collFired++ }
        api.fakeSensors.temperature.emit(20.0)
        api.fakeSensors.collisionSensor.emit(false)

        assertEquals(1, tempFired,  "temperature sensor must have a subscriber after startProgram")
        assertEquals(1, collFired,  "collision sensor must have a subscriber after startProgram")
    }

    @Test
    fun `temperature update immediately produces a movement command`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.performed.clear()

        api.fakeSensors.temperature.emit(25.0)  // first reading, no prev → desired = forward

        assertEquals(1, api.performed.size, "temperature emission must immediately issue a command")
        assertEquals(speed, api.fakeActuator.leftTrackVelocity,  "first reading must drive forward left")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity, "first reading must drive forward right")
    }

    @Test
    fun `collision update immediately triggers a movement response`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(false)  // applies current desired velocity

        assertEquals(1, api.performed.size, "collision emission must immediately issue a command")
    }

    @Test
    fun `warming drives forward`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)

        api.warmUp(20.0, 21.0)  // delta = +1.0 > epsilon → desired = forward

        assertEquals(speed, api.fakeActuator.leftTrackVelocity,  "warming must drive forward left")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity, "warming must drive forward right")
    }

    @Test
    fun `cooling produces the search arc`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)

        api.warmUp(21.0, 20.0)  // delta = -1.0 < -epsilon, searchDirection=1 → right arc

        assertEquals(speed * curveFactor, api.fakeActuator.leftTrackVelocity,
            "cooling with searchDirection=1 must slow the left track")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity,
            "cooling with searchDirection=1 must maintain right track at speed")
    }

    @Test
    fun `collision immediately overrides temperature movement`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.warmUp(20.0, 21.0)   // warming → desired = (speed, speed), actuator at (speed, speed)
        api.performed.clear()

        api.fakeSensors.collisionSensor.emit(true)   // colliding, searchDirection=1 → pivot right

        assertEquals(1, api.performed.size)
        assertEquals(-turnSpeed, api.fakeActuator.leftTrackVelocity,
            "collision must pivot right (left track backward)")
        assertEquals( turnSpeed, api.fakeActuator.rightTrackVelocity,
            "collision must pivot right (right track forward)")
    }

    @Test
    fun `clearing collision immediately resumes temperature-based movement`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)
        api.warmUp(20.0, 21.0)  // desired = forward

        api.fakeSensors.collisionSensor.emit(true)   // start colliding → pivot
        api.fakeSensors.collisionSensor.emit(false)  // clear collision → resume desired

        assertEquals(speed, api.fakeActuator.leftTrackVelocity,
            "clearing collision must resume temperature-based forward movement")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity)
    }

    @Test
    fun `collision callbacks do not alter the temperature search direction`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)

        // First cooling: searchDirection 1 → -1
        api.warmUp(21.0, 20.0)

        // Multiple collision callbacks must not touch searchDirection
        api.fakeSensors.collisionSensor.emit(true)
        api.fakeSensors.collisionSensor.emit(false)

        // Second cooling with searchDirection still -1: arc uses right track slow
        api.fakeSensors.temperature.emit(19.0)  // prev=20, delta=-1 < -epsilon, direction=-1

        assertEquals(speed, api.fakeActuator.leftTrackVelocity,
            "second cooling with direction=-1 must keep left track at speed")
        assertEquals(speed * curveFactor, api.fakeActuator.rightTrackVelocity,
            "second cooling with direction=-1 must slow right track")
    }

    @Test
    fun `repeated equal temperature readings do not flip search direction`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)

        // First cooling: searchDirection 1 → -1, desired = (speed*curveFactor, speed)
        api.warmUp(21.0, 20.0)

        // Repeated identical reading: delta = 0, no direction change
        api.fakeSensors.temperature.emit(20.0)

        // Desired should still be the arc set by the first cooling
        assertEquals(speed * curveFactor, api.fakeActuator.leftTrackVelocity,
            "equal reading must not alter the search-arc velocities")
        assertEquals(speed, api.fakeActuator.rightTrackVelocity)
    }

    @Test
    fun `hot threshold stops the robot`() {
        val fakeActuator = FakeActuator(speed, speed)  // robot is moving
        val api = RecordingRobotApi(fakeActuator)
        TemperatureSeekerProgram().startProgram(api)

        api.fakeSensors.temperature.emit(88.0)   // below threshold
        api.fakeSensors.temperature.emit(93.0)   // curr=93 >= 92 → hot threshold → desired = (0, 0)

        assertEquals(0.0, fakeActuator.leftTrackVelocity,  "hot threshold must stop left track")
        assertEquals(0.0, fakeActuator.rightTrackVelocity, "hot threshold must stop right track")
    }

    @Test
    fun `identical consecutive sensor emissions do not add duplicate commands`() {
        val api = RecordingRobotApi()
        TemperatureSeekerProgram().startProgram(api)

        api.fakeSensors.temperature.emit(20.0)     // first reading → desired = forward, 1 cmd
        val countAfterFirst = api.performed.size   // = 1

        api.fakeSensors.temperature.emit(20.0)     // delta = 0 → same desired → guard fires
        assertEquals(countAfterFirst, api.performed.size,
            "repeated equal temperature reading must not add a command")
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

        api.fakeSensors.collisionSensor.emit(false)  // desired=(speed,speed), actuator at (0,0) → 1 cmd

        assertEquals(1, api.performed.size, "restart must not leave duplicate subscriptions")
    }
}
