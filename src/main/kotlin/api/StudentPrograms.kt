package api

import program.BallFinderProgram
import program.LineFollowerProgram
import program.TemperatureSeekerProgram

/**
 * The one place programs are registered with the system. Each program you register shows up in the
 * "Program" dropdown and can be launched with "Run Program".
 */
object StudentPrograms {
    fun registerAll(registry: ProgramRegistry) {
        registry.register(LineFollowerProgram())
        registry.register(TemperatureSeekerProgram())
        registry.register(BallFinderProgram())
    }
}
