import React, { useState, useEffect } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomBar } from '../components/common/BottomBar';
import { RadarVisual } from '../components/common/RadarVisual';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import {
  athirNativeService,
  ReceiveSession,
  DiscoveredPeer,
} from '../services/athirNativeService';
import './ReceiveScreen.css';

export const ReceiveScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'استلام';
  }, []);

  const [supported] = useState(athirNativeService.isNative());
  const [session, setSession] = useState<ReceiveSession | null>(null);
  const [qrCodeUrl, setQrCodeUrl] = useState<string>('');
  const [discoveredPeer, setDiscoveredPeer] = useState<DiscoveredPeer | null>(null);
  const [showQrInline, setShowQrInline] = useState(true);
  const [statusText, setStatusText] = useState('جارٍ بدء وضع الاستلام…');
  const [errorText, setErrorText] = useState('');

  useEffect(() => {
    let unmounted = false;

    if (!athirNativeService.isNative()) {
      setStatusText('الاستلام الحقيقي متاح داخل تطبيق أندرويد فقط.');
      return () => {
        unmounted = true;
      };
    }

    // Starts the real TLS receiver server and advertises it over mDNS
    // (the session secret is delivered only inside the QR code).
    athirNativeService
      .startReceiving()
      .then(async (s) => {
        if (unmounted) return;
        setSession(s);
        setStatusText('في انتظار المرسل');

        if (s.qrDataUrl) {
          setQrCodeUrl(s.qrDataUrl);
        } else if (s.qrPayload) {
          const generated = await athirNativeService.generateQrCode(s.qrPayload);
          if (!unmounted) setQrCodeUrl(generated);
        }
      })
      .catch((err) => {
        if (unmounted) return;
        setErrorText(
          err instanceof Error
            ? err.message.replace('NATIVE_ONLY', 'غير مدعوم خارج التطبيق')
            : String(err)
        );
        setStatusText('تعذّر بدء الاستلام');
      });

    const cleanupPeer = athirNativeService.onDeviceDiscovered((peer) => {
      if (!unmounted) setDiscoveredPeer(peer);
    });

    const cleanupProgress = athirNativeService.onTransferProgress(() => {
      if (!unmounted) navigate('transfer');
    });

    return () => {
      unmounted = true;
      cleanupPeer();
      cleanupProgress();
      athirNativeService.stopReceiving().catch(() => {});
    };
  }, [navigate]);

  const handleAction = () => {
    if (discoveredPeer && discoveredPeer.secret) {
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
        {!supported ? (
          <div className="card" style={{ marginTop: '18px', padding: '28px 20px', textAlign: 'center' }}>
            <span className="ic" style={{ margin: '0 auto 14px' }}>
              <Icon name="dl" />
            </span>
            <b style={{ display: 'block', fontSize: '16px', marginBottom: '8px' }}>
              الاستلام يعمل داخل تطبيق أندرويد
            </b>
            <small style={{ color: 'var(--t2)', fontSize: '13px', lineHeight: 1.6 }}>
              خادم الاستلام المشفّر، رمز الاقتران، والاكتشاف المحلي كلها وظائف أصلية
              لا يمكن تشغيلها في المتصفح.
            </small>
          </div>
        ) : showQrInline ? (
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
                    {errorText ? 'تعذّر توليد الرمز' : 'جارٍ توليد الرمز المشفّر…'}
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
          {!supported
            ? 'غير متاح في المتصفح'
            : showQrInline
            ? 'رمز QR جاهز للمسح'
            : discoveredPeer
            ? `تم العثور على ${discoveredPeer.peerName}`
            : 'في انتظار المرسل'}
        </h1>
        <p className="sub" id="sb">
          {!supported
            ? 'افتح تطبيق أثير على أندرويد لبدء جلسة استلام مشفّرة'
            : showQrInline
            ? 'امسح الرمز من جهاز المرسل للربط المباشر'
            : discoveredPeer
            ? discoveredPeer.requiresQr
              ? 'هذا الجهاز مكتشف عبر الشبكة — يحتاج مسح رمز QR للاقتران'
              : 'اضغط اتصال للبدء'
            : 'افتح الإرسال على الجهاز الآخر'}
        </p>

        {supported && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: '20px' }}>
            <div className="g c" style={{ padding: '6px 14px', borderRadius: '20px', background: 'rgba(255,255,255,0.08)', fontSize: '13px' }}>
              <i className="dot" />
              {displayName}
            </div>
          </div>
        )}

        {errorText && (
          <p style={{ marginTop: '14px', fontSize: '13px', color: 'var(--danger)', textAlign: 'center' }}>
            {errorText}
          </p>
        )}
      </main>

      <BottomBar>
        <div className="g bx">
          <span id="st">
            {!supported
              ? 'يتطلب تطبيق أندرويد'
              : showQrInline
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
            style={{
              opacity: supported ? 1 : 0.4,
              pointerEvents: supported ? 'auto' : 'none',
            }}
          >
            {discoveredPeer ? 'اتصال' : showQrInline ? 'رادار' : 'QR'}
          </button>
        </div>
      </BottomBar>
    </div>
  );
};
