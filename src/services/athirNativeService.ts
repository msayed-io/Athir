import QRCode from 'qrcode';
import { Capacitor, registerPlugin, PluginListenerHandle } from '@capacitor/core';
import { CategoryType } from '../components/send/CategoryTabs';
import { FileTuple, RecentTuple } from '../types';

export interface DeviceInfo {
  deviceId: string;
  displayName: string;
  androidVersion: string;
  sdkInt: number;
  localIp: string;
  wifiDirectSupported: boolean;
  hotspotSupported: boolean;
  wifiAwareSupported: boolean;
}

export interface PermissionStatus {
  mediaGranted: boolean;
  nearbyGranted: boolean;
  cameraGranted: boolean;
  notificationGranted: boolean;
  contactsGranted: boolean;
  allGranted: boolean;
  sdkInt: number;
}

export interface CatalogItem {
  id: string;
  name: string;
  sizeMB: number;
  sizeBytes: number;
  uri: string;
  mimeType: string;
  category: string;
  thumbnail?: string;
  extra?: string;
}

export interface ReceiveSession {
  deviceId: string;
  displayName: string;
  host: string;
  port: number;
  fingerprint: string;
  secret: string;
  qrPayload: string;
  qrDataUrl: string;
}

export interface DiscoveredPeer {
  peerId: string;
  peerName: string;
  host: string;
  port: number;
  fingerprint: string;
  secret: string;
}

export interface TransferProgressEvent {
  bytesTransferred: number;
  totalBytes: number;
  speedBytesPerSec: number;
  speedDisplay: string;
  currentFileName: string;
  fileIndex: number;
  totalFiles: number;
  percent: number;
  sentMB: number;
  totalMB: number;
}

export interface HistoryItem {
  id?: number;
  fileName: string;
  fileSizeDisplay: string;
  direction: string;
  peerName: string;
  status: string;
  timestamp: number;
}

export interface AthirNativePluginInterface {
  getDeviceInfo(): Promise<DeviceInfo>;
  setDisplayName(options: { name: string }): Promise<{ success: boolean; name: string }>;
  checkPermissions(): Promise<PermissionStatus>;
  requestPermissions(): Promise<{ requested: boolean }>;
  getMediaCatalog(options: { category: string; query?: string; limit?: number }): Promise<{ category: string; items: CatalogItem[]; count: number }>;
  updateSelection(options: { action: 'add' | 'remove' | 'toggle'; id: string; name: string; sizeMB: number; sizeBytes: number; uri: string; mimeType?: string; category?: string }): Promise<{ id: string; isSelected: boolean; totalCount: number; totalMB: number; totalBytes: number }>;
  getSelection(): Promise<{ items: CatalogItem[]; totalCount: number; totalMB: number; totalBytes: number }>;
  clearSelection(): Promise<{ success: boolean }>;
  startReceiving(): Promise<ReceiveSession>;
  stopReceiving(): Promise<{ success: boolean }>;
  startDiscovery(): Promise<{ discovering: boolean }>;
  stopDiscovery(): Promise<{ success: boolean }>;
  connectAndSend(options: { host?: string; port?: number; fingerprint?: string; secret?: string; qrPayload?: string }): Promise<{ started: boolean; itemCount: number }>;
  cancelTransfer(): Promise<{ cancelled: boolean }>;
  pauseTransfer(): Promise<{ paused: boolean }>;
  resumeTransfer(): Promise<{ resumed: boolean }>;
  sendChatMessage(options: { text: string }): Promise<{ sent: boolean }>;
  getTransferHistory(): Promise<{ records: HistoryItem[] }>;
  clearTransferHistory(): Promise<{ success: boolean }>;
  startScanner(): Promise<{ scanning: boolean }>;
  stopScanner(): Promise<{ stopped: boolean }>;
  addListener(eventName: string, listenerFunc: (data: any) => void): Promise<PluginListenerHandle>;
}

const AthirNative = registerPlugin<AthirNativePluginInterface>('AthirNative');

// Web mock state
let mockSessionSecret = 'athir_' + Math.random().toString(36).substring(2, 10);
const webListeners = new Map<string, Set<(data: any) => void>>();

function emitWebEvent(eventName: string, data: any) {
  const set = webListeners.get(eventName);
  if (set) {
    set.forEach((fn) => fn(data));
  }
}

export const athirNativeService = {
  isNative(): boolean {
    return Capacitor.isNativePlatform();
  },

  async generateQrCode(content: String): Promise<string> {
    try {
      return await QRCode.toDataURL(content as string, {
        margin: 2,
        width: 512,
        color: {
          dark: '#000000',
          light: '#ffffff',
        },
      });
    } catch (e) {
      console.error('Error generating QR code:', e);
      return '';
    }
  },

  async getDeviceInfo(): Promise<DeviceInfo> {
    if (this.isNative()) {
      try {
        return await AthirNative.getDeviceInfo();
      } catch (e) {
        console.warn('Native getDeviceInfo error, falling back:', e);
      }
    }
    return {
      deviceId: 'web-' + Math.random().toString(36).substring(2, 9),
      displayName: 'أثير (Web)',
      androidVersion: 'Web Client',
      sdkInt: 36,
      localIp: '127.0.0.1',
      wifiDirectSupported: false,
      hotspotSupported: false,
      wifiAwareSupported: false,
    };
  },

  async checkPermissions(): Promise<PermissionStatus> {
    if (this.isNative()) {
      try {
        return await AthirNative.checkPermissions();
      } catch (e) {
        console.warn('Native checkPermissions error:', e);
      }
    }
    return {
      mediaGranted: true,
      nearbyGranted: true,
      cameraGranted: true,
      notificationGranted: true,
      contactsGranted: true,
      allGranted: true,
      sdkInt: 36,
    };
  },

  async requestPermissions(): Promise<PermissionStatus> {
    if (this.isNative()) {
      try {
        await AthirNative.requestPermissions();
        return await AthirNative.checkPermissions();
      } catch (e) {
        console.warn('Native requestPermissions error:', e);
      }
    }
    return this.checkPermissions();
  },

  async getMediaCatalog(category: CategoryType, query?: string): Promise<CatalogItem[]> {
    if (this.isNative()) {
      try {
        const res = await AthirNative.getMediaCatalog({ category, query, limit: 120 });
        if (res && res.items && res.items.length > 0) {
          return res.items;
        }
      } catch (e) {
        console.warn('Native getMediaCatalog error:', e);
      }
    }
    return [];
  },

  async updateSelection(action: 'add' | 'remove' | 'toggle', item: CatalogItem) {
    if (this.isNative()) {
      try {
        return await AthirNative.updateSelection({
          action,
          id: item.id,
          name: item.name,
          sizeMB: item.sizeMB,
          sizeBytes: item.sizeBytes,
          uri: item.uri,
          mimeType: item.mimeType,
          category: item.category,
        });
      } catch (e) {
        console.warn('Native updateSelection error:', e);
      }
    }
    return { id: item.id, isSelected: action !== 'remove', totalCount: 1, totalMB: item.sizeMB, totalBytes: item.sizeBytes };
  },

  async getSelection(): Promise<{ items: CatalogItem[]; totalCount: number; totalMB: number; totalBytes: number }> {
    if (this.isNative()) {
      try {
        return await AthirNative.getSelection();
      } catch (e) {
        console.warn('Native getSelection error:', e);
      }
    }
    return { items: [], totalCount: 0, totalMB: 0, totalBytes: 0 };
  },

  async clearSelection(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.clearSelection();
      } catch (e) {
        console.warn('Native clearSelection error:', e);
      }
    }
  },

  async startReceiving(): Promise<ReceiveSession> {
    if (this.isNative()) {
      try {
        const session = await AthirNative.startReceiving();
        if (!session.qrDataUrl && session.qrPayload) {
          session.qrDataUrl = await this.generateQrCode(session.qrPayload);
        }
        return session;
      } catch (e) {
        console.warn('Native startReceiving error:', e);
      }
    }

    // Web fallback with real generated QR Data URL
    mockSessionSecret = 'athir_' + Math.random().toString(36).substring(2, 10);
    const mockQrPayload = JSON.stringify({
      v: 1,
      id: 'web-device',
      name: 'أثير (Web)',
      host: '127.0.0.1',
      port: 8888,
      fp: 'AA:BB:CC:DD:EE:FF',
      secret: mockSessionSecret,
      transport: 'lan',
    });

    const generatedQr = await this.generateQrCode(mockQrPayload);

    return {
      deviceId: 'web-device',
      displayName: 'أثير (Web)',
      host: '127.0.0.1',
      port: 8888,
      fingerprint: 'AA:BB:CC:DD:EE:FF',
      secret: mockSessionSecret,
      qrPayload: mockQrPayload,
      qrDataUrl: generatedQr,
    };
  },

  async stopReceiving(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.stopReceiving();
      } catch (e) {
        console.warn('Native stopReceiving error:', e);
      }
    }
  },

  async startDiscovery(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.startDiscovery();
      } catch (e) {
        console.warn('Native startDiscovery error:', e);
      }
    }
  },

  async stopDiscovery(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.stopDiscovery();
      } catch (e) {
        console.warn('Native stopDiscovery error:', e);
      }
    }
  },

  async connectAndSend(options: { host?: string; port?: number; fingerprint?: string; secret?: string; qrPayload?: string }): Promise<void> {
    if (this.isNative()) {
      await AthirNative.connectAndSend(options);
      return;
    }

    // Simulate transfer progress for Web preview
    let sent = 0;
    const total = 50 * 1024 * 1024;
    const timer = setInterval(() => {
      sent += 2.5 * 1024 * 1024;
      const pct = Math.min(100, Math.floor((sent / total) * 100));
      emitWebEvent('transferProgress', {
        bytesTransferred: sent,
        totalBytes: total,
        speedBytesPerSec: 15 * 1024 * 1024,
        speedDisplay: '15.0 MB/s',
        currentFileName: 'Athir_Data.zip',
        fileIndex: 0,
        totalFiles: 1,
        percent: pct,
        sentMB: Math.round((sent / (1024 * 1024)) * 10) / 10,
        totalMB: 50.0,
      });

      if (sent >= total) {
        clearInterval(timer);
        emitWebEvent('transferComplete', { success: true, isSender: true });
      }
    }, 200);
  },

  async cancelTransfer(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.cancelTransfer();
      } catch (e) {
        console.warn('Native cancelTransfer error:', e);
      }
    }
  },

  async pauseTransfer(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.pauseTransfer();
      } catch (e) {
        console.warn('Native pauseTransfer error:', e);
      }
    }
  },

  async resumeTransfer(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.resumeTransfer();
      } catch (e) {
        console.warn('Native resumeTransfer error:', e);
      }
    }
  },

  async sendChatMessage(text: string): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.sendChatMessage({ text });
      } catch (e) {
        console.warn('Native sendChatMessage error:', e);
      }
    }
  },

  async getTransferHistory(): Promise<HistoryItem[]> {
    if (this.isNative()) {
      try {
        const res = await AthirNative.getTransferHistory();
        return res.records;
      } catch (e) {
        console.warn('Native getTransferHistory error:', e);
      }
    }
    return [];
  },

  async clearTransferHistory(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.clearTransferHistory();
      } catch (e) {
        console.warn('Native clearTransferHistory error:', e);
      }
    }
  },

  async startScanner(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.startScanner();
      } catch (e) {
        console.warn('Native startScanner error:', e);
      }
    }
  },

  async stopScanner(): Promise<void> {
    if (this.isNative()) {
      try {
        await AthirNative.stopScanner();
      } catch (e) {
        console.warn('Native stopScanner error:', e);
      }
    }
  },

  onTransferProgress(callback: (event: TransferProgressEvent) => void): () => void {
    if (this.isNative()) {
      const handlePromise = AthirNative.addListener('transferProgress', callback);
      return () => {
        handlePromise.then((h) => h.remove());
      };
    } else {
      if (!webListeners.has('transferProgress')) webListeners.set('transferProgress', new Set());
      webListeners.get('transferProgress')!.add(callback);
      return () => {
        webListeners.get('transferProgress')?.delete(callback);
      };
    }
  },

  onTransferComplete(callback: (data: { success: boolean; isSender: boolean }) => void): () => void {
    if (this.isNative()) {
      const handlePromise = AthirNative.addListener('transferComplete', callback);
      return () => {
        handlePromise.then((h) => h.remove());
      };
    } else {
      if (!webListeners.has('transferComplete')) webListeners.set('transferComplete', new Set());
      webListeners.get('transferComplete')!.add(callback);
      return () => {
        webListeners.get('transferComplete')?.delete(callback);
      };
    }
  },

  onTransferError(callback: (data: { error: string }) => void): () => void {
    if (this.isNative()) {
      const handlePromise = AthirNative.addListener('transferError', callback);
      return () => {
        handlePromise.then((h) => h.remove());
      };
    }
    return () => {};
  },

  onDeviceDiscovered(callback: (peer: DiscoveredPeer) => void): () => void {
    if (this.isNative()) {
      const handlePromise = AthirNative.addListener('deviceDiscovered', callback);
      return () => {
        handlePromise.then((h) => h.remove());
      };
    }
    return () => {};
  },

  onDeviceLost(callback: (data: { peerId: string }) => void): () => void {
    if (this.isNative()) {
      const handlePromise = AthirNative.addListener('deviceLost', callback);
      return () => {
        handlePromise.then((h) => h.remove());
      };
    }
    return () => {};
  },

  onQrScanned(callback: (data: { rawContent: string; host?: string; port?: number; fingerprint?: string; secret?: string; peerName?: string; peerId?: string }) => void): () => void {
    if (this.isNative()) {
      const handlePromise = AthirNative.addListener('qrScanned', callback);
      return () => {
        handlePromise.then((h) => h.remove());
      };
    }
    return () => {};
  },

  onChatMessage(callback: (data: { message: string }) => void): () => void {
    if (this.isNative()) {
      const handlePromise = AthirNative.addListener('messageReceived', callback);
      return () => {
        handlePromise.then((h) => h.remove());
      };
    }
    return () => {};
  },
};
