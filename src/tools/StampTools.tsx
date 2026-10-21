import { useContext, useState } from 'react';
import { FileDrop, PDF_ACCEPT, PdfInfo, Progress, ResultCard, Field, type Out, Spinner } from '../components/ui';
import { usePdfInput, usePdfDoc, useRun } from '../lib/hooks';
import { SettingsCtx } from '../lib/ctx';
import { addPageNumbers, addWatermark, optimizeStructure, parseRanges, type PageNumberOptions } from '../lib/pdf';
import { rasterizePdf } from '../lib/render';
import { PDF_MIME, formatBytes, stripPdf } from '../lib/utils';

export function WatermarkTool() {
  const { file, pick, clear, loading } = usePdfInput();
  const { busy, run } = useRun();
  const [text, setText] = useState('CONFIDENTIAL');
  const [size, setSize] = useState(48);
  const [color, setColor] = useState('#ef4444');
  const [opacity, setOpacity] = useState(0.25);
  const [position, setPosition] = useState<'center' | 'top' | 'bottom'>('center');
  const [diagonal, setDiagonal] = useState(true);
  const [margin, setMargin] = useState(36);
  const [ranges, setRanges] = useState('');
  const [outs, setOuts] = useState<Out[] | null>(null);

  const go = () => run(async () => {
    if (!file) return;
    const pages = ranges.trim() ? parseRanges(ranges, file.pages) : null;
    const data = await addWatermark(file.bytes, { text, size, color, opacity, position, diagonal, margin, pages });
    setOuts([{ name: `${stripPdf(file.name)}-watermarked.pdf`, data, mime: PDF_MIME }]);
  });

  if (!file) return <FileDrop accept={PDF_ACCEPT} onFiles={(f) => { setOuts(null); pick(f); }} disabled={loading} title="Choose a PDF" />;
  return (
    <div className="space-y-4">
      <PdfInfo file={file} onClear={() => { clear(); setOuts(null); }} />
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Text" hint="Latin characters only"><input className="input" value={text} onChange={(e) => setText(e.target.value)} /></Field>
        <Field label="Position"><select className="input" value={position} onChange={(e) => setPosition(e.target.value as typeof position)}><option value="center">Center</option><option value="top">Top</option><option value="bottom">Bottom</option></select></Field>
        <Field label={`Size: ${size} pt`}><input type="range" min={12} max={160} value={size} onChange={(e) => setSize(Number(e.target.value))} className="w-full" /></Field>
        <Field label={`Opacity: ${Math.round(opacity * 100)}%`}><input type="range" min={0.05} max={1} step={0.05} value={opacity} onChange={(e) => setOpacity(Number(e.target.value))} className="w-full" /></Field>
        <Field label="Color"><input type="color" value={color} onChange={(e) => setColor(e.target.value)} className="h-10 w-full rounded-xl border border-slate-300 dark:border-slate-700" /></Field>
        <Field label={`Margin (top/bottom): ${margin} pt`}><input type="range" min={0} max={120} value={margin} onChange={(e) => setMargin(Number(e.target.value))} className="w-full" /></Field>
        <Field label="Pages" hint={`Empty = all ${file.pages} pages`}><input className="input" value={ranges} onChange={(e) => setRanges(e.target.value)} placeholder="e.g. 1-3, 5" /></Field>
        <label className="flex items-center gap-2 self-end pb-2 text-sm"><input type="checkbox" checked={diagonal} onChange={(e) => setDiagonal(e.target.checked)} /> Diagonal (45°)</label>
      </div>
      <button className="btn btn-primary w-full sm:w-auto" disabled={busy} onClick={go}>{busy && <Spinner />} Apply watermark &amp; preview</button>
      {outs && <ResultCard outs={outs} kind="Watermark" note="Preview shows page 1 of the result. Pages with a rotated view may place the text differently." />}
    </div>
  );
}

export function PageNumbersTool() {
  const { file, pick, clear, loading } = usePdfInput();
  const { busy, run } = useRun();
  const [format, setFormat] = useState('Page {n} of {total}');
  const [size, setSize] = useState(11);
  const [color, setColor] = useState('#334155');
  const [margin, setMargin] = useState(24);
  const [start, setStart] = useState(1);
  const [position, setPosition] = useState<PageNumberOptions['position']>('bottom-center');
  const [ranges, setRanges] = useState('');
  const [outs, setOuts] = useState<Out[] | null>(null);

  const go = () => run(async () => {
    if (!file) return;
    const pages = ranges.trim() ? parseRanges(ranges, file.pages) : null;
    const data = await addPageNumbers(file.bytes, { format, size, color, margin, start, position, pages });
    setOuts([{ name: `${stripPdf(file.name)}-numbered.pdf`, data, mime: PDF_MIME }]);
  });

  if (!file) return <FileDrop accept={PDF_ACCEPT} onFiles={(f) => { setOuts(null); pick(f); }} disabled={loading} title="Choose a PDF" />;
  return (
    <div className="space-y-4">
      <PdfInfo file={file} onClear={() => { clear(); setOuts(null); }} />
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Format"><select className="input" value={format} onChange={(e) => setFormat(e.target.value)}><option value="{n}">1</option><option value="Page {n}">Page 1</option><option value="{n} / {total}">1 / 10</option><option value="Page {n} of {total}">Page 1 of 10</option></select></Field>
        <Field label="Position"><select className="input" value={position} onChange={(e) => setPosition(e.target.value as typeof position)}>
          <option value="bottom-center">Bottom center</option><option value="bottom-left">Bottom left</option><option value="bottom-right">Bottom right</option>
          <option value="top-center">Top center</option><option value="top-left">Top left</option><option value="top-right">Top right</option></select></Field>
        <Field label={`Font size: ${size} pt`}><input type="range" min={7} max={24} value={size} onChange={(e) => setSize(Number(e.target.value))} className="w-full" /></Field>
        <Field label={`Margin: ${margin} pt`}><input type="range" min={8} max={96} value={margin} onChange={(e) => setMargin(Number(e.target.value))} className="w-full" /></Field>
        <Field label="Color"><input type="color" value={color} onChange={(e) => setColor(e.target.value)} className="h-10 w-full rounded-xl border border-slate-300 dark:border-slate-700" /></Field>
        <Field label="First page number"><input className="input" type="number" value={start} onChange={(e) => setStart(Number(e.target.value) || 1)} /></Field>
        <Field label="Pages" hint={`Empty = all ${file.pages} pages`}><input className="input" value={ranges} onChange={(e) => setRanges(e.target.value)} placeholder="e.g. 2-10" /></Field>
      </div>
      <button className="btn btn-primary w-full sm:w-auto" disabled={busy} onClick={go}>{busy && <Spinner />} Add numbers &amp; preview</button>
      {outs && <ResultCard outs={outs} kind="Page numbers" />}
    </div>
  );
}

type Preset = 'optimize' | 'balanced' | 'small';
export function CompressTool() {
  const { file, pick, clear, loading } = usePdfInput();
  const { doc } = usePdfDoc(file?.bytes ?? null);
  const { settings } = useContext(SettingsCtx);
  const { busy, run } = useRun();
  const [preset, setPreset] = useState<Preset>('optimize');
  const [outs, setOuts] = useState<Out[] | null>(null);
  const [prog, setProg] = useState({ done: 0, total: 0 });

  const go = () => run(async () => {
    if (!file) return;
    let data: Uint8Array;
    if (preset === 'optimize') data = await optimizeStructure(file.bytes);
    else {
      if (!doc) return;
      setProg({ done: 0, total: doc.numPages });
      const q = preset === 'balanced' ? Math.min(settings.quality, 0.7) : 0.5;
      data = await rasterizePdf(doc, preset === 'balanced' ? 1.5 : 1, q, (done, total) => setProg({ done, total }));
    }
    setOuts([{ name: `${stripPdf(file.name)}-compressed.pdf`, data, mime: PDF_MIME }]);
  });

  const out = outs?.[0].data as Uint8Array | undefined;
  const note = file && out
    ? out.byteLength < file.size
      ? `${formatBytes(file.size)} → ${formatBytes(out.byteLength)}. Result depends on this file's contents.`
      : `${formatBytes(file.size)} → ${formatBytes(out.byteLength)}. This file did not get smaller with this preset, so keep your original or try a stronger preset.`
    : undefined;

  if (!file) return <FileDrop accept={PDF_ACCEPT} onFiles={(f) => { setOuts(null); pick(f); }} disabled={loading} title="Choose a PDF to compress" />;
  const opts: { id: Preset; title: string; desc: string }[] = [
    { id: 'optimize', title: 'Optimize structure', desc: 'Lossless. Text stays selectable. Often saves little or nothing.' },
    { id: 'balanced', title: 'Balanced (re-render as images)', desc: 'Pages become JPEG images at ~108 dpi. Text is no longer selectable or searchable.' },
    { id: 'small', title: 'Smallest (re-render as images)', desc: 'Pages become low-quality JPEG images at 72 dpi. Text is no longer selectable or searchable.' },
  ];
  return (
    <div className="space-y-4">
      <PdfInfo file={file} onClear={() => { clear(); setOuts(null); }} />
      <fieldset className="space-y-2">
        <legend className="label">Compression preset</legend>
        {opts.map((o) => (
          <label key={o.id} className={`card flex cursor-pointer items-start gap-3 p-3 ${preset === o.id ? 'ring-2 ring-indigo-500' : ''}`}>
            <input type="radio" className="mt-1" checked={preset === o.id} onChange={() => { setPreset(o.id); setOuts(null); }} />
            <span><span className="block text-sm font-medium">{o.title}</span><span className="hint">{o.desc}</span></span>
          </label>
        ))}
      </fieldset>
      <p className="hint">Actual savings depend on what is inside your PDF. No reduction is guaranteed.</p>
      <button className="btn btn-primary w-full sm:w-auto" disabled={busy} onClick={go}>{busy && <Spinner />} Compress</button>
      {busy && preset !== 'optimize' && <Progress done={prog.done} total={prog.total} label="Re-rendering pages" />}
      {outs && <ResultCard outs={outs} kind="Compress" note={note} />}
    </div>
  );
}
