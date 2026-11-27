// Verifies notification callables and client access on local Firebase.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 integration tests, 2026-11-27.
// See the Bhav Saluja entry in README.md.
const assert = require("node:assert/strict");
const {randomUUID} = require("node:crypto");
const {readFileSync} = require("node:fs");
const {join} = require("node:path");
const {before, test} = require("node:test");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");

const projectId = "demo-zephyr-notifications";
const auth = "http://127.0.0.1:9099";
const functions = `http://127.0.0.1:5001/${projectId}/us-central1`;
const documents = `projects/${projectId}/databases/(default)/documents`;
const firestore = `http://127.0.0.1:8082/v1/${documents}`;
const app = initializeApp({projectId});
const db = getFirestore(app);
const suffix = randomUUID();
const lotteryId = `pines-${suffix}`;
const campsiteId = `site-${suffix}`;
const emptyLotteryId = `empty-${suffix}`;
const accounts = {};

before(async () => {
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8082");
  const roles = [
    "owner",
    "allowed",
    "optedOut",
    "late",
    "outsider",
    "winner",
    "legacy",
    "admin",
    "unlinked",
  ];
  for (const key of roles) {
    const response = await fetch(
        auth +
        "/identitytoolkit.googleapis.com/v1/" +
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
    assert.equal(
        (
          await patch(accounts[key], `accounts/${accountId}`, {
            id: accountId,
            userType: ["entrant", "owner"],
            notificationsEnabled: true,
          })
        ).status,
        200,
    );
    assert.equal(
        (
          await patch(
              accounts[key],
              `accounts/${accountId}`,
              {
                name: key,
              },
              true,
          )
        ).status,
        200,
    );
  }
  await getAuth(app).setCustomUserClaims(accounts.admin.localId, {
    admin: true,
  });
  const refreshed = await fetch(
      auth + "/securetoken.googleapis.com/v1/token?key=local-test",
      {
        method: "POST",
        headers: {"Content-Type": "application/x-www-form-urlencoded"},
        body: new URLSearchParams({
          grant_type: "refresh_token",
          refresh_token: accounts.admin.refreshToken,
        }),
      },
  );
  assert.equal(refreshed.status, 200);
  accounts.admin.idToken = (await refreshed.json()).id_token;
  const optedOut = accounts.optedOut;
  assert.equal(
      (
        await patch(
            optedOut,
            `accounts/${optedOut.accountId}`,
            {
              notificationsEnabled: false,
            },
            true,
        )
      ).status,
      200,
  );
  const owner = accounts.owner;
  assert.equal(
      (
        await patch(owner, `campsite/${campsiteId}`, {
          ownerID: owner.accountId,
        })
      ).status,
      200,
  );
  const now = Date.now();
  for (const id of [lotteryId, emptyLotteryId]) {
    assert.equal(
        (
          await patch(owner, `lottery/${id}`, {
            campsiteId,
            lotteryName: "Whispering Pines",
            registrationStart: now - 60000,
            registrationEnd: now + 600000,
            waitlistCapacity: 2,
            entrantCount: 0,
          })
        ).status,
        200,
    );
  }
  assert.equal((await registration(accounts.allowed, 1, true)).status, 200);
  assert.equal((await registration(accounts.optedOut, 2, true)).status, 200);
  await db.doc(`waitingList/${lotteryId}_${accounts.winner.accountId}`).set({
    lotteryId,
    accountId: accounts.winner.accountId,
    drawStatus: "WINNER",
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

/**
 * Reads a document through Firestore's client rules.
 * @param {Object|null} user Auth emulator identity.
 * @param {string} path Document or collection to read.
 * @return {Promise<Response>} Firestore REST response.
 */
function read(user, path) {
  return fetch(firestore + "/" + path, {
    headers: user ? {Authorization: "Bearer " + user.idToken} : {},
  });
}

const legacyId = "legacy-" + suffix;
const adminId = "admin-" + suffix;
const unlinkedId = "unlinked-" + suffix;
const validBooking = {
  cancelled: false,
  bookedPeriodStart: Timestamp.fromMillis(1000),
  bookedPeriodEnd: Timestamp.fromMillis(2000),
};

test("verified profile reads, preferences and waitlist writes", async () => {
  const {legacy, admin, outsider, unlinked} = accounts;
  const profile = "accounts/" + legacyId;
  const unlinkedProfile = "accounts/" + unlinkedId;
  await db.doc(profile).set({
    id: legacyId,
    name: "Legacy Camper",
    email: "private@example.test",
    phoneNumber: "555-0100",
    userType: "entrant",
  });
  await db
      .doc(unlinkedProfile)
      .set({id: unlinkedId, name: "Unlinked", userType: "admin"});
  await db.doc("accounts/" + adminId).set({id: adminId, userType: "entrant"});
  await db
      .doc("notificationAccountLinks/" + legacyId)
      .set({uid: legacy.localId});
  await db
      .doc("notificationAccountLinks/" + adminId)
      .set({uid: admin.localId});

  assert.deepEqual(
      (await call("resolveAccount", unlinked, {deviceId: unlinkedId})).result,
      {accountId: unlinkedId, linkRequired: true},
  );
  assert.deepEqual(
      (await call("resolveAccount", outsider, {deviceId: legacyId})).result,
      {accountId: legacyId, linkRequired: true},
  );
  assert.deepEqual(
      (await call("resolveAccount", legacy, {deviceId: "replacement-device"}))
          .result,
      {accountId: legacyId, admin: false},
  );
  assert.deepEqual(
      (
        await call("resolveAccount", accounts.allowed, {
          deviceId: "unknown-device",
        })
      ).result,
      {accountId: accounts.allowed.accountId, admin: false},
  );
  assert.deepEqual(
      (await call("resolveAccount", admin, {deviceId: adminId})).result,
      {accountId: adminId, admin: true},
  );

  assert.equal((await read(null, profile)).status, 403);
  assert.equal((await read(outsider, profile)).status, 403);
  assert.equal((await read(outsider, "accounts")).status, 403);
  assert.equal((await read(admin, "accounts")).status, 200);
  assert.equal((await read(legacy, profile)).status, 200);
  assert.equal((await read(admin, unlinkedProfile)).status, 200);
  assert.equal((await read(unlinked, unlinkedProfile)).status, 403);
  assert.equal(
      (
        await patch(
            unlinked,
            unlinkedProfile,
            {notificationsEnabled: false},
            true,
        )
      ).status,
      403,
  );
  assert.equal(
      (await patch(outsider, profile, {notificationsEnabled: false}, true))
          .status,
      403,
  );
  assert.equal(
      (await patch(admin, profile, {notificationsEnabled: false}, true)).status,
      403,
  );
  assert.equal(
      (await patch(legacy, profile, {notificationsEnabled: false}, true))
          .status,
      200,
  );
  assert.equal(
      (await patch(legacy, profile, {userType: ["admin"]}, true)).status,
      403,
  );

  const campsite = "legacy-campsite-" + suffix;
  await db.doc("campsite/" + campsite).set({ownerID: legacyId});
  const {result: label} = await call("getAccountDisplayName", outsider, {
    campsiteId: campsite,
  });
  assert.deepEqual(label, {name: "Legacy Camper"});
  assert.equal((await read(null, "campsite/" + campsite)).status, 200);
  assert.equal((await read(null, "lottery/" + emptyLotteryId)).status, 200);
  assert.equal(
      (await call("getAccountDisplayName", null, {campsiteId: campsite})).error
          .status,
      "UNAUTHENTICATED",
  );
  const linked = {...legacy, accountId: legacyId};
  assert.equal(
      (
        await registration(
            {...outsider, accountId: legacyId},
            1,
            true,
            emptyLotteryId,
        )
      ).status,
      403,
  );
  assert.equal(
      (
        await registration(
            {...unlinked, accountId: unlinkedId},
            1,
            true,
            emptyLotteryId,
        )
      ).status,
      403,
  );
  assert.equal(
      (await registration(linked, 1, true, emptyLotteryId)).status,
      200,
  );
  assert.equal(
      (await registration(linked, 0, false, emptyLotteryId)).status,
      200,
  );
});

test("rating and moderation update the average atomically", async () => {
  const {legacy, outsider, admin, unlinked} = accounts;
  const campsiteId = "rated-site-" + suffix;
  const reservationId = "completed-" + suffix;
  const site = db.doc("campsite/" + campsiteId);
  const reservation = db.doc("reservation/" + reservationId);
  await site.set({
    ownerID: accounts.owner.accountId,
    rating: [4],
    averageRating: 4,
  });
  await reservation.set({...validBooking, accountId: legacyId, campsiteId});
  const data = {
    accountId: legacyId,
    campsiteId,
    reservationId,
    score: 2,
    review: "Nice trail.",
  };
  const score = site.collection("ratings").doc(legacyId);
  assert.equal(
      (await call("saveCampsiteFeedback", outsider, data)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (
        await call("saveCampsiteFeedback", unlinked, {
          ...data,
          accountId: unlinkedId,
        })
      ).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (
        await call("saveCampsiteFeedback", outsider, {
          ...data,
          accountId: outsider.accountId,
        })
      ).error.status,
      "FAILED_PRECONDITION",
  );
  assert.equal(
      (
        await patch(
            legacy,
            reservation.path,
            {accountId: outsider.accountId},
            true,
        )
      ).status,
      403,
  );
  assert.equal(
      (
        await patch(legacy, "reservation/fabricated-" + suffix, {
          accountId: legacyId,
          campsiteId,
          cancelled: false,
        })
      ).status,
      403,
  );
  assert.equal(
      (await patch(accounts.owner, site.path, {rating: [5]}, true)).status,
      403,
  );
  assert.equal((await patch(legacy, score.path, {score: 5})).status, 403);

  const query = await fetch(firestore + ":runQuery", {
    method: "POST",
    headers: {
      "Authorization": "Bearer " + legacy.idToken,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      structuredQuery: {
        from: [{collectionId: "reservation"}],
        where: {
          fieldFilter: {
            field: {fieldPath: "accountId"},
            op: "EQUAL",
            value: {stringValue: legacyId},
          },
        },
      },
    }),
  });
  assert.equal(query.status, 200, await query.clone().text());
  assert.equal(
      (await query.json())[0].document.name.endsWith(reservationId),
      true,
  );
  assert.equal((await read(outsider, reservation.path)).status, 403);
  assert.equal((await read(legacy, reservation.path)).status, 200);
  assert.deepEqual(
      (await call("saveCampsiteFeedback", legacy, data)).result,
      {},
  );
  assert.equal((await site.get()).get("averageRating"), 3);
  assert.equal((await score.get()).get("score"), 2);
  assert.deepEqual(
      (
        await call("saveCampsiteFeedback", legacy, {
          accountId: legacyId,
          campsiteId,
          reservationId,
          review: "Edited review.",
        })
      ).result,
      {},
  );
  assert.equal((await score.get()).get("review"), "Edited review.");
  assert.equal((await score.get()).get("score"), 2);
  assert.equal((await site.get()).get("averageRating"), 3);
  assert.equal((await read(outsider, score.path)).status, 200);
  assert.equal(
      (await call("removeCampsiteFeedback", legacy, data)).error.status,
      "PERMISSION_DENIED",
  );
  assert.deepEqual(
      (await call("removeCampsiteFeedback", admin, data)).result,
      {},
  );
  assert.deepEqual((await site.get()).get("rating"), [4]);
  assert.equal((await site.get()).get("averageRating"), 4);
  assert.equal((await score.get()).exists, false);
});

test("verified legacy admin can moderate managed campsite images", async () => {
  const {admin, outsider, unlinked} = accounts;
  const site = db.doc("campsite/managed-images-" + suffix);
  const selected = "campsites/" + site.id + "/selected.png";
  const other = "campsites/" + site.id + "/other.png";
  const bucket = getStorage(app).bucket(projectId + ".appspot.com");
  await bucket.file(selected).save(Buffer.from("selected image"));
  await bucket.file(other).save(Buffer.from("another image"));
  await site.set({
    ownerID: accounts.owner.accountId,
    compressedImages: ["first", "keep"],
    imagePath: selected,
    promoImagePaths: [selected, other],
  });
  const deleteObject = (account) =>
    fetch(
        "http://127.0.0.1:9199/v0/b/" +
        bucket.name +
        "/o/" +
        encodeURIComponent(selected),
        {
          method: "DELETE",
          headers: {Authorization: "Firebase " + account.idToken},
        },
    );
  for (const user of [outsider, unlinked]) {
    assert.equal(
        (await patch(user, site.path, {compressedImages: ["keep"]}, true))
            .status,
        403,
    );
    assert.equal(
        (
          await patch(
              user,
              site.path,
              {imagePath: "", promoImagePaths: [other]},
              true,
          )
        ).status,
        403,
    );
    assert.equal((await deleteObject(user)).status, 403);
  }
  assert.equal(
      (await patch(admin, site.path, {compressedImages: ["keep"]}, true))
          .status,
      200,
  );
  assert.deepEqual((await site.get()).get("compressedImages"), ["keep"]);
  assert.equal((await deleteObject(admin)).status, 204);
  assert.equal(
      (
        await patch(
            admin,
            site.path,
            {imagePath: "", promoImagePaths: [other]},
            true,
        )
      ).status,
      200,
  );
  assert.equal((await site.get()).get("imagePath"), "");
  assert.deepEqual((await site.get()).get("promoImagePaths"), [other]);
  assert.equal((await bucket.file(selected).exists())[0], false);
  assert.equal((await bucket.file(other).exists())[0], true);
  assert.equal(
      (await patch(admin, site.path, {ownerID: adminId}, true)).status,
      403,
  );
});

test("profile cleanup is atomic and can be retried", async () => {
  const {legacy, outsider} = accounts;
  const accountId = "delete-" + suffix;
  const profile = db.doc("accounts/" + accountId);
  const link = db.doc("notificationAccountLinks/" + accountId);
  const id = "cleanup-" + suffix;
  const campsiteId = "cleanup-site-" + suffix;
  const site = db.doc("campsite/" + campsiteId);
  const lottery = db.doc("lottery/" + id);
  const entry = db.doc("waitingList/" + id + "_" + accountId);
  const past = db.doc("reservation/past-" + suffix);
  const future = db.doc("reservation/future-" + suffix);
  const score = site.collection("ratings").doc(accountId);

  await Promise.all([
    profile.set({id: accountId, userType: "entrant"}),
    site.set({
      ownerID: accounts.owner.accountId,
      rating: [4],
      averageRating: 4,
    }),
    lottery.set({campsiteId, entrantCount: 2}),
    entry.set({accountId, lotteryId: id, drawStatus: "WAITING"}),
    past.set({...validBooking, accountId, campsiteId}),
    future.set({
      ...validBooking,
      accountId,
      campsiteId,
      bookedPeriodEnd: Timestamp.fromMillis(Date.now() + 60000),
    }),
    score.set({accountId, campsiteId, score: 5}),
  ]);

  const args = {accountId};
  assert.equal(
      (await call("deleteProfile", legacy, args)).error.status,
      "PERMISSION_DENIED",
  );
  await link.set({uid: legacy.localId});
  assert.equal(
      (await call("deleteProfile", outsider, args)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (
        await fetch(firestore + "/" + profile.path, {
          method: "DELETE",
          headers: {Authorization: "Bearer " + legacy.idToken},
        })
      ).status,
      403,
  );

  // An inconsistent score makes cleanup fail before any state is committed.
  assert.equal(
      (await call("deleteProfile", legacy, args)).error.status,
      "FAILED_PRECONDITION",
  );
  assert.equal((await profile.get()).exists, true);
  assert.equal((await profile.get()).get("deletionPending"), undefined);
  assert.equal((await entry.get()).exists, true);
  assert.equal((await future.get()).get("cancelled"), false);
  assert.equal(
      (await patch(legacy, profile.path, {notificationsEnabled: false}, true))
          .status,
      200,
  );

  await site.update({rating: [5, 4], averageRating: 4.5});
  assert.ok((await call("deleteProfile", legacy, args)).result);
  assert.equal((await link.get()).exists, false);
  assert.equal((await profile.get()).exists, false);
  assert.equal((await entry.get()).exists, false);
  assert.equal((await score.get()).exists, false);
  assert.deepEqual((await site.get()).get("rating"), [4]);
  assert.equal((await site.get()).get("averageRating"), 4);
  assert.equal((await lottery.get()).get("entrantCount"), 1);
  assert.equal((await past.get()).get("cancelled"), false);
  assert.equal((await past.get()).get("accountId"), undefined);
  assert.equal((await future.get()).get("cancelled"), true);
  assert.equal((await future.get()).get("accountId"), undefined);
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

test("legacy winner promotes first eligible backup in order", async () => {
  const {legacy, unlinked, outsider, allowed, late} = accounts;
  const id = "completed-" + suffix;
  const missing = "missing-" + suffix;
  const available = [allowed.accountId, unlinkedId, late.accountId];
  const backupIds = [missing, ...available];
  const event = db.doc("lottery/" + id);
  const result = db.doc("lotteryDrawResults/" + id);
  const registrationRef = (accountId) =>
    db.doc("waitingList/" + id + "_" + accountId);
  await event.set({campsiteId, numberOfWinners: 1, drawCompleted: true});
  await result.set({
    winnerIds: [legacyId],
    backupIds,
    winnerCount: 1,
    backupCount: backupIds.length,
  });
  await registrationRef(legacyId).set({
    accountId: legacyId,
    lotteryId: id,
    drawStatus: "WINNER",
  });
  for (const [index, accountId] of backupIds.entries()) {
    await registrationRef(accountId).set({
      accountId,
      lotteryId: id,
      drawStatus: "BACKUP",
      backupPosition: index + 1,
    });
  }
  const args = {lotteryId: id, accountId: legacyId};
  assert.equal(
      (await call("declineLotteryOffer", unlinked, args)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (await call("declineLotteryOffer", outsider, args)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (await commit(legacy, [update(result.path, {backupIds: []})])).status,
      403,
  );
  assert.equal(
      (
        await commit(legacy, [
          update(registrationRef(legacyId).path, {drawStatus: "DECLINED"}),
        ])
      ).status,
      403,
  );
  assert.deepEqual((await result.get()).get("backupIds"), backupIds);
  assert.deepEqual(
      (await call("declineLotteryOffer", legacy, args)).result,
      {},
  );
  const updated = (await result.get()).data();
  assert.deepEqual(updated.winnerIds, [available[0]]);
  assert.deepEqual(updated.backupIds, [missing, available[1], available[2]]);
  assert.deepEqual(updated.declinedIds, [legacyId]);
  assert.equal(
      (await registrationRef(legacyId).get()).get("drawStatus"),
      "DECLINED",
  );
  assert.equal(
      (await registrationRef(allowed.accountId).get()).get("drawStatus"),
      "WINNER",
  );
  assert.equal(
      (await registrationRef(unlinkedId).get()).get("backupPosition"),
      2,
  );
  assert.equal(
      (await registrationRef(late.accountId).get()).get("backupPosition"),
      3,
  );
  // The fresh account may decline its own offer. The existing unlinked profile
  // is eligible to receive that offer and must verify before it can decline.
  assert.deepEqual(
      (await call("declineLotteryOffer", allowed, {lotteryId: id})).result,
      {},
  );
  assert.deepEqual((await result.get()).get("winnerIds"), [unlinkedId]);
  assert.equal(
      (
        await call("declineLotteryOffer", unlinked, {
          lotteryId: id,
          accountId: unlinkedId,
        })
      ).error.status,
      "PERMISSION_DENIED",
  );
});

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

test("app reads still work after the legacy cutoff", async () => {
  const rules = readFileSync(
      join(__dirname, "../../firestore.rules"),
      "utf8",
  );
  const cutoff = rules.replace(
      /timestamp\.date\(\d{4}, \d+, \d+\)/,
      "timestamp.date(2000, 1, 1)",
  );
  assert.notEqual(cutoff, rules);
  const endpoint =
    "http://127.0.0.1:8082/emulator/v1/projects/" +
    projectId +
    ":securityRules";
  const loadRules = async (content) => {
    const response = await fetch(endpoint, {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        rules: {files: [{name: "firestore.rules", content}]},
      }),
    });
    assert.equal(response.status, 200, await response.clone().text());
  };
  const unrelated = "legacyOnly/record-" + suffix;
  await db.doc(unrelated).set({exists: true});
  await loadRules(cutoff);
  try {
    assert.equal((await read(null, unrelated)).status, 403);
    assert.equal((await read(null, "campsite/" + campsiteId)).status, 200);
    assert.equal((await read(null, "lottery/" + lotteryId)).status, 200);
    assert.equal(
        (
          await read(
              accounts.late,
              "waitingList/" + lotteryId + "_" + accounts.late.accountId,
          )
        ).status,
        200,
    );
    assert.equal(
        (await read(accounts.legacy, "reservation/completed-" + suffix)).status,
        200,
    );
    assert.equal(
        (await read(accounts.legacy, "accounts/" + legacyId)).status,
        200,
    );
    assert.equal(
        (await read(accounts.outsider, "accounts/" + legacyId)).status,
        403,
    );
  } finally {
    await loadRules(rules);
  }
});
