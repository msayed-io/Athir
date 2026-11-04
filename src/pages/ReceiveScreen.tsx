import React, { useState, useEffect } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomBar } from '../components/common/BottomBar';
import { RadarVisual } from '../components/common/RadarVisual';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { athirNativeService, ReceiveSession, DiscoveredPeer } from '../services/athirNativeService';
import './ReceiveScreen.css';

export const ReceiveScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'استلام';
  }, []);

  const [session, setSession] = useState<ReceiveSession | null>(null);
  const [qrCodeUrl, setQrCodeUrl] = useState<string>('');
  const [discoveredPeer, setDiscoveredPeer] = useState<DiscoveredPeer | null>(null);
  const [showQrInline, setShowQrInline] = useState(true);
  const [statusText, setStatusText] = useState('جارٍ بدء وضع الاستلام…');

  useEffect(() => {
    let unmounted = false;

    // Start receiver server and local advertising
    athirNativeService.startReceiving().then(async (s) => {
      if (!unmounted) {
        setSession(s);
        setStatusText('في انتظار المرسل');

        if (s.qrDataUrl) {
          setQrCodeUrl(s.qrDataUrl);
        } else if (s.qrPayload) {
          const generated = await athirNativeService.generateQrCode(s.qrPayload);
          if (!unmounted) {
            setQrCodeUrl(generated);
          }
        }
      }
    }).catch((err) => {
      if (!unmounted) {
        setStatusText('فشل بدء الاستلام: ' + err);
      }
    });

    // Listen for incoming connection or discovered peer
    const cleanupPeer = athirNativeService.onDeviceDiscovered((peer) => {
      if (!unmounted) {
        setDiscoveredPeer(peer);
      }
    });

    const cleanupProgress = athirNativeService.onTransferProgress(() => {
      if (!unmounted) {
        navigate('transfer');
      }
    });

    return () => {
      unmounted = true;
      cleanupPeer();
      cleanupProgress();
      athirNativeService.stopReceiving();
    };
  }, [navigate]);

  const handleAction = () => {
    if (discoveredPeer) {
      navigate('transfer');
    } else {
      setShowQrInline((prev) => !prev);
    }
  };

  const displayName = session?.displayName || 'أثير';

  return (
    <div className="page-view receive-page">
      <HeaderTop>
        <button
          type="button"
          className="g c ci"
          aria-label="الرجوع"
          onClick={goBack}
        >
          <Icon name="bk" />
        </button>
        <div className="g c">استلام</div>
      </HeaderTop>

      <main>
        {showQrInline ? (
          <div className="qr-box-container" style={{ width: '100%', display: 'flex', justifyContent: 'center' }}>
            <div
              className="g qr-card"
              style={{
                background: 'var(--card-bg, rgba(255, 255, 255, 0.06))',
                border: '1px solid rgba(255, 255, 255, 0.12)',
                borderRadius: '20px',
                padding: '20px',
                maxWidth: '320px',
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                boxShadow: '0 12px 32px rgba(0,0,0,0.3)',
              }}
            >
              <div className="qr-header" style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', fontWeight: 600 }}>
                <Icon name="qr" />
                <span>رمز الاستلام السريع</span>
              </div>
              <div
                className="qr-graphic-wrap"
                style={{
                  width: '220px',
                  height: '220px',
                  borderRadius: '16px',
                  background: '#ffffff',
                  padding: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 4px 16px rgba(0,0,0,0.2)',
                }}
              >
                {qrCodeUrl ? (
                  <img
                    src={qrCodeUrl}
                    alt="رمز الاستلام السريع"
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'contain',
                      borderRadius: '8px',
                    }}
                  />
                ) : (
                  <div style={{ color: '#666666', fontSize: '13px', textAlign: 'center' }}>
                    جارٍ توليد الرمز المشفّر…
                  </div>
                )}
              </div>
              <p className="qr-subtext" style={{ marginTop: '14px', fontSize: '12px', color: 'var(--t2)', textAlign: 'center' }}>
                امسح الرمز من جهاز المرسل للربط المباشر
              </p>
            </div>
          </div>
        ) : (
          <RadarVisual iconName="dl" />
        )}

        <h1 id="h">
          {showQrInline
            ? 'رمز QR جاهز للمسح'
            : discoveredPeer
            ? `تم العثور على ${discoveredPeer.peerName}`
            : 'في انتظار المرسل'}
        </h1>
        <p className="sub" id="sb">
          {showQrInline
            ? 'امسح الرمز من جهاز المرسل للربط المباشر'
            : discoveredPeer
            ? 'اضغط اتصال للبدء'
            : 'افتح الإرسال على الجهاز الآخر'}
        </p>

        <div style={{ display: 'flex', justifyContent: 'center', marginTop: '20px' }}>
          <div className="g c" style={{ padding: '6px 14px', borderRadius: '20px', background: 'rgba(255,255,255,0.08)', fontSize: '13px' }}>
            <i className="dot" />
            {displayName}
          </div>
        </div>
      </main>

      <BottomBar>
        <div className="g bx">
          <span id="st">
            {showQrInline
              ? 'رمز QR نشط'
              : discoveredPeer
              ? discoveredPeer.peerName
              : statusText}
          </span>
          <button
            type="button"
            className="g c p"
            id="btn"
            onClick={handleAction}
          >
            {discoveredPeer ? 'اتصال' : showQrInline ? 'رادار' : 'QR'}
          </button>
        </div>
      </BottomBar>
    </div>
  );
};
