import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { SearchField } from 'glisse';
import { HeaderTop } from '../components/common/HeaderTop';
import { BottomBar } from '../components/common/BottomBar';
import { CategoryTabs, CategoryType } from '../components/send/CategoryTabs';
import { FileSelectList } from '../components/send/FileSelectList';
import { Icon } from '../assets/icons/SvgSprite';
import { useAppNavigation } from '../services/navigationService';
import { storageService } from '../services/storageService';
import {
  athirNativeService,
  CatalogItem,
  PermissionStatus,
} from '../services/athirNativeService';
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
  const [catalogError, setCatalogError] = useState('');
  const [permStatus, setPermStatus] = useState<PermissionStatus | null>(null);
  const supported = athirNativeService.isNative();
  /** Latest category/query, so a permission event reloads what the user is actually looking at. */
  const viewRef = useRef<{ category: string; query: string }>({ category: 'apps', query: '' });

  /**
   * Storage / media permissions: asked ONCE, on the first entry to this screen.
   *
   * All three Android 13+ media permissions go out as one array, so the system shows a single
   * "Allow access to photos, videos and music" card instead of four dialogs. After the first
   * grant the OS remembers it and `status.mediaGranted` is true, so the user is never asked
   * again - the file picker just opens. Hardware/pairing permissions are NOT handled here;
   * they live with the scan and receive sessions.
   */
  useEffect(() => {
    if (!supported) return;
    let alive = true;

    (async () => {
      const status = await athirNativeService.checkPermissions().catch(() => null);
      if (!alive || !status) return;
      setPermStatus(status);
      if (!status.mediaGranted) {
        await athirNativeService.requestMediaPermissions().catch(() => {});
      }
    })();

    const cleanup = athirNativeService.onPermissionsChanged((next) => {
      if (!alive) return;
      setPermStatus(next);
      // A fresh grant can reveal files that were invisible a second ago.
      athirNativeService.invalidateCatalogCache();
      void loadCategory(
        viewRef.current.category as Parameters<typeof loadCategory>[0],
        viewRef.current.query
      );
    });

    const onVisible = () => {
      if (document.visibilityState !== 'visible') return;
      // Returning from system settings ("All files access") must refresh the list.
      athirNativeService.checkPermissions().then((next) => {
        if (!alive) return;
        setPermStatus((prev) => {
          if (prev?.allFilesAccessGranted !== next.allFilesAccessGranted) {
            athirNativeService.invalidateCatalogCache();
            void loadCategory(
              viewRef.current.category as Parameters<typeof loadCategory>[0],
              viewRef.current.query
            );
          }
          return next;
        });
      }).catch(() => {});
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      alive = false;
      cleanup();
      document.removeEventListener('visibilitychange', onVisible);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [supported]);

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
    // Cached categories render instantly: no spinner, no "loading" flash.
    setLoading(!athirNativeService.hasCachedCatalog(cat, q));
    setCatalogError('');
    try {
      if (!supported) {
        setCatalogItems([]);
        return;
      }
      const items = await athirNativeService.getMediaCatalog(cat, q);
      setCatalogItems(items);
    } catch (e) {
      setCatalogItems([]);
      setCatalogError(
        e instanceof Error
          ? `تعذّر قراءة ملفات الجهاز: ${e.message}`
          : 'تعذّر قراءة ملفات الجهاز. تحقّق من الأذونات.'
      );
    } finally {
      setLoading(false);
    }
  }, [supported]);

  useEffect(() => {
    viewRef.current = { category: activeCategory, query: searchQuery };
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

  const handlePickDocuments = useCallback(async () => {
    if (!supported) return;
    setCatalogError('');
    try {
      const picked = await athirNativeService.pickDocuments();
      if (picked.length === 0) return;
      setSelectedItemsMap((prev) => {
        const next = new Map(prev);
        picked.forEach((doc) => {
          const tuple: FileTuple = [doc.name, doc.sizeMB, undefined, doc.uri, doc.id];
          next.set(doc.name, tuple);
          athirNativeService.updateSelection('add', {
            id: doc.id,
            name: doc.name,
            sizeMB: doc.sizeMB,
            sizeBytes: doc.sizeBytes,
            uri: doc.uri,
            mimeType: doc.mimeType,
            category: 'files',
          });
        });
        return next;
      });
    } catch (e) {
      const message = e instanceof Error ? e.message : '';
      if (message.includes('PICKER_CANCELLED')) return;
      setCatalogError(
        message ? `تعذّر فتح مستندات الجهاز: ${message}` : 'تعذّر فتح مستندات الجهاز.'
      );
    }
  }, [supported]);

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

        {supported && (
          <div style={{ display: 'flex', gap: '8px', padding: '0 16px 10px' }}>
            <button
              type="button"
              className="g c"
              style={{
                height: '38px',
                padding: '0 14px',
                fontSize: '13px',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
              }}
              onClick={handlePickDocuments}
            >
              <Icon name="fold" />
              مستندات الجهاز
            </button>
          </div>
        )}

        {!supported ? (
          <div className="card" style={{ padding: '28px 20px', textAlign: 'center' }}>
            <b style={{ display: 'block', fontSize: '15px', marginBottom: '8px' }}>
              متصفح الملفات يعمل داخل تطبيق أندرويد
            </b>
            <small style={{ color: 'var(--t2)', fontSize: '13px', lineHeight: 1.6 }}>
              قراءة التطبيقات والصور والفيديو والملفات من الجهاز وظيفة أصلية عبر
              MediaStore وPackageManager، ولا تُحاكى في المتصفح.
            </small>
          </div>
        ) : catalogError ? (
          <div className="card" style={{ padding: '28px 20px', textAlign: 'center' }}>
            <small style={{ color: 'var(--danger)', fontSize: '13px', lineHeight: 1.6 }}>
              {catalogError}
            </small>
          </div>
        ) : loading ? (
          <div style={{ textAlign: 'center', padding: '40px', color: 'var(--t2)' }}>
            جارٍ تحميل ملفات الجهاز…
          </div>
        ) : (
          <>
            <FileSelectList
              files={categoryFiles}
              selectedNames={selectedNames}
              onToggleFile={handleToggleFile}
            />
            {activeCategory === 'files' &&
              permStatus &&
              permStatus.sdkInt >= 30 &&
              permStatus.allFilesAccessGranted === false && (
                <div
                  className="card"
                  style={{ marginTop: '12px', padding: '16px', textAlign: 'center' }}
                >
                  <b style={{ display: 'block', fontSize: '13.5px', marginBottom: '6px' }}>
                    أندرويد يخفي ملفات التطبيقات الأخرى
                  </b>
                  <p
                    style={{
                      fontSize: '12.5px',
                      lineHeight: 1.6,
                      color: 'var(--t2)',
                      marginBottom: '10px',
                    }}
                  >
                    لرؤية ملفات APK وZIP وPDF التي نزّلتها تطبيقات أخرى، امنح أثير «الوصول لكل
                    الملفات». القائمة تتحدّث تلقائيًا عند عودتك.
                  </p>
                  <button
                    type="button"
                    className="g c"
                    style={{ height: '38px', fontSize: '13px' }}
                    onClick={() => {
                      athirNativeService.openAllFilesAccessSettings().catch(() => {
                        setCatalogError('تعذّر فتح شاشة الوصول لكل الملفات على هذا الجهاز.');
                      });
                    }}
                  >
                    السماح بالوصول لكل الملفات
                  </button>
                </div>
              )}

            {activeCategory === 'files' && categoryFiles.length === 0 && (
              <p
                style={{
                  marginTop: '12px',
                  fontSize: '12.5px',
                  lineHeight: 1.6,
                  color: 'var(--t2)',
                  textAlign: 'center',
                }}
              >
                لا يعرض النظام أي ملف إضافي في هذا التصنيف. استخدم زر «مستندات الجهاز» أعلى
                الصفحة لاختيار أي ملف من أي مجلد (بما فيها التنزيلات) عبر منتقي النظام.
              </p>
            )}
          </>
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
