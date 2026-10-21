export interface RecentItem { id: string; name: string; size: number; kind: string; date: number }
export interface Settings { theme: 'light' | 'dark' | 'system'; quality: number }

const RECENT_KEY = 'pdfpro.recents';
const SETTINGS_KEY = 'pdfpro.settings';
const defaults: Settings = { theme: 'system', quality: 0.85 };

function read<T>(key: string, fallback: T): T {
  try {
    const v = localStorage.getItem(key);
    return v ? (JSON.parse(v) as T) : fallback;
  } catch { return fallback; }
}
function write(key: string, value: unknown) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage unavailable */ }
}

export const getSettings = (): Settings => ({ ...defaults, ...read<Partial<Settings>>(SETTINGS_KEY, {}) });
export const saveSettings = (s: Settings) => write(SETTINGS_KEY, s);
export const getRecents = (): RecentItem[] => read<RecentItem[]>(RECENT_KEY, []);
export const clearRecents = () => write(RECENT_KEY, []);
export function addRecent(item: Omit<RecentItem, 'id' | 'date'>) {
  const list = [{ ...item, id: Math.random().toString(36).slice(2), date: Date.now() }, ...getRecents()].slice(0, 30);
  write(RECENT_KEY, list);
}

export function applyTheme(theme: Settings['theme']) {
  const dark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.classList.toggle('dark', dark);
}
