import { useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight, Copy, RotateCcw, RotateCw, Trash2, Undo2 } from 'lucide-react';
import { FileDrop, PDF_ACCEPT, PdfInfo, ResultCard, type Out, Spinner } from '../components/ui';
import { PageThumb } from '../components/PageViews';
import { usePdfInput, useRun, usePdfDoc } from '../lib/hooks';
import { applyPageEdits } from '../lib/pdf';
import { setDirty } from '../lib/dirty';
import { PDF_MIME, stripPdf, uid } from '../lib/utils';

interface Item { id: string; src: number; rot: number }

export function OrganizeTool({ focus }: { focus: 'rotate' | 'reorder' }) {
  const { file, pick, clear, loading } = usePdfInput();
  const { busy, run } = useRun();
  const { doc } = usePdfDoc(file?.bytes ?? null);
  const [items, setItems] = useState<Item[]>([]);
  const [past, setPast] = useState<Item[][]>([]);
  const [outs, setOuts] = useState<Out[] | null>(null);

  useEffect(() => {
    if (file) { setItems(Array.from({ length: file.pages }, (_, i) => ({ id: uid(), src: i, rot: 0 }))); setPast([]); setOuts(null); }
  }, [file]);
  useEffect(() => {
    setDirty(past.length > 0 && !outs);
    return () => setDirty(false);
  }, [past, outs]);

  const commit = (next: Item[]) => { setPast((p) => [...p, items]); setItems(next); setOuts(null); };
  const rotate = (id: string, d: number) => commit(items.map((it) => (it.id === id ? { ...it, rot: (it.rot + d + 360) % 360 } : it)));
  const move = (i: number, d: number) => {
    const j = i + d; if (j < 0 || j >= items.length) return;
    const a = [...items]; [a[i], a[j]] = [a[j], a[i]]; commit(a);
  };
  const dup = (i: number) => { const a = [...items]; a.splice(i + 1, 0, { ...items[i], id: uid() }); commit(a); };
  const remove = (i: number) => {
    if (items.length < 2) return;
    if (!window.confirm('Remove this page from the result? You can undo it.')) return;
    commit(items.filter((_, k) => k !== i));
  };
  const undo = () => { const prev = past[past.length - 1]; if (!prev) return; setPast((p) => p.slice(0, -1)); setItems(prev); setOuts(null); };
  const apply = () => run(async () => {
    if (!file) return;
    const data = await applyPageEdits(file.bytes, items.map(({ src, rot }) => ({ src, rot })));
    setOuts([{ name: `${stripPdf(file.name)}-${focus === 'rotate' ? 'rotated' : 'reordered'}.pdf`, data, mime: PDF_MIME }]);
    setDirty(false);
  });

  if (!file) return <FileDrop accept={PDF_ACCEPT} onFiles={pick} disabled={loading} title="Choose a PDF" hint="Rotate, reorder, duplicate or remove pages. Your original is never changed." />;

  return (
    <div className="space-y-4">
      <PdfInfo file={file} onClear={() => { clear(); setItems([]); setOuts(null); }} />
      <div className="flex flex-wrap items-center gap-2">
        <button className="btn btn-ghost" onClick={undo} disabled={!past.length}><Undo2 className="h-4 w-4" /> Undo</button>
        <button className="btn btn-ghost" onClick={() => commit(items.map((it) => ({ ...it, rot: (it.rot + 90) % 360 })))}><RotateCw className="h-4 w-4" /> Rotate all</button>
        <button className="btn btn-primary ml-auto" onClick={apply} disabled={busy || !doc}>{busy && <Spinner />} Apply changes</button>
      </div>
      {doc && (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((it, i) => (
            <li key={it.id} className="card p-2">
              <div className="flex justify-center"><PageThumb doc={doc} page={it.src + 1} width={120} rotation={it.rot} /></div>
              <p className="hint mt-1 text-center">Position {i + 1} · original {it.src + 1}</p>
              <div className="mt-1 flex flex-wrap justify-center">
                <button className="icon-btn" aria-label={`Rotate position ${i + 1} left`} onClick={() => rotate(it.id, -90)}><RotateCcw className="h-4 w-4" /></button>
                <button className="icon-btn" aria-label={`Rotate position ${i + 1} right`} onClick={() => rotate(it.id, 90)}><RotateCw className="h-4 w-4" /></button>
                <button className="icon-btn" aria-label={`Move position ${i + 1} earlier`} disabled={i === 0} onClick={() => move(i, -1)}><ArrowLeft className="h-4 w-4" /></button>
                <button className="icon-btn" aria-label={`Move position ${i + 1} later`} disabled={i === items.length - 1} onClick={() => move(i, 1)}><ArrowRight className="h-4 w-4" /></button>
                <button className="icon-btn" aria-label={`Duplicate position ${i + 1}`} onClick={() => dup(i)}><Copy className="h-4 w-4" /></button>
                <button className="icon-btn text-red-600" aria-label={`Delete position ${i + 1}`} disabled={items.length < 2} onClick={() => remove(i)}><Trash2 className="h-4 w-4" /></button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {outs && <ResultCard outs={outs} kind={focus === 'rotate' ? 'Rotate' : 'Reorder'} />}
    </div>
  );
}
