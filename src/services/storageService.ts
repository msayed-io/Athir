import { CategoryType } from '../components/send/CategoryTabs';
import { FileTuple, RecentTuple } from '../types';

export const CATEGORIZED_FILES: Record<CategoryType, FileTuple[]> = {
  apps: [
    ['Athir Data Transfer v2.4.apk', 46.9],
    ['Velo Messenger.apk', 10.4],
    ['Files Pro Admin.apk', 22.1],
    ['Notes Sync.apk', 8.2],
    ['Clip Studio.apk', 3.4],
    ['Terminal Tools.apk', 31.5],
    ['Radio FM Player.apk', 12.8],
    ['Maps Lite offline.apk', 54.0],
  ],
  photos: [
    ['IMG_2026_sunset.jpg', 4.2],
    ['Photo_Portfolio.png', 8.6],
    ['Design_Mockup_v3.webp', 2.1],
    ['Family_Vacation_01.heic', 5.7],
    ['Logo_Vector_HD.svg', 0.8],
    ['Screenshot_20261102.png', 1.9],
    ['Banner_Graphic_4K.jpg', 12.3],
  ],
  audio: [
    ['Voice_Note_001.m4a', 2.5],
    ['Lecture_Recording.mp3', 18.4],
    ['Audiobook_Chapter1.flac', 45.2],
    ['Podcast_Episode_42.wav', 68.0],
    ['Call_Recording_2026.opus', 1.8],
    ['Ambient_Soundscape.ogg', 12.1],
    ['SoundTrack_Lossless.aac', 24.6],
    ['HQ_Audio_Record.wma', 9.3],
  ],
  videos: [
    ['Presentation_HD.mp4', 120.5],
    ['Family_Video_4K.mkv', 450.0],
    ['Screen_Recording.mov', 85.2],
    ['Tutorial_Guide.webm', 64.1],
    ['Clip_Short.avi', 32.8],
  ],
  contacts: [
    ['أحمد محمود - VCF.vcf', 0.1],
    ['سارة خالد - VCF.vcf', 0.1],
    ['د. محمد علي - VCF.vcf', 0.2],
    ['جهات اتصال العائلة.vcf', 1.2],
    ['فريق العمل (24 جهة).vcf', 0.8],
    ['تصدير جهات الاتصال 2026.vcf', 2.4],
  ],
  files: [
    ['مشروع_التصميم_النهائي.zip', 84.5],
    ['تقرير_المبيعات_2026.pdf', 12.3],
    ['قاعدة_البيانات_الاحتياطية.rar', 156.0],
    ['تطبيق_النظام_التحديث.apk', 38.2],
    ['جدول_البيانات_المالية.xlsx', 4.1],
    ['مستند_العمليات_الشركة.docx', 2.8],
    ['حزمة_الخطوط_والأيقونات.7z', 29.4],
    ['عرض_التقديم_المؤسسي.pptx', 19.6],
    ['ملاحظات_النظام.txt', 0.2],
    ['أرشيف_الملفات_المستلمة.tar.gz', 210.0],
  ],
};

export const INITIAL_FILES_DATA: FileTuple[] = CATEGORIZED_FILES.apps;


export const INITIAL_RECENT_DATA: RecentTuple[] = [
  ['Athir App', '46.9 MB', 'أُرسل'],
  ['Velo', '10.4 MB', 'أُرسل'],
  ['Files Pro', '22.1 MB', 'استُلم'],
  ['Notes', '8.2 MB', 'استُلم'],
  ['Clip', '3.4 MB', 'أُرسل'],
  ['Terminal', '31.5 MB', 'استُلم'],
];

export const storageService = {
  getStoredSelection(): FileTuple[] | null {
    try {
      const raw = sessionStorage.getItem('sel');
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed as FileTuple[];
      }
    } catch {
      // Ignore sessionStorage errors
    }
    return null;
  },

  setStoredSelection(items: FileTuple[]): void {
    try {
      sessionStorage.setItem('sel', JSON.stringify(items));
    } catch {
      // Ignore sessionStorage errors
    }
  },

  clearStoredSelection(): void {
    try {
      sessionStorage.removeItem('sel');
    } catch {
      // Ignore
    }
  },

  getRecentTransfers(): RecentTuple[] {
    try {
      const raw = localStorage.getItem('recent_transfers');
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed as RecentTuple[];
        }
      }
    } catch {
      // Fallback
    }
    return INITIAL_RECENT_DATA;
  },

  addRecentTransfer(item: RecentTuple): void {
    try {
      const list = this.getRecentTransfers();
      const updated = [item, ...list.filter((x) => x[0] !== item[0])].slice(0, 10);
      localStorage.setItem('recent_transfers', JSON.stringify(updated));
    } catch {
      // Fallback
    }
  },
};
