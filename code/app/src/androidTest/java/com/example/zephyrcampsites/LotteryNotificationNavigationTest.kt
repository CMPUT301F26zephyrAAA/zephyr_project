// Exercises owner-to-entrant notification navigation on a local Firebase demo project.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 Android test, 2026-11-27. See the Bhav Saluja entry in README.md.
package com.example.zephyrcampsites

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import java.io.File
import java.time.Instant
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the demo Firebase configuration, running emulators, and reversed Firebase ports. */
@RunWith(AndroidJUnit4::class)
class LotteryNotificationNavigationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun freshProfilesSendReceiveOptOutAndCompleteProfileAndBookingFlows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("notificationEmulator") == "true")
        val app = FirebaseApp.getInstance()
        assertEquals("demo-zephyr-notifications", app.options.projectId)
        val firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("127.0.0.1", 8082)
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("127.0.0.1", 9099)
        FirebaseFunctions.getInstance(app).useEmulator("127.0.0.1", 5001)

        val suffix = UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val lottery = Lottery(id = "pines-$suffix").apply {
            lotteryName = "Whispering Pines $suffix"
            campsiteId = (UUID.randomUUID().hashCode() and Int.MAX_VALUE).toString()
            registrationStart = now - day
            registrationEnd = now + day
            bookingStart = now + 2 * day
            bookingEnd = now + 5 * day
        }
        val ownerEmail = "owner-$suffix@example.test"
        val entrantEmail = "entrant-$suffix@example.test"
        val optedOutEmail = "opted-out-$suffix@example.test"

        freshProfile(auth, firestore, ownerEmail) { ownerId ->
            val campsite = Campsite(lottery.campsiteId).apply {
                name = "Whispering Pines"
                ownerID = ownerId
            }
            Tasks.await(firestore.document("campsite/${campsite.id}").set(campsite), 15, TimeUnit.SECONDS)
            Tasks.await(firestore.document("lottery/${lottery.id}").set(lottery), 15, TimeUnit.SECONDS)
        }
        lateinit var entrantAccountId: String
        freshProfile(auth, firestore, entrantEmail) { entrantId ->
            entrantAccountId = entrantId
            Tasks.await(
                firestore.document("accounts/$entrantId").update("name", "Taylor North"),
                15, TimeUnit.SECONDS
            )
            join(firestore, lottery.id, entrantId)
        }
        freshProfile(auth, firestore, optedOutEmail) { entrantId ->
            Tasks.await(
                firestore.document("accounts/$entrantId").update("name", "Morgan Lake"),
                15, TimeUnit.SECONDS
            )
            join(firestore, lottery.id, entrantId)
            val saved = TaskCompletionSource<Boolean>()
            AccountsModel(database = firestore).updateNotificationPreference(
                deviceId = entrantId,
                enabled = false,
                onSuccess = { saved.setResult(true) },
                onError = { saved.setException(it) }
            )
            Tasks.await(saved.task, 15, TimeUnit.SECONDS)
        }

        val message = "The south gate is closed. Use the north gate instead."
        signIn(auth, ownerEmail)
        val ownerId = "auth_${checkNotNull(auth.currentUser).uid}"
        val emptyHistory = Tasks.await(LotteryNotificationModel().loadSent(ownerId), 15, TimeUnit.SECONDS)
        assertTrue(emptyHistory.isEmpty())
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForAccount()
            compose.onNodeWithText("Owner").performClick()
            // AI Citation [AI-40]
            // OpenAI, Codex, US 02.10.02, 2026-11-27. Generated the history navigation assertions.
            // See Bhav's entry in the team's AI Documentation Google Doc.
            compose.onNodeWithText("Sent Notifications").performClick()
            waitFor("You haven’t sent any notifications yet.")
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("View Lotteries").performClick()
            waitFor(lottery.lotteryName)
            compose.onNodeWithText(lottery.lotteryName).performClick()
            compose.onNodeWithText("NOTIFY ENTRANTS").performScrollTo().performClick()
            waitFor("Everyone on the waitlist")
            compose.onNodeWithText("Send Notification").assertIsNotEnabled()
            compose.onNodeWithText("Message").performTextInput(message)
            compose.onNodeWithText("Send Notification").assertIsNotEnabled()
            compose.onNodeWithText("Everyone on the waitlist").performClick()
            compose.onNodeWithText("Send Notification").assertIsEnabled().performClick()
            waitFor("Notification saved. Delivered to 1 of 2 waitlisted entrants.")
            compose.onNodeWithText("View Sent Notifications").performClick()
            waitFor(message)
            waitFor(lottery.lotteryName)
            compose.onNodeWithText(message).performClick()
            waitFor("Notification Details")
            waitFor("Taylor North")
            waitFor("Delivered to inbox")
            waitFor("Morgan Lake")
            waitFor("Not delivered")
            compose.onNodeWithText("Back").performClick()
            waitFor("Sent Notifications")
            waitFor(message)
            compose.onNodeWithText("Back").performClick()
            waitFor("Notify Entrants")
            compose.onNodeWithText("Back").performClick()
            waitFor("LOTTERY DETAILS")
        }

        signIn(auth, entrantEmail)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForAccount()
            compose.onNodeWithText("Entrant").performClick()
            compose.onNodeWithText("Sent Notifications").assertDoesNotExist()
            compose.onNodeWithText("Notifications").performClick()
            waitFor(message)
            compose.onNodeWithText("Back").performClick()
            waitFor("Browse Campsites")
        }

        signIn(auth, optedOutEmail)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForAccount()
            compose.onNodeWithText("Entrant").performClick()
            compose.onNodeWithText("Notifications").performClick()
            waitFor("Notifications are turned off in Settings.")
            compose.onNodeWithText(message).assertDoesNotExist()
            val optedOutId = "auth_${checkNotNull(auth.currentUser).uid}"
            val inbox = LotteryNotificationModel().loadReceived(optedOutId)
            assertTrue(Tasks.await(inbox, 15, TimeUnit.SECONDS).isEmpty())
        }

        val reservationPath = "reservation/owner-cancel-$suffix"
        val checkIn = Instant.now().plusSeconds(7200)
        writeApprovedFixture(reservationPath, mapOf(
            "accountId" to entrantAccountId, "campsiteId" to lottery.campsiteId,
            "bookedPeriodStart" to checkIn, "bookedPeriodEnd" to checkIn.plusSeconds(86400),
            "cancelled" to false
        ))
        signIn(auth, ownerEmail)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForAccount()
            compose.onNodeWithText("Owner").performClick()
            compose.onNodeWithText("View Reservations").performClick()
            waitFor("Whispering Pines")
            waitFor("Upcoming")
            compose.onNodeWithText("Cancel reservation").performClick()
            compose.onNodeWithText("Confirm cancellation").performClick()
            waitFor("Cancelled")
            val saved = Tasks.await(firestore.document(reservationPath).get(Source.SERVER), 15, TimeUnit.SECONDS)
            assertEquals(true, saved.getBoolean("cancelled"))
            assertEquals("auth_" + checkNotNull(auth.currentUser).uid, saved.getString("cancelledBy"))
        }
        writeApprovedFixture(reservationPath, null)

        signIn(auth, optedOutEmail)
        val uid = checkNotNull(auth.currentUser).uid
        val account = Account("auth_" + uid)
        val context = instrumentation.targetContext
        val photo = File.createTempFile("profile-", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val controller = AccountsController()
            val updated = TaskCompletionSource<Account>()
            controller.updateOwnProfileImage(context, account, Uri.fromFile(photo),
                onSuccess = { updated.setResult(it) },
                onError = { updated.setException(IllegalStateException(it)) }
            )
            assertTrue(Tasks.await(updated.task, 15, TimeUnit.SECONDS).compressedImage.isNotBlank())
            val profile = firestore.document("accounts/" + account.id)
            val saved = Tasks.await(profile.get(Source.SERVER), 15, TimeUnit.SECONDS)
            assertTrue(saved.getString("compressedImage").orEmpty().isNotBlank())
            assertEquals(false, saved.getBoolean("notificationsEnabled"))
            val deleted = TaskCompletionSource<Unit>()
            controller.deleteOwnProfile(account, { deleted.setResult(Unit) }, { deleted.setException(it) })
            Tasks.await(deleted.task, 15, TimeUnit.SECONDS)
            assertFalse(Tasks.await(profile.get(Source.SERVER), 15, TimeUnit.SECONDS).exists())
        } finally {
            photo.delete()
        }
    }

    /** Starts with no Auth session, then adds a demo-only sign-in for switching test roles. */
    private fun freshProfile(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        email: String,
        action: (String) -> Unit
    ) {
        auth.signOut()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForAccount()
            val user = checkNotNull(auth.currentUser)
            assertTrue(user.isAnonymous)
            val accountId = "auth_${user.uid}"
            val profile = Tasks.await(
                firestore.document("accounts/$accountId").get(Source.SERVER), 15, TimeUnit.SECONDS
            )
            assertEquals(accountId, profile.getString("id"))
            assertEquals(true, profile.getBoolean("notificationsEnabled"))
            val credentials = EmailAuthProvider.getCredential(email, "local-demo-password")
            Tasks.await(user.linkWithCredential(credentials), 15, TimeUnit.SECONDS)
            action(accountId)
        }
    }

    private fun signIn(auth: FirebaseAuth, email: String) {
        auth.signOut()
        Tasks.await(auth.signInWithEmailAndPassword(email, "local-demo-password"), 15, TimeUnit.SECONDS)
    }

    private fun join(firestore: FirebaseFirestore, lotteryId: String, entrantId: String) {
        val joined = TaskCompletionSource<Boolean>()
        WaitingListModel(firestore).joinWaitingList(
            lotteryId,
            entrantId,
            onSuccess = { joined.setResult(true) },
            onError = { joined.setException(IllegalStateException(it)) }
        )
        Tasks.await(joined.task, 15, TimeUnit.SECONDS)
    }

    private fun waitForAccount() {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasText("Owner") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Creates or removes a booking fixture in the local demo emulator. */
    private fun writeApprovedFixture(path: String, fields: Map<String, Any>?) {
        val endpoint = "http://127.0.0.1:8082/v1/projects/demo-zephyr-notifications" +
            "/databases/(default)/documents/$path"
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Authorization", "Bearer owner")
            if (fields == null) {
                connection.requestMethod = "DELETE"
            } else {
                val values = JSONObject()
                for ((key, value) in fields) {
                    val field = when (value) {
                        is Boolean -> JSONObject().put("booleanValue", value)
                        is Instant -> JSONObject().put("timestampValue", value.toString())
                        else -> JSONObject().put("stringValue", value.toString())
                    }
                    values.put(key, field)
                }
                connection.requestMethod = "PATCH"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use {
                    it.write(JSONObject().put("fields", values).toString().toByteArray())
                }
            }
            assertEquals(200, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }
}
