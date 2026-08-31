<div dir="rtl">

# DECISIONS

## 1. החלטות ארכיטקטוניות
- **הוצאתי שכבת Service** (`LeaveRequestService`) מה‑Controller: חישוב מכסה, ולידציית `create()`, ומכונת המצבים + הנעילות של `approve()`. ה‑Controller נשאר "רזה" — רק ממפה בקשת HTTP לקריאה ל‑Service ותופס חריגות.
  - **במכוון בלי over‑engineering**: מחלקת Service קונקרטית אחת, בלי interface (`ILeaveRequestService`)/`Impl`, בלי base class גנרי. יישום אחד לכל aggregate מספיק בגודל הזה.
- **טיפול בשגיאות דרך חריגות** (`NotFoundException`/`BadRequestException`/`ConflictException`) שנתפסות מפורשות ב‑Controller — לא דרך `@Spring ResponseStatus` על המחלקות עצמן. הסיבה: הטסטים הקיימים קוראים למתודות ה‑Controller ישירות (לא דרך dispatch אמיתי של Spring MVC), אז `@ResponseStatus` פשוט לא היה מופעל שם. גיליתי את זה כי הרצתי `mvn test` בפועל ולא רק קראתי קוד — פירוט מלא בסעיף 5.
- **Concurrency דרך נעילות DB אמיתיות** (`SELECT ... FOR UPDATE` דרך `@Lock(PESSIMISTIC_WRITE)`), לא לוגיקה באפליקציה — כי זה בדיוק המקרה שה‑DB אמור לפתור, ופשוט יותר מנעילה מבוזרת.
- **Frontend**: Reactive Forms עם validator מותאם ל‑cross‑field (start≤end), ו‑Service ייעודי ל‑HTTP במקום קריאות ישירות מה‑Component.

## 2. הבאג ביתרת החופשה
- **מה היה הבאג**: ב‑`LeaveRequestsController.create()` (כיום `LeaveRequestService.create()`) חושב משתנה `used` (ימי חופשה שכבר אושרו), אבל הוא **לא היה בשימוש בפועל** בבדיקה — הבדיקה הייתה `days > employee.getAnnualQuota()` במקום `used + days > employee.getAnnualQuota()`. כתוצאה מזה, עובד עם 18 ימים מאושרים מתוך מכסה 20 יכול היה להגיש עוד בקשה של עד 20 ימים בלי שהמערכת תדחה אותה.
- **התיקון**: שינוי שורה אחת — הוספת `used +` לבדיקה.
- **הטסט שמוכיח את התיקון**: `create_ExceedingRemainingQuota_IsRejected` — עובד עם מכסה 20 ו‑18 ימים מאושרים, מנסה בקשה נוספת של 5 ימים (18+5=23>20) ומצפה ל‑400.

## 3. אישור בקשה (approve) ו‑concurrency
- **מצבים לא חוקיים**: בקשה שלא קיימת → 404. בקשה שכבר `APPROVED`/`REJECTED` → 409.
- **אישור של שתי בקשות במקביל**: שתי אישורים בו‑זמנית עלולים לדחוף עובד מעל המכסה גם אם כל אחד בנפרד תקין (למשל שתי בקשות של 6 ימים כל אחת כנגד מכסה של 10). טופל דרך נעילת שורה פסימיסטית (`FOR UPDATE`): קודם נועלת שורת ה‑request עצמו (מונע אישור כפול של אותה בקשה), ואז שורת ה‑employee (מונע אישור מקביל של שתי בקשות שונות של אותו עובד) — בתוך אותה טרנזקציה, כך שהאישור השני "רואה" את התוצאה המחויבת (committed) של הראשון לפני שהוא בודק את המכסה. הוכחתי את זה בטסט אמיתי עם שני threads בפועל (`approve_ConcurrentApprovalsExceedingQuota_OnlyOneSucceeds`), לא רק תיאורטית.
- **תקלה נוספת שמצאתי בבדיקה ידנית** (לא ב‑code review): שתי בקשות חופשה של אותו עובד יכלו לחפוף בתאריכים (למשל 31/08–02/09 ו‑01/09–05/09) בלי שהמערכת תמנע זאת — היא בדקה רק סה"כ ימים, לא התנגשות בתאריכים בפועל. תוקן: שאילתה שמוצאת בקשות לא‑דחויות של אותו עובד שחופפות בטווח, נבדקת ב‑`create()` ומחזירה 409.

## 4. על מה ויתרתי בגלל הזמן
- **ניקוי RxJS** (`takeUntilDestroyed`) ב‑4 מקומות ה‑`subscribe()` בקומפוננטה — לא בוצע. עם עוד יום הייתי מוסיף את זה בכל מקום, כולל בדיקה חוזרת שה‑optimistic update של `approve()` (עדכון ישיר של שורה ב‑array) עדיין עובד נכון תחתיו.
- **`environment.ts`**: כתובת ה‑API עדיין hardcoded בקוד (ב‑`LeaveRequestsService`) במקום דרך `src/environments/environment.ts` + `fileReplacements` ב‑`angular.json` (המנגנון הטבעי של Angular לזה — לא `.env`, כי ה‑build system של Angular 17 לא קורא `.env` בלי תלות חיצונית נוספת). עם עוד יום הייתי מעביר את זה.
- **בדיקת "אין תאריך עבר"**: הטופס מאפשר להגיש בקשה לתאריכים שכבר עברו. החלטתי שזה לא בהכרח באג — מערכות HR אמיתיות מאפשרות לעיתים קרובות דיווח למפרע (חופשת מחלה שמדווחים עליה אחרי החזרה, תיקון רישום שפוספס), וה‑README לא ביקש חוק כזה. עם עוד יום הייתי בודק עם בעל המוצר לפני שמחליט.
- **בונוס אבטחה** (`GET /api/leave-requests/search`) — ראו סעיף 5.
- **עיצוב UI**: השארתי CSS מינימלי, בלי modals/הרחבות — ה‑README מבקש במפורש לראות שיקול דעת בסדרי עדיפויות ולא מוצר מלוטש, וה‑over‑design כאן היה scope creep ביחס למשימות שבאמת נבדקות.

## 5. שימוש ב‑AI
### איפה AI עזר (כולל prompts)
1. **prompt**: "explain me exactly what is the bug" → קיבלתי אבחון מדויק עם מספרי שורות והמשתנה הספציפי שחסר בבדיקה (`used`), כולל תרחיש כשלון מספרי קונקרטי (18 used + 5 new > 20 quota). זה הוביל ישירות לתיקון של שורה אחת ולמספרים בטסט החדש.
2. **prompt מפורט** (בבקשה לרפקטור שכבת Service): "Act as a senior Java Spring Boot developer. Refactor the existing `LeaveRequestController` by extracting the business logic into a new Service layer... [7 strict constraints, כולל `@ResponseStatus` על exceptions, בלי interface, `/search` לא נוגעים בו]" → קיבלתי את כל המבנה (Service, שלוש חריגות, Controller רזה) בהתאם למגבלות. **אבל**: constraint 3 (`@ResponseStatus`) ו‑constraint 7 (הטסטים הקיימים חייבים להמשיך לעבור) התנגשו בפועל — ראו "דחיתי/תיקנתי" למטה.
3. **prompt מפורט** (טופס Angular): בקשה מפורטת ל‑Reactive Form עם ולידציית cross‑field, בלי service extraction (מתוכנן לשלב נפרד), בלי שדה days → יושם כמבוקש, כולל טסט חי בדפדפן אמיתי מול ה‑API האמיתי (submit ריק, טווח תאריכים הפוך, הגשה תקינה, הגשה שחורגת ממכסה).

### איפה דחיתי/תיקנתי הצעה של AI
- ב‑prompt מס' 2 (שכבת Service), הבקשה ביקשה `@ResponseStatus` על החריגות "כדי שה‑Controller לא יצטרך לבנות `ResponseEntity` ידנית" — אבל זה **שבר בפועל 4 מתוך 6 הטסטים הקיימים**. הסיבה: `@ResponseStatus` פועל רק כש‑Spring MVC בעצמו תופס את החריגה (דרך dispatch אמיתי של HTTP), אבל הטסטים בפרויקט קוראים ל‑`controller.create()`/`controller.approve()` **ישירות כקריאת Java רגילה**, בלי שכבת ה‑servlet בכלל — אז החריגות פשוט "עפו" דרך הטסט בלי להיתרגם לקוד סטטוס. גיליתי את זה רק כשהרצתי `mvn test` בפועל, לא מקריאת ה‑prompt. תיקנתי: חזרתי ל‑`try/catch` מפורש ב‑Controller (כמו לפני הרפקטור), והסרתי את `@ResponseStatus` מהחריגות כי הוא היה קוד מת שרק מטעה קורא עתידי לחשוב שהוא עושה את המיפוי.

### אבטחה
- **מה מצאתי**: SQL Injection ב‑`GET /api/leave-requests/search` (`backend/src/main/java/com/example/leavemanagement/controller/LeaveRequestsController.java`, מתודת `search`) — הפרמטר `name` מוכנס ישירות כ‑string concatenation לתוך שאילתת SQL native (`"... WHERE name LIKE '%" + name + "%'"`).
- **הסיכון**: תוקף יכול לשלוח ב‑`name` קטע SQL (למשל `' UNION SELECT ...--`) ולשנות את השאילתה בפועל — לחלץ נתונים שלא אמורים להיות נגישים, לעקוף את הסינון המיועד, או במקרים חמורים יותר לתמרן/לחשוף מידע ממסד הנתונים כולו.
- **תיקון**: לא בוצע בגלל הזמן — נשאר כ‑endpoint ידוע כפגיע. התיקון הנכון: החלפת ה‑string concatenation בשאילתת JPQL/פרמטרית (`@Query` עם `:name` bind parameter, או `Repository` method כמו `findByEmployee_NameContaining`), בדיוק כמו שאר השאילתות בקוד.

## 6. הוראות הרצה
- אין שינוי מהוראות ה‑README המקורי — `docker compose up --build` מרים DB+API יחד כמתואר.

</div>
