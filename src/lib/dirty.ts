let dirty = false;
export const setDirty = (v: boolean) => { dirty = v; };
export const isDirty = () => dirty;
window.addEventListener('beforeunload', (e) => { if (dirty) { e.preventDefault(); e.returnValue = ''; } });
