import type { StudentPointTransaction } from '../types';

export function mergePointTransactions(
  current: StudentPointTransaction[],
  incoming: StudentPointTransaction[],
) {
  const byId = new Map<number, StudentPointTransaction>();
  for (const transaction of [...current, ...incoming]) {
    byId.set(transaction.id, transaction);
  }
  return [...byId.values()].sort((left, right) => {
    const timeDifference = new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
    return timeDifference || right.id - left.id;
  });
}
