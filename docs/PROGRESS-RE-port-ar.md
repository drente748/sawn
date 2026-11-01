# حالة نقل ميزات Block + BlockerHero إلى Warden

> مرجع الخطة: `docs/RE-block-11.3.0-ar.md` §9، وتفاصيل الإشارات في `docs/RE-blockerhero-1.5.0-ar.md`.
> آخر تحديث: 2026-10-30 — الإصدار 0.6.0 (versionCode 10).

## المكتمل

### المرحلة A — محرك الويب (من Block)
- `block/web/BrowserConfig.kt`: جدول 116 متصفحًا منقول حرفيًا (بما فيه `com.yujian.ResideMenuDemo` بحروفه الأصلية).
- `block/web/WebAddressPattern.kt`: بوابة WebAddress كاملة (IDN/IPv4/منافذ/مسارات).
- `block/web/WebUrlExtractor.kt`: استخراج من `text` و`contentDescription` مع تخطي الحقل المركّز.
- `accessibility/BrowserUrlMonitor.kt`: يقرأ شريط العنوان بمعرّف المورد، مع BFS احتياطي بسقف 2000 عقدة.
- اختبارات: `BrowserConfigTest`, `WebUrlExtractorTest`, `WebAddressPatternTest`.

### المرحلة B — كاشفات الميزات + navigateAway (Block + BlockerHero)
- `feature/FeatureAction.kt`: محلل DSL نقي:
  - `global_action:navigate_back`
  - `global_action:home`
  - `click:<viewId>` (معرّف كامل أو اسم مورد فقط)
  - يقبل `null`، ويتجاهل الأوامر المجهولة، ويحدّ القائمة بـ 3 إجراءات كحد أقصى.
- `UiNode`: أُضيف `isVisibleToUser` و`isSelected` و`performClick()`.
- `FeatureDetector`: بوابة `isVisibleToUser` (مثل `kr0.q` في Block)، وإشارات التبويبات
  تُطابق فقط عند `selected`، وفصل `text` عن `contentDesc` بمسافة كي لا تتكوّن عبارة وهمية
  من عقدتين.
- `A11yNode`: تنفيذ الخصائص الثلاث عبر `AccessibilityNodeInfo` مع `runCatching`،
  و`findByViewId` يفضّل العقدة المرئية.
- `FeatureCatalog`: 14 ميزة تشمل:
  - Block: YouTube Shorts، ReVanced، Instagram Reels (`clips_viewer_view_pager`)،
    WhatsApp Updates (`updates_list`)، Snapchat Spotlight (`spotlight_container`).
  - BlockerHero: Instagram Search، Facebook Reels، Snapchat `ngs_*`.
  - بدائل: Instagram Lite، Facebook Lite، Vanced، WhatsApp Business.
- `AppBlockAccessibilityService`: ينفّذ `navigateAwayTo` قبل شاشة الحجب، ينتظر 300ms،
  ثم يقرر `FeatureWallPolicy`:
  - `NavigateAwayGuard` يمنع حقن BACK/HOME/click إلا إذا كان الجذر النشط **مثبتًا** أنه
    التطبيق المستهدف؛ الجذر الفارغ أو تطبيق آخر يوقف الإجراء (بخلاف `isForeground`
    المتسامح مع قرارات الحجب الكاملة).
  - يبقي الحجب إذا بقي المستخدم في التطبيق أو انتقل إلى launcher/System UI فقط.
  - يلغي الحجب إذا انتقل المستخدم إلى تطبيق آخر، بما فيه الهاتف/الطوارئ/الإعدادات.
  - لا يفعّل dedup إلا بعد نجاح `startActivity`.
- إعداد خدمة الوصول: أُضيف `flagIncludeNotImportantViews`، وأُضيفت أحداث
  `typeViewClicked|typeViewLongClicked|typeViewFocused|typeViewSelected` لتغذية الفحص
  المخنوق فقط (لا قرار الحجب الكامل)، حتى تُلتقط تغييرات التبويبات التي لا تصدر
  حدث محتوى.
- `FeatureCatalog`: ميزة TikTok أصبحت **TikTok Feed** عمدًا (For You + Following
  يشتركان في `feed_recycler`)، وأُزيل النص المترجم "For You" وبقي `for_you` محدّدًا فقط.
- اختبارات: `FeatureActionDslTest`, `FeatureCatalogTest`, `FeatureWallPolicyTest`,
  `NavigateAwayGuardTest`, `CriticalAppsTest`، وتوسعة `FeatureDetectorTest`.

### قرارات إشارات اتُّخذت بعد مراجعة مستقلة

| القرار | السبب |
|---|---|
| حذف `reel_recycler` من إشارات YouTube غير المشروطة | يظهر أيضًا في أرفف Shorts على الرئيسية/الاشتراكات؛ استخدامه كان سيعيد المستخدم للخلف أثناء التصفح العادي. استُبدل بإشارات المشغّل (`reel_watch_fragment_root`, `reel_player_page_container`, `reel_watch_player`, `reel_player_underlay`, `shorts_video_pager`). |
| `ngs_spotlight_icon_container` إلى «محدّد فقط» | أيقونة تبويب دائمة في Snapchat؛ مطابقتها غير المشروطة تحجب الكاميرا/الرئيسية. |
| حذف `View reels` و`FbShortsComposer` من إشارات Facebook | الأولى CTA عام في الـfeed، والثانية اسم class وليست نصًا مرئيًا. بقي `Navigate to your Reels profile` فقط. |
| حذف `like_button` و`peek_container`/`indicator_container` | عامة جدًا (تظهر في منشورات عادية) وتسبب حجبًا كاذبًا. |
| عدم تبنّي عناوين Explore المترجمة | مطابقة نصية هشّة تتغير باللغة/الإصدار؛ بقي `explore_grid`/`explore_recycler` كإشارة view-id. |
| عدم تبنّي `FbShortsViewerActivity` | يتطلب مطابقة `event.className` غير مدعومة حاليًا في الكشف. |
| عدم تبنّي Telegram/WhatsApp channels | تحتاج ربط className + نص و29 لغة؛ خارج نموذج view-id/text الحالي. |
| تسجيل `className` (مثل `FbShortsViewerActivity`) | لم يُضف بعد إلى `UiNode`/الأحداث؛ يحتاج اختبارًا على جهاز قبل الاعتماد عليه. |

## المؤجل (بأسباب)

| البند | السبب |
|---|---|
| JSON في assets + تحديث عن بعد (TTL 600s) للميزات والمتصفحات | يحتاج مصدر تحديث ومخطط إصدار؛ الكتالوج الحالي مضمّن ومغطى باختبارات. |
| حقن أنماط URL (B.5) مثل `youtube.com/shorts` | لا يوجد بعد محرك كلمات ويب في Warden؛ مكانه الطبيعي المرحلة F. |
| Telegram search (29 لغة) وقنوات WhatsApp (Activity class) | يحتاج إشارة `event.className` وربط `className` + نص؛ النموذج الحالي view-id/text فقط. |
| بقية المراحل C–F | لم تبدأ بعد: overlay ثلاثي الطبقات، popup closer، الريبوت الذكي، حدود الفتح، محرك الكلمات. |

## التحقق
- `.\gradlew.bat testDebugUnitTest --console=plain` → 146 اختبارًا، 0 فشل (19 ملف نتائج).
- `.\gradlew.bat assembleRelease --console=plain` → نجح؛ APK موقّع v2، الإصدار 0.6.0 (code 10).
- تحقق داخل APK: `accessibilityEventTypes=0x40082f` (يشمل click/long-click/focus/selected)
  و`accessibilityFlags=0x52` (يشمل `flagIncludeNotImportantViews`).
- الملف: `C:\Users\pc\Desktop\Warden-0.6.0-release.apk` — 34.64 MB —
  SHA256 `A11F1869C61892FAEEE9239DAFA25E88DC4148623968377EA23A7B85280B399D`.
- لا يوجد جهاز موصول؛ سلوك الوصول الحقيقي يحتاج تجربة يدوية حسب `DEVICE_TESTING.md` §1.
