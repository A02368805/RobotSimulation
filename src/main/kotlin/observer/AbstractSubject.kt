package observer

/**
 * Reusable base implementation of [Subject] — the heart of the Observer pattern.
 * Every sensor extends this class so that any sensor can be subscribed to.
 */
abstract class AbstractSubject<T> : Subject<T> {

    private val observers = mutableListOf<Observer<T>>()

    override fun subscribe(observer: Observer<T>) {
        if (observer !in observers) observers.add(observer)
    }

    override fun unsubscribe(observer: Observer<T>) {
        observers.remove(observer)
    }

    override fun notifyObservers(value: T) {
        observers.toList().forEach { it.onUpdate(value) }
    }
}
