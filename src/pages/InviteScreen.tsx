import React, { useState } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { RadarVisual } from '../components/common/RadarVisual';
import { ShareChannels } from '../components/invite/ShareChannels';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import './InviteScreen.css';

export const InviteScreen: React.FC = () => {
  const { goBack } = useAppNavigation();
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  React.useEffect(() => {
    document.title = 'دعوة';
  }, []);

  const handleSelectMethod = (method: string) => {
    // If Web Share API is available (e.g. on mobile/Capacitor)
    if (navigator.share && (method === 'More' || method === 'WhatsApp' || method === 'Facebook')) {
      navigator
        .share({
          title: 'أثير | Athir',
          text: 'قم بتنزيل تطبيق أثير لنقل البيانات والملفات محلياً بأمان وبدون إنترنت!',
          url: window.location.href,
        })
        .catch(() => {});
    } else {
      setToastMessage(`تم تفعيل خيار مشاركة: ${method}`);
      setTimeout(() => setToastMessage(null), 2500);
    }
  };

  return (
    <div className="page-view invite-page">
      <HeaderTop>
        <button
          type="button"
          className="g c ci"
          aria-label="الرجوع"
          onClick={goBack}
        >
          <Icon name="bk" />
        </button>
        <div className="g c">دعوة</div>
      </HeaderTop>

      <main>
        <RadarVisual iconName="send" />

        <h1>شارك مع الأصدقاء القريبين</h1>
        <p className="sub">بدون إنترنت</p>

        <ShareChannels onSelectMethod={handleSelectMethod} />
      </main>

      {toastMessage && (
        <div className="toast-notification">
          {toastMessage}
        </div>
      )}
    </div>
  );
};
