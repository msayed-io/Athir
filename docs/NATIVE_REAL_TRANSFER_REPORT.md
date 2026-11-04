# تقرير التحويل إلى نظام نقل حقيقي — Athir | أثير

> تاريخ التنفيذ: 2026-11-04
> الفرع: `feat/native-real-transfer` → مدموج إلى `main`
> نقطة الاسترجاع: وسم `pre-native-conversion-backup` (مدفوع إلى GitHub)
> معرّف الحزمة: `com.athir.datatransfer` · النسخة قبل العمل: `1.0.1` / `versionCode 2`

---

## 0. ملاحظة حاكمة: ما كان موجودًا فعلًا قبل هذه المهمة

المرجع الملزم هو حالة الكود، وليس أي تقرير قديم. عند قراءة المستودع سطرًا بسطر تبيّن أن **المرحلة الأصلية كانت منجزة بالفعل** قبل هذه المهمة:

| المكوّن | الحالة قبل العمل | الدليل |
|---|---|---|
| إضافة Capacitor أصلية `AthirNative` | موجودة ومسجّلة | `android/.../AthirNativePlugin.kt` (519 سطرًا)، `MainActivity.java` |
| محرك نقل بايتات حقيقي فوق TLS | موجود | `TransferEngine.kt` (465 سطرًا) + بروتوكول ATHR |
| ماسح ML Kit المدمج + CameraX | موجود | `ScannerEngine.kt`، `barcode-scanning:17.3.0`، `camera-*:1.3.4` |
| QR حقيقي (ZXing) | موجود | `QrEncoder.kt` |
| اكتشاف mDNS | موجود | `DiscoveryManager.kt` |
| كتالوج ملفات حقيقي | موجود | `MediaCatalog.kt` |
| سجل SQLite | موجود | `HistoryStore.kt` |
| خدمة نقل أمامية | موجودة | `TransferService.kt` |
| هوية جهاز حقيقية | موجودة | `IdentityStore.kt` |
| محاكاة في JavaScript | **مُزالة من الشاشات، لكن بقيت في `athirNativeService.ts`** كمسار بديل للمتصفح | انظر §2 |

لذلك كان عمل هذه المهمة: **إغلاق الفجوات الأمنية، إزالة آخر آثار المحاكاة من طبقة الجسر، تنظيف المستودع، وتصحيح التوثيق** — لا إعادة بناء ما هو مبني.

---

## 1. بيئة التنفيذ والقيد المفروض

| الأداة | متاح؟ | الاستخدام |
|---|---|---|
| قراءة/كتابة الملفات | نعم | كل التعديلات |
| Git + صلاحية دفع | نعم | وسم، فرع، دفع إلى `main` |
| Node.js 22.23.4 | نعم | `tsc`، `vite build`، `cap sync` |
| Gradle / Android SDK / JDK 21 محليًا | **لا (بقرار المالك)** | بناء APK يجري حصريًا في GitHub Actions |
| محاكي أندرويد | لا | غير مستخدم |
| جهازان حقيقيان | لا | انظر «غير مُتحقَّق منه» |

**قرار المالك الملزم:** يُمنع تجهيز أدوات أندرويد محلية أو تنفيذ بناء محلي. وقد أُوقف البناء المحلي الذي كان قد بدأ، وحُذفت حزم SDK/JDK التي نُزّلت (`/opt/android-sdk`, `/opt/jdk21`). التحقق المحلي يقتصر على تحليل ساكن وبناء الويب.

---

## 2. إزالة المحاكاة بالكامل

### 2.1 ما كان محاكاة فعلية في طبقة الجسر (وأُزيل)

في `src/services/athirNativeService.ts` كان هناك مسار كامل للمتصفح يُنتج بيانات وهمية:

| المحاكاة المُزالة | ما كانت تفعله | البديل الآن |
|---|---|---|
| `connectAndSend` بمؤقّت | كانت تُصدر أحداث تقدّم مزيفة (2.5MB/200ms، «15.0 MB/s») حتى 50MB | ترمي `NativeOnlyError` — لا تقدّم وهمي إطلاقًا |
| `startReceiving` وهمي | تُنتج جلسة بعنوان `127.0.0.1` وسر عشوائي ورمز QR لجهاز غير موجود | ترمي `NativeOnlyError` وتُظهر الشاشة حالة صريحة |
| `checkPermissions` وهمي | تُعيد «كل الأذونات ممنوحة» في المتصفح | تُعيد `supported: false` وكل القيم `false` |
| `getDeviceInfo` وهمي | هوية مزيفة «أثير (Web)» بعنوان `127.0.0.1` | ترمي `NativeOnlyError` |
| `mockSessionSecret` / `mockQrPayload` | بيانات اقتران مصطنعة | محذوفة نهائيًا |
| `webListeners` + `emitWebEvent` | ناقل أحداث مزيف | محذوف؛ الاشتراك في الأحداث يقتصر على الطبقة الأصلية |

### 2.2 إزالة المحاكاة من الشاشات

| الملف | ما تغيّر |
|---|---|
| `HomeScreen.tsx` | «الأخيرة» تُقرأ من سجل SQLite الأصلي فقط. حُذف الرجوع إلى `localStorage` (كان يمكن أن يعرض بيانات قديمة وهمية). القيمة المعروضة أصبحت `r.status` الحقيقية بدل `r.direction` |
| `HistoryScreen.tsx` | نفس المبدأ: مصدر وحيد هو `HistoryStore`. حُذف `storageService.getRecentTransfers/clearRecentTransfers` |
| `TransferScreen.tsx` | حُذف `addRecentTransfer` الذي كان يكتب سجلًا محليًا موازيًا. أُضيف عرض حقيقي لأخطاء النقل (`onTransferError`) ونتائج تحقق السلامة (`onFileIntegrity`) |
| `SendScreen.tsx` | لا قائمة وهمية: في المتصفح تُعرض حالة صريحة «متصفح الملفات يعمل داخل تطبيق أندرويد»، وفي التطبيق تُعرض أخطاء القراءة الحقيقية |
| `ReceiveScreen.tsx` | لا QR وهمي: في المتصفح تُعرض حالة «الاستلام يعمل داخل تطبيق أندرويد»؛ داخل التطبيق يُعرض QR المُولَّد من الجلسة الأصلية فقط |
| `PrepareScreen.tsx` | لا `useState` وهمي: الحالة تُقرأ من `PermissionCoordinator`، والتحديث يأتي من حدث `permissionsChanged` بعد إغلاق حوار النظام |
| `ScanScreen.tsx` | حُذف مسار كاميرا المتصفح (`getUserMedia` + `BarcodeDetector`) وتسريب `setInterval` المصاحب له؛ لا انتقال إلى شاشة النقل عند فشل الاتصال |
| `storageService.ts` | أُعيد إلى وظيفته الوحيدة: تمرير سلة الاختيار بين الشاشات (`sessionStorage['sel']`). حُذفت مصفوفات `CATEGORIZED_FILES` و`INITIAL_*` الميتة ودوال سجل `localStorage` |

### 2.3 دليل الأثر الصفري

```
$ grep -rniE "mock|simulat|fake|dummy|SmartTV|user79109045681|DeskLink" src/ android/app/src/main/java/
src/services/athirNativeService.ts:9: * ... the WebView never simulates hardware ...
android/.../DiscoveryManager.kt:219:  val blockedPrefixes = listOf("tun", "ppp", "rmnet", "ccmni", "clat", "dummy")
```
النتيجتان تعليق توثيقي واسم واجهة شبكة، لا محاكاة.

---

## 3. العقد الأمني المُنفَّذ

| # | الخاصية | التنفيذ | الملف |
|---|---|---|---|
| 1 | سر الجلسة لا يُبثّ أبدًا | حُذف `sc` من سجلات mDNS TXT. الإعلان يحمل فقط `id` و`fp` و`v` | `DiscoveryManager.kt:51-62` |
| 2 | جهاز مكتشف عبر الشبكة لا يُقترن تلقائيًا | `requiresQr=true` في الحدث، وزر الاتصال معطّل برسالة «امسح رمز QR للاقتران» | `AthirNativePlugin.kt:324`, `ScanScreen.tsx` |
| 3 | رفض أي نقل بلا سر اقتران | `MISSING_PAIRING_SECRET` يُرفض في الطبقة الأصلية قبل فتح أي مقبس | `AthirNativePlugin.kt:375-382` |
| 4 | مصادقة الجلسة | إطار `AUTH_REQ`/`AUTH_RES`، سر 128 بت من `SecureRandom`، رفض فوري + إغلاق عند عدم التطابق | `TransferEngine.kt`, `SessionSecurity.kt` |
| 5 | تثبيت الشهادة | `X509TrustManager` يقارن SHA-256 للشهادة بالبصمة القادمة من QR | `SessionSecurity.kt:79-100` |
| 6 | مفتاح فوري لكل جلسة | RSA-2048 يُولَّد داخل `startReceiving` | `SessionSecurity.kt:36-40` |
| 7 | تقييد إصدارات TLS | `TLSv1.3` ثم `TLSv1.2` فقط على الطرفين | `TransferEngine.kt:439` |
| 8 | **تحقق سلامة حقيقي** | المستقبل يقارن `FILE_DONE` بـ SHA-256 المحسوب؛ عند الاختلاف: رفض، تسجيل «فشل التحقق»، وإبلاغ الواجهة | `TransferEngine.kt:233-252` |
| 9 | منع Path Traversal | تنقية اسم الملف القادم + تحقق `canonicalPath` داخل مجلد `Downloads/Athir` | `TransferEngine.kt:400-425` |
| 10 | حد أقصى لحجم الإطار | رفض أي إطار > 512 KB قبل التخصيص (حماية من OOM) | `TransferEngine.kt:552-556` |
| 11 | مقبس لا يُعلَّق للأبد | `soTimeout` 5 دقائق على المقابس المقبولة | `TransferEngine.kt:113` |
| 12 | اتصال واحد لا يحجب غيره | Thread لكل اتصال وارد | `TransferEngine.kt:105-121` |
| 13 | أذونات لا تُعلن قبل الموافقة | النتيجة الحقيقية تُنشر بعد الحوار عبر `MainActivity.onRequestPermissionsResult` | `MainActivity.java` + `AthirNativePlugin.kt:99` |
| 14 | إذن الكتابة على أندرويد 9 وأقل | أُضيف `WRITE_EXTERNAL_STORAGE` لـ API ≤ 28 | `PermissionCoordinator.kt:28-34` |
| 15 | سطح هجوم أقل | حُذف `FileProvider` غير المستخدم و`file_paths.xml` | `AndroidManifest.xml` |
| 16 | لا اتصال بالإنترنت | لا HTTP client في المشروع؛ `usesCleartextTraffic=false` | تحقق بـ`grep` |

### نموذج الموافقة (Consent Model) المعتمد
لا يوجد حوار «هل تقبل الاستلام؟» منفصل. الموافقة تُعبَّر عنها بفعلين صريحين من المستخدم:
1. فتح شاشة الاستلام → بدء خادم TLS.
2. عرض رمز QR الذي يحمل سر الجلسة → هذا الرمز هو قناة الموافقة والخارجية الوحيدة للسر.

أي طرف لا يملك السر (بما في ذلك أي جهاز على الشبكة) **لا يستطيع** إرسال ملف. هذا يختلف جذريًا عن الحالة السابقة التي كان فيها السر مبثوثًا للجميع.

---

## 4. تنظيف المستودع

| العنصر | الإجراء | السبب |
|---|---|---|
| `android/.gradle/**` (9 ملفات، منها 1.2 MB) | أُزيل من التتبع | ذاكرة بناء Gradle لا تُحفظ في Git |
| `android/local.properties` | أُزيل من التتبع | مسار SDK محلي خاص بالجهاز |
| `android/app/src/main/assets/public/**` | أُزيل من التتبع | ناتج `cap sync`، وكان يحتوي حزمة **قديمة** (`index-DasIfM9Q.js`) تخالف البناء الحالي |
| `bun.lock` | حُذف | مديرتا حزم معًا؛ الحقيقة الوحيدة هي `package-lock.json` (المستخدم في CI) |
| `index-1.html` | حُذف | نموذج أولي ساكن للشاشة الرئيسية |
| `metadata.json` | حُذف | بقايا تصدير Google AI Studio (يعلن قدرة Gemini غير موجودة) |
| `SHAREIT_PROJECT_COMPREHENSIVE_AUDIT.md` | حُذف | تقرير قديم يصف حالة ما قبل المرحلة الأصلية ويضلّل |
| `SHAREit Developer Guide.md` | حُذف | نفس السبب |
| `SHAREit — Source-Grounded Design System…md` | حُذف | ملف فارغ (0 بايت) باسم مشوّه |
| تبعيات ميتة | حُذفت: `@google/genai`, `express`, `dotenv`, `tsx`, `@types/express`, `motion`, `lucide-react`, `autoprefixer` | لا استيراد لها في أي ملف (تحقق بـ`grep`) |
| `.gitignore` | أُعيد كتابته | يغطي الآن مخرجات أندرويد وأصول Capacitor والحزم الموقّعة |

> ملاحظة: كل المحتوى المحذوف محفوظ في تاريخ Git وفي الوسم `pre-native-conversion-backup`.

---

## 5. الحزمة والبناء

لا تغيير في إصدارات ML Kit (17.3.0 مدمج) أو CameraX (1.3.4) أو Capacitor (8.6.0): هذه هي الإصدارات المفروضة من المالك، وقد بقيت كما هي.

| البند | القيمة | المصدر |
|---|---|---|
| `minSdk / target / compile` | 24 / 36 / 36 | `android/variables.gradle` |
| AGP / Gradle / Kotlin / Java | 8.13.0 / 8.14.3 / 2.0.21 / 21 | `build.gradle`, `gradle-wrapper.properties`, `app/build.gradle` |
| Capacitor | 8.6.0 (ثلاث حزم) | `package.json` |
| ML Kit | `com.google.mlkit:barcode-scanning:17.3.0` (مدمج) | `app/build.gradle` |
| منع النموذج الديناميكي | لا وجود لـ`com.google.android.gms:play-services-mlkit-barcode-scanning` ولا وسم `com.google.mlkit.vision.DEPENDENCIES` | تحقق بـ`grep` |

### تغييرات الورك فلو (`.github/workflows/android-release.yml`)
1. **بناء APK تجريبي (debug) حقيقي** في نفس التشغيل + رفعه كأثر قابل للتنزيل باسم `athir-debug-apk` (SHA-256 يُطبع في السجل).
2. **تصحيح نص الإصدار**: أُزيل الادعاء القديم بأن النقل «محاكاة»، وأُضيف وصف دقيق لما يفعله البناء + قائمة حدود معروفة.
3. لم تُمسّ خطوات التحقق الأمني القائمة (تثبيت Actions بـSHA، `npm ci`، تحقق `apksigner` و`aapt`، Rollback، حذف keystore).

---

## 6. التحقق المُنفَّذ (أدلة فعلية)

| الفحص | الأمر | النتيجة |
|---|---|---|
| تحقق الأنواع | `npx tsc --noEmit` | ✅ صفر أخطاء |
| بناء الويب | `npx vite build` | ✅ 83 موديول، `dist/assets/index-BsF_82In.js` 346.63 kB (gzip 107.89 kB) |
| مزامنة Capacitor | `npx cap sync android` | ✅ نسخ الأصول + إنشاء `capacitor.config.json` |
| سلامة الـworkflow | تحليل YAML | ✅ مهمتان، 18 خطوة في `android-release` |
| توازن الأكواد الأصلية | فحص أقواس على 12 ملف Kotlin | ✅ صفر اختلال |
| عدم وجود محاكاة | `grep -rniE "mock|simulat|fake|dummy|SmartTV|..."` | ✅ لا نتائج فعلية |
| خلو mDNS من السر | قراءة `DiscoveryManager.kt:51-62` | ✅ `id` + `fp` + `v` فقط |
| بناء APK | GitHub Actions | انظر سجل التشغيل في §8 |

---

## 7. ما لم يُنفَّذ — صراحةً

هذه البنود مذكورة في الأمر التوجيهي ولم تُنفَّذ في هذه المهمة. لا تُقدَّم على أنها منجزة:

| البند | السبب | الأثر |
|---|---|---|
| **Wi-Fi Direct (WifiP2pManager)** كناقل | **نُفِّذ في v1.0.3** داخل `P2pTransportManager.kt` (`createGroup` + `requestGroupInfo` + `connect` + مستقبِل P2P). لم يُختبر على جهازين حقيقيين (قيد بيئي) | متاح كمسار بديل عند رفض النقطة المحلية؛ سلوكه على العتاد غير مُتحقَّق |
| **LocalOnlyHotspot** (إنشاء نقطة اتصال) | **نُفِّذ في v1.0.3**: `WifiManager.startLocalOnlyHotspot` (API 26+) مع استخراج SSID وكلمة مرور WPA2 الحقيقية من `WifiConfiguration` وإدخالهما في رمز QR، وانضمام المرسل عبر `WifiNetworkSpecifier` (Q+) أو `WifiConfiguration` (≤P). لم يُختبر على العتاد | نقل بدون راوتر صار ممكنًا برمجيًا؛ التحقق الميداني مطلوب |
| **BLE / Wi-Fi Aware** | يحتاجان مسار اكتشاف موازٍ واختبارًا على عتاد؛ خارج نطاق v1.0.3 | الاكتشاف عبر mDNS ثم QR للنقل المحلي |
| **الاستكمال بعد انقطاع (Resume)** | **نُفِّذ في v1.0.3**: `FILE_META` يحمل الاسم والحجم وSHA-256، والمستقبِل يردّ بـ`FILE_ACK` يحمل آخر إزاحة موثوقة، والمرسل يزيح `InputStream` إليها ويبدأ منها، والتقدّم يبدأ من نقطة الاستكمال هذه (بلا تصفير) | الاستكمال يعتمد على بقاء ملف `.part` في `athir_partial`؛ اختبار الانقطاع الفعلي يحتاج جهازين |
| **إطارات PAUSE/RESUME/CANCEL عبر السلك** | **نُفِّذ في v1.0.3**: قناة تحكّم ثنائية الاتجاه داخل نفس مقبس TLS، يقرأها خيط مستقل على طرف المرسل، وتُعالَج على الطرفين؛ الإلغاء يغلق المقبس بأمان ويُخطر الواجهة فورًا (`transferCancelled`) | الإيقاف/الإلغاء من أي طرف يعمل؛ الاختبار الميداني معلّق |
| **حوار قبول/رفض للملفات الواردة** | يحتاج شاشة جديدة + إطار بروتوكولي جديد. «هوية التصميم مقفلة» تمنع إضافة شاشة | الموافقة عبر فتح شاشة الاستلام وعرض QR (§3) |
| **Storage Access Framework للمستندات** | **نُفِّذ في v1.0.3**: `ACTION_OPEN_DOCUMENT` مع `takePersistableUriPermission` وزر «مستندات الجهاز» في شاشة الإرسال | يمكن اختيار أي مستند (PDF/DOCX/ZIP) وقراءته في الطبقة الأصلية |
| **MANAGE_EXTERNAL_STORAGE خلف علم بناء** | لم يُطلب لأن المسار الحالي لا يحتاجه؛ إضافته بلا حاجة تخالف سياسة Google Play | لا «كل الملفات» |
| **AAB / Google Play** | خارج نطاق الأمر | النشر عبر GitHub Releases فقط |
| **اختبارات آلية** | **نُفِّذ في v1.0.3 جزئيًا**: `./gradlew testDebugUnitTest` يعمل في CI بلا عتاد ويغطّي `AthrFrameCodec` (ترميز/فك الإطارات، سقف الحجم، إزاحة الاستكمال، تحويل الهاش) و`PartialFileStore.resumeOffset` (14 اختبارًا). لا Robolectric ولا اختبارات واجهة | لا حماية انحدار لواجهة المستخدم |

---

## 8. سجل النشر والبناء

| البند | القيمة |
|---|---|
| الوسم الاحتياطي | `pre-native-conversion-backup` |
| الفرع | `feat/native-real-transfer` → `main` |
| التشغيل | يُسجَّل في تقرير التسليم النهائي (رقمه وحالته) |
| أثر APK التجريبي | `athir-debug-apk` في صفحة التشغيل |

---

## 9. قائمة اختبار يدوي (قصيرة، على جهازين حقيقيين)

1. تثبيت APK على جهازين على نفس شبكة Wi-Fi.
2. الجهاز (أ): استلام → منح الأذونات → التأكد من ظهور QR.
3. الجهاز (ب): إرسال → اختيار ملف (تطبيق، صورة، ملف) → فحص → مسح QR.
4. التأكد من: ظهور الاسم الحقيقي للجهاز، تحرك شريط التقدم بسرعة حقيقية، وصول الملف إلى `Downloads/Athir/`.
5. فحص: `adb shell ls /sdcard/Download/Athir/` و`cmp` مع الملف الأصلي.
6. اختبار الرفض: مسح QR بعد إغلاق شاشة الاستلام على (أ) → يجب أن يفشل الاتصال برسالة واضحة لا بشاشة نقل فارغة.
7. اختبار mDNS: فتح شاشة الفحص على (ب) دون مسح QR → يجب أن يظهر الجهاز مع تعطيل زر الإرسال ورسالة «امسح رمز QR».
8. اختبار سجل: بعد نقل ناجح، فتح «السجل» على الجهازين → يجب أن يظهر سجل حقيقي، والرئيسية تعرض آخر 5 عمليات.
9. اختبار إعادة التشغيل: إغلاق التطبيق وفتحه → يجب أن يبقى السجل، وألا تظهر أي بيانات وهمية.
10. اختبار أندرويد 9 (إن توفر): استلام ملف → التأكد من نجاح الحفظ (مسار `WRITE_EXTERNAL_STORAGE`).

## 10. ما لم يُتحقَّق منه في هذه البيئة

- تشغيل التطبيق فعليًا على جهاز أو محاكي (لا يوجد أي منهما، والبناء المحلي ممنوع بقرار المالك).
- أداء النقل الحقيقي (MB/s) على شبكة فعلية.
- سلوك mDNS على أجهزة مختلفة (بعض أجهزة أندرويد تحدّ من NSD).
- الأذونات عبر إصدارات أندرويد 7/9/13/14 الحقيقية.
- أي من بنود §7.

> **الادعاء المسموح:** «لم يُعثر على مشكلة في الفحوص المذكورة في §6، وهذه الأمور لم تُتحقَّق: §10.»


---

## 11. تحديث v1.0.3 — محرّك النقل بلا راوتر + الاستكمال + قناة التحكّم

هذا القسم يوثّق ما أُضيف بعد v1.0.2. لم يُحذف أي بند من الأقسام السابقة؛ ما تغيّر مُعلَّم صراحةً أعلاه في §7.

### 11.1 ما نُفِّذ (كود حقيقي، لا محاكاة)

| الملف | الإضافة | طبيعة الاستدعاء |
|---|---|---|
| `P2pTransportManager.kt` (جديد) | `WifiP2pManager` (group owner عبر `createGroup`+`requestGroupInfo`، وانضمام عبر `connect`)، `WifiManager.startLocalOnlyHotspot` مع SSID/PSK حقيقيين، انضمام المرسل عبر `WifiNetworkSpecifier`/`WifiConfiguration`، مستقبِل بث P2P، `stopAll()` | استدعاءات عتاد فعلية، كلها داخل `try/catch` مع `Log.d(TAG="AthirP2p")` وإرجاع `Result.failure` بدل الانهيار |
| `PartialFileStore.kt` (جديد) | ملفات `.part` داخل `athir_partial`، `resumeOffset()`، `openAppend()`، `publishToDownloads()` عبر MediaStore (Q+) أو `Downloads/Athir` (أقدم) | قراءة/كتابة ملفات فعلية |
| `TransferEngine.kt` | بروتوكول الاستكمال (`FILE_META`+`FILE_ACK`+إزاحة)، قناة تحكّم ثنائية الاتجاه (PAUSE/RESUME/CANCEL) بخيط قراءة مستقل، إغلاق آمن، تقدّم بإزاحة أساس، رفع إصدار البروتوكول إلى `0x02` | شبكة TLS حقيقية |
| `AthrFrameCodec.kt` (جديد) | فصل ترميز الإطارات في كود JVM خالص قابل للاختبار في CI | — |
| `AthirNativePlugin.kt` | `startOfflineTransport` / `stopOfflineTransport` / `joinOfflineTransport` / `pickDocuments` (+`@ActivityCallback`) وثلاثة أحداث جديدة: `transferResumed`, `transferCancelled`, `transferPaused` | — |
| `SessionSecurity`/`QrEncoder`/`ReceiveScreen`/`ScanScreen`/`SendScreen`/`TransferScreen`/`athirNativeService` | رمز QR يحمل `transport` + `ssid` + `psk` + `p2pAddr`؛ زر «نقل بدون راوتر»؛ زر «مستندات الجهاز»؛ أزرار إيقاف/إلغاء موصولة بالأحداث الحقيقية وعرض «استُئنف من … MB» | — |

### 11.2 مصفوفة الصلاحيات بعد v1.0.3

`CHANGE_WIFI_STATE`، `ACCESS_WIFI_STATE`، `NEARBY_WIFI_DEVICES` (بعلم `neverForLocation`)، `ACCESS_FINE_LOCATION` و`ACCESS_COARSE_LOCATION` بسقف `maxSdkVersion=32`، `CHANGE_WIFI_MULTICAST_STATE`، `<uses-feature android.hardware.wifi.direct required="false"/>`. لا صلاحية كتابة إلا حتى API 29.

### 11.3 التدهور الآمن عند غياب العتاد

`startOfflineTransport` يجرّب النقطة المحلية أولًا؛ إذا رفضها النظام (لا دعم، أو صلاحية مرفوضة، أو `SecurityException`) يسجّل السبب ويعود إلى Wi-Fi Direct؛ وإذا فشل الاثنان يُرجع الخطأ الصريح `NO_OFFLINE_TRANSPORT: <السبب>` إلى الواجهة. لا نجاح وهمي في أي مسار.

### 11.4 ما يبقى غير مُتحقَّق (يحتاج جهازين حقيقيين)

تشكيل مجموعة Wi-Fi Direct فعليًا، نجاح `startLocalOnlyHotspot` على عتاد حقيقي، زمن الانضمام عبر `WifiNetworkSpecifier`، استكمال نقل كبير بعد قطع الشبكة عمدًا، وسلوك الإلغاء المتزامن من الطرفين. كل هذه مذكورة في §9 وتحتاج تشغيلًا يدويًا.
