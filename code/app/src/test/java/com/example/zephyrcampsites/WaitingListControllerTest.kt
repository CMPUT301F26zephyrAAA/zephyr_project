package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test

class WaitingListControllerTest {

    private class FakeWaitingListModel : WaitingListGateway {
        var joinedCalled = false
        var joinCalled = false
        var leaveCalled = false

        override fun isEntrantJoined(
            lotteryId: String,
            accountId: String,
            onResult: (Boolean) -> Unit,
            onError: (String) -> Unit
        ) {
            joinedCalled = true
            onResult(true)
        }

        override fun joinWaitingList(
            lotteryId: String,
            accountId: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            joinCalled = true
            onSuccess()
        }

        override fun leaveWaitingList(
            lotteryId: String,
            accountId: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            leaveCalled = true
            onSuccess()
        }
    }

    @Test
    fun joinRejectedWhenLotteryIdMissing() {
        val fake = FakeWaitingListModel()
        val controller = WaitingListController(fake)
        var error = ""

        controller.joinWaitingList(
            lotteryId = "",
            accountId = "account1",
            onSuccess = { fail("Should not succeed") },
            onError = { error = it }
        )

        assertEquals("Lottery ID is missing", error)
        assertFalse(fake.joinCalled)
    }

    @Test
    fun joinRejectedWhenAccountIdMissing() {
        val fake = FakeWaitingListModel()
        val controller = WaitingListController(fake)
        var error = ""

        controller.joinWaitingList(
            lotteryId = "lottery1",
            accountId = "",
            onSuccess = { fail("Should not succeed") },
            onError = { error = it }
        )

        assertEquals("Account ID is missing", error)
        assertFalse(fake.joinCalled)
    }

    @Test
    fun validJoinDelegatesToModel() {
        val fake = FakeWaitingListModel()
        val controller = WaitingListController(fake)
        var success = false

        controller.joinWaitingList(
            lotteryId = "lottery1",
            accountId = "account1",
            onSuccess = { success = true },
            onError = { fail(it) }
        )

        assertTrue(fake.joinCalled)
        assertTrue(success)
    }

    @Test
    fun leaveRejectedWhenLotteryIdMissing() {
        val fake = FakeWaitingListModel()
        val controller = WaitingListController(fake)
        var error = ""

        controller.leaveWaitingList(
            lotteryId = "",
            accountId = "account1",
            onSuccess = { fail("Should not succeed") },
            onError = { error = it }
        )

        assertEquals("Lottery ID is missing", error)
        assertFalse(fake.leaveCalled)
    }

    @Test
    fun leaveRejectedWhenAccountIdMissing() {
        val fake = FakeWaitingListModel()
        val controller = WaitingListController(fake)
        var error = ""

        controller.leaveWaitingList(
            lotteryId = "lottery1",
            accountId = "",
            onSuccess = { fail("Should not succeed") },
            onError = { error = it }
        )

        assertEquals("Account ID is missing", error)
        assertFalse(fake.leaveCalled)
    }

    @Test
    fun validLeaveDelegatesToModel() {
        val fake = FakeWaitingListModel()
        val controller = WaitingListController(fake)
        var success = false

        controller.leaveWaitingList(
            lotteryId = "lottery1",
            accountId = "account1",
            onSuccess = { success = true },
            onError = { fail(it) }
        )

        assertTrue(fake.leaveCalled)
        assertTrue(success)
    }

    @Test
    fun membershipCheckDelegatesToModel() {
        val fake = FakeWaitingListModel()
        val controller = WaitingListController(fake)
        var joined = false

        controller.isEntrantJoined(
            lotteryId = "lottery1",
            accountId = "account1",
            onResult = { joined = it },
            onError = { fail(it) }
        )

        assertTrue(fake.joinedCalled)
        assertTrue(joined)
    }
}
