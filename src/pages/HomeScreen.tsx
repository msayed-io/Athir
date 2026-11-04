import React from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { RadarVisual } from '../components/common/RadarVisual';
import { BottomNav } from '../components/common/BottomNav';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { storageService } from '../services/storageService';
import './HomeScreen.css';

export const HomeScreen: React.FC = () => {
  const { navigate } = useAppNavigation();
  const recentItems = storageService.getRecentTransfers();

  React.useEffect(() => {
    document.title = 'الرئيسية';
  }, []);

  return (
    <div className="page-view home-page">
      <HeaderTop>
        <button type="button" className="g c" onClick={() => navigate('home')} style={{ fontWeight: 700, letterSpacing: '0.5px' }}>
          Athir
        </button>
        <button
          type="button"
          className="g c ci"
          aria-label="دعوة الأصدقاء"
          onClick={() => navigate('invite')}
        >
          <Icon name="plus" />
        </button>
      </HeaderTop>

      <main>
        <RadarVisual iconName="send" />

        <h1>نقل البيانات بالأثير</h1>
        <p className="sub">بدون إنترنت، وبسرعة عالية</p>

        <div className="row" style={{ marginTop: '28px' }}>
          <button
            type="button"
            className="g c p big"
            onClick={() => navigate('send')}
          >
            <Icon name="send" />
            إرسال
          </button>
          <button
            type="button"
            className="g c big"
            onClick={() => navigate('prepare')}
          >
            <Icon name="dl" />
            استلام
          </button>
        </div>

        <button
          type="button"
          className="card li"
          style={{ marginTop: '12px', width: '100%', textAlign: 'start' }}
          onClick={() => navigate('send')}
        >
          <span className="ic">
            <Icon name="fold" />
          </span>
          <div className="gr">
            <b>الملفات</b>
            <small>تصفّح ملفات جهازك</small>
          </div>
        </button>

        <div className="h2">الأخيرة</div>

        <div className="card" id="rec">
          {recentItems.map((item, idx) => {
            const name = item[0];
            const size = item[1];
            const status = item[2];
            const iconText = name.slice(0, 2);

            return (
              <div
                key={`${name}-${idx}`}
                className="li"
                style={{ cursor: 'pointer' }}
                onClick={() => navigate('history')}
              >
                <span className="ic">{iconText}</span>
                <div className="gr">
                  <b>{name}</b>
                  <small>
                    {size} · {status}
                  </small>
                </div>
              </div>
            );
          })}
        </div>
      </main>

      <BottomNav activeTab="home" />
    </div>
  );
};
