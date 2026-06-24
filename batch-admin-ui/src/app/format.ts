/** Small presentation helpers shared by the components. */

export function statusClass(status: string | null | undefined): string {
  switch ((status ?? '').toUpperCase()) {
    case 'COMPLETED':
      return 'badge badge-ok';
    case 'STARTED':
    case 'STARTING':
    case 'STOPPING':
      return 'badge badge-run';
    case 'FAILED':
    case 'ABANDONED':
      return 'badge badge-fail';
    case 'STOPPED':
      return 'badge badge-warn';
    default:
      return 'badge badge-idle';
  }
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms == null) {
    return '—';
  }
  if (ms < 1000) {
    return `${ms} ms`;
  }
  const seconds = ms / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1)} s`;
  }
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return `${minutes}m ${rest}s`;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return date.toLocaleString();
}

export function paramsToText(params: Record<string, string> | null | undefined): string {
  if (!params) {
    return '';
  }
  return Object.entries(params)
    .map(([k, v]) => `${k}=${v}`)
    .join(', ');
}
