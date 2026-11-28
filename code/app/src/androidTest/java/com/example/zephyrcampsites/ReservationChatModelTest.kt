// Exercises production chat rules against Auth and Firestore emulators.
package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.net.HttpURLConnection
import java.net.URI
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests two-way chat and participant isolation with the chat rules in a local emulator.
 * Requires chatFirestoreEmulator=true and adb reverse for Firestore (8082) and Auth (9099).
 */
// AI Citation [AI-43] (Bhav Saluja)
// OpenAI, Codex, November 27, 2026. Verified chat with Auth profiles and canonical bookings.
// See the team AI Documentation Google Doc.
@RunWith(AndroidJUnit4::class)
class ReservationChatModelTest {
    private val apps = mutableListOf<FirebaseApp>()
    private val projectId = "demo-zephyr-notifications"
    private lateinit var reservation: ReservedUser
    private lateinit var entrantModel: ReservationChatModel
    private lateinit var ownerModel: ReservationChatModel
    private lateinit var entrantUid: String
    private lateinit var ownerUid: String
    private val ownerId get() = "auth_$ownerUid"

    @Before
    fun createParticipants() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("chatFirestoreEmulator") == "true")
        val (entrant, signedInEntrant) = connect()
        val (owner, signedInOwner) = connect()
        entrantModel = entrant
        ownerModel = owner
        entrantUid = signedInEntrant
        ownerUid = signedInOwner
        reservation = ReservedUser(
            id = UUID.randomUUID().toString(),
            accountId = "auth_$entrantUid",
            campsiteId = "chat-site-${UUID.randomUUID()}",
            bookedPeriodStart = Date(1_750_003_200_000L),
            bookedPeriodEnd = Date(1_750_176_000_000L)
        )
        val ownerFirestore = FirebaseFirestore.getInstance(apps.last())
        await(ownerFirestore.collection("campsite").document(reservation.campsiteId)
            .set(mapOf("ownerID" to ownerId, "name" to "Pinewood Lakeside")))
    }

    @After
    fun closeEmulatorClients() {
        for (app in apps) {
            await(FirebaseFirestore.getInstance(app).terminate())
            app.delete()
        }
    }

    @Test
    fun participantsExchangeMessagesInOrderAndOtherAccountsCannotOpenChat() {
        writeReservation(reservation)

        val entrantChat = await(entrantModel.open(reservation, ownerId))
        val ownerChat = await(ownerModel.open(reservation, ownerId, ownerReply = true))
        val history = TaskCompletionSource<List<ReservationMessage>>()
        val receivedByOwner = TaskCompletionSource<ReservationMessage>()
        val listener = entrantModel.listen(
            entrantChat,
            onMessages = { if (it.size == 2) { history.trySetResult(it) } },
            onError = { history.trySetException(it) }
        )
        val ownerListener = ownerModel.listen(
            ownerChat,
            onMessages = { messages ->
                messages.firstOrNull { it.senderUid == entrantUid }?.let {
                    receivedByOwner.trySetResult(it)
                }
            },
            onError = { receivedByOwner.trySetException(it) }
        )
        try {
            await(entrantModel.send(entrantChat, "What time is check-in?"))
            assertEquals("What time is check-in?", await(receivedByOwner.task).text)
            await(ownerModel.send(ownerChat, "Check-in is at 2."))
            val messages = await(history.task)
            assertEquals(listOf("What time is check-in?", "Check-in is at 2."), messages.map { it.text })
            assertEquals(listOf(entrantUid, ownerUid), messages.map { it.senderUid })
            assertTrue(runCatching { await(entrantModel.open(reservation, ownerId, ownerReply = true)) }.isFailure)
        } finally {
            ownerListener.remove()
            listener.remove()
        }
    }

    @Test
    fun reopeningChatReadsOnlyMessagesFromTheSelectedReservation() {
        val otherReservation = reservation.copy(id = UUID.randomUUID().toString())
        writeReservation(reservation)
        writeReservation(otherReservation)

        val first = await(entrantModel.open(reservation, ownerId))
        val second = await(entrantModel.open(otherReservation, ownerId))
        await(entrantModel.send(first, "First reservation"))
        await(entrantModel.send(second, "Second reservation"))

        val reopened = await(ownerModel.open(reservation, ownerId, ownerReply = true))
        val other = await(ownerModel.open(otherReservation, ownerId, ownerReply = true))
        assertEquals(listOf("First reservation"), readMessages(ownerModel, reopened).map { it.text })
        assertEquals(listOf("Second reservation"), readMessages(ownerModel, other).map { it.text })
    }

    @Test
    fun invalidMessageTextDoesNotCreateMessages() {
        writeReservation(reservation)
        val chat = await(entrantModel.open(reservation, ownerId))
        val invalid = listOf("", " \t\n", "x".repeat(MAX_RESERVATION_MESSAGE_LENGTH + 1))
        for (text in invalid) {
            val result = runCatching { await(entrantModel.send(chat, text)) }
            assertTrue(result.exceptionOrNull()?.cause is IllegalArgumentException)
        }
        assertTrue(readMessages(entrantModel, chat).isEmpty())
    }

    @Test
    fun serverRequiresSavedBookingAndCurrentParticipants() {
        assertPermissionDenied(entrantModel.open(reservation, ownerId))

        val (outsiderModel, outsiderUid) = connect()
        val outsiderAccountId = "auth_$outsiderUid"
        val outsiderDb = FirebaseFirestore.getInstance(apps.last())
        val outsideConversation = outsiderDb.collection("reservationChat").document(reservation.id)
        writeReservation(reservation)

        assertPermissionDenied(entrantModel.open(reservation, outsiderAccountId))
        assertPermissionDenied(outsiderModel.open(reservation.copy(accountId = outsiderAccountId), ownerId))
        val chat = await(entrantModel.open(reservation, ownerId))
        await(entrantModel.send(chat, "A private message"))
        assertPermissionDenied(outsideConversation.get(Source.SERVER))
        assertPermissionDenied(outsideConversation.collection("messages").get(Source.SERVER))
    }

    @Test
    fun aCampsiteOwnershipChangeDoesNotExposeExistingChatHistory() {
        writeReservation(reservation)
        val chat = await(entrantModel.open(reservation, ownerId))
        await(entrantModel.send(chat, "A private message"))

        val (newOwner, newUid) = connect()
        val newOwnerId = "auth_$newUid"
        writeDocument("campsite/${reservation.campsiteId}", mapOf("ownerID" to newOwnerId))

        assertPermissionDenied(newOwner.open(reservation, newOwnerId, ownerReply = true))
        assertPermissionDenied(ownerModel.open(reservation, ownerId, ownerReply = true))
        assertPermissionDenied(entrantModel.send(chat, "An old conversation"))
    }

    @Test
    fun completedReservationCanChatUntilItIsCancelled() {
        writeReservation(reservation)
        val first = await(entrantModel.open(reservation, ownerId))
        val second = await(ownerModel.open(reservation, ownerId, ownerReply = true))
        await(entrantModel.send(first, "Thanks for the stay."))
        await(ownerModel.send(second, "You are welcome."))

        writeReservation(reservation, cancelled = true)
        assertPermissionDenied(entrantModel.send(first, "Cancelled"))
        assertPermissionDenied(ownerModel.send(second, "Cancelled"))
        assertPermissionDenied(entrantModel.open(reservation, ownerId))
        assertPermissionDenied(ownerModel.open(reservation, ownerId, ownerReply = true))
    }

    @Test
    fun unreadUpdatesOutsideChatAndClearsWhenTheRecipientReadsIt() {
        writeReservation(reservation)

        val unread = TaskCompletionSource<Boolean>()
        val cleared = TaskCompletionSource<Boolean>()
        val listener = ownerModel.observeUnread(reservation, ownerId, ownerReply = true) { hasUnread ->
            if (hasUnread) {
                unread.trySetResult(true)
            } else if (unread.task.isComplete) {
                cleared.trySetResult(true)
            }
        }
        try {
            val entrant = await(entrantModel.open(reservation, ownerId))
            await(entrantModel.send(entrant, "Unread reservation message"))
            assertTrue(await(unread.task))

            val owner = await(ownerModel.open(reservation, ownerId, ownerReply = true))
            val received = readMessages(ownerModel, owner).single()
            await(ownerModel.markRead(owner, checkNotNull(received.sentAt)))
            assertTrue(await(cleared.task))
        } finally {
            listener.remove()
        }
    }

    private fun readMessages(model: ReservationChatModel, session: ReservationChatSession): List<ReservationMessage> {
        val result = TaskCompletionSource<List<ReservationMessage>>()
        val listener = model.listen(session, { result.trySetResult(it) }, { result.trySetException(it) })
        return try {
            await(result.task)
        } finally {
            listener.remove()
        }
    }

    private fun connect(): Pair<ReservationChatModel, String> {
        val clientName = "chat-${UUID.randomUUID()}"
        val options = FirebaseOptions.Builder().setProjectId(projectId)
            .setApplicationId("1:123456789:android:chat-test").setApiKey(clientName).build()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, options, clientName)
        apps.add(app)
        val firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("127.0.0.1", 8082)
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("127.0.0.1", 9099)
        val uid = checkNotNull(await(auth.signInAnonymously()).user).uid
        val accountId = "auth_$uid"
        await(firestore.collection("accounts").document(accountId).set(mapOf(
            "id" to accountId,
            "userType" to listOf("entrant", "owner"),
            "notificationsEnabled" to true
        )))
        return ReservationChatModel(firestore, auth) to uid
    }

    private fun assertPermissionDenied(task: Task<*>) {
        val failure = runCatching { await(task) }.exceptionOrNull()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (failure?.cause as? FirebaseFirestoreException)?.code)
    }

    private fun writeReservation(booking: ReservedUser, cancelled: Boolean = false) {
        writeDocument("reservation/${booking.id}", mapOf(
            "accountId" to booking.accountId,
            "campsiteId" to booking.campsiteId,
            "cancelled" to cancelled,
            "bookedPeriodStart" to Timestamp(checkNotNull(booking.bookedPeriodStart)),
            "bookedPeriodEnd" to Timestamp(checkNotNull(booking.bookedPeriodEnd))
        ))
    }

    private fun writeDocument(document: String, values: Map<String, Any>) {
        val path = "projects/$projectId/databases/(default)/documents"
        val connection = URI("http://127.0.0.1:8082/v1/$path:commit").toURL().openConnection() as HttpURLConnection
        val fields = JSONObject()
        for ((field, value) in values) {
            val encoded = when (value) {
                is String -> JSONObject().put("stringValue", value)
                is Boolean -> JSONObject().put("booleanValue", value)
                is Timestamp -> JSONObject().put("timestampValue", value.toDate().toInstant().toString())
                else -> error("Unsupported fixture value")
            }
            fields.put(field, encoded)
        }
        val update = JSONObject().put("name", "$path/$document").put("fields", fields)
        val body = JSONObject().put("writes", JSONArray().put(JSONObject().put("update", update)))
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            assertEquals(200, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 10, TimeUnit.SECONDS)
}
