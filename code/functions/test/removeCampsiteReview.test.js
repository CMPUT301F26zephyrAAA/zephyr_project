// Tests the review moderation callable with a fake Firestore transaction.
// OpenAI, Codex, US 03.10.01 review moderation tests, 2026-11-28.
const {test} = require("node:test");
const assert = require("node:assert/strict");
const {FieldValue} = require("firebase-admin/firestore");
const accountActions = require("../accountActions");

const admin = {uid: "moderator", token: {admin: true}};
const updatedAt = {seconds: 123, nanoseconds: 456000};
const data = {
  adminAccountId: "auth_moderator",
  campsiteId: "pine-lake",
  accountId: "auth_entrant",
  review: "Policy violation",
  updatedAt,
};
const ratingPath = "campsite/pine-lake/ratings/auth_entrant";

/**
 * Runs the real callable with in-memory records and records every write.
 * @param {Object} fields Selected feedback fields.
 * @return {Object} Callable runner and observed writes.
 */
function moderation(fields) {
  const writes = [];
  const db = {
    collection: (path) => ({
      doc: (id) => document(`${path}/${id}`),
    }),
    runTransaction: (work) => work({
      getAll: (...refs) => {
        assert.deepEqual(refs.map((ref) => ref.path), [
          "campsite/pine-lake",
          ratingPath,
        ]);
        return [
          {exists: true},
          {exists: fields !== null, get: (key) => fields?.[key]},
        ];
      },
      update: (ref, updates) => writes.push([ref.path, updates]),
      delete: (ref) => writes.push([ref.path]),
    }),
  };
  const callable = accountActions(db).removeCampsiteReview;
  return {run: (auth, payload = data) => callable.run({auth, data: payload}),
    writes};
}

/**
 * Creates a document reference for the fake database.
 * @param {string} path Document path.
 * @return {Object} Reference with a subcollection builder.
 */
function document(path) {
  return {
    path,
    collection: (name) => ({
      doc: (id) => document(`${path}/${name}/${id}`),
    }),
  };
}

test("keeps the score when it removes review text", async () => {
  const {run, writes} = moderation({...data, score: 4});
  await run(admin);
  assert.deepEqual(writes, [[ratingPath, {
    review: FieldValue.delete(),
    updatedAt: FieldValue.serverTimestamp(),
  }]]);
});

test("deletes a review-only document", async () => {
  const {run, writes} = moderation(data);
  await run(admin);
  assert.deepEqual(writes, [[ratingPath]]);
});

test("can remove legacy review text without a timestamp", async () => {
  const {run, writes} = moderation({review: data.review});
  await run(admin, {...data, updatedAt: undefined});
  assert.deepEqual(writes, [[ratingPath]]);
});

test("rejects unauthenticated and non-admin requests", async () => {
  const {run, writes} = moderation(data);
  await assert.rejects(run(undefined), {code: "unauthenticated"});
  await assert.rejects(run({uid: admin.uid, token: {}}),
      {code: "permission-denied"});
  await assert.rejects(run({uid: "someone-else", token: {admin: true}}),
      {code: "permission-denied"});
  assert.deepEqual(writes, []);
});

test("rejects edited or already removed reviews", async () => {
  for (const fields of [
    {...data, review: "Edited"},
    {...data, updatedAt: {...updatedAt, seconds: 124}},
    {...data, review: undefined},
    null,
  ]) {
    const {run, writes} = moderation(fields);
    await assert.rejects(run(admin), {code: "failed-precondition"});
    assert.deepEqual(writes, []);
  }
});
