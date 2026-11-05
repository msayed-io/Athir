import React, { useState, useEffect, useRef, useCallback } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import {
  athirNativeService,
  DiscoveredPeer,
  NativeOnlyError,
} from '../services/athirNativeService';
import './ScanScreen.css';

export const ScanScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'فحص للاتصال';
  }, []);

  const [discoveredPeer, setDiscoveredPeer] = useState<DiscoveredPeer | null>(null);
  const [supported, setSupported] = useState(athirNativeService.isNative());
  const [statusMessage, setStatusMessage] = useState<string>('');
  const [connectError, setConnectError] = useState<string>('');
  const [connecting, setConnecting] = useState(false);
  const [cameraState, setCameraState] = useState<'idle' | 'preparing' | 'running' | 'error'>('idle');
  const mountedRef = useRef(true);
  const camWindowRef = useRef<HTMLDivElement | null>(null);

  /** Reports the real on-screen rect of the camera window to the native layer. */
  const reportCameraWindow = useCallback(() => {
    const el = camWindowRef.current;
    if (!el || !athirNativeService.isNative()) return;
    const rect = el.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    athirNativeService
      .setScannerWindow({
        x: rect.left,
        y: rect.top,
        width: rect.width,
        height: rect.height,
        density: window.devicePixelRatio || 1,
      })
      .catch(() => {});
  }, []);

  const handlePeerFound = useCallback((peer: DiscoveredPeer) => {
    if (!mountedRef.current) return;
    setDiscoveredPeer((current) => {
      // A QR pairing always wins over an anonymous mDNS sighting of the same device.
      if (current && current.secret && !peer.secret) return current;
      return peer;
    });
  }, []);

  /**
   * Waits for the authoritative post-dialog permission state instead of guessing.
   *
   * This is NOT a discovery fallback: it never sets a peer, never marks a device as found and
   * never navigates anywhere. It only waits for the system dialog to be answered - the event
   * is the primary signal, and a state poll is the safety net in case an OEM drops the event.
   * The camera starts only after the real grant is read back from Android.
   */
  const waitForPermissionAnswer = useCallback(
    () =>
      new Promise<void>((resolve) => {
        let settled = false;
        let poll: ReturnType<typeof setInterval> | undefined;
        const finish = () => {
          if (settled) return;
          settled = true;
          cleanup();
          if (poll) clearInterval(poll);
          resolve();
        };
        const cleanup = athirNativeService.onPermissionsChanged(() => finish());
        poll = setInterval(() => {
          athirNativeService
            .checkPermissions()
            .then((status) => {
              if (status.cameraGranted) finish();
            })
            .catch(() => {});
        }, 700);
      }),
    []
  );

  const startCamera = useCallback(async () => {
    try {
      reportCameraWindow();
      let status = await athirNativeService.checkPermissions().catch(() => null);
      if (status && !status.cameraGranted) {
        // The camera permission is requested here only if the user skipped the
        // first-launch batch; the answer is read back from the real system state.
        // Camera + nearby devices only: this is a per-session hardware grant, requested
        // again on every visit to the scan screen.
        await athirNativeService.requestSessionPermissions().catch(() => {});
        await waitForPermissionAnswer();
        status = await athirNativeService.checkPermissions().catch(() => null);
        if (status && !status.cameraGranted) {
          if (mountedRef.current) {
            setStatusMessage(
              'إذن الكاميرا غير ممنوح — لا يمكن مسح رمز QR. فعّله من إعدادات التطبيق ثم أعد المحاولة.'
            );
          }
          return;
        }
      }
      await athirNativeService.startScanner();
      reportCameraWindow();
    } catch (e) {
      if (!mountedRef.current) return;
      const message = e instanceof Error ? e.message : '';
      setStatusMessage(
        message.includes('CAMERA_PERMISSION_REQUIRED')
          ? 'إذن الكاميرا غير ممنوح — لا يمكن مسح رمز QR.'
          : 'تعذّر تشغيل الكاميرا. تحقّق من إذن الكاميرا.'
      );
    }
  }, [waitForPermissionAnswer, reportCameraWindow]);

  useEffect(() => {
    mountedRef.current = true;

    if (!athirNativeService.isNative()) {
      setSupported(false);
      setStatusMessage('الماسح والاكتشاف يعملان داخل تطبيق أندرويد فقط.');
      return () => {
        mountedRef.current = false;
      };
    }

    const cleanupCameraState = athirNativeService.onCameraState((data) => {
      if (!mountedRef.current) return;
      setCameraState(
        data.state === 'running'
          ? 'running'
          : data.state === 'preparing'
          ? 'preparing'
          : 'error'
      );
      if (data.state === 'error' && data.detail && data.detail !== 'permission') {
        setStatusMessage(`تعذّر تشغيل الكاميرا: ${data.detail}`);
      }
    });

    const cleanupScanError = athirNativeService.onScanError((data) => {
      if (!mountedRef.current) return;
      setStatusMessage(
        data.error.toLowerCase().includes('permission')
          ? 'إذن الكاميرا غير ممنوح — لا يمكن مسح رمز QR.'
          : `تعذّر تشغيل الكاميرا: ${data.error}`
      );
    });

    // Native CameraX + ML Kit QR scanning and mDNS discovery.
    void startCamera();

    const onViewportChange = () => reportCameraWindow();
    window.addEventListener('resize', onViewportChange);
    window.addEventListener('orientationchange', onViewportChange);
    // The frame element must be laid out before its rect means anything.
    const rafId = requestAnimationFrame(() => reportCameraWindow());
    athirNativeService.startDiscovery().catch(() => {
      setStatusMessage('تعذّر بدء اكتشاف الأجهزة القريبة.');
    });

    const cleanupQr = athirNativeService.onQrScanned((data) => {
      if (!data.host || !data.port || !data.fingerprint || !data.secret) {
        setStatusMessage('الرمز الممسوح لا يحتوي بيانات اقتران صالحة.');
        return;
      }
      handlePeerFound({
        peerId: data.peerId || 'peer',
        peerName: data.peerName || 'جهاز مستلم',
        host: data.host,
        port: data.port,
        fingerprint: data.fingerprint,
        secret: data.secret,
        source: 'qr',
        requiresQr: false,
        transport: (data.transport as 'lan' | 'hotspot' | 'p2p') ?? 'lan',
        ssid: data.ssid,
        psk: data.psk,
        p2pDeviceAddress: data.p2pDeviceAddress,
      });
    });

    const cleanupPeer = athirNativeService.onDeviceDiscovered((peer) => {
      handlePeerFound({ ...peer, source: peer.source ?? 'mdns' });
    });

    return () => {
      mountedRef.current = false;
      cleanupCameraState();
      cancelAnimationFrame(rafId);
      window.removeEventListener('resize', onViewportChange);
      window.removeEventListener('orientationchange', onViewportChange);
      cleanupQr();
      cleanupPeer();
      cleanupScanError();
      athirNativeService.stopScanner().catch(() => {});
      athirNativeService.stopDiscovery().catch(() => {});
    };
  }, [handlePeerFound, startCamera, reportCameraWindow]);

  const handleConnect = async () => {
    if (!discoveredPeer || !discoveredPeer.secret || connecting) return;
    setConnectError('');
    setConnecting(true);
    try {
      // Router-free QR payloads carry the physical link credentials: join first,
      // then open the encrypted session over the resulting network.
      const needsOfflineLink =
        discoveredPeer.transport === 'hotspot' || discoveredPeer.transport === 'p2p';
      if (needsOfflineLink) {
        await athirNativeService.joinOfflineTransport({
          ssid: discoveredPeer.ssid,
          psk: discoveredPeer.psk,
          p2pDeviceAddress: discoveredPeer.p2pDeviceAddress,
        });
      }

      // A fixed "sleep 1.5s and hope DHCP is done" wait was the reason a real pairing could
      // fail on one phone and work on another. Instead, the connection itself is the probe:
      // it is retried with a short backoff while the network finishes negotiating, and it
      // only succeeds when the TLS session is actually established.
      const attempts = needsOfflineLink ? 5 : 1;
      let lastError: unknown = null;
      for (let attempt = 0; attempt < attempts; attempt += 1) {
        try {
          await athirNativeService.connectAndSend({
            host: discoveredPeer.host,
            port: discoveredPeer.port,
            fingerprint: discoveredPeer.fingerprint,
            secret: discoveredPeer.secret,
          });
          lastError = null;
          break;
        } catch (error) {
          lastError = error;
          if (!mountedRef.current) return;
          if (attempt < attempts - 1) {
            await new Promise((resolve) => setTimeout(resolve, 700));
          }
        }
      }
      if (lastError) throw lastError;
      navigate('transfer');
    } catch (error) {
      const message =
        error instanceof NativeOnlyError
          ? 'النقل الحقيقي متاح داخل تطبيق أندرويد فقط.'
          : error instanceof Error
          ? error.message
          : 'فشل بدء الاتصال بالجهاز.';
      if (mountedRef.current) {
        setConnectError(message.replace('MISSING_PAIRING_SECRET: ', ''));
        setConnecting(false);
      }
    }
  };

  const isFound = discoveredPeer !== null;
  const peerInitials = discoveredPeer ? discoveredPeer.peerName.slice(0, 2).toUpperCase() : 'AT';
  const needsQr = discoveredPeer?.requiresQr === true || !discoveredPeer?.secret;
  const canConnect = isFound && !needsQr && !connecting;

  return (
    <div className="page-view scan-page">
      <HeaderTop>
        <button
          type="button"
          className="g c ci"
          aria-label="الرجوع"
          onClick={goBack}
        >
          <Icon name="bk" />
        </button>
        <div className="g c">فحص للاتصال</div>
      </HeaderTop>

      <main style={{ padding: 0 }}>
        <div className="cam">
          <div className="fr">
            <div className="vf">
              <b />
              <b />
              <b />
              <b />
              {/* The native camera preview is positioned exactly over this element. */}
              <i className="cam-window" ref={camWindowRef} />
            </div>
            <button
              type="button"
              className="g c"
              style={{ cursor: 'pointer' }}
            >
              {supported ? 'ضع رمز QR داخل الإطار' : 'الكاميرا تعمل في تطبيق أندرويد'}
            </button>
          </div>

          <div className="sheet">
            <div className={`rg ${isFound ? 'off' : ''}`}>
              <div className="sw" />
              <em />
              <em />
              <em />
              <i
                className="bl"
                style={{
                  left: 'calc(50% - 120px)',
                  bottom: '130px',
                  animationDelay: '1s',
                }}
              />
              <i
                className="bl"
                style={{
                  left: 'calc(50% + 105px)',
                  bottom: '105px',
                  animationDelay: '1.7s',
                }}
              />
              <i
                className="bl"
                style={{
                  left: 'calc(50% + 30px)',
                  bottom: '165px',
                  animationDelay: '2.4s',
                }}
              />
              <div className="cr">
                <Icon name="hs" />
              </div>
            </div>

            <i className="gp" />
            <h2>
              {isFound
                ? needsQr
                  ? 'جهاز قريب بدون اقتران'
                  : 'تم العثور على جهاز'
                : cameraState === 'error'
                ? 'الكاميرا غير متاحة'
                : cameraState === 'running'
                ? 'وجّه الكاميرا نحو رمز QR'
                : 'جارٍ تشغيل الكاميرا…'}
            </h2>

            {isFound && discoveredPeer && (
              <div className="fi" style={{ display: 'block' }}>
                <div className="li" style={{ padding: '22px 0 0' }}>
                  <span className="ic">{peerInitials}</span>
                  <div className="gr">
                    <b>{discoveredPeer.peerName}</b>
                    <small>
                      {needsQr
                        ? 'مكتشف عبر الشبكة · امسح رمز QR للاقتران الآمن'
                        : 'مقترن · جاهز للاستلام مشفّر'}
                    </small>
                  </div>
                </div>

                {connectError && (
                  <p
                    style={{
                      marginTop: '14px',
                      fontSize: '13px',
                      color: 'var(--danger)',
                      textAlign: 'center',
                    }}
                  >
                    {connectError}
                  </p>
                )}

                <button
                  type="button"
                  className="g c p big"
                  style={{
                    display: 'flex',
                    width: '100%',
                    marginTop: '22px',
                    opacity: canConnect ? 1 : 0.4,
                    pointerEvents: canConnect ? 'auto' : 'none',
                  }}
                  onClick={handleConnect}
                  disabled={!canConnect}
                >
                  {needsQr ? 'امسح رمز QR للاقتران' : connecting ? 'جارٍ الاتصال…' : 'اتصال ونقل'}
                </button>
              </div>
            )}

            {!isFound && statusMessage && (
              <p
                style={{
                  marginTop: '12px',
                  fontSize: '13px',
                  color: 'var(--t2)',
                  textAlign: 'center',
                }}
              >
                {statusMessage}
              </p>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};
