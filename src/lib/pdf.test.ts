import { describe, expect, it } from 'vitest';
import { PDFDocument } from 'pdf-lib';
import { applyPageEdits, chunkPages, deletePages, extractPages, mergePdfs, parseRanges, parseRangeGroups, splitEvery, UserError } from './pdf';

async function makePdf(pages: number): Promise<Uint8Array> {
  const d = await PDFDocument.create();
  for (let i = 0; i < pages; i++) d.addPage([200, 200]);
  return d.save();
}
const count = async (b: Uint8Array) => (await PDFDocument.load(b)).getPageCount();

describe('range parsing', () => {
  it('parses ranges and singles', () => {
    expect(parseRanges('1-3, 5', 10)).toEqual([0, 1, 2, 4]);
    expect(parseRangeGroups('1-2,4', 5)).toEqual([[0, 1], [3]]);
  });
  it('rejects invalid input', () => {
    expect(() => parseRanges('', 5)).toThrow(UserError);
    expect(() => parseRanges('abc', 5)).toThrow(UserError);
    expect(() => parseRanges('0', 5)).toThrow(UserError);
    expect(() => parseRanges('6', 5)).toThrow(UserError);
    expect(() => parseRanges('4-2', 5)).toThrow(UserError);
  });
  it('chunks pages', () => {
    expect(chunkPages(5, 2)).toEqual([[0, 1], [2, 3], [4]]);
    expect(() => chunkPages(5, 0)).toThrow(UserError);
  });
});

describe('pdf operations', () => {
  it('merges', async () => {
    expect(await count(await mergePdfs([await makePdf(2), await makePdf(3)]))).toBe(5);
  });
  it('requires two files to merge', async () => {
    await expect(mergePdfs([await makePdf(1)])).rejects.toThrow(UserError);
  });
  it('extracts and deletes', async () => {
    const src = await makePdf(5);
    expect(await count(await extractPages(src, [0, 2]))).toBe(2);
    expect(await count(await deletePages(src, [1]))).toBe(4);
    await expect(deletePages(src, [0, 1, 2, 3, 4])).rejects.toThrow(UserError);
  });
  it('splits every N', async () => {
    const parts = await splitEvery(await makePdf(5), 2);
    expect(parts.length).toBe(3);
  });
  it('applies page edits (reorder, rotate, duplicate)', async () => {
    const out = await applyPageEdits(await makePdf(2), [{ src: 1, rot: 90 }, { src: 0, rot: 0 }, { src: 0, rot: 0 }]);
    const d = await PDFDocument.load(out);
    expect(d.getPageCount()).toBe(3);
    expect(d.getPage(0).getRotation().angle).toBe(90);
  });
  it('rejects garbage bytes', async () => {
    await expect(extractPages(new Uint8Array([1, 2, 3]), [0])).rejects.toThrow(UserError);
  });
});
