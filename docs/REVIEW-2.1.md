# مراجعة مشروع صَوْن (Sawn) — 2.1.1

**تاريخ:** 2026-11-08 · **الفرع:** `sawn-rebuild` · **العنوان الحالي:** HEAD `081077e`
**نطاق المراجعة:** ~13,500 سطر Kotlin، 220 اختبار، 3 وحدات Gradle، APK موقّع R8.

> منهجية: 5 وكلاء فحصوا بُعدًا مستقلًا لكلٍّ منهم (مسار الإقلاع · DI/Room/DataStore ·
> الخدمات · إعداد البناء/R8 · المنطق والاختبارات). كل ادعاء رُوجع سطرًا بسطر أمام الكود.
> ما لم يُتحقق منه من الجهاز الحقيقي يذكره صريحًا.

---

## 1. عطل «splash ثم شاشة سوداء» — الحالة

### 1.1 ما كان يُظنّ سابقًا ويجب سحبه
- **حدس «LocaleUtils يُنتج وسمًا غير صالح»:** خاطئ. الملف الحالي سليم (`LocaleUtils.kt`).
- **«هجرة Room v3→v4 تفشل»:** خاطئ. `MIGRATION_3_4` يطابق `4.json` حقلًا بحقل (تحقق وكيل
  مستقل)، و`fallbackToDestructiveMigration()` احتياطي عملي.

### 1.2 العيب المؤكَّد رقم 1: معالج التعطل يقتل التطبيق بدل أن يُعالجه
`app/src/main/java/com/sawn/app/SawnApp.kt:30-38`

```kotlin
Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
    Looper.prepare()            // ← على main thread: يرمي "Only one Looper may be created per thread"
    Toast.makeText(...).show()  // ← على main: يحتاج Looper (موجود) لكن سيكون ضمن المعالج المعطّل
                                // ← على worker: لا Looper → يرمي أو لا يعرض شيئًا
    Looper.loop()               // ← يكتم الاستثناء ولا يُنهي العملية
}
```

| الخيط | النتيجة |
|------|---------|
| **main** | `Looper.prepare()` يرمي استثناءً داخل المعالج نفسه → crash مزدوج. |
| **worker** | Thread spinning forever (لا crash، لا رسالة) — تسرب خيوط. |
| **الاستثناء الأصلي** | لا يُعرض أبدًا، ولا يُسجَّل (`proguard-rules.pro:12-17` يحذف كل `Log.*` من release!). |

**الأثر:** أي crash غير متوقع (بما فيه crash أثناء أول رسم في `MainActivity` أو أثناء
`viewModel()` أو أول قراءة DataStore) **لا يُعالَج ولا يُرى**, ومع R8 يصبح غير متوقع أكثر.
Debug (حيث R8 معطّل) قد يعمل بينما Release سوداء — وهذا يفسر «يعمل في المراجعة العربية
ولا يعمل على الهاتف».

**الإصلاح المطلوب (فردي، قابل للاختبار):** استبدال المعالج بـ
`Process.killProcess(Process.myPid())` (أو إعادة رفع الاستثناء عبر المعالج السابق)، وتسجيل
`Log.e` قبل الحذف بكشف استثناء لـ R8. المستخدم رأى «splash طويل ثم أسود» — وهذا أحد
المرشحين المؤكدين من الكود.

### 1.3 العيب المؤكَّد رقم 2: مسار `settingsReady` بلا `catch`
`app/src/main/java/com/sawn/app/ui/SawnViewModel.kt:78-85`

```kotlin
val settingsReady = combine(masterEnabled, strictMode, hasPin, focusEndsAt, onboardingDone) { ... true }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)   // ← بلا .catch!
```

- القيمة الابتدائية `false`، والتعليق نفسه (سطر 77) يقول «الواجهة تعتبرها قفلًا».
- إن رمى أحد المصادر الخمسة (DataStore ملف تالف أو مفتاح بقيمة من نوع خاطئ) → **`false` للأبد**
  → `MainActivity` يعرض `CircularProgressIndicator` فقط (سطر 203-207)؛ التحميل على خلفية
  داكنة مخصوص. يعطي «سوداء» وليس crash.
- **الفرق المؤكد:** في `SettingsStore` توجد **6 قراءات `.first()` بلا catch**:
  `protectionSnapshot:446`، `pinLockRemainingMillis:491`، `pinCredentials:537`،
  `isLockdownActiveNow:558`، `trustedNow:630`، `pauseDefeatsToday:649`. أي منها رمى
  في `Dagger`/`AccessController` → crash يلتقطه معالج 1.2.

**الإصلاح المطلوب:** `.catch { emit(true) }` على `settingsReady` (fail-open للواجهة مع تنبيه
optera)، وتغليف القراءات الست بـ `runCatching`، ووضع `CorruptionHandler` في DataStore.

### 1.4 ما يلزم منك (حاسم)
بدون logcat لا نستطيع أن نجزم **أيّهما** السبب:
```
adb logcat -b crash -d > %USERPROFILE%\Desktop\crash.txt
adb logcat -d > %USERPROFILE%\Desktop\run.txt
```
إن لم يتصل الهاتف: **إعدادات → التطبيقات → Sawn → التخزين → مسح البيانات** ثم التشغيل.
- اختفى الأسود → السبب 1.3 (قيمة مخزّنة تالفة).
- بقيت سوداء → السبب 1.2 (crash مخفي) — ثم نصلحه ونعيد.

### 1.5 ما لم أجد سببًا له ولا أزعمه
- لا مشكلة في `R8`/التوقيع/الإصدارات (تحقق وكيل مستقل: `configuration.txt`, `seeds.txt`,
  `output-metadata.json`).
- لا `Class.forName`/reflection في النطاق.
- الـVPN/A11y لا يبدآن قبل أول رسم؛ `theme.windowBackground=#FFF7ED`/`#1C1917`. الأسود
  هنا **ليس** شفافية نافذة، بل إما crash-loop أو spinner للأبد.

---

## 2. عيوب أمنية وحماية الشرطة (أهم من العطل نفسه للمستخدم)

| # | الشدة | الموضع | المشكلة | الأثر |
|---|------|--------|---------|------|
| A1 | **حاسم** | `PlanBScreens.kt:181-226` + `SawnViewModel.kt:625-639` | **«نسخ احتياطي» وهميّ:** `exportBackup` يصدّر النص الحرفي `"sawn-backup-export"` (لا قاعدة بيانات، لا إعدادات). `importBackup` يعيد `"pending-ui"` ولا يستورد شيئًا. | المستخدم يظنّ أنه يملك نسخة، ثم يمسح الجهاز → يخسر كل شيء. |
| A2 | **عالي** | `SettingsStore.kt:146-260, 279-...` + `SawnViewModel` | عند IOException تقرأ DataStore مفضّلات **فارغة**، والواجهة ترى الافتراضيات: `masterEnabled=false, alwaysOn=true, pornGuard=true, safeSearch=true`. | شريط «الحماية» يعرض تشغيلاً، بينما `protectionSnapshot()` يرمي → **لا حجب فعلاً**. |
| A3 | **عالي** | `AdultHosts.kt:59` + `DomainMatcher.kt:15` | `REDDIT_MEDIA = ["i.redd.it","v.redd.it","reddit-media.com"]` يقفل **كل** صور/فيديو Reddit (برمجة/وثائق/ميمز), والتوثيق نفسه في سطر 53-57 يزعم «media paths فقط». | حجب واسع يخالف النية المعلنة, ويمكن أن يفسّر «التطبيق لا يعمل». |
| A4 | **متوسط** | `RoomProtectedDaysRepository.kt:17,20` | `LocalDate::parse` بلا حماية؛ صفّ واحد تالف يرمي في كل emission ويمرّ عبر `.catch` في الـVM إلى 0 للأبد. | السلسلة/الإحصاءات صفر بعد تلف صف واحد. (اختباران أضفتُهما يولّدان العيب حاليًا — انظر §5.) |
| A5 | **متوسط** | `NightGuardWorker.kt:36-46` + `SettingsStore.setStrictMode:388` | ضبط ليل يضبط `masterEnabled=true` كل 15 دقيقة, **و`setStrictMode(true)` يرفض لأنه بلا PIN** فلا يصبح strict أبدًا. **ولا يُطفأ strict أبدًا خارج النافذة** (الفرع في سطر 40 لا يطفئه). | «Night Guard» يعطي انطباع الانضباط ولا يفرضه. **ملاحظة مؤكدة من المراجع المستقل:** الميزة **معطلة كليًا اليوم** لأن العلم `nightGuardEnabled=false` افتراضيًا والمسار الوحيد لتعيينه هو الشاشة غير الموصولة (B1). |
| A6 | **متوسط** | `SawnViewModel.kt:495-529` + `ChallengeEntities.kt:21,27-39` | `ChallengeDayEntity.date` هو PK؛ **تشغيل تحدي جديد يكتب REPLACE على نفس اليوم** فيحذف يوم التحدي السابق. و`insertRun`+`recordDay` بدون `@Transaction`. | إحصاءات تحدي 30 يوم تُخسر أيامًا صامتًا. |
| A7 | **متوسط** | `SawnViewModel.kt:400-409` + `BlockRepository` | إضافة نفس دومين مرتين → صفّان، لأن `addWebsite` بلا فحص `(type,value)`. | القائمة تتضخم بالإدخال المكرر. |

---

## 3. ميزات «موجودة» لكنها غير موصولة (Feature wiring)

| # | الشدة | الموضع | الحقيقة |
|---|------|--------|----------|
| B1 | **عالي** | `MainActivity.kt:339-374` | الشاشات السبع (challenge, night_guard, deep_focus, triggers, accountability, backup, parent_mode) **مُسجَّلة في الـNavHost لكن لا يوجد أي `navigate("…")` إليها** (تحقّقت بالأمر: 0 لكلٍّ). **مراجع مستقل أكّد عدم الوجود وعدم وجود deep-link/ACTION_VIEW أو أي مسار غير مباشر.** | المستخدم **لا يستطيع الوصول** إلى 12 ميزة Plan B من أي مكان في الواجهة. |
| B2 | **عالي** | `OnboardingHost.kt:88-95` + `SawnViewModel.completeOnboarding:96-102` | `PlanBuilder.build(answers)` **لا يُطبَّق على الإعدادات**؛ «تفعيل الخطة» يحفظ الاسم/العمر/الإجابات فقط. | «خطتك الشخصية» زخرفية؛ الأسئلة لا تغيّر أي حجب. |
| B3 | **متوسط** | `SawnViewModel.display` / `HomeScreen:118-198`, `SettingsScreen:128-145` | `profileAge` لا يُستخدم إلا للعرض. | بيانات الطفل (عمر) مخزَّنة بلا وظيفة. |
| B4 | **منخفض** | `SawnViewModel.kt:568, 620, 639` | `requestDeepFocusUnlock`، `setParentPin`، `importBackup` دوالّ فارغة/stub. | أسطح Plan B لا تستحق اسمها اليوم. |
| B5 | **متوسط** | `TriggerInsights`/`peakHour`:587-591 | `peakHour` يستبدل كل وسم بـ`BOREDOM` قبل التحليل. | «ساعة الذروة» بلا معنى مُصنَّف. |
| B6 | **متوسط** | `AccountabilityScreen` + `SawnViewModel.shareWeeklyReport:602-610` | زر «مشاركة التقرير الأسبوعي» يشارك `buildWeeklyReport(0,0,0)` حرفيًا. | يشارك صفرًا/صفرًا/صفرًا دائمًا — غير صادق. |

---

## 4. عيوب جودة/سلوك أقلophilia (لكن مؤكدة)

| # | الشدة | الموضع | المشكلة |
|---|------|--------|---------|
| C1 | **متوسط** | `SawnViewModel.kt:174-206` + `HomeScreen:130-136` | `oneSecondTicker` (كل ثانية) يُشغّل `enforcementActive` باستمرار طالما هناك مشترك (الرئيسية مفتوحة). عدّاد الجلسة/النافذة يعتمد على `SystemClock` و`System.currentTimeMillis` وتُحتسب عند كل puls؛ عند منتصف الليل قد تتأخر تحديثات الشاشة حتى نقرة/إعادة فتح. يحتاج اختبار جهاز لتحديد المسافة. |
| C2 | **متوسط** | `RoomStatsRepository:24-38` | `currentStreak()` تُحسب وقت الجمع، وتتوقف عن التحديث في منتصف الليل (لا `stateIn(WhileSubscribed)` لمُصدّر اليوم). |
| C3 | **منخفض** | `DnsPacket.kt:50-77` | `readQName` يفحص `buf.size` لا `length` ولا حقل UDP length → بايتات لم تُرسَل تُقرأ من المخزن المؤقت. المؤثر محصور داخل الـ tun المحلي. |
| C4 | **منخفض** | `BackupCrypto.kt:59-60` | `MessageDigest(...).digest(hash)` نتيجتها **مهملة**؛ التعليق يزعم «تعمية التوقيت». كود ميت. |
| C5 | **منخفض** | `ProtectedDaysDaoTest.kt:79-100` (**أضفتُه أنا**) | اختباران جديدان لتقرير ضرر التاريخ التالف — **يفشلان حاليًا** لأن `RoomProtectedDaysRepository` لم يُصلَح بعد. لا تشغّل `./gradlew test` في حالة حمراء قبل إصلاح A4. |
| C6 | **منخفض** | `PinHasher` | PBKDF2-HMAC-SHA256 310k iterations + salt عشوائي 16 بايت + مقارنة ثابتة الزمن — **جيد**. لا يوجد `PinHasherTest` (تغطية ناقصة). |
| C7 | **منخفض** | `SettingsStore`/`PinLockoutPolicy` | سقف قفل PIN = ساعة؛ `PIN_FAILS` لا يتضاءل → PIN من 10k تركيبة ~12 يومًا. |

---

## 5. ما تحقّق جيدًا ولا يحتاج تغييرًا (حتى لا يُعاد بحثه)

- ✅ **Room:** `MIGRATION_1_2/2_3/3_4` تطابق `4.json` حقلًا بحقل (نوع·مفتاح·AUTOINCR).
- ✅ **DI/Hilt:** الرسم البرمجي سليم؛ `EntryPoint` بـ `val` فقط؛ لا singleton حالة قابلة للتغيير.
- ✅ **Hilt + Application.onCreate:** `EntryPointAccessors.fromApplication` مدعوم قبل إكمال onCreate.
- ✅ **DomainMatcher:** `evil-example.com` / `pornhub.com.evil.net` لا يطابقان (اختبارات موجودة).
- ✅ **ProtectionDayPolicy:** الرياضيات والتغطية (13 اختبارًا) جيدة.
- ✅ **PIN pad:** لوحة أرقام مخصّصة بلا الحقول النصية → لا تسريب عبر Autofill.
- ✅ **VPN scope:** `addRoute` لـ IPv4-DNS و IPv6-DNS فقط — التطبيق لا يمرّر بقية حركة المرور.
- ✅ **البحث:** لا `Class.forName`/reflection في `app`/`core`.
- ✅ **الإصدارات:** AGP 8.13.2 / Kotlin 2.2.21 / Compose BOM 2025.11.01 / Room 2.8.5 / Hilt 2.58 متوافقة.
- ✅ **الاختبارات:** لا `@Ignore` ولا `assertTrue(true)` في 220 اختبارًا.

---

## 6. خطة الإصلاح المقترحة (بالترتيب)

| الأولوية | ما يُصلح | الملف |
|---------|----------|------|
| **P0** | معالج التعطل (§1.2) + `settingsReady.catch` + `runCatching` على القراءات الست (§1.3) | `SawnApp.kt`, `SawnViewModel.kt`, `SettingsStore.kt` |
| **P0** | نقل الشاشات السبع إلى الواجهة (§B1) — زر «أدوات» في الرئيسية أو قسم في الإعدادات | `MainActivity.kt`, `HomeScreen.kt` |
| **P1** | النسخ الاحتياطي الصادق (§A1): إمّا تنفيذ تصدير فعلي (لا يمكن استيراد «sawn-backup-export») أو **إخفاء الزر** وتعطيله | `SawnViewModel.kt`, `PlanBScreens.kt` |
| **P1** | تطبيق الخطة في `completeOnboarding` (§B2) + تصحيح `REDDIT_MEDIA` (§A3) | `SawnViewModel.kt`, `AdultHosts.kt` |
| **P1** | `LocalDate.parse` آمن + triggers الـREPLACE (§A4, §A6) | `RoomProtectedDaysRepository.kt`, `ChallengeEntities.kt` |
| **P2** | تصحيح Night Guard (يقف عند غياب PIN، يعلن الحاجة للمستخدم) (§A5) | `NightGuardWorker.kt` |
| **P2** | backfill للـ tests الحمراء (§C5) بعد إصلاح A4 | `ProtectedDaysDaoTest.kt` |

> **لا أُنفّذ أيًّا من هذا قبل موافقتك**، لأن كل بند يغيّر سلوكًا يراه المستخدم.
> أوصي بالبدء بـ P0 (يُحدّد عطل الشاشة السوداء) وبـ A1 (risk كبيرة للمستخدم).

---

## 7. ملحوظة على ما لم يُتحقَّق منه
- لا جهاز/محاكاة في بيئة المراجعة (VT=Off في BIOS، 3.9GB RAM) → التحقق من البرنامج فقط لا من السلوك الحقيقي.
- لم تُفحص sigv bytes (لا `apksigner`) — التوقيع مؤكَّد من `build.gradle.kts` فقط.
- لا logcat من هاتفك — لذلك §1.4.
