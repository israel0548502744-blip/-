# TripoSR על הטלפון (Android, NPU של Snapdragon)

הפרויקט ממיר את [TripoSR](https://github.com/VAST-AI-Research/TripoSR) לאפליקציית Android שהופכת תמונה אחת למודל תלת־ממד. כל החישוב רץ על הטלפון עצמו, בלי שרת. האפליקציה בנויה ל-Galaxy S25 Ultra (Snapdragon 8 Elite) ומריצה את המודל על ה-NPU (Hexagon) דרך ONNX Runtime + Qualcomm QNN. אפשר לבחור גם GPU (Adreno) או CPU.

## איך מקבלים את ה-APK

הרשת שבה הקוד נכתב חוסמת את Hugging Face, ושם נמצאים המשקלים של TripoSR. לכן ההמרה והבנייה רצות ב-GitHub Actions:

1. ב-GitHub נכנסים ל-**Actions**, בוחרים **Build TripoSR Android APK** ולוחצים **Run workflow**. ה-workflow רץ גם אוטומטית בכל push שמשנה את `android/` או את `convert/`.
2. בסוף הריצה נוצר **Release** בשם `TripoSR Android build N` ובו הקובץ `TripoSR.apk`.
3. פותחים את דף ה-Release בטלפון (למשל https://github.com/israel0548502744-blip/-/releases/tag/apk-3), מורידים ומתקינים (צריך לאשר התקנה ממקור לא ידוע).

גודל ה-APK כ-1.75GB, כי המודל עצמו (כ-1.7GB) נמצא בתוכו. בהפעלה הראשונה הוא מועתק לאחסון הפנימי, אז כדאי שיהיו כ-4GB פנויים.

## שימוש

1. לוחצים **בחר תמונה** ובוחרים תמונה של אובייקט בודד. הרקע מוסר אוטומטית.
2. בוחרים מעבד: **NPU (Hexagon)**, **GPU (Adreno)** או **CPU**.
3. בוחרים רזולוציה: 128 הכי מהירה, 256 הכי מפורטת.
4. לוחצים **צור מודל 3D**. התוצאה מוצגת על המסך: גוררים כדי לסובב וצובטים כדי להתקרב.
5. **שמור GLB + OBJ** שומר לתיקייה `Download/TripoSR`. **שתף** שולח את קובץ ה-GLB לכל אפליקציה.

> **בפעם הראשונה על NPU** האפליקציה מקמפלת את המודל ל-Hexagon. זה יכול לקחת כמה דקות, והתוצאה נשמרת בזיכרון מטמון כך שבפעמים הבאות זה מהיר. אם ה-NPU לא מצליח לטעון את המודל, האפליקציה עוברת אוטומטית ל-CPU ורושמת את הסיבה ביומן שבתחתית המסך.

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

- ה-NPU נבדק רק עד שלב הבנייה. עדיין לא הורץ על מכשיר אמיתי, אז הגרסה הראשונה היא ניסיון. אם משהו נכשל, היומן בתחתית המסך מראה איפה.
- המודל רץ ב-fp16 על ה-NPU (`enable_htp_fp16_precision`), כך שייתכנו הבדלים קטנים מול המחשב.
- אין כאן texture baking כמו ב-`--bake-texture` של המקור. הצבע נשמר לכל קודקוד.

## רישיון

TripoSR מופץ תחת רישיון MIT (ראו `third_party/TripoSR/LICENSE`). U2-Net מופץ תחת Apache-2.0.
