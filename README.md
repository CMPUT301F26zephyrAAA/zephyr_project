# zephyr_project

## Reservation chat setup

Enable Firebase Anonymous Authentication for the Android app. To link a verified device account,
use the Firebase UID shown in the app's chat setup message and create
`chatAccountLinks/{deviceAccountId}` with a string field `uid`. Keep each UID linked to only
one account. If a participant receives a new UID, verify their identity before replacing the link.
This revokes the old UID while keeping messages associated with the same device account.

An authorized operator verifies the booking and writes `reservationChatAccess/{reservationId}` with
`entrantAccountId`, `ownerAccountId`, and `campsiteId` as strings. Use the canonical reservation
document ID. The app creates the chat on first open. Only the Admin SDK or Firebase console should
write the account links and reservation access records.

Both participants can use chat after checkout. Cancellation blocks access and sending on both
sides. The rules read the booking's current cancellation status from `reservation/{reservationId}`.
That collection's identity and cancellation fields need authorized writes in a live deployment.

`code/firestore.chat.rules` is the scoped chat ruleset used for emulator testing. In a live
deployment, exclude `chatAccountLinks`, `reservationChatAccess`, and `reservationChat` and its
subcollections from any broader allow rules before applying these chat rules.
