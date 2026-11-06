package com.example.zephyrcampsites

object WaitingListValidator {

    fun canJoin(
        currentTime: Long,
        registrationStart: Long,
        registrationEnd: Long,
        alreadyJoined: Boolean
    ): Boolean {
        return currentTime >= registrationStart &&
                currentTime < registrationEnd &&
                !alreadyJoined
    }

    fun hasCapacity(
        currentCount: Int,
        capacity: Int?
    ): Boolean {
        return capacity == null || currentCount < capacity
    }
}