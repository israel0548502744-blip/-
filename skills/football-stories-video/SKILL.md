---
name: "football-stories-video"
description: "Football stories knowledge base (156 checked stories + 60 quick facts, in Hebrew) and the method for turning one into a vertical TikTok story video (סרטון סיפור כדורגל / 'הידעתם?'). Use when the user asks for a football story, story ideas, a story video script, or 'what's the next story'."
---

# סרטוני סיפורי כדורגל (Football story videos)

The user makes vertical TikTok football videos (daily predictions, see the `football-tips-video` skill). This skill is for a second format: **interesting football stories**. Talk to the user in short, simple, everyday Hebrew.

## What's in `stories/`
| File | Contents |
|---|---|
| `INDEX.md` | List of every story with its id (`06-1` = file 06, story 1) |
| `01-legends.md` | Legends: Messi's napkin, Maradona, Pelé, Zidane, Cruyff, Yashin, Maldini… |
| `02-comebacks.md` | Comebacks and crazy games: Istanbul 2005, 6-1, 4-0 at Anfield, 7-1, 93:20… |
| `03-cinderella.md` | Underdogs: Leicester, Greece 2004, Denmark 92, Iceland, Morocco, Wrexham… |
| `04-scandals.md` | Hand of God, Zidane headbutt, Suárez, Calciopoli, Cantona, Super League… |
| `05-emotional.md` | Tragedies and comebacks: Munich, Superga, Chapecoense, Eriksen, Zambia, Jota… |
| `06-weird.md` | Weird and funny: Pickles the dog, 149-0, the deliberate own goal, Ali Dia… |
| `07-transfers.md` | Money and transfers: Neymar 222m, Figo's pig head, Bosman, Coutinho… |
| `08-derbies.md` | Derbies: Boca–River in Madrid, Old Firm, Milan flares, Souness's flag… |
| `09-israel.md` | Israeli football: Spiegler 1970, Haifa 3-0 United, Hapoel TA 2002, Paris 1993… |
| `10-hot-2025-2026.md` | Recent: World Cup 2026 (Spain, Cape Verde, 6-4), Kane's Ballon d'Or, PSG, Arsenal… with sources |
| `11-did-you-know.md` | 60 short facts for 10–20 s "הידעתם?" videos |
| `USED.md` | Stories already used in videos (add a row after every video) |

Each story has: **הוק** (opening line), **הסיפור** (the facts, in order), **שאלה לתגובות** (ending question), sometimes **🎬 צילומים** (which footage to ask the user for) and **⚠️** (a detail to check or a tone note).

## Fixed rules
- **Facts only from these files, or checked with a web search that same day.** Never invent numbers, quotes, dates or scorers. A detail marked ⚠️: check it with a web search, or leave it out of the script. Stories in `10-hot-2025-2026.md` are recent: check the main numbers again before the user records.
- Anecdotes marked "לפי הסיפור" / "לפי דיווחים" stay that way in the voiceover ("לפי הסיפור…") — don't turn a legend into a fact.
- **TikTok-safe wording** (same as the tips videos): never "הימור/הימורים/יחס/ווינר/טוטו/המלצה/betting". Leicester: "סיכוי אחד ל-5,000", never "יחס".
- **Tragedies** (`05-emotional.md` and deaths anywhere): calm, respectful tone. Calm hook text ("זה הסיפור הכי עצוב בכדורגל"), **no phonk hit, no pop sounds**, no jokes, no shouting tags in ElevenLabs (use `[calm]`/`[sad]`, not `[excited] [shouting]`). Suggested song: After Dark – Mr.Kitty. Ending: "כתבו 'לזכרם' בתגובות".
- **Visual style = the tips videos' house style**: same captions (Heebo ExtraBold 80px, ≤3 words per chunk), no zoom on clips, no slow motion, best quality, no logo, sharp energetic first frame, the video ends on the comments CTA. Reuse the tools from the `football-tips-video` skill (`align.py`, `render.js`, `overlay.html`, `build.py`, `sfx.py`) — beats are `hook` and `line`; a news/archive photo goes on a beat as `"k":"photo.jpg"`. No `m`/`p` cards (those are for predictions).
- **Length**: aim for **40–45 s** (≈100–115 words of the user's ElevenLabs voice). Ask before going over 50 s. "הידעתם?" videos: 10–20 s (25–50 words).
- **Footage**: Claude can't download match footage. Tell the user exactly which clips to bring (the 🎬 line). Never use footage of a different, identifiable team or match as if it were the story; use a neutral shot (crowd, ball, stadium) instead and say so once.

## Script structure (story video)
1. **Hook** (frame 1, ≤8 words): a shocking number, a contradiction, or a curiosity gap — "149 שערים במשחק אחד!", "הם הפסידו 4-0… ועלו!". Offer 4–5 hook options when writing the script; the story's **הוק** line is one of them.
2. **Setup** (1–2 lines): when, where, who. Short.
3. **Tension** (1–2 lines): what's at stake / why it's impossible.
4. **Climax / twist** (1–2 lines): the moment. Give it its own beat.
5. **Payoff** (1 line): the "wow" fact afterwards (record, quote, what happened next).
6. **CTA** (last beat, nothing after it): the story's question, or "איזה סיפור אתם רוצים שאספר בפעם הבאה? תכתבו בתגובות!" — this one also gives ideas for the next video.

Optional follow line before the climax (like the WhatsApp line in the tips videos): "תשימו עוקב לעוד סיפורים כאלה!" — only if the user wants it.

Write numbers as words the voice says naturally ("שלוש-אפס", "בדקה התשעים ושלוש"); captions can keep digits. `hl` (green) = the key number / name; `hl2` (cyan) = a club or player name.

### Example (`06-1`, ~95 words ≈ 40 s)
```json
[{"type":"hook","vo":"גביע העולם נגנב... וכלב מצא אותו!!"},
 {"type":"line","vo":"מרץ 1966, ארבעה חודשים לפני המונדיאל באנגליה.","hl":"1966"},
 {"type":"line","vo":"גביע העולם נגנב מתערוכה בלונדון, וכל המשטרה של אנגליה מחפשת אותו.","hl":"נגנב"},
 {"type":"line","vo":"שבוע אחר כך, כלב בשם פיקלס יוצא לטיול עם הבעלים שלו.","hl":"פיקלס"},
 {"type":"line","vo":"הוא מתחיל לרחרח חבילה מתחת לשיח, עטופה בעיתון.","hl":"חבילה"},
 {"type":"line","vo":"בפנים: גביע העולם!","hl":"גביע העולם"},
 {"type":"line","vo":"פיקלס הפך לגיבור לאומי, ואפילו הוזמן למסיבת הניצחון של נבחרת אנגליה.","hl":"גיבור לאומי"},
 {"type":"line","vo":"ואנגליה? זכתה באותו קיץ במונדיאל היחיד שלה עד היום.","hl":"המונדיאל היחיד"},
 {"type":"line","vo":"איזה סיפור אתם רוצים שאספר בפעם הבאה? תכתבו בתגובות!","hl":"תכתבו בתגובות"}]
```
Give the user two copy blocks, as in the tips skill: plain text, and an ElevenLabs version (`[excited] [shouting]` before the hook and `[excited]` before the rest — or `[calm]` for a tragedy).

## Series ideas (offer when the user asks "what to do")
- **"הקאמבק הכי מטורף"** — `02` (one comeback per video).
- **"הסיפור שלא ידעתם"** — `06`, `07`.
- **"הסינדרלה של השבוע"** — `03`, plus `10` (Cape Verde, Curaçao, Paraguay).
- **"הכדורגל הישראלי"** — `09` (strong for an Israeli audience).
- **"הידעתם?" ×3** — three facts from one section of `11` in a 20–25 s video.
- **Story of the day linked to the fixtures**: before a derby/big game, pick a story about those clubs (e.g. Liverpool–United → `08-7`; a Milan game → `08-4`; Barça–Real → `01-7` / `08-9`). This also fits next to the daily predictions video.

## When the user asks "what's the next story?"
Read `USED.md`, suggest **3 unused stories** from different categories (one with a strong number hook, one emotional or Israeli, one tied to this week's fixtures), each with its hook line, and let the user pick. After the video, add a row to `USED.md`.

## Adding new stories
Same format (הוק / הסיפור / שאלה לתגובות / 🎬 / ⚠️), into the matching file, next number. Check every number with a web search first; put recent stories in `10-hot-…` with the source links at the bottom. Then regenerate `INDEX.md` (one `### N. title` heading per story).
