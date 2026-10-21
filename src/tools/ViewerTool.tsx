import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Maximize2, PanelLeft, RotateCw, X, ZoomIn, ZoomOut } from 'lucide-react';
import { FileDrop, PDF_ACCEPT, Spinner } from '../components/ui';
import { PageThumb, PageView } from '../components/PageViews';
import { usePdfInput, usePdfDoc } from '../lib/hooks';
import { clamp } from '../lib/utils';

export function ViewerTool() {
  const { file, pick, clear, loading } = usePdfInput();
  const { doc, error } = usePdfDoc(file?.bytes ?? null);
  const [page, setPage] = useState(1);
  const [zoom, setZoom] = useState<number | 'page'>(1);
  const [rot, setRot] = useState(0);
  const [thumbs, setThumbs] = useState(() => window.innerWidth >= 768);
  const total = file?.pages ?? 0;

  useEffect(() => { setPage(1); setZoom(1); setRot(0); }, [file]);
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.tagName === 'INPUT') return;
      if (e.key === 'ArrowRight' || e.key === 'PageDown') setPage((p) => Math.min(total, p + 1));
      if (e.key === 'ArrowLeft' || e.key === 'PageUp') setPage((p) => Math.max(1, p - 1));
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [total]);

  if (!file) {
    return (
      <div className="mx-auto max-w-2xl p-4">
        <FileDrop accept={PDF_ACCEPT} onFiles={pick} disabled={loading} title="Open a PDF to view" hint="Opens on this device only. Text search and selection are not available in this version." />
      </div>
    );
  }
  const step = (d: number) => setZoom((z) => clamp((z === 'page' ? 1 : z) + d, 0.5, 4));

  return (
    <div className="absolute inset-0 flex flex-col">
      <div className="flex flex-wrap items-center gap-1 border-b border-slate-200 bg-white px-2 py-1.5 dark:border-slate-800 dark:bg-slate-900">
        <button className="icon-btn" aria-label="Toggle page thumbnails" aria-pressed={thumbs} onClick={() => setThumbs((t) => !t)}><PanelLeft className="h-5 w-5" /></button>
        <span className="mx-1 max-w-[30%] truncate text-sm font-medium">{file.name}</span>
        <div className="ml-auto flex items-center gap-1">
          <button className="icon-btn" aria-label="Previous page" disabled={page <= 1} onClick={() => setPage(page - 1)}><ChevronLeft className="h-5 w-5" /></button>
          <input aria-label="Page number" className="input !w-14 !px-2 !py-1 text-center" type="number" min={1} max={total} value={page}
            onChange={(e) => setPage(clamp(Math.round(Number(e.target.value) || 1), 1, total))} />
          <span className="text-sm">/ {total}</span>
          <button className="icon-btn" aria-label="Next page" disabled={page >= total} onClick={() => setPage(page + 1)}><ChevronRight className="h-5 w-5" /></button>
          <button className="icon-btn" aria-label="Zoom out" onClick={() => step(-0.25)}><ZoomOut className="h-5 w-5" /></button>
          <button className="icon-btn" aria-label="Zoom in" onClick={() => step(0.25)}><ZoomIn className="h-5 w-5" /></button>
          <button className="icon-btn" aria-label="Fit width" onClick={() => setZoom(1)}><span className="text-xs font-semibold">W</span></button>
          <button className="icon-btn" aria-label="Fit page" onClick={() => setZoom('page')}><Maximize2 className="h-4 w-4" /></button>
          <button className="icon-btn" aria-label="Rotate view" onClick={() => setRot((r) => (r + 90) % 360)}><RotateCw className="h-5 w-5" /></button>
          <button className="icon-btn" aria-label="Close file" onClick={clear}><X className="h-5 w-5" /></button>
        </div>
      </div>
      <div className="flex min-h-0 flex-1">
        {thumbs && doc && (
          <aside className="w-24 shrink-0 space-y-2 overflow-y-auto border-r border-slate-200 bg-white p-2 dark:border-slate-800 dark:bg-slate-900 md:w-36" aria-label="Pages">
            {Array.from({ length: doc.numPages }, (_, i) => (
              <button key={i} onClick={() => setPage(i + 1)} aria-label={`Go to page ${i + 1}`} aria-current={page === i + 1}
                className={`block w-full rounded-lg p-1 ${page === i + 1 ? 'ring-2 ring-indigo-500' : ''}`}>
                <PageThumb doc={doc} page={i + 1} width={80} />
                <span className="hint block text-center">{i + 1}</span>
              </button>
            ))}
          </aside>
        )}
        <div className="min-w-0 flex-1 bg-slate-200 dark:bg-slate-800">
          {error ? <p className="p-6 text-sm text-red-600" role="alert">{error}</p>
            : doc ? <PageView doc={doc} page={page} zoom={zoom} rotation={rot} className="h-full" />
            : <div className="flex h-full items-center justify-center"><Spinner className="h-6 w-6" /></div>}
        </div>
      </div>
    </div>
  );
}
