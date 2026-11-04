import React from 'react';
import './SvgSprite.css';

export type IconSymbolId =
  | 'bk'
  | 'bell'
  | 'plus'
  | 'send'
  | 'dl'
  | 'fold'
  | 'srch'
  | 'ck'
  | 'pa'
  | 'pl'
  | 'x'
  | 'qr'
  | 'bt'
  | 'hs'
  | 'hm'
  | 'cl'
  | 'pin'
  | 'gps'
  | 'ph'
  | 'gr'
  | 'im'
  | 'vd'
  | 'mu'
  | 'doc'
  | 'up'
  | 'stp';

export interface IconProps extends React.SVGProps<SVGSVGElement> {
  name: IconSymbolId;
  className?: string;
}

export const Icon: React.FC<IconProps> = ({ name, className = 'i', ...props }) => {
  return (
    <svg className={className} {...props}>
      <use href={`#${name}`} />
    </svg>
  );
};

export const SvgSprite: React.FC = () => {
  return (
    <svg width="0" height="0" style={{ position: 'absolute' }}>
      <symbol id="bk" viewBox="0 0 24 24">
        <path d="M9 5l7 7-7 7" />
      </symbol>
      <symbol id="bell" viewBox="0 0 24 24">
        <path d="M6.5 16v-5a5.5 5.5 0 0111 0v5l1.5 2h-14zM10 21h4" />
      </symbol>
      <symbol id="plus" viewBox="0 0 24 24">
        <path d="M12 5v14M5 12h14" />
      </symbol>
      <symbol id="send" viewBox="0 0 24 24">
        <path d="M21 3L10.5 13.5M21 3l-6.5 18-4-7.5L3 9.5z" />
      </symbol>
      <symbol id="dl" viewBox="0 0 24 24">
        <path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M5 20h14" />
      </symbol>
      <symbol id="fold" viewBox="0 0 24 24">
        <path d="M3.5 7a2 2 0 012-2H10l2 2.5h6.5a2 2 0 012 2V18a2 2 0 01-2 2h-13a2 2 0 01-2-2z" />
      </symbol>
      <symbol id="srch" viewBox="0 0 24 24">
        <path d="M11 18a7 7 0 100-14 7 7 0 000 14zM20 20l-4-4" />
      </symbol>
      <symbol id="ck" viewBox="0 0 24 24">
        <path d="M5 12.5l4.5 4.5L19 7.5" />
      </symbol>
      <symbol id="pa" viewBox="0 0 24 24">
        <path d="M9 6v12M15 6v12" />
      </symbol>
      <symbol id="pl" viewBox="0 0 24 24">
        <path d="M8 5.5v13l11-6.5z" />
      </symbol>
      <symbol id="x" viewBox="0 0 24 24">
        <path d="M6 6l12 12M18 6L6 18" />
      </symbol>
      <symbol id="qr" viewBox="0 0 24 24">
        <path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2.5v2.5H14zM19 14v2M14 19.5h3M19 19v1" />
      </symbol>
      <symbol id="bt" viewBox="0 0 24 24">
        <path d="M7 7.5l10 9-5 4.5v-18l5 4.5-10 9" />
      </symbol>
      <symbol id="hs" viewBox="0 0 24 24">
        <path d="M2.5 9.5a13 13 0 0119 0M6 13a8 8 0 0112 0M9.5 16.5a3 3 0 015 0M12 20h.01" />
      </symbol>
      <symbol id="hm" viewBox="0 0 24 24">
        <path d="M4 11l8-7 8 7v8.5a.5.5 0 01-.5.5H15v-6H9v6H4.5a.5.5 0 01-.5-.5z" />
      </symbol>
      <symbol id="cl" viewBox="0 0 24 24">
        <path d="M12 21a9 9 0 100-18 9 9 0 000 18zM12 7.5V12l3 2" />
      </symbol>
      <symbol id="pin" viewBox="0 0 24 24">
        <path d="M12 21s7-5.6 7-11a7 7 0 10-14 0c0 5.4 7 11 7 11zM12 12.2a2.2 2.2 0 100-4.4 2.2 2.2 0 000 4.4z" />
      </symbol>
      <symbol id="gps" viewBox="0 0 24 24">
        <path d="M20 4L4 11l7 2 2 7z" />
      </symbol>
      <symbol id="ph" viewBox="0 0 24 24">
        <path d="M8 3h8a2 2 0 012 2v14a2 2 0 01-2 2H8a2 2 0 01-2-2V5a2 2 0 012-2zM11 18h2" />
      </symbol>
      <symbol id="gr" viewBox="0 0 24 24">
        <path d="M5 4h5a1 1 0 011 1v5a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1zM14 4h5a1 1 0 011 1v5a1 1 0 01-1 1h-5a1 1 0 01-1-1V5a1 1 0 011-1zM5 13h5a1 1 0 011 1v5a1 1 0 01-1 1H5a1 1 0 01-1-1v-5a1 1 0 011-1zM14 13h5a1 1 0 011 1v5a1 1 0 01-1 1h-5a1 1 0 01-1-1v-5a1 1 0 011-1z" />
      </symbol>
      <symbol id="im" viewBox="0 0 24 24">
        <path d="M5 4h14a1 1 0 011 1v14a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1zM4 16l5-5 4 4 3-3 4 4M9 8.5h.01" />
      </symbol>
      <symbol id="vd" viewBox="0 0 24 24">
        <path d="M4 7h9a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V9a2 2 0 012-2zM15 11l7-3.5v9L15 13" />
      </symbol>
      <symbol id="mu" viewBox="0 0 24 24">
        <path d="M9 18V5l11-2v13M9 18a3 3 0 11-6 0 3 3 0 016 0zM20 16a3 3 0 11-6 0 3 3 0 016 0z" />
      </symbol>
      <symbol id="doc" viewBox="0 0 24 24">
        <path d="M7 3h7l5 5v12a1 1 0 01-1 1H7a1 1 0 01-1-1V4a1 1 0 011-1zM14 3v5h5M9 13h6M9 17h6" />
      </symbol>
      <symbol id="up" viewBox="0 0 24 24">
        <path d="M12 19V5M5.5 11.5L12 5l6.5 6.5" />
      </symbol>
      <symbol id="stp" viewBox="0 0 24 24">
        <path d="M8.5 7h7a1.5 1.5 0 011.5 1.5v7a1.5 1.5 0 01-1.5 1.5h-7A1.5 1.5 0 017 15.5v-7A1.5 1.5 0 018.5 7z" />
      </symbol>
    </svg>
  );
};
