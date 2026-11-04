import React from 'react';
import { Icon } from '../../assets/icons/SvgSprite';
import './ShareChannels.css';

interface ShareChannelsProps {
  onSelectMethod?: (method: string) => void;
}

export const ShareChannels: React.FC<ShareChannelsProps> = ({ onSelectMethod }) => {
  const handleAction = (name: string) => {
    if (onSelectMethod) {
      onSelectMethod(name);
    }
  };

  return (
    <>
      <div className="col">
        <button
          type="button"
          className="g c p big"
          onClick={() => handleAction('Bluetooth')}
        >
          <Icon name="bt" />
          Bluetooth
        </button>
        <button
          type="button"
          className="g c big"
          onClick={() => handleAction('Hotspot')}
        >
          <Icon name="hs" />
          نقطة الاتصال
        </button>
      </div>

      <div className="h2" style={{ textAlign: 'center' }}>
        المزيد من طرق المشاركة
      </div>

      <div className="sh">
        <button type="button" onClick={() => handleAction('QR')}>
          <span className="g c ci">
            <Icon name="qr" />
          </span>
          رمز QR
        </button>

        <button type="button" onClick={() => handleAction('WhatsApp')}>
          <span className="g c ci">W</span>
          WhatsApp
        </button>

        <button type="button" onClick={() => handleAction('Facebook')}>
          <span className="g c ci">f</span>
          Facebook
        </button>

        <button type="button" onClick={() => handleAction('More')}>
          <span className="g c ci">···</span>
          المزيد
        </button>
      </div>
    </>
  );
};
