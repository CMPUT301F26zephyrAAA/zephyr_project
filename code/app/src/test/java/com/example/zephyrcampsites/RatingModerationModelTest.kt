
package com.example.zephyrcampsites

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.Timestamp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

// Hiral - [AI-05] OpenAI, ChatGPT, Testing Admin Rating Deletion.
/**
 * Local JUnit tests for admin rating moderation.
 *
 * Firebase dependencies are mocked.
 * No Firebase emulator or Android emulator is required.
 */
class RatingModerationModelTest {

    private val firestore = mock(FirebaseFirestore::class.java)
    private val functions = mock(FirebaseFunctions::class.java)
    private val ratings = mock(CollectionReference::class.java)
    private val selectedRatingDocument = mock(DocumentSnapshot::class.java)
    private val otherRatingDocument = mock(DocumentSnapshot::class.java)
    private val snapshot = mock(QuerySnapshot::class.java)
    private val metadata = mock(SnapshotMetadata::class.java)
    private val registration = mock(ListenerRegistration::class.java)
    private lateinit var model: RatingModel
    private var snapshotListener: EventListener<QuerySnapshot>? = null

    private val campsiteId = "1"
    private val adminAccountId = "auth_admin"
    private val selectedRating = CampsiteRating("stay-1", 1, "Policy violation", "auth_entrant")
    private val otherRating = CampsiteRating("stay-2", 5, "Lovely campsite", "auth_other")

    @Before
    fun setUp() {
        val campsites = mock(CollectionReference::class.java)
        val campsiteRef = mock(DocumentReference::class.java)
        val reservations = mock(CollectionReference::class.java)
        `when`(firestore.collection("reservation")).thenReturn(reservations)
        `when`(firestore.collection("campsite")).thenReturn(campsites)
        `when`(campsites.document(campsiteId)).thenReturn(campsiteRef)
        `when`(campsiteRef.collection("ratings")).thenReturn(ratings)
        `when`(snapshot.metadata).thenReturn(metadata)
        `when`(snapshot.documents).thenReturn(listOf(selectedRatingDocument, otherRatingDocument))
        configureRatingDocument(selectedRatingDocument, selectedRating)
        configureRatingDocument(otherRatingDocument, otherRating)
        configureSnapshotListener()
        model = RatingModel(firestore, functions)
    }

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

    // AI Citation [AI-39] (Bhav Saluja). Callable argument forwarding for PR #141.
    @Test
    fun sendsTheVersionDisplayedInTheConfirmation() {
        val endpoint = mock(HttpsCallableReference::class.java)
        val response = mock(HttpsCallableResult::class.java)
        val selected = selectedRating.copy(updatedAt = Timestamp(12, 345))
        val payload = mapOf(
            "campsiteId" to campsiteId, "adminAccountId" to adminAccountId,
            "accountId" to selectedRating.accountId,
            "updatedAt" to mapOf<String, Number>("seconds" to 12L, "nanoseconds" to 345)
        )
        `when`(functions.getHttpsCallable("removeCampsiteFeedback")).thenReturn(endpoint)
        val request = successfulTask(response)
        `when`(endpoint.call(payload)).thenReturn(request)

        var removed = false
        model.removeAdminRating(campsiteId, adminAccountId, selected, { removed = it }, { fail(it) })

        assertTrue(removed)
        verify(endpoint).call(payload)
    }

    @Test
    fun cannotConfirmARatingWithoutAServerVersion() {
        var error: String? = null
        model.removeAdminRating(campsiteId, adminAccountId, selectedRating, { fail() }, { error = it })
        assertEquals("Reload the rating and try again.", error)
        verifyNoInteractions(functions)
    }

    // Hiral - [AI-07] OpenAI, ChatGPT, Making changes for Admin Rating deletion and granting auth admin access to a specific user.
    @Test
    fun reviewOnlyEntryDoesNotCallRatingRemoval() {
        val reviewOnly = selectedRating.copy(score = 0, updatedAt = Timestamp(12, 345))
        var error: String? = null

        model.removeAdminRating(campsiteId, adminAccountId, reviewOnly, { fail() }, { error = it })

        assertEquals("This entry has no valid rating to remove.", error)
        verifyNoInteractions(functions)
    }

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
}
