# التقرير الفني المدقق والتشريحي لمشروع SHAREit
## (Grounded Technical Audit, Source Evidence & Production Blueprint)

> **إقرار التدقيق والتطابق البرمجي (Verification & Grounding Statement):**  
> تم فحص وتدقيق كل بند، دالة، متغير، ومسار مذكور في هذا المستند بالمقارنة المباشرة سطراً بسطر مع كود المشروع الفعلي. جميع الأدلة المذكورة أدناه مستخرجة بنسبة 100% من الملفات التنفيذية الحالية للمشروع، وتحدد بدقة بالغة ما هو مبرمج حالياً كمحاكاة مظهرية وما يحتاجه التطبيق ليتحول إلى تطبيق أندرويد حقيقي يرسل ويستقبل الملفات فعلياً.

---

## 1. الملخص الفني والحالة الراهنة للمشروع (Executive Status)

* **طبيعة المشروع الحالي:** واجهة مستخدم تفاعلية عالية الدقة والسرعة (Interactive Client-Side Web Prototype).
* **إطار العمل والتقنيات الأساسية:**
  * `React 19.0.1` (`/package.json`)
  * `TypeScript 7.0.2` مع نمط صارم للأنواع (`/src/types/index.ts`)
  * `Vite 8.3.0` مع خادم تطوير على المنفذ `3000` واستضافة `0.0.0.0`
  * `Tailwind CSS 4.3.3` عبر `@tailwindcss/vite`
  * أنماط زجاجية مخصصة داكنة (`/src/assets/styles/glass.css`, `base.css`, `variables.css`)
  * مكتبة أيقونات متجهة مدمجة تحتوي على 16 رمز SVG أصيل (`/src/assets/icons/SvgSprite.tsx`)
* **الحقيقة الهندسية الحالية:**
  التطبيق يعمل **بالمحاكاة الكاملة بنسبة 100% (100% Client-Side Simulation)**. لا يتم فتح أي قنوات اتصال عتادية (Wi-Fi Direct / Hotspot)، ولا تجري أي عمليات اتصال بمقابس شبكة حقيقية (Sockets)، ولا يتم طلب أي إذن حقيقي من نظام أندرويد، ولا يتم الوصول لملفات الهاتف الحقيقية، بل تتم محاكاة كافة الخطوات عبر كود React والحالات المحلية والمؤقتات الزمنية.

---

## 2. جدول الإثبات البرمجي: المحاكاة مقابل الواقع الفعلي (Code Evidence Matrix)

| الوظيفة في التطبيق | هل هي حقيقية؟ | الدليل القاطع من كود المشروع الحالي | ما ينقصها لتصبح حقيقية 100%؟ |
| :--- | :---: | :--- | :--- |
| **محرك نقل الملفات وحساب السرعة** | ❌ **محاكاة بحتة** | في `/src/pages/TransferScreen.tsx` (الأسطر 66-75)، السرعة ونسبة التقدم ناتجة عن معادلة جيبية رياضية تعمل بـ `requestAnimationFrame`: <br>`v = 6 + 2.5 * Math.sin(el * 1.7) + 1.2 * Math.sin(el * 5.3)` | بناء دفق بيانات حقيقي عبر **TCP Sockets** أو خادم **HTTP Stream (NanoHTTPD/Ktor)** أو **WebRTC DataChannels** ونقل بايتات الملفات الفعلية. |
| **اكتشاف الأجهزة والرادار** | ❌ **محاكاة بحتة** | في `/src/pages/ReceiveScreen.tsx` (الأسطر 20-26)، يتم اكتشاف الجهاز الوهمي (`SmartTV`) بعد انتهاء مؤقت زمني مدته 4.5 ثوانٍ فقط: <br>`setTimeout(() => setIsDiscovered(true), 4500)` | استخدام واجهات اكتشاف الأجهزة الحقيقية في أندرويد: `WifiP2pManager.discoverPeers` أو بروتوكول `NSD / mDNS` المحلي. |
| **أذونات أندرويد (الموقع وGPS)** | ❌ **محاكاة بحتة** | في `/src/pages/PrepareScreen.tsx` (الأسطر 15-16)، مفاتيح التفعيل هي مجرد متغيرات حالة محلية في رياكت: <br>`const [locationPermGranted, setLocationPermGranted] = useState(false)` | استدعاء واجهات نظام التشغيل الحقيقية: `ActivityCompat.requestPermissions` لأذونات `NEARBY_WIFI_DEVICES` و `ACCESS_FINE_LOCATION`. |
| **قوائم وتصنيفات الملفات وتطبيقات الـ APK** | ❌ **بيانات وهمية** | في `/src/services/storageService.ts` (الأسطر 4-61)، الملفات عبارة عن مصفوفات نصوص وأرقام ثابتة في الكائن `CATEGORIZED_FILES` مثل: `['DeskLink LAN v2.4.apk', 46.9]`. | استخراج تطبيقات الهاتف الحقيقية عبر `PackageManager.getInstalledApplications` واستخدام `Storage Access Framework (SAF)` لقراءة الملفات. |
| **رمز الاستجابة السريعة (QR Code)** | ❌ **رسم SVG ثابت** | في `/src/pages/ReceiveScreen.tsx` (الأسطر 66-81)، الباركود عبارة عن رسمة SVG تحتوي عناصر `<rect>` مرسومة يدوياً ولا تحتوي أي كاميرا أو بيانات مشفرة. | استخدام مكتبة توليد باركود حقيقية لتشفير بيانات الاتصال (SSID, Password, IP, Port) ومكتبة كاميرا حقيقية (مثل ZXing أو ML Kit). |
| **نقطة الاتصال والبلوتوث (Hotspot & BT)** | ❌ **عناصر واجهة فقط** | في `/src/components/invite/ShareChannels.tsx` (الأسطر 19-35)، أزرار Bluetooth و Hotspot تستدعي فقط دالة `handleAction` لعرض تنبيه نصي (Toast). | استدعاء واجهات `WifiManager.startLocalOnlyHotspot` و `BluetoothAdapter` عبر كود أندرويد أصيل (Capacitor/Native Bridge). |
| **سجل العمليات والتاريخ** | ⚠️ **تخزين محلي للمحاكاة** | في `/src/services/storageService.ts` (الأسطر 106-130)، يتم حفظ وقراءة العمليات التي تمت محاكاتها داخل `localStorage.getItem('recent_transfers')`. | ربط السجل بالمسارات الحقيقية للملفات المستلمة على ذاكرة الهاتف وفتحها بواسطة مشغلات وتطبيقات النظام (Intents). |

---

## 3. التشريح المعماري الدقيق لملفات المشروع ومكوناته (Architectural Dissection)

### 3.1 طبقة التوجيه وإدارة دورة الحياة (`/src/services/navigationService.tsx`)
* **آلية التوجيه:** موجه مبني على تجزئة عنوان الرابط (Hash-based Routing) عبر `window.location.hash`.
* **المسارات المعرفة في النوع `ScreenRoute` (`/src/types/index.ts`):**
  `'home' | 'send' | 'prepare' | 'receive' | 'transfer' | 'invite' | 'history'`
* **المزامنة مع زر الرجوع بنظام أندرويد:**
  الكود يستمع لحدث `hashchange` لتحديث حزمة التنقل `historyStack`، مما يتيح للمستخدم الرجوع بسلاسة عند الضغط على زر الرجوع في الهاتف أو المتصفح.
* **نقاء التبديل (Root Mount/Unmount Guarantee):**
  في `/src/App.tsx`، المكون `<ScreenRouter />` يستخدم المفتاح `key={currentScreen}`، مما يضمن تدمير الشاشة السابقة بالكامل من الـ DOM وإعادة بناء الشاشة الجديدة من الموضع `(0, 0)` لمنع تراكم الذاكرة وتضارب التمرير.

### 3.2 طبقة البيانات والتخزين المؤقت (`/src/services/storageService.ts`)
* **التصنيفات المتاحة:** ستة تصنيفات كاملة (`apps`, `photos`, `audio`, `videos`, `contacts`, `files`).
* **البيانات المبدئية المثبتة:**
  * التطبيقات: 8 تطبيقات وهمية (تبدأ بـ `DeskLink LAN v2.4.apk` وتنتهي بـ `Maps Lite offline.apk`).
  * الصور: 7 ملفات افتراضية (بصيغ jpg, png, webp, heic, svg).
  * الصوتيات: 8 ملفات (mp3, m4a, flac, wav, opus, ogg, aac, wma).
  * الفيديوهات: 5 ملفات (mp4, mkv, mov, webm, avi).
  * جهات الاتصال: 6 ملفات vCard (مثل `جهات اتصال العائلة.vcf`).
  * الملفات والمستندات: 10 ملفات مضغوطة ومستندات (zip, pdf, rar, docx, xlsx, pptx).
* **إدارة التخزين الفعلي للمتصفح:**
  * `sessionStorage`: يستخدم لحفظ سلة الملفات المحددة للإرسال عبر المفتاح `'sel'`.
  * `localStorage`: يستخدم لحفظ سجل العمليات عبر المفتاح `'recent_transfers'`.

### 3.3 حزمة الأيقونات المدمجة (`/src/assets/icons/SvgSprite.tsx`)
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

## 4. تدقيق الشاشات السبع من واقع الكود الفعلي (Screen-by-Screen Code Audit)

### 4.1 شاشة البداية والرئيسية (`/src/pages/HomeScreen.tsx`)
* **المسار الفعلي:** `#/home`
* **المكونات والوظائف:**
  * الترويسة `HeaderTop`: تحتوي اسم التطبيق ونقطة الحالة الخضراء `<i className="dot" />` وزر فتح الدعوة `navigate('invite')`.
  * رادار البحث الرسومي: `<RadarVisual iconName="send" />` ينبض بحلقات دوارة وموجات ثلاثية.
  * زر الإرسال الرئيسي: فئة `g c p big` ينقل لـ `navigate('send')`.
  * زر الاستلام الرئيسي: فئة `g c big` ينقل لـ `navigate('prepare')`.
  * بطاقة الملفات: تنقل مباشرة لـ `navigate('send')`.
  * السجل السريع: يستدعي `storageService.getRecentTransfers()` ويعرض آخر 6 عناصر.
  * الشريط السفلي: `<BottomNav activeTab="home" />`.

### 4.2 شاشة اختيار الملفات وتأكيد الإرسال (`/src/pages/SendScreen.tsx`)
* **المسار الفعلي:** `#/send`
* **المكونات والوظائف:**
  * التبويبات الستة: `<CategoryTabs activeCategory={activeCategory} onChange={setActiveCategory} />`.
  * البحث الفوري: حقل إدخال يظهر عند الضغط على أيقونة البحث ويفلتر بالاسم لحظياً.
  * التحديد الافتراضي المبرمج: السطر 29-33 يقوم بتحديد أول عنصرين من فئة التطبيقات تلقائياً (`DeskLink LAN v2.4.apk` و `Velo Messenger.apk`).
  * الشريط السفلي العائم `BottomBar`: يعرض عدد العناصر المحددة ومجموع أحجامها `totalSelectedMB.toFixed(1) MB`.
  * زر الإرسال: لا يمكن الضغط عليه إذا كانت السلة فارغة؛ وعند الضغط عليه يحفظ السلة في `sessionStorage` عبر `storageService.setStoredSelection(...)` وينقل المستخدم فوراً إلى `navigate('transfer')`.

### 4.3 شاشة تحضير الأذونات والموقع (`/src/pages/PrepareScreen.tsx`)
* **المسار الفعلي:** `#/prepare`
* **المكونات والوظائف:**
  * مجسم زجاجي لهاتف ذكي ورسوم توضيحية لنظام أندرويد.
  * خياران رئيسيان:
    1. **منح إذن الموقع:** زر يتحول بين "فتح" و "مُفعل" عبر `setLocationPermGranted`.
    2. **تشغيل نظام تحديد المواقع العالمي (GPS):** زر يتحول بين "فتح" و "مُفعل" عبر `setGpsServicesGranted`.
  * التحقق المشروط: المتغير `isReady = locationPermGranted && gpsServicesGranted`. زر "التالي" في الشريط السفلي يظل معطلاً بنسبة شفافية `opacity: 0.35` حتى يتم تفعيل الخيارين معاً، ثم ينقل المستخدم إلى `navigate('receive')`.

### 4.4 شاشة رادار الاستقبال والباركود (`/src/pages/ReceiveScreen.tsx`)
* **المسار الفعلي:** `#/receive`
* **المكونات والوظائف:**
  * رادار تفاعلي يحاكي التقاط الإشارات `<RadarVisual iconName="dl" />`.
  * مؤقت الاكتشاف المبرمج: السطر 20 يشغل `setTimeout` لمدة 4500 مللي ثانية، وعند اكتمالها يتغير النص من "في انتظار المرسل" إلى "تم العثور على SmartTV" ويتغير زر الإجراء من "QR" إلى "اتصال".
  * كود الـ QR البديل: زر "QR" يتيح إظهار بطاقة باركود SVG مدمجة مع نص "رمز QR جاهز للمسح"، والنقر عليها ينقل مباشرة لمرحلة النقل `navigate('transfer')`.
  * اسم الجهاز المعروض في الواجهة: معرف ثابت `user79109045681`.

### 4.5 شاشة النقل الحي ومحاكي السرعة (`/src/pages/TransferScreen.tsx`)
* **المسار الفعلي:** `#/transfer`
* **المكونات والوظائف:**
  * استرجاع الملفات: تقرأ الملفات من `sessionStorage`، وإذا كانت فارغة تستخدم مصفوفة احتياطية `DEFAULT_TRANSFER_FILES` (مكونة من: DeskLink LAN 46.9MB, Velo 10.4MB, Files Pro 22.1MB).
  * دورة المحاكاة الحسابية (`tick`): تستخدم `requestAnimationFrame` لحساب التقدم المنقول `sentRef.current` بالصيغة الجيبية:
    $$v = 6 + 2.5 \cdot \sin(\text{currentElapsed} \cdot 1.7) + 1.2 \cdot \sin(\text{currentElapsed} \cdot 5.3)$$
  * التحكم في النقل: زر إيقاف مؤقت / استئناف يعمل عبر التبديل بين أيقونتي `pl` و `pa`.
  * المكونات المعروضة:
    * `TransferSummary`: يعرض الميجابايت المنقول، الوقت المنقضي بدقائق وثوانٍ، شريط التقدم، وسرعة النقل الحالية (مثلاً `7.8 MB/s`).
    * `TransferFileList`: يعرض الملفات وقضيب تقدم مستقل لكل ملف ونسبة مئوية أو علامة `✓`.
  * عند انتهاء النقل: السطر 83 يضيف الملف المنقول تلقائياً إلى سجل العمليات المستمر `storageService.addRecentTransfer`، وتتحول الواجهة إلى حالة "اكتمل الإرسال" مع زر "تم" الذي يعيد المستخدم للشاشة الرئيسية.

### 4.6 شاشة الدعوة ومشاركة التطبيق (`/src/pages/InviteScreen.tsx`)
* **المسار الفعلي:** `#/invite`
* **المكونات والوظائف:**
  * الرادار العلوي `<RadarVisual iconName="send" />`.
  * مكون القنوات `<ShareChannels>` الذي يحتوي على:
    * زر كبير للبلوتوث (`Bluetooth`).
    * زر كبير لنقطة الاتصال (`Hotspot`).
    * أزرار إضافية لـ (`QR`, `WhatsApp`, `Facebook`, `More`).
  * معالج الإجراء `handleSelectMethod`: إذا كان المتصفح يدعم `navigator.share` (كما في متصفحات الجوال الحديثة)، يتم فتح نافذة المشاركة الحقيقية للنظام، وفي حال عدم دعمها يظهر إشعار توست أنيق `toast-notification` يحمل النص: `"تم تفعيل خيار مشاركة: [اسم الطريقة]"`.

### 4.7 شاشة سجل العمليات والتبادل (`/src/pages/HistoryScreen.tsx`)
* **المسار الفعلي:** `#/history`
* **المكونات والوظائف:**
  * قراءة السجل المخزن في `localStorage` وعرض عدد الملفات المسجلة.
  * زر **"مسح السجل"**: يستدعي `localStorage.removeItem('recent_transfers')` ويفرغ القائمة في الواجهة فوراً.
  * حالة الفراغ: إذا كان السجل فارغاً يعرض بطاقة: "لا توجد عمليات نقل سابقة".
  * قائمة العناصر: كل عنصر يعرض حرفين كأيقونة مختصرة، اسم الملف، حجمه، حالته ("أُرسل" أو "استُلم")، وعلامة التحقق الخضراء `✓`.

---

## 5. التحليل الدقيق للفجوات التقنية (Comprehensive Technical Gap Analysis)

لكي يفهم أي مهندس برمجيات أو نموذج ذكاء اصطناعي ما ينقص هذا التطبيق تحديداً للعمل كبرنامج SHAREit حقيقي بدون أي محاكاة، هذه هي الفجوات الحقيقية بالتفصيل:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        خريطة الفجوات الهندسية الكاملة                  │
├───────────────────────────────┬────────────────────────────────────────┤
│ 1. طبقة النواة ونظام التشغيل  │ لا يوجد كود أصيل لأندرويد (لا Java ولا Kotlin) │
│ 2. عتاد الاتصال اللاسلكي P2P   │ لا يوجد تفعيل لـ Local Hotspot أو Wi-Fi Direct  │
│ 3. بروتوكول نقل البايتات       │ لا توجد مقابس Sockets أو خوادم تدفق HTTP      │
│ 4. صلاحيات الـ Manifest       │ لا توجد أذونات أندرويد الحقيقية (الموقع/الأجهزة)│
│ 5. نظام الملفات واستخراج APK  │ لا يتم استعراض الذاكرة الحقيقية أو تطبيقات الجهاز│
│ 6. الباركود ومسح الكاميرا     │ لا يوجد ماسح حقيقي بالكاميرا (Camera API)     │
└───────────────────────────────┴────────────────────────────────────────┘
```

### 1. فجوة بيئة التشغيل والحزم (Native Platform Wrapper Gap)
* **المشكلة الحالية:** المشروع تطبيق ويب يعمل داخل قيود المتصفح (Web Browser Sandbox)، والمتصفح محظور أمنياً من التحكم في بطاقة الواي فاي والبلوتوث.
* **المطلوب:** تحويل المشروع إلى تطبيق هجين عبر **Capacitor** أو **React Native** أو إعادة بناء المحرك بنظام **Android Native (Kotlin)**، مع ربط الواجهة الحالية عبر جسر إضافات أصيل (Custom Native Plugin).

### 2. فجوة الاتصال العتادي المباشر (P2P Wi-Fi Direct & Hotspot Gap)
* **المشكلة الحالية:** يتم تمثيل الاتصال بنصوص ومؤقتات زمنية (`setTimeout(..., 4500)`).
* **المطلوب:**
  * **طريقة نقطة الاتصال المحلية (Local-Only Hotspot) - الطريقة المعتمدة لـ SHAREit الحديث:**
    استدعاء دالة نظام أندرويد في جهاز المستلم:
    ```kotlin
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
        override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
            val config = reservation.wifiConfiguration // اسم الشبكة SSID وكلمة المرور
            val ssid = config.SSID
            val preSharedKey = config.preSharedKey
            // إرسال هذه البيانات لتوليد رمز QR حقيقي
        }
    }, Handler(Looper.getMainLooper()))
    ```
  * **طريقة Wi-Fi Direct (P2P):**
    استخدام `WifiP2pManager` واستدعاء `discoverPeers()` و `createGroup()`.

### 3. فجوة مقابس وخوادم نقل الملفات (Byte Streaming Engine Gap)
* **المشكلة الحالية:** النقل عبارة عن عداد أرقام مبني بدالة رياضية (`v = 6 + 2.5 * sin(...)`).
* **المطلوب:**
  * تشغيل خادم مدمج خفيف الوزن (مثل `NanoHTTPD` أو `Ktor` أو `Raw ServerSocket`) على جهاز المستلم على المنفذ `8080`.
  * يقوم المرسل بعمل طلب دفق بايتات متعدد الأجزاء (`POST /transfer/upload`) يحتوي اسم وحجم وبايتات الملف الحقيقي.
  * يقوم المستلم بقراءة مجرى البيانات (`InputStream`) وحفظه في مجلد التنزيلات على الهاتف:
    ```kotlin
    val fos = FileOutputStream(File(downloadsDir, fileName))
    val buffer = ByteArray(64 * 1024) // حزم بحجم 64 كيلوبايت
    var bytesRead: Int
    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        fos.write(buffer, 0, bytesRead)
        totalBytesReceived += bytesRead
        // إرسال النسبة الحقيقية للواجهة عبر الـ Bridge
    }
    ```

### 4. فجوة أذونات أندرويد الصريحة (Android Runtime Permissions)
* **المشكلة الحالية:** المفاتيح في شاشة `PrepareScreen.tsx` تغير متغيرات محلية في الذاكرة دون طلب إذن فعلي.
* **المطلوب:** طلب الأذونات الصريحة في ملف `AndroidManifest.xml` ومعالجتها وقت التشغيل:
  * `android.permission.NEARBY_WIFI_DEVICES` (إلزامي لنظام أندرويد 13 فما فوق للاتصال المباشر).
  * `android.permission.ACCESS_FINE_LOCATION` (إلزامي للبحث عن شبكات الواي فاي).
  * `android.permission.CHANGE_WIFI_STATE` و `ACCESS_WIFI_STATE`.
  * `android.permission.FOREGROUND_SERVICE` و `FOREGROUND_SERVICE_DATA_SYNC` (لضمان استمرار النقل حتى لو قفل المستخدم شاشة الهاتف أو خرج من التطبيق).
  * `android.permission.CAMERA` (لمسح رمز الاستجابة السريعة).

### 5. فجوة قراءة ملفات الهاتف وتطبيقات الـ APK (Storage Access & App Extraction)
* **المشكلة الحالية:** الملفات معرّفة كنصوص ثابتة في كود الـ JavaScript.
* **المطلوب:**
  * استخراج تطبيقات أندرويد المثبتة الحقيقية عبر:
    `packageManager.getInstalledApplications(PackageManager.GET_META_DATA)` وقراءة مسار الحزمة الحقيقي `appInfo.sourceDir` لتحويله إلى ملف قابل للنقل.
  * ربط منتقي الملفات الحقيقي بنظام أندرويد عبر `Storage Access Framework (SAF)` أو منتقي المتصفح المتعدد `<input type="file" multiple />`.

### 6. فجوة توليد ومسح رمز الباركود الحقيقي (Real QR Code & Camera Scanner)
* **المشكلة الحالية:** كود الـ QR الحالي في `ReceiveScreen.tsx` هو مستطيلات SVG صامتة.
* **المطلوب:**
  * **توليد الكود:** دمج مكتبة مثل `qrcode` لتحويل بيانات نقطة الاتصال وعنوان IP والمنفذ إلى باركود حقيقي ديناميكي.
  * **مسح الكود:** دمج واجهة كاميرا حقيقية (مثل `html5-qrcode` على الويب، أو `@capacitor-community/barcode-scanner` أو `Google ML Kit Barcode Scanning` على أندرويد) لقراءة البيانات والاتصال التلقائي بالشبكة.

---

## 6. الخطة الهندسية التنفيذية للتحويل الفعلي (Step-by-Step Implementation Plan)

إذا طُلب من الذكاء الاصطناعي أو الفريق الهندسي البدء في تحويل هذا المشروع إلى تطبيق حقيقي، يتم تنفيذ الخطوات وفق هذا الترتيب الصارم:

### المرحلة الأولى: تغليف المشروع عبر Capacitor (Native App Packaging)
1. تثبيت الحزم المطلوبة:
   `npm install @capacitor/core @capacitor/cli @capacitor/android`
2. تهيئة المنصة:
   `npx cap init "SHAREit" "com.shareit.localtransfer" --web-dir "dist"`
3. إضافة منصة أندرويد:
   `npx cap add android`

### المرحلة الثانية: بناء إضافة الاتصال اللاسلكي المباشر (P2P Hotspot Plugin)
1. كتابة كود Kotlin داخل المجلد `android/app/src/main/java/com/shareit/localtransfer/WifiDirectPlugin.kt`.
2. ربط الدوال التالية مع واجهة React:
   * `startReceiverHotspot()`: تشغل نقطة الاتصال وتعيد (SSID, Password, Host IP, Port).
   * `connectToSender(ssid, password)`: تقوم بربط هاتف المرسل بنقطة اتصال المستلم تلقائياً.
   * `startFileServer()`: تشغل خادم استقبال الملفات.
   * `sendFile(filePath, targetIp, targetPort)`: تقرأ الملف وترسله كدفق بايتات حقيقي.

### المرحلة الثالثة: دمج الكاميرا ومولد الباركود الحقيقي
1. تثبيت حزمة الباركود:
   `npm install qrcode @types/qrcode`
2. في شاشة `ReceiveScreen.tsx`: استبدال رسمة الـ SVG الثابتة بمكون يولد صورة QR حقيقية تحمل بيانات الاتصال الفعلية.
3. في شاشة `SendScreen.tsx`: إضافة زر "مسح QR للاتصال" يفتح الكاميرا عبر واجهة النظام أو مكتبة المسح.

### المرحلة الرابعة: ربط نظام الملفات الحقيقي
1. في `SendScreen.tsx`: دمج منتقي الملفات الحقيقي وقراءة أحجامها الفعلية.
2. في `PrepareScreen.tsx`: ربط المفاتيح بطلب الأذونات الحقيقي عبر `Capacitor.Permissions.requestPermission`.

---

## 7. توجيهات إلزامية لنماذج الذكاء الاصطناعي (Directives for AI Agents)

عند قيام أي نموذج ذكاء اصطناعي بقراءة هذا الكود والتطوير عليه:
1. **لا تدعي إطلاقاً أن ميزات النقل أو الواي فاي أو البلوتوث الحالية حقيقية**؛ فهي محاكاة مرئية واضحة ومثبتة في الكود (Simulated Prototype).
2. **حافظ تماماً على التصميم الزجاجي والهوية البصرية الحالية**؛ فالواجهة الحالية متطابقة مع أرقى معايير تصميم SHAREit الكلاسيكية الداكنة.
3. **ابدأ التطوير الفعلي بتنفيذ إحدى خطوات "المرحلة الأولى والثانية"** في خارطة الطريق الموضحة أعلاه، واستخدم المعايير الفنية المثبتة في هذا الدليل.
