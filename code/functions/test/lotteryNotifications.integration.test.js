// Verifies notification callables and client access on local Firebase.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 integration tests, 2026-11-27.
// See the team AI Documentation Google Doc.
const assert = require("node:assert/strict");
const {randomUUID} = require("node:crypto");
const {before, test} = require("node:test");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");
const lotteryNotifications = require("../lotteryNotifications");

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
    "reviewer",
    "deleting",
    "admin",
    "otherBackup",
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
  // Only the createLottery function creates lotteries, so use admin access.
  for (const id of [lotteryId, emptyLotteryId]) {
    await db.doc(`lottery/${id}`).set({
      campsiteId,
      lotteryName: "Whispering Pines",
      registrationStart: now - 60000,
      registrationEnd: now + 600000,
      waitlistCapacity: 2,
      entrantCount: 0,
    });
  }
  assert.equal((await registration(accounts.allowed, 1, true)).status, 200);
  assert.equal(
      (await registration(accounts.optedOut, 2, true)).status,
      200,
  );
  await db
      .doc(`waitingList/${lotteryId}_${accounts.winner.accountId}`)
      .set({
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
  return Object.fromEntries(
      Object.entries(data).map(([key, value]) => [
        key,
      typeof value === "boolean" ?
        {booleanValue: value} :
        Number.isInteger(value) ?
          {integerValue: String(value)} :
          Array.isArray(value) ?
            {arrayValue: {values: value.map((v) => ({stringValue: v}))}} :
            {stringValue: value},
      ]),
  );
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
      ...(user.idToken ? {Authorization: `Bearer ${user.idToken}`} : {}),
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
function registration(
    user,
    count,
    join,
    id = lotteryId,
    withMarker = true,
) {
  const path = `${documents}/waitingList/${id}_${user.accountId}`;
  const entryWrite = join ?
    {
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
    } :
    {delete: path, currentDocument: {exists: true}};
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
      ...(user.idToken ? {Authorization: `Bearer ${user.idToken}`} : {}),
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
    body: JSON.stringify({
      data: {accountId: user && user.accountId, ...data},
    }),
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
      body: JSON.stringify({
        fields: {uid: {stringValue: outsider.localId}},
      }),
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

const validBooking = {
  cancelled: false,
  bookedPeriodStart: Timestamp.fromMillis(1000),
  bookedPeriodEnd: Timestamp.fromMillis(2000),
};

test("fresh profile reads and writes require ownership", async () => {
  const {admin, reviewer, outsider} = accounts;
  const profile = "accounts/" + reviewer.accountId;
  assert.equal((await read(null, profile)).status, 403);
  assert.equal((await read(outsider, profile)).status, 403);
  assert.equal((await read(outsider, "accounts")).status, 403);
  assert.equal((await read(admin, "accounts")).status, 200);
  assert.equal((await read(reviewer, profile)).status, 200);
  assert.equal(
      (await patch(outsider, profile, {notificationsEnabled: false}, true))
          .status,
      403,
  );
  assert.equal(
      (await patch(admin, profile, {notificationsEnabled: false}, true))
          .status,
      403,
  );
  assert.equal(
      (await patch(reviewer, profile, {notificationsEnabled: false}, true))
          .status,
      200,
  );
  assert.equal(
      (await patch(reviewer, profile, {userType: ["admin"]}, true)).status,
      403,
  );
  assert.equal(
      (await patch(reviewer, profile, {compressedImage: "data"}, true))
          .status,
      200,
  );
  assert.equal(
      (await patch(outsider, profile, {compressedImage: ""}, true)).status,
      403,
  );
  assert.equal(
      (
        await patch(reviewer, "accounts/old-device", {
          id: "old-device",
          userType: ["entrant", "owner"],
          notificationsEnabled: true,
        })
      ).status,
      403,
  );
  assert.equal((await read(reviewer, "unlisted/example")).status, 403);
  assert.deepEqual(
      (await call("getAccountDisplayName", outsider, {campsiteId})).result,
      {name: "owner"},
  );
  assert.equal((await read(null, "campsite/" + campsiteId)).status, 200);
  assert.equal((await read(null, "lottery/" + emptyLotteryId)).status, 200);
  assert.equal(
      (
        await registration(
            {...outsider, accountId: reviewer.accountId},
            1,
            true,
            emptyLotteryId,
        )
      ).status,
      403,
  );
  assert.equal(
      (await registration(reviewer, 1, true, emptyLotteryId)).status,
      200,
  );
  assert.equal(
      (await registration(reviewer, 0, false, emptyLotteryId)).status,
      200,
  );
});

test("owner booking scope and server-time cancellation", async () => {
  const {owner, outsider, reviewer} = accounts;
  const secondSite = "other-site-" + suffix;
  await db.doc("campsite/" + secondSite).set({ownerID: outsider.accountId});
  const future = db.doc("reservation/future-owner-" + suffix);
  const checkedIn = db.doc("reservation/checked-in-" + suffix);
  const other = db.doc("reservation/other-owner-" + suffix);
  const soon = Timestamp.fromMillis(Date.now() + 60000);
  const later = Timestamp.fromMillis(Date.now() + 120000);
  const next = {
    accountId: reviewer.accountId,
    campsiteId,
    cancelled: false,
    bookedPeriodStart: soon,
    bookedPeriodEnd: later,
  };
  await Promise.all([
    future.set(next),
    checkedIn.set({...next, bookedPeriodStart: Timestamp.now()}),
    other.set({...next, campsiteId: secondSite}),
  ]);
  assert.equal(
      (await call("listOwnerReservations", null, {})).error.status,
      "UNAUTHENTICATED",
  );
  const list = (
    await call("listOwnerReservations", owner, {
      accountId: outsider.accountId,
    })
  ).result;
  assert.deepEqual(
      new Set(list.reservations.map((row) => row.id)),
      new Set([future.id, checkedIn.id]),
  );
  assert.equal(list.reservations[0].entrantName, "reviewer");
  assert.ok(list.serverMillis > 0);
  assert.deepEqual(
      (
        await call("listOwnerReservations", outsider, {})
      ).result.reservations.map((row) => row.id),
      [other.id],
  );
  // AI Citation [AI-43] (Bhav Saluja)
  // OpenAI, Codex, November 27, 2026. Verified owner chat's campsite scope.
  // See the team AI Documentation Google Doc.
  const scopedList = (
    await call("listOwnerReservations", owner, {campsiteId})
  ).result.reservations;
  assert.deepEqual(
      new Set(scopedList.map((row) => row.id)),
      new Set([future.id, checkedIn.id]),
  );
  assert.ok(scopedList.every(
      (row) => row.entrantAccountId === reviewer.accountId,
  ));
  assert.equal(
      (await call("listOwnerReservations", outsider, {campsiteId}))
          .error.status,
      "PERMISSION_DENIED",
  );
  assert.equal((await read(owner, other.path)).status, 403);
  assert.equal((await read(owner, future.path)).status, 200);
  const cancel = (actor, booking) => {
    const write = update(booking.path, {
      cancelled: true,
      status: "cancelled",
      cancelledBy: actor.accountId,
    });
    write.updateTransforms = [
      {fieldPath: "cancelledAt", setToServerValue: "REQUEST_TIME"},
    ];
    return commit(actor, [write]);
  };
  assert.equal((await cancel(outsider, future)).status, 403);
  assert.equal((await cancel(owner, other)).status, 403);
  assert.equal((await cancel(owner, checkedIn)).status, 403);
  assert.equal((await cancel(owner, future)).status, 200);
  const saved = (await future.get()).data();
  assert.equal(saved.cancelled, true);
  assert.equal(saved.cancelledBy, owner.accountId);
  assert.equal(saved.bookedPeriodStart.isEqual(soon), true);
  assert.equal(saved.cancelledAt.toMillis() < soon.toMillis(), true);
  assert.equal((await cancel(owner, future)).status, 403);
});

test("moderation preserves reviews and updates the average", async () => {
  const {reviewer, outsider, admin} = accounts;
  const campsiteId = "rated-site-" + suffix;
  const reservationId = "completed-" + suffix;
  const site = db.doc("campsite/" + campsiteId);
  const reservation = db.doc("reservation/" + reservationId);
  await site.set({
    ownerID: accounts.owner.accountId,
    rating: [4],
    averageRating: 4,
  });
  const scoreOnly = site.collection("ratings").doc(outsider.accountId);
  const scoreOnlyVersion = Timestamp.now();
  await scoreOnly.set({score: 4, updatedAt: scoreOnlyVersion});
  await reservation.set({
    ...validBooking,
    accountId: reviewer.accountId,
    campsiteId,
  });
  const data = {
    accountId: reviewer.accountId,
    campsiteId,
    reservationId,
    score: 2,
    review: "Nice trail.",
  };
  const score = site.collection("ratings").doc(reviewer.accountId);
  assert.equal(
      (await call("saveCampsiteFeedback", outsider, data)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (await patch(accounts.owner, site.path, {rating: [5]}, true)).status,
      403,
  );
  assert.equal((await patch(reviewer, score.path, {score: 5})).status, 403);
  assert.equal((await read(outsider, reservation.path)).status, 403);
  assert.equal((await read(reviewer, reservation.path)).status, 200);
  assert.deepEqual(
      (await call("saveCampsiteFeedback", reviewer, data)).result,
      {},
  );
  assert.equal((await site.get()).get("averageRating"), 3);
  const original = (await score.get()).get("updatedAt");
  const removal = {
    campsiteId,
    accountId: reviewer.accountId,
    adminAccountId: admin.accountId,
    updatedAt: {
      seconds: original.seconds,
      nanoseconds: original.nanoseconds,
    },
  };
  assert.deepEqual(
      (
        await call("saveCampsiteFeedback", reviewer, {
          accountId: reviewer.accountId,
          campsiteId,
          reservationId,
          review: "Edited review.",
        })
      ).result,
      {},
  );
  assert.equal((await score.get()).get("score"), 2);
  assert.equal(
      (await call("removeCampsiteFeedback", reviewer, removal)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (await call("removeCampsiteFeedback", admin, removal)).error.status,
      "FAILED_PRECONDITION",
  );
  assert.equal((await score.get()).get("review"), "Edited review.");
  assert.deepEqual((await site.get()).get("rating"), [4, 2]);
  const latest = (await score.get()).get("updatedAt");
  removal.updatedAt = {
    seconds: latest.seconds,
    nanoseconds: latest.nanoseconds,
  };
  assert.deepEqual(
      (await call("removeCampsiteFeedback", admin, removal)).result,
      {},
  );
  assert.deepEqual((await site.get()).get("rating"), [4]);
  assert.equal((await site.get()).get("averageRating"), 4);
  const remaining = await score.get();
  assert.equal(remaining.get("score"), undefined);
  assert.equal(remaining.get("review"), "Edited review.");
  assert.equal(remaining.get("reservationId"), reservationId);
  assert.equal((await read(reviewer, score.path)).status, 200);
  assert.equal(
      (await call("removeCampsiteFeedback", admin, removal)).error.status,
      "FAILED_PRECONDITION",
  );
  assert.equal((await score.get()).get("review"), "Edited review.");
  assert.equal((await site.get()).get("averageRating"), 4);

  assert.deepEqual(
      (await call("removeCampsiteFeedback", admin, {
        ...removal,
        accountId: outsider.accountId,
        updatedAt: {
          seconds: scoreOnlyVersion.seconds,
          nanoseconds: scoreOnlyVersion.nanoseconds,
        },
      })).result,
      {},
  );
  assert.equal((await scoreOnly.get()).exists, false);
  const unrated = await site.get();
  assert.deepEqual(unrated.get("rating"), []);
  assert.equal(unrated.get("averageRating"), undefined);
});

test("verified admin can moderate managed campsite images", async () => {
  const {admin, outsider, otherBackup} = accounts;
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
  for (const user of [outsider, otherBackup]) {
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
      (await patch(admin, site.path, {ownerID: admin.accountId}, true)).status,
      403,
  );
});

test("profile cleanup is atomic and can be retried", async () => {
  const {deleting, outsider} = accounts;
  const accountId = deleting.accountId;
  const profile = db.doc("accounts/" + accountId);
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
      (await call("deleteProfile", outsider, args)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (
        await fetch(firestore + "/" + profile.path, {
          method: "DELETE",
          headers: {Authorization: "Bearer " + deleting.idToken},
        })
      ).status,
      403,
  );

  // An inconsistent score makes cleanup fail before any state is committed.
  assert.equal(
      (await call("deleteProfile", deleting, args)).error.status,
      "FAILED_PRECONDITION",
  );
  assert.equal((await profile.get()).exists, true);
  assert.equal((await profile.get()).get("deletionPending"), undefined);
  assert.equal((await entry.get()).exists, true);
  assert.equal((await future.get()).get("cancelled"), false);
  assert.equal(
      (
        await patch(
            deleting,
            profile.path,
            {notificationsEnabled: false},
            true,
        )
      ).status,
      200,
  );

  await site.update({rating: [5, 4], averageRating: 4.5});
  assert.ok((await call("deleteProfile", deleting, args)).result);
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
      "listLotteryNotificationRecipients",
      owner,
      {lotteryId},
  );
  assert.deepEqual(
      new Set(recipients.result.map((recipient) => recipient.accountId)),
      new Set([allowed.accountId, accounts.optedOut.accountId]),
  );
  for (const invalid of [{message: "  \n"}, {allEntrants: false}]) {
    assert.equal(
        (await send(owner, invalid)).error.status,
        "INVALID_ARGUMENT",
    );
  }
  const empty = await send(owner, {lotteryId: emptyLotteryId});
  assert.equal(empty.error.status, "FAILED_PRECONDITION");
  assert.equal(
      (
        await send(owner, {
          allEntrants: false,
          recipientIds: [accounts.outsider.accountId],
        })
      ).error.status,
      "FAILED_PRECONDITION",
  );

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
      "listReceivedLotteryNotifications",
      accounts.outsider,
      {accountId: allowed.accountId},
  );
  assert.equal(spoof.error.status, "PERMISSION_DENIED");
  const path = `${firestore}/lotteryNotifications/${messages[0].id}`;
  const response = await fetch(path, {
    headers: {Authorization: `Bearer ${allowed.idToken}`},
  });
  assert.equal(response.status, 403);
});

test("verified winner promotes first eligible backup in order", async () => {
  const {winner, otherBackup, outsider, allowed, late} = accounts;
  const id = "completed-" + suffix;
  const missing = "missing-" + suffix;
  const available = [
    allowed.accountId,
    otherBackup.accountId,
    late.accountId,
  ];
  const backupIds = [missing, ...available];
  const event = db.doc("lottery/" + id);
  const result = db.doc("lotteryDrawResults/" + id);
  const registrationRef = (accountId) =>
    db.doc("waitingList/" + id + "_" + accountId);
  await event.set({campsiteId, numberOfWinners: 1, drawCompleted: true});
  await result.set({
    winnerIds: [winner.accountId],
    backupIds,
    winnerCount: 1,
    backupCount: backupIds.length,
  });
  await registrationRef(winner.accountId).set({
    accountId: winner.accountId,
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
  const args = {lotteryId: id, accountId: winner.accountId};
  assert.equal(
      (await call("declineLotteryOffer", otherBackup, args)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (await call("declineLotteryOffer", outsider, args)).error.status,
      "PERMISSION_DENIED",
  );
  assert.equal(
      (await commit(winner, [update(result.path, {backupIds: []})])).status,
      403,
  );
  assert.equal(
      (
        await commit(winner, [
          update(registrationRef(winner.accountId).path, {
            drawStatus: "DECLINED",
          }),
        ])
      ).status,
      403,
  );
  assert.deepEqual((await result.get()).get("backupIds"), backupIds);
  assert.deepEqual(
      (await call("declineLotteryOffer", winner, args)).result,
      {},
  );
  const updated = (await result.get()).data();
  assert.deepEqual(updated.winnerIds, [available[0]]);
  assert.deepEqual(updated.backupIds, [
    missing,
    available[1],
    available[2],
  ]);
  assert.deepEqual(updated.declinedIds, [winner.accountId]);
  assert.equal(
      (await registrationRef(winner.accountId).get()).get("drawStatus"),
      "DECLINED",
  );
  assert.equal(
      (await registrationRef(allowed.accountId).get()).get("drawStatus"),
      "WINNER",
  );
  assert.equal(
      (await registrationRef(otherBackup.accountId).get()).get(
          "backupPosition",
      ),
      2,
  );
  assert.equal(
      (await registrationRef(late.accountId).get()).get("backupPosition"),
      3,
  );
  assert.deepEqual(
      (await call("declineLotteryOffer", allowed, {lotteryId: id})).result,
      {},
  );
  assert.deepEqual((await result.get()).get("winnerIds"), [
    otherBackup.accountId,
  ]);
});

test("everyone uses current membership and honors opt-outs", async () => {
  const owner = accounts.owner;
  const allowed = accounts.allowed;
  const optedOut = accounts.optedOut;
  const late = accounts.late;
  const previousInbox = await inbox(allowed);
  assert.equal((await registration(allowed, 1, false)).status, 200);
  assert.equal((await registration(late, 2, true)).status, 200);
  await db
      .doc(`waitingList/${lotteryId}_${late.accountId}`)
      .update({drawStatus: "BACKUP"});
  assert.equal(
      (
        await send(owner, {
          allEntrants: false,
          recipientIds: [allowed.accountId],
        })
      ).error.status,
      "FAILED_PRECONDITION",
  );
  const message = "Use the north gate this weekend.";
  assert.deepEqual(await send(owner, {message}), {
    result: {intended: 2, delivered: 1},
  });
  assert.equal((await inbox(late)).result[0].message, message);
  assert.deepEqual(await inbox(allowed), previousInbox);
  assert.deepEqual((await inbox(optedOut)).result, []);

  const saved = await db
      .collection("lotteryNotifications")
      .where("lotteryId", "==", lotteryId)
      .get();
  const records = saved.docs
      .map((document) => document.data())
      .filter((data) => data.message === message);
  assert.equal(records.length, 1);
  const [record] = records;
  assert.ok(record.sentAt instanceof Timestamp);
  assert.equal(record.senderAccountId, owner.accountId);
  assert.deepEqual(
      new Set(record.intendedRecipientIds),
      new Set([late.accountId, optedOut.accountId]),
  );
  assert.deepEqual(record.deliveredRecipientIds, [late.accountId]);
  assert.equal(
      (
        await patch(
            optedOut,
            `accounts/${optedOut.accountId}`,
            {
              notificationsEnabled: true,
            },
            true,
        )
      ).status,
      200,
  );
  assert.deepEqual((await inbox(optedOut)).result, []);
  assert.equal(
      (
        await patch(
            late,
            `accounts/${late.accountId}`,
            {
              notificationsEnabled: false,
            },
            true,
        )
      ).status,
      200,
  );
  assert.deepEqual((await inbox(late)).result, []);
});

// AI Citation [AI-40]
// OpenAI, Codex, US 02.10.02, 2026-11-27. Generated history emulator tests.
// See Bhav's entry in the team's AI Documentation Google Doc.
test("sent history returns only the owner's records in newest-first order",
    async () => {
      const {owner, allowed, optedOut, outsider, admin} = accounts;
      const saved = db.collection("lotteryNotifications");
      const history = "listSentLotteryNotifications";
      const details = "getSentLotteryNotification";
      const newest = {
        senderAccountId: owner.accountId,
        lotteryId,
        lotteryName: "Whispering Pines",
        campsiteId,
        message: "Check the updated arrival instructions.",
        allEntrants: true,
        intendedRecipientIds: [
          allowed.accountId, optedOut.accountId, `auth_deleted-${suffix}`,
        ],
        deliveredRecipientIds: [allowed.accountId],
        sentAt: new Timestamp(2, 2000),
      };
      const newestId = `history-z-new-${suffix}`;
      const oldestId = `history-a-old-${suffix}`;
      const privateId = `history-private-${suffix}`;
      await saved.doc(newestId).set(newest);
      await saved.doc(oldestId).set({
        ...newest,
        message: "The south gate is closed.",
        sentAt: new Timestamp(2, 1000),
      });
      await saved.doc(privateId).set({
        ...newest,
        senderAccountId: outsider.accountId,
      });

      const summary = {
        id: newestId,
        lotteryName: newest.lotteryName,
        message: newest.message,
        sentAtMillis: 2000,
        intended: 3,
        delivered: 1,
      };
      const {result: sent} = await call(history, owner, {});
      const ids = sent.map((notification) => notification.id);
      assert.deepEqual(ids.slice(-2), [newestId, oldestId]);
      assert.equal(ids.includes(privateId), false);
      assert.deepEqual(sent.at(-2), summary);
      assert.deepEqual(await call(details, owner, {notificationId: newestId}), {
        result: {
          ...summary,
          recipients: [
            {name: "allowed", delivered: true},
            {name: "optedOut", delivered: false},
            {name: "Unnamed entrant", delivered: false},
          ],
        },
      });

      assert.deepEqual(await call(history, admin, {}), {result: []});
      assert.equal((await call(history, null, {})).error.status,
          "UNAUTHENTICATED");
      assert.equal((await call(history, outsider, {
        accountId: owner.accountId,
      })).error.status, "PERMISSION_DENIED");
      for (const account of [outsider, admin]) {
        assert.equal((await call(details, account, {
          notificationId: newestId,
        })).error.status, "NOT_FOUND");
        assert.equal((await read(account,
            `lotteryNotifications/${newestId}`)).status, 403);
      }
      assert.equal((await read(owner, `lotteryNotifications/${newestId}`))
          .status, 403);
    });

// AI Citation [AI-41]
// OpenAI, Codex, US 02.10.02, 2026-11-27. Generated failure and retry tests.
// See Bhav's entry in the team's AI Documentation Google Doc.
test("records a failed send without delivering or exposing unrelated profiles",
    async () => {
      const {owner, allowed, outsider} = accounts;
      const id = `history-lottery-${suffix}`;
      const message = "Please use the marked parking bays.";
      const draft = {
        lotteryId: id,
        message,
        requestId: randomUUID(),
        allEntrants: false,
        recipientIds: [allowed.accountId, outsider.accountId],
      };
      await db.doc(`lottery/${id}`).set({
        lotteryName: "North Pines",
        campsiteId,
      });
      await db.doc(`waitingList/${id}_${allowed.accountId}`).set({
        lotteryId: id,
        accountId: allowed.accountId,
      });
      const previousInbox = await inbox(allowed);

      const {error} = await send(owner, draft);
      assert.equal(error.status, "FAILED_PRECONDITION");
      assert.equal(error.details.recordedFailure, true);
      assert.deepEqual(await send(owner, draft), {error});
      assert.equal((await send(owner, {...draft, message: "Changed"}))
          .error.status, "ALREADY_EXISTS");

      const history = "listSentLotteryNotifications";
      const {result} = await call(history, owner, {});
      const entries = result.filter((row) => row.message === message);
      assert.equal(entries.length, 1);
      const [failure] = entries;
      assert.equal(failure.failureReason, error.message);
      assert.equal(failure.delivered, 0);
      const details = "getSentLotteryNotification";
      const detail = await call(details, owner, {notificationId: failure.id});
      assert.deepEqual(detail.result.recipients, [
        {name: "allowed", delivered: false},
      ]);
      assert.deepEqual(await inbox(allowed), previousInbox);
      assert.equal((await call(details, outsider, {
        notificationId: failure.id,
      })).error.status, "NOT_FOUND");

      await db.doc(`waitingList/${id}_${outsider.accountId}`).set({
        lotteryId: id,
        accountId: outsider.accountId,
      });
      const delivered = {result: {intended: 2, delivered: 2}};
      assert.deepEqual(await send(owner, draft), delivered);
      assert.deepEqual(await send(owner, draft), delivered);
      const updated = (await call(history, owner, {})).result
          .filter((row) => row.message === message);
      assert.equal(updated.length, 2);
      assert.equal(updated[0].delivered, 2);
      assert.deepEqual(updated[1], failure);
    });

test("recovers failed transactions and preserves sends with a lost response",
    async () => {
      const {owner, allowed} = accounts;
      const id = `recovery-lottery-${suffix}`;
      await db.doc(`lottery/${id}`).set({
        lotteryName: "West Pines",
        campsiteId,
      });
      await db.doc(`waitingList/${id}_${allowed.accountId}`).set({
        lotteryId: id,
        accountId: allowed.accountId,
      });
      for (const committed of [false, true]) {
        let first = true;
        const client = {
          collection: (path) => db.collection(path),
          runTransaction: async (action) => {
            if (!first) {
              return db.runTransaction(action);
            }
            first = false;
            if (committed) {
              await db.runTransaction(action);
            }
            throw new Error("Simulated loss of the transaction response");
          },
        };
        const message = committed ? "Bring a flashlight." : "Pack out trash.";
        const request = {
          auth: {uid: owner.localId},
          data: {
            accountId: owner.accountId,
            lotteryId: id,
            message,
            requestId: randomUUID(),
            allEntrants: true,
            recipientIds: [],
          },
        };
        const {sendLotteryNotification: sendAttempt} =
          lotteryNotifications(client);
        if (committed) {
          assert.deepEqual(await sendAttempt.run(request), {
            intended: 1, delivered: 1,
          });
        } else {
          await assert.rejects(sendAttempt.run(request), {
            code: "failed-precondition",
            details: {recordedFailure: true},
          });
        }
        const history = await call("listSentLotteryNotifications", owner, {});
        const entries = history.result.filter((row) => row.message === message);
        assert.equal(entries.length, 1);
        assert.equal(Boolean(entries[0].failureReason), !committed);
        assert.equal(entries[0].delivered, committed ? 1 : 0);
      }
    });

// AI Citation [AI-42]
// OpenAI, Codex, US 02.10.02, 2026-11-27. Tested new and legacy history names.
// See Bhav's entry in the team's AI Documentation Google Doc.
test("sent names survive profile changes and older records still look them up",
    async () => {
      const {owner, allowed, optedOut} = accounts;
      const id = `history-names-${suffix}`;
      const message = "The gate opens at noon.";
      const allowedRef = db.doc(`accounts/${allowed.accountId}`);
      const optedOutRef = db.doc(`accounts/${optedOut.accountId}`);
      const originalProfiles = await db.getAll(allowedRef, optedOutRef);
      await db.doc(`lottery/${id}`).set({
        lotteryName: "East Pines",
        campsiteId,
      });
      for (const {accountId} of [allowed, optedOut]) {
        await db.doc(`waitingList/${id}_${accountId}`).set({
          lotteryId: id,
          accountId,
        });
      }

      try {
        await allowedRef.update({
          name: " Taylor North ",
          notificationsEnabled: true,
        });
        await optedOutRef.update({
          name: "Morgan Lake",
          notificationsEnabled: false,
        });
        assert.deepEqual(await send(owner, {lotteryId: id, message}), {
          result: {intended: 2, delivered: 1},
        });
        const history = await call("listSentLotteryNotifications", owner, {});
        const notification = history.result.find(
            (row) => row.message === message,
        );
        const saved = await db.collection("lotteryNotifications")
            .doc(notification.id).get();
        const {recipientNames, ...legacyRecord} = saved.data();
        assert.deepEqual(recipientNames, {
          [allowed.accountId]: "Taylor North",
          [optedOut.accountId]: "Morgan Lake",
        });
        const legacyId = `legacy-names-${suffix}`;
        await db.doc(`lotteryNotifications/${legacyId}`).set(legacyRecord);

        await allowedRef.update({name: "Taylor West"});
        await optedOutRef.delete();
        const details = "getSentLotteryNotification";
        const current = await call(details, owner, {
          notificationId: notification.id,
        });
        const legacy = await call(details, owner, {notificationId: legacyId});
        assert.deepEqual(new Set(current.result.recipients), new Set([
          {name: "Taylor North", delivered: true},
          {name: "Morgan Lake", delivered: false},
        ]));
        assert.deepEqual(new Set(legacy.result.recipients), new Set([
          {name: "Taylor West", delivered: true},
          {name: "Unnamed entrant", delivered: false},
        ]));
      } finally {
        for (const profile of originalProfiles) {
          await profile.ref.set(profile.data());
        }
      }
    });
