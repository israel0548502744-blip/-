# TripoSR על הטלפון (Android, NPU של Snapdragon)

הפרויקט ממיר את [TripoSR](https://github.com/VAST-AI-Research/TripoSR) לאפליקציית Android שהופכת תמונה אחת למודל תלת־ממד. כל החישוב רץ על הטלפון עצמו, בלי שרת. האפליקציה בנויה ל-Galaxy S25 Ultra (Snapdragon 8 Elite) ומריצה את המודל על ה-NPU (Hexagon) דרך ONNX Runtime + Qualcomm QNN. אפשר לבחור גם GPU (Adreno) או CPU.

## התקנה ועדכונים

1. מורידים את `TripoSR.apk` מה-[Release האחרון](https://github.com/israel0548502744-blip/-/releases/latest) ומתקינים (צריך לאשר התקנה ממקור לא ידוע). הקובץ קטן, עשרות MB בלבד.
2. במסך הבית לוחצים **הורד מודל**. ההורדה היא כ-1.7GB ומתבצעת **פעם אחת בלבד**. היא נמשכת ברקע וממשיכה גם אחרי ניתוק.
3. **עדכונים:** האפליקציה בודקת לבד אם יש גרסה חדשה ומציגה כפתור **עדכן עכשיו**. מורידים רק את האפליקציה; המודל נשאר מותקן.

> הגרסה הראשונה (build 3, עם המודל בתוך ה-APK) חתומה במפתח אחר. לכן צריך להסיר אותה פעם אחת לפני התקנת הגרסה החדשה. מכאן והלאה העדכונים מותקנים זה על זה.

שני workflows ב-GitHub Actions:
- `build-apk.yml` בונה את האפליקציה בכל שינוי ב-`android/` ומפרסם Release בשם `apk-N`. ה-updater קורא את ה-Release הזה.
- `build-models.yml` ממיר את TripoSR ל-ONNX ומפרסם את הקבצים ל-Release בשם `models-v1`. רץ רק כשההמרה משתנה.

## שימוש

1. **צור מודל חדש** ← בוחרים תמונה מהגלריה, מהמצלמה או מקבצים.
2. בוחרים מעבד ורמת פירוט: **NPU** הכי מהיר אבל ניסיוני, **GPU** מומלץ, **CPU** הכי יציב. רמת הפירוט היא 128, 192 או 256.
3. **צור מודל 3D**. מסך ההתקדמות מראה אחוזים ושלבים. **אפשר לצאת מהאפליקציה**: העבודה ממשיכה ברקע עם התראה.
4. במסך התוצאה: תצוגה תלת־ממדית עם רשת, צבע, תאורה, רצפה וסיבוב אוטומטי. אפשר להחליק ולפשט את המודל, ולייצא ל-GLB, OBJ, STL או PLY: לשמור, לשתף או לפתוח בצופה חיצוני.
5. כל מודל נשמר ב**פרויקטים**. יש גם ווידג'ט למסך הבית.

## דוחות קריסה

- אם האפליקציה נסגרה בגלל תקלה, בהפעלה הבאה מופיע כרטיס עם הכפתור **העתק דוח קריסה**. הדוח כולל את סוג הקריסה (גם קריסה ב-native code של ה-NPU), פרטי המכשיר ושורות הלוג האחרונות לפני הקריסה.
- אם מעבד גרם לקריסה, האפליקציה עוברת אוטומטית למעבד הבא (NPU ← GPU ← CPU). אפשר לאפס את זה בהגדרות.
- בכל רגע אפשר לפתוח **דוחות** בתפריט התחתון ולהעתיק דוח מצב מלא.

## איך זה בנוי

```
third_party/TripoSR     הקוד המקורי של TripoSR (git submodule)
convert/export_onnx.py  ממיר את TripoSR לשני קובצי ONNX
android/                אפליקציית Kotlin
.github/workflows/      בנייה אוטומטית: הורדת משקלים, המרה, APK, Release
```

TripoSR מפוצל לשני חלקים:

| קובץ | מה הוא עושה | איפה רץ |
|---|---|---|
| `triposr_encoder.onnx` | תמונה 512×512 → triplane (`[3,40,64,64]`). DINO ViT-B/16 + טרנספורמר של 16 שכבות, כ-420 מיליון פרמטרים | NPU / GPU / CPU |
| `triposr_decoder.onnx` | triplane + 32,768 נקודות → צפיפות וצבע (NeRF MLP קטן) | NPU / GPU / CPU, נקרא שוב ושוב על רשת תלת־ממדית |
| `u2netp.onnx` | הסרת רקע (אותו מודל כמו `rembg`) | CPU |

אחרי הרצת המודלים, האפליקציה מחלצת משטח מהצפיפות (Surface Nets, שמחליף את marching cubes של המקור). אחר כך היא צובעת כל קודקוד לפי הצבע שהמודל מחזיר, ושומרת GLB או OBJ.

### הרצת ההמרה במחשב (לא חובה)

```bash
git clone --recursive <repo>
pip install torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu
pip install -r convert/requirements.txt
python convert/export_onnx.py --out android/app/src/main/assets --verify
curl -L -o android/app/src/main/assets/u2netp.onnx https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx
cd android && gradle assembleRelease
```

אפשר גם לבדוק את ההמרה בלי להוריד משקלים: `--random-weights --verify`.

## מגבלות ידועות

- ה-GPU נבדק על S25 Ultra ועובד (כ-32 שניות ברזולוציה 128). ה-NPU עדיין לא עבד; דוח הקריסה יראה למה.
- מפתח החתימה (`android/keystore`) נמצא בריפו כדי שהעדכונים יותקנו זה על זה. הוא לא מיועד להפצה ציבורית.
- המודל רץ ב-fp16 על ה-NPU (`enable_htp_fp16_precision`), כך שייתכנו הבדלים קטנים מול המחשב.
- אין כאן texture baking כמו ב-`--bake-texture` של המקור. הצבע נשמר לכל קודקוד.

## רישיון

TripoSR מופץ תחת רישיון MIT (ראו `third_party/TripoSR/LICENSE`). U2-Net מופץ תחת Apache-2.0.
