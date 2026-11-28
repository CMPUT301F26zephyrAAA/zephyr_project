// AI Citation [AI-32], [AI-36]
// OpenAI, ChatGPT assisted with entrant eligibility validation,
// interrupted draw recovery, scheduling, and failure handling.
// AI usage and citations documented in the GitHub Wiki.
const {setGlobalOptions} = require("firebase-functions/v2");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

// Connect the Cloud Function to our Firebase project.
admin.initializeApp();

const db = admin.firestore();

// Limit how many function instances can run at once.
setGlobalOptions({maxInstances: 1});

Object.assign(exports, require("./lotteryNotifications")(db));
Object.assign(exports, require("./accountActions")(db));
Object.assign(exports, require("./sharedLotteries")(db));
Object.assign(exports, require("./lotteryActions")(db));

/**
 * Returns canonical, unique waiting-list registrations for a lottery.
 *
 * Excludes invalid account IDs, mismatched lottery IDs, noncanonical
 * document IDs, duplicate accounts, and entrants not in WAITING status.
 *
 * @param {Array<Object>} entrantDocs Firestore registration documents.
 * @param {string} lotteryId ID of the lottery.
 * @return {Array<Object>} Eligible registration candidates.
 */
function getEligibleEntrants(entrantDocs, lotteryId) {
  const eligibleEntrants = new Map();

  for (const entrantDoc of entrantDocs) {
    const entrant = entrantDoc.data();
    const accountId = entrant.accountId;

    // Exclude invalid registrations.
    if (entrant.lotteryId !== lotteryId ||
    typeof accountId !== "string" ||
    accountId.trim() === "" ||
    accountId.includes("/") ||
    accountId === "." ||
    accountId === ".." ||
    /^__.*__$/.test(accountId) ||
    Buffer.byteLength(accountId, "utf8") > 1500 ||
    entrant.drawStatus !== "WAITING") {
      continue;
    }

    // Match the registration ID used by the Android application.
    if (entrantDoc.id !== `${lotteryId}_${accountId}`) {
      continue;
    }

    // Keep only one registration per account.
    if (!eligibleEntrants.has(accountId)) {
      eligibleEntrants.set(accountId, {
        ...entrant,
        id: entrantDoc.id,
      });
    }
  }

  return Array.from(eligibleEntrants.values());
}

/**
 * Excludes entrants whose accounts are missing or pending deletion.
 *
 * Supports both standalone Firestore reads and transactional reads
 * when validating eligibility before saving lottery results.
 *
 * @param {Array<Object>} entrants Valid registration candidates.
 * @param {Object|null} transaction Optional Firestore transaction.
 * @return {Promise<Array<Object>>} Entrants with eligible accounts.
 */
async function filterEligibleAccounts(entrants, transaction = null) {
  if (entrants.length === 0) {
    return [];
  }

  const accountRefs = entrants.map(
      (entrant) => db.collection("accounts").doc(entrant.accountId),
  );

  const accountDocs = transaction ?
      await transaction.getAll(...accountRefs) :
      await db.getAll(...accountRefs);

  const eligibleAccountIds = new Set(
      accountDocs
          .filter((accountDoc) =>
            accountDoc.exists &&
            accountDoc.data().deletionPending !== true,
          )
          .map((accountDoc) => accountDoc.id),
  );

  return entrants.filter((entrant) =>
    eligibleAccountIds.has(entrant.accountId),
  );
}
/**
 * Randomly selects winners and puts the remaining entrants in backup order.
 *
 * @param {Array<Object>} entrants Eligible lottery entrants.
 * @param {number} numberOfWinners Maximum number of winners.
 * @return {{winners: Array<Object>, backups: Array<Object>}} Draw results.
 */
function selectWinners(entrants, numberOfWinners) {
  // Shuffle a copy so the original entrant list stays unchanged.
  const shuffled = [...entrants];

  for (let i = shuffled.length - 1; i > 0; i--) {
    const randomIndex = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[randomIndex]] =
        [shuffled[randomIndex], shuffled[i]];
  }

  // Make sure the winner count is not negative.
  const winnerCount = Math.max(0, numberOfWinners);

  // Pick winners and keep everyone else as backups.
  const winners = shuffled.slice(0, winnerCount);
  const backups = shuffled.slice(winnerCount);

  return {winners, backups};
}


/**
 * Claims a lottery before starting the draw.
 * This prevents two processes from drawing the same lottery.
 *
 * @param {string} lotteryId ID of the lottery.
 * @return {Promise<string|null>} Attempt ID, or null if not ready.
 */
async function claimLotteryDraw(lotteryId) {
  const lotteryRef = db.collection("lottery").doc(lotteryId);
  const resultRef = db.collection("lotteryDrawResults").doc(lotteryId);
  const attemptId = require("crypto").randomUUID();

  return db.runTransaction(async (transaction) => {
    // Read the latest lottery and results before making changes.
    const lotteryDoc = await transaction.get(lotteryRef);
    const resultDoc = await transaction.get(resultRef);

    if (!lotteryDoc.exists || resultDoc.exists) {
      return null;
    }

    const lottery = lotteryDoc.data();

    // Only claim lotteries whose registration period has ended.
    if (lottery.drawCompleted === true ||
        lottery.drawStatus !== "PENDING" ||
        !Number.isFinite(lottery.registrationEnd) ||
        lottery.registrationEnd > Date.now()) {
      return null;
    }

    // Mark this draw as processing.
    transaction.update(lotteryRef, {
      drawStatus: "PROCESSING",
      drawAttemptId: attemptId,
      drawStartedAt: Date.now(),
    });

    return attemptId;
  });
}


/**
 * Releases a lottery claim if the draw fails.
 * Only the attempt that claimed the lottery can release it.
 *
 * @param {string} lotteryId ID of the lottery.
 * @param {string} attemptId ID of the draw attempt.
 * @return {Promise<boolean>} True if the claim was released.
 */
async function releaseLotteryDraw(lotteryId, attemptId) {
  const lotteryRef = db.collection("lottery").doc(lotteryId);
  const resultRef = db.collection("lotteryDrawResults").doc(lotteryId);

  return db.runTransaction(async (transaction) => {
    // Read the lottery and results before updating anything.
    const lotteryDoc = await transaction.get(lotteryRef);
    const resultDoc = await transaction.get(resultRef);

    if (!lotteryDoc.exists || resultDoc.exists) {
      return false;
    }

    const lottery = lotteryDoc.data();

    // Only reset an unfinished draw owned by this attempt.
    if (lottery.drawStatus !== "PROCESSING" ||
        lottery.drawCompleted === true ||
        lottery.drawAttemptId !== attemptId) {
      return false;
    }

    // Allow the scheduler to retry the draw later.
    transaction.update(lotteryRef, {
      drawStatus: "PENDING",
      drawAttemptId: "",
      drawStartedAt: 0,
    });

    return true;
  });
}

/**
 * Recovers unfinished draws with expired or invalid start timestamps.
 *
 * Resets PROCESSING lotteries to PENDING when their processing
 * timeout has expired or their start timestamp is missing or invalid.
 * Preserves completed draws and draws with existing saved results.
 *
 * @param {string} lotteryId ID of the lottery.
 * @return {Promise<boolean>} True if the draw was reset.
 */
async function recoverExpiredLotteryDraw(lotteryId) {
  const lotteryRef = db.collection("lottery").doc(lotteryId);
  const resultRef = db.collection("lotteryDrawResults").doc(lotteryId);
  const timeout = 5 * 60 * 1000;

  return db.runTransaction(async (transaction) => {
    // Check the latest lottery and saved results.
    const lotteryDoc = await transaction.get(lotteryRef);
    const resultDoc = await transaction.get(resultRef);

    if (!lotteryDoc.exists || resultDoc.exists) {
      return false;
    }

    const lottery = lotteryDoc.data();

    // Only recover unfinished draws.
    if (lottery.drawStatus !== "PROCESSING" ||
        lottery.drawCompleted === true) {
      return false;
    }

    // Check whether the draw has a valid start timestamp.
    const hasValidStartTime =
        Number.isFinite(lottery.drawStartedAt) &&
        lottery.drawStartedAt > 0;

    // Do not interrupt a draw within its processing timeout.
    if (hasValidStartTime &&
        Date.now() - lottery.drawStartedAt < timeout) {
      return false;
    }

    // Reset the draw so it can be attempted again.
    transaction.update(lotteryRef, {
      drawStatus: "PENDING",
      drawAttemptId: "",
      drawStartedAt: 0,
    });

    return true;
  });
}


// Export the helper so we can test the winner selection separately.
exports.selectWinners = selectWinners;

/**
 * Atomically saves winners, backups, and completed lottery results.
 *
 * Revalidates draw ownership, registration deadlines, winner limits,
 * entrant eligibility, and the current waiting-list contents before
 * committing the draw results and updating entrant statuses.
 *
 * @param {string} lotteryId ID of the lottery.
 * @param {string} attemptId ID of the current draw attempt.
 * @param {Array<Object>} winners Selected winners.
 * @param {Array<Object>} backups Selected backups.
 * @param {number} numberOfWinners Configured winner count.
 * @return {Promise<void>} Resolves when results are saved.
 */
async function saveLotteryDraw(
    lotteryId, attemptId, winners, backups, numberOfWinners) {
  const lotteryRef = db.collection("lottery").doc(lotteryId);
  const resultRef = db.collection("lotteryDrawResults").doc(lotteryId);

  // Save the lottery results in one transaction.
  return db.runTransaction(async (transaction) => {
    // Read the latest lottery and any existing results.
    const lotteryDoc = await transaction.get(lotteryRef);
    const resultDoc = await transaction.get(resultRef);

    // Do not save results if the lottery no longer exists
    // or if another draw has already saved results.
    if (!lotteryDoc.exists || resultDoc.exists) {
      throw new Error("Lottery missing or results already exist");
    }

    const lottery = lotteryDoc.data();

    // Make sure this attempt still owns the draw.
    if (lottery.drawStatus !== "PROCESSING" ||
        lottery.drawCompleted === true ||
        lottery.drawAttemptId !== attemptId) {
      throw new Error("Lottery draw claim is no longer valid");
    }

    // Check that registration has ended and the winner count is unchanged.
    if (!Number.isFinite(lottery.registrationEnd) ||
        lottery.registrationEnd > Date.now() ||
        lottery.numberOfWinners !== numberOfWinners) {
      throw new Error("Lottery settings changed during the draw");
    }

    // Combine winners and backups for validation.
    const selectedEntrants = [...winners, ...backups];

    // Make sure each account appears only once.
    const accountIds = selectedEntrants.map(
        (entrant) => entrant.accountId,
    );

    if (new Set(accountIds).size !== accountIds.length) {
      throw new Error("Duplicate accounts in lottery draw");
    }

    // Check the configured number of winners.
    if (!Number.isInteger(numberOfWinners) ||
        numberOfWinners < 0 ||
        winners.length !== Math.min(
            numberOfWinners, selectedEntrants.length,
        )) {
      throw new Error("Invalid winner selection");
    }

    // Read the latest waiting list records.
    const entrantRefs = selectedEntrants.map(
        (entrant) => db.collection("waitingList").doc(entrant.id),
    );

    // Only load entrant documents if the lottery has registrations.
    const entrantDocs = entrantRefs.length > 0 ?
        await transaction.getAll(...entrantRefs) : [];

    // Make sure every selected entrant is still eligible.
    for (let i = 0; i < entrantDocs.length; i++) {
      const entrantDoc = entrantDocs[i];
      const selected = selectedEntrants[i];

      if (!entrantDoc.exists) {
        throw new Error("Lottery entrant no longer exists");
      }

      const entrant = entrantDoc.data();

      if (entrant.lotteryId !== lotteryId ||
          entrant.accountId !== selected.accountId ||
          entrant.drawStatus !== "WAITING" ||
          typeof entrant.accountId !== "string" ||
          entrant.accountId.trim() === "") {
        throw new Error("Lottery entrant is no longer eligible");
      }
    }

    // Recheck the eligible waiting list inside the transaction.
    const currentEntrants = await transaction.get(
        db.collection("waitingList")
            .where("lotteryId", "==", lotteryId),
    );

    const currentRegistrationCandidates = getEligibleEntrants(
        currentEntrants.docs,
        lotteryId,
    );

    const currentEligibleEntrants = await filterEligibleAccounts(
        currentRegistrationCandidates,
        transaction,
    );

    const currentEligibleIds = new Map(
        currentEligibleEntrants.map(
            (entrant) => [entrant.id, entrant.accountId],
        ),
    );

    // Verify that the same eligible registrations are present.
    if (currentEligibleEntrants.length !== selectedEntrants.length ||
        selectedEntrants.some((entrant) =>
          currentEligibleIds.get(entrant.id) !== entrant.accountId,
        )) {
      throw new Error("Eligible waiting list changed during the draw");
    }

    // Update the selected winners.
    for (const winner of winners) {
      const winnerRef = db.collection("waitingList").doc(winner.id);

      transaction.update(winnerRef, {
        drawStatus: "WINNER",
        backupPosition: 0,
      });
    }

    // Save each backup's status and position.
    for (let i = 0; i < backups.length; i++) {
      const backupRef = db.collection("waitingList").doc(backups[i].id);

      transaction.update(backupRef, {
        drawStatus: "BACKUP",
        backupPosition: i + 1,
      });
    }

    // Record when the draw finished.
    const completedAt = Date.now();

    // Save the draw summary.
    transaction.set(resultRef, {
      lotteryId: lotteryId,
      winnerIds: winners.map((winner) => winner.accountId),
      backupIds: backups.map((backup) => backup.accountId),
      winnerCount: winners.length,
      backupCount: backups.length,
      completedAt: completedAt,
    });

    // Mark the lottery as completed.
    transaction.update(lotteryRef, {
      drawCompleted: true,
      drawCompletedAt: completedAt,
      drawStatus: "COMPLETED",
      drawStartedAt: 0,
    });
  });
}


/**
 * Scheduled Cloud Function responsible for automatic lottery draws.
 *
 * Configured to run every minute independently of the Android app.
 * Recovers interrupted draws, identifies lotteries whose registration
 * periods have ended, claims each eligible lottery, randomly selects
 * winners and backups, and persists the results in Firestore.
 *
 * Handles individual lottery failures without interrupting the
 * processing of other lotteries and releases failed draw claims
 * so they can be retried during subsequent scheduled invocations.
 */
exports.automaticLotteryDraw = onSchedule(
    {
      schedule: "* * * * *",
      timeZone: "America/Edmonton",
      maxInstances: 1,
    },

    async () => {
      logger.info("Checking for lotteries ready for drawing.");

      // Get the current time in milliseconds.
      const currentTime = Date.now();

      // Find draws that were interrupted while processing.
      const processingSnapshot = await db.collection("lottery")
          .where("drawStatus", "==", "PROCESSING")
          .get();

      // Reset draws that have been stuck for too long.
      for (const document of processingSnapshot.docs) {
        try {
          const recovered = await recoverExpiredLotteryDraw(document.id);

          if (recovered) {
            logger.info(`Recovered interrupted lottery ${document.id}`);
          }
        } catch (error) {
          logger.error(
              `Failed to recover lottery ${document.id}:`,
              error,
          );
        }
      }

      // Find lotteries that are still pending.
      const snapshot = await db.collection("lottery")
          .where("drawStatus", "==", "PENDING")
          .get();

      // Check which lotteries have finished registration.
      for (const document of snapshot.docs) {
        const lottery = document.data();

        if (lottery.registrationEnd <= currentTime &&
            lottery.drawCompleted !== true) {
          logger.info(`Lottery ${document.id} is ready for drawing.`);

          // Claim this lottery before preparing the draw.
          let attemptId;

          try {
            attemptId = await claimLotteryDraw(document.id);
          } catch (error) {
            logger.error(`Failed to claim lottery ${document.id}:`, error);
            continue;
          }

          // Skip if another process has already claimed the lottery.
          if (!attemptId) {
            logger.info(`Lottery ${document.id} was already claimed.`);
            continue;
          }

          try {
            // Load all registrations for this lottery.
            const entrantsSnapshot = await db.collection("waitingList")
                .where("lotteryId", "==", document.id)
                .get();

            // Only include valid, unique waiting-list registrations.
            const registrationCandidates = getEligibleEntrants(
                entrantsSnapshot.docs,
                document.id,
            );

            const eligibleEntrants = await filterEligibleAccounts(
                registrationCandidates,
            );

            // Log excluded registrations without failing the draw.
            const excludedCount =
                entrantsSnapshot.size - eligibleEntrants.length;

            if (excludedCount > 0) {
              logger.warn(
                  `Lottery ${document.id}: ` +
                  `${excludedCount} invalid registrations excluded.`,
              );
            }

            // Check that the winner count is valid.
            if (!Number.isInteger(lottery.numberOfWinners) ||
                lottery.numberOfWinners < 0) {
              throw new Error(
                  `Invalid winner count for lottery ${document.id}`);
            }

            // Randomly select winners and backups.
            const {winners, backups} = selectWinners(
                eligibleEntrants,
                lottery.numberOfWinners,
            );

            logger.info(
                `Lottery ${document.id}: ${winners.length} winners, ` +
                `${backups.length} backups selected.`,
            );

            // Save the winners, backups, and completed lottery.
            await saveLotteryDraw(
                document.id,
                attemptId,
                winners,
                backups,
                lottery.numberOfWinners,
            );

            logger.info(`Lottery ${document.id} draw completed successfully.`);
          } catch (error) {
            logger.error(`Lottery ${document.id} draw failed:`, error);

            // Release the claim so the scheduler can retry later.
            try {
              await releaseLotteryDraw(document.id, attemptId);
            } catch (releaseError) {
              logger.error("Failed to release lottery claim:", releaseError);
            }
          }
        }
      }
    },
);
