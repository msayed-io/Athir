import React from 'react';
import { NavigationProvider, useAppNavigation } from './services/navigationService';
import { SvgSprite } from './assets/icons/SvgSprite';
import { BackdropBlur } from './components/common/BackdropBlur';
import { HomeScreen } from './pages/HomeScreen';
import { SendScreen } from './pages/SendScreen';
import { PrepareScreen } from './pages/PrepareScreen';
import { ReceiveScreen } from './pages/ReceiveScreen';
import { TransferScreen } from './pages/TransferScreen';
import { InviteScreen } from './pages/InviteScreen';
import { HistoryScreen } from './pages/HistoryScreen';
import { ScanScreen } from './pages/ScanScreen';

const ScreenRouter: React.FC = () => {
  const { currentScreen } = useAppNavigation();

  // Root unmount/mount guarantee: By keying on currentScreen,
  // the previous screen is cleanly destroyed and unmounted from the DOM,
  // and the target screen enters as a pristine .page-view scroll container
  // starting at strictly (0, 0) without any window.scrollTo hacks or jitter.
  switch (currentScreen) {
    case 'send':
      return <SendScreen key="send" />;
    case 'prepare':
      return <PrepareScreen key="prepare" />;
    case 'scan':
      return <ScanScreen key="scan" />;
    case 'receive':
      return <ReceiveScreen key="receive" />;
    case 'transfer':
      return <TransferScreen key="transfer" />;
    case 'invite':
      return <InviteScreen key="invite" />;
    case 'history':
      return <HistoryScreen key="history" />;
    case 'home':
    default:
      return <HomeScreen key="home" />;
  }
};

export default function App() {
  React.useEffect(() => {
    // 1. Prevent default context menu (long press / right click)
    const handleContextMenu = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
        return;
      }
      e.preventDefault();
    };

    // 2. Prevent text selection gestures on Android / touch / pointer devices
    const handleSelectStart = (e: Event) => {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
        return;
      }
      e.preventDefault();
    };

    // 3. Prevent dragging of images, links, and text
    const handleDragStart = (e: DragEvent) => {
      e.preventDefault();
    };

    window.addEventListener('contextmenu', handleContextMenu, { passive: false });
    window.addEventListener('selectstart', handleSelectStart, { passive: false });
    window.addEventListener('dragstart', handleDragStart, { passive: false });

    return () => {
      window.removeEventListener('contextmenu', handleContextMenu);
      window.removeEventListener('selectstart', handleSelectStart);
      window.removeEventListener('dragstart', handleDragStart);
    };
  }, []);

  return (
    <div className="app-container">
      <NavigationProvider>
        {/* 16-symbol inline SVG sprite */}
        <SvgSprite />

        {/* Progressive 5-layer top & bottom glass blur backdrops */}
        <BackdropBlur />

        {/* Screen Router */}
        <ScreenRouter />
      </NavigationProvider>
    </div>
  );
}
