package command

/**
 * The Invoker. Runs commands and keeps an undo/redo history using two LIFO stacks.
 * [run] executes and records; [undo]/[redo] walk the history in either direction.
 */
class CommandInvoker {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    fun run(command: Command) {
        command.execute()
        undoStack.addLast(command)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val command = undoStack.removeLast()
        command.undo()
        redoStack.addLast(command)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val command = redoStack.removeLast()
        command.execute()
        undoStack.addLast(command)
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
}
