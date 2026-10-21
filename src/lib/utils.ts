export const PDF_MIME = 'application/pdf';
export const uid = () => Math.random().toString(36).slice(2) + Date.now().toString(36);
export const clamp = (v: number, min = 0, max = 1) => Math.min(max, Math.max(min, v));
export const stripPdf = (name: string) => name.replace(/\.pdf$/i, '');

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1048576) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1048576).toFixed(2)} MB`;
}

export function formatDate(t: number): string {
  return new Date(t).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}
