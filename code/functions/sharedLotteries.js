// Persists lottery periods and winning reservations through server transactions.
// OpenAI, Codex, US 02.04.02 shared lottery and booking transactions, 2026-11-27.
const {createHash} = require("node:crypto");

const validId = (value) => typeof value === "string" &&
  /^[^/]{1,128}$/.test(value) && !/^(__.*__|\.{1,2})$/.test(value);
const overlaps = (start, end, otherStart, otherEnd) =>
  start < otherEnd && otherStart < end;
const sitesFor = (lottery) => lottery.campsiteIds?.length ?
  lottery.campsiteIds : [lottery.campsiteId];
function sharedLotteryHandlers(db, {
  now = Date.now,
  HttpsError = require("firebase-functions/v2/https").HttpsError,
  Timestamp = require("firebase-admin/firestore").Timestamp,
  FieldValue = require("firebase-admin/firestore").FieldValue,
} = {}) {
  const fail = (code, message) => {
    throw new HttpsError(code, message);
  };
  const lotteries = db.collection("lottery");
  const campsites = db.collection("campsite");
  const reservations = db.collection("reservation");

  async function verifyAccount(transaction, request) {
    if (!request.auth) {
      fail("unauthenticated", "Sign in to continue.");
    }
    const accountId = "auth_" + request.auth.uid;
    const account = await transaction.get(db.collection("accounts").doc(accountId));
    if (!account.exists || account.get("deletionPending") === true) {
      fail("permission-denied", "This account cannot perform this action.");
    }
    return accountId;
  }

  // Reading and writing the same campsite lock also serializes empty query results.
  async function lockSites(transaction, ids) {
    const refs = [...ids].sort().map((id) =>
      db.collection("lotteryBookingLocks").doc(id));
    await transaction.getAll(...refs);
    return refs;
  }

  function touchLocks(transaction, refs) {
    for (const ref of refs) {
      transaction.set(ref, {updatedAt: FieldValue.serverTimestamp()});
    }
  }

  async function saveLotterySettings(request) {
    const data = request.data || {};
    const {lotteryId, registrationStart, registrationEnd, bookingStart,
      bookingEnd, numberOfWinners, waitlistCapacity = null} = data;
    const lotteryName = typeof data.lotteryName === "string" ?
      data.lotteryName.trim() : "";
    const dates = [registrationStart, registrationEnd, bookingStart, bookingEnd];
    if (!validId(lotteryId) || !lotteryName ||
        !dates.every((value) => Number.isSafeInteger(value) && value > 0 &&
          value <= 253402300799999) ||
        !(registrationStart < registrationEnd && registrationEnd < bookingStart &&
          bookingStart < bookingEnd) ||
        !Number.isInteger(numberOfWinners) || numberOfWinners < 1 ||
        numberOfWinners > 2147483647 ||
        (waitlistCapacity !== null &&
          (!Number.isInteger(waitlistCapacity) || waitlistCapacity < 1 ||
            waitlistCapacity > 2147483647))) {
      fail("invalid-argument", "Check the lottery name, dates, and capacity.");
    }
    const settings = {lotteryName, registrationStart, registrationEnd,
      bookingStart, bookingEnd, numberOfWinners, waitlistCapacity};

    return db.runTransaction(async (transaction) => {
      const accountId = await verifyAccount(transaction, request);
      const ref = lotteries.doc(lotteryId);
      const saved = await transaction.get(ref);
      const current = saved.data();
      const ids = data.create === true ? data.campsiteIds :
        current && sitesFor(current);
      if (!Array.isArray(ids) || !ids.length || !ids.every(validId) ||
          new Set(ids).size !== ids.length || ids.length > 100) {
        fail("invalid-argument", "Select up to 100 distinct campsites.");
      }
      const sites = await transaction.getAll(...ids.map((id) => campsites.doc(id)));
      if (sites.some((site) => site.get("ownerID") !== accountId)) {
        fail("permission-denied", "You must own every participating campsite.");
      }

      if (data.create === true && current) {
        const sameSites = JSON.stringify(ids) === JSON.stringify(sitesFor(current));
        if (sameSites && Object.keys(settings).every((key) => settings[key] === current[key])) {
          return {lotteryId};
        }
        fail("already-exists", "This lottery already exists.");
      }

      if (current) {
        if (current.drawCompleted || !["PENDING", undefined].includes(current.drawStatus)) {
          fail("failed-precondition", "The draw has already started.");
        }
        const started = now() >= current.registrationStart;
        if (started && ["registrationStart", "registrationEnd", "bookingStart",
          "bookingEnd", "numberOfWinners"].some((key) => current[key] !== settings[key])) {
          fail("failed-precondition", "The dates and winner count are locked after registration opens.");
        }
        const oldCapacity = current.waitlistCapacity ?? null;
        if (oldCapacity !== waitlistCapacity &&
            ((waitlistCapacity !== null && waitlistCapacity < current.entrantCount) ||
             (started && (oldCapacity === null || waitlistCapacity === null ||
               waitlistCapacity <= oldCapacity)))) {
          fail("failed-precondition", "The waitlist capacity cannot be changed to that value.");
        }
      }
      if (registrationStart !== current?.registrationStart && registrationStart <= now()) {
        fail("invalid-argument", "Registration must start in the future.");
      }

      if (!current || current.bookingStart !== bookingStart || current.bookingEnd !== bookingEnd) {
        const locks = await lockSites(transaction, ids);
        const candidates = await transaction.get(lotteries.where("bookingEnd", ">=", bookingStart));
        for (const other of candidates.docs) {
          const period = other.data();
          // Match the existing lottery validator: touching lottery periods also conflict.
          if (other.id !== lotteryId && period.bookingStart <= bookingEnd &&
              sitesFor(period).some((id) => ids.includes(id))) {
            fail("already-exists", "A participating campsite already has a lottery in that period.");
          }
        }
        touchLocks(transaction, locks);
      }

      if (current) {
        transaction.update(ref, settings);
      } else {
        transaction.create(ref, {...settings, id: lotteryId,
          campsiteId: ids[0], campsiteIds: ids, entrantCount: 0,
          drawCompleted: false, drawCompletedAt: 0, drawStatus: "PENDING",
          drawStartedAt: 0});
      }
      return {lotteryId};
    });
  }

  async function confirmLotteryBooking(request) {
    const {lotteryId, campsiteId, start, end} = request.data || {};
    if (!validId(lotteryId) || !validId(campsiteId) ||
        !Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start >= end) {
      fail("invalid-argument", "Select a campsite and valid check-in and checkout dates.");
    }
    return db.runTransaction(async (transaction) => {
      const accountId = await verifyAccount(transaction, request);
      const reservationId = createHash("sha256")
          .update(JSON.stringify([lotteryId, accountId])).digest("hex");
      const reservation = reservations.doc(reservationId);
      const [lotteryDoc, entry, result, existing, site] = await transaction.getAll(
          lotteries.doc(lotteryId),
          db.collection("waitingList").doc(lotteryId + "_" + accountId),
          db.collection("lotteryDrawResults").doc(lotteryId),
          reservation, campsites.doc(campsiteId),
      );
      const lottery = lotteryDoc.data();
      if (!lottery?.drawCompleted || entry.get("drawStatus") !== "WINNER" ||
          entry.get("accountId") !== accountId || entry.get("lotteryId") !== lotteryId ||
          !result.get("winnerIds")?.includes(accountId) ||
          result.get("declinedIds")?.includes(accountId)) {
        fail("permission-denied", "There is no winning offer to confirm.");
      }
      if (existing.exists) {
        if (existing.get("campsiteId") === campsiteId &&
            existing.get("bookedPeriodStart").toMillis() === start &&
            existing.get("bookedPeriodEnd").toMillis() === end &&
            existing.get("cancelled") === false) {
          if (request.data.checkOnly !== true) {
            return {reservationId};
          }
        }
        fail("already-exists", "This winning offer has already been used.");
      }
      if (!site.exists || !sitesFor(lottery).includes(campsiteId) ||
          start < lottery.bookingStart || end > lottery.bookingEnd || start < now()) {
        fail("invalid-argument", "Choose a participating campsite and future dates within the lottery period.");
      }

      const siteStart = site.get("bookingPeriodStart")?.toMillis?.();
      const siteEnd = site.get("bookingPeriodEnd")?.toMillis?.();
      if (siteStart > 1 && siteEnd > siteStart && (start < siteStart || end > siteEnd)) {
        fail("failed-precondition", "Those dates are outside this campsite's available period.");
      }

      const prior = await transaction.get(reservations.where("accountId", "==", accountId));
      if (prior.docs.some((booking) => booking.get("lotteryId") === lotteryId ||
        (!booking.get("lotteryId") &&
          booking.get("campsiteId") === lottery.campsiteId &&
          booking.get("cancelled") !== true))) {
        fail("already-exists", "A reservation already exists for this offer.");
      }

      const locks = await lockSites(transaction, [campsiteId]);
      const bookings = await transaction.get(reservations.where("campsiteId", "==", campsiteId));
      if (bookings.docs.some((booking) => booking.get("cancelled") !== true &&
        overlaps(start, end, booking.get("bookedPeriodStart")?.toMillis?.(),
            booking.get("bookedPeriodEnd")?.toMillis?.()))) {
        fail("already-exists", "That campsite is booked for those dates. Choose another campsite or period.");
      }
      if (request.data.checkOnly === true) {
        return {available: true};
      }
      touchLocks(transaction, locks);
      transaction.create(reservation, {
        id: reservationId, lotteryId, accountId, campsiteId,
        bookedPeriodStart: Timestamp.fromMillis(start),
        bookedPeriodEnd: Timestamp.fromMillis(end),
        cancelled: false, status: "confirmed",
      });
      return {reservationId};
    });
  }

  return {saveLotterySettings, confirmLotteryBooking};
}

module.exports = (db) => {
  const {onCall} = require("firebase-functions/v2/https");
  const handlers = sharedLotteryHandlers(db);
  return {
    saveLotterySettings: onCall(handlers.saveLotterySettings),
    confirmLotteryBooking: onCall(handlers.confirmLotteryBooking),
  };
};
module.exports.sharedLotteryHandlers = sharedLotteryHandlers;
