# Firebase setup after the reset

1. Enable Anonymous sign-in in the approved Firebase project. At startup the app
   creates or loads `accounts/auth_<uid>`. Bookings, campsites, and waitlists use
   this same Auth profile ID.
2. Release the Firestore rules, ratings index, Storage rules, and callable Functions
   with the compatible Android build. Grant the Storage rules access to the default
   Firestore database for campsite ownership checks. Retire the old
   `resolveAccount` function if it was deployed.
3. Grant administrators the `admin: true` Firebase Auth custom claim through
   trusted operator access. Preserve existing claims. Restart the app to refresh
   the token and display the Admin role.
4. Check the release with fresh Auth accounts. Verify profile photos and deletion,
   scoped owner bookings, the check-in cutoff, feedback moderation, and lottery
   notification delivery and opt-out.

Bookings are created by a trusted writer. Owner reservations use current campsite
ownership and expose the fields needed by the screen. Firestore permits cancellation
only before `bookedPeriodStart` using server time. Cancellation keeps the reservation
record and records the actor and time.

PR #141's confirmation sends the displayed rating version. The callable checks that
version and removes feedback and updates the campsite aggregate in one transaction.

## Emulator verification

From `code/`, install dependencies with `npm --prefix functions ci`. Run the secured emulator suite with this command.

```text
firebase emulators:exec --config firebase.notifications.test.json --project demo-zephyr-notifications --only auth,firestore,functions,storage "node --test functions/test/lotteryNotifications.integration.test.js"
```

Use the same Firebase demo configuration for Android's
`LotteryNotificationNavigationTest`. Reverse ports 8082, 9099 and 5001 and pass
`notificationEmulator=true`. Provide an ignored demo `app/google-services.json`
with a placeholder API key in the Firebase format.

The Android `RatingModelTest` and `ReservationOfferIntegrationTest` use the
fixture rules in `firebase.test.json` and project `demo-zephyr`. Use the same ports
and pass `ratingFirestoreEmulator=true` and `reservationFirestoreEmulator=true`.
