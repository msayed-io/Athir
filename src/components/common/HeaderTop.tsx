import React, { ReactNode } from 'react';
import './HeaderTop.css';

interface HeaderTopProps {
  children: ReactNode;
}

export const HeaderTop: React.FC<HeaderTopProps> = ({ children }) => {
  return <header className="top">{children}</header>;
};
