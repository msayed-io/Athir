import React, { useRef } from 'react';
import { FileTuple } from '../../types';
import { Icon } from '../../assets/icons/SvgSprite';
import './FileSelectList.css';

interface FileSelectListProps {
  files: FileTuple[];
  selectedNames: Set<string>;
  onToggleFile: (file: FileTuple) => void;
}

const getFileBadge = (filename: string): string => {
  const parts = filename.split('.');
  if (parts.length > 1) {
    const ext = parts.pop()?.toUpperCase() || '';
    if (ext.length >= 2 && ext.length <= 4) {
      return ext;
    }
  }
  return filename.slice(0, 2).toUpperCase();
};

export const FileSelectList: React.FC<FileSelectListProps> = ({
  files,
  selectedNames,
  onToggleFile,
}) => {
  /**
   * Scroll-vs-tap guard.
   *
   * On Android WebView a slow drag inside a scroll container can still end in a `click`,
   * which selected a file the user never meant to tap. A pointer gesture that travelled
   * more than a few pixels (or lasted longer than a fling) is treated as a scroll and the
   * click is swallowed.
   */
  const gestureRef = useRef<{ x: number; y: number; moved: boolean }>({
    x: 0,
    y: 0,
    moved: false,
  });

  const onPointerDown = (event: React.PointerEvent) => {
    gestureRef.current = { x: event.clientX, y: event.clientY, moved: false };
  };

  const onPointerMove = (event: React.PointerEvent) => {
    const gesture = gestureRef.current;
    if (!gesture || gesture.moved) return;
    if (
      Math.abs(event.clientX - gesture.x) > 8 ||
      Math.abs(event.clientY - gesture.y) > 8
    ) {
      gesture.moved = true;
    }
  };

  const handleRowClick = (file: FileTuple) => {
    if (gestureRef.current.moved) {
      // The gesture was a scroll, not a tap.
      gestureRef.current.moved = false;
      return;
    }
    onToggleFile(file);
  };

  if (files.length === 0) {
    return (
      <div className="card" id="L" style={{ padding: '36px 16px', textAlign: 'center' }}>
        <div style={{ color: 'var(--t2)', fontSize: '14px' }}>لا توجد عناصر في هذا القسم</div>
      </div>
    );
  }

  return (
    <div
      className="card"
      id="L"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerCancel={() => {
        gestureRef.current.moved = false;
      }}
    >
      {files.map((file) => {
        const name = file[0];
        const size = file[1];
        const thumb = file[2];
        const isSelected = selectedNames.has(name);
        const iconBadge = getFileBadge(name);

        return (
          <div
            key={name}
            className={`li ${isSelected ? 'sel' : ''}`}
            onClick={() => handleRowClick(file)}
          >
            {thumb ? (
              <img
                src={thumb}
                alt=""
                className="ic"
                style={{ objectFit: 'cover', borderRadius: '8px' }}
              />
            ) : (
              <span className="ic">{iconBadge}</span>
            )}
            <div className="gr">
              <b>{name}</b>
              <small>{size} MB</small>
            </div>
            <span className="ck">
              <Icon name="ck" />
            </span>
          </div>
        );
      })}
    </div>
  );
};
