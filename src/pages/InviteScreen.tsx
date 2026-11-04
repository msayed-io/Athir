import React, { useState } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { RadarVisual } from '../components/common/RadarVisual';
import { ShareChannels } from '../components/invite/ShareChannels';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import './InviteScreen.css';

export const InviteScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  React.useEffect(() => {
    document.title = 'دعوة';
  }, []);

  const handleSelectMethod = (method: string) => {
    if (method === 'QR') {
      navigate('receive');
      return;
    }

    if (navigator.share && (method === 'More' || method === 'WhatsApp' || method === 'Facebook' || method === 'Bluetooth')) {
      navigator
        .share({
          title: 'أثير | Athir',
          text: 'قم بتثبيت تطبيق أثير لنقل البيانات والملفات محلياً بأمان وبدون إنترنت وبسرعة فائقة!',
          url: window.location.href,
        })
        .catch(() => {});
    } else {
      setToastMessage(`مشاركة التطبيق عبر ${method}`);
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
