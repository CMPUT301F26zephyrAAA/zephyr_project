package com.example.zephyrcampsites

/**
 * Model superclass to inherit from.
 * Made with type parameters, so class who inherits can/needs to specify which model class to use.
 *
 * @property observers The list of views that are observing the model.
 */
open class ObservableModel<M> {
    private val observers = mutableListOf<ViewObserver<M>>()

    // Adds observer to model
    fun addObserver(o: ViewObserver<M>) {
        observers.add(o)
    }

    // Removes observer from model
    fun removeObserver(o: ViewObserver<M>) {
        observers.remove(o)
    }

    // Tells the model to notify the observers
    protected fun notifyObservers(model: M) {
        for (i in observers) {
            i.update(model)
        }
    }
}