export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return "—";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat("en-US", {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function formatShortDate(iso: string | null | undefined): string {
  if (!iso) {
    return "—";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

export function normalizeRatePercent(
  rate: number | null | undefined,
  numerator?: number,
  denominator?: number
): number {
  if (rate === null || rate === undefined || Number.isNaN(rate)) {
    return 0;
  }
  if (rate > 1.0001) {
    return Math.max(0, Math.min(100, rate));
  }
  if (
    typeof numerator === "number" &&
    typeof denominator === "number" &&
    denominator > 0 &&
    numerator > 0
  ) {
    const asHundred = (numerator * 100) / denominator;
    const asFraction = numerator / denominator;
    if (Math.abs(rate - asHundred) <= Math.abs(rate - asFraction)) {
      return Math.max(0, Math.min(100, rate));
    }
    return Math.max(0, Math.min(100, rate * 100));
  }
  const val = rate > 0 && rate <= 1 ? rate * 100 : rate;
  return Math.max(0, Math.min(100, val));
}

export function formatPercent(
  rate: number | null | undefined,
  numerator?: number,
  denominator?: number
): string {
  return `${normalizeRatePercent(rate, numerator, denominator).toFixed(1)}%`;
}

export function truncateHash(hash: string | null | undefined, chars = 12): string {
  if (!hash) {
    return "—";
  }
  if (hash.length <= chars) {
    return hash;
  }
  return `${hash.slice(0, chars)}…`;
}

export function formatLegalBasis(basis: string): string {
  switch (basis) {
    case "STRICTLY_NECESSARY":
      return "Strictly Necessary";
    case "CONSENT":
      return "Explicit Consent";
    case "LEGITIMATE_INTEREST_OPT_OUT":
      return "Opt-Out / Legitimate Interest";
    default:
      return basis;
  }
}
