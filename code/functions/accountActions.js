// Verified profile access, lottery declines, and campsite feedback.
// AI Citation [AI-39] (Bhav Saluja). OpenAI, Codex, 2026-11-27.
// See the team AI Documentation Google Doc.
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {FieldValue} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");
const logger = require("firebase-functions/logger");

/**
 * Builds the authenticated account endpoints used by the Android app.
 * @param {FirebaseFirestore.Firestore} db App database.
 * @return {Object} Firebase callable endpoints.
 */
module.exports = (db) => {
  const accounts = db.collection("accounts");
  const links = db.collection("notificationAccountLinks");
  const reservations = db.collection("reservation");
  const campsites = db.collection("campsite");
  const lotteries = db.collection("lottery");
  const waitlist = db.collection("waitingList");
  const results = db.collection("lotteryDrawResults");

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

  /**
   * Checks the binding inside the transaction that changes account data.
   * @param {FirebaseFirestore.Transaction} transaction Active transaction.
   * @param {Object} auth Firebase Auth context of the caller.
   * @param {string} accountId Profile being acted on.
   */
  async function verify(transaction, auth, accountId) {
    const own = accountId.startsWith("auth_") ?
      accountId === "auth_" + auth.uid :
      (await transaction.get(links.doc(accountId))).get("uid") === auth.uid;
    if (!own) {
      throw new HttpsError(
          "permission-denied",
          "Verify profile ownership first.",
      );
    }
  }

  /** Finds the canonical profile without exposing private legacy fields. */
  const resolveAccount = authenticated(async (data, auth) => {
    const deviceId = id(data.deviceId);
    const linked = await links.where("uid", "==", auth.uid).limit(2).get();
    if (linked.size > 1) {
      throw new HttpsError(
          "failed-precondition",
          "Contact the profile administrator.",
      );
    }
    if (!linked.empty) {
      const accountId = linked.docs[0].id;
      if (
        !accountId.startsWith("auth_") &&
        (await accounts.doc(accountId).get()).exists
      ) {
        return {accountId, admin: auth.token.admin === true};
      }
      throw new HttpsError(
          "failed-precondition",
          "Contact the profile administrator.",
      );
    }
    if (
      !deviceId.startsWith("auth_") &&
      (await accounts.doc(deviceId).get()).exists
    ) {
      return {linkRequired: true, accountId: deviceId};
    }
    return {accountId: "auth_" + auth.uid, admin: auth.token.admin === true};
  });

  /** Returns the public label used on campsite detail screens. */
  const getAccountDisplayName = authenticated(async (data) => {
    const site = await campsites.doc(id(data.campsiteId)).get();
    const ownerId = site.get("ownerID");
    if (!ownerId) return {name: ""};
    const account = await accounts.doc(id(ownerId)).get();
    return {
      name:
        typeof account.get("name") === "string" ? account.get("name") : "",
    };
  });

  /**
   * Plans a draw update that keeps survivor positions synchronized.
   * @param {FirebaseFirestore.Transaction} transaction Active transaction.
   * @param {FirebaseFirestore.DocumentSnapshot} lottery Lottery settings.
   * @param {FirebaseFirestore.DocumentSnapshot} result Previous draw result.
   * @param {string} accountId Declining or deleted account.
   * @return {Promise<Object|null>} Draw changes, if the account is in the draw.
   */
  async function planDrawChange(transaction, lottery, result, accountId) {
    if (!lottery.exists || !result.exists) return null;
    const originalWinners = result.get("winnerIds") ?? [];
    const originalBackups = result.get("backupIds") ?? [];
    const wasWinner = originalWinners.includes(accountId);
    if (!wasWinner && !originalBackups.includes(accountId)) return null;
    const winners = originalWinners.filter((id) => id !== accountId);
    const backups = originalBackups.filter((id) => id !== accountId);
    const declined = result.get("declinedIds") ?? [];
    const changes = [];
    let promoted = false;
    const refs = backups.map((backupId) =>
      waitlist.doc(lottery.id + "_" + id(backupId)),
    );
    const entries = refs.length ? await transaction.getAll(...refs) : [];
    for (const [index, entry] of entries.entries()) {
      const backupId = backups[index];
      if (
        entry.get("accountId") !== backupId ||
        entry.get("lotteryId") !== lottery.id ||
        entry.get("drawStatus") !== "BACKUP"
      ) {
        continue;
      }
      if (
        wasWinner &&
        !promoted &&
        winners.length < lottery.get("numberOfWinners") &&
        !winners.includes(backupId) &&
        !declined.includes(backupId)
      ) {
        const candidate = await transaction.get(accounts.doc(backupId));
        if (candidate.exists && candidate.get("deletionPending") !== true) {
          winners.push(backupId);
          changes.push([
            entry.ref,
            {drawStatus: "WINNER", backupPosition: 0},
          ]);
          promoted = true;
          continue;
        }
      }
      const position = index + (promoted ? 0 : 1);
      if (entry.get("backupPosition") !== position) {
        changes.push([entry.ref, {backupPosition: position}]);
      }
    }
    const remaining = promoted ?
      backups.filter((id) => id !== winners.at(-1)) :
      backups;
    return {
      changes,
      fields: {
        winnerIds: winners,
        backupIds: remaining,
        winnerCount: winners.length,
        backupCount: remaining.length,
      },
    };
  }

  /** Declines only the authenticated entrant's unbooked winning offer. */
  const declineLotteryOffer = authenticated(async (data, auth) => {
    const accountId = id(data.accountId);
    const lotteryId = id(data.lotteryId);
    await db.runTransaction(async (transaction) => {
      await verify(transaction, auth, accountId);
      const [account, lottery, result, entry] = await transaction.getAll(
          accounts.doc(accountId),
          lotteries.doc(lotteryId),
          results.doc(lotteryId),
          waitlist.doc(lotteryId + "_" + accountId),
      );
      if (
        !account.exists ||
        account.get("deletionPending") === true ||
        lottery.get("drawCompleted") !== true ||
        entry.get("drawStatus") !== "WINNER" ||
        entry.get("accountId") !== accountId ||
        entry.get("lotteryId") !== lotteryId ||
        !result.get("winnerIds")?.includes(accountId) ||
        result.get("declinedIds")?.includes(accountId)
      ) {
        throw new HttpsError(
            "failed-precondition",
            "No winning offer is available to decline.",
        );
      }
      const bookings = await transaction.get(
          reservations.where("accountId", "==", accountId),
      );
      if (
        bookings.docs.some(
            (booking) =>
              booking.get("campsiteId") === lottery.get("campsiteId") &&
            booking.get("cancelled") !== true,
        )
      ) {
        throw new HttpsError(
            "failed-precondition",
            "Cancel your existing reservation instead.",
        );
      }
      const plan = await planDrawChange(
          transaction,
          lottery,
          result,
          accountId,
      );
      transaction.update(entry.ref, {
        drawStatus: "DECLINED",
        backupPosition: 0,
      });
      transaction.update(result.ref, {
        ...plan.fields,
        declinedIds: [
          ...new Set([...(result.get("declinedIds") ?? []), accountId]),
        ],
        lastDeclinedAccountId: accountId,
      });
      for (const [ref, fields] of plan.changes) {
        transaction.update(ref, fields);
      }
    });
    return {};
  });

  /**
   * Replaces one score while preserving legacy aggregate entries.
   * @param {FirebaseFirestore.DocumentSnapshot} campsite Scored campsite.
   * @param {number|undefined} previous One saved score to remove.
   * @param {number|undefined} next New score to append.
   * @return {Object} Updated score list and average.
   */
  function ratingAggregate(campsite, previous, next) {
    const scores = [...(campsite.get("rating") ?? [])];
    if (!scores.every((score) => Number.isInteger(score))) {
      throw new HttpsError(
          "failed-precondition",
          "Invalid campsite rating aggregate.",
      );
    }
    if (previous !== undefined) {
      const index = scores.indexOf(previous);
      if (index < 0) {
        throw new HttpsError(
            "failed-precondition",
            "Campsite rating aggregate needs repair.",
        );
      }
      scores.splice(index, 1);
    }
    if (next !== undefined) scores.push(next);
    return {
      rating: scores,
      averageRating: scores.length ?
        scores.reduce((total, score) => total + score, 0) / scores.length :
        FieldValue.delete(),
    };
  }

  /** Saves feedback for an actual completed stay owned by the caller. */
  const saveCampsiteFeedback = authenticated(async (data, auth) => {
    const accountId = id(data.accountId);
    const campsite = campsites.doc(id(data.campsiteId));
    const reservation = reservations.doc(id(data.reservationId));
    const rating = campsite.collection("ratings").doc(accountId);
    const {score, review} = data;
    if (
      (score !== undefined &&
        (!Number.isInteger(score) || score < 1 || score > 5)) ||
      typeof review !== "string" ||
      (score === undefined && !review.trim())
    ) {
      throw new HttpsError(
          "invalid-argument",
          "Enter a valid rating or review.",
      );
    }
    await db.runTransaction(async (transaction) => {
      await verify(transaction, auth, accountId);
      const [profile, site, booking, previous] = await transaction.getAll(
          accounts.doc(accountId),
          campsite,
          reservation,
          rating,
      );
      const start = booking.get("bookedPeriodStart")?.toMillis?.();
      const end = booking.get("bookedPeriodEnd")?.toMillis?.();
      const roles = profile.get("userType");
      if (
        !site.exists ||
        !profile.exists ||
        profile.get("deletionPending") === true ||
        !(
          roles === "entrant" ||
          (Array.isArray(roles) && roles.includes("entrant"))
        ) ||
        booking.get("accountId") !== accountId ||
        booking.get("campsiteId") !== campsite.id ||
        booking.get("cancelled") !== false ||
        !(start < end && end <= Date.now())
      ) {
        throw new HttpsError(
            "failed-precondition",
            "A non-cancelled stay must have ended.",
        );
      }
      const fields = {
        accountId,
        campsiteId: campsite.id,
        reservationId: reservation.id,
        review: review.trim(),
        updatedAt: FieldValue.serverTimestamp(),
      };
      if (score !== undefined) {
        fields.score = score;
        transaction.update(
            campsite,
            ratingAggregate(site, previous.get("score"), score),
        );
      }
      transaction.set(rating, fields, {merge: true});
    });
    return {};
  });

  /** Removes a rating and its aggregate entry for a verified admin. */
  const removeCampsiteFeedback = authenticated(async (data, auth) => {
    if (auth.token.admin !== true) {
      throw new HttpsError(
          "permission-denied",
          "Administrator access is required.",
      );
    }
    const campsite = campsites.doc(id(data.campsiteId));
    const rating = campsite.collection("ratings").doc(id(data.accountId));
    await db.runTransaction(async (transaction) => {
      const [site, previous] = await transaction.getAll(campsite, rating);
      if (site.exists && previous.get("score") !== undefined) {
        transaction.update(
            campsite,
            ratingAggregate(site, previous.get("score")),
        );
      }
      if (previous.exists) transaction.delete(rating);
    });
    return {};
  });

  /** Deletes the profile and its references in one transaction. */
  const deleteProfile = authenticated(async (data, auth) => {
    const accountId = id(data.accountId);
    const imagePath = await db.runTransaction(async (transaction) => {
      if (auth.token.admin !== true) {
        await verify(transaction, auth, accountId);
      }
      const profile = await transaction.get(accounts.doc(accountId));
      if (!profile.exists) return null;
      const [entries, bookings, ratings] = await Promise.all([
        transaction.get(waitlist.where("accountId", "==", accountId)),
        transaction.get(reservations.where("accountId", "==", accountId)),
        transaction.get(
            db.collectionGroup("ratings").where("accountId", "==", accountId),
        ),
      ]);
      const entryGroups = new Map();
      for (const entry of entries.docs) {
        const lotteryId = id(entry.get("lotteryId"));
        entryGroups.set(lotteryId, (entryGroups.get(lotteryId) ?? 0) + 1);
      }
      const drawUpdates = [];
      for (const [lotteryId, count] of entryGroups) {
        const [lottery, result] = await transaction.getAll(
            lotteries.doc(lotteryId),
            results.doc(lotteryId),
        );
        if (!lottery.exists) continue;
        const draw = await planDrawChange(
            transaction,
            lottery,
            result,
            accountId,
        );
        drawUpdates.push({lottery, result, count, draw});
      }
      const campsiteUpdates = [];
      for (const rating of ratings.docs) {
        const score = rating.get("score");
        if (score === undefined) continue;
        const site = await transaction.get(rating.ref.parent.parent);
        if (site.exists) {
          campsiteUpdates.push([site.ref, ratingAggregate(site, score)]);
        }
      }

      for (const {lottery, result, count, draw} of drawUpdates) {
        transaction.update(lottery.ref, {
          entrantCount: Math.max(
              0,
              (lottery.get("entrantCount") ?? 0) - count,
          ),
        });
        if (draw) {
          transaction.update(result.ref, draw.fields);
          for (const [ref, fields] of draw.changes) {
            transaction.update(ref, fields);
          }
        }
      }
      for (const entry of entries.docs) transaction.delete(entry.ref);
      for (const rating of ratings.docs) transaction.delete(rating.ref);
      for (const [ref, fields] of campsiteUpdates) {
        transaction.update(ref, fields);
      }
      for (const booking of bookings.docs) {
        const fields = {accountId: FieldValue.delete()};
        const checkout = booking.get("bookedPeriodEnd")?.toMillis?.();
        if (booking.get("cancelled") !== true && !(checkout <= Date.now())) {
          Object.assign(fields, {
            cancelled: true,
            status: "cancelled",
            cancelledAt: FieldValue.serverTimestamp(),
            cancellationReason: "Profile deleted.",
          });
        }
        transaction.update(booking.ref, fields);
      }
      const imagePath = profile.get("imagePath");
      transaction.delete(profile.ref);
      if (!accountId.startsWith("auth_")) {
        transaction.delete(links.doc(accountId));
      }
      return typeof imagePath === "string" ? imagePath : null;
    });
    // Storage is independent of Firestore. Only remove the profile's own file.
    const path = imagePath?.replace(/^\//, "").split("/");
    if (
      path?.length === 3 &&
      path[0] === "profile_images" &&
      path[1] === accountId &&
      path[2]
    ) {
      try {
        await getStorage().bucket().file(path.join("/")).delete({
          ignoreNotFound: true,
        });
      } catch (error) {
        logger.warn("Profile image cleanup failed.", {accountId, error});
      }
    }
    return {};
  });

  return {
    resolveAccount,
    getAccountDisplayName,
    declineLotteryOffer,
    saveCampsiteFeedback,
    removeCampsiteFeedback,
    deleteProfile,
  };
};
