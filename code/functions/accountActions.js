// Verified profile access, lottery declines, and campsite feedback.
// AI Citation [AI-39] (Bhav Saluja). OpenAI, Codex, 2026-11-27.
// See the team AI Documentation Google Doc.
// Hiral - [AI-10] OpenAI ChatGPT Add admin removal flow
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {FieldValue} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");
const logger = require("firebase-functions/logger");
const {usesLotteryOffer} = require("./sharedLotteries");

/**
 * Builds the authenticated account endpoints used by the Android app.
 * @param {FirebaseFirestore.Firestore} db App database.
 * @return {Object} Firebase callable endpoints.
 */
module.exports = (db) => {
  const accounts = db.collection("accounts");
  const reservations = db.collection("reservation");
  const campsites = db.collection("campsite");
  const lotteries = db.collection("lottery");
  const waitlist = db.collection("waitingList");
  const results = db.collection("lotteryDrawResults");
  const revokedOwners = db.collection("revokedCampsiteOwners");

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
   * Requires the caller to own the requested profile.
   * @param {Object} auth Verified Firebase Auth context.
   * @param {string} accountId Profile being acted on.
   */
  function verify(auth, accountId) {
    if (accountId !== "auth_" + auth.uid) {
      throw new HttpsError(
          "permission-denied",
          "This profile belongs to another account.",
      );
    }
  }

  // OpenAI Codex, US 03.07.01 owner revocation, 2026-11-28.
  /** Revokes owner access and saves an immutable audit record together. */
  const removeOwnerPrivileges = authenticated(async (data, auth) => {
    if (auth.token.admin !== true) {
      throw new HttpsError(
          "permission-denied", "Administrator access required.",
      );
    }
    const adminId = "auth_" + auth.uid;
    const ownerId = id(data.ownerId);
    const reason = typeof data.reason === "string" ? data.reason.trim() : "";
    if (ownerId === adminId || !reason || reason.length > 500) {
      throw new HttpsError(
          "invalid-argument", "Select another owner and enter a reason.",
      );
    }
    await db.runTransaction(async (transaction) => {
      const [owner, revoked] = await transaction.getAll(
          accounts.doc(ownerId),
          revokedOwners.doc(ownerId),
      );
      if (revoked.exists) return;
      const roles = owner.get("userType");
      const hasOwnerRole = Array.isArray(roles) ?
        roles.includes("owner") :
        roles == null || roles === "owner" || roles === "admin";
      if (!owner.exists || !hasOwnerRole) {
        throw new HttpsError(
            "failed-precondition", "This profile is not a registered owner.",
        );
      }
      transaction.update(owner.ref, {ownerPrivilegesRevoked: true});
      transaction.create(revoked.ref, {revoked: true});
      transaction.create(db.collection("ownerRemovalAudit").doc(ownerId), {
        adminId,
        ownerId,
        reason,
        timestamp: FieldValue.serverTimestamp(),
      });
    });
    return {};
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

  // AI Citation [AI-43] (Bhav Saluja)
  // OpenAI, Codex, November 27, 2026. Added a campsite scope for owner chat.
  // See the team AI Documentation Google Doc.
  // AI Citation [AI-49] (Bhav Saluja)
  // OpenAI, Codex, November 27, 2026. Included chat IDs in the owner list.
  // See Bhav's entry in the team's AI Documentation Google Doc.
  /** Lists only bookings for campsites currently owned by the caller. */
  const listOwnerReservations = authenticated(async (data, auth) => {
    const accountId = "auth_" + auth.uid;
    const campsiteId =
      data.campsiteId === undefined ? null : id(data.campsiteId);
    return db.runTransaction(
        async (transaction) => {
          const profile = await transaction.get(accounts.doc(accountId));
          const revoked = await transaction.get(revokedOwners.doc(accountId));
          if (!profile.exists || revoked.exists) {
            throw new HttpsError(
                "permission-denied",
                "Your profile is unavailable.",
            );
          }
          let sites;
          if (campsiteId === null) {
            sites = (await transaction.get(
                campsites.where("ownerID", "==", accountId),
            )).docs;
          } else {
            const site = await transaction.get(campsites.doc(campsiteId));
            if (site.get("ownerID") !== accountId) {
              throw new HttpsError(
                  "permission-denied",
                  "This campsite belongs to another account.",
              );
            }
            sites = [site];
          }
          const groups = await Promise.all(
              sites.map(async (site) => {
                const bookings = await transaction.get(
                    reservations.where("campsiteId", "==", site.id),
                );
                return bookings.docs.flatMap((booking) => {
                  const startMillis = booking
                      .get("bookedPeriodStart")
                      ?.toMillis?.();
                  const endMillis = booking
                      .get("bookedPeriodEnd")
                      ?.toMillis?.();
                  const cancelled = booking.get("cancelled");
                  if (
                    !(startMillis < endMillis) ||
                typeof cancelled !== "boolean"
                  ) {
                    return [];
                  }
                  return [
                    {
                      id: booking.id,
                      campsiteId: site.id,
                      campsiteName: site.get("name") ?? "",
                      startMillis,
                      endMillis,
                      cancelled,
                      entrantId: booking.get("accountId") ?? "",
                    },
                  ];
                });
              }),
          );
          const rows = groups.flat();
          const entrantIds = [
            ...new Set(rows.map((row) => row.entrantId)),
          ].filter(
              (value) =>
                typeof value === "string" && /^auth_[^/]{1,123}$/.test(value),
          );
          const entrants = entrantIds.length ?
          await transaction.getAll(
              ...entrantIds.map((value) => accounts.doc(value)),
          ) :
          [];
          const names = new Map(
              entrants.map((profile) => [profile.id, profile.get("name")]),
          );
          return {
            serverMillis: Date.now(),
            reservations: rows.map(({entrantId, ...row}) => ({
              ...row,
              entrantName: names.get(entrantId) ?? "",
              entrantAccountId:
                typeof entrantId === "string" ? entrantId : "",
            })),
          };
        },
        {readOnly: true},
    );
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
      verify(auth, accountId);
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
      // Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
      if (
        bookings.docs.some((booking) =>
          usesLotteryOffer(booking, lotteryId, lottery.data()))
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
      verify(auth, accountId);
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

  /** Removes a score and its aggregate entry for a verified admin. */
  const removeCampsiteFeedback = authenticated(async (data, auth) => {
    if (auth.token.admin !== true) {
      throw new HttpsError(
          "permission-denied",
          "Administrator access is required.",
      );
    }
    verify(auth, id(data.adminAccountId));
    const campsite = campsites.doc(id(data.campsiteId));
    const rating = campsite.collection("ratings").doc(id(data.accountId));
    const {seconds, nanoseconds} = data.updatedAt ?? {};
    if (
      !Number.isInteger(seconds) ||
      !Number.isInteger(nanoseconds) ||
      nanoseconds < 0 ||
      nanoseconds >= 1000000000
    ) {
      throw new HttpsError(
          "invalid-argument",
          "Reload the rating and try again.",
      );
    }
    await db.runTransaction(async (transaction) => {
      const [site, previous] = await transaction.getAll(campsite, rating);
      if (!site.exists || !previous.exists) {
        throw new HttpsError(
            "failed-precondition",
            "This rating is no longer available.",
        );
      }
      const saved = previous.get("updatedAt");
      if (
        saved?.seconds !== seconds ||
        saved?.nanoseconds !== nanoseconds
      ) {
        throw new HttpsError(
            "failed-precondition",
            "This rating has changed. Review it again.",
        );
      }
      const score = previous.get("score");
      if (!Number.isInteger(score) || score < 1 || score > 5) {
        throw new HttpsError(
            "failed-precondition",
            "This entry has no rating to remove.",
        );
      }
      transaction.update(campsite, ratingAggregate(site, score));
      const review = previous.get("review");
      if (typeof review === "string" && review.trim() !== "") {
        transaction.update(rating, {score: FieldValue.delete()});
      } else {
        transaction.delete(rating);
      }
    });
    return {};
  });

  /** Deletes the profile and its references in one transaction. */
  const deleteProfile = authenticated(async (data, auth) => {
    const accountId = id(data.accountId);
    const imagePath = await db.runTransaction(async (transaction) => {
      if (auth.token.admin !== true) {
        verify(auth, accountId);
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
        if (
          booking.get("cancelled") !== true &&
          !(checkout <= Date.now())
        ) {
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
    getAccountDisplayName,
    listOwnerReservations,
    declineLotteryOffer,
    saveCampsiteFeedback,
    removeCampsiteFeedback,
    removeOwnerPrivileges,
    deleteProfile,
  };
};
