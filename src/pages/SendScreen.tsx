import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { SearchField } from 'glisse';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomBar } from '../components/common/BottomBar';
import { CategoryTabs, CategoryType } from '../components/send/CategoryTabs';
import { FileSelectList } from '../components/send/FileSelectList';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { storageService } from '../services/storageService';
import { athirNativeService, CatalogItem } from '../services/athirNativeService';
import { FileTuple } from '../types';
import './SendScreen.css';

export const SendScreen: React.FC = () => {
  const { navigate, goBack } = useAppNavigation();

  useEffect(() => {
    document.title = 'إرسال';
  }, []);

  const [activeCategory, setActiveCategory] = useState<CategoryType>('apps');
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [catalogItems, setCatalogItems] = useState<CatalogItem[]>([]);
  const [loading, setLoading] = useState(true);

  // Selected items stored by filename -> FileTuple
  const [selectedItemsMap, setSelectedItemsMap] = useState<Map<string, FileTuple>>(() => {
    const initialMap = new Map<string, FileTuple>();
    const stored = storageService.getStoredSelection();
    if (stored) {
      stored.forEach((item) => {
        initialMap.set(item[0], item);
      });
    }
    return initialMap;
  });

  const loadCategory = useCallback(async (cat: CategoryType, q?: string) => {
    setLoading(true);
    try {
      const items = await athirNativeService.getMediaCatalog(cat, q);
      setCatalogItems(items);
    } catch (e) {
      console.warn('Error loading catalog:', e);
      setCatalogItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCategory(activeCategory, searchQuery);
  }, [activeCategory, searchQuery, loadCategory]);

  const categoryFiles: FileTuple[] = useMemo(() => {
    return catalogItems.map((item) => [
      item.name,
      item.sizeMB,
      item.thumbnail,
      item.uri,
      item.id,
    ]);
  }, [catalogItems]);

  const selectedNames = useMemo(() => {
    return new Set(selectedItemsMap.keys());
  }, [selectedItemsMap]);

  const totalSelectedMB = useMemo(() => {
    let sum = 0;
    selectedItemsMap.forEach((tuple) => {
      sum += tuple[1];
    });
    return Math.round(sum * 10) / 10;
  }, [selectedItemsMap]);

  const handleToggleFile = (file: FileTuple) => {
    const fileName = file[0];
    const sizeMB = file[1];
    const thumb = file[2];
    const uri = file[3] || '';
    const id = file[4] || fileName;

    setSelectedItemsMap((prev) => {
      const next = new Map(prev);
      if (next.has(fileName)) {
        next.delete(fileName);
        athirNativeService.updateSelection('remove', {
          id,
          name: fileName,
          sizeMB,
          sizeBytes: Math.round(sizeMB * 1024 * 1024),
          uri,
          mimeType: '*/*',
          category: activeCategory,
        });
      } else {
        next.set(fileName, file);
        athirNativeService.updateSelection('add', {
          id,
          name: fileName,
          sizeMB,
          sizeBytes: Math.round(sizeMB * 1024 * 1024),
          uri,
          mimeType: '*/*',
          category: activeCategory,
        });
      }
      return next;
    });
  };

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

        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px', color: 'var(--t2)' }}>
            جارٍ تحميل ملفات الجهاز…
          </div>
        ) : (
          <FileSelectList
            files={categoryFiles}
            selectedNames={selectedNames}
            onToggleFile={handleToggleFile}
          />
        )}
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
