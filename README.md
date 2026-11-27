# zephyr_project

## Lottery notifications (US 02.10.01)

Owners open **View Lotteries**, select their lottery, and use **Notify Entrants**. Entrants open
**Notifications** in Entrant mode to read messages. The inbox loads when opened and supports refresh.
The sender's confirmation counts the messages made available in the inbox. Opted-out accounts are
listed as intended recipients in the send record and do not receive a copy.

### Firebase setup

Enable Firebase Anonymous Authentication and deploy the notification callables from `code/functions`.
Use an authorized administrator to verify a device's app account and set up these Firestore documents.

- `notificationAccountLinks/{accountId}` with string field `uid` set to that installation's Firebase Auth
  UID. The app shows the account ID and UID when the link is required. Link a UID to only one
  app account. Replace the link after independently verifying a reinstalled device.
- `notificationLotteryOwners/{lotteryId}` with string fields `ownerAccountId` and `campsiteId`, matching
  the verified owner's lottery and campsite. Revoke or replace the grant when ownership changes.

Before deploying, exclude `notificationAccountLinks`, `notificationLotteryOwners`, and
`lotteryNotifications` and their descendants from every Firestore client allow rule.
The Functions Admin SDK manages these collections. The callables use the existing `accounts`,
`campsite`, `lottery`, and `waitingList` records, so production rules also need to protect
account preferences, campsite ownership, lottery associations, and waitlist membership from
unauthorized edits. The local emulator rules in `code/firestore.test.rules` protect the
three notification collections and leave the course's other test fixtures accessible.

### Verification

From `code/`, install the Functions dependencies with `npm --prefix functions ci`, then run:

```text
firebase emulators:exec --only auth,firestore,functions --project demo-zephyr-notifications --config firebase.test.json "node --test functions/test/lotteryNotifications.integration.test.js"
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

Entry for the team [AI Documentation wiki](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/wiki/AI-Documentation)
under Bhav Saluja, **AI-39**. Provider and tool are OpenAI, Codex. Subject is owner-to-waitlist
notifications (US 02.10.01). Date is 2026-11-27. Original prompt:

> Following AGENTS.md can you implement US 02.10.01. Make sure to follow all conventions mentioned there and cite youself
