package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies rating reads and callable writes against the demo Firebase workflow emulators.
 * Requires ratingFirestoreEmulator=true and reversed TCP ports 8082, 9099, and 5001.
 */
@RunWith(AndroidJUnit4::class)
class RatingModelTest {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var model: RatingModel
    private val suffix = UUID.randomUUID().toString()
    private lateinit var accountId: String
    private val accountModels = mutableMapOf<String, RatingModel>()
    private val campsiteId = "campsite-$suffix"
    private val reservationId = "stay-$suffix"
    private val createdDocuments = mutableListOf<com.google.firebase.firestore.DocumentReference>()

    @Before
    fun connectToLocalEmulator() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ratingFirestoreEmulator") == "true")
        firestore = FirebaseWorkflowFixtures.firestore
        accountId = signIn(FirebaseWorkflowFixtures.primary)
        model = accountModels.getValue(accountId)
        createDocument("accounts/$accountId", mapOf("userType" to "entrant"))
        createDocument("campsite/$campsiteId", mapOf("id" to campsiteId, "name" to "Pine Lake Campground", "rating" to listOf(4, 5)))
        createDocument("reservation/$reservationId", stayData(accountId))
    }

    @After
    fun removeLocalFixtures() {
        if (::accountId.isInitialized) {
            await(firestore.document("campsite/$campsiteId/ratings/$accountId").delete())
        }
        for (document in createdDocuments.reversed()) {
            await(document.delete())
        }
    }

    @Test
    fun firstRatingAndReplacementPersistWithCorrectAverage() {
        assertEquals(CampsiteRating(reservationId), load())
        await(submit(CampsiteRating(reservationId, 2, "Quiet lakeside stay.")))
        var campsite = await(firestore.document("campsite/$campsiteId").get(Source.SERVER)).toObject(Campsite::class.java)!!
        assertEquals(listOf(4, 5, 2), campsite.rating)
        assertEquals(11.0 / 3, campsite.averageRating!!, 0.0001)
        assertEquals(CampsiteRating(reservationId, 2, "Quiet lakeside stay."), load())

        await(submit(CampsiteRating(reservationId, 5, "Would stay again.")))
        campsite = await(firestore.document("campsite/$campsiteId").get(Source.SERVER)).toObject(Campsite::class.java)!!
        assertEquals(listOf(4, 5, 5), campsite.rating)
        assertEquals(14.0 / 3, campsite.averageRating!!, 0.0001)
        assertEquals(1, await(firestore.collection("campsite/$campsiteId/ratings").get(Source.SERVER)).size())
        assertEquals(CampsiteRating(reservationId, 5, "Would stay again."), load())
    }

    @Test
    fun writtenReviewAndStarRatingCanBeSavedIndependently() {
        await(firestore.document("accounts/$accountId").update("userType", listOf("entrant", "owner")))
        val ratingRef = firestore.document("campsite/$campsiteId/ratings/$accountId")
        await(submitReview("  Quiet lakeside stay.  "))
        val reviewOnly = await(ratingRef.get(Source.SERVER))
        assertEquals(accountId, reviewOnly.getString("accountId"))
        assertEquals(campsiteId, reviewOnly.getString("campsiteId"))
        assertEquals(reservationId, reviewOnly.getString("reservationId"))
        assertEquals("Quiet lakeside stay.", reviewOnly.getString("review"))
        assertFalse(reviewOnly.contains("score"))

        await(submit(load()!!.copy(score = 3)))
        val listed = TaskCompletionSource<List<String>>()
        val listener = model.observeReviews(campsiteId, { reviews ->
            if ("Would stay again." in reviews) listed.trySetResult(reviews)
        }, { listed.trySetException(IllegalStateException(it)) })
        try {
            await(submitReview("Would stay again."))
            assertEquals(listOf("Would stay again."), await(listed.task))
            assertEquals(3L, await(ratingRef.get(Source.SERVER)).getLong("score"))
            assertEquals(listOf(4L, 5L, 3L), await(firestore.document("campsite/$campsiteId").get(Source.SERVER)).get("rating"))
        } finally {
            listener.remove()
        }
    }

    @Test
    fun blankReviewsAndCancelledStaysAreRejected() {
        org.junit.Assert.assertThrows(java.util.concurrent.ExecutionException::class.java) {
            await(submitReview("   "))
        }
        await(firestore.document("reservation/$reservationId").update("cancelled", true))
        org.junit.Assert.assertThrows(java.util.concurrent.ExecutionException::class.java) {
            await(submitReview("This stay was cancelled."))
        }
        assertFalse(await(firestore.document("campsite/$campsiteId/ratings/$accountId").get(Source.SERVER)).exists())
    }

    @Test
    fun deletionPendingBlocksRatingAndReviewAfterOpeningForm() {
        val eligible = load()!!
        await(firestore.document("accounts/$accountId").update("deletionPending", true))

        assertRejected(eligible.copy(score = 4))
        org.junit.Assert.assertThrows(java.util.concurrent.ExecutionException::class.java) {
            await(submitReview("Quiet lakeside stay."))
        }
        assertFalse(await(firestore.document("campsite/$campsiteId/ratings/$accountId").get(Source.SERVER)).exists())
        assertEquals(listOf(4L, 5L), await(firestore.document("campsite/$campsiteId").get(Source.SERVER)).get("rating"))
    }

    @Test
    fun concurrentEntrantsDoNotOverwriteEachOthersRatings() {
        val otherAccount = signIn(FirebaseWorkflowFixtures.secondary)
        val otherStay = "other-stay-$suffix"
        createDocument("accounts/$otherAccount", mapOf("userType" to "entrant"))
        createDocument("reservation/$otherStay", stayData(otherAccount))
        val otherRating = firestore.document("campsite/$campsiteId/ratings/$otherAccount")
        createdDocuments.add(otherRating)
        val first = submit(CampsiteRating(reservationId, 1))
        val second = submit(CampsiteRating(otherStay, 3), otherAccount)
        await(Tasks.whenAll(first, second))
        val campsite = await(firestore.document("campsite/$campsiteId").get(Source.SERVER))
        assertEquals(listOf(1L, 3L, 4L, 5L), (campsite.get("rating") as List<*>).sortedBy { (it as Number).toInt() })
        assertEquals(3.25, campsite.getDouble("averageRating")!!, 0.0001)
        assertEquals(2, await(firestore.collection("campsite/$campsiteId/ratings").get(Source.SERVER)).size())
    }

    @Test
    fun cancelledFutureAndOtherEntrantsStaysAreNotOfferedOrAccepted() {
        val ref = firestore.document("reservation/$reservationId")
        for (change in listOf(
            mapOf("cancelled" to true), mapOf("accountId" to "another-entrant"),
            mapOf("campsiteId" to "another-campsite"),
            mapOf("bookedPeriodEnd" to Date(System.currentTimeMillis() + 86400000))
        )) {
            await(ref.set(stayData(accountId) + change))
            assertNull(load())
            assertRejected(CampsiteRating(reservationId, 4))
        }
        assertEquals(listOf(4L, 5L), await(firestore.document("campsite/$campsiteId").get(Source.SERVER)).get("rating"))
    }

    @Test
    fun stayCancelledAfterOpeningFormIsRejectedOnSubmit() {
        val eligible = load()!!
        await(firestore.document("reservation/$reservationId").update("cancelled", true))
        assertRejected(eligible.copy(score = 4))
        assertFalse(await(firestore.document("campsite/$campsiteId/ratings/$accountId").get(Source.SERVER)).exists())
    }

    @Test
    fun missingOrMalformedReservationFieldsFailClosed() {
        for (field in listOf("bookedPeriodStart", "bookedPeriodEnd", "cancelled")) {
            val ref = firestore.document("reservation/$reservationId")
            await(ref.set(stayData(accountId)))
            await(ref.update(field, FieldValue.delete()))
            assertNull(load())
            assertRejected(CampsiteRating(reservationId, 4))
        }
        for (change in listOf(mapOf("cancelled" to "false"), mapOf("bookedPeriodEnd" to "yesterday"))) {
            await(firestore.document("reservation/$reservationId").set(stayData(accountId) + change))
            assertNull(load())
            assertRejected(CampsiteRating(reservationId, 4))
        }
    }

    @Test
    fun ownerCannotSubmitByCallingTheModelDirectly() {
        await(firestore.document("accounts/$accountId").update("userType", "owner"))
        assertRejected(CampsiteRating(reservationId, 4))
    }

    @Test
    fun deletedCampsiteDoesNotCreateAnOrphanRating() {
        await(firestore.document("campsite/$campsiteId").delete())
        assertRejected(CampsiteRating(reservationId, 4))
        assertFalse(await(firestore.document("campsite/$campsiteId/ratings/$accountId").get(Source.SERVER)).exists())
    }

    private fun signIn(session: FirebaseApp): String {
        val auth = FirebaseAuth.getInstance(session)
        auth.signOut()
        val id = "auth_${checkNotNull(await(auth.signInAnonymously()).user).uid}"
        val functions = FirebaseFunctions.getInstance(session)
        accountModels[id] = RatingModel(firestore, functions)
        return id
    }

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)

    private fun createDocument(path: String, data: Map<String, Any>) {
        val ref = firestore.document(path)
        await(ref.set(data))
        createdDocuments.add(ref)
    }

    private fun stayData(entrantId: String): Map<String, Any> = mapOf(
        "accountId" to entrantId, "campsiteId" to campsiteId, "cancelled" to false,
        "bookedPeriodStart" to Date(System.currentTimeMillis() - 172800000),
        "bookedPeriodEnd" to Date(System.currentTimeMillis() - 86400000)
    )

    private fun load(): CampsiteRating? {
        val result = TaskCompletionSource<CampsiteRating?>()
        model.loadRating(campsiteId, accountId, { result.setResult(it) }, { result.setException(IllegalStateException(it)) })
        return await(result.task)
    }

    private fun submit(rating: CampsiteRating, entrantId: String = accountId): Task<Boolean> {
        val result = TaskCompletionSource<Boolean>()
        accountModels.getValue(entrantId).submitRating(campsiteId, entrantId, rating, { result.setResult(true) }, { result.setException(IllegalStateException(it)) })
        return result.task
    }

    private fun submitReview(review: String): Task<Unit> {
        val result = TaskCompletionSource<Unit>()
        model.submitReview(campsiteId, accountId, reservationId, review,
            { result.setResult(Unit) }, { result.setException(IllegalStateException(it)) })
        return result.task
    }

    private fun assertRejected(rating: CampsiteRating) {
        try {
            await(submit(rating))
            fail("An ineligible rating must not be saved.")
        } catch (expected: java.util.concurrent.ExecutionException) {
            assertTrue(expected.cause is IllegalStateException)
        }
    }
}
