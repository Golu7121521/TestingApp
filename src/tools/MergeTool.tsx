import { useState } from 'react';
import { ArrowDown, ArrowUp, FileText, X } from 'lucide-react';
import { FileDrop, PDF_ACCEPT, ResultCard, type Out, Spinner } from '../components/ui';
import { toast } from '../components/toast';
import { readPdfFile, type PdfFile } from '../lib/files';
import { mergePdfs, errMsg } from '../lib/pdf';
import { useRun } from '../lib/hooks';
import { PDF_MIME, formatBytes } from '../lib/utils';

export function MergeTool() {
  const [files, setFiles] = useState<PdfFile[]>([]);
  const [outs, setOuts] = useState<Out[] | null>(null);
  const { busy, run } = useRun();

  const add = (fs: File[]) => run(async () => {
    const next: PdfFile[] = [];
    for (const f of fs) {
      try { next.push(await readPdfFile(f)); } catch (e) { toast(errMsg(e), 'err'); }
    }
    setFiles((p) => [...p, ...next]);
    setOuts(null);
  });
  const move = (i: number, d: number) => {
    setFiles((p) => { const a = [...p]; const j = i + d; if (j < 0 || j >= a.length) return p; [a[i], a[j]] = [a[j], a[i]]; return a; });
    setOuts(null);
  };
  const remove = (i: number) => { setFiles((p) => p.filter((_, k) => k !== i)); setOuts(null); };
  const merge = () => run(async () => {
    const data = await mergePdfs(files.map((f) => f.bytes));
    setOuts([{ name: `merged-${files.length}-files.pdf`, data, mime: PDF_MIME }]);
  });

  return (
    <div className="space-y-4">
      <FileDrop accept={PDF_ACCEPT} multiple onFiles={add} disabled={busy} title="Add the PDFs you want to merge" hint="Files are merged in the order shown below." />
      {files.length > 0 && (
        <ul className="space-y-2" aria-label="Merge queue">
          {files.map((f, i) => (
            <li key={f.id} className="card flex items-center gap-2 p-3">
              <FileText className="h-6 w-6 shrink-0 text-red-500" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{i + 1}. {f.name}</p>
                <p className="hint">{f.pages} pages · {formatBytes(f.size)}</p>
              </div>
              <button className="icon-btn" aria-label={`Move ${f.name} up`} disabled={i === 0} onClick={() => move(i, -1)}><ArrowUp className="h-4 w-4" /></button>
              <button className="icon-btn" aria-label={`Move ${f.name} down`} disabled={i === files.length - 1} onClick={() => move(i, 1)}><ArrowDown className="h-4 w-4" /></button>
              <button className="icon-btn" aria-label={`Remove ${f.name}`} onClick={() => remove(i)}><X className="h-4 w-4" /></button>
            </li>
          ))}
        </ul>
      )}
      <button className="btn btn-primary w-full sm:w-auto" disabled={busy || files.length < 2} onClick={merge}>
        {busy && <Spinner />} Merge {files.length > 1 ? `${files.length} PDFs` : 'PDFs'}
      </button>
      {files.length === 1 && <p className="hint">Add at least one more PDF.</p>}
      {outs && <ResultCard outs={outs} kind="Merge" />}
    </div>
  );
}
