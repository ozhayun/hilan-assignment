<div dir="rtl">

# DECISIONS

## 1. החלטות ארכיטקטוניות
- **הוצאתי שכבת Service** (`LeaveRequestService`) מה‑Controller: חישוב מכסה, ולידציית `create()`, ומכונת המצבים + הנעילות של `approve()`. ה‑Controller נשאר "רזה" — רק ממפה בקשת HTTP לקריאה ל‑Service ותופס חריגות.
  - **במכוון בלי over‑engineering**: מחלקת Service קונקרטית אחת, בלי interface (`ILeaveRequestService`)/`Impl`, בלי base class גנרי. יישום אחד לכל aggregate מספיק בגודל הזה.
- **טיפול בשגיאות דרך חריגות** (`NotFoundException`/`BadRequestException`/`ConflictException`) שנתפסות מפורשות ב‑Controller — לא דרך `@Spring ResponseStatus` על המחלקות עצמן. ההחלטה התקבלה לאחר בדיקת תאימות מול תשתית הטסטים הקיימת. הטסטים בפרויקט מחווטים לקרוא למתודות ה‑Controller ישירות, כך שהסתמכות על מנגנון ה‑dispatch של Spring (כמו `@ResponseStatus`) הייתה שוברת את התאימות לאחור.
- **ניהול Concurrency דרך Database Locks (`PESSIMISTIC_WRITE`)**: במקום לממש נעילה ברמת האפליקציה (שעלולה להיכשל בסביבה מבוזרת עם מספר מופעים), בחרתי להעביר את האחריות למסד הנתונים עצמו דרך `@Lock`. זו הגישה היציבה ביותר למניעת Race Conditions על משאבים קריטיים, ללא צורך במנגנון נעילה חיצוני מורכב.
- **Frontend**: Reactive Forms עם validator מותאם ל‑cross‑field (start≤end), ו‑Service ייעודי ל‑HTTP במקום קריאות ישירות מה‑Component. כתובת ה‑API דרך `src/environments/environment.ts` (+ `fileReplacements` ב‑`angular.json`), ו‑`takeUntilDestroyed` בכל 4 מקומות ה‑`subscribe()` בקומפוננטה — בלי unsubscribe ידני שנשכח.

## 2. הבאג ביתרת החופשה
- **מה היה הבאג**: ב‑`LeaveRequestsController.create()` (כיום `LeaveRequestService.create()`) חושב משתנה `used` (ימי חופשה שכבר אושרו), אבל הוא **לא היה בשימוש בפועל** בבדיקה — הבדיקה הייתה `days > employee.getAnnualQuota()` במקום `used + days > employee.getAnnualQuota()`. כתוצאה מזה, עובד עם 18 ימים מאושרים מתוך מכסה 20 יכול היה להגיש עוד בקשה של עד 20 ימים בלי שהמערכת תדחה אותה.
- **התיקון**: שינוי שורה אחת — הוספת `used +` לבדיקה.
- **הטסט שמוכיח את התיקון**: `create_ExceedingRemainingQuota_IsRejected` — עובד עם מכסה 20 ו‑18 ימים מאושרים, מנסה בקשה נוספת של 5 ימים (18+5=23>20) ומצפה ל‑400.

## 3. אישור בקשה (approve) ו‑concurrency
- **מצבים לא חוקיים**: בקשה שלא קיימת → 404. בקשה שכבר `APPROVED`/`REJECTED` → 409.
- **אישור של שתי בקשות במקביל**: שתי אישורים בו‑זמנית עלולים לדחוף עובד מעל המכסה גם אם כל אחד בנפרד תקין (למשל שתי בקשות של 6 ימים כל אחת כנגד מכסה של 10). טופל דרך נעילת שורה פסימיסטית (`FOR UPDATE`): קודם נועלת שורת ה‑request עצמו (מונע אישור כפול של אותה בקשה), ואז שורת ה‑employee (מונע אישור מקביל של שתי בקשות שונות של אותו עובד) — בתוך אותה טרנזקציה, כך שהאישור השני "רואה" את התוצאה המחויבת (committed) של הראשון לפני שהוא בודק את המכסה. הוכחתי את זה בטסט אמיתי עם שני threads בפועל (`approve_ConcurrentApprovalsExceedingQuota_OnlyOneSucceeds`), לא רק תיאורטית.
- **תקלה נוספת שמצאתי בבדיקה ידנית**: שתי בקשות חופשה של אותו עובד יכלו לחפוף בתאריכים (למשל 31/08–02/09 ו‑01/09–05/09) בלי שהמערכת תמנע זאת — היא בדקה רק סה"כ ימים, לא התנגשות בתאריכים בפועל. תוקן: שאילתה שמוצאת בקשות לא‑דחויות של אותו עובד שחופפות בטווח, נבדקת ב‑`create()` ומחזירה 409.

## 4. על מה ויתרתי בגלל הזמן
- **בדיקת "אין תאריך עבר"**: הטופס מאפשר להגיש בקשה לתאריכים שכבר עברו. החלטתי שזה לא בהכרח באג — מערכות HR אמיתיות מאפשרות לעיתים קרובות דיווח למפרע (חופשת מחלה שמדווחים עליה אחרי החזרה, תיקון רישום שפוספס), וה‑README לא ביקש חוק כזה. עם עוד יום הייתי בודק עם בעל המוצר לפני שמחליט.
- **עיצוב UI**: בחרתי להשאיר את העיצוב (CSS) מינימלי ופונקציונלי. הדגש במטלה הוא על פתרון בעיות לוגיות, אבטחה וארכיטקטורה. השקעה ב‑UI מורכב בשלב זה תהווה חריגה מהמיקוד המבוקש.
- (בהתחלה גם ניקוי RxJS ו‑`environment.ts` היו ברשימת הוויתורים בגלל זמן — בסוף היה זמן להשלים אותם, ראו סעיף 1.)

## 5. שימוש ב‑AI
### איפה AI עזר (כולל prompts)
1. **יצירת תשתיות טסטים לאימות באגים**: במקום לכתוב מאפס את תבנית הטסט עם Testcontainers, השתמשתי ב‑AI כדי לייצר במהירות את מבנה הטסט שמאתגר את המערכת.
   **prompt**: "Generate a JUnit 5 integration test using the existing Testcontainers setup for `LeaveRequestsController.create()`. The test should configure a Dana Levi user with a quota of 20 and 18 used days, and assert that a new 5‑day request returns a 400 Bad Request to prove the quota validation fix."
   **למה זה עזר**: זה איפשר לי להתמקד בפתרון הלוגי (תיקון הבאג) במקום לבזבז זמן על כתיבת boilerplate של הגדרות טסטים ו‑mocks.
2. **prompt מפורט** (בבקשה לרפקטור שכבת Service): "Act as a senior Java Spring Boot developer. Refactor the existing `LeaveRequestController` by extracting the business logic into a new Service layer... [7 strict constraints, כולל `@ResponseStatus` על exceptions, בלי interface, `/search` לא נוגעים בו]" → קיבלתי את כל המבנה (Service, שלוש חריגות, Controller רזה) בהתאם למגבלות. constraint 3 (`@ResponseStatus`) ו‑constraint 7 (הטסטים הקיימים חייבים להמשיך לעבור) התנגשו בפועל: הטסטים קוראים למתודות ה‑Controller ישירות ולא דרך dispatch אמיתי של Spring MVC, אז `@ResponseStatus` לא היה מופעל. תוקן בחזרה ל‑`try/catch` מפורש ב‑Controller.
3. **prompt מפורט** (טופס Angular): בקשה מפורטת ל‑Reactive Form עם ולידציית cross‑field, בלי service extraction (מתוכנן לשלב נפרד), בלי שדה days → יושם כמבוקש, כולל טסט חי בדפדפן אמיתי מול ה‑API האמיתי (submit ריק, טווח תאריכים הפוך, הגשה תקינה, הגשה שחורגת ממכסה).

### איפה דחיתי/תיקנתי הצעה של AI (ולידציה חסרה בלוגיקה העסקית)
- כש‑AI כתב את הלוגיקה העסקית בשכבת ה‑Service, חישוב ימי החופשה הסתמך על ההפרש הפשוט בין תאריך ההתחלה לתאריך הסיום, בלי שום בדיקה שהטווח לא הפוך. ההנחה המשתמעת הייתה שהוולידציה שכבר קיימת ב‑Frontend (start≤end) מספיקה — הנחה שגויה, כי כל קריאה ישירה ל‑API (למשל דרך Swagger) עוקפת אותה לגמרי. איך זה התגלה בפועל ומה ההשפעה המדויקת מתועדים בסעיף "אבטחה" למטה.
- **תיקנתי**: הוספתי ולידציה מפורשת בצד השרת ב‑`create()` שדוחה בקשה שבה `endDate` לפני `startDate` (400), לפני שהמכסה או חפיפת התאריכים נבדקות ולפני ששום שורה נשמרת.

### אבטחה
- **מה מצאתי**: SQL Injection ב‑`GET /api/leave-requests/search` (`backend/src/main/java/com/example/leavemanagement/controller/LeaveRequestsController.java`, מתודת `search`) — הפרמטר `name` הוכנס ישירות כ‑string concatenation לתוך שאילתת SQL native (`"... WHERE name LIKE '%" + name + "%'"`).
- **הסיכון**: תוקף יכול לשלוח ב‑`name` קטע SQL (למשל `' OR '1'='1`) ולשנות את השאילתה בפועל — לחלץ נתונים שלא אמורים להיות נגישים, לעקוף את הסינון המיועד, או במקרים חמורים יותר לתמרן/לחשוף מידע ממסד הנתונים כולו.
- **תיקון**: **בוצע** — הוחלף ב‑`LeaveRequestRepository.findByEmployee_NameContainingIgnoreCase(String name)`, שאילתת Spring Data עם bind parameter אמיתי, בלי string concatenation. אפשר גם להסיר לגמרי את התלות הישירה של ה‑Controller ב‑`EntityManager`. נוספו שני טסטים: חיפוש רגיל עדיין עובד, ותשלובת injection קלאסית (`' OR '1'='1`) מטופלת כטקסט מילולי ומחזירה רשימה ריקה במקום לשנות את השאילתה — אומת גם ידנית מול ה‑API החי.

### ממצא נוסף: עקיפת המכסה דרך טווח תאריכים הפוך (חמור יותר מה‑SQLi)
- **איך מצאתי את זה**: לאחר שהקוד נראה "מוכן", ביקשתי מ‑AI לבצע עצמו‑ביקורת אדוורסרית — לשחק תפקיד של Senior Staff Engineer קפדן במיוחד שמחפש באגים, race conditions וחורי אבטחה, בלי נימוס. הביקורת העלתה חשד קונקרטי, ואז **אימתתי אותו בעצמי מול ה‑API החי** (לא הסתפקתי בטענה) — זה בדיוק מה שהוביל לגילוי האמיתי.
- **מה מצאתי**: ה‑Frontend אוכף `startDate ≤ endDate`, אבל ה‑Backend **לא** — שום מקום בקוד לא בדק את זה לפני חישוב `days = daysBetween(start, end) + 1`. שליחת בקשה ישירות ל‑API (למשל דרך Swagger, שחשוף לפי הוראות ה‑README) עם טווח הפוך (`startDate=2026-11-10, endDate=2026-11-01`) החזירה **200 OK** עם `"days": -8`.
- **למה זה חמור**: זה לא רק ערך מוזר בשדה — זה **עקיפה בפועל של בדיקת המכסה שהמשימה דרשה לתקן**. אישרתי בקשה עם `days=-8` לעובדת עם 18 ימים מאושרים מתוך מכסה 20, ואז שלחתי בקשה לגיטימית נוספת של 5 ימים (18+5=23>20, אמורה להידחות) — **היא התקבלה**, כי הסכום הרץ הפך ל‑18+(-8)=10, ו‑10+5=15≤20 עבר בהצלחה. כלומר: מסלול שני, לא מאומת בצד השרת, מאפשר לעקוף לגמרי את הבאג המרכזי של המשימה (סעיף 2).
- **תיקון**: הוספת בדיקה ב‑`LeaveRequestService.create()` שדוחה `endDate` לפני `startDate` עם 400, לפני שהמכסה/חפיפה נבדקות ולפני ששום שורה נשמרת. טסט חדש (`create_ReversedDateRange_IsRejectedAndCannotCorruptQuota`) מוכיח גם את הדחייה וגם שהסכום הרץ לא מתקלקל. אומת מחדש ידנית מול ה‑API החי — אותה בקשה בדיוק שהצליחה קודם מחזירה כעת 400.

## 6. הוראות הרצה
- אין שינוי מהוראות ה‑README המקורי — `docker compose up --build` מרים DB+API יחד כמתואר.

</div>
