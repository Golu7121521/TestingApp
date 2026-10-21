import * as pdfjs from 'pdfjs-dist';
import type { PDFDocumentProxy, PDFPageProxy } from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.js?url';
import { UserError, buildPdfFromJpegs } from './pdf';

pdfjs.GlobalWorkerOptions.workerSrc = workerUrl;

export type PdfDoc = PDFDocumentProxy;

export async function openPdf(bytes: Uint8Array): Promise<PdfDoc> {
  try {
    // pdf.js transfers the buffer to its worker, so give it a copy
    return await pdfjs.getDocument({ data: bytes.slice() }).promise;
  } catch (e) {
    if ((e as { name?: string })?.name === 'PasswordException') {
      throw new UserError('This PDF is password-protected. Unlocking is not supported in this app.');
    }
    throw new UserError('Could not open this PDF. It may be damaged.');
  }
}

export interface RenderOpts { scale: number; rotation?: number; dpr?: number }

export function renderPage(doc: PdfDoc, pageNumber: number, canvas: HTMLCanvasElement, opts: RenderOpts) {
  let task: ReturnType<PDFPageProxy['render']> | null = null;
  let cancelled = false;
  const promise = (async () => {
    const page = await doc.getPage(pageNumber);
    if (cancelled) return;
    const viewport = page.getViewport({ scale: opts.scale, rotation: (page.rotate + (opts.rotation ?? 0)) % 360 });
    const dpr = opts.dpr ?? Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.floor(viewport.width * dpr);
    canvas.height = Math.floor(viewport.height * dpr);
    canvas.style.width = `${Math.floor(viewport.width)}px`;
    canvas.style.height = `${Math.floor(viewport.height)}px`;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    task = page.render({ canvasContext: ctx, viewport, transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined });
    try {
      await task.promise;
    } catch (e) {
      if ((e as { name?: string })?.name !== 'RenderingCancelledException') throw e;
    }
  })();
  return { promise, cancel: () => { cancelled = true; task?.cancel(); } };
}

export async function pageSize(doc: PdfDoc, pageNumber: number, rotation = 0) {
  const page = await doc.getPage(pageNumber);
  const vp = page.getViewport({ scale: 1, rotation: (page.rotate + rotation) % 360 });
  return { width: vp.width, height: vp.height };
}

export async function renderToBlob(
  doc: PdfDoc, pageNumber: number, scale: number, mime: 'image/png' | 'image/jpeg', quality: number,
): Promise<Blob> {
  const canvas = document.createElement('canvas');
  await renderPage(doc, pageNumber, canvas, { scale, dpr: 1 }).promise;
  const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, mime, quality));
  canvas.width = canvas.height = 0;
  if (!blob) throw new UserError('Could not create an image from this page (it may be too large). Try a lower resolution.');
  return blob;
}

/** Re-renders each page as a JPEG and builds a new PDF. Text will no longer be selectable. */
export async function rasterizePdf(
  doc: PdfDoc, scale: number, quality: number, onProgress: (done: number, total: number) => void,
): Promise<Uint8Array> {
  const items: { bytes: Uint8Array; w: number; h: number }[] = [];
  for (let n = 1; n <= doc.numPages; n++) {
    const size = await pageSize(doc, n);
    const blob = await renderToBlob(doc, n, scale, 'image/jpeg', quality);
    items.push({ bytes: new Uint8Array(await blob.arrayBuffer()), w: size.width, h: size.height });
    onProgress(n, doc.numPages);
  }
  return buildPdfFromJpegs(items);
}
