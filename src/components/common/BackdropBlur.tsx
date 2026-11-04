import React, { useMemo } from 'react';
import './BackdropBlur.css';

interface BackdropLayerProps {
  isBottom?: boolean;
}

const BackdropLayer: React.FC<BackdropLayerProps> = ({ isBottom }) => {
  const direction = isBottom ? 'top' : 'bottom';

  const layers = useMemo(() => {
    return Array.from({ length: 5 }, (_, k) => {
      const n = 100 - k * 17;
      const mask = `linear-gradient(to ${direction}, #000 ${n - 35}%, transparent ${n}%)`;
      const blur = `blur(${(0.7 * Math.pow(2, k)).toFixed(1)}px)`;

      return {
        key: k,
        style: {
          backdropFilter: blur,
          WebkitBackdropFilter: blur,
          maskImage: mask,
          WebkitMaskImage: mask,
        } as React.CSSProperties,
      };
    });
  }, [direction]);

  return (
    <div className={`pb ${isBottom ? 'b' : ''}`}>
      {layers.map((layer) => (
        <i key={layer.key} style={layer.style} />
      ))}
    </div>
  );
};

import { useAppNavigation } from '../../services/navigationService';

export const BackdropBlur: React.FC = () => {
  const { currentScreen } = useAppNavigation();
  const showBottom = currentScreen !== 'scan';

  return (
    <>
      <BackdropLayer />
      {showBottom && <BackdropLayer isBottom />}
    </>
  );
};
