package com.example.zephyrcampsites

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore

internal const val MAX_RESERVATION_MESSAGE_LENGTH = 2_000

internal data class ReservationMessage(
    val id: String, val senderUid: String, val text: String, val senderAccountId: String? = null
) {
    fun from(accountId: String?, uid: String?): Boolean =
        if (senderAccountId == null) senderUid == uid else senderAccountId == accountId
}

internal data class ReservationChatSession(
    val document: DocumentReference, val userUid: String, val userAccountId: String
)

internal class ReservationChatLinkRequired(accountId: String, uid: String) :
    IllegalStateException("Chat account setup required. Device ID: $accountId. Firebase UID: $uid.")

/**
 * Reads and writes messages for a reservation using its Firestore participant record.
 * @param firestore Database that holds the conversations.
 * @param auth Firebase identity for the current installation.
 */
internal class ReservationChatModel(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private var signingIn: Task<String>? = null

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
        if (reservation.cancelled ||
            !listOf(reservation.id, reservation.accountId, ownerAccountId, reservation.campsiteId).all(::validId)
        ) {
            return Tasks.forException(IllegalArgumentException("Reservation chat unavailable."))
        }

        val accountId = if (ownerReply) ownerAccountId else reservation.accountId
        val conversation = firestore.collection("reservationChat").document(reservation.id)
        val participants = mapOf(
            "entrantAccountId" to reservation.accountId, "ownerAccountId" to ownerAccountId,
            "campsiteId" to reservation.campsiteId
        )
        return authenticate().onSuccessTask { uid ->
            verifyAccountLink(accountId, uid).onSuccessTask {
                firestore.runTransaction { transaction ->
                    val existing = transaction.get(conversation)
                    if (existing.exists()) {
                        check(participants.all { (field, value) -> existing.get(field) == value }) {
                            "Reservation chat unavailable."
                        }
                    } else {
                        transaction.set(conversation, participants)
                    }
                    ReservationChatSession(conversation, uid, accountId)
                }
            }
        }
    }

    @Synchronized
    private fun authenticate(): Task<String> {
        auth.currentUser?.uid?.let { return Tasks.forResult(it) }
        signingIn?.takeUnless { it.isComplete }?.let { return it }
        return auth.signInAnonymously().onSuccessTask { signIn ->
            Tasks.forResult(checkNotNull(signIn.user).uid)
        }.also { signingIn = it }
    }

    private fun verifyAccountLink(accountId: String, uid: String): Task<Unit> {
        return firestore.collection("chatAccountLinks").document(accountId).get(Source.SERVER)
            .continueWithTask { result ->
                val error = result.exception
                when {
                    error is FirebaseFirestoreException &&
                        error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        Tasks.forException(ReservationChatLinkRequired(accountId, uid))
                    error != null -> Tasks.forException(error)
                    result.result.get("uid") == uid -> Tasks.forResult(Unit)
                    else -> Tasks.forException(ReservationChatLinkRequired(accountId, uid))
                }
            }
    }

    private fun validId(id: String): Boolean = id.isNotBlank() && !id.contains("/") && id != "." && id != ".."

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
                        ReservationMessage(message.id, sender, text, message.get("senderAccountId") as? String)
                    })
                }
            }
    }

    /**
     * Saves a message under the verified reservation with a server timestamp.
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
                "text" to message,
                "sentAt" to FieldValue.serverTimestamp()
            ))
            Unit
        }
    }
}
