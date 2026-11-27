package com.example.zephyrcampsites

import kotlin.collections.plus

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.util.Date

// Hiral - [AI-05] OpenAI, ChatGPT, Testing Admin Rating Deletion.
/**
 * Local JUnit unit tests for RatingModel.
 *
 * Firebase dependencies are mocked.
 * No Android emulator, Firestore emulator or Android Context
 * is required.
 */
class RatingModelTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var reservations: CollectionReference
    private lateinit var campsites: CollectionReference
    private lateinit var accounts: CollectionReference
    private lateinit var ratings: CollectionReference

    private lateinit var campsiteRef: DocumentReference
    private lateinit var accountRef: DocumentReference
    private lateinit var reservationRef: DocumentReference
    private lateinit var ratingRef: DocumentReference

    private lateinit var campsiteDocument: DocumentSnapshot
    private lateinit var accountDocument: DocumentSnapshot
    private lateinit var reservationDocument: DocumentSnapshot
    private lateinit var ratingDocument: DocumentSnapshot

    private lateinit var reservationQuery: Query
    private lateinit var querySnapshot: QuerySnapshot
    private lateinit var transaction: Transaction

    private lateinit var model: RatingModel

    private val accountId = "entrant-1"
    private val campsiteId = "1"
    private val reservationId = "stay-1"

    @Before
    fun setUp() {

        firestore = mock(FirebaseFirestore::class.java)

        reservations = mock(CollectionReference::class.java)
        campsites = mock(CollectionReference::class.java)
        accounts = mock(CollectionReference::class.java)
        ratings = mock(CollectionReference::class.java)

        campsiteRef = mock(DocumentReference::class.java)
        accountRef = mock(DocumentReference::class.java)
        reservationRef = mock(DocumentReference::class.java)
        ratingRef = mock(DocumentReference::class.java)

        campsiteDocument = mock(DocumentSnapshot::class.java)
        accountDocument = mock(DocumentSnapshot::class.java)
        reservationDocument = mock(DocumentSnapshot::class.java)
        ratingDocument = mock(DocumentSnapshot::class.java)

        reservationQuery = mock(Query::class.java)
        querySnapshot = mock(QuerySnapshot::class.java)

        transaction = mock(Transaction::class.java)

        `when`(firestore.collection("reservation"))
            .thenReturn(reservations)

        `when`(firestore.collection("campsite"))
            .thenReturn(campsites)

        `when`(firestore.collection("accounts"))
            .thenReturn(accounts)

        `when`(campsites.document(campsiteId))
            .thenReturn(campsiteRef)

        `when`(accounts.document(accountId))
            .thenReturn(accountRef)

        `when`(reservations.document(reservationId))
            .thenReturn(reservationRef)

        `when`(campsiteRef.collection("ratings"))
            .thenReturn(ratings)

        `when`(ratings.document(accountId))
            .thenReturn(ratingRef)

        `when`(
            reservations.whereEqualTo("accountId", accountId)
        ).thenReturn(reservationQuery)

        `when`(querySnapshot.documents)
            .thenReturn(listOf(reservationDocument))

        val reservationQueryTask = successfulTask(querySnapshot)
        val ratingDocumentTask = successfulTask(ratingDocument)
        `when`(reservationQuery.get(Source.SERVER))
            .thenReturn(reservationQueryTask)
        `when`(ratingRef.get(Source.SERVER))
            .thenReturn(ratingDocumentTask)

        `when`(reservationDocument.id)
            .thenReturn(reservationId)

        `when`(reservationDocument.data)
            .thenReturn(validStayData())

        `when`(campsiteDocument.exists())
            .thenReturn(true)

        `when`(campsiteDocument.get("rating"))
            .thenReturn(listOf(4L, 5L))

        `when`(accountDocument.get("userType"))
            .thenReturn(listOf("entrant"))

        `when`(ratingDocument.exists())
            .thenReturn(false)

        `when`(ratingDocument.id)
            .thenReturn(accountId)

        `when`(transaction.get(campsiteRef))
            .thenReturn(campsiteDocument)

        `when`(transaction.get(accountRef))
            .thenReturn(accountDocument)

        `when`(transaction.get(reservationRef))
            .thenReturn(reservationDocument)

        `when`(transaction.get(ratingRef))
            .thenReturn(ratingDocument)

        `when`(
            firestore.runTransaction(
                any<Transaction.Function<Transaction>>()
            )
        ).thenAnswer { invocation ->

            val operation =
                invocation.getArgument<Transaction.Function<Transaction>>(0)

            try {
                successfulTask(operation.apply(transaction))
            } catch (exception: Exception) {
                failedTask<Transaction>(exception)
            }
        }

        model = RatingModel(firestore)
    }

    /**
     * Verifies that an eligible entrant without a saved rating
     * receives an empty rating.
     */
    @Test
    fun eligibleEntrantWithoutPreviousRatingReceivesEmptyRating() {

        val result = loadRating()

        assertNotNull(result)
        assertEquals(reservationId, result!!.reservationId)
        assertEquals(0, result.score)
        assertEquals("", result.review)
        assertNull(result.accountId)
    }

    /**
     * Verifies that an existing rating is loaded correctly.
     */
    @Test
    fun existingRatingIsLoadedCorrectly() {

        val updatedAt = Timestamp(Date())

        `when`(ratingDocument.exists())
            .thenReturn(true)

        `when`(ratingDocument.get("score"))
            .thenReturn(4L)

        `when`(ratingDocument.get("review"))
            .thenReturn("Quiet lakeside stay.")

        `when`(ratingDocument.get("updatedAt"))
            .thenReturn(updatedAt)

        val result = loadRating()

        assertNotNull(result)
        assertEquals(reservationId, result!!.reservationId)
        assertEquals(4, result.score)
        assertEquals("Quiet lakeside stay.", result.review)
        assertEquals(accountId, result.accountId)
        assertEquals(updatedAt, result.updatedAt)
    }

    /**
     * Verifies that submitting a first rating includes the
     * existing campsite scores in the updated average.
     */
    @Test
    fun firstRatingUpdatesCampsiteAverageCorrectly() {

        assertTrue(
            submitRating(
                CampsiteRating(
                    reservationId,
                    2,
                    "Quiet lakeside stay."
                )
            )
        )

        verify(transaction).update(
            campsiteRef,
            mapOf(
                "rating" to listOf(4, 5, 2),
                "averageRating" to 11.0 / 3
            )
        )

        verify(transaction).set(
            eq(ratingRef),
            anyMap<String, Any>()
        )
    }

    /**
     * Verifies that replacing a rating does not add
     * another contribution to the campsite aggregate.
     */
    @Test
    fun replacementRatingUpdatesAverageWithoutIncreasingCount() {

        `when`(campsiteDocument.get("rating"))
            .thenReturn(listOf(4L, 5L, 2L))

        `when`(ratingDocument.exists())
            .thenReturn(true)

        `when`(ratingDocument.getLong("score"))
            .thenReturn(2L)

        assertTrue(
            submitRating(
                CampsiteRating(
                    reservationId,
                    5,
                    "Would stay again."
                )
            )
        )

        verify(transaction).update(
            campsiteRef,
            mapOf(
                "rating" to listOf(4, 5, 5),
                "averageRating" to 14.0 / 3
            )
        )

        verify(transaction).set(
            eq(ratingRef),
            anyMap<String, Any>()
        )
    }

    /**
     * Verifies that replacing one rating preserves
     * identical scores belonging to other entrants.
     */
    @Test
    fun replacingRatingPreservesOtherEntrantsEqualScores() {

        `when`(campsiteDocument.get("rating"))
            .thenReturn(listOf(4L, 4L, 4L))

        `when`(ratingDocument.exists())
            .thenReturn(true)

        `when`(ratingDocument.getLong("score"))
            .thenReturn(4L)

        assertTrue(
            submitRating(
                CampsiteRating(reservationId, 1)
            )
        )

        verify(transaction).update(
            campsiteRef,
            mapOf(
                "rating" to listOf(4, 4, 1),
                "averageRating" to 3.0
            )
        )
    }

    /**
     * Verifies that cancelled stays cannot be rated.
     */
    @Test
    fun cancelledStayIsNotOfferedOrAccepted() {

        `when`(reservationDocument.data)
            .thenReturn(
                validStayData() + mapOf(
                    "cancelled" to true
                )
            )

        assertNull(loadRating())

        assertRejected(
            CampsiteRating(reservationId, 4)
        )
    }

    /**
     * Verifies that future stays cannot be rated.
     */
    @Test
    fun futureStayIsNotOfferedOrAccepted() {

        val now = System.currentTimeMillis()

        `when`(reservationDocument.data)
            .thenReturn(
                validStayData() + mapOf(
                    "bookedPeriodStart" to
                            Timestamp(Date(now + 86400000L)),

                    "bookedPeriodEnd" to
                            Timestamp(Date(now + 172800000L))
                )
            )

        assertNull(loadRating())

        assertRejected(
            CampsiteRating(reservationId, 4)
        )
    }

    /**
     * Verifies that another entrant's reservation
     * cannot be used to submit a rating.
     */
    @Test
    fun otherEntrantsStayIsNotOfferedOrAccepted() {

        `when`(reservationDocument.data)
            .thenReturn(
                validStayData() + mapOf(
                    "accountId" to "another-entrant"
                )
            )

        assertNull(loadRating())

        assertRejected(
            CampsiteRating(reservationId, 4)
        )
    }

    /**
     * Verifies that a reservation for another campsite
     * cannot be used to rate the selected campsite.
     */
    @Test
    fun otherCampsitesStayIsNotOfferedOrAccepted() {

        `when`(reservationDocument.data)
            .thenReturn(
                validStayData() + mapOf(
                    "campsiteId" to "another-campsite"
                )
            )

        assertNull(loadRating())

        assertRejected(
            CampsiteRating(reservationId, 4)
        )
    }

    /**
     * Verifies that eligibility is checked again when
     * submitting a previously loaded rating.
     */
    @Test
    fun stayCancelledAfterOpeningFormIsRejectedOnSubmit() {

        val eligible = loadRating()

        assertNotNull(eligible)

        `when`(reservationDocument.data)
            .thenReturn(
                validStayData() + mapOf(
                    "cancelled" to true
                )
            )

        assertRejected(
            eligible!!.copy(score = 4)
        )
    }

    /**
     * Verifies that reservations missing required
     * fields cannot be rated.
     */
    @Test
    fun missingReservationFieldsFailClosed() {

        val requiredFields = listOf(
            "accountId",
            "campsiteId",
            "bookedPeriodStart",
            "bookedPeriodEnd",
            "cancelled"
        )

        for (field in requiredFields) {

            `when`(reservationDocument.data)
                .thenReturn(validStayData() - field)

            assertNull(
                "Reservation missing $field must be rejected.",
                loadRating()
            )

            assertRejected(
                CampsiteRating(reservationId, 4)
            )
        }
    }

    /**
     * Verifies that incorrectly typed reservation
     * fields cannot be used to submit ratings.
     */
    @Test
    fun malformedReservationFieldsFailClosed() {

        val malformedFields = listOf(
            mapOf("cancelled" to "false"),
            mapOf("bookedPeriodStart" to "yesterday"),
            mapOf("bookedPeriodEnd" to "yesterday")
        )

        for (change in malformedFields) {

            `when`(reservationDocument.data)
                .thenReturn(validStayData() + change)

            assertNull(loadRating())

            assertRejected(
                CampsiteRating(reservationId, 4)
            )
        }
    }

    /**
     * Verifies that an owner without the entrant role
     * cannot submit a rating.
     */
    @Test
    fun ownerCannotSubmitByCallingTheModelDirectly() {

        `when`(accountDocument.get("userType"))
            .thenReturn(listOf("owner"))

        assertRejected(
            CampsiteRating(reservationId, 4)
        )
    }

    /**
     * Verifies that accounts containing multiple roles
     * can rate a campsite if they contain the entrant role.
     */
    @Test
    fun accountWithEntrantAndOwnerRolesCanSubmitRating() {

        `when`(accountDocument.get("userType"))
            .thenReturn(listOf("entrant", "owner"))

        assertTrue(
            submitRating(
                CampsiteRating(reservationId, 4)
            )
        )

        verify(transaction).update(
            campsiteRef,
            mapOf(
                "rating" to listOf(4, 5, 4),
                "averageRating" to 13.0 / 3
            )
        )
    }

    /**
     * Verifies that an account without any roles
     * cannot submit a rating.
     */
    @Test
    fun accountWithoutRolesCannotSubmitRating() {

        `when`(accountDocument.get("userType"))
            .thenReturn(emptyList<String>())

        assertRejected(
            CampsiteRating(reservationId, 4)
        )
    }

    /**
     * Verifies that the model rejects ratings when
     * the campsite no longer exists.
     */
    @Test
    fun deletedCampsiteDoesNotCreateAnOrphanRating() {

        `when`(campsiteDocument.exists())
            .thenReturn(false)

        assertRejected(
            CampsiteRating(reservationId, 4)
        )
    }

    /**
     * Verifies that scores outside the valid range
     * cannot be submitted.
     */
    @Test
    fun invalidRatingScoresAreRejected() {

        for (score in listOf(-1, 0, 6)) {

            assertRejected(
                CampsiteRating(reservationId, score)
            )
        }
    }

    /**
     * Verifies that a reservation query failure
     * produces a readable error.
     */
    @Test
    fun reservationQueryFailureReturnsError() {

        val failedQueryTask = failedTask<QuerySnapshot>(
            IllegalStateException("Query failed.")
        )

        `when`(reservationQuery.get(Source.SERVER))
            .thenReturn(failedQueryTask)

        var error: String? = null
        var resultCalled = false

        model.loadRating(
            campsiteId,
            accountId,
            onResult = {
                resultCalled = true
            },
            onError = {
                error = it
            }
        )

        assertFalse(resultCalled)

        assertEquals(
            "Unable to check your past stays. Please try again.",
            error
        )
    }

    /**
     * Verifies that a rating document read failure
     * produces a readable error.
     */
    @Test
    fun ratingReadFailureReturnsError() {

        val failedRatingTask = failedTask<DocumentSnapshot>(
            IllegalStateException("Read failed.")
        )

        `when`(ratingRef.get(Source.SERVER))
            .thenReturn(failedRatingTask)

        var error: String? = null
        var resultCalled = false

        model.loadRating(
            campsiteId,
            accountId,
            onResult = {
                resultCalled = true
            },
            onError = {
                error = it
            }
        )

        assertFalse(resultCalled)

        assertEquals(
            "Unable to load your rating. Please try again.",
            error
        )
    }

    /**
     * Creates a valid completed reservation.
     */
    private fun validStayData(): Map<String, Any> {

        val now = System.currentTimeMillis()

        return mapOf(
            "accountId" to accountId,
            "campsiteId" to campsiteId,
            "cancelled" to false,

            "bookedPeriodStart" to
                    Timestamp(Date(now - 172800000L)),

            "bookedPeriodEnd" to
                    Timestamp(Date(now - 86400000L))
        )
    }

    /**
     * Calls the actual RatingModel.loadRating function.
     */
    private fun loadRating(): CampsiteRating? {

        var result: CampsiteRating? = null
        var error: String? = null
        var completed = false

        model.loadRating(
            campsiteId,
            accountId,
            onResult = {
                result = it
                completed = true
            },
            onError = {
                error = it
                completed = true
            }
        )

        assertTrue(
            "The load callback must complete.",
            completed
        )

        assertNull(error)

        return result
    }

    /**
     * Calls the actual RatingModel.submitRating function.
     */
    private fun submitRating(
        rating: CampsiteRating
    ): Boolean {

        var successful = false
        var completed = false

        model.submitRating(
            campsiteId,
            accountId,
            rating,
            onSuccess = {
                successful = true
                completed = true
            },
            onError = {
                successful = false
                completed = true
            }
        )

        assertTrue(
            "The submit callback must complete.",
            completed
        )

        return successful
    }

    /**
     * Verifies that a rejected rating produces an error
     * without requesting any database writes.
     */
    private fun assertRejected(
        rating: CampsiteRating
    ) {

        var successful = false
        var error: String? = null

        model.submitRating(
            campsiteId,
            accountId,
            rating,
            onSuccess = {
                successful = true
            },
            onError = {
                error = it
            }
        )

        assertFalse(successful)

        assertEquals(
            "Unable to save your rating. " +
                    "Check your completed stay and try again.",
            error
        )

        verify(transaction, never()).update(
            any(DocumentReference::class.java),
            anyMap<String, Any>()
        )

        verify(transaction, never()).set(
            any(DocumentReference::class.java),
            anyMap<String, Any>()
        )
    }

    /**
     * Creates a mocked successful Firebase Task.
     *
     * The success listener is called immediately,
     * without using Android's main thread.
     */
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
                invocation.getArgument<OnSuccessListener<T>>(0)

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

    /**
     * Creates a mocked failed Firebase Task.
     *
     * The failure listener is called immediately,
     * without using Android's main thread.
     */
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
                invocation.getArgument<OnFailureListener>(0)

            listener.onFailure(exception)

            task
        }

        return task
    }
}