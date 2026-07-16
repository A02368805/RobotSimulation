import observer.AbstractSubject
import observer.Observer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class TestSubject<T> : AbstractSubject<T>() {
    fun emit(value: T) = notifyObservers(value)
}

class AbstractSubjectTest {

    @Test
    fun `subscribed observer receives the emitted value`() {
        val subject = TestSubject<Int>()
        val received = mutableListOf<Int>()
        subject.subscribe { received.add(it) }
        subject.emit(42)
        assertEquals(listOf(42), received)
    }

    @Test
    fun `two observers both receive values in subscription order`() {
        val subject = TestSubject<String>()
        val log = mutableListOf<String>()
        subject.subscribe { log.add("A:$it") }
        subject.subscribe { log.add("B:$it") }
        subject.emit("x")
        assertEquals(listOf("A:x", "B:x"), log)
    }

    @Test
    fun `subscribing the same observer twice results in one notification`() {
        val subject = TestSubject<Int>()
        var count = 0
        val obs = Observer<Int> { count++ }
        subject.subscribe(obs)
        subject.subscribe(obs)
        subject.emit(1)
        assertEquals(1, count)
    }

    @Test
    fun `unsubscribed observer receives no later values`() {
        val subject = TestSubject<Int>()
        val received = mutableListOf<Int>()
        val obs = Observer<Int> { received.add(it) }
        subject.subscribe(obs)
        subject.emit(1)
        subject.unsubscribe(obs)
        subject.emit(2)
        assertEquals(listOf(1), received)
    }

    @Test
    fun `unsubscribing an absent observer is harmless`() {
        val subject = TestSubject<Int>()
        val obs = Observer<Int> {}
        subject.unsubscribe(obs)
        subject.emit(99)
    }

    @Test
    fun `observer may unsubscribe itself during notification without error and remaining observers still fire`() {
        val subject = TestSubject<Int>()
        val log = mutableListOf<String>()

        lateinit var selfRemover: Observer<Int>
        selfRemover = Observer { value ->
            log.add("self:$value")
            subject.unsubscribe(selfRemover)
        }
        val after = Observer<Int> { log.add("after:$it") }

        subject.subscribe(selfRemover)
        subject.subscribe(after)
        subject.emit(7)

        assertEquals(listOf("self:7", "after:7"), log, "both observers fire on first emission")

        subject.emit(8)
        assertEquals(listOf("self:7", "after:7", "after:8"), log, "selfRemover does not fire after unsubscription")
    }

    @Test
    fun `repeated notifications each deliver to all current subscribers`() {
        val subject = TestSubject<Int>()
        val received = mutableListOf<Int>()
        subject.subscribe { received.add(it) }
        subject.emit(1)
        subject.emit(2)
        subject.emit(3)
        assertEquals(listOf(1, 2, 3), received)
    }
}
