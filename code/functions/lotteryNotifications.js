// Lottery notification callables for verified accounts and campsite owners.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 waitlist notifications, 2026-11-27.
// Generated the delivery and authorization code.
// See the team's AI documentation.
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {FieldValue} = require("firebase-admin/firestore");
const {createHash} = require("node:crypto");

const MAX_MESSAGE_LENGTH = 2000;

/**
 * Creates the notification endpoints using the application's Firestore.
 * @param {Object} db Firestore database.
 * @return {Object} Callable notification endpoints.
 */
function lotteryNotifications(db) {
  const accounts = db.collection("accounts");
  const notifications = db.collection("lotteryNotifications");

  /**
   * Verifies the caller against their Firebase Auth profile.
   * @param {Object} request Callable request.
   * @param {Object} reader Firestore or its current transaction.
   * @return {Promise<Object>} Verified account snapshot.
   */
  async function verifyAccount(request, reader) {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Firebase sign-in is required.");
    }
    const accountId = (request.data || {}).accountId;
    if (!validId(accountId)) {
      throw new HttpsError("invalid-argument", "Invalid account.");
    }
    if (accountId !== "auth_" + request.auth.uid) {
      throw new HttpsError("permission-denied", "This profile is not yours.");
    }
    const account = (await reader.getAll(accounts.doc(accountId)))[0];
    if (!account.exists || account.get("deletionPending") === true) {
      throw new HttpsError("permission-denied", "This account is unavailable.");
    }
    return account;
  }

  /**
   * Checks current ownership on the write-protected lottery and campsite.
   * @param {Object} reader Firestore or its current transaction.
   * @param {string} lotteryId Lottery selected by the owner.
   * @param {string} ownerId Verified caller account ID.
   * @return {Promise<Object>} Current lottery snapshot.
   */
  async function verifyLotteryOwner(reader, lotteryId, ownerId) {
    if (!validId(lotteryId)) {
      throw new HttpsError("invalid-argument", "Invalid lottery.");
    }
    const [lottery] = await reader.getAll(
        db.collection("lottery").doc(lotteryId),
    );
    const campsiteId = lottery.get("campsiteId");
    if (!validId(campsiteId)) {
      throw new HttpsError(
          "permission-denied", "This lottery is unavailable for notifications.",
      );
    }
    const [campsite] = await reader.getAll(
        db.collection("campsite").doc(campsiteId),
    );
    if (campsite.get("ownerID") !== ownerId) {
      throw new HttpsError(
          "permission-denied", "Only the campsite owner can notify entrants.",
      );
    }
    return lottery;
  }

  /**
   * Loads existing account records for the lottery's current waitlist.
   * @param {Object} lottery Lottery snapshot.
   * @param {Object} transaction Current read or send transaction.
   * @return {Promise<Array<Object>>} Current waitlisted accounts.
   */
  async function waitlistAccounts(lottery, transaction) {
    const waitingList = db.collection("waitingList")
        .where("lotteryId", "==", lottery.id);
    const registrations = await transaction.get(waitingList);
    const refs = registrations.docs.flatMap((document) => {
      const accountId = document.get("accountId");
      const status = document.get("drawStatus");
      const stillWaiting = status === undefined || status === null ||
        status === "WAITING" || status === "BACKUP";
      if (!validId(accountId) || !accountId.startsWith("auth_") ||
          document.id !== `${lottery.id}_${accountId}` ||
          !stillWaiting) {
        return [];
      }
      return [accounts.doc(accountId)];
    });
    if (refs.length === 0) {
      return [];
    }
    const profiles = await transaction.getAll(...refs);
    return profiles.filter((profile) =>
      profile.exists && profile.get("deletionPending") !== true,
    );
  }

  const listLotteryNotificationRecipients = onCall((request) =>
    db.runTransaction(
        async (transaction) => {
          const owner = await verifyAccount(request, transaction);
          const lottery = await verifyLotteryOwner(
              transaction, request.data.lotteryId, owner.id,
          );
          const recipients = await waitlistAccounts(lottery, transaction);
          return recipients.map((profile) => {
            const displayName = profile.get("name");
            return {
              accountId: profile.id,
              name: typeof displayName === "string" ? displayName.trim() : "",
            };
          });
        }, {readOnly: true},
    ),
  );

  const sendLotteryNotification = onCall(async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Firebase sign-in is required.");
    }
    const data = request.data || {};
    const message = typeof data.message === "string" ? data.message.trim() : "";
    const selected = data.recipientIds;
    if (!message || message.length > MAX_MESSAGE_LENGTH ||
        typeof data.allEntrants !== "boolean" || !Array.isArray(selected) ||
        (data.allEntrants ? selected.length !== 0 : selected.length === 0) ||
        !selected.every(validId) || typeof data.requestId !== "string" ||
        !/^[0-9a-f-]{36}$/.test(data.requestId)) {
      throw new HttpsError(
          "invalid-argument", "Enter a message and choose recipients.",
      );
    }

    return db.runTransaction(async (transaction) => {
      const owner = await verifyAccount(request, transaction);
      const lottery = await verifyLotteryOwner(
          transaction, data.lotteryId, owner.id,
      );
      const notificationId = createHash("sha256")
          .update(JSON.stringify([owner.id, data.requestId])).digest("hex");
      const notification = notifications.doc(notificationId);
      const existing = await transaction.get(notification);
      if (existing.exists) {
        const sameSelection = data.allEntrants ||
          sameRecipients(existing.get("intendedRecipientIds"), selected);
        if (existing.get("lotteryId") !== lottery.id ||
            existing.get("message") !== message ||
            existing.get("allEntrants") !== data.allEntrants ||
            !sameSelection) {
          throw new HttpsError(
              "already-exists", "This send ID was used for another message.",
          );
        }
        return deliveryCounts(existing.data());
      }

      const waitlist = await waitlistAccounts(lottery, transaction);
      const selectedIds = new Set(selected);
      const intended = data.allEntrants ? waitlist : waitlist.filter(
          (profile) => selectedIds.has(profile.id),
      );
      if (intended.length === 0 ||
          (!data.allEntrants && intended.length !== selectedIds.size)) {
        throw new HttpsError(
            "failed-precondition",
            "The waitlist changed. Reload and try again.",
        );
      }
      const enabled = intended.filter(notificationsEnabled);
      const lotteryName = lottery.get("lotteryName");
      const record = {
        lotteryId: lottery.id,
        lotteryName: typeof lotteryName === "string" && lotteryName.trim() ?
          lotteryName.trim() : "Campsite lottery",
        campsiteId: lottery.get("campsiteId"),
        senderAccountId: owner.id,
        message,
        allEntrants: data.allEntrants,
        intendedRecipientIds: intended.map((profile) => profile.id),
        deliveredRecipientIds: enabled.map((profile) => profile.id),
        sentAt: FieldValue.serverTimestamp(),
      };
      transaction.create(notification, record);
      return deliveryCounts(record);
    });
  });

  const listReceivedLotteryNotifications = onCall((request) =>
    db.runTransaction(
        async (transaction) => {
          const account = await verifyAccount(request, transaction);
          if (!notificationsEnabled(account)) {
            return [];
          }
          const received = await transaction.get(notifications.where(
              "deliveredRecipientIds", "array-contains", account.id,
          ));
          return received.docs.map((document) => ({
            id: document.id,
            lotteryName: document.get("lotteryName"),
            message: document.get("message"),
            sentAtMillis: document.get("sentAt").toMillis(),
          })).sort((first, second) => second.sentAtMillis - first.sentAtMillis);
        }, {readOnly: true},
    ),
  );

  // AI Citation [AI-40]
  // OpenAI, Codex, US 02.10.02, 2026-11-27. Generated history reads.
  // See Bhav's entry in the team's AI Documentation Google Doc.
  const listSentLotteryNotifications = onCall((request) =>
    db.runTransaction(async (transaction) => {
      const owner = await verifyHistoryOwner(request, transaction);
      const sent = await transaction.get(notifications.where(
          "senderAccountId", "==", owner.id,
      ));
      return sent.docs.sort((first, second) => {
        const firstTime = first.get("sentAt");
        const secondTime = second.get("sentAt");
        return secondTime.seconds - firstTime.seconds ||
          secondTime.nanoseconds - firstTime.nanoseconds;
      }).map(sentNotificationSummary);
    }, {readOnly: true}),
  );

  const getSentLotteryNotification = onCall((request) =>
    db.runTransaction(async (transaction) => {
      const owner = await verifyHistoryOwner(request, transaction);
      const notificationId = request.data.notificationId;
      if (!validId(notificationId)) {
        throw new HttpsError("invalid-argument", "Invalid notification.");
      }
      const notification = await transaction.get(
          notifications.doc(notificationId),
      );
      if (notification.get("senderAccountId") !== owner.id) {
        throw new HttpsError("not-found", "Notification unavailable.");
      }

      const intended = notification.get("intendedRecipientIds");
      const delivered = new Set(notification.get("deliveredRecipientIds"));
      const profiles = intended.length === 0 ? [] : await transaction.getAll(
          ...intended.map((id) => accounts.doc(id)),
      );
      const recipients = profiles.map((profile) => {
        const displayName = profile.get("name");
        const name = typeof displayName === "string" ? displayName.trim() : "";
        return {
          name: name || "Unnamed entrant",
          delivered: delivered.has(profile.id),
        };
      });
      return {...sentNotificationSummary(notification), recipients};
    }, {readOnly: true}),
  );

  /**
   * Verifies the caller's account and owner role before reading history.
   * @param {Object} request Callable request.
   * @param {Object} transaction Current read transaction.
   * @return {Promise<Object>} Verified owner profile.
   */
  async function verifyHistoryOwner(request, transaction) {
    const account = await verifyAccount(request, transaction);
    const roles = account.get("userType");
    if (!Array.isArray(roles) || !roles.includes("owner")) {
      throw new HttpsError("permission-denied", "Owner access is required.");
    }
    return account;
  }

  return {
    listLotteryNotificationRecipients,
    sendLotteryNotification,
    listReceivedLotteryNotifications,
    listSentLotteryNotifications,
    getSentLotteryNotification,
  };
}

/**
 * Builds a history row from the record saved by the owner send transaction.
 * @param {Object} notification Saved notification snapshot.
 * @return {Object} Message, lottery, timestamp and delivery counts.
 */
function sentNotificationSummary(notification) {
  const record = notification.data();
  return {
    id: notification.id,
    lotteryName: record.lotteryName,
    message: record.message,
    sentAtMillis: record.sentAt.toMillis(),
    ...deliveryCounts(record),
  };
}

/**
 * Validates a Firestore document ID used by the notifications feature.
 * @param {*} value Candidate ID.
 * @return {boolean} Whether the value is a supported document ID.
 */
function validId(value) {
  return typeof value === "string" && value.trim() !== "" &&
    Buffer.byteLength(value, "utf8") <= 128 && !value.includes("/") &&
    value !== "." && value !== ".." && !/^__.*__$/.test(value);
}

/**
 * Compares an individual send's saved and requested recipients.
 * @param {Array<string>} saved Previously stored recipient IDs.
 * @param {Array<string>} selected Requested recipient IDs.
 * @return {boolean} Whether the selections match.
 */
function sameRecipients(saved, selected) {
  const requestedIds = new Set(selected);
  return saved.length === requestedIds.size &&
    saved.every((id) => requestedIds.has(id));
}

/**
 * Reads the preference, defaulting to enabled when unset.
 * @param {Object} account Recipient account snapshot.
 * @return {boolean} Whether the account accepts notifications.
 */
function notificationsEnabled(account) {
  const preference = account.get("notificationsEnabled");
  return preference === undefined || preference === null ||
    preference === true;
}

/**
 * Summarizes the result without returning other accounts' preferences.
 * @param {Object} record Saved notification data.
 * @return {Object} Intended and delivered recipient counts.
 */
function deliveryCounts(record) {
  return {
    intended: record.intendedRecipientIds.length,
    delivered: record.deliveredRecipientIds.length,
  };
}

module.exports = lotteryNotifications;
