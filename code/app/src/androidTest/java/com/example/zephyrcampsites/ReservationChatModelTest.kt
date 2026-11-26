package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URI
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
 * Requires chatFirestoreEmulator=true and adb reverse for Firestore (8097) and Auth (9097).
 */
@RunWith(AndroidJUnit4::class)
class ReservationChatModelTest {
    private val apps = mutableListOf<FirebaseApp>()
    private val projectId = "demo-zephyr-reservation-chat"
    private val reservation = ReservedUser(UUID.randomUUID().toString(), "entrant-${UUID.randomUUID()}", "1")
    private val ownerId = "owner-${UUID.randomUUID()}"

    @Before
    fun requireEmulator() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("chatFirestoreEmulator") == "true")
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
        val (entrantModel, entrantUid) = connect()
        val (ownerModel, ownerUid) = connect()
        val otherModel = connect().first
        approveReservation(entrantUid, ownerUid)

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
            assertTrue(runCatching { await(otherModel.open(reservation, ownerId)) }.isFailure)
        } finally {
            ownerListener.remove()
            listener.remove()
        }
    }

    @Test
    fun reopeningChatReadsOnlyMessagesFromTheSelectedReservation() {
        val (entrantModel, entrantUid) = connect()
        val (ownerModel, ownerUid) = connect()
        val otherReservation = reservation.copy(id = UUID.randomUUID().toString())
        approveReservation(entrantUid, ownerUid)
        approveReservation(entrantUid, ownerUid, otherReservation)

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
        val (entrantModel, entrantUid) = connect()
        approveReservation(entrantUid, connect().second)
        val chat = await(entrantModel.open(reservation, ownerId))
        val invalid = listOf("", " \t\n", "x".repeat(MAX_RESERVATION_MESSAGE_LENGTH + 1))
        for (text in invalid) {
            val result = runCatching { await(entrantModel.send(chat, text)) }
            assertTrue(result.exceptionOrNull()?.cause is IllegalArgumentException)
        }
        assertTrue(readMessages(entrantModel, chat).isEmpty())
    }

    @Test
    fun opensOnlyVerifiedReservationsAndRevokesAnOldUidWhenRelinked() {
        val (entrantModel, entrantUid) = connect()
        val (_, ownerUid) = connect()
        assertTrue(runCatching { await(entrantModel.open(reservation, ownerId)) }.isFailure)

        approveReservation(entrantUid, ownerUid)
        assertTrue(runCatching { await(entrantModel.open(reservation, "wrong-owner")) }.isFailure)
        val first = await(entrantModel.open(reservation, ownerId))
        await(entrantModel.send(first, "Original message"))

        val (relinkedModel, newUid) = connect()
        writeDocument("chatAccountLinks/${reservation.accountId}", mapOf("uid" to newUid))
        assertTrue(runCatching { await(entrantModel.open(reservation, ownerId)) }.isFailure)
        assertTrue(runCatching { await(entrantModel.send(first, "Old identity")) }.isFailure)

        val reopened = await(relinkedModel.open(reservation, ownerId))
        val message = readMessages(relinkedModel, reopened).single()
        assertEquals("Original message", message.text)
        assertTrue(message.from(reservation.accountId, newUid))
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
        firestore.useEmulator("127.0.0.1", 8097)
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("127.0.0.1", 9097)
        val uid = checkNotNull(await(auth.signInAnonymously()).user).uid
        return ReservationChatModel(firestore, auth) to uid
    }

    private fun approveReservation(entrantUid: String, ownerUid: String, booking: ReservedUser = reservation) {
        writeDocument("chatAccountLinks/${booking.accountId}", mapOf("uid" to entrantUid))
        writeDocument("chatAccountLinks/$ownerId", mapOf("uid" to ownerUid))
        writeDocument("reservationChatAccess/${booking.id}", mapOf(
            "entrantAccountId" to booking.accountId, "ownerAccountId" to ownerId, "campsiteId" to booking.campsiteId
        ))
    }

    private fun writeDocument(document: String, values: Map<String, String>) {
        val path = "projects/$projectId/databases/(default)/documents"
        val connection = URI("http://127.0.0.1:8097/v1/$path:commit").toURL().openConnection() as HttpURLConnection
        val fields = JSONObject()
        for ((field, value) in values) {
            fields.put(field, JSONObject().put("stringValue", value))
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
