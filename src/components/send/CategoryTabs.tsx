import React, { useRef, useEffect } from 'react';
import { TabBar } from 'glisse';
import './CategoryTabs.css';

export type CategoryType = 'apps' | 'photos' | 'audio' | 'videos' | 'contacts' | 'files';

interface CategoryTabsProps {
  activeCategory: CategoryType;
  onChange: (category: CategoryType) => void;
}

interface CategoryOption {
  value: CategoryType;
  label: string;
}

// In Arabic (reading right-to-left):
// Left-to-right items in LTR container for glisse physics:
// files, contacts, videos, audio, photos, apps
// So that 'apps' (التطبيقات) is on the physical right!
const CATEGORIES: CategoryOption[] = [
  { value: 'files', label: 'الملفات' },
  { value: 'contacts', label: 'جهات الاتصال' },
  { value: 'videos', label: 'الفيديوهات' },
  { value: 'audio', label: 'الصوتيات' },
  { value: 'photos', label: 'الصور' },
  { value: 'apps', label: 'التطبيقات' },
];

export const CategoryTabs: React.FC<CategoryTabsProps> = ({ activeCategory, onChange }) => {
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll active category into view smoothly when selection changes
  useEffect(() => {
    if (scrollRef.current) {
      const activeEl = scrollRef.current.querySelector<HTMLElement>('[data-selected]');
      if (activeEl) {
        activeEl.scrollIntoView({
          behavior: 'smooth',
          inline: 'center',
          block: 'nearest',
        });
      }
    }
  }, [activeCategory]);

  // Initial scroll position to the right (towards 'التطبيقات') on mount
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollLeft = scrollRef.current.scrollWidth;
    }
  }, []);

  return (
    <div className="category-tabs-capsule">
      <div className="category-tabs-scroll" ref={scrollRef} dir="ltr">
        <TabBar
          aria-label="أقسام الملفات"
          value={activeCategory}
          onValueChange={(val) => {
            if (val) onChange(val as CategoryType);
          }}
          items={CATEGORIES}
        />
      </div>
    </div>
  );
};
