import React from 'react';
import { TabBar } from 'glisse';
import { Icon } from '../../assets/icons/SvgSprite';
import { useAppNavigation } from '../../services/navigationService';
import { BottomBar } from './BottomBar';
import './BottomNav.css';

interface BottomNavProps {
  activeTab?: 'home' | 'files' | 'history';
}

export const BottomNav: React.FC<BottomNavProps> = ({ activeTab = 'home' }) => {
  const { navigate } = useAppNavigation();

  return (
    <BottomBar>
      <div className="bottom-nav-glisse-container" dir="ltr">
        <TabBar
          aria-label="شريط التنقل الرئيسي"
          value={activeTab}
          onValueChange={(val) => {
            if (val === 'home') navigate('home');
            else if (val === 'files') navigate('send');
            else if (val === 'history') navigate('history');
          }}
          items={[
            {
              value: 'history',
              label: 'السجل',
              icon: <Icon name="cl" />,
            },
            {
              value: 'files',
              label: 'الملفات',
              icon: <Icon name="fold" />,
            },
            {
              value: 'home',
              label: 'الرئيسية',
              icon: <Icon name="hm" />,
            },
          ]}
        />
      </div>
    </BottomBar>
  );
};

