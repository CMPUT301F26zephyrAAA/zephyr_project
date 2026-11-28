// Persists reservation conversations, messages, and read receipts for signed-in participants.
package com.example.zephyrcampsites

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

internal const val MAX_RESERVATION_MESSAGE_LENGTH = 2_000

/** A message saved under one reservation conversation. */
internal data class ReservationMessage(
    val id: String, val senderUid: String, val text: String, val senderAccountId: String? = null,
    val sentAt: Timestamp? = null
) {
    /** Checks the sender account, using the Auth UID for older messages without a sender account. */
    fun from(accountId: String?, uid: String?): Boolean =
        if (senderAccountId == null) senderUid == uid else senderAccountId == accountId
}

/**
 * An open conversation and its verified sending and receiving participants.
 * @property document The reservation's conversation document.
 * @property userUid Firebase Auth UID of the sender.
 * @property userAccountId Account ID of the sender.
 * @property recipientAccountId Account ID of the other participant.
 */
internal data class ReservationChatSession(
    val document: DocumentReference,
    val userUid: String,
    val userAccountId: String,
    val recipientAccountId: String
)

// AI Citation [AI-43] (Bhav Saluja)
// OpenAI, Codex, November 27, 2026. Reused the app's Firebase Auth identity for chat.
// See the team AI Documentation Google Doc.
/**
 * Reads and writes messages scoped to a saved reservation and its verified participants.
 * @param firestore Database that holds the conversations.
 * @param auth Firebase Auth session established when the app loads the current profile.
 */
internal class ReservationChatModel(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    /**
     * Opens or creates the conversation after Firestore verifies the signed-in participant.
     * @param reservation Reservation selected by the entrant or owner.
     * @param ownerAccountId Account linked to the reserved campsite.
     * @param ownerReply Whether the signed-in participant is replying as the campsite owner.
     * @return The conversation and signed-in sender.
     */
    fun open(
        reservation: ReservedUser,
        ownerAccountId: String,
        ownerReply: Boolean = false
    ): Task<ReservationChatSession> {
        val participants = mapOf(
            "entrantAccountId" to reservation.accountId, "ownerAccountId" to ownerAccountId,
            "campsiteId" to reservation.campsiteId
        )
        val session = signedInSession(reservation, ownerAccountId, ownerReply)
            ?: return Tasks.forException(IllegalStateException("Reservation chat unavailable."))
        return firestore.runTransaction { transaction ->
            val existing = transaction.get(session.document)
            if (existing.exists()) {
                check(participants.all { (field, value) -> existing.get(field) == value }) {
                    "Reservation chat unavailable."
                }
            } else {
                transaction.set(session.document, participants)
            }
            session
        }
    }

    private fun signedInSession(
        reservation: ReservedUser,
        ownerAccountId: String,
        ownerReply: Boolean
    ): ReservationChatSession? {
        val uid = auth.currentUser?.uid ?: return null
        val accountId = if (ownerReply) ownerAccountId else reservation.accountId
        if (reservation.cancelled || accountId != "auth_$uid" || !validId(reservation.id) ||
            !validId(ownerAccountId) || !validId(reservation.accountId) || !validId(reservation.campsiteId)
        ) {
            return null
        }
        val document = firestore.collection("reservationChat").document(reservation.id)
        val recipientId = if (ownerReply) reservation.accountId else ownerAccountId
        return ReservationChatSession(document, uid, accountId, recipientId)
    }

    private fun validId(id: String): Boolean =
        id.length <= 128 && id.isNotBlank() && !id.contains("/") && id != "." && id != ".."

    /**
     * Listens for confirmed messages in server timestamp order.
     * @param session Open reservation conversation.
     * @param onMessages Receives the latest ordered messages.
     * @param onError Called if the conversation can no longer be read.
     * @return Registration to remove when leaving the screen.
     */
    fun listen(
        session: ReservationChatSession,
        onMessages: (List<ReservationMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return session.document.collection("messages").orderBy("sentAt")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onError(error)
                } else if (snapshot != null && !snapshot.metadata.isFromCache) {
                    onMessages(snapshot.documents.mapNotNull { message ->
                        val sender = message.get("senderUid") as? String ?: return@mapNotNull null
                        val text = message.get("text") as? String ?: return@mapNotNull null
                        ReservationMessage(
                            message.id, sender, text, message.get("senderAccountId") as? String,
                            message.getTimestamp("sentAt")
                        )
                    })
                }
            }
    }

    /**
     * Observes unread incoming messages without creating a conversation from the reservation list.
     *
     * @param reservation Booking whose unread state is requested.
     * @param ownerAccountId Account linked to the reserved campsite.
     * @param ownerReply Whether the badge belongs to the owner.
     * @param onUnread Receives the participant's current unread state.
     * @return Registration to remove when the reservation row leaves the screen.
     */
    fun observeUnread(
        reservation: ReservedUser,
        ownerAccountId: String,
        ownerReply: Boolean,
        onUnread: (Boolean) -> Unit
    ): ListenerRegistration {
        val session = signedInSession(reservation, ownerAccountId, ownerReply)
        if (session == null) {
            onUnread(false)
            return ListenerRegistration {}
        }
        var active = true
        var latestMessage: ListenerRegistration? = null
        var readReceipt: ListenerRegistration? = null
        var incomingAt: Timestamp? = null
        var readAt: Timestamp? = null
        var receiptLoaded = false

        fun reportUnread() {
            if (active && receiptLoaded) {
                onUnread(incomingAt?.let { incoming -> readAt?.let { incoming > it } ?: true } ?: false)
            }
        }

        val conversation = session.document.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                receiptLoaded = false
                latestMessage?.remove()
                readReceipt?.remove()
                if (active) {
                    onUnread(false)
                }
            } else if (snapshot != null && !snapshot.metadata.isFromCache && snapshot.exists() &&
                latestMessage == null && active
            ) {
                val sender = if (ownerReply) reservation.accountId else ownerAccountId
                latestMessage = session.document.collection("messages")
                    .whereEqualTo("senderAccountId", sender)
                    .orderBy("sentAt", Query.Direction.DESCENDING).limit(1)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { messages, messageError ->
                        if (messageError != null || messages?.metadata?.isFromCache == false) {
                            incomingAt = messages?.documents?.firstOrNull()?.getTimestamp("sentAt")
                            reportUnread()
                        }
                    }
                readReceipt = receipt(session).addSnapshotListener(MetadataChanges.INCLUDE) { read, readError ->
                    if (readError != null && active) {
                        onUnread(false)
                    } else if (read != null && !read.metadata.isFromCache) {
                        readAt = read.getTimestamp("readAt")
                        receiptLoaded = true
                        reportUnread()
                    }
                }
            }
        }

        return ListenerRegistration {
            active = false
            conversation.remove()
            latestMessage?.remove()
            readReceipt?.remove()
        }
    }

    /**
     * Records the latest incoming message seen, preserving a newer receipt if one already exists.
     * @param session Open reservation conversation.
     * @param seenAt Server timestamp of the newest incoming message displayed.
     * @return The server write result.
     */
    fun markRead(session: ReservationChatSession, seenAt: Timestamp): Task<Unit> {
        val receipt = receipt(session)
        return firestore.runTransaction { transaction ->
            val readAt = transaction.get(receipt).getTimestamp("readAt")
            if (readAt == null || seenAt > readAt) {
                transaction.set(receipt, mapOf("readAt" to seenAt))
            }
            Unit
        }
    }

    private fun receipt(session: ReservationChatSession): DocumentReference =
        session.document.collection("readReceipts").document(session.userAccountId)

    // AI Citation [AI-49] (Bhav Saluja)
    // OpenAI, Codex, November 27, 2026. Saved explicit recipient and reservation IDs.
    // See Bhav's entry in the team's AI Documentation Google Doc.
    /**
     * Saves a message with both participant IDs, the reservation ID, and a server timestamp.
     * @param session Open reservation conversation.
     * @param text Message entered by the participant.
     * @return The server write result. Transactions fail when offline.
     */
    fun send(session: ReservationChatSession, text: String): Task<Unit> {
        val message = text.trim()
        if (message.isEmpty() || message.length > MAX_RESERVATION_MESSAGE_LENGTH) {
            return Tasks.forException(IllegalArgumentException("Invalid message."))
        }
        val document = session.document.collection("messages").document()
        return firestore.runTransaction { transaction ->
            transaction.set(document, mapOf(
                "senderUid" to session.userUid,
                "senderAccountId" to session.userAccountId,
                "recipientAccountId" to session.recipientAccountId,
                "reservationId" to session.document.id,
                "text" to message,
                "sentAt" to FieldValue.serverTimestamp()
            ))
            Unit
        }
    }
}
