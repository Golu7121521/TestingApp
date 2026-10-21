import { Capacitor } from '@capacitor/core';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { addRecent } from './storage';

function toBase64(blob: Blob): Promise<string> {
  return new Promise((res, rej) => {
    const r = new FileReader();
    r.onload = () => res(String(r.result).split(',')[1] ?? '');
    r.onerror = () => rej(r.error);
    r.readAsDataURL(blob);
  });
}

/** Web: browser download. Android app: write to cache and open the share sheet (Save to Files / Drive / WhatsApp...). */
export async function saveOutput(data: Uint8Array | Blob, name: string, mime: string, kind: string): Promise<void> {
  const blob = data instanceof Blob ? data : new Blob([data as BlobPart], { type: mime });
  if (Capacitor.isNativePlatform()) {
    const path = `${Date.now()}-${name.replace(/[^\w.\- ]+/g, '_')}`;
    await Filesystem.writeFile({ path, data: await toBase64(blob), directory: Directory.Cache });
    const { uri } = await Filesystem.getUri({ path, directory: Directory.Cache });
    try {
      await Share.share({ title: name, url: uri, dialogTitle: 'Save or share' });
    } catch (e) {
      if (!/cancel/i.test(String((e as Error)?.message ?? e))) throw e;
      return;
    }
  } else {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = name;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  }
  addRecent({ name, size: blob.size, kind });
}
