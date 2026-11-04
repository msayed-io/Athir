import React, { useState, useEffect, useCallback } from 'react';
import { Switch } from 'glisse';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomBar } from '../components/common/BottomBar';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { athirNativeService, PermissionStatus } from '../services/athirNativeService';
import './PrepareScreen.css';

const EMPTY_STATUS: PermissionStatus = {
  mediaGranted: false,
  nearbyGranted: false,
  cameraGranted: false,
  notificationGranted: false,
  contactsGranted: false,
  allGranted: false,
  sdkInt: 0,
  supported: false,
};

export const PrepareScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'التحضيرات';
  }, []);

  const [status, setStatus] = useState<PermissionStatus>(EMPTY_STATUS);
  const [requesting, setRequesting] = useState(false);
  const [errorText, setErrorText] = useState('');

  const applyStatus = useCallback((next: PermissionStatus) => {
    setStatus(next);
  }, []);

  const checkPerms = useCallback(async () => {
    if (!athirNativeService.isNative()) {
      setStatus(EMPTY_STATUS);
      return;
    }
    try {
      applyStatus(await athirNativeService.checkPermissions());
    } catch {
      setStatus(EMPTY_STATUS);
    }
  }, [applyStatus]);

  useEffect(() => {
    checkPerms();
    // The authoritative post-dialog status always arrives through this event.
    const cleanup = athirNativeService.onPermissionsChanged((next) => {
      applyStatus(next);
      setRequesting(false);
    });
    return cleanup;
  }, [checkPerms, applyStatus]);

  const requestAll = useCallback(async () => {
    setErrorText('');
    setRequesting(true);
    try {
      await athirNativeService.requestPermissions();
    } catch (e) {
      setRequesting(false);
      setErrorText('تعذّر طلب الأذونات. افتح إعدادات التطبيق لمنحها يدويًا.');
    }
  }, []);

  const sdkVersion = status.sdkInt || 33;
  const perm1Granted = status.nearbyGranted && status.cameraGranted;
  const perm2Granted = status.mediaGranted;
  const count = (perm1Granted ? 1 : 0) + (perm2Granted ? 1 : 0);
  const isReady = status.supported && status.allGranted;
  const unsupported = !status.supported;

  const handleTogglePerm1 = async (val: boolean) => {
    if (val) {
      await requestAll();
    }
  };

  const handleTogglePerm2 = async (val: boolean) => {
    if (val) {
      await requestAll();
    }
  };

  const handleNext = () => {
    if (!isReady) return;
    navigate('receive');
  };

  return (
    <div className="page-view prepare-page">
      <HeaderTop>
        <button
          type="button"
          className="g c ci"
          aria-label="الرجوع"
          onClick={goBack}
        >
          <Icon name="bk" />
        </button>
        <div className="g c">التحضيرات</div>
      </HeaderTop>

      <main>
        <div className="rd s">
          <em />
          <em />
          <em />
          <div className="o">
            <i />
            <i />
            <i />
          </div>
          <div className="core">
            <Icon name="pin" />
          </div>
        </div>

        <h1>جهّز جهازك</h1>
        <p className="sub">
          {unsupported ? 'الأذونات تُدار داخل تطبيق أندرويد' : 'خطوتان لاكتشاف الأجهزة القريبة'}
        </p>

        <div className="card" style={{ marginTop: '24px' }}>
          <div className="pm">
            <div className="t">
              <span className="ic">
                <Icon name="pin" />
              </span>
              <b>{sdkVersion >= 33 ? 'أجهزة Wi-Fi القريبة' : 'منح إذن الموقع'}</b>
              <div dir="ltr" style={{ display: 'inline-flex' }}>
                <Switch
                  aria-label="منح إذن الاتصال القريب"
                  checked={perm1Granted}
                  disabled={unsupported || requesting}
                  onCheckedChange={handleTogglePerm1}
                />
              </div>
            </div>
            <p>
              {sdkVersion >= 33
                ? 'إذن الاتصال بالأجهزة القريبة بدون الحاجة للوصول إلى موقعك الجغرافي، مع إذن الكاميرا لمسح رمز الاقتران.'
                : 'ابحث عن جهاز قريب قام بتثبيت تطبيق أثير ويمكنك النقل مباشرة إليه.'}
            </p>
          </div>

          <div className="pm">
            <div className="t">
              <span className="ic">
                <Icon name="gps" />
              </span>
              <b>{sdkVersion >= 33 ? 'الوصول للوسائط والملفات' : 'الوصول لوحدة التخزين'}</b>
              <div dir="ltr" style={{ display: 'inline-flex' }}>
                <Switch
                  aria-label="تشغيل أذونات التخزين والوسائط"
                  checked={perm2Granted}
                  disabled={unsupported || requesting}
                  onCheckedChange={handleTogglePerm2}
                />
              </div>
            </div>
            <p>
              {sdkVersion >= 33
                ? 'منح الوصول للصور والفيديو والموسيقى والتطبيقات لتتمكن من إرسالها واستلامها.'
                : 'تمكين الوصول لوحدة التخزين لنقل واستلام الملفات بأمان وسرعة.'}
            </p>
          </div>
        </div>

        {errorText && (
          <p style={{ marginTop: '14px', fontSize: '13px', color: 'var(--danger)', textAlign: 'center' }}>
            {errorText}
          </p>
        )}

        {!unsupported && !isReady && count === 2 && (
          <p style={{ marginTop: '14px', fontSize: '13px', color: 'var(--t2)', textAlign: 'center' }}>
            بعض الأذونات ما زالت مرفوضة. اضغط المفتاح مرة أخرى أو امنحها من إعدادات النظام.
          </p>
        )}
      </main>

      <BottomBar>
        <div className="g bx">
          <span id="sm">{unsupported ? '—' : `${count} / 2`}</span>
          <button
            type="button"
            className="g c p"
            id="nx"
            style={{
              opacity: isReady ? 1 : 0.4,
              pointerEvents: isReady ? 'auto' : 'none',
              transition: 'opacity 0.25s ease',
            }}
            disabled={!isReady}
            onClick={handleNext}
          >
            التالي
          </button>
        </div>
      </BottomBar>
    </div>
  );
};
