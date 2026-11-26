const {test} = require("node:test");
const assert = require("node:assert/strict");
const {randomUUID} = require("node:crypto");
const admin = require("firebase-admin");

// Never allow these tests to connect to production Firestore.
if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080" ||
    process.env.GCLOUD_PROJECT !== "demo-zephyr") {
  throw new Error("Integration tests require the local demo-zephyr emulator.");
}

admin.initializeApp({projectId: "demo-zephyr"});

const db = admin.firestore();
const schedulerUrl =
    "http://127.0.0.1:5001/demo-zephyr/us-central1/" +
    "automaticLotteryDraw-0";

/**
 * Invokes the scheduled lottery function locally.
 * @return {Promise<void>}
 */
async function runDraw() {
  const response = await fetch(schedulerUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-CloudScheduler": "true",
    },
    body: "{}",
  });

  assert.equal(response.status, 200);
}

/**
 * Creates an isolated lottery fixture.
 * @param {Object} t Node test context.
 * @param {Object} options Lottery configuration.
 * @return {Promise<Object>} Created fixture.
 */
async function createLottery(t, options = {}) {
  const lotteryId = `integration-${randomUUID()}`;
  const entrantCount = options.entrantCount == null ?
      5 : options.entrantCount;
  const numberOfWinners = options.numberOfWinners == null ?
      2 : options.numberOfWinners;

  const lotteryRef = db.collection("lottery").doc(lotteryId);
  const resultRef = db.collection("lotteryDrawResults").doc(lotteryId);

  const refs = [lotteryRef, resultRef];
  const entrants = [];

  t.after(async () => {
    for (const ref of refs.reverse()) {
      await ref.delete();
    }
  });

  const lottery = {
    id: lotteryId,
    campsiteId: `campsite-${lotteryId}`,
    registrationStart: Date.now() - 3600000,
    registrationEnd: Date.now() - 60000,
    numberOfWinners,
    entrantCount,
    drawStatus: "PENDING",
    drawCompleted: false,
    drawStartedAt: 0,
    drawAttemptId: "",
    ...options.lottery,
  };

  if (options.omitStartTime) {
    delete lottery.drawStartedAt;
  }

  await lotteryRef.set(lottery);

  for (let i = 0; i < entrantCount; i++) {
    const accountId = `${lotteryId}-entrant-${i}`;
    const registrationId = `${lotteryId}_${accountId}`;

    const accountRef = db.collection("accounts").doc(accountId);
    const registrationRef =
        db.collection("waitingList").doc(registrationId);

    refs.push(accountRef, registrationRef);

    await accountRef.set({
      id: accountId,
      userType: ["entrant", "owner"],
      deletionPending: false,
      notificationsEnabled: true,
    });

    await registrationRef.set({
      id: registrationId,
      lotteryId,
      accountId,
      joinedAt: Date.now() - 120000,
      drawStatus: "WAITING",
      backupPosition: 0,
    });

    entrants.push({
      accountId,
      accountRef,
      registrationRef,
    });
  }

  return {
    lotteryId,
    lotteryRef,
    resultRef,
    entrants,
    refs,
  };
}

/**
 * Checks persisted lottery results and registration statuses.
 * @param {Object} fixture Lottery fixture.
 * @param {Array<Object>} eligible Eligible entrants.
 * @param {number} winnerCount Expected winners.
 * @return {Promise<Object>} Saved result.
 */
async function verifyResults(fixture, eligible, winnerCount) {
  const lotteryDoc = await fixture.lotteryRef.get();
  const resultDoc = await fixture.resultRef.get();

  assert.equal(lotteryDoc.get("drawCompleted"), true);
  assert.equal(lotteryDoc.get("drawStatus"), "COMPLETED");
  assert.equal(lotteryDoc.get("drawStartedAt"), 0);
  assert.equal(resultDoc.exists, true);

  const result = resultDoc.data();

  assert.equal(result.winnerIds.length, winnerCount);
  assert.equal(result.backupIds.length, eligible.length - winnerCount);
  assert.equal(result.winnerCount, result.winnerIds.length);
  assert.equal(result.backupCount, result.backupIds.length);

  const selectedIds = [...result.winnerIds, ...result.backupIds];

  assert.equal(new Set(selectedIds).size, selectedIds.length);

  assert.deepEqual(
      [...selectedIds].sort(),
      eligible.map((entrant) => entrant.accountId).sort(),
  );

  for (const entrant of eligible) {
    const registration = await entrant.registrationRef.get();

    if (result.winnerIds.includes(entrant.accountId)) {
      assert.equal(registration.get("drawStatus"), "WINNER");
      assert.equal(registration.get("backupPosition"), 0);
    } else {
      assert.equal(registration.get("drawStatus"), "BACKUP");
      assert.equal(
          registration.get("backupPosition"),
          result.backupIds.indexOf(entrant.accountId) + 1,
      );
    }
  }

  return result;
}

test("draws configured winners and persists backups", async (t) => {
  const fixture = await createLottery(t);

  await runDraw();

  await verifyResults(fixture, fixture.entrants, 2);
});

test("does not repeat a completed draw", async (t) => {
  const fixture = await createLottery(t);

  await runDraw();

  const first = await verifyResults(fixture, fixture.entrants, 2);

  await runDraw();

  const second = await verifyResults(fixture, fixture.entrants, 2);

  assert.deepEqual(second, first);
});

test("handles fewer entrants than available spots", async (t) => {
  const fixture = await createLottery(t, {
    entrantCount: 2,
    numberOfWinners: 5,
  });

  await runDraw();

  await verifyResults(fixture, fixture.entrants, 2);
});

test("completes a lottery with no entrants", async (t) => {
  const fixture = await createLottery(t, {
    entrantCount: 0,
  });

  await runDraw();

  await verifyResults(fixture, [], 0);
});

test("does not draw before registration closes", async (t) => {
  const fixture = await createLottery(t, {
    lottery: {
      registrationEnd: Date.now() + 3600000,
    },
  });

  await runDraw();

  const lottery = await fixture.lotteryRef.get();
  const result = await fixture.resultRef.get();

  assert.equal(lottery.get("drawStatus"), "PENDING");
  assert.equal(lottery.get("drawCompleted"), false);
  assert.equal(result.exists, false);
});

test("excludes registrations with blank account IDs", async (t) => {
  const fixture = await createLottery(t);

  await fixture.entrants[0].registrationRef.update({
    accountId: "",
  });

  await runDraw();

  await verifyResults(fixture, fixture.entrants.slice(1), 2);
});

test("excludes noncanonical duplicate registrations", async (t) => {
  const fixture = await createLottery(t);

  const duplicateRef = db.collection("waitingList")
      .doc(`duplicate-${fixture.lotteryId}`);

  fixture.refs.push(duplicateRef);

  const original = await fixture.entrants[0].registrationRef.get();

  await duplicateRef.set({
    ...original.data(),
    id: duplicateRef.id,
  });

  await runDraw();

  await verifyResults(fixture, fixture.entrants, 2);

  const duplicate = await duplicateRef.get();

  assert.equal(duplicate.get("drawStatus"), "WAITING");
});

test("excludes entrants with deleted accounts", async (t) => {
  const fixture = await createLottery(t);

  await fixture.entrants[0].accountRef.delete();

  await runDraw();

  await verifyResults(fixture, fixture.entrants.slice(1), 2);
});

test("excludes accounts pending deletion", async (t) => {
  const fixture = await createLottery(t);

  await fixture.entrants[0].accountRef.update({
    deletionPending: true,
  });

  await runDraw();

  await verifyResults(fixture, fixture.entrants.slice(1), 2);
});

test("excludes registrations that are not WAITING", async (t) => {
  const fixture = await createLottery(t);

  await fixture.entrants[0].registrationRef.update({
    drawStatus: "DECLINED",
  });

  await runDraw();

  await verifyResults(fixture, fixture.entrants.slice(1), 2);
});

test("completes when all entrants are ineligible", async (t) => {
  const fixture = await createLottery(t);

  for (const entrant of fixture.entrants) {
    await entrant.accountRef.update({
      deletionPending: true,
    });
  }

  await runDraw();

  await verifyResults(fixture, [], 0);
});

test("recovers an expired processing draw", async (t) => {
  const fixture = await createLottery(t, {
    lottery: {
      drawStatus: "PROCESSING",
      drawAttemptId: "expired-attempt",
      drawStartedAt: Date.now() - 600000,
    },
  });

  await runDraw();

  await verifyResults(fixture, fixture.entrants, 2);
});

test("does not reset an active processing draw", async (t) => {
  const fixture = await createLottery(t, {
    lottery: {
      drawStatus: "PROCESSING",
      drawAttemptId: "active-attempt",
      drawStartedAt: Date.now(),
    },
  });

  await runDraw();

  const lottery = await fixture.lotteryRef.get();
  const result = await fixture.resultRef.get();

  assert.equal(lottery.get("drawStatus"), "PROCESSING");
  assert.equal(lottery.get("drawAttemptId"), "active-attempt");
  assert.equal(result.exists, false);
});

test("recovers a legacy draw with no start timestamp", async (t) => {
  const fixture = await createLottery(t, {
    omitStartTime: true,
    lottery: {
      drawStatus: "PROCESSING",
      drawAttemptId: "legacy-attempt",
    },
  });

  await runDraw();

  await verifyResults(fixture, fixture.entrants, 2);
});

test("recovers a legacy draw with zero start timestamp", async (t) => {
  const fixture = await createLottery(t, {
    lottery: {
      drawStatus: "PROCESSING",
      drawAttemptId: "legacy-attempt",
      drawStartedAt: 0,
    },
  });

  await runDraw();

  await verifyResults(fixture, fixture.entrants, 2);
});

test("excludes invalid Firestore account document IDs", async (t) => {
  const fixture = await createLottery(t);

  const invalidIds = [".", "..", "__invalid__"];

  for (const accountId of invalidIds) {
    const ref = db.collection("waitingList")
        .doc(`${fixture.lotteryId}_${accountId}`);

    fixture.refs.push(ref);

    await ref.set({
      lotteryId: fixture.lotteryId,
      accountId,
      drawStatus: "WAITING",
      backupPosition: 0,
    });
  }

  await runDraw();

  await verifyResults(fixture, fixture.entrants, 2);
});

test("repeated concurrent requests preserve unique results", async (t) => {
  const fixture = await createLottery(t);

  await Promise.all([runDraw(), runDraw()]);

  const first = await verifyResults(fixture, fixture.entrants, 2);

  await runDraw();

  const second = await verifyResults(fixture, fixture.entrants, 2);

  assert.deepEqual(first, second);
});

test("an invalid lottery does not block other lotteries", async (t) => {
  const invalid = await createLottery(t, {
    numberOfWinners: -1,
  });

  const valid = await createLottery(t);

  await runDraw();

  await verifyResults(valid, valid.entrants, 2);

  const invalidLottery = await invalid.lotteryRef.get();
  const invalidResult = await invalid.resultRef.get();

  assert.equal(invalidLottery.get("drawStatus"), "PENDING");
  assert.equal(invalidLottery.get("drawCompleted"), false);
  assert.equal(invalidLottery.get("drawAttemptId"), "");
  assert.equal(invalidResult.exists, false);
});
