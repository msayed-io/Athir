import { FileTuple } from '../types';

/**
 * Selection basket hand-off between screens.
 *
 * This is the only remaining browser-storage usage in the app and it stores nothing
 * but the identifiers/URIs of files the user explicitly picked in the current session.
 * The authoritative copy of the selection lives in the native `SelectionStore`; this
 * mirror exists so the UI can survive a WebView reload while keeping the same
 * one-screen-at-a-time routing model.
 *
 * Transfer history is NOT stored here: it is persisted by the native SQLite
 * `HistoryStore` from real completed transfers only.
 */
export const storageService = {
  getStoredSelection(): FileTuple[] | null {
    try {
      const raw = sessionStorage.getItem('sel');
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed as FileTuple[];
      }
    } catch {
      // Ignore sessionStorage errors
    }
    return null;
  },

  setStoredSelection(items: FileTuple[]): void {
    try {
      sessionStorage.setItem('sel', JSON.stringify(items));
    } catch {
      // Ignore sessionStorage errors
    }
  },

  clearStoredSelection(): void {
    try {
      sessionStorage.removeItem('sel');
    } catch {
      // Ignore
    }
  },
};
