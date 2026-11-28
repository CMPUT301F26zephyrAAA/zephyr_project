
package com.example.zephyrcampsites

import java.util.Date

// AI Citation [AI-15], [AI-16]
// OpenAI, ChatGPT assisted with reservation filtering,
// sorting, date validation, and testability improvements.
// AI usage and citations documented in the GitHub Wiki.
/**
 * Controller responsible for retrieving and organizing an entrant's
 * active and past campsite reservations.
 *
 * Uses ReservationsModel to retrieve reservation records from Firestore
 * and applies filtering and sorting based on reservation dates and
 * cancellation status.
 *
 * US 01.06.01 - Active Reservations
 * US 01.06.02 - Past Reservations
 *
 * @param reservationsModel Optional model used to retrieve reservation records.
 */
class ReservationsController(
    private val reservationsModel: ReservationsModel? = null
) {

    /**
     * Initializes the Firebase-backed model only when reservation
     * retrieval is needed. Filtering tests do not initialize Firebase.
     */
    private val model: ReservationsModel by lazy {
        reservationsModel ?: ReservationsModel()
    }

    internal fun loadOwnerReservations(
        onSuccess: (OwnerReservationList) -> Unit, onError: (Exception) -> Unit
    ) = model.getOwnerReservations(onSuccess, onError)

    internal fun cancelOwnerReservation(
        reservation: OwnerReservation, owner: Account, onSuccess: () -> Unit, onError: (Exception) -> Unit
    ) = model.cancelOwnerReservation(reservation.id, owner.id, onSuccess, onError)

    /**
     * Filters the owner-scoped reservations returned by the callable to active stays.
     *
     * @param reservations Reservations already scoped to the signed-in owner's campsites.
     * @param currentTimeMillis Server-aligned current time in milliseconds.
     * @return Confirmed, non-cancelled reservations whose checkout has not passed.
     */
    internal fun filterActiveOwnerReservations(
        reservations: List<OwnerReservation>,
        currentTimeMillis: Long
    ): List<OwnerReservation> {
        return reservations
            .filter { reservation ->
                !reservation.cancelled &&
                    reservation.startMillis < reservation.endMillis &&
                    reservation.endMillis > currentTimeMillis
            }
            .sortedBy { it.startMillis }
    }

    /**
     * Filters the owner-scoped reservations returned by the callable to completed stays.
     *
     * @param reservations Reservations already scoped to the signed-in owner's campsites.
     * @param currentTimeMillis Server-aligned current time in milliseconds.
     * @return Non-cancelled reservations whose checkout has passed, newest first.
     */
    internal fun filterPastOwnerReservations(
        reservations: List<OwnerReservation>,
        currentTimeMillis: Long
    ): List<OwnerReservation> {
        return reservations
            .filter { reservation ->
                !reservation.cancelled &&
                    reservation.startMillis < reservation.endMillis &&
                    reservation.endMillis <= currentTimeMillis
            }
            .sortedByDescending { it.endMillis }
    }

    /**
     * Retrieves all reservations for an entrant in a single request.
     * The caller can then classify active and past reservations
     * using the same cutoff time.
     */
    fun getReservations(
        accountId: String,
        onSuccess: (List<ReservedUser>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        model.getEntrantReservations(
            accountId = accountId,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Retrieves the entrant's active and upcoming reservations.
     *
     * A reservation is active or upcoming if it has not been cancelled,
     * has valid booking dates, and its end date is later than the
     * current time.
     *
     * @param accountId Current entrant's device-linked account ID.
     * @param onSuccess Called with active reservations sorted by start date.
     * @param onError Called if reservation retrieval fails.
     */
    fun getActiveReservations(
        accountId: String,
        onSuccess: (List<ReservedUser>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        model.getEntrantReservations(
            accountId = accountId,
            onSuccess = { reservations ->
                onSuccess(filterActiveReservations(reservations))
            },
            onError = onError
        )
    }

    /**
     * Retrieves the entrant's past reservations.
     *
     * A reservation is considered past when its end date is at or
     * before the current time and it has not been cancelled.
     *
     * @param accountId Current entrant's device-linked account ID.
     * @param onSuccess Called with past reservations sorted by end date.
     * @param onError Called if reservation retrieval fails.
     */
    fun getPastReservations(
        accountId: String,
        onSuccess: (List<ReservedUser>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        model.getEntrantReservations(
            accountId = accountId,
            onSuccess = { reservations ->
                onSuccess(filterPastReservations(reservations))
            },
            onError = onError
        )
    }

    /**
     * Filters active and upcoming reservations.
     *
     * A reservation is active or upcoming when it has not been cancelled,
     * has a valid booking period, and its checkout date is in the future.
     *
     * @param reservations Reservations to filter.
     * @param currentTime Time used to determine whether a reservation is active.
     * @return Active reservations sorted by start date, earliest first.
     */
    fun filterActiveReservations(
        reservations: List<ReservedUser>,
        currentTime: Date = Date()
    ): List<ReservedUser> {
        return reservations
            .filter { reservation ->
                val start = reservation.bookedPeriodStart
                val end = reservation.bookedPeriodEnd

                !reservation.cancelled &&
                        start != null &&
                        end != null &&
                        start.before(end) &&
                        end.after(currentTime)
            }
            .sortedBy { it.bookedPeriodStart }
    }

    /**
     * Filters active reservations belonging to campsites
     * owned by the specified owner.
     *
     * @param reservations Reservations to filter.
     * @param campsites Available campsites.
     * @param ownerId Current owner's account ID.
     * @param currentTime Time used to determine active reservations.
     * @return Active reservations belonging to the owner.
     */
    fun filterOwnerActiveReservations(
        reservations: List<ReservedUser>,
        campsites: List<Campsite>,
        ownerId: String,
        currentTime: Date = Date()
    ): List<ReservedUser> {

        val ownerCampsiteIds = filterOwnerCampsites(
            campsites,
            ownerId
        ).map { it.id }.toSet()

        val ownerReservations = reservations.filter {
            it.campsiteId in ownerCampsiteIds
        }

        return filterActiveReservations(
            ownerReservations,
            currentTime
        )
    }

    /**
     * Filters past reservations, including cancelled reservations whose checkout dates have passed.
     * @param reservations Reservations to filter.
     * @param currentTime Time used to determine whether a reservation is past.
     * @return Past reservations sorted by checkout date, most recent first.
     */
    fun filterPastReservations(
        reservations: List<ReservedUser>,
        currentTime: Date = Date()
    ): List<ReservedUser> {
        return reservations
            .filter { reservation ->
                val start = reservation.bookedPeriodStart
                val end = reservation.bookedPeriodEnd

                start != null &&
                        end != null &&
                        start.before(end) &&
                        !end.after(currentTime)
            }
            .sortedByDescending { it.bookedPeriodEnd }
    }
}
