// **AI Citation** [AI-35]
// OpenAI, ChatGPT assisted with Firestore emulator integration
// tests for reservation declines and automatic backup promotion.
// AI usage and citation documented in the GitHub Wiki.

package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for lottery backup promotion.
 *
 * Tests the actual ReservationOfferModel transactions
 * against the local Firestore emulator.
 *
 * Requires reservationFirestoreEmulator=true and
 * adb reverse tcp:8080 tcp:8080.
 *
 * Does not use the production Firebase database.
 */
@RunWith(AndroidJUnit4::class)
class ReservationOfferIntegrationTest {

    private lateinit var app: FirebaseApp
    private lateinit var firestore: FirebaseFirestore
    private lateinit var model: ReservationOfferModel

    private val suffix = UUID.randomUUID().toString()

    private val lotteryId = "offer-test-$suffix"
    private val campsiteId = "campsite-$suffix"

    private val winner1 = "winner1-$suffix"
    private val winner2 = "winner2-$suffix"

    private val backup1 = "backup1-$suffix"
    private val backup2 = "backup2-$suffix"
    private val backup3 = "backup3-$suffix"

    private val createdDocuments =
        mutableListOf<DocumentReference>()

    /**
     * Creates a separate Firebase instance connected
     * exclusively to the local emulator.
     */
    @Before
    fun connectToLocalEmulator() {

        assumeTrue(
            InstrumentationRegistry.getArguments()
                .getString("reservationFirestoreEmulator") == "true"
        )

        val options = FirebaseOptions.Builder()
            .setProjectId("demo-zephyr")
            .setApplicationId(
                "1:123456789:android:reservation-test"
            )
            .setApiKey("local-emulator-only")
            .build()

        app = FirebaseApp.initializeApp(
            InstrumentationRegistry.getInstrumentation()
                .targetContext,
            options,
            "reservation-$suffix"
        )

        firestore = FirebaseFirestore.getInstance(app)

        firestore.useEmulator("127.0.0.1", 8080)

        model = ReservationOfferModel(firestore)

        createLotteryFixtures()
    }

    /**
     * Creates a completed lottery containing
     * two winners and three ordered backups.
     */
    private fun createLotteryFixtures() {

        createDocument(
            "lottery/$lotteryId",
            mapOf(
                "id" to lotteryId,
                "campsiteId" to campsiteId,
                "numberOfWinners" to 2L,
                "entrantCount" to 5L,
                "drawCompleted" to true,
                "drawStatus" to "COMPLETED",
                "drawCompletedAt" to System.currentTimeMillis()
            )
        )

        createDocument(
            "lotteryDrawResults/$lotteryId",
            mapOf(
                "lotteryId" to lotteryId,
                "winnerIds" to listOf(winner1, winner2),
                "backupIds" to listOf(
                    backup1, backup2, backup3
                ),
                "declinedIds" to emptyList<String>(),
                "winnerCount" to 2L,
                "backupCount" to 3L,
                "completedAt" to System.currentTimeMillis()
            )
        )

        createEntrant(winner1, "WINNER", 0)
        createEntrant(winner2, "WINNER", 0)

        createEntrant(backup1, "BACKUP", 1)
        createEntrant(backup2, "BACKUP", 2)
        createEntrant(backup3, "BACKUP", 3)
    }

    /**
     * Creates an account and its canonical registration.
     */
    private fun createEntrant(
        accountId: String,
        status: String,
        position: Int
    ) {
        createDocument(
            "accounts/$accountId",
            mapOf(
                "id" to accountId,
                "userType" to "entrant",
                "deletionPending" to false
            )
        )

        val registrationId = "${lotteryId}_${accountId}"

        createDocument(
            "waitingList/$registrationId",
            mapOf(
                "id" to registrationId,
                "lotteryId" to lotteryId,
                "accountId" to accountId,
                "drawStatus" to status,
                "backupPosition" to position,
                "joinedAt" to System.currentTimeMillis()
            )
        )
    }

    // TEST 1: Normal backup promotion.

    @Test
    fun winnerDeclinePromotesFirstBackup() {

        await(decline(winner1))

        val result = loadResult()

        assertEquals(
            listOf(winner2, backup1),
            getIds(result, "winnerIds")
        )

        assertEquals(
            listOf(backup2, backup3),
            getIds(result, "backupIds")
        )

        assertEquals(
            listOf(winner1),
            getIds(result, "declinedIds")
        )

        assertEquals("DECLINED", loadStatus(winner1))
        assertEquals("WINNER", loadStatus(backup1))

        assertEquals(2L, result.getLong("winnerCount"))
        assertEquals(2L, result.getLong("backupCount"))
    }

    // TEST 2: Deleted backup account.

    @Test
    fun deletedBackupAccountIsSkipped() {

        await(
            firestore.document("accounts/$backup1").delete()
        )

        await(decline(winner1))

        val result = loadResult()

        assertEquals(
            listOf(winner2, backup2),
            getIds(result, "winnerIds")
        )

        assertEquals(
            listOf(backup3),
            getIds(result, "backupIds")
        )

        assertFalse(
            backup1 in getIds(result, "winnerIds")
        )

        assertEquals("WINNER", loadStatus(backup2))
    }

    // TEST 3: Account pending deletion.

    @Test
    fun backupPendingDeletionIsSkipped() {

        await(
            firestore.document("accounts/$backup1")
                .update("deletionPending", true)
        )

        await(decline(winner1))

        val result = loadResult()

        assertEquals(
            listOf(winner2, backup2),
            getIds(result, "winnerIds")
        )

        // Temporarily unavailable accounts remain in the queue.
        assertEquals(
            listOf(backup1, backup3),
            getIds(result, "backupIds")
        )

        assertEquals("BACKUP", loadStatus(backup1))
        assertEquals("WINNER", loadStatus(backup2))
    }

    // TEST 4: Missing waiting-list registration.

    @Test
    fun missingBackupRegistrationIsSkipped() {

        await(registrationRef(backup1).delete())

        await(decline(winner1))

        val result = loadResult()

        assertEquals(
            listOf(winner2, backup2),
            getIds(result, "winnerIds")
        )

        assertEquals(
            listOf(backup3),
            getIds(result, "backupIds")
        )

        assertEquals("WINNER", loadStatus(backup2))
    }

    // TEST 5: No eligible backups remain.

    @Test
    fun declineSucceedsWithoutEligibleBackups() {

        for (backupId in listOf(backup1, backup2, backup3)) {
            await(
                firestore.document("accounts/$backupId")
                    .delete()
            )
        }

        await(decline(winner1))

        val result = loadResult()

        assertEquals(
            listOf(winner2),
            getIds(result, "winnerIds")
        )

        assertTrue(
            getIds(result, "backupIds").isEmpty()
        )

        assertEquals("DECLINED", loadStatus(winner1))

        assertEquals(1L, result.getLong("winnerCount"))
        assertEquals(0L, result.getLong("backupCount"))
    }

    // TEST 6: Duplicate decline prevention.

    @Test
    fun winnerCannotDeclineTwice() {

        await(decline(winner1))

        val originalResult = loadResult().data

        assertDeclineRejected(winner1)

        assertEquals(
            originalResult,
            loadResult().data
        )

        assertEquals("DECLINED", loadStatus(winner1))
    }

    // TEST 7: Two winners decline.

    @Test
    fun twoWinnerDeclinesPromoteDifferentBackups() {

        val firstDecline = decline(winner1)
        val secondDecline = decline(winner2)

        await(
            Tasks.whenAll(firstDecline, secondDecline)
        )

        val result = loadResult()

        val winners = getIds(result, "winnerIds")
        val backups = getIds(result, "backupIds")
        val declined = getIds(result, "declinedIds")

        assertEquals(
            setOf(backup1, backup2),
            winners.toSet()
        )

        assertEquals(2, winners.size)
        assertEquals(2, winners.distinct().size)

        assertEquals(
            listOf(backup3),
            backups
        )

        assertEquals(
            setOf(winner1, winner2),
            declined.toSet()
        )

        assertEquals("DECLINED", loadStatus(winner1))
        assertEquals("DECLINED", loadStatus(winner2))

        assertEquals("WINNER", loadStatus(backup1))
        assertEquals("WINNER", loadStatus(backup2))

        assertEquals(2L, result.getLong("winnerCount"))
    }

    // TEST 8: Winner limit enforcement.

    @Test
    fun replacementDoesNotExceedWinnerLimit() {

        // Simulate a lottery whose configured limit is
        // now one, but which currently has two winners.
        await(
            firestore.document("lottery/$lotteryId")
                .update("numberOfWinners", 1L)
        )

        await(decline(winner1))

        val result = loadResult()

        assertEquals(
            listOf(winner2),
            getIds(result, "winnerIds")
        )

        assertEquals(
            listOf(backup1, backup2, backup3),
            getIds(result, "backupIds")
        )

        assertEquals(1L, result.getLong("winnerCount"))

        assertEquals("BACKUP", loadStatus(backup1))
        assertEquals("BACKUP", loadStatus(backup2))
        assertEquals("BACKUP", loadStatus(backup3))
    }

    // TEST 9: Backup positions are updated.

    @Test
    fun remainingBackupPositionsAreUpdated() {

        await(decline(winner1))

        val promoted = loadRegistration(backup1)
        val remaining1 = loadRegistration(backup2)
        val remaining2 = loadRegistration(backup3)

        assertEquals(
            0L,
            promoted.getLong("backupPosition")
        )

        assertEquals(
            1L,
            remaining1.getLong("backupPosition")
        )

        assertEquals(
            2L,
            remaining2.getLong("backupPosition")
        )
    }

    // TEST 10: Firestore results remain consistent.

    @Test
    fun updatedLotteryResultsPersistConsistently() {

        await(decline(winner1))

        val result = loadResult()

        val winners = getIds(result, "winnerIds")
        val backups = getIds(result, "backupIds")
        val declined = getIds(result, "declinedIds")

        assertEquals(
            winners.size.toLong(),
            result.getLong("winnerCount")
        )

        assertEquals(
            backups.size.toLong(),
            result.getLong("backupCount")
        )

        val allEntrants = winners + backups + declined

        assertEquals(
            allEntrants.size,
            allEntrants.distinct().size
        )

        assertEquals(5, allEntrants.size)

        for (accountId in winners) {
            assertEquals("WINNER", loadStatus(accountId))
        }

        for (accountId in backups) {
            assertEquals("BACKUP", loadStatus(accountId))
        }

        for (accountId in declined) {
            assertEquals("DECLINED", loadStatus(accountId))
        }

        val lottery = await(
            firestore.document("lottery/$lotteryId")
                .get(Source.SERVER)
        )

        assertEquals(
            true,
            lottery.getBoolean("drawCompleted")
        )

        assertEquals(
            "COMPLETED",
            lottery.getString("drawStatus")
        )

        val winnerLimit =
            lottery.getLong("numberOfWinners")!!

        assertTrue(
            winners.size.toLong() <= winnerLimit
        )
    }

    // Helper functions.

    private fun <T> await(task: Task<T>): T {
        return Tasks.await(
            task,
            30,
            TimeUnit.SECONDS
        )
    }

    private fun createDocument(
        path: String,
        data: Map<String, Any>
    ) {
        val ref = firestore.document(path)

        createdDocuments.add(ref)

        await(ref.set(data))
    }

    private fun registrationRef(
        accountId: String
    ): DocumentReference {
        return firestore.document(
            "waitingList/${lotteryId}_${accountId}"
        )
    }

    private fun loadRegistration(
        accountId: String
    ): DocumentSnapshot {
        return await(
            registrationRef(accountId).get(Source.SERVER)
        )
    }

    private fun loadStatus(accountId: String): String? {
        return loadRegistration(accountId)
            .getString("drawStatus")
    }

    private fun loadResult(): DocumentSnapshot {
        return await(
            firestore.document(
                "lotteryDrawResults/$lotteryId"
            ).get(Source.SERVER)
        )
    }

    private fun getIds(
        document: DocumentSnapshot,
        field: String
    ): List<String> {

        val values = document.get(field) as? List<*>
            ?: error("Missing result field: $field")

        return values.map {
            it as? String
                ?: error("Invalid account ID in $field")
        }
    }

    /**
     * Converts the model's callbacks into a Task
     * so the test can wait for transaction completion.
     */
    private fun decline(accountId: String): Task<Boolean> {

        val result = TaskCompletionSource<Boolean>()

        model.declineReservationOffer(
            lotteryId = lotteryId,
            accountId = accountId,
            onSuccess = {
                result.setResult(true)
            },
            onError = {
                result.setException(
                    IllegalStateException(it)
                )
            }
        )

        return result.task
    }

    private fun assertDeclineRejected(accountId: String) {

        try {
            await(decline(accountId))

            fail("The decline should have been rejected.")

        } catch (expected: ExecutionException) {

            assertTrue(
                expected.cause is IllegalStateException
            )
        }
    }

    /**
     * Removes only documents created by this test.
     */
    @After
    fun removeLocalFixtures() {

        if (this::firestore.isInitialized) {

            try {
                for (document in createdDocuments.reversed()) {
                    await(document.delete())
                }
            } finally {
                await(firestore.terminate())
            }
        }

        if (this::app.isInitialized) {
            app.delete()
        }
    }
}