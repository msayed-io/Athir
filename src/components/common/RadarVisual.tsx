import React from 'react';
import { Icon, IconSymbolId } from '../../assets/icons/SvgSprite';
import './RadarVisual.css';

interface RadarVisualProps {
  iconName?: IconSymbolId;
}

export const RadarVisual: React.FC<RadarVisualProps> = ({ iconName = 'send' }) => {
  return (
    <div className="rd">
      <em />
      <em />
      <em />
      <div className="o">
        <i />
        <i />
        <i />
      </div>
      <div className="core">
        <Icon name={iconName} className="i" />
      </div>
    </div>
  );
};
