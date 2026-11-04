import QRCode from 'qrcode';
import { Capacitor, registerPlugin, PluginListenerHandle } from '@capacitor/core';
import { CategoryType } from '../components/send/CategoryTabs';
import { FileTuple } from '../types';

/**
 * Bridge to the local `AthirNative` Capacitor plugin (Kotlin).
 *
 * Architecture rule enforced in this file: the WebView never simulates hardware,
 * network, filesystem, permissions or camera behaviour. When the app runs inside a
 * plain browser there is no native layer, so every hardware-backed capability
 * reports an explicit "native only" condition instead of inventing data.
 * File bytes never cross this bridge: only identifiers, URIs and aggregated
 * progress events are exchanged.
 */

export class NativeOnlyError extends Error {
  code = 'NATIVE_ONLY';
  constructor(capability: string) {
    super(`${capability} requires the Athir Android application (com.athir.datatransfer).`);
    this.name = 'NativeOnlyError';
  }
}

export interface DeviceInfo {
  deviceId: string;
  displayName: string;
  androidVersion: string;
  sdkInt: number;
  localIp: string;
  wifiDirectSupported: boolean;
  hotspotSupported: boolean;
  wifiAwareSupported: boolean;
  platform: 'native' | 'web';
}

export interface PermissionStatus {
  mediaGranted: boolean;
  nearbyGranted: boolean;
  cameraGranted: boolean;
  notificationGranted: boolean;
  contactsGranted: boolean;
  allGranted: boolean;
  sdkInt: number;
  supported: boolean;
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
  /** 'qr' when the pairing secret came from a scanned QR code, 'mdns' otherwise. */
  source: 'qr' | 'mdns';
  /** True when the peer was discovered without a pairing secret and a QR scan is required. */
  requiresQr: boolean;
  /** Physical transport announced inside the QR payload. */
  transport?: 'lan' | 'hotspot' | 'p2p';
  /** Local-only hotspot SSID (router-free mode). */
  ssid?: string;
  /** Local-only hotspot WPA2 passphrase (router-free mode). */
  psk?: string;
  /** Group owner Wi-Fi Direct device address (router-free mode). */
  p2pDeviceAddress?: string;
}

export interface OfflineTransportResult {
  transport: 'hotspot' | 'p2p';
  ssid: string;
  passphrase: string;
  host: string;
  p2pDeviceAddress: string;
  qrPayload: string;
  qrDataUrl: string;
}

export interface PickedDocument {
  id: string;
  name: string;
  sizeBytes: number;
  sizeMB: number;
  uri: string;
  mimeType: string;
  category: string;
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
  getMediaCatalog(options: { category: string; query?: string; limit?: number }): Promise<{
    category: string;
    items: CatalogItem[];
    count: number;
  }>;
  updateSelection(options: {
    action: 'add' | 'remove' | 'toggle';
    id: string;
    name: string;
    sizeMB: number;
    sizeBytes: number;
    uri: string;
    mimeType?: string;
    category?: string;
  }): Promise<{ id: string; isSelected: boolean; totalCount: number; totalMB: number; totalBytes: number }>;
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
  setScannerWindow(options: {
    x: number;
    y: number;
    width: number;
    height: number;
    density: number;
  }): Promise<{ applied: boolean }>;
  stopScanner(): Promise<{ stopped: boolean }>;
  startOfflineTransport(): Promise<OfflineTransportResult>;
  stopOfflineTransport(): Promise<{ success: boolean }>;
  joinOfflineTransport(options: { ssid?: string; psk?: string; p2pDeviceAddress?: string }): Promise<{
    joined: boolean;
    transport: string;
  }>;
  pickDocuments(): Promise<{ items: PickedDocument[]; count: number }>;
  addListener(eventName: string, listenerFunc: (data: any) => void): Promise<PluginListenerHandle>;
}

const AthirNative = registerPlugin<AthirNativePluginInterface>('AthirNative');

/** Every hardware-backed call funnels through here so browser behaviour stays explicit. */
function requireNative(capability: string): void {
  if (!Capacitor.isNativePlatform()) {
    throw new NativeOnlyError(capability);
  }
}

function subscribeNative<T>(eventName: string, callback: (data: T) => void): () => void {
  const handlePromise = AthirNative.addListener(eventName, callback);
  return () => {
    handlePromise.then((h) => h.remove()).catch(() => {});
  };
}

/**
 * Session cache for the media catalog. The send screen used to re-query MediaStore and
 * re-decode every thumbnail on each visit, which is what flashed "جارٍ التحميل" every time.
 */
const catalogCache = new Map<string, CatalogItem[]>();

export const athirNativeService = {
  isNative(): boolean {
    return Capacitor.isNativePlatform();
  },

  /** Real QR image generation (used only as a fallback when the native encoder is unavailable). */
  async generateQrCode(content: string): Promise<string> {
    try {
      return await QRCode.toDataURL(content, {
        margin: 2,
        width: 512,
        color: { dark: '#000000', light: '#ffffff' },
      });
    } catch (e) {
      console.error('Error generating QR code:', e);
      return '';
    }
  },

  async getDeviceInfo(): Promise<DeviceInfo> {
    requireNative('Device identity');
    return AthirNative.getDeviceInfo();
  },

  async checkPermissions(): Promise<PermissionStatus> {
    if (!this.isNative()) {
      return {
        mediaGranted: false,
        nearbyGranted: false,
        cameraGranted: false,
        notificationGranted: false,
        contactsGranted: false,
        allGranted: false,
        sdkInt: 0,
        supported: false,
      };
    }
    const status = await AthirNative.checkPermissions();
    return { ...status, supported: true };
  },

  async requestPermissions(): Promise<PermissionStatus> {
    requireNative('Runtime permissions');
    await AthirNative.requestPermissions();
    // The authoritative answer is pushed through `permissionsChanged`; this immediate
    // read is only a best-effort snapshot and must never be treated as the final state.
    return { ...(await AthirNative.checkPermissions()), supported: true };
  },

  async getMediaCatalog(category: CategoryType, query?: string): Promise<CatalogItem[]> {
    if (!this.isNative()) {
      // No fabrication: a browser has no device media catalog to enumerate.
      return [];
    }
    const key = `${category}|${query ?? ''}`;
    const cached = catalogCache.get(key);
    if (cached) return cached;

    const res = await AthirNative.getMediaCatalog({ category, query, limit: 120 });
    const items = res?.items ?? [];
    catalogCache.set(key, items);
    return items;
  },

  /** Drops the session catalog cache (after a transfer, or on explicit refresh). */
  invalidateCatalogCache(): void {
    catalogCache.clear();
  },

  /** True when a category is already cached, so the screen can render without a spinner. */
  hasCachedCatalog(category: CategoryType, query?: string): boolean {
    return catalogCache.has(`${category}|${query ?? ''}`);
  },

  async updateSelection(action: 'add' | 'remove' | 'toggle', item: CatalogItem) {
    if (!this.isNative()) {
      return {
        id: item.id,
        isSelected: action !== 'remove',
        totalCount: 0,
        totalMB: 0,
        totalBytes: 0,
      };
    }
    return AthirNative.updateSelection({
      action,
      id: item.id,
      name: item.name,
      sizeMB: item.sizeMB,
      sizeBytes: item.sizeBytes,
      uri: item.uri,
      mimeType: item.mimeType,
      category: item.category,
    });
  },

  async getSelection(): Promise<{ items: CatalogItem[]; totalCount: number; totalMB: number; totalBytes: number }> {
    if (!this.isNative()) {
      return { items: [], totalCount: 0, totalMB: 0, totalBytes: 0 };
    }
    return AthirNative.getSelection();
  },

  async clearSelection(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.clearSelection();
  },

  async startReceiving(): Promise<ReceiveSession> {
    requireNative('Starting a receiving session');
    const session = await AthirNative.startReceiving();
    if (!session.qrDataUrl && session.qrPayload) {
      session.qrDataUrl = await this.generateQrCode(session.qrPayload);
    }
    return session;
  },

  async stopReceiving(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.stopReceiving();
  },

  async startDiscovery(): Promise<void> {
    requireNative('Local device discovery');
    await AthirNative.startDiscovery();
  },

  async stopDiscovery(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.stopDiscovery();
  },

  async connectAndSend(options: {
    host?: string;
    port?: number;
    fingerprint?: string;
    secret?: string;
    qrPayload?: string;
  }): Promise<void> {
    requireNative('Peer-to-peer transfer');
    await AthirNative.connectAndSend(options);
  },

  async cancelTransfer(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.cancelTransfer().catch(() => {});
  },

  async pauseTransfer(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.pauseTransfer().catch(() => {});
  },

  async resumeTransfer(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.resumeTransfer().catch(() => {});
  },

  async sendChatMessage(text: string): Promise<void> {
    if (!this.isNative()) {
      throw new NativeOnlyError('Chat over the transfer session');
    }
    await AthirNative.sendChatMessage({ text });
  },

  async getTransferHistory(): Promise<HistoryItem[]> {
    if (!this.isNative()) {
      return [];
    }
    const res = await AthirNative.getTransferHistory();
    return res?.records ?? [];
  },

  async clearTransferHistory(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.clearTransferHistory();
  },

  async startScanner(): Promise<void> {
    requireNative('QR scanning');
    await AthirNative.startScanner();
  },

  async stopScanner(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.stopScanner();
  },

  onTransferProgress(callback: (event: TransferProgressEvent) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('transferProgress', callback);
  },

  onTransferComplete(callback: (data: { success: boolean; isSender: boolean }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('transferComplete', callback);
  },

  onTransferError(callback: (data: { error: string }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('transferError', callback);
  },

  onFileIntegrity(callback: (data: { fileName: string; verified: boolean }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('fileIntegrity', callback);
  },

  /** Real camera/scanner failures (missing permission, camera unavailable, bind failure). */
  onScanError(callback: (data: { error: string }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('scanError', callback);
  },

  /** preparing | running | error - the real state of the native camera window. */
  onCameraState(callback: (data: { state: string; detail: string }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('cameraState', callback);
  },

  async setScannerWindow(rect: {
    x: number;
    y: number;
    width: number;
    height: number;
    density: number;
  }): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.setScannerWindow(rect);
  },

  onPermissionsChanged(callback: (status: PermissionStatus) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('permissionsChanged', (data: PermissionStatus) =>
      callback({ ...data, supported: true })
    );
  },

  onDeviceDiscovered(callback: (peer: DiscoveredPeer) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('deviceDiscovered', callback);
  },

  onDeviceLost(callback: (data: { peerId: string }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('deviceLost', callback);
  },

  onQrScanned(
    callback: (data: {
      rawContent: string;
      host?: string;
      port?: number;
      fingerprint?: string;
      secret?: string;
      peerName?: string;
      peerId?: string;
      source?: string;
      transport?: 'lan' | 'hotspot' | 'p2p';
      ssid?: string;
      psk?: string;
      p2pDeviceAddress?: string;
    }) => void
  ): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('qrScanned', callback);
  },

  onChatMessage(callback: (data: { message: string }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('messageReceived', callback);
  },

  /** Emitted when a transfer continues from a previously received byte offset. */
  onTransferResumed(callback: (data: { fileName: string; offsetBytes: number }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('transferResumed', callback);
  },

  /** Emitted when either side cancels the session over the control channel. */
  onTransferCancelled(callback: (data: { byPeer: boolean }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('transferCancelled', callback);
  },

  onTransferPaused(callback: (data: { paused: boolean }) => void): () => void {
    if (!this.isNative()) return () => {};
    return subscribeNative('transferPaused', callback);
  },

  // ---- router-free transport (Wi-Fi Direct / local-only hotspot) ----

  async startOfflineTransport(): Promise<OfflineTransportResult> {
    requireNative('Router-free transfer setup');
    return AthirNative.startOfflineTransport();
  },

  async stopOfflineTransport(): Promise<void> {
    if (!this.isNative()) return;
    await AthirNative.stopOfflineTransport().catch(() => {});
  },

  async joinOfflineTransport(options: {
    ssid?: string;
    psk?: string;
    p2pDeviceAddress?: string;
  }): Promise<void> {
    requireNative('Joining a router-free network');
    await AthirNative.joinOfflineTransport(options);
  },

  // ---- Storage Access Framework picker ----

  async pickDocuments(): Promise<PickedDocument[]> {
    requireNative('Document picking');
    const res = await AthirNative.pickDocuments();
    return res?.items ?? [];
  },
};

export type { FileTuple };
