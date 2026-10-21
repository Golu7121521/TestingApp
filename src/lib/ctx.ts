import { createContext } from 'react';
import type { Settings } from './storage';

export const SettingsCtx = createContext<{ settings: Settings; update: (p: Partial<Settings>) => void }>({
  settings: { theme: 'system', quality: 0.85 },
  update: () => {},
});
