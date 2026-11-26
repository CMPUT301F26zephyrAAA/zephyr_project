const {test} = require("node:test");
const assert = require("node:assert/strict");
const {selectWinners} = require("../index");

/**
 * Creates sample lottery entrants.
 * @param {number} count Number of entrants to create.
 * @return {Array<Object>} Sample entrants.
 */
function createEntrants(count) {
  return Array.from({length: count}, (_, index) => ({
    id: `registration-${index + 1}`,
    accountId: `entrant-${index + 1}`,
  }));
}

test("selects the configured number of winners", () => {
  const entrants = createEntrants(10);

  const {winners, backups} = selectWinners(entrants, 3);

  assert.equal(winners.length, 3);
  assert.equal(backups.length, 7);
});

test("selects all entrants when fewer than winner limit", () => {
  const entrants = createEntrants(2);

  const {winners, backups} = selectWinners(entrants, 5);

  assert.equal(winners.length, 2);
  assert.equal(backups.length, 0);
});

test("handles an empty waiting list", () => {
  const {winners, backups} = selectWinners([], 3);

  assert.deepEqual(winners, []);
  assert.deepEqual(backups, []);
});

test("handles a zero winner limit", () => {
  const entrants = createEntrants(5);

  const {winners, backups} = selectWinners(entrants, 0);

  assert.equal(winners.length, 0);
  assert.equal(backups.length, 5);
});

test("handles a negative winner limit defensively", () => {
  const entrants = createEntrants(5);

  const {winners, backups} = selectWinners(entrants, -1);

  assert.equal(winners.length, 0);
  assert.equal(backups.length, 5);
});

test("does not select the same entrant twice", () => {
  const entrants = createEntrants(20);

  const {winners, backups} = selectWinners(entrants, 8);

  const selected = [...winners, ...backups];
  const accountIds = selected.map((entrant) => entrant.accountId);

  assert.equal(selected.length, 20);
  assert.equal(new Set(accountIds).size, 20);
});

test("winners and backups do not overlap", () => {
  const entrants = createEntrants(10);

  const {winners, backups} = selectWinners(entrants, 4);

  const winnerIds = new Set(
      winners.map((entrant) => entrant.accountId),
  );

  for (const backup of backups) {
    assert.equal(winnerIds.has(backup.accountId), false);
  }
});

test("preserves every eligible entrant in the results", () => {
  const entrants = createEntrants(12);

  const {winners, backups} = selectWinners(entrants, 5);

  const originalIds = entrants
      .map((entrant) => entrant.accountId)
      .sort();

  const selectedIds = [...winners, ...backups]
      .map((entrant) => entrant.accountId)
      .sort();

  assert.deepEqual(selectedIds, originalIds);
});

test("does not modify the original entrant list", () => {
  const entrants = createEntrants(10);
  const original = structuredClone(entrants);

  selectWinners(entrants, 4);

  assert.deepEqual(entrants, original);
});

test("handles a single eligible entrant", () => {
  const entrants = createEntrants(1);

  const {winners, backups} = selectWinners(entrants, 1);

  assert.deepEqual(winners, entrants);
  assert.deepEqual(backups, []);
});
