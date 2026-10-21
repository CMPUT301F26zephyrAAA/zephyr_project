package com.example.zephyrcampsites

/**
 * Interface to inherit from for Views.
 */
interface ViewObserver<M> {
    fun update( model: M)
}