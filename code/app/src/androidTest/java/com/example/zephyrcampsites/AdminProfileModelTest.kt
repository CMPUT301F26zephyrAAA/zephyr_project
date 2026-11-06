package com.example.zephyrcampsites

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.Source
import java.util.Date
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks profile cleanup and stored reservation history using only the local emulator. */
@RunWith(AndroidJUnit4::class)
class AdminProfileModelTest : AdminEmulatorTest() {
    @Test
    fun removalCleansWaitingListsAndActiveReservationsWithoutErasingHistory() {
        val accountId = "entrant-$suffix"
        val lotteryId = "lottery-$suffix"
        val now = System.currentTimeMillis()
        put("accounts/$accountId", mapOf("userType" to "entrant", "notificationsEnabled" to false))
        put("accounts/other-$suffix", mapOf("userType" to "entrant"))
        put("lottery/$lotteryId", mapOf("entrantCount" to 2L))
        put("waitingList/entry-$suffix", mapOf("accountId" to accountId, "lotteryId" to lotteryId))
        put(
            "waitingList/other-$suffix",
            mapOf("accountId" to "other-$suffix", "lotteryId" to lotteryId),
        )
        for ((id, end) in listOf("active" to now + 86400000, "past" to now - 86400000)) {
            put(
                "reservation/$id-$suffix",
                mapOf(
                    "accountId" to accountId,
                    "campsiteId" to "site-$suffix",
                    "bookedPeriodStart" to Date(now - 172800000),
                    "bookedPeriodEnd" to Date(end),
                    "cancelled" to false,
                ),
            )
        }
        val result = TaskCompletionSource<Boolean>()
        AccountsModel(firestore)
            .deleteAccount(
                Account(accountId),
                "admin-$suffix",
                { result.setResult(true) },
                { result.setException(it) },
            )
        assertTrue(await(result.task))
        assertFalse(await(firestore.document("accounts/$accountId").get(Source.SERVER)).exists())
        assertFalse(
            await(firestore.document("waitingList/entry-$suffix").get(Source.SERVER)).exists()
        )
        assertTrue(
            await(firestore.document("waitingList/other-$suffix").get(Source.SERVER)).exists()
        )
        assertEquals(
            1L,
            await(firestore.document("lottery/$lotteryId").get(Source.SERVER))
                .getLong("entrantCount"),
        )
        val active = await(firestore.document("reservation/active-$suffix").get(Source.SERVER))
        assertTrue(active.getBoolean("cancelled") == true)
        assertEquals("admin-$suffix", active.getString("cancelledBy"))
        assertNotNull(active.getTimestamp("cancelledAt"))
        assertFalse(
            await(firestore.document("reservation/past-$suffix").get(Source.SERVER))
                .getBoolean("cancelled")!!
        )
        assertTrue(await(firestore.document("accounts/other-$suffix").get(Source.SERVER)).exists())
    }

    @Test
    fun profileWithoutRelatedRecordsCanBeRemoved() {
        val id = "entrant-$suffix"
        put("accounts/$id", mapOf("userType" to "entrant"))
        val result = TaskCompletionSource<Boolean>()
        AccountsModel(firestore)
            .deleteAccount(
                Account(id),
                "admin-$suffix",
                { result.setResult(true) },
                { result.setException(it) },
            )
        assertTrue(await(result.task))
        assertFalse(await(firestore.document("accounts/$id").get(Source.SERVER)).exists())
    }

    @Test
    fun accountMarkedForRemovalCannotRejoinAWaitingList() {
        val id = "entrant-$suffix"
        val lotteryId = "lottery-$suffix"
        val now = System.currentTimeMillis()
        put("accounts/$id", mapOf("userType" to "entrant", "deletionPending" to true))
        put(
            "lottery/$lotteryId",
            mapOf(
                "registrationStart" to now - 10000,
                "registrationEnd" to now + 60000,
                "entrantCount" to 0L,
            ),
        )
        val result = TaskCompletionSource<Boolean>()
        WaitingListModel(firestore)
            .joinWaitingList(
                lotteryId,
                id,
                { result.setResult(true) },
                { result.setResult(false) },
            )
        assertFalse(await(result.task))
        assertEquals(
            0L,
            await(firestore.document("lottery/$lotteryId").get(Source.SERVER))
                .getLong("entrantCount"),
        )
    }

    @Test
    fun offlineRemovalReportsFailureWithoutDeletingTheProfile() {
        val id = "entrant-$suffix"
        put("accounts/$id", mapOf("userType" to "entrant"))
        await(firestore.disableNetwork())
        val result = TaskCompletionSource<Boolean>()
        AccountsModel(firestore)
            .deleteAccount(
                Account(id),
                "admin-$suffix",
                { result.setResult(true) },
                { result.setResult(false) },
            )
        assertFalse(await(result.task))
        await(firestore.enableNetwork())
        val profile = await(firestore.document("accounts/$id").get(Source.SERVER))
        assertTrue(profile.exists())
        assertNotEquals(true, profile.getBoolean("deletionPending"))
    }

    @Test
    fun observerReadsSavedRolePreferenceAndProfileImage() {
        val id = "entrant-$suffix"
        put(
            "accounts/$id",
            mapOf(
                "userType" to "owner",
                "notificationsEnabled" to false,
                "imagePath" to "profiles/$id.jpg",
            ),
        )
        val result = TaskCompletionSource<Account>()
        val listener =
            AccountsModel(firestore)
                .observeAccounts(
                    { profiles ->
                        profiles.find { it.id == id }?.let { result.trySetResult(it) }
                    },
                    { result.trySetException(it) },
                )
        try {
            val profile = await(result.task)
            assertEquals("owner", profile.userType)
            assertFalse(profile.notificationsEnabled)
            assertEquals("profiles/$id.jpg", profile.imagePath)
        } finally {
            listener.remove()
        }
    }
}
