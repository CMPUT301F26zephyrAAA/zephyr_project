// Exercises owner-to-entrant notification navigation on a local Firebase demo project.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 Android test, 2026-11-27. See the Bhav Saluja entry in README.md.
package com.example.zephyrcampsites

import android.provider.Settings
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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun ownerSendsFromLotteryDetailsAndEntrantReadsTheNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("notificationEmulator") == "true")
        val app = FirebaseApp.getInstance()
        assertEquals("demo-zephyr-notifications", app.options.projectId)
        val firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("127.0.0.1", 8082)
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("127.0.0.1", 9099)
        FirebaseFunctions.getInstance(app).useEmulator("127.0.0.1", 5001)

        val accountId = Settings.Secure.getString(
            instrumentation.targetContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val uid = checkNotNull(Tasks.await(auth.signInAnonymously(), 15, TimeUnit.SECONDS).user).uid
        val suffix = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val day = TimeUnit.DAYS.toMillis(1)
        val lottery = Lottery(id = "pines-$suffix").apply {
            lotteryName = "Whispering Pines $suffix"
            campsiteId = "7021001"
            entrantCount = 1
            registrationStart = now - day
            registrationEnd = now + day
            bookingStart = now + 2 * day
            bookingEnd = now + 5 * day
        }
        val campsite = Campsite(lottery.campsiteId).apply {
            name = "Whispering Pines"
            ownerID = accountId
        }
        val registration = WaitingUser("${lottery.id}_$accountId").apply {
            this.lotteryId = lottery.id
            this.accountId = accountId
            joinedAt = now
        }
        val otherLot = Lottery(id = "other-$suffix").apply {
            lotteryName = "Other campsite $suffix"
            campsiteId = "7021002"
        }
        val otherSite = Campsite(otherLot.campsiteId).apply {
            name = "Other campsite"
            ownerID = "another-owner"
        }
        val fixtures = mapOf(
            "accounts/$accountId" to Account(id = accountId, name = "Alex Camper"),
            "campsite/${campsite.id}" to campsite,
            "campsite/${otherSite.id}" to otherSite,
            "lottery/${lottery.id}" to lottery,
            "lottery/${otherLot.id}" to otherLot,
            "waitingList/${registration.id}" to registration
        )
        for ((path, document) in fixtures) {
            Tasks.await(firestore.document(path).set(document), 15, TimeUnit.SECONDS)
        }
        writeApprovedFixture("notificationAccountLinks/$accountId", mapOf("uid" to uid))
        writeApprovedFixture(
            "notificationLotteryOwners/${lottery.id}",
            mapOf("ownerAccountId" to accountId, "campsiteId" to campsite.id)
        )

        val message = "The south gate is closed. Use the north gate instead."
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(20_000) {
                    compose.onAllNodes(hasText("Owner") and isEnabled())
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText("Owner").performClick()
                compose.onNodeWithText("View Lotteries").performClick()
                waitFor(lottery.lotteryName)
                compose.onNodeWithText(otherLot.lotteryName).assertDoesNotExist()
                compose.onNodeWithText(lottery.lotteryName).performClick()
                compose.onNodeWithText("NOTIFY ENTRANTS").performScrollTo().performClick()
                waitFor("Alex Camper")
                compose.onNodeWithText("Send Notification").assertIsNotEnabled()
                compose.onNodeWithText("Message").performTextInput(message)
                compose.onNodeWithText("Send Notification").assertIsNotEnabled()
                compose.onNodeWithText("Alex Camper").performClick()
                compose.onNodeWithText("Send Notification").assertIsEnabled().performClick()
                waitFor("Notification saved. Delivered to 1 of 1 waitlisted entrants.")
                compose.onNodeWithText("Back").performClick()
                waitFor("LOTTERY DETAILS")
                compose.onNodeWithText("BACK").performScrollTo().performClick()
                waitFor("My Lotteries")
                compose.onNodeWithText("Back").performClick()
                waitFor("View Lotteries")
                compose.onNodeWithText("Back").performClick()
                waitFor("Entrant")
                compose.onNodeWithText("Entrant").performClick()
                compose.onNodeWithText("Notifications").performClick()
                waitFor(message)
                compose.onNodeWithText("Back").performClick()
                waitFor("Browse Campsites")
            }
        } finally {
            for (path in fixtures.keys) {
                Tasks.await(firestore.document(path).delete(), 15, TimeUnit.SECONDS)
            }
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Creates an administrator-provisioned record in the local demo emulator. */
    private fun writeApprovedFixture(path: String, fields: Map<String, String>) {
        val endpoint = "http://127.0.0.1:8082/v1/projects/demo-zephyr-notifications" +
            "/databases/(default)/documents/$path"
        val values = JSONObject()
        for ((key, value) in fields) {
            values.put(key, JSONObject().put("stringValue", value))
        }
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PATCH"
            connection.doOutput = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("fields", values).toString().toByteArray())
            }
            assertEquals(200, connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }
}
