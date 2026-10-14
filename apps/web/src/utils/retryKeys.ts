const prefix = 'kinetic:retry:';
const fallback = new Map<string, string>();

/** Retain a key after network failures so a retry cannot create a second checkout/payment. */
export function retryKey(scope: string): string {
  const name = prefix + scope;
  try {
    const existing = sessionStorage.getItem(name);
    if (existing) return existing;
  } catch {
    /* An in-memory key still protects double clicks when storage is unavailable. */
  }
  const key = fallback.get(name) ?? crypto.randomUUID();
  fallback.set(name, key);
  try {
    sessionStorage.setItem(name, key);
  } catch {
    /* Use the in-memory fallback. */
  }
  return key;
}

export function forgetKey(scope: string): void {
  const name = prefix + scope;
  fallback.delete(name);
  try {
    sessionStorage.removeItem(name);
  } catch {
    /* Storage is optional. */
  }
}

export function clearCheckoutKeys(): void {
  for (const key of fallback.keys()) {
    if (key.startsWith(prefix + 'checkout:')) fallback.delete(key);
  }
  try {
    Object.keys(sessionStorage)
      .filter((key) => key.startsWith(prefix + 'checkout:'))
      .forEach((key) => sessionStorage.removeItem(key));
  } catch {
    /* Storage is optional. */
  }
}
