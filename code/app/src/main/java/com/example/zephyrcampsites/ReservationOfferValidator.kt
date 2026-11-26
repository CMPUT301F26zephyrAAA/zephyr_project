package com.example.zephyrcampsites

// AI Citation [AI-25]
// OpenAI, ChatGPT assisted with reservation offer
// decline eligibility validation.
// AI usage and citations documented in the GitHub Wiki.

/**
 * Validates lottery reservation offer actions.
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

    fun canPromote(
        registrationExists: Boolean,
        registrationMatches: Boolean,
        entrantStatus: String?,
        accountExists: Boolean,
        deletionPending: Boolean
    ): Boolean {
        return registrationExists &&
                registrationMatches &&
                entrantStatus == "BACKUP" &&
                accountExists &&
                !deletionPending
    }

    fun canIssueReplacementOffer(
        currentWinnerCount: Int,
        numberOfWinners: Long
    ): Boolean {
        return currentWinnerCount >= 0 &&
                numberOfWinners > 0 &&
                currentWinnerCount < numberOfWinners
    }
}