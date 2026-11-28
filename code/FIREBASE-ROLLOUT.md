# Firebase account rollout

The Android app signs in with Firebase Auth at startup. New profiles use `auth_<uid>`.
For each of the 15 existing profiles, keep its current Firestore document ID and all
booking, campsite and waitlist references to that ID. The app looks up an operator-managed
binding before reading the private profile. The old Android ID only helps the app locate
a candidate for enrollment.

1. Confirm the original account owner via the team’s independent roster and a known
   contact channel or an in-person check. Ask that person to open the enrollment screen
   on their authenticated installation and send the displayed Firebase UID through the
   same channel. The notification-enabled legacy build shows the account ID and UID
   in **Entrant → Notifications** when linking is required. On a replacement device,
   use **Link existing profile** on the updated app's role screen. The independent
   check establishes ownership. Account IDs, UIDs, and profile contact fields identify
   the records involved.
2. With trusted Firebase operator access, confirm the UID exists in the project's Auth
   users. Check that `notificationAccountLinks` has no other document with that UID and
   that the original account document exists. Set
   `notificationAccountLinks/{originalAccountId}` to `{ uid: "<verified UID>" }`.
   Keep booking records and campsite ownership fields intact. Verify a returning app
   session resolves to the original ID and can open My Trips. Treat any newly created
   empty Auth profile on a replacement device as separate data. Check for activity
   before removing a duplicate profile.
3. Confirm admin assignments independently. Use the Firebase Admin SDK to set the
   `admin: true` custom claim while preserving any other existing claims. Verify each
   admin's legacy binding and refresh their app session. To replace a lost installation,
   repeat verification and replace the UID on the original link. For admins, also
   revoke the old UID's admin claim and disable or revoke its Auth session.
4. Stage the ratings index and the `resolveAccount` and `getAccountDisplayName` callables
   first. Collect the UID from the existing build where available and use the updated
   build to pilot enrollment and confirm original account IDs and bookings. Keep the
   current app and rules available during enrollment. Tell users when the updated app
   and protected write endpoints will switch over. Validate stored booking ownership
   and rating aggregates before using older records as proof of a completed stay.
5. In the coordinated switch, publish the Firestore rules before exposing the new
   mutation callables. Coordinate the Storage rules on the app's image bucket and
   grant the Storage-to-Firestore rules access for the default database.
   Verify the updated Android build against those services. Old app versions use direct
   profile, offer and rating writes and must be retired at the switch. Keep an operator
   available to complete any deferred linkage without changing existing IDs.

With these rules, private profile and booking reads require the owning Firebase identity
or an authorized administrator. Campsite and lottery browsing, authenticated rating
and waitlist reads, and the campsite owner display-name lookup remain available after
the legacy cutoff. The reservation reader queries by the entrant's preserved account ID.
Booking creation is a trusted writer operation. Reservation chat uses the entrant on
the saved booking and the owner on its campsite. The first open creates the
conversation, and both parties can use it after checkout while the booking remains
uncancelled. Deploy the chat rules, unread-message index, and updated
`listOwnerReservations` callable with the chat-enabled Android build.

## Owner reservation chat release (PR #153)

Use a release revision that includes [PR #153](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/pull/153)
and the callable-backed lottery app and rules from
[PR #154](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/pull/154).
The live lottery rules require `createLottery` and `updateLottery` for creation
and date changes. Confirm those callables are active and the release app uses them.

1. Export the active default Firestore release and record its ruleset ID for rollback.
   Compare its source with the release revision's `code/firestore.rules`. The
   changes for this rollout should be limited to chat. Keep the deployed lottery
   and waitlist rules, including the transaction-backed count updates. Resolve
   any other drift before publishing the full Firestore rules file.
2. Verify the existing `messages` index on `senderAccountId` (ascending) and
   `sentAt` (descending) is ready. Stage the Android build and validate it with
   the release rules in the Firebase emulators. Update only the
   `listOwnerReservations` callable so its results include both `campsiteId`
   and `entrantAccountId`. Use a checkout with LF line endings for the
   Functions predeploy lint. Run from `code/` during the coordinated release.

   ```powershell
   firebase deploy --project zephyrproject-aa324 --only functions:listOwnerReservations
   ```

3. Pause chat use for the switch and publish the reviewed Firestore rules, then
   distribute the staged build in the same release window. The old
   `reservationChatAccess` rules reject messages with the new fields. The new
   rules require both fields on every message create, so older builds must
   upgrade before sending again. Existing history needs no backfill.

   ```powershell
   firebase deploy --project zephyrproject-aa324 --only firestore:rules
   ```

4. Once the rules have propagated, reopen the updated app as the owner and
   entrant on a confirmed booking. Verify both can send, older messages remain
   readable, and an unrelated account has no access. Smoke-test lottery creation,
   date edits, and waitlist join and leave with the release build. If the
   cutover must be reversed, restore the recorded ruleset and the previous
   compatible app together. Admin Message Logs are tracked in
   [US 03.12.01](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/issues/60).

Profile deletion commits the profile, bookings, registrations, and rating changes in one
transaction. An unsuccessful transaction leaves the account usable and supports retry.
Active and upcoming stays are cancelled and detached. Past stay records retain their
dates and status. An older persisted deletion flag can be cleared by launching the app
as the verified owner. If cleanup reports an invalid rating aggregate, an operator
needs to reconcile that campsite's saved ratings before retrying.

The Auth callable and rules regression suite uses `firebase.notifications.test.json`.
For `ReservationChatModelTest`, use that configuration with
`--project demo-zephyr-notifications`, reverse ports 8082 and 9099, and pass
`chatFirestoreEmulator=true` to the Android runner.
The Android rating and decline model suites use the demo fixture rules under
`firebase.test.json`. Start the Auth, Firestore, and Functions emulators with
`--project demo-zephyr`, reverse ports 8082, 9099, and 5001, and select the
`ratingFirestoreEmulator=true` and `reservationFirestoreEmulator=true` runner
arguments for those suites. The fixture rules are only for integration setup.

### Owner privilege removal

Roll out the updated Firestore and Storage rules, account and notification callables,
scheduled lottery draw, and Android build together. The app observes the
server-written revocation flags before displaying entrant listings or enabling owner mode.
Administrators can review the reason and server timestamp in `ownerRemovalAudit`.
Campsites, lottery entries and results, and reservations remain available in Firestore.

The booking callable reads `revokedCampsiteOwners/{ownerID}` in the same transaction
that creates a reservation. Deploy it and the lottery settings callables with the
revocation callable so that each write path observes the same policy.
The Firebase integration test for owner removal uses the Auth, Firestore, Functions,
and Storage emulator configuration above.

OpenAI Codex assisted on 2026-11-28 with the US 03.07.01 admin profile UI,
owner revocation callable, Firebase rules, and regression tests.
