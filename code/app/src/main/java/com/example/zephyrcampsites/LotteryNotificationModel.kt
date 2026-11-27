// Connects lottery notification screens to Firebase Auth and the notification callables.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 waitlist notifications, 2026-11-27. Generated the Firebase client model.
// See Bhav Saluja's entry in the team AI Documentation Google Doc.
package com.example.zephyrcampsites

import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions

/** A waitlisted account and its display name for the recipient picker. */
internal data class LotteryNotificationRecipient(val accountId: String, val name: String)

/** Counts from a confirmed Firebase send. */
internal data class LotteryNotificationResult(val intended: Int, val delivered: Int)

/** A stored notification as seen by one recipient. */
internal data class ReceivedLotteryNotification(
    val id: String,
    val lotteryName: String,
    val message: String,
    val sentAtMillis: Long
)

/**
 * Reads and sends lottery notifications using the current installation's Firebase identity.
 *
 * @param functions Firebase callable endpoint client.
 */
internal class LotteryNotificationModel(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {

    /**
     * Loads a lottery's eligible waitlist for its verified owner.
     * @param accountId Current Firebase Auth profile ID.
     * @param lotteryId Selected lottery ID.
     * @return The current recipients in display order.
     */
    fun loadRecipients(
        accountId: String,
        lotteryId: String
    ): Task<List<LotteryNotificationRecipient>> {
        return call("listLotteryNotificationRecipients", accountId, mapOf("lotteryId" to lotteryId)) {
            (it as List<*>).map { entry ->
                val recipient = entry as Map<*, *>
                val name = recipient["name"] as String
                LotteryNotificationRecipient(
                    accountId = recipient["accountId"] as String,
                    name = name.ifBlank { "Unnamed entrant" }
                )
            }.sortedBy { recipient -> recipient.name.lowercase() }
        }
    }

    /**
     * Sends the message for the current owner and waits for the write to commit.
     * @param accountId Current Firebase Auth profile ID.
     * @param lotteryId Selected lottery ID.
     * @param message Trimmed message that passed validation.
     * @param allEntrants Whether to resolve the entire waitlist at send time.
     * @param recipientIds Selected accounts for an individual send.
     * @param requestId ID reused when retrying the same send.
     * @return Confirmed delivery counts.
     */
    fun send(
        accountId: String,
        lotteryId: String,
        message: String,
        allEntrants: Boolean,
        recipientIds: Set<String>,
        requestId: String
    ): Task<LotteryNotificationResult> {
        val data = mapOf(
            "lotteryId" to lotteryId,
            "message" to message,
            "allEntrants" to allEntrants,
            "recipientIds" to recipientIds.toList(),
            "requestId" to requestId
        )
        return call("sendLotteryNotification", accountId, data) {
            val counts = it as Map<*, *>
            LotteryNotificationResult(
                intended = (counts["intended"] as Number).toInt(),
                delivered = (counts["delivered"] as Number).toInt()
            )
        }
    }

    /**
     * Loads the current account's in-app notifications.
     * @param accountId Current Firebase Auth profile ID.
     * @return Delivered messages in newest-first order.
     */
    fun loadReceived(accountId: String): Task<List<ReceivedLotteryNotification>> {
        return call("listReceivedLotteryNotifications", accountId) {
            (it as List<*>).map { entry ->
                val notification = entry as Map<*, *>
                ReceivedLotteryNotification(
                    id = notification["id"] as String,
                    lotteryName = notification["lotteryName"] as String,
                    message = notification["message"] as String,
                    sentAtMillis = (notification["sentAtMillis"] as Number).toLong()
                )
            }
        }
    }

    /** Calls Firebase using the current signed-in session. */
    private fun <T> call(
        endpoint: String, accountId: String, data: Map<String, Any> = emptyMap(),
        decode: (Any?) -> T
    ): Task<T> = functions.getHttpsCallable(endpoint).call(data + ("accountId" to accountId))
        .continueWith { task ->
            task.exception?.let { throw it }
            decode(task.result.data)
        }
}
