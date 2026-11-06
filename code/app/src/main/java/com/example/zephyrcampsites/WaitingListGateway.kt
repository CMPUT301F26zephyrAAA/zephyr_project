package com.example.zephyrcampsites

// [AI-05] OpenAI, ChatGPT, assistance with designing a testable interface for waiting-list operations

interface WaitingListGateway {
    fun isEntrantJoined(
        lotteryId: String,
        accountId: String,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit
    )

    fun joinWaitingList(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    fun leaveWaitingList(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
}
