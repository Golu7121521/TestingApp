import { useContext, useEffect, useState } from 'react';
import JSZip from 'jszip';
import { ArrowDown, ArrowUp, X } from 'lucide-react';
import { FileDrop, IMG_ACCEPT, PDF_ACCEPT, PdfInfo, Progress, ResultCard, Field, type Out, Spinner } from '../components/ui';
import { toast } from '../components/toast';
import { SettingsCtx } from '../lib/ctx';
import { checkImage, imagesToPdf } from '../lib/images';
import { renderToBlob } from '../lib/render';
import { errMsg, parseRanges } from '../lib/pdf';
import { usePdfInput, usePdfDoc, useRun } from '../lib/hooks';
import { PDF_MIME, formatBytes, stripPdf, uid } from '../lib/utils';

function Thumb({ file }: { file: File }) {
  const [url, setUrl] = useState('');
  useEffect(() => { const u = URL.createObjectURL(file); setUrl(u); return () => URL.revokeObjectURL(u); }, [file]);
  return <img src={url} alt="" className="h-12 w-12 shrink-0 rounded-md object-cover" />;
}

export function ImagesToPdfTool() {
  const { settings } = useContext(SettingsCtx);
  const [items, setItems] = useState<{ id: string; file: File }[]>([]);
  const [fit, setFit] = useState<'a4' | 'image'>('a4');
  const [outs, setOuts] = useState<Out[] | null>(null);
  const [prog, setProg] = useState({ done: 0, total: 0 });
  const { busy, run } = useRun();

  const add = (fs: File[]) => {
    const ok: { id: string; file: File }[] = [];
    for (const f of fs) { try { checkImage(f); ok.push({ id: uid(), file: f }); } catch (e) { toast(errMsg(e), 'err'); } }
    setItems((p) => [...p, ...ok]); setOuts(null);
  };
  const move = (i: number, d: number) => setItems((p) => { const a = [...p]; const j = i + d; if (j < 0 || j >= a.length) return p; [a[i], a[j]] = [a[j], a[i]]; return a; });
  const convert = () => run(async () => {
    setProg({ done: 0, total: items.length });
    const data = await imagesToPdf(items.map((i) => i.file), { fit, quality: settings.quality }, (done, total) => setProg({ done, total }));
    setOuts([{ name: `images-${items.length}-pages.pdf`, data, mime: PDF_MIME }]);
  });

  return (
    <div className="space-y-4">
      <FileDrop accept={IMG_ACCEPT} multiple onFiles={add} disabled={busy} title="Add images (JPG, PNG, WebP, GIF)" hint="Each image becomes one page, in the order shown." />
      {items.length > 0 && (
        <ul className="space-y-2">
          {items.map((it, i) => (
            <li key={it.id} className="card flex items-center gap-2 p-2">
              <Thumb file={it.file} />
              <div className="min-w-0 flex-1"><p className="truncate text-sm">{i + 1}. {it.file.name}</p><p className="hint">{formatBytes(it.file.size)}</p></div>
              <button className="icon-btn" aria-label="Move up" disabled={i === 0} onClick={() => move(i, -1)}><ArrowUp className="h-4 w-4" /></button>
              <button className="icon-btn" aria-label="Move down" disabled={i === items.length - 1} onClick={() => move(i, 1)}><ArrowDown className="h-4 w-4" /></button>
              <button className="icon-btn" aria-label="Remove" onClick={() => setItems((p) => p.filter((x) => x.id !== it.id))}><X className="h-4 w-4" /></button>
            </li>
          ))}
        </ul>
      )}
      <Field label="Page size">
        <select className="input" value={fit} onChange={(e) => setFit(e.target.value as 'a4' | 'image')}>
          <option value="a4">A4 (fit image inside, with margin)</option>
          <option value="image">Same size as each image</option>
        </select>
      </Field>
      <button className="btn btn-primary w-full sm:w-auto" disabled={busy || !items.length} onClick={convert}>{busy && <Spinner />} Create PDF</button>
      {busy && <Progress done={prog.done} total={prog.total} label="Converting images" />}
      {outs && <ResultCard outs={outs} kind="Images to PDF" />}
    </div>
  );
}

export function PdfToImagesTool() {
  const { settings } = useContext(SettingsCtx);
  const { file, pick, clear, loading } = usePdfInput();
  const { doc } = usePdfDoc(file?.bytes ?? null);
  const [fmt, setFmt] = useState<'png' | 'jpeg'>('png');
  const [scale, setScale] = useState(2);
  const [ranges, setRanges] = useState('');
  const [outs, setOuts] = useState<Out[] | null>(null);
  const [prog, setProg] = useState({ done: 0, total: 0 });
  const { busy, run } = useRun();

  const go = () => run(async () => {
    if (!file || !doc) return;
    const pages = ranges.trim() ? parseRanges(ranges, file.pages) : Array.from({ length: file.pages }, (_, i) => i);
    const base = stripPdf(file.name);
    const ext = fmt === 'png' ? 'png' : 'jpg';
    const zip = new JSZip();
    const imgs: Out[] = [];
    setProg({ done: 0, total: pages.length });
    for (let k = 0; k < pages.length; k++) {
      const blob = await renderToBlob(doc, pages[k] + 1, scale, fmt === 'png' ? 'image/png' : 'image/jpeg', settings.quality);
      const name = `${base}-page-${pages[k] + 1}.${ext}`;
      zip.file(name, blob);
      if (pages.length <= 20) imgs.push({ name, data: blob, mime: blob.type });
      setProg({ done: k + 1, total: pages.length });
    }
    const zipBlob = await zip.generateAsync({ type: 'blob', compression: 'STORE' });
    setOuts([{ name: `${base}-images.zip`, data: zipBlob, mime: 'application/zip' }, ...imgs]);
  });

  if (!file) return <FileDrop accept={PDF_ACCEPT} onFiles={(f) => { setOuts(null); pick(f); }} disabled={loading} title="Choose a PDF to export as images" />;
  return (
    <div className="space-y-4">
      <PdfInfo file={file} onClear={() => { clear(); setOuts(null); }} />
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Format"><select className="input" value={fmt} onChange={(e) => setFmt(e.target.value as 'png' | 'jpeg')}><option value="png">PNG (sharp, larger)</option><option value="jpeg">JPG (smaller)</option></select></Field>
        <Field label="Resolution"><select className="input" value={scale} onChange={(e) => setScale(Number(e.target.value))}><option value={1}>Standard (72 dpi)</option><option value={2}>High (144 dpi)</option><option value={3}>Very high (216 dpi)</option></select></Field>
        <Field label="Pages" hint="Leave empty for all pages"><input className="input" value={ranges} onChange={(e) => setRanges(e.target.value)} placeholder="e.g. 1-3, 5" /></Field>
      </div>
      <button className="btn btn-primary w-full sm:w-auto" disabled={busy || !doc} onClick={go}>{busy && <Spinner />} Export images</button>
      {busy && <Progress done={prog.done} total={prog.total} label="Rendering pages" />}
      {outs && <ResultCard outs={outs} kind="PDF to images" note="JPG quality follows your default in Settings." />}
    </div>
  );
}
