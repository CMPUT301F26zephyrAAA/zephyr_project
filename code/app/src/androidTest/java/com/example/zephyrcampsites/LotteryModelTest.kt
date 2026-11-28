// AI Citation [AI-40]
// Anthropic, Claude Code assisted with generating Firestore emulator
// tests for booking periods, overlap rechecks, and the edit cutoff.
// AI usage and citation documented in the team AI Documentation.
// Tests LotteryModel booking-period checks and the edit cutoff against the local Firestore emulator.
// Run with -Pandroid.testInstrumentationRunnerArguments.adminFirestoreEmulator=true and
// adb reverse tcp:8082 tcp:8082. Never points at the live Firebase project.
package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.Source
import java.util.concurrent.ExecutionException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies US 02.04.04 booking rules through the real LotteryModel and Firestore transactions. */
@RunWith(AndroidJUnit4::class)
class LotteryModelTest : AdminEmulatorTest() {
    private lateinit var model: LotteryModel
    private val campsiteId = "site-$suffix"
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L
    private val day = 24 * hour

    // Registration runs from tomorrow to the day after, so booking can start on day 3.
    private val registrationStart = now + day
    private val registrationEnd = now + 2 * day
    private val bookingDayStart = now + 3 * day

    // Lotteries created by the model are not tracked by AdminEmulatorTest.put.
    private val createdLotteryIds = mutableListOf<String>()

    @After
    fun removeCreatedLotteries() {
        if (this::model.isInitialized) {
            model.stopListening()
        }
        createdLotteryIds.forEach { await(firestore.document("lottery/$it").delete()) }
    }

    @Test
    fun singleDayBookingPeriodIsSaved() {
        startModel()
        val bookingEnd = bookingDayStart + day - 60_000L

        val lotteryId = await(create(bookingDayStart, bookingEnd).task)

        val saved = await(firestore.document("lottery/$lotteryId").get(Source.SERVER))
        assertEquals(bookingDayStart, saved.getLong("bookingStart"))
        assertEquals(bookingEnd, saved.getLong("bookingEnd"))
    }

    @Test
    fun equalBookingStartAndEndIsRejected() {
        startModel()

        assertThrows(IllegalArgumentException::class.java) {
            create(bookingDayStart, bookingDayStart)
        }
    }

    @Test
    fun bookingStartAtRegistrationEndIsRejected() {
        startModel()

        assertThrows(IllegalArgumentException::class.java) {
            create(registrationEnd, registrationEnd + day)
        }
    }

    @Test
    fun overlappingAndTouchingPeriodsAreRejectedForSameCampsite() {
        seedLottery("existing-$suffix", campsiteId, bookingDayStart, bookingDayStart + day)
        startModel()
        waitForLottery("existing-$suffix")

        assertThrows(IllegalArgumentException::class.java) {
            create(bookingDayStart + hour, bookingDayStart + 2 * day)
        }
        assertThrows(IllegalArgumentException::class.java) {
            create(bookingDayStart + day, bookingDayStart + 2 * day)
        }
    }

    @Test
    fun samePeriodOnAnotherCampsiteIsAllowed() {
        seedLottery("other-$suffix", "other-site-$suffix", bookingDayStart, bookingDayStart + day)
        startModel()
        waitForLottery("other-$suffix")

        await(create(bookingDayStart, bookingDayStart + day).task)
    }

    @Test
    fun createTransactionRejectsOverlapMissingFromLocalList() {
        startModel()
        model.stopListening()
        // Saved after the listener stopped, so only the transaction recheck can see it.
        seedLottery("late-$suffix", campsiteId, bookingDayStart, bookingDayStart + day)

        val error = awaitFailure(create(bookingDayStart, bookingDayStart + day).task)

        assertMessage(error, "overlaps")
    }

    @Test
    fun updateTransactionRejectsOverlapMissingFromLocalList() {
        seedLottery("edited-$suffix", campsiteId, bookingDayStart, bookingDayStart + day)
        startModel()
        waitForLottery("edited-$suffix")
        model.stopListening()
        seedLottery("late-$suffix", campsiteId, bookingDayStart + 5 * day, bookingDayStart + 6 * day)

        val error = awaitFailure(
            update("edited-$suffix", bookingDayStart + 5 * day, bookingDayStart + 6 * day)
        )

        assertMessage(error, "overlaps")
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
        startModel()
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
        startModel()
        waitForLottery("started-$suffix")

        await(update("started-$suffix", bookingDayStart, bookingDayStart + day, "Renamed"))

        val saved = await(firestore.document("lottery/started-$suffix").get(Source.SERVER))
        assertEquals("Renamed", saved.getString("lotteryName"))
    }

    @Test
    fun updateTransactionRejectsDateChangeWhenLocalCopyIsStale() {
        seedLottery("stale-$suffix", campsiteId, bookingDayStart, bookingDayStart + day)
        startModel()
        waitForLottery("stale-$suffix")
        model.stopListening()
        // Registration has now started in Firestore, but the local copy still shows it as future.
        await(
            firestore.document("lottery/stale-$suffix")
                .update("registrationStart", now - hour)
        )

        val error = awaitFailure(
            update("stale-$suffix", bookingDayStart + day, bookingDayStart + 2 * day)
        )

        assertMessage(error, "cannot be changed after registration starts")
    }

    private fun startModel() {
        model = LotteryModel(firestore)
    }

    private fun seedLottery(
        id: String,
        lotteryCampsiteId: String,
        bookingStart: Long,
        bookingEnd: Long,
        startedRegistration: Boolean = false
    ) {
        put(
            "lottery/$id",
            mapOf(
                "id" to id,
                "lotteryName" to "Seeded",
                "campsiteId" to lotteryCampsiteId,
                "registrationStart" to if (startedRegistration) now - hour else registrationStart,
                "registrationEnd" to registrationEnd,
                "bookingStart" to bookingStart,
                "bookingEnd" to bookingEnd,
                "numberOfWinners" to 1,
                "entrantCount" to 0L,
                "drawStatus" to "PENDING",
                "drawCompleted" to false,
            ),
        )
    }

    private fun create(bookingStart: Long, bookingEnd: Long): TaskCompletionSource<String> {
        val result = TaskCompletionSource<String>()
        val id = model.createLottery(
            lotteryName = "Test lottery",
            campsiteId = campsiteId,
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            numberOfWinners = 1,
            waitlistCapacity = null,
            onSuccess = { result.setResult(it) },
            onFailure = { result.setException(it) }
        )
        createdLotteryIds.add(id)
        return result
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
