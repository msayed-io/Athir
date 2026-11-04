export type ScreenRoute = 'home' | 'send' | 'prepare' | 'receive' | 'transfer' | 'invite' | 'history' | 'scan';

export type FileTuple = [name: string, sizeMB: number];

export type RecentTuple = [name: string, sizeDisplay: string, statusText: string];

export interface FileItem {
  name: string;
  size: number;
}
