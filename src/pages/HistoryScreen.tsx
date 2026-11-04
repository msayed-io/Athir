import React, { useState } from 'react';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomNav } from '../components/common/BottomNav';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { storageService } from '../services/storageService';
import './HistoryScreen.css';

export const HistoryScreen: React.FC = () => {
  const { goBack } = useAppNavigation();
  const [items, setItems] = useState(() => storageService.getRecentTransfers());

  React.useEffect(() => {
    document.title = 'السجل';
  }, []);

  const handleClear = () => {
    try {
      localStorage.removeItem('recent_transfers');
    } catch {
      // Ignore
    }
    setItems([]);
  };

  return (
    <div className="page-view history-page">
      <HeaderTop>
        <button
          type="button"
          className="g c ci"
          aria-label="الرجوع"
          onClick={goBack}
        >
          <Icon name="bk" />
        </button>
        <div className="g c">السجل</div>
      </HeaderTop>

      <main>
        <div className="h2" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>سجل النقل والتبادل</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '11px', color: 'var(--t3)' }}>{items.length} ملفات</span>
            {items.length > 0 && (
              <button
                type="button"
                style={{
                  fontSize: '11px',
                  color: 'var(--t2)',
                  padding: '2px 8px',
                  borderRadius: '10px',
                  background: 'var(--h)',
                }}
                onClick={handleClear}
              >
                مسح السجل
              </button>
            )}
          </div>
        </div>

        {items.length === 0 ? (
          <div className="card" style={{ padding: '36px 16px', textAlign: 'center' }}>
            <div style={{ color: 'var(--t2)', fontSize: '15px' }}>لا توجد عمليات نقل سابقة</div>
          </div>
        ) : (
          <div className="card">
            {items.map((item, idx) => {
              const name = item[0];
              const size = item[1];
              const status = item[2];
              const iconText = name.slice(0, 2);

              return (
                <div key={`${name}-${idx}`} className="li">
                  <span className="ic">{iconText}</span>
                  <div className="gr">
                    <b>{name}</b>
                    <small>
                      {size} · {status}
                    </small>
                  </div>
                  <small className="st ok">✓</small>
                </div>
              );
            })}
          </div>
        )}
      </main>

      <BottomNav activeTab="history" />
    </div>
  );
};
