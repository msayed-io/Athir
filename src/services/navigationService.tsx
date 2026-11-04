import React, { createContext, useContext, useState, useEffect, ReactNode, useCallback, useRef } from 'react';
import { ScreenRoute } from '../types';

export type NavigationDirection = 'push' | 'pop' | 'tab' | 'none';

interface NavigationContextType {
  currentScreen: ScreenRoute;
  direction: NavigationDirection;
  navigate: (screen: ScreenRoute, customDirection?: NavigationDirection) => void;
  goBack: () => void;
}

const NavigationContext = createContext<NavigationContextType | undefined>(undefined);

const routeToHash = (screen: ScreenRoute): string => {
  switch (screen) {
    case 'send':
      return '#send';
    case 'prepare':
      return '#prepare';
    case 'receive':
      return '#receive';
    case 'transfer':
      return '#transfer';
    case 'invite':
      return '#invite';
    case 'history':
      return '#history';
    case 'scan':
      return '#scan';
    case 'home':
    default:
      return '#home';
  }
};

const hashToRoute = (hash: string): ScreenRoute => {
  const clean = hash.replace(/^#\/?/, '').toLowerCase();
  switch (clean) {
    case 'send':
      return 'send';
    case 'prepare':
      return 'prepare';
    case 'receive':
      return 'receive';
    case 'transfer':
      return 'transfer';
    case 'invite':
      return 'invite';
    case 'history':
      return 'history';
    case 'scan':
      return 'scan';
    case 'home':
    default:
      return 'home';
  }
};

const TAB_ROUTES: ScreenRoute[] = ['home', 'history', 'invite'];

const determineDirection = (
  from: ScreenRoute,
  to: ScreenRoute,
  explicitDirection?: NavigationDirection
): NavigationDirection => {
  if (explicitDirection) return explicitDirection;
  if (from === to) return 'none';

  // Returning to home from modal or sub screens is a pop exit
  if (to === 'home' && (from === 'send' || from === 'receive' || from === 'transfer' || from === 'scan' || from === 'prepare')) {
    return 'pop';
  }

  // Switching between main tab bar destinations
  if (TAB_ROUTES.includes(from) && TAB_ROUTES.includes(to)) {
    return 'tab';
  }

  // Pushing a deeper modal / action screen
  return 'push';
};

export const NavigationProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [historyStack, setHistoryStack] = useState<ScreenRoute[]>(() => {
    const initialRoute = typeof window !== 'undefined' ? hashToRoute(window.location.hash) : 'home';
    return [initialRoute];
  });

  const [direction, setDirection] = useState<NavigationDirection>('none');
  const currentScreen = historyStack[historyStack.length - 1] || 'home';
  const prevScreenRef = useRef<ScreenRoute>(currentScreen);

  const navigate = useCallback((screen: ScreenRoute, customDirection?: NavigationDirection) => {
    setHistoryStack((prev) => {
      const active = prev[prev.length - 1] || 'home';
      if (active === screen) return prev;
      const nextDir = determineDirection(active, screen, customDirection);
      setDirection(nextDir);
      prevScreenRef.current = active;
      return [...prev, screen];
    });
    if (typeof window !== 'undefined') {
      window.location.hash = routeToHash(screen);
    }
  }, []);

  const goBack = useCallback(() => {
    setDirection('pop');
    setHistoryStack((prev) => {
      if (prev.length > 1) {
        const next = prev.slice(0, prev.length - 1);
        const lastScreen = next[next.length - 1];
        if (typeof window !== 'undefined') {
          window.location.hash = routeToHash(lastScreen);
        }
        return next;
      }
      if (typeof window !== 'undefined') {
        window.location.hash = '#home';
      }
      return ['home'];
    });
  }, []);

  // Listen to browser hash changes (including Android back button)
  useEffect(() => {
    const onHashChange = () => {
      const targetScreen = hashToRoute(window.location.hash);
      setHistoryStack((prev) => {
        const active = prev[prev.length - 1] || 'home';
        if (active === targetScreen) return prev;
        const isBack = prev.length > 1 && prev[prev.length - 2] === targetScreen;
        const dir = isBack ? 'pop' : determineDirection(active, targetScreen);
        setDirection(dir);
        if (isBack) {
          return prev.slice(0, prev.length - 1);
        }
        return [...prev, targetScreen];
      });
    };

    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  return (
    <NavigationContext.Provider value={{ currentScreen, direction, navigate, goBack }}>
      {children}
    </NavigationContext.Provider>
  );
};

export const useAppNavigation = (): NavigationContextType => {
  const context = useContext(NavigationContext);
  if (!context) {
    throw new Error('useAppNavigation must be used within a NavigationProvider');
  }
  return context;
};
