# تقرير هندسة عكسية — Block (App & Site Blocker) 11.3.0
### تحليل معماري كامل + خطة دمج نهائية (أفكار BlockerHero + Block → Warden)

> **الهدف:** `com.wverlaek.block` — الإصدار 11.3.0 (versionCode 97)
> **الملف:** `Block-App-Site-Blocker_11.3.0_apkcube.apk` (11.6MB، classes.dex = 11.2MB + 2 dex صغيرة، 14,243 فئة)
> **المنهجية:** تحليل ساكن فقط (لم يُشغَّل ولم يُثبَّت). فكّ كامل عبر jadx (نجح بمعظمه، بعض الدوال الكبيرة لم تُفكّ)، موارد عبر Apktool، مانيفست عبر aapt.
> **ملاحظة معمارية مهمة:** كود التطبيق نفسه غير مشوّش تقريباً (`block.*`) — فقط المكتبات الثالثة مشوّشة (`defpackage.*`). هذا أعطى قراءة نظيفة جداً للمكوّنات الأساسية.
> **التوقيع:** لم يُتحقق من digest منشور.

---

## 1. الهوية والمكوّنات

| البند | القيمة |
|---|---|
| Package | `com.wverlaek.block` (البنية الداخلية `block.*`) |
| versionName / Code | 11.3.0 / 97 |
| minSdk / targetSdk | 23 / 35 |
| الأذونات | `PACKAGE_USAGE_STATS`، `BIND_ACCESSIBILITY_SERVICE`، `QUERY_ALL_PACKAGES`، `SYSTEM_ALERT_WINDOW`، `REQUEST_DELETE_PACKAGES`، `SCHEDULE_EXACT_ALARM`، **`NFC`**، `FOREGROUND_SERVICE_SPECIAL_USE`، `POST_NOTIFICATIONS`، `BILLING` |
| **لا يوجد** | `BIND_VPN_SERVICE` — لا طبقة VPN/DNS هنا أيضاً |

### المكونات الرئيسية

| المكوّن | الدور |
|---|---|
| `block.libraries.fgappscanner.accessibility.FGAccessibilityService` | كشف الويب + ميزات التطبيقات (شريط العنوان/عناصر الواجهة) |
| `block.libraries.blocks.execution.service.BlockerForegroundService` | خدمة أمامية (SPECIAL_USE) تعرض إشعار الحالة |
| `block.libraries.blocks.blockscreen.*` | شاشة الحجب: `TransparentCloseAppDialog` + `CloseSystemDialogReceiver` |
| `block.libraries.blocks.reboot.BootReceiver` + `ActivateAfterRebootNotificationActionReceiver` | إعادة تفعيل الحجب بعد الريبوت (+ كشف force-stop) |
| `block.libraries.blocks.scheduling.BlockActivationAlarmReceiver` + `ExactAlarmReminderReceiver` | تفعيل/انتهاء الجداول بمواعيد دقيقة |
| `com.wverlaek.block.features.detection.notifications.NotificationListener` | **إزالة إشعارات التطبيقات المحجوبة** |
| `block.libraries.nfc.NfcScanActivity` | مسح NFC لإلغاء الإيقاف |
| `com.wverlaek.block.tasker.*` | تكامل Tasker |
| `block.libraries.widget.*` + `block.features.usage/reports` | ويدجت وقت الشاشة + تقارير أسبوعية/سنوية |
| `block.libraries.pin.LockScreenActivity` / `RequiresPinActivity` | PIN لحماية الإعدادات |
| `dev.doubledot.doki` | "Don't kill my app" دليل إبقاء الخدمة |

### إعداد خدمة الوصول
```xml
accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged"
accessibilityFeedbackType="feedbackVisual" notificationTimeout="300"
accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows"
canRetrieveWindowContent="true"
```
لاحظ `typeWindowsChanged` — لدعم تعدد النوافذ (تقسيم الشاشة/النوافذ العائمة).

---

## 2. الفكرة المعمارية المحورية: **فصل طبقتي الكشف**

| الطبقة | المصدر | تُكشف |
|---|---|---|
| **التطبيقات** (فتح/استخدام/حدود/فتحات) | `UsageStatsManager.queryEvents` (polling + Reflection لـ API التوافق، `nk6/p53`) | فتح تطبيق محجوب، حد استخدام، حد فتحات |
| **الويب + ميزات التطبيق** | AccessibilityService | دومين في شريط العنوان، Reels/Shorts/Spotlight/Updates |

هذا يقلّل الحمل على خدمة الوصول (الأحداث للنص فقط عند الحاجة) ويعطي دقة أعلى للتطبيقات. الأحداث الموحدة (`ej6 {pkg, target, type:"web"/"app_feature"}`) تُدفع إلى **مقيّم مركزي** (`tx6.f`) يفحص القيود النشطة ويقرر.

---

## 3. محرك الويب — الأنضج قطعة في التطبيق (`FGAccessibilityService`)

### 3.1 قاعدة بيانات المتصفحات (`cg0` = `BrowserConfig`)
~**75 متصفحاً** مشفّراً داخلياً كـ `{b: package, u: "viewId1,viewId2", cd: fallback}` — قائمة منسوخة أدناه (الأهم):

| المتصفح | معرفات شريط العنوان (مرشحون) | fallback |
|---|---|---|
| Chrome (+beta/canary/dev/GoogleApps) | `origin,url_bar` | ✗ |
| Firefox family (Fenix/IceRaven/ForkClient) | `mozac_browser_toolbar_url_view` | ✓ (بحث شجري) |
| Brave (+beta/dev/nightly) | `url_bar` | ✗ |
| DuckDuckGo | `omnibarTextInput` | ✗ |
| Samsung (`com.sec.android.app.sbrowser`) | `location_bar_edit_text` | ✗ |
| Opera/Mini/GX/Touch | `url_field` / `addressbarEdit` | ✗ |
| Edge (كل القنوات) | `url_bar` | ✗ |
| Vivaldi/Qwant/Ecosia/Kiwi/Via/Bromite/Cromite/Mull/… | معرّفاتها الخاصة | بعضها ✓ |
| Yandex | `bro_omnibar_address_title_text,bro_omnibox_collapsed_title` + **محوّل نص مخصص** | ✗ |
| 1DM / IDM+ | `search` | ✗ |

**خصائص ذكية:**
- **مرشحون متعددون**: Chrome يجرّب `origin` ثم `url_bar`.
- **`cd` (raw-view-id fallback)**: لبعض المتصفحات (عائلة mozac) إن فشل `findAccessibilityNodeInfosByViewId` → **جولة BFS على الشجرة (سقف 2000 عقدة)** بحثاً عن view-id مطابق ضمن مجموعة `d`.
- **محوّل نص لكل متصفح** (`cg0.f`): Yandex يملك محوّلاً خاصاً — التطبيق قابل لتخصيص استخراج النص لكل متصفح.
- **التحديث عن بعد**: الفئة Gson-قابلة للتسلسل `{"b":..,"u":..,"cd":..}` مع **كاش TTL = 600 ثانية** (`qi0(Duration.ofSeconds(600))`) — قاعدة المتصفحات قابلة للتحديث بدون إصدار جديد.
- **قياس نجاح الكشف**: كل محاولة تُبلَّغ عبر `jk2.b(pkg, viewId, success, method)` بأنماط `VIEW_ID` / `RAW_VIEW_ID` / `TYPING` / `NOT_FOUND` — بيانات ميدانية لتحسين الجدول باستمرار.

### 3.2 استخراج URL من العقدة (`i()`)
1. **إن كانت العقدة focused → إيقاف** (المستخدم يكتب الآن؛ لا حجب أثناء الكتابة) — يُبلَّغ كـ `TYPING`.
2. `text` → يمر عبر المحوّل الخاص بالمتصفح.
3. وإلا `contentDescription` → Regex أول `https?://\S+` (يقص عند `.,;!?`)؛ إن لم يوجد → **Regex دومينات عارية**: `[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]...)+(/...)`.
4. **بوابة نهائية `md4`**: النمط الكامل لـ WebAddress/Chromium (دعم IDN/Unicode TLDs + IPv4) — `matches()` على النص كله. نمط عالمي جاهز للأخذ.

### 3.3 حالة الجلسة
- تتبع "الويب الأمامي" لكل حزمة (`y: pkg → domain`) وأحداث دخول/خروج (`"web"` usage events).
- الأحداث: `TYPE_WINDOW_STATE_CHANGED` (32) = انتقال foreground؛ `TYPE_WINDOW_CONTENT_CHANGED` (2048) و`TYPE_WINDOWS_CHANGED` (4194304) = إعادة فحص؛ **بسعة 500ms** بين أحداث نفس الحزمة.
- تجاهل دائم: `gu5.a = {"com.android.systemui"}` فقط (لاحظ: قائمة تجاهل صغيرة — لا قوائم حماية طويلة؛ الحماية تأتي من "لا تكوين" للمتصفح).

### 3.4 إغلاق نوافذ النظام المنبثقة (Popup Closer) — مضاد التفاف
عند حدث من `com.android.systemui` أثناء نافذة "إغلاق منبثق" حديثة:
- جدولة إعادة محاولة: **حتى 5 محاولات كل 2 ثانية** (throttle 200ms، صلاحية 60 ثانية)، قابلة للضبط من الـ remote config (`popup_close_max_retries` وغيرها).
- يجد نافذة `systemui` التي تحمل حزمة الهدف (`getWindows()` → root.packageName == target) ويعرف مستطيلها `getBoundsInScreen`.
- يبحث داخل النوافذ المتقاطعة عن زر الإغلاق عبر **قائمة معرّفات view** ويضغطه (`ACTION_CLICK`)، ويتحقق من اختفاء النافذة بعد كل محاولة.
- **الوظيفة**: قتل حوارات النظام التي "تُحيي" التطبيق المحجوب (مثل "App keeps stopping — Open again" أو "Open with") — ثغرة شائعة في تطبيقات الحجب، مغلقة هنا.
- مكمّل بثنائي: مستقبل بث `CLOSE_SYSTEM_DIALOGS` (`CloseSystemDialogReceiver`) يطلق نفس المنطق.

---

## 4. نظام "ميزات التطبيق" القابل للتحديث (`mp` / `f42` / `bm1`)

```kotlin
// block.libraries.usage.features (Gson: id/packageName/displayName/detection/navigateAwayTo)
data class AppFeature(
  id: String, packageName: String, displayName: String,
  detection: DetectionConfig(viewIds: List<String>),
  navigateAwayTo: List<String>,   // DSL أوامر: "global_action:navigate_back"
)
```

الميزات المضمّنة (5):

| id | الحزمة | عرض | كشف | إغلاق |
|---|---|---|---|---|
| `youtube_shorts` | com.google.android.youtube | Shorts | `reel_watch_fragment_root`, `reel_recycler` | navigate_back |
| `revanced_youtube_shorts` | app.revanced.android.youtube | Shorts | نفسها | navigate_back |
| `instagram_stories` | com.instagram.android | Reels | `clips_viewer_view_pager` | navigate_back |
| `whatsapp_updates` | com.whatsapp | Updates | `updates_list` | navigate_back |
| `snapchat_spotlight` | com.snapchat.android | Spotlight | `spotlight_container` | navigate_back |

**النقاط الذهبية:**
- **التحديث عن بعد بنفس آلية المتصفحات** (Gson + كاش 600s) → إضافة دعم Instagram Lite أو تغيير معرفات النسخ الجديدة **بدون تحديث التطبيق**.
- **`navigateAwayTo` كسلسلة أوامر**: `global_action:navigate_back` — بنية قابلة للتوسعة (ممكن `click:<viewId>` أو `global_action:home` مستقبلاً).
- **الكشف (`kr0.q`)**: لكل viewId → `pkg:id/<id>` → `findAccessibilityNodeInfosByViewId` → **شريط `isVisibleToUser()`** (يمنع كشف عناصر مخفية في الشجرة) → أول ظهور = إصابة.
- **التنفيذ (`th3.j` + `p15`)**: عند حجب ميزة، يرسل للخدمة طلب `p15(appPackage, viewIds, now)` — الخدمة تنفّذ `navigateAwayTo` (BACK) فتغادر شاشة الميزة، ثم تُعرض شاشة الحجب.

---

## 5. تنفيذ الحجب — ثلاث طبقات عرض + إعادة توجيه

### 5.1 المقيّم المركزي (`tx6.f(pra, changed)`)
لكل حدث انتهاك (`pra`):
- `p90` = تطبيق (foreground) | `r90` = موقع (domain) | `q90` = ميزة (featureId)
يجد القيود النشطة المتطابقة (`h50{nz2 restriction, b80 state}` + `zl5.f(restriction, event)`) ويحدد **السبب بترتيب أولوية**:

| الأولوية | حالة القيد | نوع السبب النهائي |
|---|---|---|
| 1 | `bk3` حظر يدوي بوقت انتهاء | `qr0` Manual |
| 2 | `u85` حظر جدول بوقت انتهاء | `rr0` Scheduled |
| 3 | `sp2` حد ساعة: used > limit | `or0` HourlyLimit |
| 4 | `ed1` حد يوم: used > limit | `mr0` DailyLimit |
| 5 | `zp2`/`md1` حد فتحات | `pr0`/`nr0` OpenLimit |

مع **حماية مضادة للطوب** (brick): إن كان الحدث = فتح `com.android.settings` **ولا يوجد أي launcher على الجهاز** → لا حجب + Toast تحذيري (لأن المستخدم لن يستطيع الوصول لأي شيء).

### 5.2 إثراء حدث الويب (`tx6.F`)
عند مطابقة دومين: `zqa.h(restriction, domain)` يرجع `mc0` = الدخيلة: إما **مطابقة دومين** أو **مطابقة كلمة (`lc0`)**، وتُستخرج "الكلمة/القاعدة" (`vqa.g`) → تُعرض في شاشة الحجب وتُرسل analytics (`website_closed` vs `keyword_closed`).

### 5.3 تنفيذ حجب الموقع = **إعادة توجيه داخل المتصفح**
```java
String url = remoteConfig("website_block_redirect_url", "https://blockapps.app/block-screen.html");
// حماية من الحلقة: إن كان الدومين الحالي == دومين صفحة الحجب → لا تحويل
Intent i = new Intent(ACTION_VIEW, uri).setPackage(browserPkg)
        .putExtra("com.android.browser.application_id", browserPkg)
        .addFlags(NEW_TASK | CLEAR_TOP | ...);   // يفتح صفحة الحجب في نفس المتصفح
// + إظهار شاشة الحجب فوقه (hr0.d)
```
فكرة عبقرية: صفحة الحجب تُعرض **داخل المتصفح نفسه** (تجربة طبيعية) مع إبقاء شاشة الحجب فوقها كطبقة.

### 5.4 شاشة الحجب (`hr0` — BlockScreenManager) — **ثلاث طبقات**
1. **Overlay فوري** (الافتراضي، إن توفر `canDrawOverlays` ولم يُعطَّل عن بعد `block_screen_draw_over_screen_disabled`):
   - ComposeView كامل الشاشة عبر WindowManager، type `2038` (أو 2006)، flags شفافة، "Block Screen"، معالجة notch + دوّار الشاشة + ثيم داكن/فاتح + ألوان ديناميكية.
   - **بدون أي تأخير إطلاق نشاط** — تظهر فوق التطبيق المحجوب لحظياً.
2. **Activity شفافة** (fallback): `CloseAppDialog$TransparentCloseAppDialog` مع extras: `blocked_app_pkg`, `blocked_web_closed_by`, `blocked_web_domain_to_query`, `blocked_by_feature_id`, `blocked_reason_text`, `block_name`, `restriction_icon`, `close_delay_sec`؛ واجهة: أيقونة + عنوان + سبب + **اقتباس تحفيزي** (`quotes_enabled`, `quotes_custom`) + أزرار (إغلاق/فتح التطبيق بشرط الإيقاف).
3. **العدّاد الإغلاق** (`close_delay_sec`): يمنع إغلاق الشاشة فوراً؛ العدّاد مشترك عالمياً (`hr0.b`) للثبات عبر الحجبات المتتالية.
4. **كشف الإحباط**: إن واجه المستخدم العدّاد **>3 مرات خلال دقيقتين** (مع سقف شاشة منخفض) → يُظهر تلميحاً (مرة/يوم) لتقصير التأخير — تصميم صحة-رقمية حقيقي.
5. **حجب التطبيق (p90)**: العودة للصفحة الرئيسية (HOME) + الشاشة.
6. **حجب الميزة (q90)**: أمر navigateAway عبر الخدمة + الشاشة.

### 5.5 حماية متعددة النوافذ
`typeWindowsChanged` + جولة على `getWindows()` كلها عند الأحداث — يغطي تقسيم الشاشة (المتصفح في النصف، الموقع المحجوب في النصف الآخر).

---

## 6. الاستمرارية والمتانة (anti-bypass)

1. **`pref_activate_on_reboot`** — "إبقاء الحجب والحدود نشطة بعد إعادة التشغيل" (تحذير لا يمكن إبطاله لقيود نشطة).
2. **BootReceiver الذكي**:
   - إذا افتُح التطبيق قبل الرسالة → تجاهل.
   - Android 15+: `ActivityManager.getHistoricalProcessStartReasons` → **إن كان التطبيق force-stopped قبل الريبوت → لا إعادة تفعيل** (يغلق حيلة force-stop ثم reboot).
   - وإلا: تفعيل وضع "بعد الريبوت" + إشعار "Deactivated blocks after reboot — Press to reactivate".
3. **Strict mode** (خيار إيقاف لكل قيد): "Strict: no pausing" — لا إيقاف/تعديل للقيد النشط؛ **الريبوت هو مخرج الطوارئ** فقط إذا كان `activate_on_reboot` معطلاً.
4. **التعديلات المُجدولة**: "التعديلات التي تُرخي الحجب (إزالة تطبيقات/رفع حدود) تُؤجل لنهاية القيد أو نهاية اليوم" — يعطل التخريب الذاتي الفوري.
5. **Device Admin اختياري**: منع force-close/uninstall.
6. **إشعار الخدمة الأمامية** يعرض الحالة: "No restricted apps" / "%1$s, %2$s restricted" — شفافية مستمرة.
7. **CloseSystemDialogs + Popup closer** (فوق) — إغلاق حوارات النظام.
8. **Don't kill my app** (دoki) مدمج لضبط OEM.

---

## 7. حدود الاستخدام والإيقاف المؤقت

### 7.1 أربعة أنواع حدود
| الحد | الوصف | حالة القيد |
|---|---|---|
| **حد ساعة** | وقت شاشة أقصى/ساعة، بأيام محددة | `sp2{used, limit}` |
| **حد يوم** | وقت شاشة أقصى/يوم | `ed1{used, limit}` |
| **حد فتحات يومي** | كم مرة يُفتح التطبيق/يوم ("reduce constant checking") | `zp2`/`md1` |
| **حد فتحات ساعي** | كم مرة/ساعة | — |

### 7.2 تذكيرات الحدود (قبل الحجب الصلب!)
`pref_limit_reminder_notifications`: "عند الاقتراب من الحد أو الانتقال إلى تطبيق ذي حد" / "فقط عند الاقتراب" / "أبداً" — تنبيهات لينة تُخفف الاحتكاك.

### 7.3 خيارات الإيقاف المؤقت (Pause options) — لكل قيد
- **Delay**: انتظار عدّاد قبل الإيقاف.
- **Type text**: كتابة جملة محددة (احتكاك مقصود).
- **Scan QR/barcode** (ML Kit): مسح كود مُختار مسبقاً.
- **Scan NFC tag**: مسم على **NFC tag فيزيائي** (كُل طفل/النفس الذات — المفتاح بعيد).
- **Strict**: لا إيقاف إطلاقاً.
(مزيج مجاني/مدفوع + مدة إيقاف مخصصة كحد أقصى.)

### 7.4 إضافات UX
- ويدجتا Block + ScreenTime (Glance)، تقارير أسبوعية/سنوية (مخططات MPAndroidChart)، تكامل Tasker، Bug report receiver، Debug log activity، مكافئات إحصائية لكل لحظة استخدام (ej6 → Room) لتقرير "أين ذهب وقتك".

---

## 8. مقارنة التطبيقين الثلاثة (ملخص تنفيذي)

| القدرة | Warden (الحالي) | BlockerHero 1.5.0 | Block 11.3.0 |
|---|---|---|---|
| VPN/DNS hosts | ✓ (تفوق) | ✗ | ✗ |
| فحص صور NSFW (TFLite) | ✓ (تفوق) | ✗ | ✗ |
| كشف شريط عنوان بمعرّفات | جزئي (مسح نصي) | ✓ من الخادم | ✓✓ مضمّنة ~75 متصفحاً + fallback + محولات |
| كشف ميزات التطبيقات (view-ids) | جزئي (نصوص) | ✓ من الخادم | ✓ مضمّنة + **قابلة للتحديث عن بعد** |
| إغلاق الميزة بإجراء (BACK/…) | ✗ (نشاط حجب فقط) | ✓ BACK | ✓ DSL أوامر (`global_action:*`) |
| إعادة توجيه المتصفح لصفحة حجب | ✗ | ✓ (SafeSearch) | ✓✓ (صفحة حجب + حماية لوب) |
| Overlay فوري بلا نشاط | ✗ (InterceptActivity) | ✓ (WindowManager) | ✓✓ (Compose overlay + fallback + countdown + اقتباسات + كشف إحباط) |
| إغلاق منبثقات النظام | ✗ | ✗ | ✓✓ (retry + bounds + click) |
| حدود استخدام/فتحات + تذكيرات لينة | جزئي (LimitReached) | ✗ (Focus فقط) | ✓✓ (4 أنواع + تذكيرات + إحصاء) |
| استمرارية بعد الريبوت | ✗ | Device admin فقط | ✓✓ (مع كشف force-stop + وضع بعد-الريبوت) |
| إيقاف مؤقت بصرامة (QR/NFC/كتابة) | PIN فقط | ✗ | ✓✓ |
| جداول أسبوعية | ✓ | Focus | ✓✓ (مواعيد دقيقة + alarm receivers) |
| تحكم سحابي/تحكم آبائي | ✗ | ✓✓ | جزئي (Tasker/remote config) |

---

## 9. خطة الدمج النهائية لـ Warden (تحل محل خطة التقرير الأول — تُدمج أفكار التطبيقين)

### المرحلة A — محرك الويب الممتاز (من Block، أثمن نقل)
**ملف جديد:** `block/WebDetector.kt` (أو تطوير `BrowserUrlMonitor`):
1. **قاعدة `BrowserConfig`**: انسخ جدول ~75 متصفحاً من `cg0` (سرد كامل متوفر في `jadx_src/sources/defpackage/cg0.java` بمشروعنا في scratch) إلى Kotlin: `data class BrowserConfig(pkg, urlViewIds: List<String>, rawFallback: Boolean, normalizer: (String)->String? = identity)` مع **مرشحين متعددين** لكل متصفح.
2. **الاستخراج**: port `i()`: focus→تخطي؛ text→normalizer؛ contentDescription→`https?://` ثم دومين عارٍ؛ البوابة النهائية = **نمط WebAddress** (انسخ `md4.a` حرفياً — دعم IDN/IPv4 كامل).
3. **Fallback شجري** (`cd`): BFS بسقف **2000 عقدة** للعثور على view-id (بعض متصفحات mozac).
4. **تحديث عن بعد لاحقاً**: غلّف القاعدة بـ JSON في `assets/` الآن، واجعلها قابلة للاستبدال من خادمك مستقبلاً (TTL 600s كما فعلوا).
5. **قياس النجاح** (اختياري): سجّل `VIEW_ID/RAW/TYPING/NOT_FOUND` محلياً — يؤمّن تحسين الجدول.

### المرحلة B — كاشفات الميزات القابلة للتحديث (من Block + BlockerHero)
**ملف:** `feature/AppFeatures.kt`
1. نموذج `AppFeature(id, packageName, displayName, detectionViewIds, navigateAwayTo: List<String>)` + **JSON في assets** (بنفس مخطط Gson: `id/packageName/displayName/detection.viewIds/navigateAwayTo`).
2. أضف قائمة الميزات الداخلية الخمسة من Block + **قائمة BlockerHero** (كاشفات Instagram search/reels، Facebook reels، WA Channels، Telegram search بـ 29 لغة، Snapchat ngs_*):
   - Instagram: `clips_tab`(selected)/`like_button`(visible)/`peek_container`+`indicator_container`/`action_bar_title`∈{Explore,…}, search ids.
   - Facebook: contentDesc "View reels", `FbShortsViewerActivity`.
   - WA/W4B: `com.whatsapp.newsletter.directory.ui.NewsletterDirectoryCategoriesActivity`.
   - Telegram: Regex `telegram|challegram` + EditText + نصوص "Search" بـ 29 لغة.
   - Snapchat: `ngs_spotlight_icon_container`, `ngs_community_icon_container`.
3. **كشف**: viewId + `isVisibleToUser()` + `selected`/`visible` حسب الحالة (port `kr0.q` + إضافات BlockerHero).
4. **الإغلاق بـ DSL**: نفّذ `navigateAwayTo`: `global_action:navigate_back|global_action:home|click:<viewId>` — بدل إطلاق InterceptActivity فقط.
5. **حقن أنماط URL** (من BlockerHero `K0.c.m`): عند تفعيل Short/Reels/Spotlight أضف `m.youtube.com/shorts`, `instagram.com/reels`, `snapchat.com/discover`, `snapchat.com/spotlight` إلى قائمة كلمات الويب.

### المرحلة C — تنفيذ الحجب ثلاثي الطبقات (من Block)
**ملف:** `ui/intercept/BlockScreenManager.kt`
1. **Overlay فوري**: إذا `canDrawOverlays` → ComposeView عبر WindowManager (type 2038، flags 16777472، -3) بمحتوى شاشة الحجب الحالية — **بدون بدء InterceptActivity** (يلغي وميض الرؤية). عند عدم توفر الإذن → مسار InterceptActivity الحالي (fallback مثلهم تماماً).
2. **صفحة حجب داخل المتصفح**: عند حجب موقع → `ACTION_VIEW` إلى `https://<نطاقك>/block.html` في نفس المتصفح (+ `application_id` extra) **قبل** الإظهار؛ **واقي اللوب**: إن كان الدومين الحالي = دومين صفحة الحجب → تخطَّ.
3. **عدّاد إغلاق + كشف إحباط**: settings جديدة (`blockScreenDelaySeconds`) + منطق "3 مرات/دقيقتين → تلميح تقصير" (مرة/يوم).
4. **اقتباسات تحفيزية** اختيارية (asset بسيط + مفتاح settings).

### المرحلة D — المتانة (من Block + BlockerHero)
1. **Popup closer**: نسخة مصغرة من `ek4`: عند حجب تطبيق، راقب أحداث `com.android.systemui` لمدة 60 ثانية؛ إن ظهرت نافذة systemui لحزمة الهدف → ابحث عن زر إغلاق ضمن قائمة معرّفات معروفة واضغطه (5 محاولات/2 ثانية). + مستقبل `CLOSE_SYSTEM_DIALOGS`.
2. **الريبوت الذكي**: BootReceiver + فحص `getHistoricalProcessStartReasons().wasForceStopped()` (API 35) + إعداد "إبقاء الحجب بعد الريبوت" + إشعار "اضغط لإعادة التفعيل". (warden لديه AdminManager + BootReceiver — أضِف فحص force-stop والوضع.)
3. **Device admin**: فعّل مسار warden الحالي كخيار "متانة قصوى".
4. **تعديلات مؤجلة**: أي تعديل "يرخّي" قيداً نشطاً → يؤجل حتى نهايته (فلسفة strict).

### المرحلة E — حدود الاستخدام والتذكيرات (من Block)
1. **Open limits**: حد فتحات يومي/ساعي لكل تطبيق (عدّاد أحداث ACTIVITY_RESUMED في VPN/الخدمة) — ميزة غير موجودة في Warden وتناسب قصة السوشل ميديا تماماً (TikTok كل 5 دقائق…).
2. **تذكيرات لينة**: إشعار عند "اقتراب الحد" أو "الانتقال إلى تطبيق ذي حد" قبل الحجب الصلب.
3. اربطها بـ `AccessDecision` الحالي: أولوية القرار = HardBlock(يدوي/جدول) → LimitReached(حد استخدام) → **OpenLimitReached** (جديد).

### المرحلة F — السوشل ميديا بالتحديد (دمج محركين)
1. **المتن النصي من BlockerHero**: مترجم `KeywordEngine` (regex + تطبيع + حدود) + قوائم مصنّفة + قائمة سماح بتشويش + **حقن أنماط الروابط** (B.5) + **حقن SafeSearch URL** (خريطة x.java) + **حجب بحث الصور/الفيديو** (y.java) + حجب المتصفحات غير المدعومة (resolveActivity).
2. **الصور من Warden**: ابقِ `ImageScreeningOrchestrator` (TFLite) — تشغّله بعد فشل مسار النص، مثل الآن.
3. **التنفيذ من Block**: إعادة توجيه المتصفح + overlay فوري + إغلاق الميزة بـ navigateAway.
4. **النتيجة**: سوشل ميديا محجوبة من 4 اتجاهات: (نص/كلمات) + (عناصر واجهة) + (URL/شريط عنوان) + (صور) — أفضل تغطية ممكنة بلا VPN للنطاقات داخل التطبيقات، وVPN يبقى طبقة الشبكة عندك.

### أولوية التنفيذ (مُحدّثة)
| # | العمل | المصدر | الأثر |
|---|---|---|---|
| 1 | BrowserConfig ~75 متصفحاً + استخراج + WebAddress gate + fallback شجري | Block | ★★★★★ |
| 2 | AppFeatures قابلة للتحديث + كشف + navigateAway DSL | Block + BlockerHero | ★★★★★ |
| 3 | BlockScreenManager ثلاثي الطبقات + صفحة حجب + عدّاد + كشف إحباط | Block | ★★★★☆ |
| 4 | KeywordEngine + قوائم مصنّفة + SafeSearch URL + صور/فيديو + غير مدعوم | BlockerHero | ★★★★☆ |
| 5 | Popup closer + CLOSE_SYSTEM_DIALOGS | Block | ★★★☆☆ |
| 6 | Open limits + تذكيرات لينة | Block | ★★★☆☆ |
| 7 | Boot ذكي (force-stop detect) + تعديلات مؤجلة + strict | Block | ★★☆☆☆ |
| 8 | عزل إشعارات التطبيقات المحجوبة (NotificationListener) | Block | ★★☆☆☆ |

---

## 10. ما لم نتمكن من إثباته (Unknowns)
1. **مصدر التحديث عن بعد** للمتصفحات/الميزات: آلية الـ TTL واضحة (600s) لكن نقطة الجلب (ملف في Firebase Remote Config؟ رابط؟) لم تُحدَّد نهائياً في الكود المفكّك.
2. تفاصيل محوّل Yandex (`w40(27)`) وحالة "usage reminder" الداخلية في `tx6` — lambda-merged.
3. دفق الأحداث `ej6 → o45` (مخزن الاستخدام) لم نتتبعه كاملاً؛ النمط العام واضح (Room + تجميعات).
4. بعض مكونات SDK (ads/mlkit/glance) دخلت بالفئات المشوّشة؛ أي أثر لها في الكود المذكور أدناه يجب ألا يُنقل.

## 11. الأدلة
| ما تبحث عنه | الملف (بعد التفكيك في `scratch/blocksite-re/`) |
|---|---|
| خدمة الوصول (الويب + الميزات + popup closer) | `jadx_src/sources/block/libraries/fgappscanner/accessibility/FGAccessibilityService.java` |
| قاعدة المتصفحات (~75) | `jadx_src/sources/defpackage/cg0.java` (BrowserConfig) |
| نمط URL العالمي (WebAddress/IDN) | `jadx_src/sources/defpackage/md4.java` |
| سجل الميزات | `jadx_src/sources/defpackage/mp.java` + `f42.java` + `bm1.java` |
| مطابقة الميزة | `jadx_src/sources/defpackage/kr0.java` (q) |
| المنفذ (إعادة توجيه/إغلاق/ميزة) | `jadx_src/sources/defpackage/th3.java` (j) |
| المقيّم المركزي | `jadx_src/sources/defpackage/tx6.java` (f, F) + `h50/b80` |
| شاشة الحجب ثلاثية الطبقات | `jadx_src/sources/defpackage/hr0.java` + `block/.../TransparentCloseAppDialog.java` |
| منبثقات النظام | `ek4.java` + `block/.../CloseSystemDialogReceiver.java` |
| الريبوت | `block/.../reboot/BootReceiver.java` + `ActivateAfterRebootNotificationActionReceiver.java` |
| عزل الإشعارات | `com/wverlaek/block/features/detection/notifications/NotificationListener.java` |
| NFC | `block/libraries/nfc/NfcScanActivity.java` |
| إعدادات/نصوص الميزات | `apktool_out/res/values/strings.xml` (pref_key_*، pause_option_*، limits) |
| إعداد الخدمة | `apktool_out/res/xml/accessibility_service.xml` |

---

*أُعدّ التقريران (هذا + `RE-blockerhero-1.5.0-ar.md`) بتحليل ساكن فقط؛ لا شيء نُفِّذ أو أُرسل. جميع معرّفات الواجهة والثوابت المذكورة منسوخة حرفياً من الكود المفكّك والمتاحة في مجلد scratch لاستخدامها مباشرة في النقل.*
