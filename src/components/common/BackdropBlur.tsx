import React from 'react';
import './BackdropBlur.css';
import { useAppNavigation } from '../../services/navigationService';

interface BackdropLayerProps {
  isBottom?: boolean;
}

/**
 * Single glass scrim per screen edge.
 *
 * This used to be five stacked `backdrop-filter` surfaces per edge (ten in total, always
 * mounted). Every `backdrop-filter` forces the compositor to snapshot and re-blur the whole
 * area beneath it whenever anything repaints, so ten of them (plus the blur on every glass
 * button) saturated the Android WebView compositor and made the entire app janky - even
 * when the user was only tapping between screens.
 *
 * One layer per edge keeps the exact same visual language (a fade into a softly blurred
 * backdrop) at a fraction of the raster cost, because the mask gradient and the ::after
 * scrim carry almost all of the perceived effect.
 */
const BackdropLayer: React.FC<BackdropLayerProps> = ({ isBottom }) => {
  const direction = isBottom ? 'top' : 'bottom';
  const mask = `linear-gradient(to ${direction}, #000 45%, transparent 100%)`;

  return (
    <div className={`pb ${isBottom ? 'b' : ''}`}>
      <i
        style={{
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
          maskImage: mask,
          WebkitMaskImage: mask,
        }}
      />
    </div>
  );
};

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
