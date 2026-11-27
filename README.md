# zephyr_project

## Lottery notifications (US 02.10.01)

Owners open **View Lotteries**, select their lottery, and use **Notify Entrants**. Entrants open
**Notifications** in Entrant mode to read messages. The inbox loads when opened and supports refresh.
The sender's confirmation counts the messages made available in the inbox. Opted-out accounts are
listed as intended recipients in the send record and do not receive a copy.

### Firebase account rollout

See the [Firebase account rollout guide](code/FIREBASE-ROLLOUT.md) for legacy enrollment and cutover.

### Verification

From `code/`, install the Functions dependencies with `npm --prefix functions ci`, then run:

```text
firebase emulators:exec --only auth,firestore,functions,storage --project demo-zephyr-notifications --config firebase.notifications.test.json "node --test functions/test/lotteryNotifications.integration.test.js"
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

Entry for the team [AI Documentation](https://docs.google.com/document/d/1tokZ9zH_kofR3yHP1pHCsvjfKh9LKhI2hS_QRGLvjXE/edit?tab=t.0)
under Bhav Saluja, **AI-39**. Provider and tool are OpenAI, Codex. Subject is owner-to-waitlist
notifications (US 02.10.01). Date is 2026-11-27. Original prompt:

> Following AGENTS.md can you implement US 02.10.01. Make sure to follow all conventions mentioned there and cite youself

Follow-up prompt:

> Continue with one follow-up commit that brings the branch in sync with the deployed Firebase rules and functions, establishes Firebase Auth identity at startup for new profiles, and protects waitlist and preference writes. Preserve existing profiles and bookings. Verify the owner-to-entrant flow and opt-out using fresh accounts

Codex also generated the legacy rollout corrections and backup queue rule and tests on November 27,
2026. The request was to keep the live preference and waitlist behavior for unlinked profiles,
retain verified notification ownership, and preserve the eligible backup queue after a decline.

On November 27, 2026, OpenAI Codex reproduced and fixed two emulator regressions under **AI-39**.
It restored the legacy profile deletion flow and allowed atomic backup promotion in a legacy
lottery when the winner is unlinked and the backup uses Firebase Auth. It added regression tests
for both flows. See the team AI Documentation entry above.

On November 27, 2026, OpenAI Codex also traced the legacy account rollout and generated
the Firebase Auth binding, Firestore and Storage access changes, transactional profile
deletion and booking cleanup, lottery decline and backup promotion, and rating and
administrator moderation. It generated emulator cases covering linked, unlinked, fresh,
and admin accounts. The team AI Documentation records the request and chat link.
