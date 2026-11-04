import React, { useState, useEffect, useMemo } from 'react';
import { SearchField } from 'glisse';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomBar } from '../components/common/BottomBar';
import { CategoryTabs, CategoryType } from '../components/send/CategoryTabs';
import { FileSelectList } from '../components/send/FileSelectList';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { storageService, CATEGORIZED_FILES } from '../services/storageService';
import { FileTuple } from '../types';
import './SendScreen.css';

export const SendScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'إرسال';
  }, []);

  // Category selection across 6 categories
  const [activeCategory, setActiveCategory] = useState<CategoryType>('apps');

  // Search toggle & query
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Selected items stored by filename -> FileTuple
  const [selectedItemsMap, setSelectedItemsMap] = useState<Map<string, FileTuple>>(() => {
    const initialMap = new Map<string, FileTuple>();
    // Pre-select first 2 items of default 'apps' category
    const defaultApps = CATEGORIZED_FILES.apps;
    if (defaultApps[0]) initialMap.set(defaultApps[0][0], defaultApps[0]);
    if (defaultApps[1]) initialMap.set(defaultApps[1][0], defaultApps[1]);
    return initialMap;
  });

  // Current category file items
  const categoryFiles: FileTuple[] = useMemo(() => {
    return CATEGORIZED_FILES[activeCategory] || [];
  }, [activeCategory]);

  // Filtered files based on search query
  const filteredFiles = useMemo(() => {
    if (!searchQuery.trim()) return categoryFiles;
    const query = searchQuery.toLowerCase();
    return categoryFiles.filter(([name]) => name.toLowerCase().includes(query));
  }, [categoryFiles, searchQuery]);

  // Set of selected filenames for quick check
  const selectedNames = useMemo(() => {
    return new Set(selectedItemsMap.keys());
  }, [selectedItemsMap]);

  // Total selected size in MB
  const totalSelectedMB = useMemo(() => {
    let sum = 0;
    selectedItemsMap.forEach((tuple) => {
      sum += tuple[1];
    });
    return sum;
  }, [selectedItemsMap]);

  // Toggle selection for a file item
  const handleToggleFile = (file: FileTuple) => {
    const fileName = file[0];
    setSelectedItemsMap((prev) => {
      const next = new Map(prev);
      if (next.has(fileName)) {
        next.delete(fileName);
      } else {
        next.set(fileName, file);
      }
      return next;
    });
  };

  // Save selected items to Session Storage and navigate to scan screen
  const handleSend = () => {
    if (selectedItemsMap.size === 0) return;
    const selectedList = Array.from(selectedItemsMap.values());
    storageService.setStoredSelection(selectedList);
    navigate('scan');
  };

  const hasSelection = selectedItemsMap.size > 0;

  return (
    <div className="page-view send-page">
      <HeaderTop>
        <button
          type="button"
          className="g c ci"
          aria-label="الرجوع"
          onClick={goBack}
        >
          <Icon name="bk" />
        </button>
        <div className="g c">إرسال</div>
        <button
          type="button"
          className="g c ci"
          aria-label="بحث"
          onClick={() => setIsSearchOpen((prev) => !prev)}
        >
          <Icon name="srch" />
        </button>
      </HeaderTop>

      <main>
        {isSearchOpen && (
          <div className="search-input-wrap">
            <SearchField
              placeholder="ابحث عن ملف أو تطبيق…"
              value={searchQuery}
              onValueChange={setSearchQuery}
              autoFocus
            />
          </div>
        )}

        <CategoryTabs
          activeCategory={activeCategory}
          onChange={setActiveCategory}
        />

        <FileSelectList
          files={filteredFiles}
          selectedNames={selectedNames}
          onToggleFile={handleToggleFile}
        />
      </main>

      <BottomBar>
        <div className="g bx">
          <span id="sm">
            {selectedItemsMap.size} عناصر
            <small>{totalSelectedMB.toFixed(1)} MB</small>
          </span>
          <button
            type="button"
            className="g c p"
            id="go"
            style={{
              opacity: hasSelection ? 1 : 0.4,
              pointerEvents: hasSelection ? 'auto' : 'none',
            }}
            onClick={handleSend}
          >
            إرسال
          </button>
        </div>
      </BottomBar>
    </div>
  );
};

