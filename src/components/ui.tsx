import { useRef, useState, type ReactNode } from 'react';
import { Capacitor } from '@capacitor/core';
import { Download, FileText, Loader2, Share2, Upload } from 'lucide-react';
import { saveOutput } from '../lib/save';
import { errMsg } from '../lib/pdf';
import { usePdfDoc } from '../lib/hooks';
import { PageThumb } from './PageViews';
import { toast } from './toast';
import { formatBytes } from '../lib/utils';
import type { PdfFile } from '../lib/files';

export const PDF_ACCEPT = '.pdf,application/pdf';
export const IMG_ACCEPT = 'image/jpeg,image/png,image/webp,image/gif';

export function Spinner({ className = 'h-4 w-4' }: { className?: string }) {
  return <Loader2 className={`animate-spin ${className}`} aria-hidden />;
}

export function FileDrop({ accept, multiple, onFiles, title, hint, disabled }: {
  accept: string; multiple?: boolean; onFiles: (f: File[]) => void; title: string; hint?: string; disabled?: boolean;
}) {
  const ref = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  return (
    <div
      onDragOver={(e) => { e.preventDefault(); setOver(true); }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault(); setOver(false);
        const fs = Array.from(e.dataTransfer.files);
        if (fs.length && !disabled) onFiles(multiple ? fs : fs.slice(0, 1));
      }}
      className={`flex flex-col items-center gap-2 rounded-2xl border-2 border-dashed p-6 text-center transition ${over ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-950/30' : 'border-slate-300 dark:border-slate-700'}`}
    >
      <input ref={ref} type="file" accept={accept} multiple={multiple} className="sr-only" tabIndex={-1}
        onChange={(e) => { const fs = Array.from(e.target.files ?? []); e.target.value = ''; if (fs.length) onFiles(fs); }} />
      <Upload className="h-7 w-7 text-indigo-600" aria-hidden />
      <p className="font-medium">{title}</p>
      {hint && <p className="hint">{hint}</p>}
      <button className="btn btn-primary mt-1" disabled={disabled} onClick={() => ref.current?.click()}>
        {disabled ? <Spinner /> : <Upload className="h-4 w-4" />} {multiple ? 'Choose files' : 'Choose file'}
      </button>
    </div>
  );
}

export function PdfInfo({ file, onClear }: { file: PdfFile; onClear: () => void }) {
  return (
    <div className="card flex items-center gap-3 p-3">
      <FileText className="h-8 w-8 shrink-0 text-red-500" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{file.name}</p>
        <p className="hint">{file.pages} {file.pages === 1 ? 'page' : 'pages'} · {formatBytes(file.size)}</p>
      </div>
      <button className="btn btn-ghost !px-3 !py-1.5" onClick={onClear}>Change</button>
    </div>
  );
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="block">
      <span className="label">{label}</span>
      {children}
      {hint && <span className="hint mt-1 block">{hint}</span>}
    </label>
  );
}

export interface Out { name: string; data: Uint8Array | Blob; mime: string }

export function ResultCard({ outs, kind, note }: { outs: Out[]; kind: string; note?: string }) {
  const first = outs[0];
  const pdfBytes = first && first.data instanceof Uint8Array && first.mime === 'application/pdf' ? first.data : null;
  const { doc } = usePdfDoc(pdfBytes);
  const [busy, setBusy] = useState<string | null>(null);
  const native = Capacitor.isNativePlatform();
  const size = (d: Uint8Array | Blob) => (d instanceof Blob ? d.size : d.byteLength);

  const save = async (o: Out) => {
    setBusy(o.name);
    try {
      await saveOutput(o.data, o.name, o.mime, kind);
      toast(native ? 'Pick where to save in the share sheet.' : 'Download started.', 'ok');
    } catch (e) { toast(errMsg(e), 'err'); } finally { setBusy(null); }
  };

  return (
    <section className="card mt-4 p-4" aria-live="polite">
      <h2 className="font-semibold">Result ready</h2>
      <div className="mt-3 flex gap-4">
        {doc && <div className="w-24 shrink-0 sm:w-36"><PageThumb doc={doc} page={1} width={140} /></div>}
        <ul className="min-w-0 flex-1 space-y-2">
          {outs.map((o) => (
            <li key={o.name} className="flex items-center gap-2">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{o.name}</p>
                <p className="hint">{formatBytes(size(o.data))}</p>
              </div>
              <button className="btn btn-primary !px-3 !py-2" disabled={busy === o.name} onClick={() => save(o)}>
                {busy === o.name ? <Spinner /> : native ? <Share2 className="h-4 w-4" /> : <Download className="h-4 w-4" />}
                {native ? 'Save / Share' : 'Download'}
              </button>
            </li>
          ))}
        </ul>
      </div>
      {note && <p className="hint mt-3">{note}</p>}
    </section>
  );
}

export function Progress({ done, total, label }: { done: number; total: number; label: string }) {
  const pct = total ? Math.round((done / total) * 100) : 0;
  return (
    <div className="mt-3" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={label}>
      <div className="mb-1 flex justify-between text-xs"><span>{label}</span><span>{done}/{total}</span></div>
      <div className="h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
        <div className="h-full bg-indigo-600 transition-all" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
