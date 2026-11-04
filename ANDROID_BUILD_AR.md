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

الـworkflow الوحيد `Android Signed Release` يعمل عبر `workflow_dispatch` على فرع `main`. يقوم في تشغيل واحد بفحص TypeScript وواجهة الويب، وحساب الإصدار التالي، وبناء APK release موقّع، وتثبيته وتشغيله على محاكي API 36، ثم إنشاء commit إصدار وعلامة GitHub Release ورفع ملف APK والتحقق منه قبل نشر الإصدار.

مفتاح التوقيع لا يوجد في المستودع. تُستخدم الأسرار التالية من بيئة GitHub المسماة `android-release` فقط:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

تُكتب نسخة مؤقتة من keystore داخل مجلد runner الخاص، وتُحذف حتى عند فشل المهمة. لا تضف ملفات `.jks` أو `.keystore` أو `keystore.properties` إلى Git.

## حدود التطبيق الحالية

هذا العمل يجهّز غلاف Android ويُبقي تصميم الويب كما هو. ميزات اكتشاف الأجهزة، واختيار الملفات، ورمز QR، وسرعة النقل وسجل النقل موجودة حاليًا كمحاكاة واجهة/بيانات محلية في الشيفرة؛ تغليفها بـCapacitor لا يحولها تلقائيًا إلى نقل ملفات حقيقي أو Wi-Fi Direct. لذلك يثبت اختبار المحاكي الإقلاع وظهور الشاشة الرئيسية فقط، ولا يثبت نقلًا فعليًا بين جهازين.

## المصادر الرسمية

- [تثبيت Capacitor وإضافته إلى مشروع ويب قائم](https://capacitorjs.com/docs/getting-started)
- [دليل Capacitor الرسمي لمنصة Android](https://capacitorjs.com/docs/android)
- [متطلبات بيئة Capacitor 8](https://capacitorjs.com/docs/getting-started/environment-setup)
- [دليل تحديث Capacitor 8 ومتطلبات Android](https://capacitorjs.com/docs/updating/8-0)
- [إصدارات Capacitor المنشورة](https://github.com/ionic-team/capacitor/releases)
