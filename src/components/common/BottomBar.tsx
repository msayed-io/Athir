import React, { ReactNode } from 'react';
import './BottomBar.css';

interface BottomBarProps {
  children: ReactNode;
}

export const BottomBar: React.FC<BottomBarProps> = ({ children }) => {
  return <div className="bar">{children}</div>;
};
