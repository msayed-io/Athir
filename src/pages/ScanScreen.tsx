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
  const mountedRef = useRef(true);

  const handlePeerFound = useCallback((peer: DiscoveredPeer) => {
    if (!mountedRef.current) return;
    setDiscoveredPeer((current) => {
      // A QR pairing always wins over an anonymous mDNS sighting of the same device.
      if (current && current.secret && !peer.secret) return current;
      return peer;
    });
  }, []);

  useEffect(() => {
    mountedRef.current = true;

    if (!athirNativeService.isNative()) {
      setSupported(false);
      setStatusMessage('الماسح والاكتشاف يعملان داخل تطبيق أندرويد فقط.');
      return () => {
        mountedRef.current = false;
      };
    }

    // Native CameraX + ML Kit QR scanning and mDNS discovery.
    athirNativeService.startScanner().catch(() => {
      setStatusMessage('تعذّر تشغيل الكاميرا. تحقّق من إذن الكاميرا.');
    });
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
      cleanupQr();
      cleanupPeer();
      athirNativeService.stopScanner().catch(() => {});
      athirNativeService.stopDiscovery().catch(() => {});
    };
  }, [handlePeerFound]);

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
        // Give the platform a moment to complete DHCP/group negotiation.
        await new Promise((resolve) => setTimeout(resolve, 1500));
      }
      await athirNativeService.connectAndSend({
        host: discoveredPeer.host,
        port: discoveredPeer.port,
        fingerprint: discoveredPeer.fingerprint,
        secret: discoveredPeer.secret,
      });
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
                : 'البحث عن مستلمين…'}
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
