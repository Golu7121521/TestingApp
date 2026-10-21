import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, Info } from 'lucide-react';

type Kind = 'ok' | 'err' | 'info';
interface T { id: number; msg: string; kind: Kind }
const listeners = new Set<(t: T) => void>();
let n = 0;

export const toast = (msg: string, kind: Kind = 'info') => {
  const t = { id: ++n, msg, kind };
  listeners.forEach((l) => l(t));
};

export function Toaster() {
  const [items, setItems] = useState<T[]>([]);
  useEffect(() => {
    const l = (t: T) => {
      setItems((i) => [...i, t]);
      setTimeout(() => setItems((i) => i.filter((x) => x.id !== t.id)), 5000);
    };
    listeners.add(l);
    return () => { listeners.delete(l); };
  }, []);
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-center gap-2 px-4 md:bottom-6" role="status" aria-live="polite">
      {items.map((t) => (
        <div key={t.id} className="pointer-events-auto flex max-w-md items-start gap-2 rounded-xl bg-slate-900 px-4 py-3 text-sm text-white shadow-lg dark:bg-slate-100 dark:text-slate-900">
          {t.kind === 'err' ? <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-400" /> : t.kind === 'ok' ? <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" /> : <Info className="mt-0.5 h-4 w-4 shrink-0" />}
          <span>{t.msg}</span>
        </div>
      ))}
    </div>
  );
}
