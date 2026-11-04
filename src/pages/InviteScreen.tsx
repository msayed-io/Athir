import React, { useState } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { RadarVisual } from '../components/common/RadarVisual';
import { ShareChannels } from '../components/invite/ShareChannels';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { athirNativeService } from '../services/athirNativeService';
import './InviteScreen.css';

export const InviteScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  React.useEffect(() => {
    document.title = 'دعوة';
  }, []);

  const showToast = (message: string) => {
    setToastMessage(message);
    setTimeout(() => setToastMessage(null), 2500);
  };

  const handleSelectMethod = async (method: string) => {
    if (method === 'QR') {
      navigate('receive');
      return;
    }

    // Android WebView does not implement the Web Share API, so sharing is done by the
    // native layer: a real ACTION_SEND chooser with the installed APK attached. The user
    // picks WhatsApp / Bluetooth / Nearby Share themselves.
    if (athirNativeService.isNative()) {
      try {
        const result = await athirNativeService.shareApp();
        if (!result.apkAttached) {
          showToast('تمت المشاركة كنص فقط — تعذّر إرفاق ملف التطبيق.');
        }
      } catch (e) {
        showToast(
          e instanceof Error && e.message
            ? 'تعذّر فتح قائمة المشاركة. تثبيت التطبيق قد يكون مقيدًا على هذا الجهاز.'
            : 'تعذّر فتح قائمة المشاركة.'
        );
      }
      return;
    }

    if (typeof navigator !== 'undefined' && navigator.share) {
      try {
        await navigator.share({
          title: 'أثير | Athir',
          text: 'قم بتثبيت تطبيق أثير لنقل البيانات والملفات محلياً بأمان وبدون إنترنت وبسرعة فائقة!',
        });
      } catch {
        // The user dismissed the sheet: nothing to report.
      }
      return;
    }

    showToast('المشاركة تعمل داخل تطبيق أندرويد.');
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
