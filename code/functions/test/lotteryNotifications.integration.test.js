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
const firestore = `http://127.0.0.1:8082/v1/projects/${projectId}/databases/(default)/documents`;
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
    const accountId = `${key}-${suffix}`;
    accounts[key] = {...user, accountId};
    const profile = {name: key};
    if (key !== "allowed") {
      profile.notificationsEnabled = key !== "optedOut";
    }
    await db.doc(`accounts/${accountId}`).set(profile);
    await db.doc(`notificationAccountLinks/${accountId}`)
        .set({uid: user.localId});
  }
  const ownerAccountId = accounts.owner.accountId;
  await db.doc(`campsite/${campsiteId}`).set({ownerID: ownerAccountId});
  for (const id of [lotteryId, emptyLotteryId]) {
    await db.doc(`lottery/${id}`)
        .set({campsiteId, lotteryName: "Whispering Pines"});
    await db.doc(`notificationLotteryOwners/${id}`)
        .set({campsiteId, ownerAccountId});
  }
  await Promise.all([
    register("allowed"),
    register("optedOut", "WAITING"),
    register("winner", "WINNER"),
  ]);
});

/**
 * Seeds a registration shaped like the Android waiting-list writer.
 * @param {string} account Fixture account name.
 * @param {string=} drawStatus Optional waitlist status.
 * @return {Promise<Object>} Firestore write result.
 */
function register(account, drawStatus) {
  const accountId = accounts[account].accountId;
  const registration = {lotteryId, accountId, joinedAt: Date.now()};
  if (drawStatus !== undefined) {
    registration.drawStatus = drawStatus;
  }
  return db.doc(`waitingList/${lotteryId}_${accountId}`).set(registration);
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
  const grant = db.doc(`notificationLotteryOwners/${lotteryId}`);
  await grant.delete();
  assert.equal((await send(owner)).error.status, "PERMISSION_DENIED");
  await grant.set({campsiteId, ownerAccountId: owner.accountId});
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

test("everyone uses current membership and honors opt-outs", async () => {
  const owner = accounts.owner;
  const allowed = accounts.allowed;
  const optedOut = accounts.optedOut;
  const late = accounts.late;
  const previousInbox = await inbox(allowed);
  await register("late", "BACKUP");
  await db.doc(`waitingList/${lotteryId}_${allowed.accountId}`).delete();
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
  await db.doc(`accounts/${optedOut.accountId}`)
      .update({notificationsEnabled: true});
  assert.deepEqual((await inbox(optedOut)).result, []);
  await db.doc(`accounts/${late.accountId}`)
      .update({notificationsEnabled: false});
  assert.deepEqual((await inbox(late)).result, []);
});
