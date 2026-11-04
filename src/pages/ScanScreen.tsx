import React, { useState, useEffect, useRef, useCallback } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { athirNativeService, DiscoveredPeer } from '../services/athirNativeService';
import './ScanScreen.css';

export const ScanScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'فحص للاتصال';
  }, []);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const [discoveredPeer, setDiscoveredPeer] = useState<DiscoveredPeer | null>(null);
  const [cameraActive, setCameraActive] = useState(false);
  const startedRef = useRef(false);

  const handlePeerFound = useCallback((peer: DiscoveredPeer) => {
    setDiscoveredPeer(peer);
  }, []);

  // Web camera preview fallback for non-native browsers
  const startWebCamera = useCallback(async () => {
    if (startedRef.current || athirNativeService.isNative()) return;
    if (typeof navigator === 'undefined' || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      return;
    }

    startedRef.current = true;
    try {
      let stream: MediaStream;
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { exact: 'environment' } },
          audio: false,
        });
      } catch {
        stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
      }

      streamRef.current = stream;
      setCameraActive(true);
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.play().catch(() => {});
      }

      if ('BarcodeDetector' in window) {
        // @ts-expect-error BarcodeDetector is experimental in browsers
        const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
        const intervalId = setInterval(async () => {
          try {
            if (videoRef.current) {
              const barcodes = await detector.detect(videoRef.current);
              if (barcodes.length > 0) {
                clearInterval(intervalId);
                const raw = barcodes[0].rawValue;
                try {
                  const parsed = JSON.parse(raw);
                  handlePeerFound({
                    peerId: parsed.id || 'peer',
                    peerName: parsed.name || 'جهاز مستلم',
                    host: parsed.host || '127.0.0.1',
                    port: parsed.port || 8888,
                    fingerprint: parsed.fp || '',
                    secret: parsed.secret || '',
                  });
                } catch {
                  // Ignore
                }
              }
            }
          } catch {
            // Ignore
          }
        }, 300);

        return () => clearInterval(intervalId);
      }
    } catch {
      startedRef.current = false;
      setCameraActive(false);
    }
  }, [handlePeerFound]);

  useEffect(() => {
    // 1. Native CameraX + ML Kit Barcode scanning
    if (athirNativeService.isNative()) {
      athirNativeService.startScanner();
    } else {
      startWebCamera();
    }

    // 2. Local mDNS discovery
    athirNativeService.startDiscovery();

    // 3. Listen to ML Kit QR scans
    const cleanupQr = athirNativeService.onQrScanned((data) => {
      if (data.host && data.port && data.fingerprint && data.secret) {
        handlePeerFound({
          peerId: data.peerId || 'peer',
          peerName: data.peerName || 'جهاز مستلم',
          host: data.host,
          port: data.port,
          fingerprint: data.fingerprint,
          secret: data.secret,
        });
      }
    });

    // 4. Listen to discovered peers via mDNS
    const cleanupPeer = athirNativeService.onDeviceDiscovered((peer) => {
      handlePeerFound(peer);
    });

    return () => {
      cleanupQr();
      cleanupPeer();
      athirNativeService.stopScanner();
      athirNativeService.stopDiscovery();
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
      }
    };
  }, [startWebCamera, handlePeerFound]);

  const handleConnect = async () => {
    if (!discoveredPeer) return;
    try {
      await athirNativeService.connectAndSend({
        host: discoveredPeer.host,
        port: discoveredPeer.port,
        fingerprint: discoveredPeer.fingerprint,
        secret: discoveredPeer.secret,
      });
      navigate('transfer');
    } catch (e) {
      console.error('Connection error:', e);
      navigate('transfer');
    }
  };

  const isFound = discoveredPeer !== null;
  const peerInitials = discoveredPeer ? discoveredPeer.peerName.slice(0, 2).toUpperCase() : 'AT';

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
          <video
            ref={videoRef}
            muted
            playsInline
            autoPlay
            style={{ opacity: cameraActive ? 1 : 0, transition: 'opacity 0.4s ease' }}
          />

          <div className="fr">
            <div className="vf">
              <b />
              <b />
              <b />
              <b />
            </div>
            <button
              type="button"
              className="g c"
              style={{ cursor: 'pointer' }}
            >
              ضع رمز QR داخل الإطار
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
            <h2>{isFound ? 'تم العثور على جهاز' : 'البحث عن مستلمين…'}</h2>

            {isFound && discoveredPeer && (
              <div className="fi" style={{ display: 'block' }}>
                <div className="li" style={{ padding: '22px 0 0' }}>
                  <span className="ic">{peerInitials}</span>
                  <div className="gr">
                    <b>{discoveredPeer.peerName}</b>
                    <small>قريب · جاهز للاستلام مشفّر</small>
                  </div>
                </div>
                <button
                  type="button"
                  className="g c p big"
                  style={{
                    display: 'flex',
                    width: '100%',
                    marginTop: '22px',
                  }}
                  onClick={handleConnect}
                >
                  اتصال ونقل
                </button>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};
