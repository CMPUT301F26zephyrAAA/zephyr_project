// AI Citation [AI-43]
// Anthropic, Claude Code assisted with generating emulator tests for
// US 02.04.05 winner count checks in saveLotterySettings and the rules.
// AI usage and citation documented in the team AI Documentation.
// Verifies the US 02.04.05 winner count rules against saveLotterySettings
// and the lottery Firestore rules, on local emulators only.
const assert = require("node:assert/strict");
const {randomUUID} = require("node:crypto");
const {before, test} = require("node:test");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");

const projectId = "demo-zephyr-winners";
const auth = "http://127.0.0.1:9099";
const functions = `http://127.0.0.1:5001/${projectId}/us-central1`;
const documents = `projects/${projectId}/databases/(default)/documents`;
const firestore = `http://127.0.0.1:8082/v1/${documents}`;
const app = initializeApp({projectId});
const db = getFirestore(app);
const hour = 3600000;
const day = 24 * hour;
let owner;

before(async () => {
  // Never run against a real project.
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8082");
  const response = await fetch(
      auth + "/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local",
      {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({returnSecureToken: true}),
      },
  );
  assert.equal(response.ok, true);
  const user = await response.json();
  owner = {...user, accountId: `auth_${user.localId}`};
  await db.doc(`accounts/${owner.accountId}`).set({
    id: owner.accountId,
    userType: ["entrant", "owner"],
  });
});

/**
 * Creates a campsite owned by the test owner.
 * @return {Promise<string>} Campsite ID.
 */
async function campsite() {
  const id = `site-${randomUUID()}`;
  await db.doc(`campsite/${id}`).set({id, ownerID: owner.accountId});
  return id;
}

/**
 * Builds lottery settings with registration tomorrow and booking on day 3.
 * @param {Object} overrides Fields to replace.
 * @return {Object} Callable lottery settings.
 */
function settings(overrides = {}) {
  const now = Date.now();
  return {
    lotteryName: "Test lottery",
    registrationStart: now + day,
    registrationEnd: now + 2 * day,
    bookingStart: now + 3 * day,
    bookingEnd: now + 4 * day,
    numberOfWinners: 3,
    waitlistCapacity: null,
    ...overrides,
  };
}

/**
 * Calls saveLotterySettings as the owner.
 * @param {Object} data Callable parameters.
 * @return {Promise<Object>} Callable response body.
 */
async function save(data) {
  const response = await fetch(`${functions}/saveLotterySettings`, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${owner.idToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({data}),
  });
  return response.json();
}

/**
 * Creates a lottery through saveLotterySettings, as the app does.
 * @param {Object} values Lottery settings.
 * @return {Promise<Object>} Callable response body.
 */
async function create(values) {
  return save({
    create: true,
    lotteryId: `lottery-${randomUUID()}`,
    campsiteIds: [await campsite()],
    ...values,
  });
}

/**
 * Saves a lottery directly with admin access, for fixtures.
 * @param {Object} values Lottery settings.
 * @return {Promise<string>} Lottery ID.
 */
async function seed(values) {
  const id = `seed-${randomUUID()}`;
  const site = await campsite();
  await db.doc(`lottery/${id}`).set({
    id,
    campsiteId: site,
    campsiteIds: [site],
    entrantCount: 0,
    drawCompleted: false,
    drawStatus: "PENDING",
    ...values,
  });
  return id;
}

/**
 * Updates lottery fields directly through the Firestore rules.
 * @param {string} lotteryId Lottery to update.
 * @param {Object} data Fields to update.
 * @return {Promise<Response>} Firestore REST response.
 */
function patch(lotteryId, data) {
  const query = new URLSearchParams();
  for (const key of Object.keys(data)) {
    query.append("updateMask.fieldPaths", key);
  }
  query.append("currentDocument.exists", "true");
  const fields = Object.fromEntries(
      Object.entries(data).map(([key, value]) => [
        key,
        Number.isInteger(value) ?
          {integerValue: String(value)} :
          {stringValue: value},
      ]),
  );
  return fetch(`${firestore}/lottery/${lotteryId}?${query}`, {
    method: "PATCH",
    headers: {
      "Authorization": `Bearer ${owner.idToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({fields}),
  });
}

test("saves the winner count set when creating a lottery", async () => {
  const response = await create(settings({numberOfWinners: 3}));
  assert.ok(response.result, JSON.stringify(response));
  const saved = await db.doc(`lottery/${response.result.lotteryId}`).get();
  assert.equal(saved.get("numberOfWinners"), 3);
});

test("rejects winner counts that are not positive whole numbers", async () => {
  for (const value of [0, -1, 1.5, "2", null]) {
    const response = await create(settings({numberOfWinners: value}));
    assert.equal(response.error?.status, "INVALID_ARGUMENT",
        `${JSON.stringify(value)} was accepted`);
  }
  const values = settings();
  delete values.numberOfWinners;
  assert.equal((await create(values)).error?.status, "INVALID_ARGUMENT");
});

test("changes the winner count before registration starts", async () => {
  const values = settings({numberOfWinners: 2});
  const lotteryId = await seed(values);
  const response = await save({lotteryId, ...values, numberOfWinners: 5});
  assert.ok(response.result, JSON.stringify(response));
  const saved = await db.doc(`lottery/${lotteryId}`).get();
  assert.equal(saved.get("numberOfWinners"), 5);
});

test("locks the winner count by server time after registration starts",
    async () => {
      // Registration started an hour ago on the server.
      const values = settings({
        registrationStart: Date.now() - hour,
        numberOfWinners: 2,
      });
      const lotteryId = await seed(values);
      const changed = await save({lotteryId, ...values, numberOfWinners: 5});
      assert.equal(changed.error?.status, "FAILED_PRECONDITION");

      const renamed = await save({
        lotteryId,
        ...values,
        lotteryName: "Renamed",
      });
      assert.ok(renamed.result, JSON.stringify(renamed));
      const saved = await db.doc(`lottery/${lotteryId}`).get();
      assert.equal(saved.get("numberOfWinners"), 2);
      assert.equal(saved.get("lotteryName"), "Renamed");
    });

test("rules deny direct winner count writes", async () => {
  const before = await seed(settings({numberOfWinners: 2}));
  assert.equal((await patch(before, {numberOfWinners: 9})).status, 403);

  const started = await seed(settings({
    registrationStart: Date.now() - hour,
    numberOfWinners: 2,
  }));
  assert.equal((await patch(started, {numberOfWinners: 9})).status, 403);
  assert.equal(
      (await db.doc(`lottery/${started}`).get()).get("numberOfWinners"), 2);

  assert.equal((await patch(before, {lotteryName: "Direct"})).status, 200);
});
