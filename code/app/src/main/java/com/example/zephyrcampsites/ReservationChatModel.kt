package com.example.zephyrcampsites

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore

internal const val MAX_RESERVATION_MESSAGE_LENGTH = 2_000

internal data class ReservationMessage(val id: String, val senderUid: String, val text: String)

internal data class ReservationChatSession(val document: DocumentReference, val userUid: String)

/**
 * Reads and writes messages for a reservation using its Firestore participant record.
 * @param firestore Database that holds the conversations.
 * @param auth Firebase identity for the current installation.
 */
internal class ReservationChatModel(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    /**
     * Opens the conversation after Firestore verifies the signed-in participant.
     * @param reservation Reservation selected from My Trips.
     * @param ownerAccountId Account linked to the reserved campsite.
     * @return The conversation and signed-in sender.
     */
    fun open(reservation: ReservedUser, ownerAccountId: String): Task<ReservationChatSession> {
        if (reservation.id.isBlank() || reservation.id.contains("/") ||
            reservation.id == "." || reservation.id == ".." ||
            reservation.accountId.isBlank() || ownerAccountId.isBlank() || reservation.campsiteId.isBlank()
        ) {
            return Tasks.forException(IllegalArgumentException("Reservation chat unavailable."))
        }

        val currentUid = auth.currentUser?.uid
        val signedIn = if (currentUid != null) {
            Tasks.forResult(currentUid)
        } else {
            auth.signInAnonymously().continueWith { signIn ->
                checkNotNull(signIn.result.user).uid
            }
        }
        val conversation = firestore.collection("reservationChat").document(reservation.id)
        return signedIn.onSuccessTask { uid ->
            conversation.get(Source.SERVER).continueWith { request ->
                val document = request.result
                val entrantUid = document.get("entrantUid") as? String
                val ownerUid = document.get("ownerUid") as? String
                check(
                    document.exists() && (uid == entrantUid || uid == ownerUid) &&
                        document.get("entrantAccountId") == reservation.accountId &&
                        document.get("ownerAccountId") == ownerAccountId &&
                        document.get("campsiteId") == reservation.campsiteId
                ) { "Reservation chat unavailable." }
                ReservationChatSession(conversation, uid)
            }
        }
    }

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
                        ReservationMessage(message.id, sender, text)
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
                "text" to message,
                "sentAt" to FieldValue.serverTimestamp()
            ))
            Unit
        }
    }
}
