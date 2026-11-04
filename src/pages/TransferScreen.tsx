import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppNavigation } from '../services/navigationService';
import { storageService } from '../services/storageService';
import { athirNativeService, TransferProgressEvent } from '../services/athirNativeService';
import { FileTuple } from '../types';
import './TransferScreen.css';

export const TransferScreen: React.FC = () => {
  const { navigate } = useAppNavigation();

  useEffect(() => {
    document.title = 'متصل';
  }, []);

  // 1. Load initial files from selection
  const [files, setFiles] = useState<FileTuple[]>(() => {
    const sel = storageService.getStoredSelection();
    if (sel && sel.length > 0) return sel;
    return [];
  });

  useEffect(() => {
    athirNativeService.getSelection().then((res) => {
      if (res && res.items && res.items.length > 0) {
        setFiles(res.items.map((i) => [i.name, i.sizeMB, i.thumbnail, i.uri, i.id]));
      }
    });
  }, []);

  // 2. Chat messages
  const [messages, setMessages] = useState<string[]>(() => {
    try {
      const rawMsgs = sessionStorage.getItem('msgs');
      if (rawMsgs) {
        const parsed = JSON.parse(rawMsgs);
        if (Array.isArray(parsed)) return parsed;
      }
    } catch {
      // Fallback
    }
    return [];
  });

  const totalMB = useMemo(() => {
    return files.reduce((acc, f) => acc + f[1], 0);
  }, [files]);

  // Real transfer state from native events
  const [sentMB, setSentMB] = useState(0);
  const [speedText, setSpeedText] = useState('');
  const [completedFileNames, setCompletedFileNames] = useState<Set<string>>(new Set());
  const [run, setRun] = useState(true);
  const [fin, setFin] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [txText, setTxText] = useState('');
  const [topPbOpacity, setTopPbOpacity] = useState(0);
  const [errorMessage, setErrorMessage] = useState('');
  const supported = athirNativeService.isNative();

  const menuRef = useRef<HTMLDivElement>(null);

  // 3. Scroll opacity listener for top backdrop
  useEffect(() => {
    const handleScroll = () => {
      if (document.documentElement.scrollHeight > window.innerHeight + 1) {
        setTopPbOpacity(Math.min(1, window.scrollY / 28));
      }
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    handleScroll();
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // 4. Close menu on click outside
  useEffect(() => {
    const handleDocClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setIsMenuOpen(false);
      }
    };
    document.addEventListener('click', handleDocClick);
    return () => document.removeEventListener('click', handleDocClick);
  }, []);

  // 5. Connect native transfer progress and event listeners
  useEffect(() => {
    const cleanupProgress = athirNativeService.onTransferProgress((e: TransferProgressEvent) => {
      setSentMB(e.sentMB);
      setSpeedText(e.speedDisplay);
      if (e.percent >= 100) {
        setFin(true);
      }
    });

    const cleanupComplete = athirNativeService.onTransferComplete(() => {
      setFin(true);
      setRun(false);
    });

    const cleanupChat = athirNativeService.onChatMessage((data) => {
      setMessages((prev) => [...prev, data.message]);
    });

    const cleanupError = athirNativeService.onTransferError((data) => {
      setErrorMessage(data.error || 'فشل النقل');
      setRun(false);
    });

    const cleanupIntegrity = athirNativeService.onFileIntegrity((data) => {
      if (!data.verified) {
        setErrorMessage(
          `فشل التحقق من سلامة الملف "${data.fileName}" بعد النقل. أعد المحاولة.`
        );
      }
    });

    return () => {
      cleanupProgress();
      cleanupComplete();
      cleanupChat();
      cleanupError();
      cleanupIntegrity();
    };
  }, [files]);

  // 6. Action button state
  const actionState: 'send' | 'off' | 'stop' | 'play' = useMemo(() => {
    if (txText.trim()) return 'send';
    if (fin) return 'off';
    if (run) return 'stop';
    return 'play';
  }, [txText, fin, run]);

  const handleActionClick = () => {
    if (actionState === 'send') {
      const text = txText.trim();
      if (!text) return;
      const updatedMsgs = [...messages, text];
      setMessages(updatedMsgs);
      try {
        sessionStorage.msgs = JSON.stringify(updatedMsgs);
      } catch {
        // Ignore
      }
      athirNativeService.sendChatMessage(text);
      setTxText('');
      setTimeout(() => {
        window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
      }, 50);
    } else if (actionState === 'stop') {
      athirNativeService.pauseTransfer();
      setRun(false);
    } else if (actionState === 'play') {
      athirNativeService.resumeTransfer();
      setRun(true);
    }
  };

  const handleClose = () => {
    athirNativeService.cancelTransfer();
    navigate('home');
  };

  const overallPercent = totalMB > 0 ? Math.min(100, (sentMB / totalMB) * 100) : fin ? 100 : 0;

  // Helper to generate the 5 glass blur backdrop layers
  const renderBackdropLayers = (direction: 'top' | 'bottom') => {
    const layers = [];
    for (let k = 0; k < 5; k++) {
      const n = 100 - k * 17;
      const m = `linear-gradient(to ${direction}, #000 ${n - 35}%, transparent ${n}%)`;
      const blurPx = (0.7 * Math.pow(2, k)).toFixed(1);
      const filterCss = `blur(${blurPx}px)`;
      layers.push(
        <i
          key={k}
          style={{
            backdropFilter: filterCss,
            WebkitBackdropFilter: filterCss,
            WebkitMask: m,
            mask: m,
          }}
        />
      );
    }
    return layers;
  };

  return (
    <div className="page-view transfer-page">
      {/* Top and Bottom Backdrop Blur Layers */}
      <div className="pb" style={{ opacity: topPbOpacity }}>
        {renderBackdropLayers('bottom')}
      </div>
      <div className="pb b">{renderBackdropLayers('top')}</div>

      {/* Top Header */}
      <header className="top">
        <button
          type="button"
          className="g c ci"
          aria-label="إغلاق"
          onClick={handleClose}
        >
          <svg className="i">
            <use href="#x" />
          </svg>
        </button>
        <div className="g c">
          <i className="dot"></i>متصل · نقل مشفّر
        </div>
        <div className="g c ci">{files.length}</div>
      </header>

      {/* Main Content Area */}
      <main>
        <div className="pg">
          <i
            id="fl"
            style={
              {
                '--e': `calc(${overallPercent}% + ${overallPercent * 0.96}px)`,
              } as React.CSSProperties
            }
          ></i>
          <b id="pc">{Math.floor(overallPercent)}%</b>
          <span id="mb">
            {fin
              ? 'اكتمل النقل'
              : run
              ? speedText
                ? `${sentMB.toFixed(1)} / ${totalMB.toFixed(1)} MB (${speedText})`
                : `${sentMB.toFixed(1)} / ${totalMB.toFixed(1)} MB`
              : 'متوقف مؤقتًا'}
          </span>
        </div>

        {!supported && (
          <div
            className="card"
            style={{ padding: '16px', marginBottom: '12px', textAlign: 'center' }}
          >
            <small style={{ color: 'var(--t2)', fontSize: '13px', lineHeight: 1.6 }}>
              النقل الحقيقي يعمل داخل تطبيق أندرويد فقط. هذه معاينة واجهة.
            </small>
          </div>
        )}

        {errorMessage && (
          <div
            className="card"
            style={{ padding: '14px 16px', marginBottom: '12px', textAlign: 'center' }}
          >
            <small style={{ color: 'var(--danger)', fontSize: '13px', lineHeight: 1.6 }}>
              {errorMessage}
            </small>
          </div>
        )}

        <div className="h2">الملفات</div>

        <div className="card" id="L">
          {files.length === 0 ? (
            <div style={{ padding: '24px', textAlign: 'center', color: 'var(--t2)' }}>
              لا توجد ملفات قيد النقل
            </div>
          ) : (
            files.map((file, i) => {
              const fileName = file[0];
              const fileSize = file[1];
              const isComplete = fin || completedFileNames.has(fileName) || overallPercent >= 100;
              const pctDisplay = isComplete ? '✓' : `${Math.floor(overallPercent)}%`;

              return (
                <div key={`${fileName}-${i}`} className="li">
                  <span className="ic">{fileName.slice(0, 2).toUpperCase()}</span>
                  <div className="gr">
                    <b>{fileName}</b>
                    <div className="tr s">
                      <i style={{ width: `${isComplete ? 100 : overallPercent}%` }}></i>
                    </div>
                  </div>
                  <small className={`st ${isComplete ? 'ok' : ''}`}>
                    {pctDisplay}
                  </small>
                </div>
              );
            })
          )}
        </div>

        <div className="ms" id="ms">
          {messages.map((msg, index) => (
            <div key={index} className="bb">
              {msg}
            </div>
          ))}
        </div>
      </main>

      {/* Attachment Menu Popup */}
      <div
        className={`mn g ${isMenuOpen ? 'open' : ''}`}
        id="mn"
        ref={menuRef}
      >
        <a
          href="#send"
          onClick={(e) => {
            e.preventDefault();
            setIsMenuOpen(false);
            navigate('send');
          }}
        >
          <svg className="i">
            <use href="#gr" />
          </svg>
          التطبيقات
        </a>
        <a
          href="#send"
          onClick={(e) => {
            e.preventDefault();
            setIsMenuOpen(false);
            navigate('send');
          }}
        >
          <svg className="i">
            <use href="#im" />
          </svg>
          الصور
        </a>
        <a
          href="#send"
          onClick={(e) => {
            e.preventDefault();
            setIsMenuOpen(false);
            navigate('send');
          }}
        >
          <svg className="i">
            <use href="#vd" />
          </svg>
          الفيديو
        </a>
        <a
          href="#send"
          onClick={(e) => {
            e.preventDefault();
            setIsMenuOpen(false);
            navigate('send');
          }}
        >
          <svg className="i">
            <use href="#mu" />
          </svg>
          الموسيقى
        </a>
        <a
          href="#send"
          onClick={(e) => {
            e.preventDefault();
            setIsMenuOpen(false);
            navigate('send');
          }}
        >
          <svg className="i">
            <use href="#doc" />
          </svg>
          الملفات
        </a>
      </div>

      {/* Bottom Bar Input & Controls */}
      <div className="bar">
        <div className="g cb">
          <button
            type="button"
            className={`atb ${isMenuOpen ? 'open' : ''}`}
            id="atb"
            aria-label="إرفاق"
            onClick={(e) => {
              e.stopPropagation();
              setIsMenuOpen((prev) => !prev);
            }}
          >
            <svg className="i">
              <use href="#plus" />
            </svg>
          </button>
          <input
            id="tx"
            placeholder="أدخل الرسالة…"
            enterKeyHint="send"
            autoComplete="off"
            value={txText}
            onChange={(e) => setTxText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                if (actionState === 'send') {
                  handleActionClick();
                }
              }
            }}
          />
          <button
            type="button"
            className="act"
            id="act"
            data-s={actionState}
            aria-label="إيقاف / إرسال"
            onClick={handleActionClick}
          >
            <svg className="i iu">
              <use href="#up" />
            </svg>
            <svg className="i fl is">
              <use href="#stp" />
            </svg>
            <svg className="i fl ip">
              <use href="#pl" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
};
