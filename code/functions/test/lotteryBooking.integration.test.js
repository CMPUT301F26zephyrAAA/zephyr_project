// AI Citation [AI-41]
// Anthropic, Claude Code assisted with generating emulator tests for
// the lottery save functions and the lottery Firestore rules.
// AI usage and citation documented in the team AI Documentation.
// Verifies lottery booking checks in the createLottery and updateLottery
// callables and the lottery Firestore rules, on local emulators only.
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
const suffix = randomUUID();
const hour = 3600000;
const day = 24 * hour;
const users = {};

before(async () => {
  // Never run against a real project.
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8082");
  for (const key of ["owner", "outsider"]) {
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
 * Creates a lottery through the callable as the owner.
 * @param {string} campsiteId Campsite in the lottery.
 * @param {Object} overrides Settings to replace.
 * @param {Object} user Caller, the owner by default.
 * @return {Promise<Object>} Callable response body.
 */
function create(campsiteId, overrides = {}, user = users.owner) {
  return call("createLottery", user, {
    lotteryId: `lottery-${randomUUID()}`,
    campsiteId,
    ...settings(overrides),
  });
}

/**
 * Saves a lottery directly with admin access, for fixtures.
 * @param {string} campsiteId Campsite in the lottery.
 * @param {Object} overrides Fields to replace.
 * @return {Promise<string>} Lottery ID.
 */
async function seed(campsiteId, overrides = {}) {
  const id = `seed-${randomUUID()}`;
  await db.doc(`lottery/${id}`).set({
    id,
    campsiteId,
    ...settings(),
    entrantCount: 0,
    drawCompleted: false,
    drawStatus: "PENDING",
    ...overrides,
  });
  return id;
}

/**
 * Writes a lottery document directly through the Firestore rules.
 * @param {Object} user Auth emulator user.
 * @param {string} path Firestore document path.
 * @param {Object} data Fields to write.
 * @param {boolean} merge Whether to update only the given fields.
 * @return {Promise<Response>} Firestore REST response.
 */
function patch(user, path, data, merge) {
  const query = new URLSearchParams();
  if (merge) {
    for (const key of Object.keys(data)) {
      query.append("updateMask.fieldPaths", key);
    }
    query.append("currentDocument.exists", "true");
  }
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

test("saves a single-day booking period", async () => {
  const siteId = await campsite();
  const values = settings();
  const response = await create(siteId, values);
  assert.ok(response.result, JSON.stringify(response));
  const saved = await db.doc(`lottery/${response.result.lotteryId}`).get();
  assert.equal(saved.get("bookingStart"), values.bookingStart);
  assert.equal(saved.get("bookingEnd"), values.bookingEnd);
  assert.equal(saved.get("entrantCount"), 0);
  assert.equal(saved.get("drawStatus"), "PENDING");
});

test("rejects invalid booking periods", async () => {
  const siteId = await campsite();
  const values = settings();
  const atRegistrationEnd = await create(siteId, {
    bookingStart: values.registrationEnd,
  });
  assert.equal(atRegistrationEnd.error.status, "INVALID_ARGUMENT");
  const empty = await create(siteId, {bookingEnd: values.bookingStart});
  assert.equal(empty.error.status, "INVALID_ARGUMENT");
  const pastStart = await create(siteId, {
    registrationStart: Date.now() - hour,
  });
  assert.equal(pastStart.error.status, "INVALID_ARGUMENT");
});

test("rejects overlapping and touching periods on a campsite", async () => {
  const siteId = await campsite();
  const first = settings();
  assert.ok((await create(siteId, first)).result);

  const overlap = await create(siteId, {
    bookingStart: first.bookingStart + hour,
    bookingEnd: first.bookingEnd + day,
  });
  assert.equal(overlap.error.status, "FAILED_PRECONDITION");
  assert.match(overlap.error.message, /overlaps/);

  const touching = await create(siteId, {
    bookingStart: first.bookingEnd,
    bookingEnd: first.bookingEnd + day,
  });
  assert.equal(touching.error.status, "FAILED_PRECONDITION");

  const otherSite = await campsite();
  assert.ok((await create(otherSite, first)).result);
});

test("allows only one of two overlapping saves sent at once", async () => {
  // The emulator runs HTTP calls one at a time (maxInstances is 1), so call
  // the function in this process to make the two transactions overlap.
  const fft = require("firebase-functions-test")({projectId});
  const actions = require("../lotteryActions")(db);
  const createLottery = fft.wrap(actions.createLottery);
  const siteId = await campsite();
  const save = (overrides) => createLottery({
    data: {
      lotteryId: `lottery-${randomUUID()}`,
      campsiteId: siteId,
      ...settings(overrides),
    },
    auth: {uid: users.owner.localId},
  });
  const results = await Promise.allSettled([
    save({}),
    save({bookingStart: settings().bookingStart + hour}),
  ]);
  fft.cleanup();
  assert.equal(
      results.filter((result) => result.status === "fulfilled").length,
      1,
      JSON.stringify(results),
  );
  const saved = await db.collection("lottery")
      .where("campsiteId", "==", siteId)
      .get();
  assert.equal(saved.size, 1);
});

test("requires a signed-in campsite owner", async () => {
  const siteId = await campsite();
  assert.equal((await create(siteId, {}, null)).error.status,
      "UNAUTHENTICATED");
  assert.equal((await create(siteId, {}, users.outsider)).error.status,
      "PERMISSION_DENIED");
  const lotteryId = await seed(siteId);
  const edit = await call("updateLottery", users.outsider, {
    lotteryId,
    ...settings({lotteryName: "Taken"}),
  });
  assert.equal(edit.error.status, "PERMISSION_DENIED");
});

test("locks dates by server time after registration starts", async () => {
  const siteId = await campsite();
  // Registration started an hour ago on the server.
  const started = settings({registrationStart: Date.now() - hour});
  const lotteryId = await seed(siteId, started);

  const moved = await call("updateLottery", users.owner, {
    lotteryId,
    ...started,
    bookingStart: started.bookingStart + day,
    bookingEnd: started.bookingEnd + day,
  });
  assert.equal(moved.error.status, "FAILED_PRECONDITION");
  assert.match(moved.error.message, /cannot be changed/);

  const renamed = await call("updateLottery", users.owner, {
    lotteryId,
    ...started,
    lotteryName: "Renamed",
  });
  assert.ok(renamed.result, JSON.stringify(renamed));
  const saved = await db.doc(`lottery/${lotteryId}`).get();
  assert.equal(saved.get("lotteryName"), "Renamed");
  assert.equal(saved.get("bookingStart"), started.bookingStart);
});

test("edits dates before registration and rechecks overlap", async () => {
  const siteId = await campsite();
  const first = settings();
  const firstId = await seed(siteId, first);
  const secondId = await seed(siteId, {
    bookingStart: first.bookingStart + 5 * day,
    bookingEnd: first.bookingEnd + 5 * day,
  });

  const moved = await call("updateLottery", users.owner, {
    lotteryId: firstId,
    ...first,
    bookingStart: first.bookingStart + day,
    bookingEnd: first.bookingEnd + day,
  });
  assert.ok(moved.result, JSON.stringify(moved));

  const intoSecond = await call("updateLottery", users.owner, {
    lotteryId: firstId,
    ...first,
    bookingStart: first.bookingStart + 5 * day,
    bookingEnd: first.bookingEnd + 5 * day,
  });
  assert.equal(intoSecond.error.status, "FAILED_PRECONDITION");
  assert.ok(secondId);
});

test("applies the waitlist capacity rules", async () => {
  const siteId = await campsite();
  const values = settings({waitlistCapacity: 5});
  const lotteryId = await seed(siteId, {...values, entrantCount: 3});
  const belowCount = await call("updateLottery", users.owner, {
    lotteryId,
    ...values,
    waitlistCapacity: 2,
  });
  assert.equal(belowCount.error.status, "FAILED_PRECONDITION");

  const started = settings({
    registrationStart: Date.now() - hour,
    waitlistCapacity: 5,
  });
  const startedId = await seed(siteId, {
    ...started,
    bookingStart: started.bookingStart + 10 * day,
    bookingEnd: started.bookingEnd + 10 * day,
  });
  const base = {
    lotteryId: startedId,
    ...started,
    bookingStart: started.bookingStart + 10 * day,
    bookingEnd: started.bookingEnd + 10 * day,
  };
  const decreased = await call("updateLottery", users.owner, {
    ...base,
    waitlistCapacity: 4,
  });
  assert.equal(decreased.error.status, "FAILED_PRECONDITION");
  const increased = await call("updateLottery", users.owner, {
    ...base,
    waitlistCapacity: 6,
  });
  assert.ok(increased.result, JSON.stringify(increased));
});

test("rules deny direct lottery creates and date changes", async () => {
  const siteId = await campsite();
  const owner = users.owner;
  const direct = await patch(owner, `lottery/direct-${suffix}`, {
    campsiteId: siteId,
    lotteryName: "Direct",
    entrantCount: 0,
  }, false);
  assert.equal(direct.status, 403);

  const lotteryId = await seed(siteId);
  const dates = await patch(owner, `lottery/${lotteryId}`, {
    bookingStart: Date.now() + 30 * day,
  }, true);
  assert.equal(dates.status, 403);

  const name = await patch(owner, `lottery/${lotteryId}`, {
    lotteryName: "Renamed directly",
  }, true);
  assert.equal(name.status, 200);
});
