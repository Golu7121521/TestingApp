import { useCallback, useEffect, useState } from 'react';
import { openPdf, type PdfDoc } from './render';
import { errMsg } from './pdf';
import { readPdfFile, type PdfFile } from './files';
import { toast } from '../components/toast';

export function useRun() {
  const [busy, setBusy] = useState(false);
  const run = useCallback(async (fn: () => Promise<void>) => {
    setBusy(true);
    try { await fn(); } catch (e) { toast(errMsg(e), 'err'); } finally { setBusy(false); }
  }, []);
  return { busy, run };
}

export function usePdfDoc(bytes: Uint8Array | null) {
  const [doc, setDoc] = useState<PdfDoc | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setDoc(null); setError(null);
    if (!bytes) return;
    let cancelled = false;
    let opened: PdfDoc | undefined;
    openPdf(bytes)
      .then((d) => { if (cancelled) { d.destroy(); return; } opened = d; setDoc(d); })
      .catch((e) => { if (!cancelled) setError(errMsg(e)); });
    return () => { cancelled = true; opened?.destroy(); };
  }, [bytes]);
  return { doc, error };
}

export function usePdfInput() {
  const [file, setFile] = useState<PdfFile | null>(null);
  const { busy, run } = useRun();
  const pick = useCallback((fs: File[]) => run(async () => {
    if (fs[0]) setFile(await readPdfFile(fs[0]));
  }), [run]);
  return { file, pick, clear: () => setFile(null), loading: busy };
}
