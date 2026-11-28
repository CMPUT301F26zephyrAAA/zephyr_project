// Validates owner messages and coordinates notification sends.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 waitlist notifications, 2026-11-27. Generated the controller and validation.
// See Bhav Saluja's entry in the team AI Documentation Google Doc.
package com.example.zephyrcampsites

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctionsException

internal const val MAX_LOTTERY_NOTIFICATION_LENGTH = 2_000

/** Validates requests before forwarding them to the notification model. */
internal class LotteryNotificationController(
    private val model: LotteryNotificationModel = LotteryNotificationModel()
) {
    /** Loads the current recipient choices for the selected lottery. */
    fun loadRecipients(accountId: String, lotteryId: String) =
        model.loadRecipients(accountId, lotteryId)

    /** Validates an owner send before Firebase rechecks access and recipients. */
    fun send(
        accountId: String,
        lotteryId: String,
        message: String,
        allEntrants: Boolean,
        recipientIds: Set<String>,
        requestId: String
    ): Task<LotteryNotificationResult> {
        val error = lotteryNotificationValidationError(message, allEntrants || recipientIds.isNotEmpty())
        if (error != null) {
            return Tasks.forException(InvalidLotteryNotification(error))
        }
        val selectedIds = if (allEntrants) { emptySet() } else { recipientIds }
        return model.send(accountId, lotteryId, message.trim(), allEntrants, selectedIds, requestId)
    }

    /** Loads notifications addressed to the entrant. */
    fun loadReceived(accountId: String) = model.loadReceived(accountId)

    // AI Citation [AI-40]
    // OpenAI, Codex, US 02.10.02, 2026-11-27. Generated the history controller methods.
    // See Bhav's entry in the team's AI Documentation Google Doc.
    /** Loads the owner's sent notifications. */
    fun loadSent(accountId: String) = model.loadSent(accountId)

    /** Loads the message and delivery outcomes for a selected notification. */
    fun loadSentDetails(accountId: String, notificationId: String) =
        model.loadSentDetails(accountId, notificationId)
}

/** Identifies a validation error from the notification composer. */
private class InvalidLotteryNotification(message: String) : IllegalArgumentException(message)

/** Returns the first validation error in the send form, if any. */
internal fun lotteryNotificationValidationError(message: String, hasRecipients: Boolean): String? {
    return when {
        message.isBlank() -> "Enter a notification message."
        message.trim().length > MAX_LOTTERY_NOTIFICATION_LENGTH -> "Use 2,000 characters or fewer."
        !hasRecipients -> "Choose at least one recipient."
        else -> null
    }
}

/** Uses safe user-facing messages for expected errors and a fallback for unavailable Firebase. */
internal fun lotteryNotificationError(error: Exception, fallback: String): String {
    return when {
        error is InvalidLotteryNotification -> error.message ?: fallback
        error is FirebaseFunctionsException && error.code in setOf(
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION
        ) -> error.message ?: fallback
        else -> fallback
    }
}
