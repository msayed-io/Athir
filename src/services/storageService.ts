import { CategoryType } from '../components/send/CategoryTabs';
import { FileTuple, RecentTuple } from '../types';

export const CATEGORIZED_FILES: Record<CategoryType, FileTuple[]> = {
  apps: [],
  photos: [],
  audio: [],
  videos: [],
  contacts: [],
  files: [],
};

export const INITIAL_FILES_DATA: FileTuple[] = [];

export const INITIAL_RECENT_DATA: RecentTuple[] = [];

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

  getRecentTransfers(): RecentTuple[] {
    try {
      const raw = localStorage.getItem('recent_transfers');
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed as RecentTuple[];
        }
      }
    } catch {
      // Fallback
    }
    return [];
  },

  addRecentTransfer(item: RecentTuple): void {
    try {
      const list = this.getRecentTransfers();
      const updated = [item, ...list.filter((x) => x[0] !== item[0])].slice(0, 20);
      localStorage.setItem('recent_transfers', JSON.stringify(updated));
    } catch {
      // Fallback
    }
  },

  clearRecentTransfers(): void {
    try {
      localStorage.removeItem('recent_transfers');
    } catch {
      // Fallback
    }
  },
};
