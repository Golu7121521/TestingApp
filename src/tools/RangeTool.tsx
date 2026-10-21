import { useState } from 'react';
import { FileDrop, PDF_ACCEPT, PdfInfo, ResultCard, Field, type Out, Spinner } from '../components/ui';
import { PageStrip } from '../components/PageViews';
import { usePdfInput, useRun, usePdfDoc } from '../lib/hooks';
import { chunkPages, deletePages, extractPages, parseRangeGroups, parseRanges, splitEvery, UserError } from '../lib/pdf';
import { PDF_MIME, stripPdf } from '../lib/utils';

type Mode = 'extract' | 'delete' | 'split';
const COPY: Record<Mode, { cta: string; kind: string }> = {
  extract: { cta: 'Extract pages', kind: 'Extract' },
  delete: { cta: 'Delete pages', kind: 'Delete pages' },
  split: { cta: 'Split PDF', kind: 'Split' },
};

export function RangeTool({ mode }: { mode: Mode }) {
  const { file, pick, clear, loading } = usePdfInput();
  const { busy, run } = useRun();
  const [ranges, setRanges] = useState('');
  const [splitBy, setSplitBy] = useState<'ranges' | 'every'>('ranges');
  const [n, setN] = useState(1);
  const [outs, setOuts] = useState<Out[] | null>(null);
  const { doc } = usePdfDoc(file?.bytes ?? null);

  const go = () => run(async () => {
    if (!file) return;
    const base = stripPdf(file.name);
    const slug = ranges.replace(/\s/g, '').replace(/,/g, '_');
    const res: Out[] = [];
    if (mode === 'extract') {
      const p = parseRanges(ranges, file.pages);
      res.push({ name: `${base}-pages-${slug}.pdf`, data: await extractPages(file.bytes, p), mime: PDF_MIME });
    } else if (mode === 'delete') {
      const p = parseRanges(ranges, file.pages);
      if (!window.confirm(`Remove ${p.length} page(s) into a new file? Your original file is not changed.`)) return;
      res.push({ name: `${base}-without-${slug}.pdf`, data: await deletePages(file.bytes, p), mime: PDF_MIME });
    } else if (splitBy === 'every') {
      if (!Number.isInteger(n) || n < 1) throw new UserError('Pages per file must be a whole number of 1 or more.');
      chunkPages(file.pages, n);
      const parts = await splitEvery(file.bytes, n);
      parts.forEach((b, i) => res.push({ name: `${base}-part-${i + 1}.pdf`, data: b, mime: PDF_MIME }));
    } else {
      const groups = parseRangeGroups(ranges, file.pages);
      for (let i = 0; i < groups.length; i++) {
        const g = groups[i];
        const label = g.length > 1 ? `${g[0] + 1}-${g[g.length - 1] + 1}` : `${g[0] + 1}`;
        res.push({ name: `${base}-pages-${label}.pdf`, data: await extractPages(file.bytes, g), mime: PDF_MIME });
      }
    }
    setOuts(res);
  });

  if (!file) {
    return <FileDrop accept={PDF_ACCEPT} onFiles={(f) => { setOuts(null); pick(f); }} disabled={loading} title="Choose a PDF" hint="Your file stays on this device." />;
  }
  const needsRanges = mode !== 'split' || splitBy === 'ranges';

  return (
    <div className="space-y-4">
      <PdfInfo file={file} onClear={() => { clear(); setOuts(null); setRanges(''); }} />
      {doc && <PageStrip doc={doc} />}
      {mode === 'split' && (
        <fieldset className="flex flex-wrap gap-4">
          <legend className="label">Split method</legend>
          <label className="flex items-center gap-2 text-sm"><input type="radio" checked={splitBy === 'ranges'} onChange={() => setSplitBy('ranges')} /> By page ranges (one file per range)</label>
          <label className="flex items-center gap-2 text-sm"><input type="radio" checked={splitBy === 'every'} onChange={() => setSplitBy('every')} /> Every N pages</label>
        </fieldset>
      )}
      {needsRanges ? (
        <Field label="Pages" hint={`Document has ${file.pages} pages. Example: 1-3, 5, 8-10`}>
          <input className="input" value={ranges} onChange={(e) => setRanges(e.target.value)} placeholder="1-3, 5, 8-10" inputMode="text" />
        </Field>
      ) : (
        <Field label="Pages per file">
          <input className="input" type="number" min={1} max={file.pages} value={n} onChange={(e) => setN(Number(e.target.value))} />
        </Field>
      )}
      <button className={`btn w-full sm:w-auto ${mode === 'delete' ? 'btn-danger' : 'btn-primary'}`} disabled={busy} onClick={go}>
        {busy && <Spinner />} {COPY[mode].cta}
      </button>
      {outs && <ResultCard outs={outs} kind={COPY[mode].kind} />}
    </div>
  );
}
