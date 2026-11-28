// Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
// Runs the production callable handlers against an in-memory transaction fixture.
const assert = require("node:assert/strict");
const {test} = require("node:test");
const {sharedLotteryHandlers} = require("../sharedLotteries");

class TestHttpsError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

const timestamp = (ms) => ({toMillis: () => ms});

function fixture() {
  const rows = {
    "accounts/auth_owner": {},
    "campsite/1": {ownerID: "auth_owner"},
    "campsite/2": {ownerID: "auth_owner"},
    "campsite/3": {ownerID: "auth_other"},
    "campsite/4": {ownerID: "auth_owner"},
  };
  let pending = Promise.resolve();
  const snapshot = (path) => ({
    id: path.split("/")[1],
    exists: path in rows,
    data: () => rows[path],
    get: (key) => rows[path]?.[key],
  });
  const db = {
    collection: (name) => ({
      doc: (id) => name + "/" + id,
      where: (key, op, value) => ({name, key, op, value}),
    }),
    runTransaction: (handler) => {
      const run = pending.then(async () => {
        const writes = [];
        const get = async (ref) => {
          assert.equal(writes.length, 0, "all reads must precede writes");
          if (typeof ref === "string") return snapshot(ref);
          const {name, key, op, value} = ref;
          const docs = Object.entries(rows)
              .filter(([path, data]) => path.startsWith(name + "/") &&
                (op === "==" ? data[key] === value : data[key] >= value))
              .map(([path]) => snapshot(path));
          return {docs};
        };
        const response = await handler({
          get,
          getAll: (...refs) => Promise.all(refs.map(get)),
          create: (ref, data) => {
            assert.equal(ref in rows, false);
            writes.push(() => { rows[ref] = data; });
          },
          update: (ref, data) => writes.push(() => Object.assign(rows[ref], data)),
          set: (ref, data) => writes.push(() => { rows[ref] = data; }),
        });
        writes.forEach((write) => write());
        return response;
      });
      pending = run.catch(() => {});
      return run;
    },
  };
  const handlers = sharedLotteryHandlers(db, {
    now: () => 1,
    HttpsError: TestHttpsError,
    Timestamp: {fromMillis: timestamp},
    FieldValue: {serverTimestamp: () => 1},
  });
  return {rows, ...handlers};
}

const request = (uid, data) => ({auth: {uid}, data});
const settings = {
  lotteryId: "shared",
  create: true,
  lotteryName: "Shared lottery",
  campsiteIds: ["1", "2"],
  registrationStart: 10,
  registrationEnd: 20,
  bookingStart: 30,
  bookingEnd: 60,
  numberOfWinners: 2,
};
const stay = {lotteryId: "shared", campsiteId: "2", start: 30, end: 45};

async function draw(f) {
  await f.saveLotterySettings(request("owner", settings));
  f.rows["lottery/shared"].drawCompleted = true;
  f.rows["lotteryDrawResults/shared"] = {winnerIds: ["auth_a", "auth_b", "auth_c"]};
  for (const uid of ["a", "b", "c", "backup"]) {
    f.rows["accounts/auth_" + uid] = {};
    f.rows["waitingList/shared_auth_" + uid] = {
      accountId: "auth_" + uid,
      lotteryId: "shared",
      drawStatus: uid === "backup" ? "BACKUP" : "WINNER",
    };
  }
}

test("owner saves all campsite links together and cannot include someone else's site", async () => {
  const f = fixture();
  await assert.rejects(f.saveLotterySettings(request("owner", {
    ...settings, campsiteIds: ["1", "3"],
  })), {code: "permission-denied"});
  assert.equal(f.rows["lottery/shared"], undefined);
  await f.saveLotterySettings(request("owner", settings));
  await f.saveLotterySettings(request("owner", settings));
  assert.deepEqual(f.rows["lottery/shared"].campsiteIds, ["1", "2"]);
  assert.equal(f.rows["lottery/shared"].campsiteId, "1");
  assert.ok(f.rows["lotteryBookingLocks/1"]);
  assert.ok(f.rows["lotteryBookingLocks/2"]);
});

test("a linked campsite conflicts with legacy and shared lotteries on create or edit", async () => {
  const f = fixture();
  f.rows["lottery/legacy"] = {campsiteId: "2", bookingStart: 70, bookingEnd: 80};
  await f.saveLotterySettings(request("owner", settings));
  await assert.rejects(f.saveLotterySettings(request("owner", {
    ...settings, create: false, bookingEnd: 70,
  })), {code: "already-exists"});
  await assert.rejects(f.saveLotterySettings(request("owner", {
    ...settings, lotteryId: "new", campsiteIds: ["2"], bookingEnd: 75,
  })), {code: "already-exists"});
  await f.saveLotterySettings(request("owner", {
    ...settings, lotteryId: "unrelated", campsiteIds: ["4"],
  }));
  assert.equal(f.rows["lottery/shared"].bookingEnd, 60);
});

test("only a winner can check a participating campsite within the lottery period", async () => {
  const f = fixture();
  await draw(f);
  await assert.rejects(f.confirmLotteryBooking(request("backup", stay)),
      {code: "permission-denied"});
  await assert.rejects(f.confirmLotteryBooking(request("a", {...stay, campsiteId: "4"})),
      {code: "invalid-argument"});
  await assert.rejects(f.confirmLotteryBooking(request("a", {...stay, end: 61})),
      {code: "invalid-argument"});
  const check = await f.confirmLotteryBooking(request("a", {...stay, checkOnly: true}));
  assert.deepEqual(check, {available: true});
  assert.equal(Object.keys(f.rows).some((key) => key.startsWith("reservation/")), false);
});

test("one confirmed reservation per offer and no overlaps on the same campsite", async () => {
  const f = fixture();
  await draw(f);
  const {reservationId} = await f.confirmLotteryBooking(request("a", stay));
  const saved = f.rows["reservation/" + reservationId];
  assert.equal(saved.campsiteId, "2");
  assert.equal(saved.lotteryId, "shared");
  assert.equal(saved.status, "confirmed");
  assert.equal(saved.bookedPeriodStart.toMillis(), 30);
  assert.deepEqual(await f.confirmLotteryBooking(request("a", stay)), {reservationId});
  await assert.rejects(f.confirmLotteryBooking(request("a", {...stay, campsiteId: "1"})),
      {code: "already-exists"});
  await assert.rejects(f.confirmLotteryBooking(request("b", {...stay, start: 40})),
      {code: "already-exists"});
  await f.confirmLotteryBooking(request("b", {...stay, start: 45, end: 60}));
  await f.confirmLotteryBooking(request("c", {...stay, campsiteId: "1"}));
});

test("confirmation rechecks a previously available period and ignores cancelled stays", async () => {
  const f = fixture();
  await draw(f);
  await f.confirmLotteryBooking(request("a", {...stay, checkOnly: true}));
  const {reservationId} = await f.confirmLotteryBooking(request("b", stay));
  await assert.rejects(f.confirmLotteryBooking(request("a", stay)), {code: "already-exists"});
  f.rows["reservation/" + reservationId].cancelled = true;
  await f.confirmLotteryBooking(request("a", stay));
  await assert.rejects(f.confirmLotteryBooking(request("b", {...stay, campsiteId: "1"})),
      {code: "already-exists"});
});

test("a site's available period and the winner's decline are enforced at confirmation", async () => {
  const f = fixture();
  await draw(f);
  f.rows["campsite/2"].bookingPeriodStart = timestamp(35);
  f.rows["campsite/2"].bookingPeriodEnd = timestamp(50);
  await assert.rejects(f.confirmLotteryBooking(request("a", stay)),
      {code: "failed-precondition"});
  const validStay = {...stay, start: 35, end: 50, checkOnly: true};
  await f.confirmLotteryBooking(request("a", validStay));
  f.rows["lotteryDrawResults/shared"].declinedIds = ["auth_a"];
  await assert.rejects(f.confirmLotteryBooking(request("a", {...validStay, checkOnly: false})),
      {code: "permission-denied"});
});
