package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
 * Verifies rating reads and transactions against a local Firestore emulator on port 8082.
 * Requires ratingFirestoreEmulator=true and adb reverse tcp:8082 tcp:8082. Uses a named demo project;
 * it never accesses the app's live Firebase project or launches MainActivity.
 */
@RunWith(AndroidJUnit4::class)
class RatingModelTest {
    private lateinit var app: FirebaseApp
    private lateinit var firestore: FirebaseFirestore
    private lateinit var model: RatingModel
    private val suffix = UUID.randomUUID().toString()
    private val accountId = "entrant-$suffix"
    private val campsiteId = "campsite-$suffix"
    private val reservationId = "stay-$suffix"
    private val createdDocuments = mutableListOf<com.google.firebase.firestore.DocumentReference>()

    @Before
    fun connectToLocalEmulator() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ratingFirestoreEmulator") == "true")
        val options = FirebaseOptions.Builder().setProjectId("demo-zephyr-rating")
            .setApplicationId("1:123456789:android:rating-test").setApiKey("local-emulator-only").build()
        app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext, options, "rating-$suffix")
        firestore = FirebaseFirestore.getInstance(app)
        firestore.useEmulator("127.0.0.1", 8082)
        model = RatingModel(firestore)
        createDocument("accounts/$accountId", mapOf("userType" to "entrant"))
        createDocument("campsite/$campsiteId", mapOf("id" to campsiteId, "name" to "Pine Lake Campground", "rating" to listOf(4, 5)))
        createDocument("reservation/$reservationId", stayData(accountId))
    }

    @After
    fun removeLocalFixtures() {
        if (this::firestore.isInitialized) {
            await(firestore.document("campsite/$campsiteId/ratings/$accountId").delete())
            for (document in createdDocuments.reversed()) {
                await(document.delete())
            }
            await(firestore.terminate())
        }
        if (this::app.isInitialized) {
            app.delete()
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
    fun concurrentEntrantsDoNotOverwriteEachOthersRatings() {
        val otherAccount = "other-$suffix"
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
        model.submitRating(campsiteId, entrantId, rating, { result.setResult(true) }, { result.setException(IllegalStateException(it)) })
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
