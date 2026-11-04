import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useAppNavigation } from '../services/navigationService';
import { storageService } from '../services/storageService';
import { FileTuple } from '../types';
import './TransferScreen.css';

const DEFAULT_FILES: FileTuple[] = [
  ['Athir App', 46.9],
  ['Velo', 10.4],
  ['Files Pro', 22.1],
];

export const TransferScreen: React.FC = () => {
  const { navigate } = useAppNavigation();

  useEffect(() => {
    document.title = 'متصل';
  }, []);

  // 1. Load initial files from sessionStorage.sel or fallback to default
  const files: FileTuple[] = useMemo(() => {
    let selFiles: FileTuple[] = DEFAULT_FILES;
    try {
      const rawSel = sessionStorage.getItem('sel');
      if (rawSel) {
        const parsed = JSON.parse(rawSel);
        if (Array.isArray(parsed) && parsed.length > 0) {
          selFiles = parsed;
        }
      }
    } catch {
      // Fallback
    }
    // Clean up sel as in transfer.html
    sessionStorage.removeItem('sel');
    return selFiles;
  }, []);

  // 2. Load initial messages from sessionStorage.msgs
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

  const fileOffsets = useMemo(() => {
    let sum = 0;
    return files.map((f) => {
      const current = sum;
      sum += f[1];
      return current;
    });
  }, [files]);

  // Transfer simulation state
  const [sentMB, setSentMB] = useState(0);
  const [run, setRun] = useState(true);
  const [fin, setFin] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [txText, setTxText] = useState('');
  const [topPbOpacity, setTopPbOpacity] = useState(0);

  const menuRef = useRef<HTMLDivElement>(null);

  // Animation Refs
  const runRef = useRef(true);
  const finRef = useRef(false);
  const sentRef = useRef(0);
  const elRef = useRef(0);
  const lastRef = useRef(performance.now());
  const totalRef = useRef(totalMB);
  totalRef.current = totalMB;

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

  // 5. Transfer animation loop
  useEffect(() => {
    lastRef.current = performance.now();
    let animId: number;

    const tick = (t: number) => {
      const delta = Math.min((t - lastRef.current) / 1000, 0.1);
      lastRef.current = t;

      if (runRef.current && !finRef.current) {
        elRef.current += delta;
        const el = elRef.current;
        const tt = totalRef.current;

        // Formula from transfer.html:
        // v = TT / Math.max(14, TT / 6) * (1 + 0.3 * Math.sin(el * 1.7) + 0.1 * Math.sin(el * 5.3))
        const base = tt / Math.max(14, tt / 6);
        const v = base * (1 + 0.3 * Math.sin(el * 1.7) + 0.1 * Math.sin(el * 5.3));

        sentRef.current = Math.min(tt, sentRef.current + v * delta);

        if (sentRef.current >= tt) {
          finRef.current = true;
          setFin(true);

          if (files.length > 0) {
            storageService.addRecentTransfer([
              files[0][0],
              `${files[0][1]} MB`,
              'أُرسل',
            ]);
          }
        }

        setSentMB(sentRef.current);
      }

      animId = requestAnimationFrame(tick);
    };

    animId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(animId);
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
      setTxText('');
      setTimeout(() => {
        window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
      }, 50);
    } else if (actionState === 'stop') {
      runRef.current = false;
      setRun(false);
    } else if (actionState === 'play') {
      runRef.current = true;
      setRun(true);
    }
  };

  const overallPercent = totalMB > 0 ? (sentMB / totalMB) * 100 : 0;

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
          onClick={() => navigate('home')}
        >
          <svg className="i">
            <use href="#x" />
          </svg>
        </button>
        <div className="g c">
          <i className="dot"></i>متصل · SmartTV
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
              ? 'اكتمل'
              : run
              ? `${sentMB.toFixed(1)} / ${totalMB.toFixed(1)} MB`
              : 'متوقف مؤقتًا'}
          </span>
        </div>

        <div className="h2">الملفات</div>

        <div className="card" id="L">
          {files.map((file, i) => {
            const fileName = file[0];
            const fileSize = file[1];
            const offset = fileOffsets[i];
            const q = Math.max(0, Math.min(1, (sentMB - offset) / fileSize));
            const isComplete = q >= 1;
            const pctDisplay = isComplete ? '✓' : `${Math.floor(q * 100)}%`;

            return (
              <div key={`${fileName}-${i}`} className="li">
                <span className="ic">{fileName.slice(0, 2)}</span>
                <div className="gr">
                  <b>{fileName}</b>
                  <div className="tr s">
                    <i style={{ width: `${q * 100}%` }}></i>
                  </div>
                </div>
                <small className={`st ${isComplete ? 'ok' : ''}`}>
                  {pctDisplay}
                </small>
              </div>
            );
          })}
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
