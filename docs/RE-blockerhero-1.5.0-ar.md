# تقرير هندسة عكسية — BlockerHero 1.5.0
### تحليل معماري كامل لمحرك حجب المحتوى الإباحي في السوشل ميديا، وخطة نقله إلى Warden

> **الهدف:** `com.blockerhero` — الإصدار 1.5.0 (versionCode 190)
> **الملف:** `BlockerHero-Content-Blocker_1.5.0_apkcube.apks` (9.6MB، مع splits للغات والكثافات، `base.apk` = 9,007,059 بايت)
> **المنهجية:** تحليل ساكن فقط (static, read-only) — لم يُشغَّل التطبيق ولم يُثبَّت على أي جهاز. تم فكّ `classes.dex` عبر baksmali (7,321 فئة) وتفكيك كامل إلى Java عبر jadx (7,099 فئة)، وفكّ الموارد عبر Apktool، وقراءة المانيفست عبر aapt.
> **حدود مهمة:** R8 خفّض الأسماء (`D4.r`, `K6.u0`, `Y3.b`...)، وبعض الدوال لم تُفكّك (jadx overflow). كل ما يلي مبني على قراءة الكود الفعلي؛ المواضع غير المؤكدة موسومة صراحةً.
> **التوقيع:** لم يُتحقق من صحة توقيع الحزمة (لا يوجد digest منشور للمقارنة) — التحليل يعتمد على المحتوى فقط.

---

## 1. الهوية والحزمة

| البند | القيمة |
|---|---|
| Package | `com.blockerhero` |
| versionName / Code | `1.5.0` / `190` |
| minSdk / targetSdk | 23 / 35 |
| اللغة والتقنيات | Kotlin، Jetpack Compose، Room، Retrofit/OkHttp، Firebase (Auth/Messaging/Analytics)، Play Billing، Huawei AGConnect، WorkManager |
| الأذونات اللافتة | `SYSTEM_ALERT_WINDOW` (طبقة الحجب)، `REQUEST_DELETE_PACKAGES`، `USE_BIOMETRIC`، `POST_NOTIFICATIONS`، `BILLING`، `RECEIVE_BOOT_COMPLETED`، `FOREGROUND_SERVICE` |
| **لا يوجد** | `BIND_VPN_SERVICE` — لا طبقة VPN/DNS إطلاقاً |

### المكونات (من AndroidManifest)

| المكوّن | الدور |
|---|---|
| `com.blockerhero.features.MainActivity` | الواجهة (Compose) |
| `com.blockerhero.services.MyAccessibilityService` | **الدماغ**: كل كشف وحجب المحتوى |
| `com.blockerhero.receiver.MyDeviceAdminReceiver` | Device Admin فارغ السياسات (`<uses-policies/>` فقط) — لعرقلة إلغاء التثبيت |
| `com.blockerhero.services.fcm.FCMService` + `FCMBroadcastReceiver` | أوامر سحابية (مزامنة قوائم، تحكم آبائي، إشعارات بأزرار) |
| `androidx.work.*` | مزامنات دورية |

### إعداد خدمة إمكانية الوصول (`res/xml/accessibility_xml.xml`)

```xml
<accessibility-service
    android:accessibilityEventTypes="typeViewClicked|typeViewLongClicked|typeViewFocused|typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackAllMask"
    android:notificationTimeout="100"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true" />
```

مقارنةً بـ Warden الحالي: Warden يستمع فقط لـ `typeWindowStateChanged|typeWindowContentChanged` بدون `flagIncludeNotImportantViews` وبدون `typeViewFocused|typeViewClicked`. هذه الفروق مهمة: أحداث `FOCUSED`/`CLICKED` هي ما يلتقط لحظة فتح شريط البحث أو تحرير عنوان URL.

---

## 2. نظرة معمارية عامة

```
┌────────────────────────────── MyAccessibilityService ──────────────────────────────┐
│  onAccessibilityEvent(event)                                                       │
│   1) فلترة (حزمة التطبيق نفسه / طبقة حجب ظاهرة / هواتف واتصال / قائمة تجاهل)       │
│   2) بوابة الميزات الخاصة بالمنصة (Instagram/FB/YT/Snap/WA/TG)  → نوع حجب 12..18   │
│   3) استخراج النص: event.text + contentDescription + source.text + source.desc      │
│      → lowercase → z.d() تطبيع → تقسيم على الفراغات؛ إن > 25 رمزاً → تجاهل          │
│   4) كاشف شاشات إلغاء التثبيت (A.a) لكل OEM → نوع 2 أو تذكير الحماية               │
│   5) Focus Mode (قائمة تطبيقات مسموحة) → نوع 1                                      │
│   6) قائمة التطبيقات المحجوبة f11988L → نوع 4                                       │
│   7) المتصفحات: قراءة شريط العنوان عبر view-id من الخادم (a0) → r.b / r.a          │
│   8) قائمة عناوين URL المحجوبة f12014r0 → r.a(زائفة)                               │
│   9) تطبيقات يمكنها فتح الروابط (متصفحات غير مدعومة) + WebView → r.m → r.c          │
│  10) حجب تطبيقات "محتوى للبالغين" من الخادم f11994R → نوع 7                        │
│                                                                                    │
│  عرض الحجب: u0.u(service, pkg, type, node/event, matchedKeyword, label)            │
│   → GLOBAL_ACTION_BACK ثم overlay TYPE_APPLICATION_OVERLAY + عدّاد + تقرير         │
└────────────────────────────────────────────────────────────────────────────────────┘

مصادر القوائم:
  خادم HTTPS (api.blockerhero.com) ──GET /blacklist/global?from=...──► Room (GlobalBlockedItem)
  قاعدة البيانات المحلية ──Flow──► D4.j (إعادة بناء 20+ قائمة مصنّفة في الذاكرة)
  إعدادات المستخدم    ──Room──► D4.h (قوائم المستخدم: كلمات/تطبيقات/استثناءات/متصفحات)
  مفاتيح الميزات      ──SharedPreferences──► Y3.b + مزامنة تحكم آبائي عبر FCM
```

النقطة الجوهرية: **BlockerHero محرك نصّي-بنيوي (text + view-id) بالكامل**. لا صور، لا OCR، لا DNS. قوّته كلها في (أ) دقة تحديد عناصر واجهات المنصات بأسمائها الداخلية، (ب) قراءة شريط عنوان المتصفح بعنصر محدد لكل متصفح، (ج) فرض SafeSearch بإعادة توجيه URL، (د) قوائم كلمات مصنّفة آتية من الخادم تُترجم إلى Regex.

---

## 3. خط أنابيب `onAccessibilityEvent` بالتفصيل (المصدر: `MyAccessibilityService.java:409-605`)

### 3.1 الفلاتر المبكرة
1. تجاهل أحداث حزمة التطبيق نفسه (`P7.k.a(pkg, getPackageName())`).
2. إن كانت طبقة الحجب `c().f15588a` ظاهرة → إرجاع (منع التكرار).
3. إن كانت طبقة "منع إلغاء التثبيت" `e().f540s` ظاهرة، أو الحزمة ضمن `r.j()` (هاتف/اتصال/جهات + 3 حزم خاصة)، أو `className` ضمن `f11998V` → إرجاع.
4. التقاط أحداث للتصحيح فقط إذا فُعّل `KEY_IS_DEBUGGING_TAB_ENABLED` + `KEY_IS_DEBUGGING_ENABLED` (أداة دعم فني تبني log كامل عن الحدث، `D4.c.a`).
5. `packageName == "android"` → إرجاع.

### 3.2 بوابات المنصات (قبل أي فحص نصي عام)

| المنصة | الشرط | النوع |
|---|---|---|
| Instagram / Lite | `r.i()`: عنصر `:id/search_tab` محدد، أو `:id/action_bar_search_edit_text`، أو `:id/search_bar_real_field` | **12** (Instagram search) |
| Instagram / Lite + Facebook / Lite | `r.h()`: `:id/clips_tab` محدد، `:id/like_button` مرئي، `:id/peek_container` + `:id/indicator_container`، `action_bar_title` = "Explore" بأي لغة؛ فيسبوك: `contentDesc="View reels"`، الفئة `com.facebook.fbshorts.viewer.activity.FbShortsViewerActivity`، نصوص `FbShortsComposer` / `Navigate to your Reels profile` / `Add comment...` | **13** (Reels) + `GLOBAL_ACTION_BACK` |
| WhatsApp / WA Business | الفئة = `com.whatsapp.newsletter.directory.ui.NewsletterDirectoryCategoriesActivity` | **18** (Channels) |
| Telegram / Challegram | الحزمة تطابق Regex `telegram\|challegram` + الفئة `EditText` + نص موجود في قائمة "Search" بـ 29 لغة | **15** (Telegram search) |
| Snapchat | `:id/ngs_spotlight_icon_container` أو `:id/ngs_community_icon_container` | **17** (Stories/Spotlight) |
| YouTube / Vanced / ReVanced | `:id/reel_recycler` | **16** (Shorts) |

كل ضربة → `u0.u(...)` فوراً ثم `return` (لا يكمل الفحص النصي العام).

**لماذا هذا أقوى من نهج Warden الحالي؟** Warden يكشف "Reels" بالنصوص والتحذيرات العامة فقط؛ BlockerHero يقرأ عناصر الواجهة الفعلية (`clips_tab`, `reel_recycler`, `ngs_spotlight_icon_container`) — وهي مستقرة عبر تحديثات المنصات لأنها أسماء موارد داخلية، وتعمل بلا أي نص إنجليزي.

### 3.3 استخراج النص والتطبيع
`r.f(event)` يجمع: `event.getText()` + `event.getContentDescription()` + `source.getText()` + `source.getContentDescription()` → `toString()` → `toLowerCase(Locale.ROOT)` → `z.d()`.

`z.d()` (تطبيع):
- `%20` → مسافة، `%23` → `#`
- حذف كل ترقيم `[+="\[\]$%\-\\,_~\`’';:!?/|^<>›&{}()]` → مسافة
- ضغط المسافات المتتالية، lowercase، trim
- بالتالي: يُطبَّع نص الشاشة وروابط URL معاً للمطابقة الموحّدة.

ثم: لو عدد الرموز (بعد التقسيم على `\s+`) **> 25 → إرجاع**. هذا سقف أداء يمنع فحص نصوص طويلة (تعليقات، مقالات).

### 3.4 حجب كلمات القوائم (النوع 0) — المساران

**مسار المتصفح المدعوم** (الحزمة ضمن `Z` + عنصر شريط العنوان معروف):
```java
r.a(service, event, text, /*supportedBrowser=*/true, /*hardcodedListed=*/zM1);
// داخل r.a:
if (KEY_IS_PORN_BLOCKER_ENABLED && text.length() >= 3) {
    if (z.b(text, allowlist f12022v0) != null) return;      // قائمة السماح أولاً
    String hit = z.b(text, supported ? f12016s0 : f12018t0); // global vs user
    if (hit != null) { BACK; u0.u(..., 0, event, hit, ...); } // نوع 0 + عرض الكلمة
}
```
ثم تجد `BrowserDetails(pkg, urlBarId)` وتقرأ العقدة بالمعرّف `r.e(source, urlBarId)` وتنادي `r.b(node, zM1)`:

```java
// r.b: نص شريط العنوان
if (KEY_IS_IMG_VID_SEARCH_BLOCKED && supported && z.c(text, f12010n0) && z.b(text, f11990N)==null) -> block type 3
if (KEY_IS_FILTER_SEARCH_RESULTS_ENABLED) {              // افتراضي true
    Uri u = parse(text);
    if (host in SEARCH_ENGINES) {
        if (safeParamMissing(u)) → coroutine D4.w → open(safeUrl in same browser)
    }
}
norm = z.d(text + " " + contentDesc);
if (norm.length() < 3 || focused) skip;
if (norm.length() > 250) { YouTube-family = allow "text_bg"; else skip }
if (z.b(norm, allowlist)==null) hit = z.b(norm, f12016s0); // نوع 0
```

**مسار URL آخر** (`f12014r0` = كلمات URL من المستخدم نوع 12 + الخادم نوع 2): `r.a(..., false, false)`.

### 3.5 كشف "متصفحات غير مدعومة" (r.m → r.c)

`r.m` يعيد رمزاً داخلياً (`f11985I`) ثم `r.c` يعرض الحجب أو التذكير:

| الرمز | المعنى |
|---|---|
| 28 | تطبيق Google (quicksearchbox) في شاشة بحث داخلية (node `googleapp_root` بلا viewId، حدث 8) |
| 29 | حزمة **قادرة على فتح روابط** (`resolveActivity(ACTION_VIEW, http://www.google.com)` == نفسها) وليست ضمن قائمة المتصفحات المدعومة المضمّنة |
| 30 | الحزمة تطابق Regex من الخادم (`f11999W` — قائمة أسماء حزم محجوبة) |
| 31 | داخل `android.webkit.WebView` والنص يطابق قائمة الخادم `X` وليس ضمن استثناءات `Y` |
| 32 | داخل WebView والنص يطابق كلمات المستخدم/الخادم (`f11987K + f11989M`) وليس في قائمة السماح |

سلوك `r.c`: إن كان `KEY_IS_UNSUPPORTED_BROWSERS_BLOCKED` → حجب نوع **8**؛ وإلا إشعار تذكيري واحد كل **5 دقائق لكل حزمة** (`detect_unsupported_browser_message`).

### 3.6 حجب تطبيقات "قد تحتوي محتوى بالغين"
```java
if (KEY_IS_PORN_BLOCKER_ENABLED && z.c(pkg, f11994R)) → u0.u(..., 7, ...);
```
`f11994R` = نوع 6 من الخادم (أسماء حزم). الرسالة: "This app is blocked because it may contain adult content."

### 3.7 عرض الحجب `u0.u` (المصدر: `K6/u0.java:370-524`)
1. إن كانت الطبقة ظاهرة → إرجاع.
2. `performGlobalAction(GLOBAL_ACTION_BACK)` **قبل** عرض أي شيء (إخراج المستخدم من الشاشة فوراً).
3. عرض `blocked_screen_overlay` عبر `WindowManager` بنوع `2032` (TYPE_APPLICATION_OVERLAY)، ملء الشاشة، مع:
   - عدّاد تنازلي من `KEY_BLOCKED_SCREEN_COUNTDOWN_SECONDS`
   - رسالة مخصصة `KEY_CUSTOM_BLOCKED_SCREEN_MESSAGE` (افتراضي "This page is blocked.")
   - أيقونة واسم التطبيق المحجوب، الكلمة المطابقة، نص "لماذا حُجب؟"
   - زر إغلاق، زر "لماذا حُجبت"، رابط "أبلغ عن حجب خاطئ"
4. يبني `BlockedAccessibilityLog`: الحزمة، اسم التطبيق، اسم المُثبِّت، النوع، الكلمة المطابقة + مصدرها (User/Global)، نوع الحدث، viewId، الفئة، focus/checked، هل تطبيق نظام، وقت الحجب.
5. زر الإبلاغ → `POST report-wrongly-blocked` إلى الخادم (حلقة تعلّم/تصحيح للقوائم).

رسائل الأنواع (من `D4/AbstractC0141a.f2221b`):

| النوع | الرسالة |
|---|---|
| 2 | Uninstall Protection is active... / التطبيق محجوب لأنه ضمن قائمتك |
| 3 | تم حجب بحث الصور والفيديو بواسطتك |
| 4 | التطبيق محجوب لأنه في قائمة الحجب |
| 7 | التطبيق محجوب لأنه قد يحتوي محتوى للبالغين |
| 8 | المتصفحات غير المدعومة محجوبة — استخدم Chrome/Firefox/Brave/Opera/DuckDuckGo/Vivaldi/Tor/... |
| 12 | Instagram search |
| 15 | Telegram search |
| 16 | YouTube Shorts |
| 17 | Snapchat Stories & Spotlight |
| 18 | WhatsApp channels |

---

## 4. مترجم الكلمات إلى Regex — `D4/z` (أهم قطعة قابلة للنقل)

`Keyword` = `{ originalKeyword: String, regex: Regex }`.

### 4.1 `z.e(list)` — بناء Regex من نصوص خام
لكل نص:
1. `z.a(s)`: هروب `"`, `.`, `*`, `+` (استبدال `.` بـ `\.` و`*` بـ `\*`... مع تحويل `\.\+` → `.+` و `\.\*` → `.*` — أي أن الوصف الخام يمكن أن يحتوي wildcards صريحة).
2. lowercase، استبدال `[/\-~_:]` بمسافة، ضغط المسافات، trim.
3. حدود الكلمة: إن كان الطول ≠ 3 وفيه `"` → `\b...\b`؛ إن بدأ بـ `x.com` → بادئة `\b`؛ إن كان الطول = 3 → `\b...\b`.
4. `Keyword.from(pattern)` = `Regex(pattern)` (يرمي استثناءً بصمت عند فشل التركيب → يُسقط).
5. **توليد بدائل ترتيب الكلمات**: أي كلمة تحتوي `) (` أو `).*(` تُولّد صيغة معكوسة + نسخة مسافة — لمطابقة العبارات بغضّ النظر عن الترتيب.

### 4.2 `z.b(text, keywords)` — المطابقة مع استخراج الدليل
```java
for (Keyword k : keywords) {
    match = k.regex.find(text);
    if (match == null) continue;
    if (groups > 1) return join(groups);
    if (original.contains("|")) return match.value;      // بدائل: أعد ما طابق فعلاً
    return strip(original, ".*", "\\");                  // وإلا أعد النص الأصلي
}
return null;
```
**عائد الدالة هو "الكلمة التي سبّبت الحجب"** — تُعرض للمستخدم في شاشة الحجب وتُرسَل مع التقرير. هذا فرق نوعي عن مجرد boolean.

### 4.3 `z.c(text, list<String>)` — مطابقة حزم/أسماء
كل نص في القائمة يُترجم إلى Regex مرة واحدة ويُكاش في `ConcurrentHashMap`. تُستخدم لمطابقة **أسماء الحزم** أيضاً (قائمة المتصفحات، قائمة تطبيقات البالغين...) — أي أن الخادم يستطيع حجب حزمة بنمط Regex لا باسم حرفي.

### 4.4 حيلة قائمة السماح — `K0.c.l`
```java
// خذ كلمات المستخدم الأصلية، ولكل كلمة "محظورة" من الخادم (نوع 3)
// إذا طابقت إحدى كلمات المستخدم، استبدل المقطع المطابق داخل الكلمة المحظورة
// بسلسلة عشوائية 8 محارف a-zA-Z0-9 (مثال: "sex" → "Xa8fK2pQ")
// ثم allowlist = قائمة السماح الخاصة بالمستخدم + الكلمات المحظورة "المشوَّشة"
```
الفائدة: إذا أضاف المستخدم كلمة إلى قائمة السماح تطابق جزئياً كلمة عالمية محظورة، تُعاد كتابة الكلمة العالمية ذاتها في نسخة القائمة حتى لا تُطلق الحجب — بدون تعديل القائمة العالمية نفسها.

### 4.5 تطبيع خاص بـ Telegram
`p196w6.b.s()` (تنظيف اسم الحزمة) و`z.d` يُستخدمان أيضاً لتوحيد أسماء الحزم.

---

## 5. القوائم المصنّفة القادمة من الخادم

`GlobalBlockedItem { id: Int, keyword: String, type: Int }` مخزَّن في Room ويُعاد بناؤه في الذاكرة عبر `D4/j` عند كل تغيير.

| type | التفسير (من مواضع الاستخدام) | الحقل النهائي |
|---|---|---|
| 1 | كلمات مفتاحية عالمية | `f11993Q` (Regex عبر `z.e`) |
| 2 | كلمات/أنماط عناوين URL (مهرَّبة) | `f12008l0` → تُدمج في `f12014r0` |
| 3 | كلمات محظورة تُستخدم لبناء قائمة السماح | `f11995S` |
| 4 | حزم متصفحات إضافية | `f11996T` → `f12020u0` |
| 6 | حزم "محتوى بالغين" | `f11994R` (نوع الحجب 7) |
| 7 | معرّفات view (لعقد تُستثنى/تُفحص) | `f11997U` |
| 8 | أسماء حزم محجوبة (Regex) | `f11999W` (نوع 30) |
| 9 | عبارات بحث صور/فيديو | `f12010n0` |
| 10 | **"حزمة:معرّف شريط العنوان"** مثل `com.android.chrome:id/url_bar` | `a0` قائمة `BrowserDetails` + `Z` أسماء الحزم |
| 11 | حزم OEM (شاشات النظام) | `f12011o0` |
| 13 | معرّفات view لشاشات إلغاء التثبيت | `f12002f0` |
| 14 | أسماء فئات تُتجاهل | `f11998V` |
| 17 | حزم OEM مع وسم `#systemui` | تُدمج في `f12012p0` / `f12013q0` |
| 18 | كلمات فحص محتوى WebView | `X` (نوع 31) |
| 19 | استثناءات WebView | `Y` |
| 20 | أسماء فئات شاشات إلغاء التثبيت | `b0` |
| 21 | قائمة مرتبطة بالنسخة (+ نسخة التطبيق نفسها تُضاف) | `c0` |
| 22 | أسماء فئات إضافية | `f12004h0` |
| 23 / 26 | حزم محجوبة (وسم `#systemui_only` للفصل) | `f12005i0` / `f12006j0` |
| 24 | نصوص شاشات إلغاء التثبيت | `f12001e0` |
| 27 | معرّفات view إضافية | `f12003g0` |
| 28 | Regex واحدة (تُبنى بنمط `DOT_MATCHES_ALL`) | `f12007k0` |
| 29 | حزم تُفحص فيها `:id/admin_name` | `f12000d0` |
| 5, 12, 15, 16 | **غير مستخدمة في الكود المفكّك** (محجوزة/مهجورة) | — |

معالجة `#systemui` / `#systemui_only`: وسوم نصية تُضاف لنهاية الـ keyword لتفصل "حزم واجهة النظام" عن غيرها، وتُزال قبل الاستخدام النهائي.

### قوائم المستخدم (`UserBlockedItem { app_id_or_keyword, type, child_partner_request_type }`)

| type | المعنى | الحقل |
|---|---|---|
| 1 | كلمة/نطاق؛ إن صحّ كـ URL (`https://` + الفحص) → قائمة URL، وإلا كلمة | `f11989M` / `f11987K` |
| 2 | تطبيق محجوب (اسم حزمة حرفي) | `f11988L` |
| 3 | قائمة السماح | `f11990N` |
| 4 | متصفحات إضافية يدعمها المستخدم | `f11991O` |
| 12 | كلمات URL | `f11992P` |

`child_partner_request_type == 2` → تُستثنى (طلب ابن بانتظار موافقة الوالد).

### التركيب النهائي
```java
f12016s0 = dedupe(f11987K + f11989M + f11993Q);   // القائمة "العالمية" للفحص
f12018t0 = dedupe(f11987K + f11993Q);              // نسخة بدون URL المستخدم
f12020u0 = dedupe(f11991O + f11996T);              // المتصفحات
f12022v0 = allowlistBuilder(f11990N, f11995S);     // قائمة السماح (بعد التشويش)
f12014r0 = dedupe(f11992P + f12008l0);             // كلمات URL
```

### حقن أنماط المنصات في القائمة العالمية — `K0.c.m`
```java
if (KEY_IS_YOUTUBE_SHORTS_BLOCKED)      add "m.youtube.com/shorts"
if (KEY_IS_INSTAGRAM_REELS_BLOCKED)     add "instagram.com/reels"
if (KEY_IS_SNAPCHAT_STORIES_BLOCKED)    add "snapchat.com/discover", "snapchat.com/spotlight"
else remove
```
هذه الطريقة تجعل حجب Shorts/Reels/Spotlight يعمل **داخل المتصفح أيضاً** (لأن هذه الروابط تظهر نصاً في شريط العنوان/الصفحة)، وليس داخل التطبيقات فقط.

---

## 6. المتصفحات و SafeSearch

### 6.1 المتصفحات المدعومة (مضمّنة)
```
com.android.chrome, com.chrome.canary, com.chrome.beta,
org.mozilla.firefox, org.mozilla.rocket, com.brave.browser,
com.opera.browser, com.opera.mini.native, com.duckduckgo.mobile.android,
com.vivaldi.browser, org.torproject.torbrowser, com.nationaledtech.spinbrowser,
com.ecosia.android, com.hsv.freeadblockerbrowser,
idm.internet.download.manager.plus, com.instantbits.cast.webvideo
```

### 6.2 قائمة "حزمة:معرّف شريط العنوان" (نوع 10 من الخادم)
الخادم يرسل مثلاً `com.android.chrome:id/url_bar`. الكود يقسم على `:id/` ويبني `BrowserDetails(packageName, urlBarId)`، مع حالة خاصة: `com.netflix.mediaclient` → `android:id/search_src_text`.
الفائدة: لا حاجة لتخمين أي عقدة هي شريط العنوان — تُقرأ بالمعرّف مباشرة عبر `findAccessibilityNodeInfosByViewId`.

### 6.3 فرض SafeSearch (إعادة توجيه URL)
عند `KEY_IS_FILTER_SEARCH_RESULTS_ENABLED` (default **true**)، إذا كان المضيف محرك بحث معروفاً وبحثه بلا معامل الأمان:

| المضيف | المعامل | القيمة |
|---|---|---|
| google.com / www.google.com | `safe` | `active` |
| bing.com / www.bing.com | `adlt` | `strict` |
| ecosia.org / www.ecosia.org | `sfs` | `true` |
| qwant.com / www.qwant.com | `s` | `2` |
| duckduckgo.com | `kp` | `1` |
| yandex.com | `family` | `yes` |
| search.yahoo.com + كل النطاقات الإقليمية (in./video./images.) | `vm` | `r` |
| search.brave.com | إعادة كتابة المضيف إلى `safe.search.brave.com` | — |

التنفيذ: `Intent(ACTION_VIEW, safeUrl)` + `setPackage(pkg)` + `putExtra("com.android.browser.application_id", pkg)` + flag `NEW_TASK` — أي **إعادة فتح الرابط الآمن في نفس المتصفح**، مع علم `f12024w0` لمنع الحلقة. (في google.com يشترط أن يكون المسار `/search` ويبني `?safe=active&` قبل استعلام المستخدم.)

### 6.4 حجب بحث الصور والفيديو
Regex-ان مضمّنان:
```java
imgvid = "imghp|images|footage|imagens|imagenes|imágenes|immagini|картинки|চিত্রকল্প|videos|vidéos|vídeos|видео|तस्वीरें|इमेज|वीडियो|ভিডিও|व्हिडिओ|વીડિયો|വീഡിയോകൾ"
results = "image result for|de recherche d images pour|related image|image associée"
```
شرطان: النص < 20 محرفاً للبحث، أو < 60 لعناوين النتائج. يُضبط رمز داخلي 25/26/27 ويُحجب بنوع 3.

---

## 7. الاستمرارية ومنع الإلغاء

### 7.1 Device Admin
`device_admin.xml` بسيط بلا سياسات (`<uses-policies/>`) — الدور الوحيد: **جعل إلغاء التثبيت يستلزم إلغاء تفعيل المدير أولاً** (تعقيد إضافي للمستخدم/الطفل). الكود يتعامل بحذر: إذا كانت الفئة `DeviceAdminAdd` يتوقف عن الحجب حتى لا يحجب شاشة النظام نفسها.

### 7.2 كاشف شاشات إلغاء التثبيت — `D4/A.a`
دالة ضخمة (394 سطراً) تكشف شاشة إلغاء التثبيت عبر عشرات الـ heuristics حسب OEM، وتُرجع سبباً فرعياً 1..23 مخزّناً في `f11985I` (يُرسل مع السجل):
- Samsung: `com.samsung.android.spay` شاشة `FrameLayout` + حدث 32
- Google Play: `com.android.vending` + فئات `MainActivity`/`DrawerLayout`
- Google app: `GoogleAppActivity`
- Settings/MIUI: `android.widget.Button` + حدث 1، أو `TextView`/`FrameLayout` + حدث 2048؛ `com.miui.securitycenter` مع `card_list`/`AppManagerMainActivity`
- Launcher: سحب الأيقونة `:id/delete_icon_container`، `txt_uninstall_main_title`، `checkBox_show_freeze_badge`
- شاشات مدير التطبيقات: `app_manager_details_appversion` + `app_manager_details_applabel`
- Oppo/Realme/Motorola/Transsion: قواعد خاصة بقوائم الخادم (`f12005i0`، `f12006j0`)
- Regex مخصص من الإعدادات `KEY_CUSTOM_BLOCKED_OPTION_IN_UNINSTALL_PROTECTION`

عند الكشف: إن كانت الحماية نشطة `d().u()` → BACK + حجب نوع **2**؛ وإلا → طبقة تذكير `prevent_uninstall_reminder` ("Protect now") بحد أقصى مرة/5 دقائق وليس قبل 3 دقائق من بدء الخدمة.

`d().u()` = (مفتاح Premium لكل مستخدم `com.blockerhero.KEY_IS_PREMIUM` **أو** نافذة حماية مؤقتة `KEY_UNINSTALL_PROTECTION_STARTED_AT`/`_EXPIRE_AT`) **و** الآن داخل النافذة **و** `M1.J(context)` (تحقق تفعيل إمكانية الوصول).

### 7.3 التطبيقات الجديدة
`PACKAGE_ADDED` (مع التحقق `firstInstallTime == lastUpdateTime` أي تثبيت جديد لا تحديث):
إن `KEY_IS_NEWLY_INSTALLED_APPS_BLOCKED` + Premium + لا يطابق Regex `KEY_DO_NOT_BLOCK_THESE_NEWLY_INSTALLED_APPS` → **إضافة تلقائية إلى قائمة حجب المستخدم** ومزامنتها للخادم. `PACKAGE_REMOVED` → حذف من القائمة.

---

## 8. السحابة والتحكم الآبائي

### 8.1 API (Retrofit، `X3/b`) — Base: `https://api.blockerhero.com/`
| Endpoint | الوظيفة |
|---|---|
| `GET blacklist/global?from=` | قائمة الحجب العالمية (delta من نسخة) |
| `GET blacklist/global-guest?from=` | نفسها لغير المسجلين |
| `GET/POST blacklist`, `blacklist/add`, `blacklist/delete` | قائمة المستخدم |
| `GET/POST user-preferences`, `user-preferences/add` | إعدادات |
| `GET/POST/DELETE focus-modes` | أوضاع التركيز |
| `POST auth/login/google` | دخول بـ id_token |
| `POST get-settings-for-update` (code) | **جلب إعدادات يفرضها الوالد** |
| `POST accountability-partner/update` | ربط شريك المساءلة |
| `POST accountability-partner/child-request` | طلب الابن |
| `POST accountability-partner/parent-response` | رد الوالد |
| `POST report-wrongly-blocked` | تقرير حجب خاطئ (BlockedAccessibilityLog) |
| `GET user/subscriptions`, `POST purchase/validate` | الاشتراكات |

### 8.2 FCM (`FCMService.g`) — أنواع الرسائل
`TYPE_SYNC_GLOBAL_BLOCKLIST`, `TYPE_CALL_GLOBAL_BLOCKLIST_API`, `TYPE_SYNC_USER_BLOCKLIST`, `TYPE_CALL_USER_BLOCKLIST_API`, `TYPE_SYNC_FOCUS_MODE`, `TYPE_CALL_FOCUS_MODE_API`, `TYPE_SYNC_SUBSCRIPTION`, `NOTIFICATION_TYPE_CALL_USER_SUBS_API`, `TYPE_UPDATE_USER_PREFERENCES`, `TYPE_UPDATE_USER`, `TYPE_DELETE_ACCOUNT`, `TYPE_RESET_FOCUS_MODE`, `TYPE_SYNC_BLOCKED_ITEM`.
الأزرار: `ACTION_ACCEPT` / `ACTION_REJECT` (طلبات الشريك)، `ACTION_OPEN_URL`، `ACTION_OPEN_MAIN_ACTIVITY`.

### 8.3 التحكم الآبائي (Accountability Partner)
- الوالد يفرض مفاتيح مباشرة: `KEY_IS_PORN_BLOCKER_ENABLED=true`، `KEY_IS_UNSUPPORTED_BROWSERS_BLOCKED=true` (`p084j4/d`).
- `get-settings-for-update(code)` يعيد `List<Preference>{key, value, type}` تُطبَّق عبر `Y3.b.v()` (string/int/long/boolean).
- لكل مفتاح: `KEY_..._PARTNER_REQ_TIME_MILLIS` + إمكانية إخفاء الميزة عن واجهة الابن عبر `KEY_..._VISIBILITY` (افتراضياً ظاهرة إلا «Filter search results»).
- `KEY_MYSELF_PARTNER_ATTEMPTS_LEFT` = محاولات متبقية للابن.

### 8.4 مفاتيح الميزات (كاملة، من `A3/f` + `p148r3/A`)
| المفتاح | الميزة |
|---|---|
| `KEY_IS_PORN_BLOCKER_ENABLED` | Block adult content |
| `KEY_IS_FILTER_SEARCH_RESULTS_ENABLED` (default true) | Filter search results (SafeSearch عبر URL) |
| `KEY_IS_IMG_VID_SEARCH_BLOCKED` | Block image & video search |
| `KEY_IS_INSTAGRAM_REELS_BLOCKED` | Block Instagram Reels |
| `KEY_IS_INSTAGRAM_SEARCH_BLOCKED` | Block Instagram search bar |
| `KEY_IS_YOUTUBE_SHORTS_BLOCKED` | Block YouTube Shorts |
| `KEY_IS_WHATSAPP_CHANNELS_BLOCKED` | Block WhatsApp channels |
| `KEY_IS_TELEGRAM_SEARCH_BLOCKED` | Block Telegram search |
| `KEY_IS_SNAPCHAT_STORIES_BLOCKED` | Block Snapchat Stories & Spotlight |
| `KEY_IS_UNSUPPORTED_BROWSERS_BLOCKED` | Block unsupported browsers |
| `KEY_IS_NEWLY_INSTALLED_APPS_BLOCKED` | Auto-block newly installed apps |
| `KEY_CUSTOM_BLOCKED_SCREEN_MESSAGE` / `KEY_BLOCKED_SCREEN_COUNTDOWN_SECONDS` / `KEY_CUSTOM_REDIRECT_URL_ON_BLOCK` | تخصيص شاشة الحجب |
| `KEY_IS_DEBUGGING_TAB_ENABLED` / `KEY_IS_DEBUGGING_ENABLED` | أدوات دعم فني |
| `KEY_IS_PREMIUM` (لكل مستخدم) | Premium |

### 8.5 الأداء والحماية
- `IS_GLOBAL_BLOCKLIST_FETCHED_ONCE18` / `IS_USER_BLOCKLIST_FETCHED_ONCE4` / `IS_USER_PREFERENCES_FETCHED_ONCE2` — لمرة واحدة لتفادي طلبات متكررة.
- إذا فشل أي شيء: `S8.c.r().a(e)` (تسجيل أخطاء سحابي) — التطبيق لا يسقط.
- `KEY_IS_ACCESSIBILITY_STARTED_AT` — يُستخدم لتأخير تذكيرات الحماية 3 دقائق.
- حالة "عدم إلغاء التثبيت" تُفحص في شاشة الحجب أيضاً (زر "Why blocked" يظهر نوع 2).

---

## 9. تقييم صريح: قوة BlockerHero مقابل Warden

### ما يفعله BlockerHero أفضل من Warden اليوم
1. **كشف المنصات بمعرّفات الواجهة** (`clips_tab`, `reel_recycler`, `ngs_spotlight_icon_container`, نشاط قنوات واتساب) — دقة أعلى بكثير من نصوص التحذير العامة.
2. **قراءة شريط عنوان المتصفح بمعرّف محدد لكل متصفح** (جدول من الخادم) بدل مسح أي نص يشبه URL.
3. **فرض SafeSearch بإعادة توجيه URL** — يعمل حتى على محركات لا تدعم DNS آمن (Yandex/Qwant/Ecosia/Yahoo).
4. **حجب بحث الصور والفيديو** بعبارات متعددة اللغات.
5. **حقن أنماط روابط Shorts/Reels/Spotlight** في قائمة الكلمات — يغطي المتصفح أيضاً.
6. **مترجم كلمات→Regex مع حدود وترتيب عشوائي** + إعادة "الكلمة المطابقة" للعرض والتقرير.
7. **قوائم خادم مصنّفة (29 نوعاً) بمعنى تشغيلي لكل نوع** + مزامنة delta.
8. **قائمة سماح بتشويش عشوائي** لمنع تعارض السماح مع الحجب.
9. **كاشف شاشات إلغاء التثبيت متعدد OEM** + حماية Device Admin.
10. **تحكم آبائي عن بعد** + إخفاء الميزات + تقرير حجب خاطئ يعود للخادم.

### ثغرات في نهج BlockerHero (لا تنقلها)
1. لا VPN/DNS → أي مسار لا يمر بالـ accessibility (مثلاً محتوى داخل مشغل فيديو مخصص، أو WebView لا يُطلق أحداثاً) قد يفلت.
2. لا فحص صور/NSFW → وسائط بالغة بلا نصوص أو view-ids لا تُلتقط (Warden يملك TFLite وهذا تفوق حقيقي).
3. الكلمات العالمية على الأرجح إنجليزية؛ التغطية العربية في القوائم غير مؤكدة (لم نجد قوائم مضمّنة — كلها من الخادم).
4. اعتماد كامل على الخادم للقوائم الأولى (`IS_GLOBAL_BLOCKLIST_FETCHED_ONCE18`) — بلا شبكة أول تشغيل = لا حجب.
5. بعض الـ heuristics هشّة (Google quicksearchbox، شاشات OEM تتغير).
6. لا حدود استخدام/جداول زمنية متقدمة (فقط Focus Mode) — Warden أقوى هنا.

**الخلاصة:** الجمع بين محرك Warden (VPN DNS + TFLite + جداول + حدود) لطبقات الشبكة/الصور، ومحرك BlockerHero (view-id + URL bar + SafeSearch URL + keyword-regex + قوائم مصنّفة) لطبقة الشاشة/التطبيقات، يعطي تغطية أعلى بكثير من كل منهما منفرداً.

---

## 10. خطة النقل إلى Warden — مراحل بملفات محددة

> الملفات المرجعية في مشروعك: `app/src/main/java/com/warden/blocker/…`
> المبدأ: لا نستبدل محركك بل نضيف طبقة "كشف دقيق" فوقه، ونوسّع مصدر القوائم.

### المرحلة 1 — محرك الكلمات (Regex + تطبيع + سماح)
**ملف جديد:** `block/KeywordEngine.kt`
```kotlin
data class BlockKeyword(val original: String, val regex: Regex)

object KeywordEngine {
    // 1) compile(raw): port D4.z.a + z.e
    fun compile(list: List<String>): List<BlockKeyword>
    // 2) match(text, keywords): يعيد الكلمة المطابقة (String?) — port D4.z.b
    fun match(text: String, keywords: List<BlockKeyword>): String?
    // 3) matches(text, patterns: List<String>): Boolean — port D4.z.c بكاش Regex
    fun matches(text: String, patterns: Collection<String>): Boolean
    // 4) normalize(raw): port D4.z.d (punctuation strip, %20/%23, lowercase, collapse)
    fun normalize(raw: String?): String?
    // 5) buildAllowlist(userAllow: List<String>, serverBanned: List<BlockKeyword>): List<BlockKeyword>
    //    port K0.c.l (تشويش عشوائي 8 محارف)
}
```
- أضف `BlockKeyword` إلى `block/` واستخدمه في `SocialContentDetector` (استبدال `NSFW_TOKEN`/`EXPLICIT_SEARCH_PATTERNS` المضمّنة بقوائم قابلة للتحديث + احتفظ بالعربية من Warden).
- طبيعية `z.d` (حذف الترقيم) **تفوق** تطبيع Warden الحالي (NFKC فقط) — أضفها.
- أضف حدود كلمة `\b` للكلمات القصيرة (≤3) و`\b` لبادئات مثل `x.com`.

### المرحلة 2 — كاشفات واجهات المنصات (أكبر مكسب للسوشل)
**توسيع:** `feature/SocialPlatform.kt` أو `feature/FeatureCatalog.kt`
```kotlin
data class PlatformSignal(
    val viewIds: List<String> = emptyList(),        // contains
    val classes: List<String> = emptyList(),        // equals/contains
    val textEquals: List<String> = emptyList(),     // نصوص محلية (Explore/Search...)
    val textContains: List<String> = emptyList(),
    val requireSelected: Boolean = false,
    val requireVisible: Boolean = false,
)
```
أضف لكل منصة (القيم الحرفية من BlockerHero):
- **Instagram/Lite**: `clips_tab` (selected), `like_button` (visible), `peek_container`+`indicator_container`, `action_bar_title` ∈ {Explore, Explorar, Explorer, Entdecken, استكشاف, …}; search: `search_tab`, `action_bar_search_edit_text`, `search_bar_real_field`.
- **Facebook/Lite**: `contentDesc="View reels"`, class `com.facebook.fbshorts.viewer.activity.FbShortsViewerActivity`, نصوص `FbShortsComposer`, `Navigate to your Reels profile`, `Add comment...`.
- **YouTube + `com.vanced.android.youtube` + `app.revanced.android.youtube`**: `reel_recycler`.
- **Snapchat**: `ngs_spotlight_icon_container`, `ngs_community_icon_container`.
- **WhatsApp + `com.whatsapp.w4b`**: class `com.whatsapp.newsletter.directory.ui.NewsletterDirectoryCategoriesActivity`.
- **Telegram/Challegram** (`Regex("telegram|challegram")`): class `EditText` + نص ∈ قائمة "Search" بـ 29 لغة (انسخها من `D4/AbstractC0141a.f2222c`).
- **WebView**: class `android.webkit.WebView` → افحص النص بالقوائم العالمية مع استثناء `Y`.

في `AppBlockAccessibilityService.maybeScanInAppContent`:
```kotlin
val platformHit = SocialPlatform.byPackage(pkg)?.let { SocialContentDetector.detect(it, root) }
// أضف قبل الحجب:
if (platformHit != null) performGlobalAction(GLOBAL_ACTION_BACK) // كما يفعل BlockerHero
```
**ملاحظة حرجة:** أضف `typeViewClicked|typeViewLongClicked|typeViewFocused` و`flagIncludeNotImportantViews` إلى `res/xml/accessibility_service_config.xml`؛ بدونهما لن تصل أحداث فتح شريط البحث/التبويب في بعض المنصات.

### المرحلة 3 — المتصفحات: URL bar + SafeSearch + صور/فيديو
**تعديل:** `accessibility/BrowserUrlMonitor.kt`
1. جدول `mapOf(pkg to urlBarId)` (نفس فكرة `a0`):
   - Chrome: `com.android.chrome:id/url_bar`
   - Firefox: `org.mozilla.firefox:id/url_bar_title` / `:id/url_bar`
   - Samsung: `com.sec.android.app.sbrowser:id/url_bar`
   - Brave/Vivaldi/Opera/DDG/Edge: معرّفاتها المقابلة (أضف fallback: أول `EditText` نصه URL صالح).
2. اقرأ النص فقط من شريط العنوان أولاً؛ وإن غاب fallback للمسح الحالي (لا تحذفه — مفيد للمتصفحات غير المعروفة).
3. **المتصفحات غير المدعومة**: `resolveActivity(ACTION_VIEW, "http://www.google.com")` ومقارنة بالحزمة — مكاش (`Map<String, Boolean>`). سلوك: مفتاح إعداد جديد `blockUnsupportedBrowsers`؛ مفعّل → حجب/تذكير (رسالة من Warden).
4. **SafeSearch URL**: أضف إلى `block/SafeSearchHosts.kt` خريطة `host → (param, value)` واحتفظ بخريطة DNS الحالية. عند اكتشاف محرك بحث بلا معامل → `startActivity(VIEW, safeUrl, package=pkg)` + علم in-flight لمدة ثانية. غطِّ: google(safe=active), bing(adlt=strict), ecosia(sfs=true), qwant(s=2), duckduckgo(kp=1), yandex(family=yes), yahoo(vm=r), brave(→safe.search.brave.com).
5. **حجب بحث الصور/الفيديو**: انسخ Regex-ي `y.f2295a`/`y.f2296b` واعرض الحجب عند مطابقة نص شريط العنوان (طول <20/<60).

### المرحلة 4 — القوائم المصنّفة (بديل AdultListFetcher)
**توسيع:** `security/AdultListFetcher.kt` → `security/BlocklistFetcher.kt`
- صيغة JSON داخلية بنفس فكرة الأنواع: `[{keyword, type}]` بأنواع فرعية على الأقل:
  - `1` كلمات عالمية, `2` كلمات URL, `6` حزم بالغين, `8` حزم محجوبة, `9` عبارات بحث صور/فيديو, `10` "حزمة:viewId" شريط عنوان, `17/23` حزم OEM/تطبيقات.
- إن كان لديك خادم لاحقاً: `GET /blocklist?from=<version>` (delta) — نفس تصميم BlockerHero مع `IS_..._FETCHED_ONCE` لتفادي التكرار.
- إن لم يكن: ابدأ بملف JSON مضمّن في `assets/` (بذرة) + التحديث من الـ fetcher الحالي.

### المرحلة 5 — شاشة الحجب والاستمرارية
1. **BACK قبل العرض**: في `launchIntercept` أضف `performGlobalAction(GLOBAL_ACTION_BACK)` قبل `startActivity` لأنواع `KIND_SOCIAL`/`KIND_FEATURE` (يمنع رؤية المحتوى لثانية). احتفظ بـ InterceptActivity الحالي بدل overlay خام (أفضل من ناحية الأمان والتوافق).
2. **إعدادات جديدة** في `SettingsStore`: `blockedScreenCountdownSeconds`, `customBlockedMessage`, `customRedirectUrlOnBlock`, `blockUnsupportedBrowsers`, `blockImgVidSearch`, `blockNewInstalls`, `blockNewInstallsExemptRegex`.
3. **منع إلغاء التثبيت**: `AdminManager` موجود — أضف `security/UninstallGuard.kt` بنسخة مبسطة من `D4/A.a`: كشف حزم الإعدادات/المشغّل النظامية + `delete_icon_container` + `txt_uninstall_main_title` + `app_manager_details_*` + شاشة `DeviceAdminAdd` (تجاهل). عند الكشف: BACK + InterceptActivity بنوع "uninstall protection" (أو تذكير "Protect now" إن لم يكن مفعّلاً).
4. **التطبيقات الجديدة**: أضف `receiver/AppInstallReceiver.kt` على `PACKAGE_ADDED/REMOVED` بنفس تحقق `firstInstallTime == lastUpdateTime` + استثناء Regex.
5. **تقرير الحجب**: أضف زر "أبلغ عن حجب خاطئ" في InterceptActivity يحفظ سجلاً محلياً الآن (Room)، وقابل للإرسال لاحقاً إلى خادمك.

### المرحلة 6 — أداء وتغطية
- سقف 25 رمزاً لنص الحدث + سقف 250 محرفاً للنص المطبَّع (كما يفعل BlockerHero) لتقليل التكلفة.
- `notificationTimeout=100` (موجود) + `flagIncludeNotImportantViews` + أنواع الأحداث الإضافية.
- كاش `CACHE_TTL_MS` موجود؛ أضف كاش `compiled regex` (ConcurrentHashMap) كما في `z.a`.
- اسحب `SocialContentDetector` من المسار الساخن إلى `Dispatchers.Default` (موجود) مع `scanInFlight` (موجود).

### أولوية التنفيذ المقترحة
| # | العمل | الأثر | الجهد |
|---|---|---|---|
| 1 | كاشفات view-id للمنصات (المرحلة 2) | ★★★★★ (جوهر الشكوى) | متوسط |
| 2 | URL bar لكل متصفح + حجب المتصفحات غير المدعومة (3.1-3.3) | ★★★★☆ | متوسط |
| 3 | KeywordEngine + قائمة السماح (1) | ★★★★☆ | منخفض |
| 4 | SafeSearch URL + بحث الصور/الفيديو (3.4-3.5) | ★★★☆☆ | منخفض |
| 5 | حقن روابط Shorts/Reels/Spotlight (K0.c.m) | ★★★☆☆ | منخفض |
| 6 | منع إلغاء التثبيت + التطبيقات الجديدة (5.3-5.4) | ★★☆☆☆ | متوسط |
| 7 | قوائم مصنّفة/مزامنة (4) | ★★★★☆ (على المدى الطويل) | مرتفع |

---

## 11. ما لم نتمكن من إثباته (Unknowns)

1. **محتوى القوائم نفسها**: كل الكلمات/الحزم تأتي من الخادم — لا توجد قوائم مضمّنة في APK. لم نتمكن من رؤية أي كلمة فعلية (لا يمكن استخراجها من الكود).
2. **معنى `from`**: يُرسل كـ `@Query("from")` لكن نقطة حساب القيمة الأصلية لم تُحدَّد بدقة (مرشّح قوي: نسخة مخزّنة في SharedPreferences). الأنواع 20-29 جزئية الاستخدام.
3. **`libafe2.so`** في split arm64: مكتبة أصلية (1.67MB) لم تُحلَّل — غير مؤكد إن كانت لحماية/كشف root أو SDK طرف ثالث.
4. **دوال لم تُفكّك**: `D4/t.p`, `FCMService.g` (jadx overflow) — سلوكها مُستنتج من الأسماء والاستدعاءات لا من الكود.
5. **خادم API**: لا فحص حي — المسارات والأنواع من الكود فقط؛ صلاحية/توقيع الردود غير معروفة.
6. **التوقيع**: لم يُتحقق من صحة APK أمام digest منشور.

---

## 12. قائمة الأدلة (أين يوجد كل شيء بعد التفكيك)

| ما تبحث عنه | الملف |
|---|---|
| الخدمة/الدماغ | `jadx_src/sources/com/blockerhero/services/MyAccessibilityService.java` |
| كاشفات المنصات | `.../D4/r.java` (r.h/i/k/l/m/n) |
| كشف إلغاء التثبيت | `.../D4/A.java` |
| التطبيع والمطابقة | `.../D4/z.java` |
| محركات البحث (SafeSearch) | `.../D4/x.java` + `.../D4/w.java` |
| regex بحث الصور/الفيديو | `.../D4/y.java` |
| قائمة السماح المشوَّشة + حقن URLs | `K0/c.java` (`l` و `m`) |
| بناء القوائم من الخادم | `D4/j.java` (أنواع 1-29) |
| قوائم المستخدم | `D4/h.java` |
| عرض الحجب + السجل | `K6/u0.java` (`u`) |
| إعدادات/مفاتيح | `Y3/b.java` + `p148r3/A.java` + `A3/f.java` |
| API | `X3/b.java` (base في `U3/a.java:148`) |
| FCM | `.../services/fcm/FCMService.java` |
| قوائم المتصفحات/النصوص | `D4/AbstractC0141a.java` |
| المانيفست | `apktool_out/AndroidManifest.xml` |
| إعداد الخدمة | `apktool_out/res/xml/accessibility_xml.xml` |
| النصوص (رسائل الحجب) | `apktool_out/res/values/strings.xml` |
| smali (احتياطي) | `smali_out/…` |

---

*أُعدّ هذا التقرير بتحليل ساكن فقط. لم يُشغَّل التطبيق، ولم تُرسَل أي بيانات لخوادم أي طرف. جميع أسماء الحزم/المعرّفات المذكورة مأخوذة حرفياً من الكود المفكّك.*
