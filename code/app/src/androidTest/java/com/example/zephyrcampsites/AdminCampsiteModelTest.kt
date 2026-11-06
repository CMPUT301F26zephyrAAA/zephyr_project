package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.Source
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies admin campsite removal and its related-record cleanup using local emulator fixtures. */
@RunWith(AndroidJUnit4::class)
class AdminCampsiteModelTest : AdminEmulatorTest() {
    private lateinit var model: CampsiteModel
    private val campsiteId = "site-$suffix"
    private val admin = Account("admin-$suffix", userType = "admin")

    @After
    fun stopListener() {
        if (this::model.isInitialized) {
            model.stopListening()
        }
    }

    @Test
    fun removalDeletesRelatedLotteriesAndEntriesButKeepsOtherCampsites() {
        put("campsite/$campsiteId", mapOf("id" to campsiteId, "name" to "Pine Lake"))
        put("campsite/other-$suffix", mapOf("id" to "other-$suffix"))
        put(
            "lottery/lottery-$suffix",
            mapOf("campsiteId" to campsiteId, "registrationEnd" to Long.MAX_VALUE),
        )
        put(
            "waitingList/entry-$suffix",
            mapOf("lotteryId" to "lottery-$suffix", "accountId" to "entrant-$suffix"),
        )
        put("lottery/other-$suffix", mapOf("campsiteId" to "other-$suffix"))
        put("waitingList/other-$suffix", mapOf("lotteryId" to "other-$suffix"))
        model = CampsiteModel(firestore)
        remove(admin)
        for (path in
            listOf(
                "campsite/$campsiteId",
                "lottery/lottery-$suffix",
                "waitingList/entry-$suffix",
            )) {
            assertFalse(path, await(firestore.document(path).get(Source.SERVER)).exists())
        }
        for (path in
            listOf(
                "campsite/other-$suffix",
                "lottery/other-$suffix",
                "waitingList/other-$suffix",
            )) {
            assertTrue(path, await(firestore.document(path).get(Source.SERVER)).exists())
        }
    }

    @Test
    fun campsiteWithoutLotteriesCanBeRemoved() {
        put("campsite/$campsiteId", mapOf("id" to campsiteId))
        model = CampsiteModel(firestore)
        remove(admin)
        assertFalse(await(firestore.document("campsite/$campsiteId").get(Source.SERVER)).exists())
    }

    @Test
    fun entrantAndOwnerCannotRequestRemoval() {
        put("campsite/$campsiteId", mapOf("id" to campsiteId))
        model = CampsiteModel(firestore)
        for (role in listOf("entrant", "owner")) {
            val result = TaskCompletionSource<Boolean>()
            CampsiteController(model)
                .removeCampsite(
                    Account("account-$suffix", userType = role),
                    Campsite(campsiteId),
                    onSuccess = { result.setResult(true) },
                    onFailure = { result.setResult(false) },
                )
            assertFalse(await(result.task))
        }
        assertTrue(await(firestore.document("campsite/$campsiteId").get(Source.SERVER)).exists())
    }

    @Test
    fun failedServerReadReportsFailureAndKeepsCampsite() {
        put("campsite/$campsiteId", mapOf("id" to campsiteId))
        model = CampsiteModel(firestore)
        await(firestore.disableNetwork())
        val result = TaskCompletionSource<Boolean>()
        CampsiteController(model)
            .removeCampsite(
                admin,
                Campsite(campsiteId),
                onSuccess = { result.setResult(true) },
                onFailure = { result.setResult(false) },
            )
        assertFalse(await(result.task))
        await(firestore.enableNetwork())
        assertTrue(await(firestore.document("campsite/$campsiteId").get(Source.SERVER)).exists())
    }

    private fun remove(account: Account) {
        val result = TaskCompletionSource<Boolean>()
        CampsiteController(model)
            .removeCampsite(
                account,
                Campsite(campsiteId),
                onSuccess = { result.setResult(true) },
                onFailure = { result.setException(IllegalStateException(it)) },
            )
        assertTrue(await(result.task))
    }
}
