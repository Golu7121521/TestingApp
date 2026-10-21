import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { App as CapApp } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { ArrowLeft, FileText, HelpCircle, Home as HomeIcon, Moon, Search, Settings as SettingsIcon, Sun } from 'lucide-react';
import { TOOLS, type ToolId } from './tools/registry';
import { Home, SettingsPage } from './pages';
import { Toaster } from './components/toast';
import { ErrorBoundary } from './components/ErrorBoundary';
import { SettingsCtx } from './lib/ctx';
import { applyTheme, getSettings, saveSettings, type Settings } from './lib/storage';
import { isDirty, setDirty } from './lib/dirty';

type Route = 'home' | 'settings' | ToolId;

export default function App() {
  const [settings, setSettings] = useState<Settings>(getSettings);
  const [route, setRoute] = useState<Route>('home');
  const [query, setQuery] = useState('');
  const routeRef = useRef(route);
  routeRef.current = route;

  useEffect(() => {
    applyTheme(settings.theme);
    if (settings.theme !== 'system') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const h = () => applyTheme('system');
    mq.addEventListener('change', h);
    return () => mq.removeEventListener('change', h);
  }, [settings.theme]);

  const update = useCallback((p: Partial<Settings>) => {
    setSettings((s) => { const n = { ...s, ...p }; saveSettings(n); return n; });
  }, []);

  const go = useCallback((r: Route) => {
    if (isDirty() && !window.confirm('You have unsaved changes. Leave this screen and discard them?')) return;
    setDirty(false);
    setRoute(r);
  }, []);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const sub = CapApp.addListener('backButton', () => {
      if (routeRef.current !== 'home') go('home'); else CapApp.exitApp();
    });
    return () => { sub.then((s) => s.remove()); };
  }, [go]);

  const ctx = useMemo(() => ({ settings, update }), [settings, update]);
  const tool = TOOLS.find((t) => t.id === route);
  const dark = document.documentElement.classList.contains('dark');

  const nav = [
    { id: 'home' as Route, label: 'Home', icon: HomeIcon },
    { id: 'viewer' as Route, label: 'Open PDF', icon: FileText },
    { id: 'settings' as Route, label: 'Settings', icon: SettingsIcon },
  ];

  let content;
  if (route === 'home') content = <div className="mx-auto max-w-5xl p-4 md:p-6"><Home query={query} onOpen={go} /></div>;
  else if (route === 'settings') content = <div className="mx-auto max-w-5xl p-4 md:p-6"><SettingsPage /></div>;
  else if (tool?.Comp && tool.full) content = <tool.Comp />;
  else if (tool?.Comp) {
    content = (
      <div className="mx-auto max-w-3xl p-4 md:p-6">
        <h1 className="text-2xl font-semibold">{tool.title}</h1>
        <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">{tool.desc}</p>
        <tool.Comp />
      </div>
    );
  }

  return (
    <SettingsCtx.Provider value={ctx}>
      <div className="flex h-full">
        <aside className="hidden w-56 shrink-0 flex-col gap-1 border-r border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-900 md:flex">
          <div className="mb-3 flex items-center gap-2 px-2 py-1">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-red-600 text-xs font-bold text-white">PDF</span>
            <span className="font-semibold">PDF Pro</span>
          </div>
          {nav.map((n) => (
            <button key={n.id} onClick={() => go(n.id)} aria-current={route === n.id ? 'page' : undefined}
              className={`flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium ${route === n.id ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950/50 dark:text-indigo-300' : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'}`}>
              <n.icon className="h-4 w-4" /> {n.label}
            </button>
          ))}
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex items-center gap-2 border-b border-slate-200 bg-white px-3 py-2 dark:border-slate-800 dark:bg-slate-900">
            {route !== 'home' && <button className="icon-btn" aria-label="Back to home" onClick={() => go('home')}><ArrowLeft className="h-5 w-5" /></button>}
            <span className="flex h-7 w-7 items-center justify-center rounded-md bg-red-600 text-[10px] font-bold text-white md:hidden">PDF</span>
            <div className="relative min-w-0 flex-1 md:max-w-md">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden />
              <input type="search" aria-label="Search tools and exported files" placeholder="Search tools and exports"
                className="input !pl-9" value={query}
                onChange={(e) => { setQuery(e.target.value); if (route !== 'home') go('home'); }} />
            </div>
            <button className="icon-btn ml-auto" aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'} title="Toggle theme"
              onClick={() => update({ theme: dark ? 'light' : 'dark' })}>
              {dark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
            </button>
            <button className="icon-btn" aria-label="Help and limitations" title="Help" onClick={() => go('settings')}><HelpCircle className="h-5 w-5" /></button>
          </header>

          <main className="relative min-h-0 flex-1 overflow-y-auto">
            <ErrorBoundary key={route}>{content}</ErrorBoundary>
          </main>

          <nav className="flex border-t border-slate-200 bg-white md:hidden dark:border-slate-800 dark:bg-slate-900" style={{ paddingBottom: 'env(safe-area-inset-bottom)' }} aria-label="Primary">
            {nav.map((n) => (
              <button key={n.id} onClick={() => go(n.id)} aria-current={route === n.id ? 'page' : undefined}
                className={`flex flex-1 flex-col items-center gap-0.5 py-2 text-xs ${route === n.id ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-500'}`}>
                <n.icon className="h-5 w-5" /> {n.label}
              </button>
            ))}
          </nav>
        </div>
      </div>
      <Toaster />
    </SettingsCtx.Provider>
  );
}
