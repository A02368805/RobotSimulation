import command.Command
import command.CommandInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Records every execute() and undo() call in order for LIFO verification. */
private class RecordingCommand(private val id: String, private val log: MutableList<String>) : Command {
    override fun execute() { log.add("exec:$id") }
    override fun undo()    { log.add("undo:$id") }
}

class CommandInvokerTest {

    @Test
    fun `run executes the command and enables undo`() {
        val invoker = CommandInvoker()
        assertFalse(invoker.canUndo())
        val log = mutableListOf<String>()
        invoker.run(RecordingCommand("A", log))
        assertEquals(listOf("exec:A"), log)
        assertTrue(invoker.canUndo())
    }

    @Test
    fun `undo calls the most recently run command`() {
        val invoker = CommandInvoker()
        val log = mutableListOf<String>()
        invoker.run(RecordingCommand("A", log))
        invoker.run(RecordingCommand("B", log))
        invoker.undo()
        assertEquals(listOf("exec:A", "exec:B", "undo:B"), log)
    }

    @Test
    fun `redo re-executes the most recently undone command`() {
        val invoker = CommandInvoker()
        val log = mutableListOf<String>()
        invoker.run(RecordingCommand("A", log))
        invoker.undo()
        assertFalse(invoker.canUndo())
        assertTrue(invoker.canRedo())
        invoker.redo()
        assertEquals(listOf("exec:A", "undo:A", "exec:A"), log)
        assertTrue(invoker.canUndo())
        assertFalse(invoker.canRedo())
    }

    @Test
    fun `undo and redo move commands between stacks`() {
        val invoker = CommandInvoker()
        val log = mutableListOf<String>()
        invoker.run(RecordingCommand("A", log))
        invoker.undo()
        assertTrue(invoker.canRedo())
        invoker.redo()
        assertTrue(invoker.canUndo())
        assertFalse(invoker.canRedo())
    }

    @Test
    fun `running a new command after undo clears redo history`() {
        val invoker = CommandInvoker()
        val log = mutableListOf<String>()
        invoker.run(RecordingCommand("A", log))
        invoker.undo()
        assertTrue(invoker.canRedo())
        invoker.run(RecordingCommand("B", log))
        assertFalse(invoker.canRedo(), "redo stack must be cleared after a new run")
        assertTrue(invoker.canUndo())
    }

    @Test
    fun `empty undo and redo do not throw`() {
        val invoker = CommandInvoker()
        invoker.undo()
        invoker.redo()
        assertFalse(invoker.canUndo())
        assertFalse(invoker.canRedo())
    }

    @Test
    fun `multiple commands undo in strict LIFO order`() {
        val invoker = CommandInvoker()
        val log = mutableListOf<String>()
        invoker.run(RecordingCommand("1", log))
        invoker.run(RecordingCommand("2", log))
        invoker.run(RecordingCommand("3", log))
        invoker.undo()
        invoker.undo()
        invoker.undo()
        assertEquals(
            listOf("exec:1", "exec:2", "exec:3", "undo:3", "undo:2", "undo:1"),
            log,
        )
        assertFalse(invoker.canUndo())
        assertTrue(invoker.canRedo())
    }

    @Test
    fun `redo after multiple undos restores in correct order`() {
        val invoker = CommandInvoker()
        val log = mutableListOf<String>()
        invoker.run(RecordingCommand("1", log))
        invoker.run(RecordingCommand("2", log))
        invoker.undo()
        invoker.undo()
        invoker.redo()
        invoker.redo()
        assertEquals(
            listOf("exec:1", "exec:2", "undo:2", "undo:1", "exec:1", "exec:2"),
            log,
        )
    }
}
