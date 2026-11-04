import React, { useState, useEffect, useRef, useCallback } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import './ScanScreen.css';

export const ScanScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'فحص للاتصال';
  }, []);

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [isFound, setIsFound] = useState(false);
  const [cameraActive, setCameraActive] = useState(false);
  const startedRef = useRef(false);

  const found = useCallback(() => {
    setIsFound(true);
  }, []);

  const startCamera = useCallback(async () => {
    if (startedRef.current) return;
    if (typeof navigator === 'undefined' || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      return;
    }

    startedRef.current = true;
    try {
      let stream: MediaStream;
      try {
        // Request strictly the back/rear camera (environment facing mode)
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { exact: 'environment' } },
          audio: false,
        });
      } catch {
        try {
          // Fallback to ideal environment camera
          stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'environment' },
            audio: false,
          });
        } catch {
          // Fallback to default camera if environment mode is unavailable
          stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
        }
      }

      streamRef.current = stream;
      setCameraActive(true);
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.onloadedmetadata = () => {
          videoRef.current?.play().catch(() => {});
        };
        videoRef.current.play().catch(() => {});
      }

      // BarcodeDetector API detection if supported in browser
      if ('BarcodeDetector' in window) {
        // @ts-expect-error BarcodeDetector is experimental
        const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
        const intervalId = setInterval(async () => {
          try {
            if (videoRef.current && (await detector.detect(videoRef.current)).length > 0) {
              clearInterval(intervalId);
              found();
            }
          } catch {
            // Ignore frame detection errors
          }
        }, 400);

        return () => clearInterval(intervalId);
      }
    } catch {
      startedRef.current = false;
      setCameraActive(false);
    }
  }, [found]);

  useEffect(() => {
    // Attempt camera permission on mount
    startCamera();

    // Auto-discover fallback after 6 seconds (exact match to scan1.html: setTimeout(found, 6000))
    const timer = setTimeout(() => {
      found();
    }, 6000);

    return () => {
      clearTimeout(timer);
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
      }
    };
  }, [startCamera, found]);

  const handleConnect = () => {
    navigate('transfer');
  };

  const handleTriggerCamera = (e: React.MouseEvent) => {
    e.stopPropagation();
    startedRef.current = false;
    startCamera();
  };

  return (
    <div className="page-view scan-page" onClick={() => startCamera()}>
      {/* Header with back button on the right and title capsule on the left */}
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
            <div className="vf" onClick={handleTriggerCamera}>
              <b />
              <b />
              <b />
              <b />
            </div>
            <button
              type="button"
              className="g c"
              style={{ cursor: 'pointer' }}
              onClick={handleTriggerCamera}
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

            {isFound && (
              <div className="fi" style={{ display: 'block' }}>
                <div className="li" style={{ padding: '22px 0 0' }}>
                  <span className="ic">SM</span>
                  <div className="gr">
                    <b>SmartTV</b>
                    <small>قريب · جاهز للاستلام</small>
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
                  اتصال
                </button>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};
