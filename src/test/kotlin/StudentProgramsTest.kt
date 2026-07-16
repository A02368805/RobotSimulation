import api.DefaultProgramRegistry
import api.StudentPrograms
import kotlin.test.Test
import kotlin.test.assertEquals

class StudentProgramsTest {

    private fun freshRegistry() = DefaultProgramRegistry().also { StudentPrograms.registerAll(it) }

    @Test
    fun `exactly three programs are registered`() {
        assertEquals(3, freshRegistry().programs().size)
    }

    @Test
    fun `program names are correct and in order`() {
        val names = freshRegistry().programs().map { it.name }
        assertEquals(listOf("Line Follower", "Temperature Seeker", "Ball Finder"), names)
    }

    @Test
    fun `calling registerAll once produces no unexpected program types`() {
        val programs = freshRegistry().programs()
        assertEquals("Line Follower",      programs[0].name)
        assertEquals("Temperature Seeker", programs[1].name)
        assertEquals("Ball Finder",        programs[2].name)
    }

}
