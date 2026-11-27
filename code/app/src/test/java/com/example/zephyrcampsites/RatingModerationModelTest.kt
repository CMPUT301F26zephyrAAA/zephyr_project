
package com.example.zephyrcampsites

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Local JUnit tests for admin rating moderation.
 *
 * Firebase dependencies are mocked.
 * No Firebase emulator or Android emulator is required.
 */
class RatingModerationModelTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var campsites: CollectionReference
    private lateinit var reservations: CollectionReference
    private lateinit var ratings: CollectionReference

    private lateinit var campsiteRef: DocumentReference
    private lateinit var selectedRatingRef: DocumentReference
    private lateinit var otherRatingRef: DocumentReference

    private lateinit var campsiteDocument: DocumentSnapshot
    private lateinit var selectedRatingDocument: DocumentSnapshot
    private lateinit var otherRatingDocument: DocumentSnapshot

    private lateinit var transaction: Transaction
    private lateinit var snapshot: QuerySnapshot
    private lateinit var metadata: SnapshotMetadata
    private lateinit var registration: ListenerRegistration

    private lateinit var model: RatingModel

    private var snapshotListener: EventListener<QuerySnapshot>? = null

    private val campsiteId = "1"

    private val selectedRating = CampsiteRating(
        reservationId = "stay-1",
        score = 1,
        review = "Policy violation",
        accountId = "entrant-1"
    )

    private val otherRating = CampsiteRating(
        reservationId = "stay-2",
        score = 5,
        review = "Lovely campsite",
        accountId = "entrant-2"
    )

    @Before
    fun setUp() {

        firestore = mock(FirebaseFirestore::class.java)

        campsites = mock(CollectionReference::class.java)
        reservations = mock(CollectionReference::class.java)
        ratings = mock(CollectionReference::class.java)

        campsiteRef = mock(DocumentReference::class.java)
        selectedRatingRef = mock(DocumentReference::class.java)
        otherRatingRef = mock(DocumentReference::class.java)

        campsiteDocument = mock(DocumentSnapshot::class.java)
        selectedRatingDocument = mock(DocumentSnapshot::class.java)
        otherRatingDocument = mock(DocumentSnapshot::class.java)

        transaction = mock(Transaction::class.java)
        snapshot = mock(QuerySnapshot::class.java)
        metadata = mock(SnapshotMetadata::class.java)
        registration = mock(ListenerRegistration::class.java)

        `when`(firestore.collection("campsite"))
            .thenReturn(campsites)

        `when`(firestore.collection("reservation"))
            .thenReturn(reservations)

        `when`(campsites.document(campsiteId))
            .thenReturn(campsiteRef)

        `when`(campsiteRef.collection("ratings"))
            .thenReturn(ratings)

        `when`(ratings.document("entrant-1"))
            .thenReturn(selectedRatingRef)

        `when`(ratings.document("entrant-2"))
            .thenReturn(otherRatingRef)

        configureRatingDocument(
            selectedRatingDocument,
            selectedRating
        )

        configureRatingDocument(
            otherRatingDocument,
            otherRating
        )

        `when`(campsiteDocument.exists())
            .thenReturn(true)

        `when`(campsiteDocument.get("rating"))
            .thenReturn(listOf(1L, 5L))

        `when`(transaction.get(campsiteRef))
            .thenReturn(campsiteDocument)

        `when`(transaction.get(selectedRatingRef))
            .thenReturn(selectedRatingDocument)

        `when`(transaction.get(otherRatingRef))
            .thenReturn(otherRatingDocument)

        `when`(snapshot.metadata)
            .thenReturn(metadata)

        `when`(metadata.isFromCache)
            .thenReturn(false)

        `when`(snapshot.documents)
            .thenReturn(
                listOf(
                    selectedRatingDocument,
                    otherRatingDocument
                )
            )

        configureTransaction()

        configureSnapshotListener()

        model = RatingModel(firestore)
    }

    /**
     * AC1:
     * All individual ratings can be loaded.
     */
    @Test
    fun adminCanLoadAllIndividualRatings() {

        var result: List<CampsiteRating>? = null
        var error: String? = null

        model.observeAdminRatings(
            campsiteId = campsiteId,
            onRatings = { result = it },
            onError = { error = it }
        )

        snapshotListener!!.onEvent(snapshot, null)

        assertNull(error)

        assertEquals(
            listOf(selectedRating, otherRating),
            result
        )
    }

    /**
     * AC2 and AC5:
     * Only the selected rating document is deleted.
     */
    @Test
    fun adminCanRemoveSelectedRating() {

        assertTrue(removeRating(selectedRating))

        verify(transaction).delete(selectedRatingRef)

        verify(transaction, never())
            .delete(otherRatingRef)
    }

    /**
     * AC6:
     * Removing a score of 1 from [1, 5]
     * produces an average of 5.
     */
    @Test
    fun removingRatingUpdatesCampsiteAverage() {

        assertTrue(removeRating(selectedRating))

        verify(transaction).update(
            campsiteRef,
            mapOf(
                "rating" to listOf(5),
                "averageRating" to 5.0
            )
        )
    }

    /**
     * AC6:
     * Removing a rating recalculates a fractional average.
     */
    @Test
    fun removingRatingRecalculatesFractionalAverage() {

        `when`(campsiteDocument.get("rating"))
            .thenReturn(listOf(1L, 4L, 5L))

        assertTrue(removeRating(selectedRating))

        verify(transaction).update(
            campsiteRef,
            mapOf(
                "rating" to listOf(4, 5),
                "averageRating" to 4.5
            )
        )
    }

    /**
     * AC6:
     * Removing the final rating clears the stored average.
     */
    @Test
    fun removingFinalRatingClearsAverage() {

        `when`(campsiteDocument.get("rating"))
            .thenReturn(listOf(1L))

        val deleteAverage = FieldValue.delete()

        assertTrue(removeRating(selectedRating))

        verify(transaction).update(
            campsiteRef,
            mapOf(
                "rating" to emptyList<Int>(),
                "averageRating" to deleteAverage
            )
        )

        verify(transaction).delete(selectedRatingRef)
    }

    /**
     * AC5 and AC6:
     * Ratings with equal scores must remain independent.
     */
    @Test
    fun removingRatingPreservesOtherEqualScores() {

        `when`(campsiteDocument.get("rating"))
            .thenReturn(listOf(1L, 1L, 5L))

        assertTrue(removeRating(selectedRating))

        verify(transaction).update(
            campsiteRef,
            mapOf(
                "rating" to listOf(1, 5),
                "averageRating" to 3.0
            )
        )

        verify(transaction).delete(selectedRatingRef)

        verify(transaction, never())
            .delete(otherRatingRef)
    }

    /**
     * Prevents subtracting the same rating twice.
     */
    @Test
    fun alreadyDeletedRatingDoesNotChangeAverage() {

        `when`(selectedRatingDocument.exists())
            .thenReturn(false)

        var result: Boolean? = null
        var error: String? = null

        model.removeAdminRating(
            campsiteId = campsiteId,
            selected = selectedRating,
            onResult = { result = it },
            onError = { error = it }
        )

        assertEquals(false, result)
        assertNull(error)

        verifyNoDatabaseWrites()
    }

    /**
     * A rating changed after selection must not be deleted
     * using the stale selection.
     */
    @Test
    fun staleRatingSelectionIsRejected() {

        `when`(selectedRatingDocument.get("review"))
            .thenReturn("Updated review")

        assertRemovalRejected(selectedRating)

        verifyNoDatabaseWrites()
    }

    /**
     * A rating without a valid document ID cannot be removed.
     */
    @Test
    fun ratingWithoutAccountIdIsRejected() {

        val invalidRating = selectedRating.copy(
            accountId = null
        )

        var resultCalled = false
        var error: String? = null

        model.removeAdminRating(
            campsiteId = campsiteId,
            selected = invalidRating,
            onResult = { resultCalled = true },
            onError = { error = it }
        )

        assertFalse(resultCalled)

        assertEquals(
            "This rating has no valid document ID.",
            error
        )

        verifyNoInteractions(transaction)
    }

    /**
     * Blank document IDs are rejected.
     */
    @Test
    fun blankRatingAccountIdIsRejected() {

        val invalidRating = selectedRating.copy(
            accountId = ""
        )

        var error: String? = null

        model.removeAdminRating(
            campsiteId,
            invalidRating,
            onResult = {
                fail("Invalid rating must not be removed.")
            },
            onError = { error = it }
        )

        assertEquals(
            "This rating has no valid document ID.",
            error
        )

        verifyNoInteractions(transaction)
    }

    /**
     * A deleted campsite cannot have its aggregate updated.
     */
    @Test
    fun missingCampsitePreventsRatingRemoval() {

        `when`(campsiteDocument.exists())
            .thenReturn(false)

        assertRemovalRejected(selectedRating)

        verifyNoDatabaseWrites()
    }

    /**
     * A score missing from the aggregate must not be removed.
     */
    @Test
    fun missingAggregateScorePreventsRemoval() {

        `when`(campsiteDocument.get("rating"))
            .thenReturn(listOf(4L, 5L))

        assertRemovalRejected(selectedRating)

        verifyNoDatabaseWrites()
    }

    /**
     * AC7:
     * Updated snapshots no longer contain a deleted rating.
     *
     * This verifies snapshot handling, not real persistence.
     */
    @Test
    fun removedRatingIsExcludedFromUpdatedSnapshot() {

        val receivedRatings =
            mutableListOf<List<CampsiteRating>>()

        model.observeAdminRatings(
            campsiteId,
            onRatings = {
                receivedRatings.add(it)
            },
            onError = {
                fail(it)
            }
        )

        snapshotListener!!.onEvent(snapshot, null)

        assertEquals(
            listOf(selectedRating, otherRating),
            receivedRatings.last()
        )

        assertTrue(removeRating(selectedRating))

        `when`(snapshot.documents)
            .thenReturn(listOf(otherRatingDocument))

        snapshotListener!!.onEvent(snapshot, null)

        assertEquals(
            listOf(otherRating),
            receivedRatings.last()
        )

        assertFalse(
            receivedRatings.last().contains(selectedRating)
        )
    }

    /**
     * An empty snapshot is returned as an empty rating list.
     */
    @Test
    fun noRatingsReturnsEmptyList() {

        `when`(snapshot.documents)
            .thenReturn(emptyList())

        var result: List<CampsiteRating>? = null

        model.observeAdminRatings(
            campsiteId,
            onRatings = { result = it },
            onError = { fail(it) }
        )

        snapshotListener!!.onEvent(snapshot, null)

        assertNotNull(result)
        assertTrue(result!!.isEmpty())
    }

    /**
     * Cached snapshots must not replace server-confirmed data.
     */
    @Test
    fun cachedRatingsAreNotReportedAsServerResults() {

        `when`(metadata.isFromCache)
            .thenReturn(true)

        var resultCalled = false

        model.observeAdminRatings(
            campsiteId,
            onRatings = { resultCalled = true },
            onError = { fail(it) }
        )

        snapshotListener!!.onEvent(snapshot, null)

        assertFalse(resultCalled)
    }

    /**
     * AC8:
     * A Firestore permission-denied response is reported
     * as a failed removal.
     *
     * This does not independently verify Firestore rules.
     */
    @Test
    fun permissionDeniedPreventsSuccessfulRemoval() {

        val permissionError = SecurityException("Permission denied.")

        val deniedTask = failedTask<Boolean>(permissionError)

        `when`(
            firestore.runTransaction(
                any<Transaction.Function<Boolean>>()
            )
        ).thenReturn(deniedTask)

        assertRemovalRejected(selectedRating)

        verifyNoDatabaseWrites()
    }

    /**
     * A failed Firebase transaction reports an error.
     */
    @Test
    fun failedRatingRemovalReturnsError() {

        val failure = failedTask<Boolean>(
            IllegalStateException("Transaction failed.")
        )

        `when`(
            firestore.runTransaction(
                any<Transaction.Function<Boolean>>()
            )
        ).thenReturn(failure)

        assertRemovalRejected(selectedRating)

        verifyNoDatabaseWrites()
    }

    /**
     * A failed ratings observation reports a readable error.
     */
    @Test
    fun ratingObservationFailureReturnsError() {

        var error: String? = null
        var resultCalled = false
        model.observeAdminRatings(
            campsiteId,
            onRatings = { resultCalled = true },
            onError = { error = it }
        )
        val failure = mock(FirebaseFirestoreException::class.java)
        snapshotListener!!.onEvent(null, failure)
        assertFalse(resultCalled)
        assertEquals(
            "Unable to load campsite ratings.",
            error
        )
    }

    /**
     * Leaving the ratings screen can remove the listener.
     */
    @Test
    fun stoppingObservationRemovesListener() {

        val stopObserving = model.observeAdminRatings(
            campsiteId,
            onRatings = {},
            onError = {}
        )

        stopObserving()

        verify(registration).remove()
    }

    // =====================================================
    // HELPER FUNCTIONS
    // =====================================================

    private fun configureRatingDocument(
        document: DocumentSnapshot,
        rating: CampsiteRating
    ) {

        `when`(document.exists())
            .thenReturn(true)

        `when`(document.id)
            .thenReturn(rating.accountId!!)

        `when`(document.get("reservationId"))
            .thenReturn(rating.reservationId)

        `when`(document.get("score"))
            .thenReturn(rating.score.toLong())

        `when`(document.get("review"))
            .thenReturn(rating.review)

        `when`(document.get("updatedAt"))
            .thenReturn(rating.updatedAt)
    }

    private fun configureTransaction() {

        `when`(
            firestore.runTransaction(
                any<Transaction.Function<Boolean>>()
            )
        ).thenAnswer { invocation ->

            val operation =
                invocation.getArgument<
                        Transaction.Function<Boolean>
                        >(0)

            try {

                val result = operation.apply(transaction)

                successfulTask(result)

            } catch (exception: Exception) {

                failedTask<Boolean>(exception)
            }
        }
    }

    private fun configureSnapshotListener() {

        `when`(
            ratings.addSnapshotListener(
                eq(MetadataChanges.INCLUDE),
                any<EventListener<QuerySnapshot>>()
            )
        ).thenAnswer { invocation ->

            snapshotListener =
                invocation.getArgument<
                        EventListener<QuerySnapshot>
                        >(1)

            registration
        }
    }

    private fun removeRating(
        rating: CampsiteRating
    ): Boolean {

        var result: Boolean? = null
        var error: String? = null

        model.removeAdminRating(
            campsiteId,
            rating,
            onResult = { result = it },
            onError = { error = it }
        )

        assertNull(error)
        assertNotNull(result)

        return result == true
    }

    private fun assertRemovalRejected(
        rating: CampsiteRating
    ) {

        var resultCalled = false
        var error: String? = null

        model.removeAdminRating(
            campsiteId,
            rating,
            onResult = { resultCalled = true },
            onError = { error = it }
        )

        assertFalse(resultCalled)

        assertEquals(
            "Unable to remove this rating. " +
                    "Review the current rating and try again.",
            error
        )
    }

    private fun verifyNoDatabaseWrites() {

        verify(transaction, never()).update(
            any(DocumentReference::class.java),
            anyMap<String, Any>()
        )

        verify(transaction, never()).delete(
            any(DocumentReference::class.java)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> successfulTask(
        result: T
    ): Task<T> {

        val task = mock(Task::class.java) as Task<T>

        `when`(
            task.addOnSuccessListener(
                any<OnSuccessListener<T>>()
            )
        ).thenAnswer { invocation ->

            val listener =
                invocation.getArgument<
                        OnSuccessListener<T>
                        >(0)

            listener.onSuccess(result)

            task
        }

        `when`(
            task.addOnFailureListener(
                any(OnFailureListener::class.java)
            )
        ).thenReturn(task)

        return task
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> failedTask(
        exception: Exception
    ): Task<T> {

        val task = mock(Task::class.java) as Task<T>

        `when`(
            task.addOnSuccessListener(
                any<OnSuccessListener<T>>()
            )
        ).thenReturn(task)

        `when`(
            task.addOnFailureListener(
                any(OnFailureListener::class.java)
            )
        ).thenAnswer { invocation ->

            val listener =
                invocation.getArgument<
                        OnFailureListener
                        >(0)

            listener.onFailure(exception)

            task
        }

        return task
    }
}