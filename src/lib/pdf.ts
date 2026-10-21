import { PDFDocument, StandardFonts, degrees, rgb } from 'pdf-lib';

export class UserError extends Error {}

export const errMsg = (e: unknown): string =>
  e instanceof UserError ? e.message : 'Something went wrong. ' + (e instanceof Error ? e.message : '');

export async function loadPdfLib(bytes: Uint8Array): Promise<PDFDocument> {
  try {
    return await PDFDocument.load(bytes);
  } catch (e) {
    const msg = String((e as Error)?.message ?? e);
    if (/encrypt/i.test(msg)) {
      throw new UserError('This PDF is password-protected or encrypted. Unlocking is not supported in this app.');
    }
    throw new UserError('This PDF is damaged or could not be read.');
  }
}

/** "1-3, 5" -> [[0,1,2],[4]] (0-based, one group per comma part) */
export function parseRangeGroups(input: string, max: number): number[][] {
  const parts = input.split(',').map((s) => s.trim()).filter(Boolean);
  if (!parts.length) throw new UserError('Enter page numbers, for example 1-3, 5, 8-10.');
  return parts.map((p) => {
    const m = /^(\d+)(?:\s*-\s*(\d+))?$/.exec(p);
    if (!m) throw new UserError(`"${p}" is not a valid page range.`);
    const a = Number(m[1]);
    const b = m[2] ? Number(m[2]) : a;
    if (a < 1 || b < 1 || a > max || b > max) throw new UserError(`"${p}" is outside this document (pages 1–${max}).`);
    if (a > b) throw new UserError(`"${p}" is backwards. Write it as ${b}-${a}.`);
    const g: number[] = [];
    for (let i = a; i <= b; i++) g.push(i - 1);
    return g;
  });
}

export function parseRanges(input: string, max: number): number[] {
  return Array.from(new Set(parseRangeGroups(input, max).flat()));
}

export async function mergePdfs(list: Uint8Array[]): Promise<Uint8Array> {
  if (list.length < 2) throw new UserError('Add at least two PDF files to merge.');
  const out = await PDFDocument.create();
  for (const b of list) {
    const src = await loadPdfLib(b);
    const pages = await out.copyPages(src, src.getPageIndices());
    pages.forEach((p) => out.addPage(p));
  }
  return out.save();
}

async function extractFrom(src: PDFDocument, pages: number[]): Promise<Uint8Array> {
  if (!pages.length) throw new UserError('No pages selected.');
  const out = await PDFDocument.create();
  const copied = await out.copyPages(src, pages);
  copied.forEach((p) => out.addPage(p));
  return out.save();
}

export async function extractPages(bytes: Uint8Array, pages: number[]): Promise<Uint8Array> {
  return extractFrom(await loadPdfLib(bytes), pages);
}

export async function deletePages(bytes: Uint8Array, remove: number[]): Promise<Uint8Array> {
  const src = await loadPdfLib(bytes);
  const rm = new Set(remove);
  const keep = src.getPageIndices().filter((i) => !rm.has(i));
  if (!keep.length) throw new UserError('You cannot delete every page of the document.');
  return extractFrom(src, keep);
}

export function chunkPages(total: number, n: number): number[][] {
  if (!Number.isInteger(n) || n < 1) throw new UserError('Pages per file must be a whole number of 1 or more.');
  const out: number[][] = [];
  for (let i = 0; i < total; i += n) {
    out.push(Array.from({ length: Math.min(n, total - i) }, (_, k) => i + k));
  }
  return out;
}

export async function splitEvery(bytes: Uint8Array, n: number): Promise<Uint8Array[]> {
  const src = await loadPdfLib(bytes);
  const groups = chunkPages(src.getPageCount(), n);
  if (groups.length < 2) throw new UserError('That would produce only one file. Use a smaller number.');
  const outs: Uint8Array[] = [];
  for (const g of groups) outs.push(await extractFrom(src, g));
  return outs;
}

export interface PageEdit { src: number; rot: number }

export async function applyPageEdits(bytes: Uint8Array, edits: PageEdit[]): Promise<Uint8Array> {
  if (!edits.length) throw new UserError('The document must keep at least one page.');
  const src = await loadPdfLib(bytes);
  const out = await PDFDocument.create();
  for (const e of edits) {
    const [p] = await out.copyPages(src, [e.src]);
    p.setRotation(degrees((((p.getRotation().angle + e.rot) % 360) + 360) % 360));
    out.addPage(p);
  }
  return out.save();
}

function hexColor(c: string) {
  const m = /^#?([0-9a-f]{6})$/i.exec(c);
  const v = m ? parseInt(m[1], 16) : 0;
  return rgb(((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255);
}

export interface WatermarkOptions {
  text: string; size: number; color: string; opacity: number;
  position: 'center' | 'top' | 'bottom'; diagonal: boolean; margin: number;
  pages: number[] | null;
}

export async function addWatermark(bytes: Uint8Array, o: WatermarkOptions): Promise<Uint8Array> {
  if (!o.text.trim()) throw new UserError('Enter the watermark text.');
  const doc = await loadPdfLib(bytes);
  const font = await doc.embedFont(StandardFonts.HelveticaBold);
  let w: number;
  try { w = font.widthOfTextAtSize(o.text, o.size); }
  catch { throw new UserError('Watermark text supports Latin characters only (standard PDF font).'); }
  const h = o.size * 0.35;
  const theta = o.diagonal ? 45 : 0;
  const rad = (theta * Math.PI) / 180;
  for (const i of o.pages ?? doc.getPageIndices()) {
    const page = doc.getPage(i);
    const { width, height } = page.getSize();
    const cx = width / 2;
    const cy = o.position === 'center' ? height / 2 : o.position === 'top' ? height - o.margin - o.size : o.margin + o.size;
    page.drawText(o.text, {
      x: cx - (w / 2) * Math.cos(rad) + h * Math.sin(rad),
      y: cy - (w / 2) * Math.sin(rad) - h * Math.cos(rad),
      size: o.size, font, color: hexColor(o.color), opacity: o.opacity, rotate: degrees(theta),
    });
  }
  return doc.save();
}

export interface PageNumberOptions {
  format: string; size: number; color: string; margin: number; start: number;
  position: 'bottom-center' | 'bottom-left' | 'bottom-right' | 'top-center' | 'top-left' | 'top-right';
  pages: number[] | null;
}

export async function addPageNumbers(bytes: Uint8Array, o: PageNumberOptions): Promise<Uint8Array> {
  const doc = await loadPdfLib(bytes);
  const font = await doc.embedFont(StandardFonts.Helvetica);
  const total = doc.getPageCount();
  const [v, hz] = o.position.split('-');
  for (const i of o.pages ?? doc.getPageIndices()) {
    const page = doc.getPage(i);
    const { width, height } = page.getSize();
    const label = o.format.replace('{n}', String(i + o.start)).replace('{total}', String(total));
    const w = font.widthOfTextAtSize(label, o.size);
    const x = hz === 'left' ? o.margin : hz === 'right' ? width - o.margin - w : (width - w) / 2;
    const y = v === 'top' ? height - o.margin - o.size : o.margin;
    page.drawText(label, { x, y, size: o.size, font, color: hexColor(o.color) });
  }
  return doc.save();
}

/** x/y = centre of the image as a fraction of the page (origin top-left). */
export async function placeImage(
  bytes: Uint8Array, png: Uint8Array, pageIndex: number, xFrac: number, yFrac: number, wFrac: number,
): Promise<Uint8Array> {
  const doc = await loadPdfLib(bytes);
  const page = doc.getPage(pageIndex);
  if (page.getRotation().angle % 360 !== 0) {
    throw new UserError('This page is rotated. Fix its rotation in Rotate Pages first, then sign.');
  }
  const img = await doc.embedPng(png);
  const { width, height } = page.getSize();
  const w = wFrac * width;
  const h = (w * img.height) / img.width;
  page.drawImage(img, { x: xFrac * width - w / 2, y: height - yFrac * height - h / 2, width: w, height: h });
  return doc.save();
}

export async function optimizeStructure(bytes: Uint8Array): Promise<Uint8Array> {
  const doc = await loadPdfLib(bytes);
  return doc.save({ useObjectStreams: true });
}

export async function buildPdfFromJpegs(items: { bytes: Uint8Array; w: number; h: number }[]): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  for (const it of items) {
    const img = await doc.embedJpg(it.bytes);
    const page = doc.addPage([it.w, it.h]);
    page.drawImage(img, { x: 0, y: 0, width: it.w, height: it.h });
  }
  return doc.save();
}
