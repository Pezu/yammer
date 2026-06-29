import { parseUtc, roDate } from './tz';

/**
 * Human relative time: "just now" (<1m), "x min ago" (<60m), "x hours ago" (<24h),
 * otherwise the date as "dd.MM.YYYY" (Romanian local time). The backend timestamp is
 * naive UTC, so it is parsed as UTC before any elapsed-time math.
 */
export function timeAgo(iso: string): string {
  const d = parseUtc(iso);
  if (!d) return '';
  const min = Math.floor((Date.now() - d.getTime()) / 60000);
  if (min < 1) return 'just now';
  if (min < 60) return `${min} min ago`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`;
  return roDate(d);
}
