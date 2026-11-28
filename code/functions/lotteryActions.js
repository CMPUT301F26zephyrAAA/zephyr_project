// AI Citation [AI-41]
// Anthropic, Claude Code assisted with the lottery save functions,
// including the transactional overlap check and server-time date lock.
// AI usage and citation documented in the team AI Documentation.
// Creates and edits campsite lotteries with server-side booking checks.
// Saves run in one Admin SDK transaction, so the overlap query and the
// write happen together, and the edit cutoff uses the server's clock.
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {FieldValue} = require("firebase-admin/firestore");

/**
 * Builds the lottery save endpoints used by the Android app.
 * @param {FirebaseFirestore.Firestore} db App database.
 * @return {Object} Firebase callable endpoints.
 */
module.exports = (db) => {
  const campsites = db.collection("campsite");
  const lotteries = db.collection("lottery");
  const bookingLocks = db.collection("lotteryBookingLocks");

  /**
   * Lists the campsites of a shared or legacy lottery.
   * @param {FirebaseFirestore.DocumentSnapshot} lottery Saved lottery.
   * @return {string[]} Participating campsite IDs.
   */
  function participatingSites(lottery) {
    const linked = lottery.get("campsiteIds");
    return Array.isArray(linked) && linked.length ? linked : [lottery.get("campsiteId")];
  }

  /**
   * Validates a Firestore document ID supplied by the caller.
   * @param {*} value Untrusted document identifier.
   * @return {string} Valid Firestore document ID.
   */
  function id(value) {
    if (
      typeof value !== "string" ||
      !/^[^/]{1,128}$/.test(value) ||
      !value.trim() ||
      value === "." ||
      value === ".."
    ) {
      throw new HttpsError("invalid-argument", "Invalid record ID.");
    }
    return value;
  }

  /**
   * Validates a date sent as epoch milliseconds.
   * @param {*} value Untrusted date value.
   * @param {string} label Field name used in the error message.
   * @return {number} Date in epoch milliseconds.
   */
  function millis(value, label) {
    if (!Number.isSafeInteger(value) || value <= 0) {
      throw new HttpsError("invalid-argument", `Invalid ${label}.`);
    }
    return value;
  }

  /**
   * Reads and validates the lottery settings sent by the app.
   * @param {Object} data Untrusted callable data.
   * @return {Object} Validated lottery settings.
   */
  function settings(data) {
    const name =
      typeof data.lotteryName === "string" ? data.lotteryName.trim() : "";
    if (!name) {
      throw new HttpsError("invalid-argument", "Enter a lottery name.");
    }
    const values = {
      lotteryName: name,
      registrationStart: millis(data.registrationStart, "registration start"),
      registrationEnd: millis(data.registrationEnd, "registration end"),
      bookingStart: millis(data.bookingStart, "booking start"),
      bookingEnd: millis(data.bookingEnd, "booking end"),
      numberOfWinners: data.numberOfWinners,
      waitlistCapacity: data.waitlistCapacity ?? null,
    };
    if (values.registrationStart >= values.registrationEnd) {
      throw new HttpsError("invalid-argument", "Invalid registration period.");
    }
    // A single-day booking period is valid when its end is after its start.
    if (values.bookingStart >= values.bookingEnd) {
      throw new HttpsError(
          "invalid-argument",
          "Booking end must be after booking start.",
      );
    }
    if (values.bookingStart <= values.registrationEnd) {
      throw new HttpsError(
          "invalid-argument",
          "Booking must start after registration ends.",
      );
    }
    if (
      !Number.isInteger(values.numberOfWinners) ||
      values.numberOfWinners <= 0
    ) {
      throw new HttpsError(
          "invalid-argument",
          "Number of winners must be positive.",
      );
    }
    if (
      values.waitlistCapacity !== null &&
      (!Number.isInteger(values.waitlistCapacity) ||
        values.waitlistCapacity <= 0)
    ) {
      throw new HttpsError(
          "invalid-argument",
          "Waitlist capacity must be positive.",
      );
    }
    return values;
  }

  /**
   * Requires the caller to own the campsite, matching the Firestore rules.
   * @param {FirebaseFirestore.Transaction} transaction Save transaction.
   * @param {string} campsiteId Campsite in the lottery.
   * @param {Object} auth Verified Firebase Auth context.
   */
  async function requireOwner(transaction, campsiteId, auth) {
    const site = await transaction.get(campsites.doc(campsiteId));
    if (!site.exists || site.get("ownerID") !== "auth_" + auth.uid) {
      throw new HttpsError(
          "permission-denied",
          "Only the campsite owner can manage its lottery.",
      );
    }
  }

  /**
   * Rejects a booking period that overlaps another lottery for the campsite.
   *
   * The per-site locks match shared lottery publishing. Touching periods conflict.
   *
   * @param {FirebaseFirestore.Transaction} transaction Save transaction.
   * @param {string[]} campsiteIds Campsites in the lottery.
   * @param {string} lotteryId Lottery being saved, skipped in the check.
   * @param {number} bookingStart Booking period start.
   * @param {number} bookingEnd Booking period end.
   */
  async function requireNoOverlap(
      transaction, campsiteIds, lotteryId, bookingStart, bookingEnd) {
    const locks = [...new Set(campsiteIds)].sort().map((id) => bookingLocks.doc(id));
    await transaction.getAll(...locks);
    const others = await transaction.get(
        lotteries.where("bookingEnd", ">=", bookingStart),
    );
    for (const other of others.docs) {
      const otherStart = other.get("bookingStart");
      const otherEnd = other.get("bookingEnd");
      // Older records without booking dates do not block these dates.
      if (
        other.id === lotteryId ||
        !Number.isFinite(otherStart) ||
        !Number.isFinite(otherEnd) ||
        !participatingSites(other).some((id) => campsiteIds.includes(id))
      ) {
        continue;
      }
      if (!(bookingStart > otherEnd || bookingEnd < otherStart)) {
        throw new HttpsError(
            "failed-precondition",
            "Booking period overlaps with another lottery for this campsite.",
        );
      }
    }
    for (const lock of locks) {
      transaction.set(lock, {updatedAt: FieldValue.serverTimestamp()});
    }
  }

  /**
   * Requires Firebase identity before passing a request to its handler.
   * @param {Function} handler Callable implementation.
   * @return {Function} Firebase callable requiring an authenticated caller.
   */
  function authenticated(handler) {
    return onCall((request) => {
      if (!request.auth) {
        throw new HttpsError("unauthenticated", "Sign in to continue.");
      }
      return handler(request.data ?? {}, request.auth);
    });
  }

  /** Creates a lottery after checking ownership, dates, and overlap. */
  const createLottery = authenticated(async (data, auth) => {
    const lotteryId = id(data.lotteryId);
    const campsiteId = id(data.campsiteId);
    const values = settings(data);
    if (values.registrationStart <= Date.now()) {
      throw new HttpsError(
          "invalid-argument",
          "Registration start must be in the future.",
      );
    }
    const lotteryRef = lotteries.doc(lotteryId);
    await db.runTransaction(async (transaction) => {
      await requireOwner(transaction, campsiteId, auth);
      if ((await transaction.get(lotteryRef)).exists) {
        throw new HttpsError("already-exists", "This lottery already exists.");
      }
      await requireNoOverlap(
          transaction,
          [campsiteId],
          lotteryId,
          values.bookingStart,
          values.bookingEnd,
      );
      transaction.create(lotteryRef, {
        id: lotteryId,
        campsiteId,
        ...values,
        entrantCount: 0,
        drawCompleted: false,
        drawCompletedAt: 0,
        drawStatus: "PENDING",
        drawStartedAt: 0,
      });
    });
    return {lotteryId};
  });

  /** Saves the Edit Lottery form, locking dates by server time. */
  const updateLottery = authenticated(async (data, auth) => {
    const lotteryRef = lotteries.doc(id(data.lotteryId));
    const values = settings(data);
    await db.runTransaction(async (transaction) => {
      const lottery = await transaction.get(lotteryRef);
      if (!lottery.exists) {
        throw new HttpsError("not-found", "Lottery not found.");
      }
      const campsiteIds = [...new Set(participatingSites(lottery))];
      for (const campsiteId of campsiteIds) {
        await requireOwner(transaction, id(campsiteId), auth);
      }
      if (
        lottery.get("drawCompleted") === true ||
        (lottery.get("drawStatus") ?? "PENDING") !== "PENDING"
      ) {
        throw new HttpsError(
            "failed-precondition",
            "This lottery's draw has already started.",
        );
      }

      // Server time, not the phone's clock, decides the cutoff.
      const now = Date.now();
      const started = now >= lottery.get("registrationStart");
      const datesChanged = [
        "registrationStart",
        "registrationEnd",
        "bookingStart",
        "bookingEnd",
      ].some((field) => values[field] !== lottery.get(field));
      if (started && datesChanged) {
        throw new HttpsError(
            "failed-precondition",
            "Registration and booking dates cannot be changed " +
            "after registration starts.",
        );
      }
      if (
        values.registrationStart !== lottery.get("registrationStart") &&
        values.registrationStart <= now
      ) {
        throw new HttpsError(
            "invalid-argument",
            "Registration start must be in the future.",
        );
      }
      if (
        values.bookingStart !== lottery.get("bookingStart") &&
        values.bookingStart <= now
      ) {
        throw new HttpsError(
            "invalid-argument",
            "Booking start must be in the future.",
        );
      }
      const winnersChanged =
        values.numberOfWinners !== lottery.get("numberOfWinners");
      if (started && winnersChanged) {
        throw new HttpsError(
            "failed-precondition",
            "Number of winners cannot be changed after registration starts.",
        );
      }

      // Same capacity rules as LotteryCapacityValidator in the app.
      const oldCapacity = lottery.get("waitlistCapacity") ?? null;
      const newCapacity = values.waitlistCapacity;
      if (newCapacity !== oldCapacity) {
        if (
          newCapacity !== null &&
          newCapacity < (lottery.get("entrantCount") ?? 0)
        ) {
          throw new HttpsError(
              "failed-precondition",
              "Capacity cannot be less than the current entrant count.",
          );
        }
        if (
          started &&
          (newCapacity === null ||
            oldCapacity === null ||
            newCapacity <= oldCapacity)
        ) {
          throw new HttpsError(
              "failed-precondition",
              "Capacity can only be increased after registration starts.",
          );
        }
      }

      await requireNoOverlap(
          transaction,
          campsiteIds,
          lottery.id,
          values.bookingStart,
          values.bookingEnd,
      );
      transaction.update(lotteryRef, values);
    });
    return {};
  });

  return {createLottery, updateLottery};
};
