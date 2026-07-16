import command.RobotActuator
import command.SetTrackVelocitiesCommand
import testutil.FakeActuator
import kotlin.test.Test
import kotlin.test.assertEquals

class SetTrackVelocitiesCommandTest {

    @Test
    fun `execute sets both target velocities on the actuator`() {
        val actuator = FakeActuator()
        val cmd = SetTrackVelocitiesCommand(actuator, 100.0, 80.0)
        cmd.execute()
        assertEquals(100.0, actuator.leftTrackVelocity)
        assertEquals(80.0, actuator.rightTrackVelocity)
    }

    @Test
    fun `undo restores the velocities that were present before execute`() {
        val actuator = FakeActuator(leftTrackVelocity = 50.0, rightTrackVelocity = 50.0)
        val cmd = SetTrackVelocitiesCommand(actuator, 100.0, -100.0)
        cmd.execute()
        cmd.undo()
        assertEquals(50.0, actuator.leftTrackVelocity)
        assertEquals(50.0, actuator.rightTrackVelocity)
    }

    @Test
    fun `execute captures state immediately before each invocation so redo works correctly`() {
        val actuator = FakeActuator()
        // First command: 0,0 -> 60,60
        val cmd1 = SetTrackVelocitiesCommand(actuator, 60.0, 60.0)
        cmd1.execute()
        // Second command while actuator is at 60,60: -> 90,90
        val cmd2 = SetTrackVelocitiesCommand(actuator, 90.0, 90.0)
        cmd2.execute()
        // Undo second: should restore 60,60
        cmd2.undo()
        assertEquals(60.0, actuator.leftTrackVelocity)
        assertEquals(60.0, actuator.rightTrackVelocity)
        // Re-execute second (redo): capture again from 60,60 and set 90,90
        cmd2.execute()
        assertEquals(90.0, actuator.leftTrackVelocity)
        assertEquals(90.0, actuator.rightTrackVelocity)
        // Undo again: should restore 60,60
        cmd2.undo()
        assertEquals(60.0, actuator.leftTrackVelocity)
        assertEquals(60.0, actuator.rightTrackVelocity)
    }

    @Test
    fun `command depends only on RobotActuator not on concrete Robot`() {
        // This test compiles only if SetTrackVelocitiesCommand accepts RobotActuator.
        // Using FakeActuator (which is not model.Robot) proves the dependency is on the interface.
        val actuator: RobotActuator = FakeActuator(10.0, 20.0)
        val cmd = SetTrackVelocitiesCommand(actuator, 0.0, 0.0)
        cmd.execute()
        assertEquals(0.0, actuator.leftTrackVelocity)
        assertEquals(0.0, actuator.rightTrackVelocity)
        cmd.undo()
        assertEquals(10.0, actuator.leftTrackVelocity)
        assertEquals(20.0, actuator.rightTrackVelocity)
    }
}
