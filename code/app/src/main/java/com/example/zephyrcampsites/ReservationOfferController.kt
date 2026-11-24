package com.example.zephyrcampsites

/**
 * Controller for entrant reservation-offer actions.
 */
class ReservationOfferController(private val reservationOfferModel: ReservationOfferModel) {
    fun getEntrantDrawStatus(
        lotteryId: String,
        accountId: String,
        onResult: (String?) -> Unit,
        onError: (String) -> Unit
    ) {
        reservationOfferModel.getEntrantDrawStatus(
            lotteryId = lotteryId,
            accountId = accountId,
            onResult = onResult,
            onError = onError
        )
    }

    fun declineReservationOffer(
        lotteryId: String,
        accountId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (lotteryId.isBlank() || accountId.isBlank()) {
            onError("Lottery or account ID is missing")
            return
        }

        reservationOfferModel.declineReservationOffer(
            lotteryId = lotteryId,
            accountId = accountId,
            onSuccess = onSuccess,
            onError = onError
        )
    }


    fun getDeclinedEntrants(
        lotteryId: String,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        reservationOfferModel.getDeclinedEntrants(
            lotteryId = lotteryId,
            onSuccess = onSuccess,
            onError = onError
        )
    }

}
