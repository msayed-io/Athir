# إعداد Android وCapacitor لأثير

## الإصدارات والهوية

- Capacitor Core وAndroid وCLI مثبتة جميعها على الإصدار المستقر `8.6.0`.
- معرف التطبيق: `com.athir.datatransfer`، واسم التطبيق في Android: `أثير`.
- أصول الويب تُبنى إلى `dist/` ثم تُزامن إلى منصة Android عبر Capacitor.
- Android: `minSdk 24` و`compileSdk 36` و`targetSdk 36`، وJava 21، وGradle Wrapper 8.14.3.

## التطوير محليًا

يتطلب Capacitor 8 Node.js 22 أو أحدث. افتح مجلد `android/` في Android Studio 2025.2.1 أو أحدث وثبّت Android SDK API 36، ثم شغّل:

```bash
npm ci
npm run lint
npm run android:sync
```

لفتح المشروع الأصلي في Android Studio:

```bash
npm run android:open
```

## إصدار APK موقّع

الـworkflow الوحيد `Android Signed Release` يعمل تلقائيًا عند كل دفعة على فرع `main`، وكذلك يدويًا عبر `workflow_dispatch`. في تشغيل واحد يفحص TypeScript وواجهة الويب ومزامنة Capacitor، ويحسب الإصدار التالي، ويبني APK release موقّعًا ويتحقق من توقيعه ومعرّف الحزمة ورقم الإصدار؛ ثم ينشئ commit الإصدار والعلامة ويرفع APK إلى GitHub Release وينشره. لا يشغّل هذا الـworkflow محاكي Android أو اختبارًا على جهاز.

مفتاح التوقيع لا يوجد في المستودع. تُستخدم الأسرار التالية من بيئة GitHub المسماة `android-release` فقط:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

تُكتب نسخة مؤقتة من keystore داخل مجلد runner الخاص، وتُحذف حتى عند فشل المهمة. لا تضف ملفات `.jks` أو `.keystore` أو `keystore.properties` إلى Git.

## حالة التطبيق الحالية

التطبيق ينقل ملفات حقيقية فعليًا: اكتشاف عبر mDNS، اقتران برمز QR حقيقي، نقل بايتات عبر TLS بتثبيت البصمة، تحقق SHA-256 لكل ملف، كتالوج ملفات من الجهاز، وسجل SQLite دائم. لا توجد أي محاكاة في طبقة الواجهة.

الحدود الحقيقية المتبقية (تفصيلها في `docs/NATIVE_REAL_TRANSFER_REPORT.md`): لا Wi-Fi Direct ولا نقطة اتصال محلية ولا BLE/Wi-Fi Aware كنواقل، ولا استكمال للنقل بعد الانقطاع، والاكتشاف عبر mDNS يتطلب مسح رمز QR للاقتران (بالتصميم، لأن السر لا يُبثّ).

الـworkflow يبني الآن نسختين: APK release موقّعة ومنشورة، وAPK debug مرفوعة كأثر قابل للتنزيل (`athir-debug-apk`) للاختبار اليدوي. ولا يشغّل محاكيًا، لذا فإن نجاح البناء يثبت التجميع والتوقيع وبيانات الحزمة، ولا يُعدّ اختبارًا للسلوك على جهاز.

## المصادر الرسمية

- [تثبيت Capacitor وإضافته إلى مشروع ويب قائم](https://capacitorjs.com/docs/getting-started)
- [دليل Capacitor الرسمي لمنصة Android](https://capacitorjs.com/docs/android)
- [متطلبات بيئة Capacitor 8](https://capacitorjs.com/docs/getting-started/environment-setup)
- [دليل تحديث Capacitor 8 ومتطلبات Android](https://capacitorjs.com/docs/updating/8-0)
- [إصدارات Capacitor المنشورة](https://github.com/ionic-team/capacitor/releases)


---

## تحديث v1.0.3

- النقل بلا راوتر: نقطة اتصال محلية (API 26+) مع SSID/كلمة مرور حقيقيين داخل رمز QR، وبديل Wi-Fi Direct.
- استكمال النقل من آخر بايت موثوق (`FILE_ACK` يحمل الإزاحة) مع التحقق SHA-256 بعد الاستكمال.
- قناة تحكّم حقيقية: إيقاف/استكمال/إلغاء من أي طرف عبر إطارات داخل نفس مقبس TLS.
- اختيار المستندات عبر `ACTION_OPEN_DOCUMENT` (SAF) بدل الاعتماد على `MediaStore.Files` وحده.
- اختبارات CI بلا عتاد: `:app:testDebugUnitTest` (AthrFrameCodec + PartialFileStore).
- ما يبقى غير مُتحقَّق: أي سلوك لاسلكي فعلي بين جهازين (لا عتاد في بيئة التنفيذ).
