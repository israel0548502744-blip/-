"""Builds the public legal pages (GitHub Pages, docs/) from the texts shipped in the app.

  python convert/legal_pages.py

The app's assets/legal/*.txt are the single source: first line = title, numbered lines =
headings, "- " lines = bullets. Run after editing them, then commit docs/*.html.
"""
import html
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "assets", "legal")
OUT = os.path.join(ROOT, "docs")

STYLE = """
:root { --bg:#0d0824; --card:#16112f; --text:#f6f3ff; --sub:#b3abd6; --accent:#c084fc; }
* { box-sizing:border-box }
body { margin:0; background:var(--bg); color:var(--text); font:16px/1.7 system-ui, -apple-system, "Segoe UI", Rubik, Arial, sans-serif; }
main { max-width:760px; margin:0 auto; padding:32px 16px 64px; }
section { background:var(--card); border:1px solid #ffffff1f; border-radius:20px; padding:24px; margin-bottom:24px; }
h1 { font-size:28px; margin:0 0 4px; background:linear-gradient(90deg,#f1ecff,#c3b2ff); -webkit-background-clip:text; color:transparent; }
h2 { font-size:18px; margin:24px 0 6px; }
p, li { color:var(--sub); margin:6px 0; }
ul { padding-inline-start:20px; }
a { color:var(--accent); }
nav { display:flex; gap:16px; margin-bottom:20px; flex-wrap:wrap; }
.en { direction:ltr; text-align:left; }
"""


def render(path, lang):
    lines = open(path, encoding="utf-8").read().splitlines()
    out, in_list = [], False
    for i, line in enumerate(lines):
        line = line.strip()
        text = html.escape(line)
        text = re.sub(r"(https?://\S+|[\w.+-]+@[\w-]+\.[\w.]+)",
                      lambda m: f'<a href="{"mailto:" if "@" in m.group(1) and "://" not in m.group(1) else ""}{m.group(1)}">{m.group(1)}</a>', text)
        is_item = line.startswith("- ")
        if in_list and not is_item:
            out.append("</ul>")
            in_list = False
        if i == 0:
            out.append(f"<h1>{text}</h1>")
        elif not line:
            continue
        elif is_item:
            if not in_list:
                out.append("<ul>")
                in_list = True
            out.append(f"<li>{text[2:]}</li>")
        elif line[0].isdigit() and len(line) < 60:
            out.append(f"<h2>{text}</h2>")
        else:
            out.append(f"<p>{text}</p>")
    if in_list:
        out.append("</ul>")
    cls = ' class="en" dir="ltr" lang="en"' if lang == "en" else ' dir="rtl" lang="he"'
    return f"<section{cls}>\n" + "\n".join(out) + "\n</section>"


def page(title, body):
    nav = ('<nav><a href="index.html">ראשי</a><a href="terms.html">תנאי שימוש</a>'
           '<a href="privacy.html">מדיניות פרטיות</a></nav>')
    return f"""<!doctype html>
<html lang="he" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Makeo 3D · {html.escape(title)}</title><style>{STYLE}</style></head>
<body><main>{nav}
{body}
</main></body></html>
"""


def main():
    pages = {
        "terms.html": ("תנאי שימוש", [("terms_he.txt", "he")]),
        "privacy.html": ("מדיניות פרטיות · Privacy policy", [("privacy_he.txt", "he"), ("privacy_en.txt", "en")]),
    }
    for name, (title, parts) in pages.items():
        body = "\n".join(render(os.path.join(SRC, f), lang) for f, lang in parts)
        open(os.path.join(OUT, name), "w", encoding="utf-8").write(page(title, body))
    index = ('<section dir="rtl"><h1>מסמכים משפטיים</h1>'
             '<p>תמונה לתלת־ממד ויצירות להדפסה, ישירות בטלפון. כל העיבוד נעשה במכשיר.</p><ul>'
             '<li><a href="terms.html">תנאי שימוש</a></li>'
             '<li><a href="privacy.html">מדיניות פרטיות (Privacy policy)</a></li></ul></section>')
    open(os.path.join(OUT, "index.html"), "w", encoding="utf-8").write(page("מסמכים משפטיים", index))
    # Serve the folder as plain files (no Jekyll), so only these pages become web pages.
    open(os.path.join(OUT, ".nojekyll"), "w").close()
    print("wrote", ", ".join(list(pages) + ["index.html"]))


if __name__ == "__main__":
    main()
