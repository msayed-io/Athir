import React, { useState, useEffect, useCallback } from 'react';
import { Switch } from 'glisse';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomBar } from '../components/common/BottomBar';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { athirNativeService } from '../services/athirNativeService';
import './PrepareScreen.css';

export const PrepareScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'التحضيرات';
  }, []);

  const [perm1Granted, setPerm1Granted] = useState(false);
  const [perm2Granted, setPerm2Granted] = useState(false);
  const [sdkVersion, setSdkVersion] = useState(36);

  const checkPerms = useCallback(async () => {
    const status = await athirNativeService.checkPermissions();
    setPerm1Granted(status.nearbyGranted);
    setPerm2Granted(status.mediaGranted);
    setSdkVersion(status.sdkInt);
  }, []);

  useEffect(() => {
    checkPerms();
  }, [checkPerms]);

  const handleTogglePerm1 = async (val: boolean) => {
    if (val) {
      const res = await athirNativeService.requestPermissions();
      setPerm1Granted(res.nearbyGranted);
      setPerm2Granted(res.mediaGranted);
    } else {
      setPerm1Granted(false);
    }
  };

  const handleTogglePerm2 = async (val: boolean) => {
    if (val) {
      const res = await athirNativeService.requestPermissions();
      setPerm1Granted(res.nearbyGranted);
      setPerm2Granted(res.mediaGranted);
    } else {
      setPerm2Granted(false);
    }
  };

  const count = (perm1Granted ? 1 : 0) + (perm2Granted ? 1 : 0);
  const isReady = count === 2;

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
        <p className="sub">خطوتان لاكتشاف الأجهزة القريبة</p>

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
                  onCheckedChange={handleTogglePerm1}
                />
              </div>
            </div>
            <p>
              {sdkVersion >= 33
                ? 'إذن الاتصال بالأجهزة القريبة بدون الحاجة للوصول إلى موقعك الجغرافي.'
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
      </main>

      <BottomBar>
        <div className="g bx">
          <span id="sm">{count} / 2</span>
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
