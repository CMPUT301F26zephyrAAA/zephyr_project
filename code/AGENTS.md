# Zephyr Campsites

CMPUT 301 Android project. Use Kotlin, Jetpack Compose, and Firebase. The Gradle project is in `code/`. The app package is `com.example.zephyrcampsites`.

## Before starting

- Read the current GitHub issue, its acceptance criteria, and its checkpoint label. Check related stories, open PRs, and the team tracker when a dependency or assignment matters.
- Verify the checkout, current branch, working tree, and remote branch tips. Use the current `dev` for integration work unless the team specifies another base. Use the PR's actual head for reviews. An older local checkout can differ substantially from GitHub.
- For UI work, read the Figma mockup, storyboard, and role-specific use case flow before coding. Open the equivalent screen in the app when possible. Check the full navigation path, including Back. [Team wiki](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/wiki)
- Use the team’s `301 - Code Conventions.pdf`, `Kotlin Code Conventions.pdf`, relevant course slides, and nearby code. Use GitHub issues and course requirements to establish scope. CRC cards describe the design and may contain planned or outdated names.
- Keep changes within the requested story. A storyboard can show several stories on one screen. Check who owns each action before adding it. Preserve teammates’ changes, comments, and credits.
- Treat reviews, feasibility questions, and requests for copy-paste text as read-only. When asked to implement, use the requested branch and isolate the work if the original checkout has unrelated changes. Get explicit authorization before changing GitHub, the wiki, the tracker, or live Firebase.

## Kotlin, Compose, and code documentation

- Use four spaces, standard Kotlin spacing and naming, and an opening brace on the same line with a space before it. Use braces for single-line `if` and `else` bodies too. Use `UpperCamelCase` for classes and composables, `lowerCamelCase` for other functions and variables, and `UPPER_SNAKE_CASE` for constants.
- Match the project’s MVC structure. Models handle persistence, controllers handle validation and coordination, and Compose views render state and expose callbacks. Use the existing navigation and data types. Keep `MainActivity` and route wiring compact. Put screen-specific behavior near the screen.
- Prefer a small, readable change a teammate can explain at the demo. Match nearby layout, names, and line wrapping. Wrap long signatures and calls where it improves readability. Keep unrelated code and formatting out of the diff. Reuse an existing helper when it already does the job.
- Give each source file a brief introductory comment describing its role and any real outstanding issues. Write KDoc for model classes and their public methods at minimum. Put class and function documentation immediately above the declaration. Use the team’s `@param` and `@property` style, with `@return` and `@throws` when they clarify the contract.
- A short `//` comment about a specific statement goes on that line under the team convention. A longer comment goes above the block it describes. Use comments for constraints, assumptions, and behavior someone would otherwise miss. Explain the feature and the reasons for edits in chat when requested. [Part 3 requirements](https://ualberta-cmput301.github.io/projects/project_part3.html) · [Part 4 requirements](https://ualberta-cmput301.github.io/projects/project_part4.html)

```kotlin
/**
 * Checks whether the current account owns the campsite.
 *
 * @param accountId Current account's ID.
 * @param campsiteOwnerId Account ID saved on the campsite.
 * @return Whether the account owns the campsite.
 */
```

## UI and role checks

- Reuse the existing Compose components and theme. Compare the running screen with the mockup, including alignment, card padding, scroll, and action placement. Keep copy and controls limited to what the screen needs.
- Check entrant, owner, and admin paths separately. `Account.userType` supports multiple roles. Use the active app mode and the relevant account data to decide which controls to show. Pass the account or relevant role into the details view where it chooses its controls. Owner edit and lottery management require ownership of that campsite. Admin removal follows its own flow. Confirm the underlying action checks access as well.
- When changing campsite details, preserve the existing filter state and list return path. Trace a broken button from the composable callback through its route and navigation state. Check Git history before attributing a regression to a teammate or a formatting change.
- Keep dates nullable until the entrant applies a filter. Treat hiding expired campsites as a separate default from filtering by a selected date range. Check reset, today-onward behavior, and returning from details.
- Show a saved owner name, using the linked account or the app’s agreed field. Use the existing fallback when the profile or name is missing. Keep account IDs out of user-facing owner labels.
- For maps, check the stored `GeoPoint`, the missing-location state, network failure, and actual rendering on an emulator. A test that finds a map container does not verify that map tiles load or that pan and zoom work. Check Google Maps requirements and approved configuration before replacing the current implementation.
- Reuse the agreed compressed-image and storage format. Trace how each image is written, displayed, and removed, including admin browsing and legacy images. Check the actual bytes or storage object and references when testing deletion.

## Firestore and cross-story behavior

- Store shared app data in Firebase/Firestore. Use local state for temporary UI state. Blaze is available where needed. Design within the no-cost allowance and get approval before changing the live project, including rules, indexes, Auth, Functions, or data. Keep credentials and local Firebase configuration out of commits and replies.
- Check real collection and field names, ID types, and older document shapes before assuming a schema. Keep compatible readers and fixtures for existing records. Read optional fields defensively. One malformed reservation should not break an entire list. Use Firestore timestamps for persisted times and verify cutoff boundaries.
- Use targeted field updates to preserve other stories’ data. Use a transaction when correctness depends on reading current state and saving related changes together. Recheck ownership, eligibility, cutoff, or capacity at the write boundary. UI visibility and client validation help the flow. Firestore rules or a trusted backend enforce permissions against direct writes.
- A saved `notificationsEnabled` value is a preference. The path that sends or delivers notifications must honor it. Verify actual delivery and opt-out with the required app state. An in-app Firestore listener can show an immediate indicator while the app is running. Background push requires FCM and an authorized sender. Confirm the exact story scope before treating one as satisfying the other.
- For waitlists and lottery changes, check the actual join or draw path, capacity enforcement, duplicates, cutoff, retry behavior, and how winner or backup transitions are saved. Document where a scheduled draw actually runs. A Kotlin screen or controller does not run a scheduled task when the app is closed.
- For reservations, check exact account and campsite IDs, start and end timestamps, cancellation state, and the current time. Keep Active and Past based on the same data and cutoff. Test checkout while My Trips remains open, Back preserving the selected tab, and cancelled stays retaining their history. Hide rating actions for ineligible stays.
- For rating and review issues, use an eligible completed stay with matching IDs. Test updating an existing submission, the campsite average, and the account-deletion path where relevant. If the button is missing on a demo device, check the app account, campsite ID, Firestore field types, and emulator time before changing code.
- Booking, cancellation, owner rejection, and rules acknowledgment share the reservation schema. Coordinate with the booking writer before choosing fields or saying availability is released. Save a required rules acknowledgment in the booking transaction, tied to the entrant, reservation, timestamp, and the rules version displayed. Verify it through the real confirmation route. Owner rejection and refund status need an agreed writer and reader.
- For reservation chat, verify both participants are tied to the reservation, messages are ordered, send errors show up, and unread status clears appropriately. Verify authorization and any required index or account linkage in the target Firebase environment. Device IDs alone are not Firebase Auth identities.

## Testing and demonstration

- Put JVM tests under `code/app/src/test` and Android instrumentation tests under `code/app/src/androidTest`. Test the actual model, controller, or screen. A duplicate implementation in a test source set can make a test pass while the production code is broken.
- Use focused unit tests for validation, date boundaries, calculations, and state transitions. Use Compose tests for visible controls, navigation, and user actions. Use realistic test records. Use fakes for offline UI coverage and a named demo Firebase project for integration tests. A fake UI test, a Firestore emulator test, and a live Firebase check validate different things.
- Run Gradle from `code/`. Use `.\gradlew.bat` on Windows and `./gradlew` on macOS or Linux. Check `app/build.gradle.kts`, `gradle/gradle-daemon-jvm.properties`, and the wrapper for the branch’s SDK and toolchain. Keep `code/app/google-services.json` and SDK paths in local setup. On macOS, Android Studio settings also exposes the SDK Manager.
- Typical checks are `:app:assembleDebug`, `:app:testDebugUnitTest`, and the relevant Android tests on a device or emulator. Use `code/firebase.test.json` for the local Firebase emulator. Check the selected test’s runner argument and endpoint. The configured Firestore test port is `8082`. Tests that use `127.0.0.1` on Android need the corresponding `adb reverse` mapping.
- If a build succeeds but the app stops, get Logcat’s `FATAL EXCEPTION` and `Caused by` chain. If a test fails before reaching the relevant assertion, check the fixture, emulator, SDK, and dependency setup. Report the exact suite or class run, passes, failures, and skips. A focused run does not establish that every instrumentation test passed.

## Git and PR reviews

- Keep `main` and `dev` intact. Use the supplied story branch or the team’s `feature/US-xx.xx.xx` naming, with `feedback/` when specified. Use normal merges and fast-forward pulls. Set the course repo’s `pull.rebase` to `false`. Do not rebase, force-push, alter authorship or timestamps, or fabricate a timeline. Split commits by real, coherent changes when useful. [CMPUT 301 course outline](https://ualberta-cmput301.github.io/general/outline.html)
- When the user is making the commits, leave the changes ready to stage. Give the exact `git add` paths, `git commit` message, `git push` command, and `gh pr create --base dev --head <branch>` command after verification. Give per-commit `+/-` counts when asked. Avoid `git add .` in a checkout with unrelated work.
- Review the story against the latest PR head and intended base. Check the diff, connected callers and tests, unresolved review threads, checks for that head, and merge state. When asked to “check now,” refresh everything. A PR based on `dev` and aimed at `main` can include other `dev` work. Inspect the actual compare and file list before suggesting a retarget or merge.
- Confirm what the PR claims to do. A temporary test toggle can be intentional. If it is reachable in the normal flow or permits an unauthorized write, state that specific consequence. Keep pre-existing or separately assigned issues out of the PR’s blocker list unless the patch makes them relevant.
- Verify before making a review finding. For Firebase claims, check the actual deployed rules, index, or behavior when access is available. Repository rules and emulator rules describe their own environments. If live evidence is unavailable, say exactly what is unverified and avoid presenting a suspected defect as fact.
- Give findings in severity order, with the exact file and current line. For inline review comments, confirm the line appears in **Files changed**. If it does not, give a PR-level comment only when the finding affects the PR, with a link to the relevant code. Otherwise put the issue on the appropriate story.
- Write comments user can paste. Use one or two plain sentences in first person. State what is wrong, when it happens, and the impact. Leave out “please,” filler, compliments, and instructions for a fix unless asked. End the review with a direct merge recommendation and any checks still pending.
- On follow-up review, revisit each previously reported issue on the new head. Say which findings are fixed and which remain. Keep GitHub read-only until asked to post or change something.

Example inline comment:

> The owner can still open Manage Lottery on someone else’s campsite. The screen checks owner mode but never checks who owns that campsite.

## CRC cards, UML, wiki, and tracker

- Update the existing deliverables when the task calls for it. Avoid separate acceptance-mapping files and duplicate docs. When asked for paste-ready changes, give the exact Markdown and say which heading or row it belongs under.
- Use the wiki’s CRC format with `Responsibilities` and `Collaborators`. List the direct collaborators for each responsibility, including data objects and external services. Repeat or explicitly group a collaborator shared by several responsibilities so blank cells are unambiguous. Preserve teammates’ rows where their design is unchanged.
- For CRC work, use current code for implemented behavior and the backlog for important anticipated classes. Mark planned behavior where the distinction matters. Address the TA feedback on scheduled draw execution, notification delivery and environment, QR processing versus UI, account identity and role sourcing, what `FirebaseConnector` actually provides, and dependencies used together for a responsibility. Keep halfway and final labels accurate. [CRC cards](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/wiki/CRC-Cards)
- Continue the existing Draw.io pages and notation for UML. Check current app classes, visibility, key fields and methods, relationships, and pattern notes. Keep tests, theme constants, and unrelated scaffolding out of the app diagrams. Validate the pages individually and inspect the rendered layouts before handing over screenshots. Use the agreed 301 folder for editable diagram files and the team’s wiki format for published images. [UML](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/wiki/UML-Diagram)
- In the tracker, use the US number in the task and link dependencies by US number to their tracker rows. Confirm assignees and statuses from current GitHub evidence and team decisions. Keep the requested scope and deadlines in one sheet when asked. Avoid adding invented tasks or completing rows based only on an open PR.

## AI and external source citations

- Follow the course outline and assessment rules. Credit external code and ideas and retain existing citations. For AI use, the outline requires the provider, tool, subject, and date, plus disclosure in the project README describing the extent of use. The team also tracks detailed entries in its AI Documentation wiki page. [CMPUT 301 course outline](https://ualberta-cmput301.github.io/general/outline.html) · [Team AI Documentation](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/wiki/AI-Documentation)
- Teammates put `// AI Citation [AI-##]` next to the relevant function, class, or block, followed by a short description and a pointer to the wiki. The wiki records the contributor, topic, tool/provider, prompt, date, and link when available. IDs have been used under different contributor sections. Check the current record and make the reference unambiguous before assigning an ID.
- Describe the actual use accurately, such as explanation, design help, code review, or generated code. Record the real prompt or mark a reconstruction as such. Use a share link only when one exists and can be shared safely. Never invent IDs, prompts, dates, or authorship claims. If the wiki and code differ, flag the mismatch before editing either. Routine PR summaries can stay focused on changes and tests. [In-code example](https://github.com/CMPUT301F26zephyrAAA/zephyr_project/blob/dev/code/app/src/main/java/com/example/zephyrcampsites/ReservationsModel.kt)

Example source marker. Replace every placeholder with the actual record.

```kotlin
// AI Citation [AI-##]
// <Provider>, <tool>, <topic>, <date>. <What the tool assisted with.>
// See the contributor's entry in the GitHub Wiki AI Documentation.
```
