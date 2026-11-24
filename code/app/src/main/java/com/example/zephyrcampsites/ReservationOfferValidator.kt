
package com.example.zephyrcampsites

/**
 * Validates whether an entrant may decline a lottery-winning offer.
 */
object ReservationOfferValidator {

    fun canDecline(
        drawCompleted: Boolean,
        entrantStatus: String,
        alreadyDeclined: Boolean
    ): Boolean {
        return drawCompleted &&
                entrantStatus == "WINNER" &&
                !alreadyDeclined
    }
}
