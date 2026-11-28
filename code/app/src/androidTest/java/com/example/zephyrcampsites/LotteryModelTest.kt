// AI Citation [AI-40], [AI-41], [AI-42]
// Anthropic, Claude Code assisted with generating emulator tests for
// booking periods, server-side overlap checks, and the edit cutoff.
// AI usage and citation documented in the team AI Documentation.
// Tests LotteryModel booking checks through the local Auth, Firestore, and Functions emulators.
// Run with -Pandroid.testInstrumentationRunnerArguments.lotteryFunctionsEmulator=true and
// adb reverse for ports 8082, 9099, and 5001. Never points at the live Firebase project.
package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies US 02.04.04 booking rules through the real LotteryModel and saveLotterySettings. */
@RunWith(AndroidJUnit4::class)
class LotteryModelTest {
    private companion object {
        // Error saveLotterySettings returns for an overlapping booking period.
        const val OVERLAP_MESSAGE = "already has a lottery in that period"
    }

    private lateinit var firestore: FirebaseFirestore
    private lateinit var model: LotteryModel
    private val suffix = UUID.randomUUID().toString()
    private val campsiteId = "site-$suffix"
    private val otherCampsiteId = "other-site-$suffix"
    private val hour = 3_600_000L
    private val day = 24 * hour

    // The app checks dates with the device clock and the functions use server time, and an
    // emulator's clock can be a day off. Future dates use the later clock and started
    // registrations use the earlier one, so both sides agree.
    private var futureBase = 0L
    private var ownerId = ""
    private var startedTime = 0L

    // Registration runs from tomorrow to the day after, so booking can start on day 3.
    private val registrationStart get() = futureBase + day
    private val registrationEnd get() = futureBase + 2 * day
    private val bookingDayStart get() = futureBase + 3 * day

    @Before
    fun connectToEmulators() {
        assumeTrue(
            InstrumentationRegistry.getArguments()
                .getString("lotteryFunctionsEmulator") == "true"
        )
        firestore = FirebaseWorkflowFixtures.firestore
        val deviceNow = System.currentTimeMillis()
        val serverNow = readServerTime()
        futureBase = maxOf(deviceNow, serverNow)
        startedTime = minOf(deviceNow, serverNow) - hour
        val session = FirebaseWorkflowFixtures.primary
        val auth = FirebaseAuth.getInstance(session)
        auth.signOut()
        val ownerId = "auth_${checkNotNull(await(auth.signInAnonymously()).user).uid}"

        // saveLotterySettings requires a saved account for the signed-in owner.
        await(firestore.document("accounts/$ownerId").set(mapOf("id" to ownerId)))
        this.ownerId = ownerId

        // Both campsites belong to the signed-in owner, so the functions accept the saves.
        for (id in listOf(campsiteId, otherCampsiteId)) {
            await(
                firestore.document("campsite/$id")
                    .set(mapOf("id" to id, "ownerID" to ownerId))
            )
        }
        model = LotteryModel(firestore, FirebaseFunctions.getInstance(session))
    }

    @After
    fun removeTestData() {
        if (!this::model.isInitialized) {
            return
        }
        model.stopListening()
        for (id in listOf(campsiteId, otherCampsiteId)) {
            val saved = await(
                firestore.collection("lottery").whereEqualTo("campsiteId", id).get()
            )
            saved.documents.forEach { await(it.reference.delete()) }
            await(firestore.document("campsite/$id").delete())
        }
        await(firestore.document("accounts/$ownerId").delete())
    }

    @Test
    fun singleDayBookingPeriodIsSaved() {
        val bookingEnd = bookingDayStart + day - 60_000L

        val lotteryId = await(create(bookingDayStart, bookingEnd))

        val saved = await(firestore.document("lottery/$lotteryId").get(Source.SERVER))
        assertEquals(bookingDayStart, saved.getLong("bookingStart"))
        assertEquals(bookingEnd, saved.getLong("bookingEnd"))
    }

    @Test
    fun equalBookingStartAndEndIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            create(bookingDayStart, bookingDayStart)
        }
    }

    @Test
    fun bookingStartAtRegistrationEndIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            create(registrationEnd, registrationEnd + day)
        }
    }

    @Test
    fun overlappingAndTouchingPeriodsAreRejectedForSameCampsite() {
        seedLottery("existing-$suffix", campsiteId, bookingDayStart, bookingDayStart + day)

        val overlap = awaitFailure(create(bookingDayStart + hour, bookingDayStart + 2 * day))
        assertMessage(overlap, OVERLAP_MESSAGE)
        val touching = awaitFailure(create(bookingDayStart + day, bookingDayStart + 2 * day))
        assertMessage(touching, OVERLAP_MESSAGE)
    }

    @Test
    fun secondarySiteInSharedLotteryBlocksOverlap() {
        await(
            create(bookingDayStart, bookingDayStart + day, listOf(campsiteId, otherCampsiteId))
        )

        val error = awaitFailure(
            create(bookingDayStart, bookingDayStart + day, listOf(otherCampsiteId))
        )

        assertMessage(error, OVERLAP_MESSAGE)
    }

    @Test
    fun samePeriodOnAnotherCampsiteIsAllowed() {
        seedLottery("other-$suffix", otherCampsiteId, bookingDayStart, bookingDayStart + day)

        await(create(bookingDayStart, bookingDayStart + day))
    }

    @Test
    fun serverRejectsOverlapMissingFromLocalList() {
        model.stopListening()
        // Saved after the listener stopped, so only the function's check can see it.
        seedLottery("late-$suffix", campsiteId, bookingDayStart, bookingDayStart + day)

        val error = awaitFailure(create(bookingDayStart, bookingDayStart + day))

        assertMessage(error, OVERLAP_MESSAGE)
    }

    @Test
    fun bookingDatesCanMoveBeforeRegistrationStarts() {
        seedLottery("edited-$suffix", campsiteId, bookingDayStart, bookingDayStart + day)
        waitForLottery("edited-$suffix")

        await(update("edited-$suffix", bookingDayStart + 5 * day, bookingDayStart + 6 * day))

        val saved = await(firestore.document("lottery/edited-$suffix").get(Source.SERVER))
        assertEquals(bookingDayStart + 5 * day, saved.getLong("bookingStart"))
    }

    @Test
    fun bookingDatesCannotChangeAfterRegistrationStarts() {
        seedLottery(
            "started-$suffix",
            campsiteId,
            bookingDayStart,
            bookingDayStart + day,
            startedRegistration = true
        )
        waitForLottery("started-$suffix")

        assertThrows(IllegalArgumentException::class.java) {
            update("started-$suffix", bookingDayStart + day, bookingDayStart + 2 * day)
        }
    }

    @Test
    fun unchangedDatesCanBeSavedAfterRegistrationStarts() {
        seedLottery(
            "started-$suffix",
            campsiteId,
            bookingDayStart,
            bookingDayStart + day,
            startedRegistration = true
        )
        waitForLottery("started-$suffix")

        await(update("started-$suffix", bookingDayStart, bookingDayStart + day, "Renamed"))

        val saved = await(firestore.document("lottery/started-$suffix").get(Source.SERVER))
        assertEquals("Renamed", saved.getString("lotteryName"))
    }

    @Test
    fun serverRejectsDateChangeWhenLocalCopyIsStale() {
        seedLottery("stale-$suffix", campsiteId, bookingDayStart, bookingDayStart + day)
        waitForLottery("stale-$suffix")
        model.stopListening()
        // Registration has now started on the server, but the local copy still shows it as future.
        await(
            firestore.document("lottery/stale-$suffix")
                .update("registrationStart", startedTime)
        )

        val error = awaitFailure(
            update("stale-$suffix", bookingDayStart + day, bookingDayStart + 2 * day)
        )

        assertMessage(error, "locked after registration opens")
    }

    private fun seedLottery(
        id: String,
        lotteryCampsiteId: String,
        bookingStart: Long,
        bookingEnd: Long,
        startedRegistration: Boolean = false
    ) {
        await(
            firestore.document("lottery/$id").set(
                mapOf(
                    "id" to id,
                    "lotteryName" to "Seeded",
                    "campsiteId" to lotteryCampsiteId,
                    "registrationStart" to
                            if (startedRegistration) startedTime else registrationStart,
                    "registrationEnd" to registrationEnd,
                    "bookingStart" to bookingStart,
                    "bookingEnd" to bookingEnd,
                    "numberOfWinners" to 1,
                    "entrantCount" to 0L,
                    "drawStatus" to "PENDING",
                    "drawCompleted" to false,
                )
            )
        )
    }

    private fun create(
        bookingStart: Long,
        bookingEnd: Long,
        campsiteIds: List<String> = listOf(campsiteId)
    ): Task<String> {
        val result = TaskCompletionSource<String>()
        model.createLottery(
            lotteryName = "Test lottery",
            campsiteIds = campsiteIds,
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = 1,
            waitlistCapacity = null,
            onSuccess = { result.setResult(it) },
            onFailure = { result.setException(it) }
        )
        return result.task
    }

    private fun update(
        lotteryId: String,
        bookingStart: Long,
        bookingEnd: Long,
        lotteryName: String = "Seeded"
    ): Task<Unit> {
        val result = TaskCompletionSource<Unit>()
        // Send the registration start the local copy holds, as the Edit Lottery screen does.
        val localRegistrationStart = model.lotteries.first { it.id == lotteryId }.registrationStart
        model.updateLottery(
            lotteryId = lotteryId,
            lotteryName = lotteryName,
            registrationStart = localRegistrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = 1,
            waitlistCapacity = null,
            onSuccess = { result.setResult(Unit) },
            onFailure = { result.setException(it) }
        )
        return result.task
    }

    private fun waitForLottery(lotteryId: String) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (model.lotteries.none { it.id == lotteryId }) {
            if (System.currentTimeMillis() > deadline) {
                fail("Lottery $lotteryId never reached the local list.")
            }
            Thread.sleep(50)
        }
    }

    /** Reads the emulator's server time by saving and reading back a server timestamp. */
    private fun readServerTime(): Long {
        val clock = firestore.document("serverTime/$suffix")
        await(clock.set(mapOf("now" to FieldValue.serverTimestamp())))
        val time = checkNotNull(await(clock.get(Source.SERVER)).getTimestamp("now"))
        await(clock.delete())
        return time.toDate().time
    }

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 20, TimeUnit.SECONDS)

    private fun awaitFailure(task: Task<*>): Throwable {
        try {
            await(task)
        } catch (e: ExecutionException) {
            return e.cause ?: e
        }
        fail("Expected the save to fail.")
        throw AssertionError()
    }

    private fun assertMessage(error: Throwable, expected: String) {
        val messages = generateSequence(error) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(
            "Expected \"$expected\" in $messages",
            messages.any { it.contains(expected) }
        )
    }
}
