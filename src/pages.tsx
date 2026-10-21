import { useContext, useState } from 'react';
import { FileText, Trash2 } from 'lucide-react';
import { TOOLS, type Tool, type ToolId } from './tools/registry';
import { SettingsCtx } from './lib/ctx';
import { clearRecents, getRecents } from './lib/storage';
import { formatBytes, formatDate } from './lib/utils';

export function Home({ query, onOpen }: { query: string; onOpen: (id: ToolId) => void }) {
  const [recents, setRecents] = useState(getRecents);
  const q = query.trim().toLowerCase();
  const tools = TOOLS.filter((t) => t.id !== 'viewer' && (!q || (t.title + ' ' + t.desc).toLowerCase().includes(q)));
  const items = recents.filter((r) => !q || r.name.toLowerCase().includes(q));
  const hour = new Date().getHours();
  const greet = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{greet}</h1>
          <p className="text-sm text-slate-600 dark:text-slate-400">Pick a tool. Files are processed on this device.</p>
        </div>
        <button className="btn btn-primary" onClick={() => onOpen('viewer')}><FileText className="h-4 w-4" /> Open PDF</button>
      </div>

      <section aria-label="Tools">
        {!tools.length && <p className="card p-6 text-center text-sm">No tools match “{query}”. Try “merge”, “split” or “sign”.</p>}
        <ul className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
          {tools.map((t: Tool) => (
            <li key={t.id}>
              <button
                onClick={() => !t.unavailable && onOpen(t.id)}
                disabled={!!t.unavailable}
                title={t.unavailable}
                className="card flex h-full w-full flex-col items-start gap-2 p-4 text-left transition enabled:hover:border-indigo-400 disabled:opacity-60"
              >
                <t.icon className={`h-6 w-6 ${t.id === 'delete' ? 'text-red-600' : 'text-indigo-600'}`} aria-hidden />
                <span className="text-sm font-semibold">{t.title}</span>
                <span className="hint">{t.unavailable ?? t.desc}</span>
                {t.unavailable && <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-xs font-medium dark:bg-slate-800">Not available</span>}
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section aria-label="Recent exports">
        <div className="mb-2 flex items-center justify-between">
          <h2 className="font-semibold">Recent exports</h2>
          {recents.length > 0 && <button className="btn btn-ghost !px-3 !py-1.5" onClick={() => { clearRecents(); setRecents([]); }}><Trash2 className="h-4 w-4" /> Clear list</button>}
        </div>
        {items.length === 0 ? (
          <p className="card p-6 text-center text-sm text-slate-600 dark:text-slate-400">{q ? 'No exports match your search.' : 'Nothing exported yet. Files you save from any tool will be listed here (name and size only).'}</p>
        ) : (
          <ul className="card divide-y divide-slate-200 dark:divide-slate-800">
            {items.map((r) => (
              <li key={r.id} className="flex items-center gap-3 p-3">
                <FileText className="h-6 w-6 shrink-0 text-red-500" aria-hidden />
                <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{r.name}</p><p className="hint">{r.kind} · {formatBytes(r.size)} · {formatDate(r.date)}</p></div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

export function SettingsPage() {
  const { settings, update } = useContext(SettingsCtx);
  const [count, setCount] = useState(getRecents().length);
  return (
    <div className="max-w-2xl space-y-6">
      <h1 className="text-2xl font-semibold">Settings</h1>
      <section className="card space-y-4 p-4">
        <fieldset>
          <legend className="label">Theme</legend>
          <div className="flex gap-4">
            {(['system', 'light', 'dark'] as const).map((t) => (
              <label key={t} className="flex items-center gap-2 text-sm capitalize"><input type="radio" checked={settings.theme === t} onChange={() => update({ theme: t })} /> {t}</label>
            ))}
          </div>
        </fieldset>
        <label className="block">
          <span className="label">Default export quality: {Math.round(settings.quality * 100)}%</span>
          <input type="range" min={0.5} max={1} step={0.05} value={settings.quality} onChange={(e) => update({ quality: Number(e.target.value) })} className="w-full" />
          <span className="hint">Used for JPG output, Images to PDF and the Balanced compression preset.</span>
        </label>
      </section>
      <section className="card space-y-2 p-4 text-sm">
        <h2 className="font-semibold">Privacy</h2>
        <p>All PDF processing happens on your device. Documents are never uploaded and the app does not log file contents or signatures.</p>
        <p>Only app settings and a list of exported file names and sizes are stored locally. Clearing app or browser data removes them.</p>
        <p>Source files are held in memory only while a tool is open; they are not kept between sessions.</p>
      </section>
      <section className="card space-y-3 p-4 text-sm">
        <h2 className="font-semibold">Storage</h2>
        <p>{count} export record(s) stored on this device.</p>
        <button className="btn btn-ghost" onClick={() => { clearRecents(); setCount(0); }}>Clear export list</button>
      </section>
      <section className="card space-y-2 p-4 text-sm">
        <h2 className="font-semibold">Help and limitations</h2>
        <ul className="list-disc space-y-1 pl-5">
          <li>Encrypted / password-protected PDFs cannot be opened or unlocked.</li>
          <li>Signatures are visual images, not cryptographic digital signatures.</li>
          <li>Compression presets that re-render pages remove selectable text.</li>
          <li>Protect, Unlock, OCR, Annotate and PDF-to-Word/Excel/PowerPoint are not available.</li>
          <li>On Android, “Save / Share” opens the share sheet so you can save to Files, Drive, etc.</li>
        </ul>
      </section>
    </div>
  );
}
