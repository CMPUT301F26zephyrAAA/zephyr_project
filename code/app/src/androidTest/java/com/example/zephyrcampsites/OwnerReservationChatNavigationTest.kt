// Verifies owner-initiated chat from the real reservation list with local Firebase emulators.
package com.example.zephyrcampsites

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// AI Citation [AI-49] (Bhav Saluja)
// OpenAI, Codex, November 27, 2026. Verified both app entry points against one confirmed booking.
// See Bhav's entry in the team's AI Documentation Google Doc.
/** Requires ownerChatEmulator=true and adb reverse for ports 8082, 9099, and 5001. */
@RunWith(AndroidJUnit4::class)
class OwnerReservationChatNavigationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun ownerStartsConversationAndEntrantRepliesFromMyTrips() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ownerChatEmulator") == "true")
        val app = FirebaseApp.getInstance()
        assertEquals("demo-zephyr-notifications", app.options.projectId)
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        auth.useEmulator("127.0.0.1", 9099)
        firestore.useEmulator("127.0.0.1", 8082)
        val functions = FirebaseFunctions.getInstance(app)
        functions.useEmulator("127.0.0.1", 5001)

        val suffix = UUID.randomUUID().toString().take(8)
        val ownerEmail = "owner-$suffix@example.test"
        val entrantEmail = "entrant-$suffix@example.test"
        val campsiteId = (UUID.randomUUID().hashCode() and Int.MAX_VALUE).toString()
        val campsiteName = "Whispering Pines $suffix"
        val bookingId = "confirmed-$suffix"
        val pastBookingId = "completed-$suffix"
        val ownerName = "Casey Morgan"
        val entrantName = "Taylor North"
        val ownerId = createProfile(auth, firestore, ownerEmail, ownerName)
        val campsite = Campsite(campsiteId).apply {
            name = campsiteName
            ownerID = ownerId
        }
        await(firestore.document("campsite/$campsiteId").set(campsite))
        val entrantId = createProfile(auth, firestore, entrantEmail, entrantName)
        val now = Instant.now()
        seedConfirmedBooking(bookingId, campsiteId, entrantId, now.plusSeconds(TimeUnit.DAYS.toSeconds(7)))
        seedConfirmedBooking(pastBookingId, campsiteId, entrantId, now.minusSeconds(TimeUnit.DAYS.toSeconds(7)))

        val firstMessage = "The north gate will be open when you arrive."
        val reply = "Thanks, we expect to arrive at four."
        await(auth.signInWithEmailAndPassword(ownerEmail, TEST_PASSWORD))
        val ownerBookings = await(functions.getHttpsCallable("listOwnerReservations").call()).data as Map<*, *>
        val ownerBooking = (ownerBookings["reservations"] as List<*>)
            .filterIsInstance<Map<*, *>>().single { it["id"] == bookingId }
        assertEquals(campsiteId, ownerBooking["campsiteId"])
        assertEquals(entrantId, ownerBooking["entrantAccountId"])
        ActivityScenario.launch(MainActivity::class.java).use {
            openOwnerBooking(campsiteName)
            waitFor("Confirmed · Upcoming")
            compose.onNodeWithText("Message Entrant").performScrollTo().performClick()
            waitFor("MESSAGES")
            waitFor(entrantName)
            compose.onNodeWithText("SEND").assertIsNotEnabled()
            sendMessage(firstMessage)
            waitFor(firstMessage)
            pressBack()
            waitFor("Reservation Details")
            waitFor(bookingId)
            pressBack()
            compose.onNodeWithText("Active").assertIsSelected()
            waitFor(campsiteName)
        }

        await(auth.signInWithEmailAndPassword(entrantEmail, TEST_PASSWORD))
        ActivityScenario.launch(MainActivity::class.java).use {
            openRole("Entrant")
            waitFor("My Trips")
            compose.onNodeWithText("My Trips").performClick()
            waitFor("New message")
            compose.onNodeWithText("View Details", ignoreCase = true).performClick()
            compose.onNodeWithText("Message Owner", ignoreCase = true).performClick()
            waitFor("MESSAGES")
            waitFor(firstMessage)
            waitFor(ownerName)
            sendMessage(reply)
            waitFor(reply)
        }

        await(auth.signInWithEmailAndPassword(ownerEmail, TEST_PASSWORD))
        ActivityScenario.launch(MainActivity::class.java).use {
            openOwnerBooking(campsiteName)
            compose.onNodeWithText("Message Entrant").performScrollTo().performClick()
            waitFor(firstMessage)
            waitFor(reply)
            waitFor("$entrantName ·", substring = true)
            waitFor("You ·", substring = true)
            compose.onNodeWithText("BACK").performClick()
            waitFor(bookingId)
            pressBack()
            compose.onNodeWithText("Past").performClick()
            waitFor(campsiteName)
            compose.onNodeWithText(campsiteName).performClick()
            waitFor(pastBookingId)
            waitFor("Completed")
            compose.onNodeWithText("Cancel reservation").assertDoesNotExist()
            compose.onNodeWithText("Message Entrant").performScrollTo().performClick()
            waitFor("MESSAGES")
            compose.onNodeWithText(firstMessage).assertDoesNotExist()
            val pastMessage = "Thank you for staying at Whispering Pines."
            sendMessage(pastMessage)
            waitFor(pastMessage)
            pressBack()
            waitFor(pastBookingId)
            pressBack()
            compose.onNodeWithText("Past").assertIsSelected()
            waitFor(campsiteName)
        }

        val conversation = firestore.document("reservationChat/$bookingId")
        val messages = await(conversation.collection("messages").orderBy("sentAt").get(Source.SERVER))
        assertEquals(listOf(firstMessage, reply), messages.documents.map { it.getString("text") })
        assertEquals(listOf(ownerId, entrantId), messages.documents.map { it.getString("senderAccountId") })
        assertEquals(listOf(entrantId, ownerId), messages.documents.map { it.getString("recipientAccountId") })
        for (message in messages) {
            assertEquals(bookingId, message.getString("reservationId"))
            assertNotNull(message.getTimestamp("sentAt"))
        }
    }

    private fun openOwnerBooking(campsiteName: String) {
        openRole("Owner")
        waitFor("View Reservations")
        compose.onNodeWithText("View Reservations").performClick()
        waitFor(campsiteName)
        compose.onNodeWithText(campsiteName).performClick()
        waitFor("Reservation Details")
    }

    private fun openRole(role: String) {
        val enabledRole = hasText(role) and isEnabled()
        compose.waitUntil(conditionDescription = "$role role enabled", timeoutMillis = 15_000) {
            compose.onAllNodes(enabledRole).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(enabledRole).performClick()
    }

    private fun sendMessage(message: String) {
        waitFor("Type a message")
        compose.onNodeWithText("Type a message").performTextInput(message)
        compose.onNodeWithText("SEND").performClick()
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(conditionDescription = "text '$text'", timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun createProfile(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        email: String,
        name: String
    ): String {
        val user = checkNotNull(await(auth.createUserWithEmailAndPassword(email, TEST_PASSWORD)).user)
        val id = "auth_${user.uid}"
        val account = firestore.document("accounts/$id")
        await(account.set(mapOf("id" to id, "userType" to listOf("entrant", "owner"), "notificationsEnabled" to true)))
        await(account.update("name", name))
        return id
    }

    /** Seeds the booking a trusted writer would produce in the disposable demo emulator. */
    private fun seedConfirmedBooking(
        bookingId: String,
        campsiteId: String,
        entrantId: String,
        checkIn: Instant
    ) {
        val path = "projects/demo-zephyr-notifications/databases/(default)/documents"
        val uri = URI("http://127.0.0.1:8082/v1/$path/reservation/$bookingId")
        val checkOut = checkIn.plusSeconds(TimeUnit.DAYS.toSeconds(2))
        val fields = JSONObject()
            .put("accountId", JSONObject().put("stringValue", entrantId))
            .put("campsiteId", JSONObject().put("stringValue", campsiteId))
            .put("status", JSONObject().put("stringValue", "confirmed"))
            .put("cancelled", JSONObject().put("booleanValue", false))
            .put("bookedPeriodStart", JSONObject().put("timestampValue", checkIn.toString()))
            .put("bookedPeriodEnd", JSONObject().put("timestampValue", checkOut.toString()))
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PATCH"
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

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 15, TimeUnit.SECONDS)

    private companion object {
        const val TEST_PASSWORD = "zephyr-demo-password"
    }
}
