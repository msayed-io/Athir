# التقرير الفني المدقق والتشريحي لمشروع Athir | أثير (SHAREit)
## (Grounded Technical Audit, Source Evidence & Production Blueprint)

> **إقرار التدقيق والتطابق البرمجي المحدث (Updated Verification & Grounding Statement):**  
> تم فحص وتدقيق كل بند، دالة، مكون، ومسار مذكور في هذا المستند بالمقارنة المباشرة سطراً بسطر مع الكود التنفيذي الفعلي للمشروع (بما في ذلك التحديثات الأخيرة وإضافة شاشة الفحص بالكاميرا `ScanScreen` ومكتبة `glisse` وتكوينات `Capacitor`). جميع الأدلة المذكورة أدناه مستخرجة بنسبة 100% من الملفات التنفيذية الحالية، وتحدد بدقة بالغة ما هو مبرمج حالياً بالواجهة، وما تم ربطه مع واجهات المتصفح الحقيقية (مثل كاميرا الويب واستكشاف الباركود)، وما يحتاجه التطبيق ليتحول إلى تطبيق أندرويد أصيل يرسل ويستقبل الملفات عبر بطاقة الواي فاي المباشرة.

---

## 1. الملخص الفني والحالة الراهنة للمشروع (Executive Status)

* **اسم الهوية والتطبيق:** `Athir | أثير` (معرف التطبيق في Capacitor: `com.athir.datatransfer`).
* **طبيعة المشروع الحالي:** واجهة مستخدم تفاعلية متقدمة تجمع بين محاكاة بروتوكولات النقل والربط الحقيقي لكاميرا المتصفح ومسح الـ QR (`Interactive Client-Side Web Prototype with Real Web Camera Integration`).
* **إطار العمل والتقنيات الأساسية:**
  * `React 19.0.1` (`/package.json`)
  * `TypeScript 7.0.2` مع نمط صارم للأنواع (`/src/types/index.ts`)
  * `Vite 8.3.0` مع خادم تطوير على المنفذ `3000` واستضافة `0.0.0.0`
  * `Tailwind CSS 4.3.3` عبر `@tailwindcss/vite`
  * مكتبة واجهات الأجهزة والمكونات `glisse 0.5.0` (مستخدمة في شريط التبويبات `TabBar` وحقل البحث `SearchField`)
  * مكتبة الحركة `motion 12.23.24` وأيقونات `lucide-react 0.546.0`
  * أنماط زجاجية مخصصة داكنة (`/src/assets/styles/glass.css`, `base.css`, `variables.css`)
  * مكتبة أيقونات متجهة مدمجة تحتوي على 16 رمز SVG أصيل (`/src/assets/icons/SvgSprite.tsx`)
  * إعدادات الهجين أندرويد عبر Capacitor (`/capacitor.config.json`) مع تفعيل إذن الكاميرا داخل الأطر (`/metadata.json`).
* **الحقيقة الهندسية الحالية:**
  التطبيق يوفر تجربة مستخدم كاملة لـ **8 شاشات تفاعلية**. تم ربط **كاميرا الجهاز الحقيقية** عبر واجهة `navigator.mediaDevices.getUserMedia` واكتشاف الـ QR الحقيقي عبر API `BarcodeDetector` في شاشة `ScanScreen` الجديدة. أما على مستوى شبكة النقل، لا يتم فتح قنوات عتادية مباشرة (Wi-Fi Direct / Local Hotspot) بل تتم محاكاة سرعة النقل واكتشاف الرادار عبر المؤقتات والمعادلات الجيبية داخل React.

---

## 2. جدول الإثبات البرمجي: المحاكاة مقابل الواقع الفعلي (Code Evidence Matrix)

| الوظيفة في التطبيق | هل هي حقيقية؟ | الدليل القاطع من كود المشروع الحالي | ما ينقصها لتصبح حقيقية 100%؟ |
| :--- | :---: | :--- | :--- |
| **مسح الكاميرا ورمز الـ QR** | ⚡ **حقيقي جزئياً (Real Camera Stream)** | في `/src/pages/ScanScreen.tsx` (الأسطر 34-78)، يتم استدعاء كاميرا الجهاز الحقيقية عبر `navigator.mediaDevices.getUserMedia({ video: { facingMode: { exact: 'environment' } } })` وفحص الإطارات عبر `window.BarcodeDetector`. | ربط النتيجة المشفرة في الـ QR بآلية الاتصال التلقائي بشبكة الواي فاي أو نقطة الاتصال عبر جسر Capacitor Native Plugin. |
| **محرك نقل الملفات وحساب السرعة** | ❌ **محاكاة بحتة** | في `/src/pages/TransferScreen.tsx` (الأسطر 66-75)، السرعة ونسبة التقدم ناتجة عن معادلة جيبية رياضية تعمل بـ `requestAnimationFrame`: <br>`v = 6 + 2.5 * Math.sin(el * 1.7) + 1.2 * Math.sin(el * 5.3)` | بناء دفق بيانات حقيقي عبر **TCP Sockets** أو خادم **HTTP Stream (NanoHTTPD/Ktor)** أو **WebRTC DataChannels** ونقل بايتات الملفات الفعلية. |
| **اكتشاف الأجهزة والرادار** | ❌ **محاكاة ومؤقت زمن** | في `/src/pages/ReceiveScreen.tsx` (الأسطر 20-26) وفي `/src/pages/ScanScreen.tsx` (الأسطر 89-92)، يتم إظهار الجهاز الوهمي (`SmartTV`) بعد مؤقت 4.5 ثوانٍ في الاستلام و6 ثوانٍ في الفحص. | استخدام واجهات اكتشاف الأجهزة الحقيقية في أندرويد: `WifiP2pManager.discoverPeers` أو بروتوكول `NSD / mDNS` المحلي. |
| **أذونات أندرويد (الموقع وGPS)** | ❌ **محاكاة واجهة** | في `/src/pages/PrepareScreen.tsx` (الأسطر 15-16)، مفاتيح التفعيل هي متغيرات حالة محلية في رياكت: <br>`const [locationPermGranted, setLocationPermGranted] = useState(false)` | استدعاء واجهات نظام التشغيل الحقيقية عبر Capacitor Permissions لأذونات `NEARBY_WIFI_DEVICES` و `ACCESS_FINE_LOCATION`. |
| **قوائم وتصنيفات الملفات وتطبيقات الـ APK** | ❌ **بيانات وهمية** | في `/src/services/storageService.ts` (الأسطر 4-61)، الملفات عبارة عن مصفوفات نصوص وأرقام ثابتة في الكائن `CATEGORIZED_FILES` مثل: `['DeskLink LAN v2.4.apk', 46.9]`. | استخراج تطبيقات الهاتف الحقيقية عبر `PackageManager.getInstalledApplications` واستخدام `Storage Access Framework (SAF)` لقراءة الملفات. |
| **رمز QR في شاشة الاستلام** | ❌ **رسم SVG تفاعلي** | في `/src/pages/ReceiveScreen.tsx` (الأسطر 66-81)، الباركود عبارة عن رسمة SVG تحتوي عناصر `<rect>` مرسومة يدوياً ينقل لمرحلة النقل عند الضغط عليه. | استخدام مكتبة توليد باركود حقيقية لتشفير بيانات الاتصال الفعلية (SSID, Password, IP, Port). |
| **نقطة الاتصال والبلوتوث (Hotspot & BT)** | ❌ **عناصر واجهة وتوست** | في `/src/components/invite/ShareChannels.tsx` (الأسطر 19-35)، أزرار Bluetooth و Hotspot تستدعي دالة `handleAction` لعرض تنبيه نصي (Toast) أو استخدام `navigator.share`. | استدعاء واجهات `WifiManager.startLocalOnlyHotspot` و `BluetoothAdapter` عبر كود أندرويد أصيل. |
| **سجل العمليات والتاريخ** | ⚠️ **تخزين محلي للمحاكاة** | في `/src/services/storageService.ts` (الأسطر 106-130)، يتم حفظ وقراءة العمليات التي تمت محاكاتها داخل `localStorage.getItem('recent_transfers')`. | ربط السجل بالمسارات الحقيقية للملفات المستلمة على ذاكرة الهاتف وفتحها بواسطة مشغلات وتطبيقات النظام (Intents). |

---

## 3. التشريح المعماري الدقيق لملفات المشروع ومكوناته (Architectural Dissection)

### 3.1 طبقة التوجيه وإدارة دورة الحياة وسلوك اللمس (`/src/services/navigationService.tsx` & `/src/App.tsx`)
* **آلية التوجيه:** موجه مبني على تجزئة عنوان الرابط (Hash-based Routing) عبر `window.location.hash`.
* **المسارات المعرفة في النوع `ScreenRoute` (`/src/types/index.ts`):**  
  تتكون الشاشات الآن من **8 مسارات كاملة**:
  `'home' | 'send' | 'prepare' | 'receive' | 'transfer' | 'invite' | 'history' | 'scan'`
* **المزامنة مع زر الرجوع بنظام أندرويد:**
  الكود يستمع لحدث `hashchange` لتحديث حزمة التنقل `historyStack` وإضافة اتجاه الانتقال (`push`, `pop`, `tab`) مما يتيح التراجع السلس.
* **الحماية من إيماءات ومتصفح الويب (`/src/App.tsx` الأسطر 43-76):**
  يحتوي التطبيق في المكون الرئيسي على معالجات أحداث تمنع قائمة السياق عند الضغط المطول (`contextmenu`)، وتمنع تحديد النصوص (`selectstart`)، وتمنع سحب الصور والروابط (`dragstart`) لمنح التطبيق مظهر وإحساس تطبيق أندرويد الأصيل (Native App Behavior).
* **نقاء التبديل (Root Mount/Unmount Guarantee):**
  في `/src/App.tsx`، المكون `<ScreenRouter />` يستخدم المفتاح `key={currentScreen}`، مما يضمن تدمير الشاشة السابقة بالكامل من الـ DOM وإعادة بناء الشاشة الجديدة من الموضع `(0, 0)`.

### 3.2 طبقة البيانات والتخزين المؤقت (`/src/services/storageService.ts`)
* **التصنيفات المتاحة:** ستة تصنيفات كاملة (`apps`, `photos`, `audio`, `videos`, `contacts`, `files`).
* **البيانات المبدئية المثبتة:**
  * التطبيقات: 8 تطبيقات وهمية (تبدأ بـ `DeskLink LAN v2.4.apk` وتنتهي بـ `Maps Lite offline.apk`).
  * الصور: 7 ملفات افتراضية (بصيغ jpg, png, webp, heic, svg).
  * الصوتيات: 8 ملفات (mp3, m4a, flac, wav, opus, ogg, aac, wma).
  * الفيديوهات: 5 ملفات (mp4, mkv, mov, webm, avi).
  * جهات الاتصال: 6 ملفات vCard.
  * الملفات والمستندات: 10 ملفات مضغوطة ومستندات (zip, pdf, rar, docx, xlsx, pptx).
* **إدارة التخزين الفعلي للمتصفح:**
  * `sessionStorage`: يستخدم لحفظ سلة الملفات المحددة للإرسال عبر المفتاح `'sel'`.
  * `localStorage`: يستخدم لحفظ سجل العمليات عبر المفتاح `'recent_transfers'`.

### 3.3 المكونات البصرية والخلفيات المتقدمة (`/src/components/common/BackdropBlur.tsx`)
* **الخلفيات الزجاجية المتراتبة:** مكون `<BackdropBlur />` ينشئ 5 طبقات تدرج زجاجي مخصصة مع إغباش متضاعف (`blur(0.7px)` إلى `blur(11.2px)`).
* **التكيف الذكي مع شاشة الكاميرا (السطر 42):** تم برمجة `BackdropBlur` ليتجاهل إظهار الطبقة السفلى عندما تكون الشاشة الحالية هي `'scan'` (`currentScreen !== 'scan'`) لتزويد الكاميرا برؤية كاملة بدون أي عائق بصرى.

### 3.4 حزمة المكونات الخارجية وتكامل المكتبات (`glisse`)
* **شريط التبويبات المتقدم (`/src/components/common/BottomNav.tsx`):** يعتمد المكون على `TabBar` من مكتبة `glisse` لتوفير تنقل عصري وسلس بين الرئيسية والملفات والسجل.
* **حقل البحث الفوري (`/src/pages/SendScreen.tsx`):** يعتمد على `SearchField` من مكتبة `glisse` مع التفعيل التلقائي للتركيز (`autoFocus`) لفلترة الملفات والتطبيقات فورياً.

### 3.5 حزمة الأيقونات المدمجة (`/src/assets/icons/SvgSprite.tsx`)
يحتوي على 16 أيقونة SVG متجهة مخصصة بالكامل بدون استدعاء ملفات خارجية:
1. `bk`: سهم الرجوع للخلف.
2. `bell`: جرس التنبيهات.
3. `plus`: علامة الإضافة والدعوة.
4. `send`: أيقونة الإرسال (طائرة ورقية).
5. `dl`: أيقونة الاستلام والتنزيل.
6. `fold`: مجلد الملفات.
7. `srch`: عدسة البحث.
8. `ck`: علامة الاختيار والصح.
9. `pa`: إيقاف مؤقت (Pause).
10. `pl`: استئناف التشغيل (Play).
11. `x`: زر الإغلاق والإنهاء.
12. `qr`: رمز الاستجابة السريعة (QR).
13. `bt`: رمز البلوتوث.
14. `hs`: رمز نقطة الاتصال (Hotspot / Wi-Fi waves).
15. `hm`: رمز الصفحة الرئيسية.
16. `cl`: رمز سجل العمليات والوقت (Clock / History).

---

## 4. تدقيق الشاشات الثماني من واقع الكود الفعلي (Screen-by-Screen Code Audit)

### 4.1 شاشة البداية والرئيسية (`/src/pages/HomeScreen.tsx`)
* **المسار الفعلي:** `#/home`
* **المكونات والوظائف:**
  * الترويسة `HeaderTop`: تحتوي اسم التطبيق الرسمي `Athir` وزر فتح الدعوة `navigate('invite')`.
  * رادار البحث الرسومي: `<RadarVisual iconName="send" />` ينبض بحلقات دوارة وموجات ثلاثية.
  * زر الإرسال الرئيسي: ينقل فوراً لشاشة اختيار الملفات `navigate('send')`.
  * زر الاستلام الرئيسي: ينقل لشاشة تحضير الأذونات `navigate('prepare')`.
  * بطاقة الملفات: تنقل مباشرة لـ `navigate('send')`.
  * السجل السريع: يستدعي `storageService.getRecentTransfers()` ويعرض آخر العمليات المحفوظة.
  * الشريط السفلي: `<BottomNav activeTab="home" />` المبني بمكونات `glisse`.

### 4.2 شاشة اختيار الملفات وتأكيد الإرسال (`/src/pages/SendScreen.tsx`)
* **المسار الفعلي:** `#/send`
* **المكونات والوظائف:**
  * التبويبات الستة: `<CategoryTabs activeCategory={activeCategory} onChange={setActiveCategory} />`.
  * البحث الفوري: عند الضغط على أيقونة البحث يظهر حقل `SearchField` المدمج من `glisse` لفلترة القوائم لحظياً.
  * التحديد الافتراضي المبرمج: تحديد أول عنصرين من فئة التطبيقات تلقائياً (`DeskLink LAN v2.4.apk` و `Velo Messenger.apk`).
  * الشريط السفلي العائم `BottomBar`: يعرض عدد العناصر المحددة ومجموع أحجامها.
  * زر الإرسال: عند الضغط عليه يحفظ السلة المحددة في `sessionStorage` عبر `storageService.setStoredSelection(...)` وينقل المستخدم **مباشرة إلى شاشة فحص الباركود والكاميرا** `navigate('scan')`.

### 4.3 شاشة الفحص الحقيقي للاتصال والباركود (`/src/pages/ScanScreen.tsx`) - **الشاشة الجديدة**
* **المسار الفعلي:** `#/scan`
* **المكونات والوظائف:**
  * **الاتصال الحقيقي بالكاميرا (`startCamera`):** يستدعي `navigator.mediaDevices.getUserMedia` بطلب الكاميرا الخلفية (`facingMode: 'environment'`) ويعرض البث المباشر داخل عنصر `<video>` بعرض كامل وشفافية متدرجة.
  * **مستكشف الـ QR الفعلي:** يستخدم API `BarcodeDetector` للتحقق من وجود رموز QR داخل إطار الفيديو كل 400 مللي ثانية.
  * **إطار المستعرض الرسومي (`.fr` & `.vf`):** يعرض إطار زوايا المستعرض التفاعلي مع نص "ضع رمز QR داخل الإطار"، وعند الضغط عليه يعاد تشغيل الكاميرا.
  * **مؤقت الاكتشاف الاحتياطي (Auto Discovery Fallback):** في حال عدم مسح رمز باركود خلال 6 ثوانٍ (`setTimeout(found, 6000)`), تتفعل حالة `isFound = true` تلقائياً.
  * **بطاقة النتيجة والربط:** تظهر ورقة العمل السفلية (`.sheet`) اسم الجهاز المكتشف `SmartTV` مع زر "اتصال" رئيسي ينقل المستخدم فوراً لمرحلة النقل الحقيقي `navigate('transfer')`.

### 4.4 شاشة تحضير الأذونات والموقع (`/src/pages/PrepareScreen.tsx`)
* **المسار الفعلي:** `#/prepare`
* **المكونات والوظائف:**
  * مجسم زجاجي لهاتف ذكي ورسوم توضيحية لنظام أندرويد.
  * خياران رئيسيان:
    1. **منح إذن الموقع:** زر يتحول بين "فتح" و "مُفعل" عبر `setLocationPermGranted`.
    2. **تشغيل نظام تحديد المواقع العالمي (GPS):** زر يتحول بين "فتح" و "مُفعل" عبر `setGpsServicesGranted`.
  * التحقق المشروط: `isReady = locationPermGranted && gpsServicesGranted`. زر "التالي" يظل معطلاً بنسبة شفافية `opacity: 0.35` حتى يتم تفعيل الخيارين، ثم ينقل المستخدم إلى شاشة الرادار والاستلام `navigate('receive')`.

### 4.5 شاشة رادار الاستقبال والباركود (`/src/pages/ReceiveScreen.tsx`)
* **المسار الفعلي:** `#/receive`
* **المكونات والوظائف:**
  * رادار تفاعلي يحاكي التقاط الإشارات `<RadarVisual iconName="dl" />`.
  * مؤقت الاكتشاف: يستدعي `setTimeout` لمدة 4500 مللي ثانية للتغيير إلى حالة "تم العثور على SmartTV".
  * كود الـ QR البديل: زر "QR" يتيح إظهار بطاقة باركود SVG تفاعلية مدمجة مع نص "رمز QR جاهز للمسح"، والنقر عليها ينقل مباشرة لمرحلة النقل `navigate('transfer')`.
  * اسم الجهاز المعروض: معرف أثير الثابت `user79109045681`.

### 4.6 شاشة النقل الحي ومحاكي السرعة (`/src/pages/TransferScreen.tsx`)
* **المسار الفعلي:** `#/transfer`
* **المكونات والوظائف:**
  * استرجاع الملفات: تقرأ الملفات من `sessionStorage` (المحفوظة من شاشة الإرسال)، أو تستخدم القائمة الافتراضية عند الفراغ.
  * دورة المحاكاة الحسابية (`tick`): تستخدم `requestAnimationFrame` لحساب التقدم المنقول `sentRef.current` بالصيغة الجيبية:
    $$v = 6 + 2.5 \cdot \sin(\text{currentElapsed} \cdot 1.7) + 1.2 \cdot \sin(\text{currentElapsed} \cdot 5.3)$$
  * التحكم في النقل: زر إيقاف مؤقت / استئناف يعمل عبر التبديل بين أيقونتي `pl` و `pa`.
  * المكونات المعروضة:
    * `TransferSummary`: يعرض الميجابايت المنقول، الوقت المنقضي، شريط التقدم، وسرعة النقل الحالية (`MB/s`).
    * `TransferFileList`: يعرض الملفات وقضيب تقدم مستقل لكل ملف ونسبة مئوية أو علامة `✓`.
  * عند انتهاء النقل: يضيف الملفات تلقائياً إلى سجل العمليات المستمر `storageService.addRecentTransfer` مع إمكانية العودة للرئيسية عبر زر "تم".

### 4.7 شاشة الدعوة ومشاركة التطبيق (`/src/pages/InviteScreen.tsx`)
* **المسار الفعلي:** `#/invite`
* **المكونات والوظائف:**
  * الرادار العلوي `<RadarVisual iconName="send" />`.
  * مكون القنوات `<ShareChannels>` الذي يحتوي على أزرار Bluetooth، Hotspot، QR، WhatsApp، Facebook، و More.
  * معالج المشاركة: يستخدم `navigator.share` الحقيقي للمتصفح إذا كان مدعوماً، أو يظهر إشعار توست أنيق `toast-notification` يحمل اسم الطريقة المحددة.

### 4.8 شاشة سجل العمليات والتبادل (`/src/pages/HistoryScreen.tsx`)
* **المسار الفعلي:** `#/history`
* **المكونات والوظائف:**
  * قراءة السجل المخزن في `localStorage` وعرض عدد الملفات المسجلة.
  * زر **"مسح السجل"**: يستدعي `localStorage.removeItem('recent_transfers')` ويفرغ القائمة فوراً.
  * قائمة العناصر: عرض الحجم، الحالة ("أُرسل" أو "استُلم")، وعلامة التحقق الخضراء `✓`.

---

## 5. تحليل تكوين الهجين والموبايل (Capacitor & PWA Mobile Config)

### 5.1 إعدادات Capacitor (`/capacitor.config.json`)
```json
{
  "appId": "com.athir.datatransfer",
  "appName": "أثير",
  "webDir": "dist",
  "bundledWebRuntime": false
}
```
* **معرف التطبيق:** `com.athir.datatransfer` جاهز للتجميع والتصدير كـ APK لأجهزة الأندرويد.

### 5.2 إعدادات الأذونات والـ Manifest (`/metadata.json` & `/public/manifest.json`)
* **إذن الكاميرا:** تم تضمين `"requestFramePermissions": ["camera"]` في `metadata.json` لضمان عمل الكاميرا المباشرة داخل الـ iframe وتطبيق الويب بدون حظر أمني.
* **إعدادات الشاشة:** الشاشة مصممة بـ `viewport-fit=cover` و اتجاه `rtl` ونسق داكن كامل `#000000`.

---

## 6. التحليل الدقيق للفجوات التقنية (Comprehensive Technical Gap Analysis)

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        خريطة الفجوات الهندسية المحدثة                  │
├───────────────────────────────┬────────────────────────────────────────┤
│ 1. مسح الكاميرا والـ QR       │ ✅ تم الدمج حقيقياً عبر getUserMedia و BarcodeDetector │
│ 2. طبقة النظام والهجين        │ تم ضبط Capacitor Config وينقص حزمة Native Build │
│ 3. عتاد الاتصال اللاسلكي P2P   │ لا يوجد تفعيل لـ Local Hotspot أو Wi-Fi Direct  │
│ 4. بروتوكول نقل البايتات       │ لا توجد مقابس Sockets أو خوادم تدفق HTTP      │
│ 5. أذونات الـ Manifest        │ ينقص ربط أذونات أندرويد الحقيقية (الموقع/الأجهزة)│
│ 6. نظام الملفات واستخراج APK  │ لا يتم استعراض الذاكرة الحقيقية أو تطبيقات الجهاز│
└───────────────────────────────┴────────────────────────────────────────┘
```

---

## 7. الخطة الهندسية التنفيذية للتحويل الفعلي (Step-by-Step Implementation Plan)

إذا طُلب من الفريق الهندسي البدء في تحويل هذا المشروع إلى تطبيق حقيقي متكامل عبر Capacitor وأندرويد الأصيل:

### المرحلة الأولى: تجميع وتصدير حزمة أندرويد (Capacitor Android Packaging)
1. تثبيت حزم Capacitor وتوليد المجلد الأصلي:
   `npm install @capacitor/core @capacitor/cli @capacitor/android`
   `npx cap add android`
2. إضافة الأذونات المطلوبة في `android/app/src/main/AndroidManifest.xml`:
   * `android.permission.CAMERA`
   * `android.permission.ACCESS_FINE_LOCATION`
   * `android.permission.NEARBY_WIFI_DEVICES`
   * `android.permission.CHANGE_WIFI_STATE`

### المرحلة الثانية: بناء إضافة الاتصال المباشر (P2P Wi-Fi Native Plugin)
1. كتابة كود Kotlin المباشر لنقطة الاتصال `WifiDirectPlugin.kt`:
   ```kotlin
   val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
   wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
       override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
           val config = reservation.wifiConfiguration
           // تشفير بيانات SSID و Password إرسالها لـ QR Code
       }
   }, Handler(Looper.getMainLooper()))
   ```
2. ربط خادم النقل المدمج `NanoHTTPD` أو `ServerSocket` بنطاق المنفذ `8080`.

### المرحلة الثالثة: دمج منتقي الملفات الحقيقي
1. ربط `SendScreen.tsx` بمنتقي ملفات أندرويد عبر `Storage Access Framework (SAF)`.
2. استخراج قائمة التطبيقات المثبتة حقيقياً عبر `PackageManager.getInstalledApplications`.

---

## 8. توجيهات إلزامية لنماذج الذكاء الاصطناعي (Directives for AI Agents)

عند قيام أي نموذج ذكاء اصطناعي بقراءة هذا الكود والتطوير عليه:
1. **اعلم أن الكاميرا ومسح الـ QR في شاشة `ScanScreen` تعمل عبر واجهات المتصفح الحقيقية (`getUserMedia`)**، بينما محاكي نقل السرعة واكتشاف شبكة الواي فاي يستندان إلى محاكاة تفاعلية عالية الدقة.
2. **حافظ تماماً على الهوية البصرية الزجاجية والتصميم الداكن والتوجيه اليمين-إلى-اليسار (RTL)** المعتمد باسم `Athir | أثير`.
3. **التزم بالهيكلية الثمانية للشاشات** واستخدم المكونات المعتمدة من مكتبة `glisse` لضمان الاتساق البرمجي والتصميمي.
