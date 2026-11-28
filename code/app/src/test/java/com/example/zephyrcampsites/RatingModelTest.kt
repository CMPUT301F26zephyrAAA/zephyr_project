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
    private lateinit var ratings: CollectionReference

    private lateinit var campsiteRef: DocumentReference
    private lateinit var ratingRef: DocumentReference

    private lateinit var reservationDocument: DocumentSnapshot
    private lateinit var ratingDocument: DocumentSnapshot

    private lateinit var reservationQuery: Query
    private lateinit var querySnapshot: QuerySnapshot

    private lateinit var model: RatingModel

    private val accountId = "entrant-1"
    private val campsiteId = "1"
    private val reservationId = "stay-1"

    @Before
    fun setUp() {

        firestore = mock(FirebaseFirestore::class.java)

        reservations = mock(CollectionReference::class.java)
        campsites = mock(CollectionReference::class.java)
        ratings = mock(CollectionReference::class.java)

        campsiteRef = mock(DocumentReference::class.java)
        ratingRef = mock(DocumentReference::class.java)

        reservationDocument = mock(DocumentSnapshot::class.java)
        ratingDocument = mock(DocumentSnapshot::class.java)

        reservationQuery = mock(Query::class.java)
        querySnapshot = mock(QuerySnapshot::class.java)


        `when`(firestore.collection("reservation"))
            .thenReturn(reservations)

        `when`(firestore.collection("campsite"))
            .thenReturn(campsites)

        `when`(campsites.document(campsiteId))
            .thenReturn(campsiteRef)

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

        `when`(ratingDocument.exists())
            .thenReturn(false)

        `when`(ratingDocument.id)
            .thenReturn(accountId)

        model = RatingModel(firestore)
    }

    // Feedback write behavior is covered through the callable in the emulator suites.
    @Test
    fun eligibleEntrantWithoutPreviousRatingReceivesEmptyRating() {

        val result = loadRating()

        assertNotNull(result)
        assertEquals(reservationId, result!!.reservationId)
        assertEquals(0, result.score)
        assertEquals("", result.review)
        assertNull(result.accountId)
    }

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

    @Test
    fun cancelledStayIsNotOffered() {

        `when`(reservationDocument.data)
            .thenReturn(
                validStayData() + mapOf(
                    "cancelled" to true
                )
            )

        assertNull(loadRating())
    }

    @Test
    fun futureStayIsNotOffered() {

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
    }

    @Test
    fun otherEntrantsStayIsNotOffered() {

        `when`(reservationDocument.data)
            .thenReturn(
                validStayData() + mapOf(
                    "accountId" to "another-entrant"
                )
            )

        assertNull(loadRating())
    }

    @Test
    fun otherCampsitesStayIsNotOffered() {

        `when`(reservationDocument.data)
            .thenReturn(
                validStayData() + mapOf(
                    "campsiteId" to "another-campsite"
                )
            )

        assertNull(loadRating())
    }

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

        }
    }

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

        }
    }

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
