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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    private val reservation = ReservedUser(UUID.randomUUID().toString(), "entrant-device", "1")
    private val ownerId = "owner-device"

    @After
    fun closeEmulatorClients() {
        for (app in apps) {
            await(FirebaseFirestore.getInstance(app).terminate())
            app.delete()
        }
    }

    @Test
    fun participantsExchangeMessagesInOrderAndOtherAccountsCannotOpenChat() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("chatFirestoreEmulator") == "true")
        val (entrantModel, entrantUid) = connect()
        val (ownerModel, ownerUid) = connect()
        val otherModel = connect().first
        createConversation(entrantUid, ownerUid)

        val entrantChat = await(entrantModel.open(reservation, ownerId))
        val ownerChat = await(ownerModel.open(reservation, ownerId))
        val history = TaskCompletionSource<List<ReservationMessage>>()
        val listener = entrantModel.listen(
            entrantChat,
            onMessages = { if (it.size == 2) { history.trySetResult(it) } },
            onError = { history.trySetException(it) }
        )
        try {
            await(entrantModel.send(entrantChat, "What time is check-in?"))
            await(ownerModel.send(ownerChat, "Check-in is at 2."))
            val messages = await(history.task)
            assertEquals(listOf("What time is check-in?", "Check-in is at 2."), messages.map { it.text })
            assertEquals(listOf(entrantUid, ownerUid), messages.map { it.senderUid })
            assertTrue(runCatching { await(otherModel.open(reservation, ownerId)) }.isFailure)
        } finally {
            listener.remove()
        }
    }

    private fun connect(): Pair<ReservationChatModel, String> {
        val options = FirebaseOptions.Builder().setProjectId(projectId)
            .setApplicationId("1:123456789:android:chat-test").setApiKey("local-emulator-only").build()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, options, "chat-${UUID.randomUUID()}")
        apps.add(app)
        val firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("127.0.0.1", 8097)
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("127.0.0.1", 9097)
        val uid = checkNotNull(await(auth.signInAnonymously()).user).uid
        return ReservationChatModel(firestore, auth) to uid
    }

    private fun createConversation(entrantUid: String, ownerUid: String) {
        val path = "projects/$projectId/databases/(default)/documents/reservationChat?documentId=${reservation.id}"
        val connection = URI("http://127.0.0.1:8097/v1/$path").toURL().openConnection() as HttpURLConnection
        val fields = JSONObject()
        val participants = mapOf(
            "entrantUid" to entrantUid, "ownerUid" to ownerUid,
            "entrantAccountId" to reservation.accountId, "ownerAccountId" to ownerId,
            "campsiteId" to reservation.campsiteId
        )
        for ((field, value) in participants) {
            fields.put(field, JSONObject().put("stringValue", value))
        }
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.outputStream.use { it.write(JSONObject().put("fields", fields).toString().toByteArray()) }
            assertEquals(200, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 10, TimeUnit.SECONDS)
}
