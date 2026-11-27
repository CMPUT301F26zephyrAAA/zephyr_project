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
Booking creation is a trusted writer operation. The app's existing chat rules remain
scoped to the separately approved participants.

Profile deletion commits the profile, bookings, registrations, and rating changes in one
transaction. An unsuccessful transaction leaves the account usable and supports retry.
Active and upcoming stays are cancelled and detached. Past stay records retain their
dates and status. An older persisted deletion flag can be cleared by launching the app
as the verified owner. If cleanup reports an invalid rating aggregate, an operator
needs to reconcile that campsite's saved ratings before retrying.

The Auth callable and rules regression suite uses `firebase.notifications.test.json`.
The Android rating and decline model suites use the demo fixture rules under
`firebase.test.json`. Start the Auth, Firestore, and Functions emulators with
`--project demo-zephyr`, reverse ports 8082, 9099, and 5001, and select the
`ratingFirestoreEmulator=true` and `reservationFirestoreEmulator=true` runner
arguments for those suites. The fixture rules are only for integration setup.
