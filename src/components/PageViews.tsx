import { useEffect, useRef, useState, type ReactNode } from 'react';
import { renderPage, pageSize, type PdfDoc } from '../lib/render';

/** Small lazily-rendered thumbnail. */
export function PageThumb({ doc, page, width = 120, rotation = 0 }: { doc: PdfDoc; page: number; width?: number; rotation?: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const wrap = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = wrap.current;
    if (!el) return;
    const io = new IntersectionObserver(([e]) => { if (e.isIntersecting) setVisible(true); }, { rootMargin: '200px' });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  useEffect(() => {
    if (!visible || !canvas.current) return;
    let cancelled = false;
    let job: ReturnType<typeof renderPage> | undefined;
    (async () => {
      const s = await pageSize(doc, page, rotation);
      if (cancelled || !canvas.current) return;
      job = renderPage(doc, page, canvas.current, { scale: width / s.width, rotation });
      await job.promise;
    })().catch(() => {});
    return () => { cancelled = true; job?.cancel(); };
  }, [doc, page, width, rotation, visible]);

  return (
    <div ref={wrap} className="flex min-h-[80px] items-center justify-center overflow-hidden rounded-md bg-slate-100 dark:bg-slate-800" style={{ minWidth: Math.min(width, 80) }}>
      <canvas ref={canvas} className="block max-w-full" aria-label={`Page ${page} preview`} />
    </div>
  );
}

/** Large page view. zoom: 'page' = fit whole page, number = multiple of fit-width. */
export function PageView({ doc, page, zoom, rotation = 0, className = '', onPick, children }: {
  doc: PdfDoc; page: number; zoom: number | 'page'; rotation?: number; className?: string;
  onPick?: (x: number, y: number) => void; children?: ReactNode;
}) {
  const box = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const [size, setSize] = useState({ w: 0, h: 0 });

  useEffect(() => {
    const el = box.current!;
    const update = () => setSize({ w: el.clientWidth, h: el.clientHeight });
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const needH = zoom === 'page' ? size.h : 0;
  useEffect(() => {
    if (!size.w || !canvas.current) return;
    let cancelled = false;
    let job: ReturnType<typeof renderPage> | undefined;
    (async () => {
      const s = await pageSize(doc, page, rotation);
      if (cancelled || !canvas.current) return;
      const pad = 24;
      const fitW = (size.w - pad) / s.width;
      let scale = zoom === 'page' ? Math.min(fitW, (size.h - pad) / s.height) : zoom * fitW;
      scale = Math.max(0.1, scale);
      const dpr = Math.min(window.devicePixelRatio || 1, 2, 4096 / (s.width * scale));
      job = renderPage(doc, page, canvas.current, { scale, rotation, dpr });
      await job.promise;
    })().catch(() => {});
    return () => { cancelled = true; job?.cancel(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doc, page, zoom, rotation, size.w, needH]);

  return (
    <div ref={box} className={`overflow-auto ${className}`}>
      <div
        className="relative mx-auto my-3 w-fit shadow-md"
        onClick={onPick ? (e) => {
          const r = e.currentTarget.getBoundingClientRect();
          onPick((e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height);
        } : undefined}
      >
        <canvas ref={canvas} className="block bg-white" />
        {children}
      </div>
    </div>
  );
}

export function PageStrip({ doc }: { doc: PdfDoc }) {
  return (
    <div className="flex gap-2 overflow-x-auto pb-2" aria-label="Page previews">
      {Array.from({ length: doc.numPages }, (_, i) => (
        <div key={i} className="shrink-0 text-center">
          <PageThumb doc={doc} page={i + 1} width={72} />
          <span className="hint">{i + 1}</span>
        </div>
      ))}
    </div>
  );
}
