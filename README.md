# zephyr_project

## Lottery notifications (US 02.10.01)

Owners open **View Lotteries**, select their lottery, and use **Notify Entrants**. Entrants open
**Notifications** in Entrant mode to read messages. The inbox loads when opened and supports refresh.
The sender's confirmation counts the messages made available in the inbox. Opted-out accounts are
listed as intended recipients in the send record and do not receive a copy.

### Firebase setup

The app signs in anonymously at startup. New profiles use `accounts/auth_<uid>`, so their own
waitlist and preference writes work immediately. The installed app continues to use an existing
device-ID profile and its booking references when it finds one.

For protected writes on an existing device-ID profile, an authorized administrator verifies the
profile and links its Firebase Auth UID in `notificationAccountLinks/{accountId}`. The notification
flow shows both IDs when the link is required. Reinstallation of a legacy profile requires separate
identity verification before its link is changed. The notification functions verify lottery ownership
from the campsite and lottery records.

`code/firestore.rules` starts from the deployed November 27 rules and applies the waitlist and
preference write protections. It keeps the deployed reservation and reservation-chat rules.
Coordinate an approved rules rollout with the updated Android build. Older builds use device IDs
for new profiles and omit the account marker required by protected waitlist transactions.

### Verification

From `code/`, install the Functions dependencies with `npm --prefix functions ci`, then run:

```text
firebase emulators:exec --only auth,firestore,functions --project demo-zephyr-notifications --config firebase.notifications.test.json "node --test functions/test/lotteryNotifications.integration.test.js"
```

The Android navigation test requires an ignored debug `app/google-services.json` for the
`demo-zephyr-notifications` project and package `com.example.zephyrcampsites`. Use a placeholder API
key in the standard Firebase API-key format. With the same emulators running, reverse TCP ports
`8082`, `9099`, and `5001` through ADB, then run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.zephyrcampsites.LotteryNotificationNavigationTest' '-Pandroid.testInstrumentationRunnerArguments.notificationEmulator=true'
```

### AI disclosure

OpenAI Codex generated the implementation and tests for US 02.10.01 on 2026-11-27, including
the Compose screens, Kotlin MVC code, Firebase Functions, authorization, and route wiring.
Codex also brought the deployed notification source into the branch and generated the startup Auth,
Firestore rule changes, and fresh-account verification for the follow-up on November 27, 2026.

Entry for the team [AI Documentation wiki](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/wiki/AI-Documentation)
under Bhav Saluja, **AI-39**. Provider and tool are OpenAI, Codex. Subject is owner-to-waitlist
notifications (US 02.10.01). Date is 2026-11-27. Original prompt:

> Following AGENTS.md can you implement US 02.10.01. Make sure to follow all conventions mentioned there and cite youself

Follow-up prompt:

> Continue with one follow-up commit that brings the branch in sync with the deployed Firebase rules and functions, establishes Firebase Auth identity at startup for new profiles, and protects waitlist and preference writes. Preserve existing profiles and bookings. Verify the owner-to-entrant flow and opt-out using fresh accounts
