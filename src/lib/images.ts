import { PDFDocument } from 'pdf-lib';
import { UserError } from './pdf';
import { MAX_BYTES } from './files';

export function checkImage(file: File): void {
  if (file.size === 0) throw new UserError(`"${file.name}" is empty (0 bytes).`);
  if (file.size > MAX_BYTES) throw new UserError(`"${file.name}" is larger than 150 MB.`);
  const ok = /^image\/(jpeg|png|webp|gif)$/.test(file.type) || /\.(jpe?g|png|webp|gif)$/i.test(file.name);
  if (!ok) throw new UserError(`"${file.name}" is not a supported image. Use JPG, PNG, WebP or GIF.`);
}

async function decode(file: File, quality: number) {
  let bmp: ImageBitmap;
  try { bmp = await createImageBitmap(file); }
  catch { throw new UserError(`"${file.name}" could not be decoded as an image.`); }
  const c = document.createElement('canvas');
  c.width = bmp.width; c.height = bmp.height;
  const ctx = c.getContext('2d')!;
  const png = file.type === 'image/png' || /\.png$/i.test(file.name);
  if (!png) { ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, c.width, c.height); }
  ctx.drawImage(bmp, 0, 0);
  bmp.close();
  const blob = await new Promise<Blob | null>((r) => c.toBlob(r, png ? 'image/png' : 'image/jpeg', quality));
  if (!blob) throw new UserError(`"${file.name}" is too large to convert.`);
  return { bytes: new Uint8Array(await blob.arrayBuffer()), png, w: c.width, h: c.height };
}

export async function imagesToPdf(
  files: File[], opts: { fit: 'a4' | 'image'; quality: number }, onProgress: (done: number, total: number) => void,
): Promise<Uint8Array> {
  if (!files.length) throw new UserError('Add at least one image.');
  const doc = await PDFDocument.create();
  for (let i = 0; i < files.length; i++) {
    const d = await decode(files[i], opts.quality);
    const img = d.png ? await doc.embedPng(d.bytes) : await doc.embedJpg(d.bytes);
    if (opts.fit === 'image') {
      const page = doc.addPage([d.w, d.h]);
      page.drawImage(img, { x: 0, y: 0, width: d.w, height: d.h });
    } else {
      const landscape = d.w > d.h;
      const [pw, ph] = landscape ? [841.89, 595.28] : [595.28, 841.89];
      const m = 24;
      const s = Math.min((pw - 2 * m) / d.w, (ph - 2 * m) / d.h);
      const w = d.w * s, h = d.h * s;
      const page = doc.addPage([pw, ph]);
      page.drawImage(img, { x: (pw - w) / 2, y: (ph - h) / 2, width: w, height: h });
    }
    onProgress(i + 1, files.length);
  }
  return doc.save();
}
