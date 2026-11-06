package com.example.zephyrcampsites

/***
 * Controller for entrant waiting list actions
 * Handles requests from the UI and delegates firebase
 * reads and writes to WaitingListModel
 */
class WaitingListController(
    private val waitingListModel: WaitingListGateway
) {
    /**
     * Checks whether an entrant has already joined a lottery
     *
     * @param lotteryId ID of the lottery
     * @param accountId Device identified entrant ID
     * @param onResult Returns true if the entrant is registered
     * @param onError Returns a readable error message
     */

    fun isEntrantJoined(
        lotteryId: String,
        accountId: String,
        onResult: (Boolean) -> Unit,
        onError: (String) -> Unit
    ){
        waitingListModel.isEntrantJoined(
            lotteryId = lotteryId,
            accountId = accountId,
            onResult = onResult,
            onError = onError
        )
    }

    /**
     * Requests that an entrant join a lottery waiting list
     * the model checks the registration period and prevents duplication registration before writing to Firebase
     */
    fun joinWaitingList(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()){
            onError("Lottery ID is missing")
            return
        }

        if(accountId.isBlank()) {
            onError("Account ID is missing")
            return
        }

        waitingListModel.joinWaitingList(
            lotteryId = lotteryId,
            accountId = accountId,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Requests removal of an entrant from a lottery waiting list
     * the model verifies draw status before deleting the entry
     */
    fun leaveWaitingList(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank()){
            onError("Lottery ID is missing")
            return
        }

        if(accountId.isBlank()) {
            onError("Account ID is missing")
            return
        }

        waitingListModel.leaveWaitingList(
            lotteryId = lotteryId,
            accountId = accountId,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}