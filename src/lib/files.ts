import { UserError, loadPdfLib } from './pdf';
import { uid } from './utils';

export const MAX_BYTES = 150 * 1024 * 1024;

export interface PdfFile { id: string; name: string; size: number; bytes: Uint8Array; pages: number }

export async function readPdfFile(file: File): Promise<PdfFile> {
  if (file.size === 0) throw new UserError(`"${file.name}" is empty (0 bytes).`);
  if (file.size > MAX_BYTES) throw new UserError(`"${file.name}" is larger than 150 MB.`);
  if (!(file.type === 'application/pdf' || /\.pdf$/i.test(file.name))) {
    throw new UserError(`"${file.name}" is not a PDF file.`);
  }
  const bytes = new Uint8Array(await file.arrayBuffer());
  const head = new TextDecoder('latin1').decode(bytes.subarray(0, 1024));
  if (!head.includes('%PDF-')) throw new UserError(`"${file.name}" does not look like a valid PDF.`);
  try {
    const doc = await loadPdfLib(bytes);
    return { id: uid(), name: file.name, size: file.size, bytes, pages: doc.getPageCount() };
  } catch (e) {
    throw new UserError(`"${file.name}": ${(e as Error).message}`);
  }
}
