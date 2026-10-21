import { useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Eraser, ImagePlus } from 'lucide-react';
import { FileDrop, PDF_ACCEPT, PdfInfo, ResultCard, type Out, Spinner, IMG_ACCEPT } from '../components/ui';
import { PageView } from '../components/PageViews';
import { toast } from '../components/toast';
import { usePdfInput, usePdfDoc, useRun } from '../lib/hooks';
import { placeImage, errMsg } from '../lib/pdf';
import { checkImage } from '../lib/images';
import { PDF_MIME, clamp, stripPdf } from '../lib/utils';

function cropToDataUrl(c: HTMLCanvasElement): string | null {
  const ctx = c.getContext('2d')!;
  const { data, width, height } = ctx.getImageData(0, 0, c.width, c.height);
  let x0 = width, y0 = height, x1 = -1, y1 = -1;
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    if (data[(y * width + x) * 4 + 3] > 0) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
  }
  if (x1 < 0) return null;
  const pad = 6;
  x0 = Math.max(0, x0 - pad); y0 = Math.max(0, y0 - pad); x1 = Math.min(width - 1, x1 + pad); y1 = Math.min(height - 1, y1 + pad);
  const out = document.createElement('canvas');
  out.width = x1 - x0 + 1; out.height = y1 - y0 + 1;
  out.getContext('2d')!.drawImage(c, x0, y0, out.width, out.height, 0, 0, out.width, out.height);
  return out.toDataURL('image/png');
}

function SignaturePad({ onChange }: { onChange: (url: string | null) => void }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const pt = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const c = ref.current!; const r = c.getBoundingClientRect();
    return { x: ((e.clientX - r.left) * c.width) / r.width, y: ((e.clientY - r.top) * c.height) / r.height };
  };
  const ctx = () => { const c = ref.current!.getContext('2d')!; c.lineWidth = 4; c.lineCap = 'round'; c.lineJoin = 'round'; c.strokeStyle = '#0f172a'; return c; };
  const clear = () => { const c = ref.current!; c.getContext('2d')!.clearRect(0, 0, c.width, c.height); onChange(null); };

  const upload = async (fs: File[]) => {
    try {
      const f = fs[0]; if (!f) return;
      checkImage(f);
      const bmp = await createImageBitmap(f);
      const c = document.createElement('canvas'); c.width = bmp.width; c.height = bmp.height;
      c.getContext('2d')!.drawImage(bmp, 0, 0); bmp.close();
      onChange(c.toDataURL('image/png'));
      toast('Signature image loaded.', 'ok');
    } catch (e) { toast(errMsg(e), 'err'); }
  };

  return (
    <div className="space-y-2">
      <canvas
        ref={ref} width={800} height={260} aria-label="Draw your signature here"
        className="h-40 w-full touch-none rounded-xl border border-slate-300 bg-white dark:border-slate-700"
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); drawing.current = true; const p = pt(e); const c = ctx(); c.beginPath(); c.moveTo(p.x, p.y); c.lineTo(p.x + 0.1, p.y + 0.1); c.stroke(); }}
        onPointerMove={(e) => { if (!drawing.current) return; const p = pt(e); const c = ctx(); c.lineTo(p.x, p.y); c.stroke(); }}
        onPointerUp={() => { if (!drawing.current) return; drawing.current = false; onChange(cropToDataUrl(ref.current!)); }}
      />
      <div className="flex flex-wrap gap-2">
        <button className="btn btn-ghost" onClick={clear}><Eraser className="h-4 w-4" /> Clear</button>
        <label className="btn btn-ghost cursor-pointer"><ImagePlus className="h-4 w-4" /> Upload image
          <input type="file" accept={IMG_ACCEPT} className="sr-only" onChange={(e) => { const fs = Array.from(e.target.files ?? []); e.target.value = ''; upload(fs); }} />
        </label>
      </div>
    </div>
  );
}

const toBytes = (url: string) => Uint8Array.from(atob(url.split(',')[1]), (c) => c.charCodeAt(0));

export function SignTool() {
  const { file, pick, clear, loading } = usePdfInput();
  const { doc } = usePdfDoc(file?.bytes ?? null);
  const { busy, run } = useRun();
  const [page, setPage] = useState(1);
  const [sig, setSig] = useState<string | null>(null);
  const [pos, setPos] = useState({ x: 0.7, y: 0.85 });
  const [w, setW] = useState(0.25);
  const [outs, setOuts] = useState<Out[] | null>(null);
  const drag = useRef(false);

  const apply = () => run(async () => {
    if (!file || !sig) return;
    const data = await placeImage(file.bytes, toBytes(sig), page - 1, pos.x, pos.y, w);
    setOuts([{ name: `${stripPdf(file.name)}-signed.pdf`, data, mime: PDF_MIME }]);
  });

  if (!file) return <FileDrop accept={PDF_ACCEPT} onFiles={(f) => { setOuts(null); pick(f); }} disabled={loading} title="Choose a PDF to sign" />;
  return (
    <div className="space-y-4">
      <p className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200">
        This places a <b>visual image</b> of your signature on the page. It is not a legally verified digital (cryptographic) signature.
      </p>
      <PdfInfo file={file} onClear={() => { clear(); setOuts(null); }} />
      <div>
        <p className="label">1. Draw or upload your signature</p>
        <SignaturePad onChange={(u) => { setSig(u); setOuts(null); }} />
      </div>
      <div>
        <p className="label">2. Drag the signature to where it should go</p>
        <div className="mb-2 flex items-center gap-2">
          <button className="icon-btn" aria-label="Previous page" disabled={page <= 1} onClick={() => setPage(page - 1)}><ChevronLeft className="h-5 w-5" /></button>
          <span className="text-sm">Page {page} / {file.pages}</span>
          <button className="icon-btn" aria-label="Next page" disabled={page >= file.pages} onClick={() => setPage(page + 1)}><ChevronRight className="h-5 w-5" /></button>
          <label className="ml-auto flex items-center gap-2 text-sm">Size
            <input type="range" min={0.08} max={0.6} step={0.01} value={w} onChange={(e) => setW(Number(e.target.value))} />
          </label>
        </div>
        {doc ? (
          <div className="rounded-xl bg-slate-200 dark:bg-slate-800">
            <PageView doc={doc} page={page} zoom={1} onPick={sig ? (x, y) => setPos({ x: clamp(x), y: clamp(y) }) : undefined}>
              {sig && (
                <img src={sig} alt="Signature position" draggable={false}
                  style={{ left: `${pos.x * 100}%`, top: `${pos.y * 100}%`, width: `${w * 100}%`, touchAction: 'none' }}
                  className="absolute -translate-x-1/2 -translate-y-1/2 cursor-move rounded ring-2 ring-indigo-500/70"
                  onClick={(e) => e.stopPropagation()}
                  onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); drag.current = true; }}
                  onPointerMove={(e) => {
                    if (!drag.current) return;
                    const r = e.currentTarget.parentElement!.getBoundingClientRect();
                    setPos({ x: clamp((e.clientX - r.left) / r.width), y: clamp((e.clientY - r.top) / r.height) });
                  }}
                  onPointerUp={() => { drag.current = false; }}
                />
              )}
            </PageView>
          </div>
        ) : <div className="flex justify-center p-8"><Spinner className="h-6 w-6" /></div>}
      </div>
      <button className="btn btn-primary w-full sm:w-auto" disabled={busy || !sig} onClick={apply}>{busy && <Spinner />} Place signature &amp; preview</button>
      {!sig && <p className="hint">Draw or upload a signature first.</p>}
      {outs && <ResultCard outs={outs} kind="Sign" note="Visual signature only." />}
    </div>
  );
}
