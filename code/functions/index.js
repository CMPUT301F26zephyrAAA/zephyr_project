
const {setGlobalOptions} = require("firebase-functions/v2");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

// Connect the Cloud Function to our Firebase project.
admin.initializeApp();

const db = admin.firestore();

// Limit how many function instances can run at once.
setGlobalOptions({maxInstances: 1});


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
 * Resets a draw that has been stuck for more than five minutes.
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

    // Only recover unfinished draws that have timed out.
    if (lottery.drawStatus !== "PROCESSING" ||
        lottery.drawCompleted === true ||
        !Number.isFinite(lottery.drawStartedAt) ||
        lottery.drawStartedAt <= 0 ||
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
 * Saves the winners and backups after the lottery draw.
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

    // Check that the waiting list has not changed.
    const currentEntrants = await transaction.get(
        db.collection("waitingList")
            .where("lotteryId", "==", lotteryId),
    );

    if (currentEntrants.size !== selectedEntrants.length) {
      throw new Error("Waiting list changed during the draw");
    }
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
// Check for lotteries that need a draw every 60 minutes.
exports.automaticLotteryDraw = onSchedule(
    {
      schedule: "every 60 minutes",
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
        const recovered = await recoverExpiredLotteryDraw(document.id);

        if (recovered) {
          logger.info(`Recovered interrupted lottery ${document.id}`);
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
          const attemptId = await claimLotteryDraw(document.id);

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

            // Keep the Firestore document ID with each entrant.
            const entrants = entrantsSnapshot.docs.map((entrantDoc) => ({
              id: entrantDoc.id,
              ...entrantDoc.data(),
            }));

            // Only entrants still waiting can join the draw.
            const eligibleEntrants = entrants.filter((entrant) =>
              entrant.lotteryId === document.id &&
  typeof entrant.accountId === "string" &&
  entrant.accountId.trim() !== "" &&
  entrant.drawStatus === "WAITING",
            );

            // Check for duplicate account registrations.
            const accountIds = eligibleEntrants.map(
                (entrant) => entrant.accountId,
            );
            const hasDuplicates =
              new Set(accountIds).size !== accountIds.length;

            if (eligibleEntrants.length !== entrants.length || hasDuplicates) {
              throw new Error(
                  `Invalid waiting list for lottery ${document.id}`,
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
