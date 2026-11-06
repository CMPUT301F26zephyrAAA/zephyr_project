package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test

/** Verifies rating request validation, eligibility results, and model failures using a local fake. */
class RatingControllerTest {
    private val model = FakeRatingModel()
    private val controller = RatingController(model)
    private val entrant = Account("entrant-1")

    @Test
    fun eligibleEntrantReceivesSavedRating() {
        var loaded: CampsiteRating? = null
        controller.loadRating("1", entrant, { loaded = it }, { fail(it) })
        assertEquals(model.rating, loaded)
        assertEquals(1, model.loadCalls)
    }

    @Test
    fun noStayDoesNotOfferARating() {
        model.rating = null
        controller.loadRating("1", entrant, { assertNull(it) }, { fail(it) })
    }

    @Test
    fun missingAccountAndOtherRolesNeverLoadOrSave() {
        for (account in listOf(null, entrant.copy(userType = "owner"), entrant.copy(userType = "admin"))) {
            controller.loadRating("1", account, { assertNull(it) }, { fail(it) })
            controller.submitRating("1", account, CampsiteRating("stay", 4), { fail("Unexpected save") }, {})
        }
        assertEquals(0, model.loadCalls)
        assertEquals(0, model.saveCalls)
    }

    @Test
    fun invalidIdsDoNotReachTheModel() {
        for (id in listOf("", " ", "wrong/path")) {
            controller.loadRating(id, entrant, { assertNull(it) }, { fail(it) })
            controller.submitRating(id, entrant, CampsiteRating("stay", 4), { fail("Unexpected save") }, {})
            controller.submitRating("1", entrant.copy(id = id), CampsiteRating("stay", 4), { fail("Unexpected save") }, {})
            controller.submitRating("1", entrant, CampsiteRating(id, 4), { fail("Unexpected save") }, {})
        }
        assertEquals(0, model.loadCalls)
        assertEquals(0, model.saveCalls)
    }

    @Test
    fun missingAndOutOfRangeScoresAreRejected() {
        for (score in listOf(0, -1, 6)) {
            var error = ""
            controller.submitRating("1", entrant, CampsiteRating("stay", score), { fail("Unexpected save") }, { error = it })
            assertEquals("Select a rating from 1 to 5.", error)
        }
        assertEquals(0, model.saveCalls)
    }

    @Test
    fun reviewIsOptionalAndTrimmedBeforeSaving() {
        var saved = false
        controller.submitRating("1", entrant, CampsiteRating("stay", 1, "  Clean and quiet.  "), { saved = true }, { fail(it) })
        assertTrue(saved)
        assertEquals(CampsiteRating("stay", 1, "Clean and quiet."), model.rating)
        controller.submitRating("1", entrant, CampsiteRating("stay", 5), {}, { fail(it) })
        assertEquals("", model.rating?.review)
    }

    @Test
    fun saveFailureIsReportedWithoutCallingSuccess() {
        model.error = "Stay was cancelled."
        var error = ""
        val original = model.rating
        controller.submitRating("1", entrant, CampsiteRating("stay", 4), { fail("Unexpected success") }, { error = it })
        assertEquals("Stay was cancelled.", error)
        assertEquals(original, model.rating)
    }

    @Test
    fun eligibilityReadFailureIsReported() {
        model.error = "Unable to check stays."
        controller.loadRating("1", entrant, { fail("Unexpected result") }, { assertEquals(model.error, it) })
    }

    private class FakeRatingModel : RatingGateway {
        var loadCalls = 0
        var saveCalls = 0
        var rating: CampsiteRating? = CampsiteRating("stay", 3)
        var error: String? = null

        override fun loadRating(campsiteId: String, accountId: String, onResult: (CampsiteRating?) -> Unit, onError: (String) -> Unit) {
            loadCalls++
            val failure = error
            if (failure != null) { onError(failure) } else { onResult(rating) }
        }

        override fun submitRating(campsiteId: String, accountId: String, rating: CampsiteRating, onSuccess: () -> Unit, onError: (String) -> Unit) {
            saveCalls++
            val failure = error
            if (failure != null) {
                onError(failure)
            } else {
                this.rating = rating
                onSuccess()
            }
        }
    }
}
