// Verifies notification callables and client access on local Firebase.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 integration tests, 2026-11-27.
// See the Bhav Saluja entry in README.md.
const assert = require("node:assert/strict");
const {randomUUID} = require("node:crypto");
const {before, test} = require("node:test");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");

const projectId = "demo-zephyr-notifications";
const auth = "http://127.0.0.1:9099";
const functions = `http://127.0.0.1:5001/${projectId}/us-central1`;
const documents = `projects/${projectId}/databases/(default)/documents`;
const firestore = `http://127.0.0.1:8082/v1/${documents}`;
const db = getFirestore(initializeApp({projectId}));
const suffix = randomUUID();
const lotteryId = `pines-${suffix}`;
const campsiteId = `site-${suffix}`;
const emptyLotteryId = `empty-${suffix}`;
const accounts = {};

before(async () => {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8082");
  const roles = ["owner", "allowed", "optedOut", "late", "outsider", "winner"];
  for (const key of roles) {
    const response = await fetch(
        auth + "/identitytoolkit.googleapis.com/v1/" +
          "accounts:signUp?key=local-test",
        {
          method: "POST",
          headers: {"Content-Type": "application/json"},
          body: JSON.stringify({returnSecureToken: true}),
        },
    );
    assert.equal(response.ok, true);
    const user = await response.json();
    const accountId = `auth_${user.localId}`;
    accounts[key] = {...user, accountId};
    assert.equal((await patch(accounts[key], `accounts/${accountId}`, {
      id: accountId, userType: ["entrant", "owner"], notificationsEnabled: true,
    })).status, 200);
    assert.equal((await patch(accounts[key], `accounts/${accountId}`, {
      name: key,
    }, true)).status, 200);
  }
  const optedOut = accounts.optedOut;
  assert.equal((await patch(optedOut, `accounts/${optedOut.accountId}`, {
    notificationsEnabled: false,
  }, true)).status, 200);
  const owner = accounts.owner;
  assert.equal((await patch(owner, `campsite/${campsiteId}`, {
    ownerID: owner.accountId,
  })).status, 200);
  const now = Date.now();
  for (const id of [lotteryId, emptyLotteryId]) {
    assert.equal((await patch(owner, `lottery/${id}`, {
      campsiteId,
      lotteryName: "Whispering Pines",
      registrationStart: now - 60000,
      registrationEnd: now + 600000,
      waitlistCapacity: 2,
      entrantCount: 0,
    })).status, 200);
  }
  assert.equal((await registration(accounts.allowed, 1, true)).status, 200);
  assert.equal((await registration(accounts.optedOut, 2, true)).status, 200);
  await db.doc(`waitingList/${lotteryId}_${accounts.winner.accountId}`).set({
    lotteryId, accountId: accounts.winner.accountId, drawStatus: "WINNER",
  });
});

/**
 * Encodes the scalar and list fields used by the Firestore REST fixtures.
 * @param {Object} data Document fields.
 * @return {Object} Typed Firestore fields.
 */
function fields(data) {
  return Object.fromEntries(Object.entries(data).map(([key, value]) => [
    key, typeof value === "boolean" ? {booleanValue: value} :
      Number.isInteger(value) ? {integerValue: String(value)} :
        Array.isArray(value) ?
          {arrayValue: {values: value.map((v) => ({stringValue: v}))}} :
          {stringValue: value},
  ]));
}

/**
 * Writes Firestore through client rules using the chosen Firebase identity.
 * @param {Object} user Auth emulator user.
 * @param {string} path Firestore document path.
 * @param {Object} data Document fields.
 * @param {boolean} merge Whether to update just the supplied fields.
 * @return {Promise<Response>} Firestore REST response.
 */
function patch(user, path, data, merge = false) {
  const query = new URLSearchParams();
  if (merge) {
    for (const key of Object.keys(data)) {
      query.append("updateMask.fieldPaths", key);
    }
  }
  return fetch(`${firestore}/${path}?${query}`, {
    method: "PATCH",
    headers: {
      ...(user.idToken ? {"Authorization": `Bearer ${user.idToken}`} : {}),
      "Content-Type": "application/json",
    },
    body: JSON.stringify({fields: fields(data)}),
  });
}

/**
 * Pairs a real client registration or withdrawal with its count update.
 * @param {Object} user Auth emulator user.
 * @param {number} count Expected next count.
 * @param {boolean} join Whether to create the registration.
 * @param {string} id Lottery to change.
 * @param {boolean} withMarker Whether the client includes an account marker.
 * @return {Promise<Response>} Firestore REST response.
 */
function registration(user, count, join, id = lotteryId, withMarker = true) {
  const path = `${documents}/waitingList/${id}_${user.accountId}`;
  const entryWrite = join ? {
    update: {
      name: path,
      fields: fields({
        lotteryId: id,
        accountId: user.accountId,
        joinedAt: Date.now(),
        drawStatus: "WAITING",
      }),
    },
    currentDocument: {exists: false},
  } : {delete: path, currentDocument: {exists: true}};
  const counter = {entrantCount: count};
  if (withMarker) {
    counter.waitlistChangeAccountId = user.accountId;
  }
  return commit(user, [entryWrite, update(`lottery/${id}`, counter)]);
}

/**
 * Writes related client changes together through the Firestore rules.
 * @param {Object} user Auth emulator user.
 * @param {Array<Object>} writes Typed Firestore mutations.
 * @return {Promise<Response>} Firestore REST response.
 */
function commit(user, writes) {
  return fetch(`${firestore}:commit`, {
    method: "POST",
    headers: {
      ...(user.idToken ? {"Authorization": `Bearer ${user.idToken}`} : {}),
      "Content-Type": "application/json",
    },
    body: JSON.stringify({writes}),
  });
}

/**
 * Builds a targeted update to an existing record.
 * @param {string} path Firestore document path.
 * @param {Object} data Fields to update.
 * @return {Object} Typed Firestore mutation.
 */
function update(path, data) {
  return {
    update: {name: `${documents}/${path}`, fields: fields(data)},
    updateMask: {fieldPaths: Object.keys(data)},
    currentDocument: {exists: true},
  };
}

/**
 * Calls an endpoint with the chosen Auth emulator identity.
 * @param {string} endpoint Callable function name.
 * @param {Object|null} user Auth emulator user, if signed in.
 * @param {Object} data Callable parameters.
 * @return {Promise<Object>} Callable response.
 */
async function call(endpoint, user, data) {
  const headers = {"Content-Type": "application/json"};
  if (user) {
    headers.Authorization = `Bearer ${user.idToken}`;
  }
  const response = await fetch(`${functions}/${endpoint}`, {
    method: "POST",
    headers,
    body: JSON.stringify({data: {accountId: user && user.accountId, ...data}}),
  });
  return response.json();
}

/**
 * Sends through the same callable as the app.
 * @param {Object|null} user Auth emulator user, if signed in.
 * @param {Object} options Overrides for the send parameters.
 * @return {Promise<Object>} Callable response.
 */
function send(user, options = {}) {
  return call("sendLotteryNotification", user, {
    requestId: randomUUID(),
    lotteryId,
    allEntrants: true,
    recipientIds: [],
    message: "The south access road is closed.",
    ...options,
  });
}

/**
 * Reads the same recipient-only view used by the app inbox.
 * @param {Object} user Auth emulator user.
 * @return {Promise<Object>} Callable response.
 */
function inbox(user) {
  return call("listReceivedLotteryNotifications", user, {});
}

test("requires a verified owner and denies direct client writes", async () => {
  const owner = accounts.owner;
  const outsider = accounts.outsider;
  assert.equal((await send(null)).error.status, "UNAUTHENTICATED");
  const spoof = await send(outsider, {accountId: owner.accountId});
  assert.equal(spoof.error.status, "PERMISSION_DENIED");
  assert.equal(spoof.error.details.linkRequired, true);
  assert.equal((await send(outsider)).error.status, "PERMISSION_DENIED");
  const campsite = db.doc(`campsite/${campsiteId}`);
  await campsite.update({ownerID: outsider.accountId});
  assert.equal((await send(owner)).error.status, "PERMISSION_DENIED");
  await campsite.update({ownerID: owner.accountId});

  for (const path of [
    `notificationAccountLinks/${owner.accountId}`,
    `notificationLotteryOwners/${lotteryId}`,
    "lotteryNotifications/client-created",
  ]) {
    const response = await fetch(`${firestore}/${path}`, {
      method: "PATCH",
      headers: {
        "Authorization": `Bearer ${outsider.idToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({fields: {uid: {stringValue: outsider.localId}}}),
    });
    assert.equal(response.status, 403);
  }
});

test("keeps legacy writes and protects managed records", async () => {
  const {owner, allowed, outsider} = accounts;
  assert.equal((await patch(outsider, `accounts/${allowed.accountId}`, {
    notificationsEnabled: false,
  }, true)).status, 403);
  assert.equal((await patch(allowed, `accounts/${allowed.accountId}`, {
    notificationsEnabled: "false",
  }, true)).status, 403);

  const legacyId = `legacy-${suffix}`;
  const legacy = db.doc(`accounts/${legacyId}`);
  await legacy.set({id: legacyId, name: "Legacy Camper", userType: "entrant"});
  const unlinked = {accountId: legacyId};
  assert.equal((await patch(unlinked, legacy.path, {
    notificationsEnabled: false,
  }, true)).status, 200);
  assert.equal((await legacy.get()).get("notificationsEnabled"), false);
  assert.equal((await patch(unlinked, legacy.path, {
    notificationsEnabled: "false",
  }, true)).status, 403);
  const legacyCampsite = `legacy-camp-${suffix}`;
  const legacyLottery = `legacy-lottery-${suffix}`;
  assert.equal((await patch(unlinked, `campsite/${legacyCampsite}`, {
    ownerID: legacyId,
  })).status, 200);
  assert.equal((await patch(unlinked, `lottery/${legacyLottery}`, {
    campsiteId: legacyCampsite,
    registrationStart: Date.now() - 60000,
    registrationEnd: Date.now() + 600000,
    entrantCount: 0,
  })).status, 200);
  // Installed clients omit the account marker.
  const joined = await registration(unlinked, 1, true, legacyLottery, false);
  assert.equal(joined.status, 200);
  assert.equal((await db.doc(`lottery/${legacyLottery}`).get())
      .get("entrantCount"), 1);
  const left = await registration(unlinked, 0, false, legacyLottery, false);
  assert.equal(left.status, 200);
  const managedJoin = await registration(unlinked, 1, true, emptyLotteryId);
  assert.equal(managedJoin.status, 403);
  const unlinkedSend = await send(
      {...outsider, accountId: legacyId}, {lotteryId: legacyLottery},
  );
  assert.equal(unlinkedSend.error.status, "PERMISSION_DENIED");

  assert.equal((await registration(outsider, 3, true)).status, 403);
  assert.equal((await patch(owner, `lottery/${lotteryId}`, {
    entrantCount: 12,
  }, true)).status, 403);
  assert.equal((await patch(outsider,
      `waitingList/${emptyLotteryId}_${outsider.accountId}`, {
        accountId: outsider.accountId, lotteryId: emptyLotteryId,
        joinedAt: Date.now(),
      })).status, 403);
  assert.equal((await patch(outsider,
      `waitingList/${lotteryId}_${allowed.accountId}`, {
        drawStatus: "BACKUP",
      }, true)).status, 403);

  await db.doc(`notificationAccountLinks/${legacyId}`)
      .set({uid: outsider.localId});
  const linked = {...outsider, accountId: legacyId};
  assert.equal((await patch(linked, legacy.path, {
    notificationsEnabled: false,
  }, true)).status, 200);
  const legacyJoin = await registration(linked, 1, true, emptyLotteryId);
  const legacyLeave = await registration(linked, 0, false, emptyLotteryId);
  assert.equal(legacyJoin.status, 200);
  assert.equal(legacyLeave.status, 200);
  assert.equal((await legacy.get()).get("name"), "Legacy Camper");
});

test("an unlinked profile can complete deletion and cleanup", async () => {
  const accountId = `legacy-delete-${suffix}`;
  const user = {accountId};
  const profilePath = `accounts/${accountId}`;
  const site = `legacy-delete-site-${suffix}`;
  const id = `legacy-delete-lottery-${suffix}`;
  const waitingPath = `waitingList/${id}_${accountId}`;
  const reservationPath = `reservation/legacy-delete-${suffix}`;
  const bookingStart = Timestamp.fromMillis(Date.now() + 86400000);
  const bookingEnd = Timestamp.fromMillis(Date.now() + 172800000);
  const batch = db.batch();
  batch.create(db.doc(profilePath), {
    id: accountId, userType: "entrant", notificationsEnabled: false,
  });
  batch.create(db.doc(`campsite/${site}`), {
    ownerID: `legacy-owner-${suffix}`,
  });
  batch.create(db.doc(`lottery/${id}`), {
    campsiteId: site, entrantCount: 1, drawStatus: "COMPLETED",
  });
  batch.create(db.doc(waitingPath), {
    lotteryId: id, accountId, drawStatus: "WINNER", backupPosition: 0,
  });
  batch.create(db.doc(reservationPath), {
    accountId, campsiteId: site, bookedPeriodStart: bookingStart,
    bookedPeriodEnd: bookingEnd, cancelled: false,
  });
  await batch.commit();

  const managedPath = `accounts/${accounts.allowed.accountId}`;
  assert.equal((await patch(user, managedPath, {deletionPending: true},
      true)).status, 403);
  assert.equal((await commit(user, [
    {delete: `${documents}/${managedPath}`},
  ])).status, 403);
  const removeProfile = {delete: `${documents}/${profilePath}`};
  assert.equal((await commit(user, [removeProfile])).status, 403);
  const pending = await patch(user, profilePath, {deletionPending: true}, true);
  assert.equal(pending.status, 200, await pending.text());

  const detachBooking = update(reservationPath, {cancelled: true});
  detachBooking.updateMask.fieldPaths.push("accountId");
  const deleted = await commit(user, [
    update(`lottery/${id}`, {
      entrantCount: 0, waitlistChangeAccountId: accountId,
    }),
    {delete: `${documents}/${waitingPath}`},
    detachBooking,
    removeProfile,
  ]);
  assert.equal(deleted.status, 200, await deleted.text());
  assert.equal((await db.doc(profilePath).get()).exists, false);
  assert.equal((await db.doc(waitingPath).get()).exists, false);
  assert.equal((await db.doc(`lottery/${id}`).get()).get("entrantCount"), 0);
  const booking = await db.doc(reservationPath).get();
  assert.equal(booking.get("cancelled"), true);
  assert.equal(booking.get("accountId"), undefined);
  assert.deepEqual(booking.get("bookedPeriodStart"), bookingStart);
  assert.deepEqual(booking.get("bookedPeriodEnd"), bookingEnd);
});

test("validates sends and delivers only to the chosen entrant", async () => {
  const owner = accounts.owner;
  const allowed = accounts.allowed;
  const recipients = await call(
      "listLotteryNotificationRecipients", owner, {lotteryId},
  );
  assert.deepEqual(
      new Set(recipients.result.map((recipient) => recipient.accountId)),
      new Set([allowed.accountId, accounts.optedOut.accountId]),
  );
  for (const invalid of [{message: "  \n"}, {allEntrants: false}]) {
    assert.equal((await send(owner, invalid)).error.status, "INVALID_ARGUMENT");
  }
  const empty = await send(owner, {lotteryId: emptyLotteryId});
  assert.equal(empty.error.status, "FAILED_PRECONDITION");
  assert.equal((await send(owner, {
    allEntrants: false, recipientIds: [accounts.outsider.accountId],
  })).error.status, "FAILED_PRECONDITION");

  const request = {
    allEntrants: false,
    recipientIds: [allowed.accountId],
    requestId: randomUUID(),
  };
  const result = {result: {intended: 1, delivered: 1}};
  assert.deepEqual(await send(owner, request), result);
  assert.deepEqual(await send(owner, request), result);
  const {result: messages} = await inbox(allowed);
  assert.equal(messages.length, 1);
  assert.equal(messages[0].message, "The south access road is closed.");
  assert.equal(messages[0].lotteryName, "Whispering Pines");
  assert.ok(messages[0].sentAtMillis > 0);
  assert.deepEqual((await inbox(accounts.optedOut)).result, []);
  assert.deepEqual((await inbox(accounts.outsider)).result, []);
  const spoof = await call(
      "listReceivedLotteryNotifications", accounts.outsider,
      {accountId: allowed.accountId},
  );
  assert.equal(spoof.error.status, "PERMISSION_DENIED");
  const path = `${firestore}/lotteryNotifications/${messages[0].id}`;
  const response = await fetch(path, {
    headers: {Authorization: `Bearer ${allowed.idToken}`},
  });
  assert.equal(response.status, 403);
});

for (const legacy of [false, true]) {
  const actor = legacy ? "unlinked winner" : "verified winner";
  test(`${actor} can promote a backup and preserve the queue`, async () => {
    const id = `finished-${legacy ? "legacy" : "managed"}-${suffix}`;
    const winner = legacy ? {accountId: `unlinked-winner-${suffix}`} :
      accounts.winner;
    const site = legacy ? `unlinked-site-${suffix}` : campsiteId;
    const backupIds = [accounts.late, accounts.optedOut, accounts.outsider]
        .map((backup) => backup.accountId);
    const [promotedId, ...remaining] = backupIds;
    const winnerPath = `waitingList/${id}_${winner.accountId}`;
    const backupPath = (accountId) => `waitingList/${id}_${accountId}`;
    const resultPath = `lotteryDrawResults/${id}`;
    const batch = db.batch();
    if (legacy) {
      batch.create(db.doc(`accounts/${winner.accountId}`), {
        name: "Old Camper",
      });
      batch.create(db.doc(`campsite/${site}`), {
        ownerID: `legacy-owner-${suffix}`,
      });
    }
    batch.create(db.doc(`lottery/${id}`), {
      campsiteId: site, drawCompleted: true, drawStatus: "COMPLETED",
      entrantCount: 4,
    });
    batch.create(db.doc(winnerPath), {
      lotteryId: id, accountId: winner.accountId,
      drawStatus: "WINNER", backupPosition: 0,
    });
    backupIds.forEach((accountId, index) => {
      batch.create(db.doc(backupPath(accountId)), {
        lotteryId: id, accountId, drawStatus: "BACKUP",
        backupPosition: index + 1,
      });
    });
    batch.create(db.doc(resultPath), {
      winnerIds: [winner.accountId], backupIds,
      declinedIds: [], winnerCount: 1, backupCount: 3,
    });
    await batch.commit();
    const decline = update(winnerPath, {
      drawStatus: "DECLINED", backupPosition: 0,
    });
    const promotion = update(backupPath(promotedId), {
      drawStatus: "WINNER", backupPosition: 0,
    });
    const resultsForQueue = (queue) => update(resultPath, {
      lastDeclinedAccountId: winner.accountId,
      winnerIds: [promotedId], backupIds: queue,
      declinedIds: [winner.accountId],
      winnerCount: 1, backupCount: queue.length,
    });
    for (const queue of [remaining.slice(0, 1), [...remaining].reverse()]) {
      const result = await commit(winner, [
        decline, promotion, resultsForQueue(queue),
      ]);
      assert.equal(result.status, 403);
    }
    assert.equal((await commit(winner, [
      decline, resultsForQueue(remaining),
    ])).status, 403);
    const original = await db.doc(resultPath).get();
    assert.deepEqual(original.get("backupIds"), backupIds);
    assert.equal((await commit(winner, [promotion])).status, 403);
    const writes = [
      decline, promotion, resultsForQueue(remaining),
      ...remaining.map((accountId, index) => update(backupPath(accountId), {
        backupPosition: index + 1,
      })),
    ];
    if (legacy) {
      const owner = db.doc(`campsite/${site}`);
      await owner.update({ownerID: accounts.owner.accountId});
      assert.equal((await commit(winner, writes)).status, 403);
      await owner.update({ownerID: `legacy-owner-${suffix}`});
    }
    const result = await commit(winner, writes);
    assert.equal(result.status, 200, await result.text());
    const saved = await db.doc(resultPath).get();
    assert.deepEqual(saved.get("backupIds"), remaining);
    assert.equal(saved.get("backupCount"), 2);
    for (const [index, accountId] of remaining.entries()) {
      const registration = await db.doc(backupPath(accountId)).get();
      assert.equal(registration.get("drawStatus"), "BACKUP");
      assert.equal(registration.get("backupPosition"), index + 1);
    }
  });
}

test("everyone uses current membership and honors opt-outs", async () => {
  const owner = accounts.owner;
  const allowed = accounts.allowed;
  const optedOut = accounts.optedOut;
  const late = accounts.late;
  const previousInbox = await inbox(allowed);
  assert.equal((await registration(allowed, 1, false)).status, 200);
  assert.equal((await registration(late, 2, true)).status, 200);
  await db.doc(`waitingList/${lotteryId}_${late.accountId}`)
      .update({drawStatus: "BACKUP"});
  assert.equal((await send(owner, {
    allEntrants: false, recipientIds: [allowed.accountId],
  })).error.status, "FAILED_PRECONDITION");
  const message = "Use the north gate this weekend.";
  assert.deepEqual(await send(owner, {message}), {
    result: {intended: 2, delivered: 1},
  });
  assert.equal((await inbox(late)).result[0].message, message);
  assert.deepEqual(await inbox(allowed), previousInbox);
  assert.deepEqual((await inbox(optedOut)).result, []);

  const saved = await db.collection("lotteryNotifications")
      .where("lotteryId", "==", lotteryId).get();
  const records = saved.docs.map((document) => document.data())
      .filter((data) => data.message === message);
  assert.equal(records.length, 1);
  const [record] = records;
  assert.ok(record.sentAt instanceof Timestamp);
  assert.equal(record.senderAccountId, owner.accountId);
  assert.deepEqual(new Set(record.intendedRecipientIds),
      new Set([late.accountId, optedOut.accountId]));
  assert.deepEqual(record.deliveredRecipientIds, [late.accountId]);
  assert.equal((await patch(optedOut, `accounts/${optedOut.accountId}`, {
    notificationsEnabled: true,
  }, true)).status, 200);
  assert.deepEqual((await inbox(optedOut)).result, []);
  assert.equal((await patch(late, `accounts/${late.accountId}`, {
    notificationsEnabled: false,
  }, true)).status, 200);
  assert.deepEqual((await inbox(late)).result, []);
});
