import React from 'react';
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
  return (
    <div className="card" id="L">
      {files.map((file) => {
        const name = file[0];
        const size = file[1];
        const isSelected = selectedNames.has(name);
        const iconBadge = getFileBadge(name);

        return (
          <div
            key={name}
            className={`li ${isSelected ? 'sel' : ''}`}
            onClick={() => onToggleFile(file)}
          >
            <span className="ic">{iconBadge}</span>
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

