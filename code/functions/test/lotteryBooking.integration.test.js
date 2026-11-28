// AI Citation [AI-41], [AI-42]
// Anthropic, Claude Code assisted with generating emulator tests for
// US 02.04.04 booking checks in saveLotterySettings and the lottery rules.
// AI usage and citation documented in the team AI Documentation.
// Verifies US 02.04.04 against the saveLotterySettings and
// confirmLotteryBooking callables and the lottery Firestore rules,
// on local emulators only.
const assert = require("node:assert/strict");
const {randomUUID} = require("node:crypto");
const {before, test} = require("node:test");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");

const projectId = "demo-zephyr-booking";
const auth = "http://127.0.0.1:9099";
const functions = `http://127.0.0.1:5001/${projectId}/us-central1`;
const documents = `projects/${projectId}/databases/(default)/documents`;
const firestore = `http://127.0.0.1:8082/v1/${documents}`;
const app = initializeApp({projectId});
const db = getFirestore(app);
const hour = 3600000;
const day = 24 * hour;
const users = {};

before(async () => {
  // Never run against a real project.
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8082");
  for (const key of ["owner", "outsider", "winner"]) {
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
    users[key] = {...user, accountId: `auth_${user.localId}`};
    await db.doc(`accounts/${users[key].accountId}`).set({
      id: users[key].accountId,
      userType: ["entrant", "owner"],
    });
  }
});

/**
 * Creates a campsite owned by the test owner.
 * @return {Promise<string>} Campsite ID.
 */
async function campsite() {
  const id = `site-${randomUUID()}`;
  await db.doc(`campsite/${id}`).set({id, ownerID: users.owner.accountId});
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
    bookingEnd: now + 4 * day - 60000,
    numberOfWinners: 1,
    waitlistCapacity: null,
    ...overrides,
  };
}

/**
 * Calls a callable function as the given user.
 * @param {string} endpoint Callable function name.
 * @param {Object|null} user Auth emulator user, if signed in.
 * @param {Object} data Callable parameters.
 * @return {Promise<Object>} Callable response body.
 */
async function call(endpoint, user, data) {
  const headers = {"Content-Type": "application/json"};
  if (user) {
    headers.Authorization = `Bearer ${user.idToken}`;
  }
  const response = await fetch(`${functions}/${endpoint}`, {
    method: "POST",
    headers,
    body: JSON.stringify({data}),
  });
  return response.json();
}

/**
 * Creates a lottery through saveLotterySettings, as the app does.
 * @param {string[]} siteIds Participating campsites.
 * @param {Object} overrides Settings to replace.
 * @param {Object|null} user Caller, the owner by default.
 * @return {Promise<Object>} Callable response body.
 */
function create(siteIds, overrides = {}, user = users.owner) {
  return call("saveLotterySettings", user, {
    create: true,
    lotteryId: `lottery-${randomUUID()}`,
    campsiteIds: siteIds,
    ...settings(overrides),
  });
}

/**
 * Edits a lottery through saveLotterySettings, as the app does.
 * @param {string} lotteryId Lottery to edit.
 * @param {Object} values Full lottery settings.
 * @return {Promise<Object>} Callable response body.
 */
function update(lotteryId, values) {
  return call("saveLotterySettings", users.owner, {lotteryId, ...values});
}

/**
 * Saves a lottery directly with admin access, for fixtures.
 * @param {Object} fields Lottery fields.
 * @return {Promise<string>} Lottery ID.
 */
async function seed(fields) {
  const id = `seed-${randomUUID()}`;
  await db.doc(`lottery/${id}`).set({
    id,
    entrantCount: 0,
    drawCompleted: false,
    drawStatus: "PENDING",
    ...settings(),
    ...fields,
  });
  return id;
}

/**
 * Updates lottery fields directly through the Firestore rules.
 * @param {Object} user Auth emulator user.
 * @param {string} path Firestore document path.
 * @param {Object} data Fields to update.
 * @return {Promise<Response>} Firestore REST response.
 */
function patch(user, path, data) {
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
  return fetch(`${firestore}/${path}?${query}`, {
    method: "PATCH",
    headers: {
      "Authorization": `Bearer ${user.idToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({fields}),
  });
}

/**
 * Wraps saveLotterySettings in this process, so two calls really overlap.
 * The emulator runs HTTP calls one at a time (maxInstances is 1).
 * @return {Object} Wrapped function and its cleanup.
 */
function inProcessSave() {
  const fft = require("firebase-functions-test")({projectId});
  const handlers = require("../sharedLotteries")(db);
  const wrapped = fft.wrap(handlers.saveLotterySettings);
  const save = (data) => wrapped({data, auth: {uid: users.owner.localId}});
  return {save, cleanup: () => fft.cleanup()};
}

test("saves a single-day booking period", async () => {
  const site = await campsite();
  const values = settings();
  const response = await create([site], values);
  assert.ok(response.result, JSON.stringify(response));
  const saved = await db.doc(`lottery/${response.result.lotteryId}`).get();
  assert.equal(saved.get("bookingStart"), values.bookingStart);
  assert.equal(saved.get("bookingEnd"), values.bookingEnd);
});

test("rejects invalid booking periods", async () => {
  const site = await campsite();
  const values = settings();
  const empty = await create([site], {bookingEnd: values.bookingStart});
  assert.equal(empty.error.status, "INVALID_ARGUMENT");
  const atRegistrationEnd = await create([site], {
    bookingStart: values.registrationEnd,
  });
  assert.equal(atRegistrationEnd.error.status, "INVALID_ARGUMENT");
});

test("rejects overlapping and touching periods on a campsite", async () => {
  const site = await campsite();
  const first = settings();
  assert.ok((await create([site], first)).result);
  const overlap = await create([site], {
    bookingStart: first.bookingStart + hour,
    bookingEnd: first.bookingEnd + day,
  });
  assert.ok(overlap.error, "overlapping period accepted");
  const touching = await create([site], {
    bookingStart: first.bookingEnd,
    bookingEnd: first.bookingEnd + day,
  });
  assert.ok(touching.error, "touching period accepted");
  assert.ok((await create([await campsite()], first)).result);
});

test("a secondary site in a shared lottery blocks an overlap", async () => {
  const first = await campsite();
  const second = await campsite();
  const values = settings();
  assert.ok((await create([first, second], values)).result);
  const onSecondary = await create([second], values);
  assert.ok(onSecondary.error, "overlap on the secondary site accepted");
});

test("an older lottery with only campsiteId blocks an overlap", async () => {
  const site = await campsite();
  const values = settings();
  await seed({campsiteId: site, ...values});
  assert.ok((await create([site], values)).error);
});

test("allows only one of two overlapping creates sent at once", async () => {
  const {save, cleanup} = inProcessSave();
  const site = await campsite();
  const creating = (overrides) => save({
    create: true,
    lotteryId: `lottery-${randomUUID()}`,
    campsiteIds: [site],
    ...settings(overrides),
  });
  const results = await Promise.allSettled([
    creating({}),
    creating({bookingStart: settings().bookingStart + hour}),
  ]);
  cleanup();
  assert.equal(
      results.filter((result) => result.status === "fulfilled").length,
      1,
      JSON.stringify(results.map((result) => result.status)),
  );
});

test("allows only one of a create and an edit into one period", async () => {
  const {save, cleanup} = inProcessSave();
  const site = await campsite();
  const values = settings();
  const existing = await seed({
    campsiteId: site,
    campsiteIds: [site],
    ...values,
    bookingStart: values.bookingStart + 10 * day,
    bookingEnd: values.bookingEnd + 10 * day,
  });
  const results = await Promise.allSettled([
    save({
      create: true,
      lotteryId: `lottery-${randomUUID()}`,
      campsiteIds: [site],
      ...values,
    }),
    save({lotteryId: existing, ...values}),
  ]);
  cleanup();
  assert.equal(
      results.filter((result) => result.status === "fulfilled").length,
      1,
      JSON.stringify(results.map((result) => result.status)),
  );
});

test("requires a signed-in owner of every campsite", async () => {
  const site = await campsite();
  assert.equal((await create([site], {}, null)).error.status,
      "UNAUTHENTICATED");
  assert.equal((await create([site], {}, users.outsider)).error.status,
      "PERMISSION_DENIED");
});

test("locks dates by server time after registration starts", async () => {
  const site = await campsite();
  const started = settings({registrationStart: Date.now() - hour});
  const lotteryId = await seed({
    campsiteId: site,
    campsiteIds: [site],
    ...started,
  });
  const moved = await update(lotteryId, {
    ...started,
    bookingStart: started.bookingStart + day,
    bookingEnd: started.bookingEnd + day,
  });
  assert.equal(moved.error.status, "FAILED_PRECONDITION");
  const renamed = await update(lotteryId, {...started, lotteryName: "Renamed"});
  assert.ok(renamed.result, JSON.stringify(renamed));
});

test("keeps winner reservations inside the booking period", async () => {
  const site = await campsite();
  const values = settings({
    registrationStart: Date.now() - 2 * hour,
    registrationEnd: Date.now() - hour,
  });
  const lotteryId = await seed({
    campsiteId: site,
    campsiteIds: [site],
    ...values,
    drawCompleted: true,
    drawStatus: "COMPLETED",
  });
  const winner = users.winner;
  await db.doc(`waitingList/${lotteryId}_${winner.accountId}`).set({
    lotteryId,
    accountId: winner.accountId,
    drawStatus: "WINNER",
  });
  await db.doc(`lotteryDrawResults/${lotteryId}`).set({
    lotteryId,
    winnerIds: [winner.accountId],
    declinedIds: [],
  });
  const reserve = (start, end) => call("confirmLotteryBooking", winner, {
    lotteryId,
    campsiteId: site,
    start,
    end,
  });
  const before = await reserve(values.bookingStart - day,
      values.bookingStart + hour);
  assert.ok(before.error, "reservation starting before the period accepted");
  const after = await reserve(values.bookingEnd - hour,
      values.bookingEnd + day);
  assert.ok(after.error, "reservation ending after the period accepted");
  const inside = await reserve(values.bookingStart, values.bookingEnd);
  assert.ok(inside.result, JSON.stringify(inside));
});

test("rules deny direct lottery creates and date changes", async () => {
  const site = await campsite();
  const owner = users.owner;
  const created = await fetch(`${firestore}/lottery/direct-${randomUUID()}`, {
    method: "PATCH",
    headers: {
      "Authorization": `Bearer ${owner.idToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({fields: {campsiteId: {stringValue: site}}}),
  });
  assert.equal(created.status, 403);

  const values = settings({registrationStart: Date.now() - hour});
  const lotteryId = await seed({
    campsiteId: site,
    campsiteIds: [site],
    ...values,
  });
  const path = `lottery/${lotteryId}`;
  assert.equal((await patch(owner, path, {
    bookingStart: values.bookingStart + day,
  })).status, 403);
  assert.equal((await patch(owner, path, {
    registrationEnd: values.bookingEnd + day,
  })).status, 403);
  assert.equal((await patch(owner, path, {
    lotteryName: "Renamed directly",
  })).status, 200);
});
