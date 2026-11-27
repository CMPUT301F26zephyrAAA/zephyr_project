package com.example.zephyrcampsites

// **AI Citation** [AI-25], [AI-33]
// OpenAI, ChatGPT assisted with reservation offer
// decline eligibility validation.
// AI usage and citations documented in the GitHub Wiki.

/**
 * Validates lottery reservation offer actions.
 */
object ReservationOfferValidator {

    /**
     * Determines whether an entrant can decline a winning offer.
     *
     * @param drawCompleted Whether the lottery draw has completed.
     * @param entrantStatus Current draw status of the entrant.
     * @param alreadyDeclined Whether the entrant previously declined.
     * @return True if the entrant is eligible to decline.
     */
    fun canDecline(
        drawCompleted: Boolean,
        entrantStatus: String,
        alreadyDeclined: Boolean
    ): Boolean {
        return drawCompleted &&
                entrantStatus == "WINNER" &&
                !alreadyDeclined
    }

    /**
     * Determines whether a backup entrant is eligible for promotion.
     *
     * @param registrationExists Whether the registration exists.
     * @param registrationMatches Whether the lottery and account IDs match.
     * @param entrantStatus Current draw status of the entrant.
     * @param accountExists Whether the entrant's account exists.
     * @param deletionPending Whether the account is pending deletion.
     * @return True if the backup is eligible for promotion.
     */
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

    /**
     * Checks whether another offer can be issued within the winner limit.
     *
     * @param currentWinnerCount Number of current winning offers.
     * @param numberOfWinners Maximum configured winner count.
     * @return True if another winning offer can be issued.
     */
    fun canIssueReplacementOffer(
        currentWinnerCount: Int,
        numberOfWinners: Long
    ): Boolean {
        return currentWinnerCount >= 0 &&
                numberOfWinners > 0 &&
                currentWinnerCount < numberOfWinners
    }
}