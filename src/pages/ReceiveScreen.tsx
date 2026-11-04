import React, { useState, useEffect } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomBar } from '../components/common/BottomBar';
import { RadarVisual } from '../components/common/RadarVisual';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import './ReceiveScreen.css';

export const ReceiveScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'استلام';
  }, []);

  // Discovery simulation timer
  const [isDiscovered, setIsDiscovered] = useState(false);
  const [showQrInline, setShowQrInline] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsDiscovered(true);
    }, 4500);

    return () => clearTimeout(timer);
  }, []);

  const handleAction = () => {
    if (isDiscovered) {
      navigate('transfer');
    } else {
      setShowQrInline((prev) => !prev);
    }
  };

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
          <div className="qr-box-container">
            <div
              className="g qr-card"
              onClick={() => {
                setIsDiscovered(true);
                navigate('transfer');
              }}
              title="انقر للاتصال المباشر"
            >
              <div className="qr-header">
                <Icon name="qr" />
                <span>رمز الاستلام السريع</span>
              </div>
              <div className="qr-graphic-wrap">
                <svg viewBox="0 0 100 100" className="qr-svg">
                  <rect x="10" y="10" width="25" height="25" fill="var(--bg)" />
                  <rect x="15" y="15" width="15" height="15" fill="var(--on)" />
                  <rect x="19" y="19" width="7" height="7" fill="var(--bg)" />
                  <rect x="65" y="10" width="25" height="25" fill="var(--bg)" />
                  <rect x="70" y="15" width="15" height="15" fill="var(--on)" />
                  <rect x="74" y="19" width="7" height="7" fill="var(--bg)" />
                  <rect x="10" y="65" width="25" height="25" fill="var(--bg)" />
                  <rect x="15" y="70" width="15" height="15" fill="var(--on)" />
                  <rect x="19" y="74" width="7" height="7" fill="var(--bg)" />
                  <rect x="45" y="15" width="10" height="20" fill="var(--bg)" />
                  <rect x="40" y="45" width="20" height="10" fill="var(--bg)" />
                  <rect x="45" y="65" width="15" height="15" fill="var(--bg)" />
                  <rect x="65" y="45" width="20" height="15" fill="var(--bg)" />
                  <rect x="75" y="75" width="15" height="15" fill="var(--bg)" />
                </svg>
              </div>
              <p className="qr-subtext">امسح الرمز أو انقر للاتصال المباشر</p>
            </div>
          </div>
        ) : (
          <RadarVisual iconName="dl" />
        )}

        <h1 id="h">
          {showQrInline
            ? 'رمز QR جاهز للمسح'
            : isDiscovered
            ? 'تم العثور على SmartTV'
            : 'في انتظار المرسل'}
        </h1>
        <p className="sub" id="sb">
          {showQrInline
            ? 'امسح الرمز من جهاز المرسل للربط المباشر'
            : isDiscovered
            ? 'اضغط اتصال للبدء'
            : 'افتح الإرسال على الجهاز الآخر'}
        </p>

        <div style={{ display: 'flex', justifyContent: 'center', marginTop: '24px' }}>
          <div className="g c">
            <i className="dot" />
            user79109045681
          </div>
        </div>
      </main>

      <BottomBar>
        <div className="g bx">
          <span id="st">
            {showQrInline
              ? 'جاهز للمسح'
              : isDiscovered
              ? 'SmartTV'
              : 'جارٍ البحث…'}
          </span>
          <button
            type="button"
            className="g c p"
            id="btn"
            onClick={handleAction}
          >
            {isDiscovered ? 'اتصال' : 'QR'}
          </button>
        </div>
      </BottomBar>
    </div>
  );
};


