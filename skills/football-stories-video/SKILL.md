---
name: "football-stories-video"
description: "All of the user's vertical TikTok football videos in one skill: (1) the daily predictions video (סרטון תחזיות כדורגל, page יום המזל) built from their clips, match images, slips and voiceover — full rules and tools in tips-video.md; (2) football story videos (סרטון סיפור כדורגל / 'הידעתם?') from a knowledge base of 156 checked stories + 60 quick facts in Hebrew. Use for predictions videos, voiceover scripts, football stories, story ideas, or 'what's the next story'. Story marks and view numbers live on the control page https://claude.ai/artifact/FStHaRy3KgKGVPfBfqVpnR (db collection `used`)."
---

# סרטוני כדורגל: תחזיות + סיפורים (Football videos: predictions + stories)

The user makes vertical TikTok football videos in two formats. Talk to the user in short, simple, everyday Hebrew.

| Format | When | Read |
|---|---|---|
| **Daily predictions video** (סרטון תחזיות) | The user sends clips, match images, slips, voiceover, or asks for a predictions script / "כמה פגענו" | **`tips-video.md` — read it fully and follow it exactly.** It is the complete football-tips-video skill: fixed rules, house style, workflow, and the tool files (`align.py`, `render.js`, `overlay.html`, `build.py`, `sfx.py`). Brand assets are in `assets/`. |
| **Story video** (סרטון סיפור / "הידעתם?" / "נחשו מי") | The user asks for a football story, story ideas, a story script | This file + `stories/`. The video build uses the same tools from `tips-video.md` (they include the story graphics below). |

**After every predictions video**, add one line suggesting a story video about one of that day's clubs (look the clubs up in `stories/12-clubs.md`, skip stories already marked used on the control page).

The rest of this file is about story videos.

## Files
| File | Contents |
|---|---|
| `tips-video.md` | The full predictions-video skill (rules, workflow, tool files) |
| `assets/` | Brand assets used by `tips-video.md` (clover logo; the round badge is reference only) |
| `stories/INDEX.md` | List of every story with its id (`06-1` = file 06, story 1) |
| `stories/01-legends.md` | Legends: Messi's napkin, Maradona, Pelé, Zidane, Cruyff, Yashin, Maldini… |
| `stories/02-comebacks.md` | Comebacks and crazy games: Istanbul 2005, 6-1, 4-0 at Anfield, 7-1, 93:20… |
| `stories/03-cinderella.md` | Underdogs: Leicester, Greece 2004, Denmark 92, Iceland, Morocco, Wrexham… |
| `stories/04-scandals.md` | Hand of God, Zidane headbutt, Suárez, Calciopoli, Cantona, Super League… |
| `stories/05-emotional.md` | Tragedies and comebacks: Munich, Superga, Chapecoense, Eriksen, Zambia, Jota… |
| `stories/06-weird.md` | Weird and funny: Pickles the dog, 149-0, the deliberate own goal, Ali Dia… |
| `stories/07-transfers.md` | Money and transfers: Neymar 222m, Figo's pig head, Bosman, Coutinho… |
| `stories/08-derbies.md` | Derbies: Boca–River in Madrid, Old Firm, Milan flares, Souness's flag… |
| `stories/09-israel.md` | Israeli football: Spiegler 1970, Haifa 3-0 United, Hapoel TA 2002, Paris 1993… |
| `stories/10-hot-2025-2026.md` | Recent: World Cup 2026 (Spain, Cape Verde, 6-4), Kane's Ballon d'Or, PSG, Arsenal… with sources |
| `stories/11-did-you-know.md` | 60 short facts for 10–20 s "הידעתם?" videos |
| `stories/12-clubs.md` | Per club: stable facts + the ids of its stories (for matching stories to the day's fixtures) |
| `stories/13-guess-who.md` | 20 "נחשו מי השחקן" quizzes (clues from hard to easy + answer) |
| `stories/CALENDAR.md` | Exact date of 111 stories, for "בדיוק היום לפני X שנים" videos |
| `scripts/` | Ready-to-record scripts, one file per planned video (`2026-11-06_09-9.json`): date, ids, why, footage needed, post text and the beats with graphics |
| `stories/catalog.json` | Everything above as one JSON (built by `tools/build_catalog.py`); the control page is built from it |
| `tools/` | `build_catalog.py`, `build_page.py` + `control_page.template.html` (the control page source) |

Each story has: **הוק** (opening line), **הסיפור** (the facts, in order), **שאלה לתגובות** (ending question), sometimes **🎬 צילומים** (which footage to ask the user for) and **⚠️** (a detail to check or a tone note).

## Fixed rules
- **Facts only from these files, or checked with a web search that same day.** Never invent numbers, quotes, dates or scorers. A detail marked ⚠️: check it with a web search, or leave it out of the script. Stories in `10-hot-2025-2026.md` are recent: check the main numbers again before the user records.
- Anecdotes marked "לפי הסיפור" / "לפי דיווחים" stay that way in the voiceover ("לפי הסיפור…") — don't turn a legend into a fact.
- **TikTok-safe wording** (same as the predictions videos): never "הימור/הימורים/יחס/ווינר/טוטו/המלצה/betting". Leicester: "סיכוי אחד ל-5,000", never "יחס".
- **Tragedies** (`05-emotional.md` and deaths anywhere): calm, respectful tone. Calm hook text ("זה הסיפור הכי עצוב בכדורגל"), **no phonk hit, no pop sounds**, no jokes, no shouting tags in ElevenLabs (use `[calm]`/`[sad]`, not `[excited] [shouting]`). Suggested song: After Dark – Mr.Kitty. Ending: "כתבו 'לזכרם' בתגובות".
- **Visual style = the predictions videos' house style** (`tips-video.md`): same captions (Heebo ExtraBold 80px, ≤3 words per chunk), no zoom on clips, no slow motion, best quality, no logo, sharp energetic first frame, the video ends on the comments CTA. Reuse the tools from the "Tool files" section of `tips-video.md` (`align.py`, `render.js`, `overlay.html`, `build.py`, `sfx.py`) and its setup/run steps — beats are `hook` and `line`; a news/archive photo goes on a beat as `"k":"photo.jpg"`. No `m`/`p` cards (those are for predictions). Story graphics: see "Story graphics" below.
- **Length**: **25–45 s** (≈2.3 words per second of the user's ElevenLabs voice). The ready scripts are short on purpose (≈50–75 words, 22–31 s) so viewers stay to the end; write longer only when the story needs it or the user asks. Ask before going over 50 s. "הידעתם?" videos: 10–20 s (25–50 words).
- **Footage**: Claude can't download match footage. Tell the user exactly which clips to bring (the 🎬 line). Never use footage of a different, identifiable team or match as if it were the story; use a neutral shot (crowd, ball, stadium) instead and say so once.

## Script structure (story video)
1. **Hook** (frame 1, ≤8 words): a shocking number, a contradiction, or a curiosity gap — "149 שערים במשחק אחד!", "הם הפסידו 4-0… ועלו!". Offer 4–5 hook options when writing the script; the story's **הוק** line is one of them.
2. **Setup** (1–2 lines): when, where, who. Short.
3. **Tension** (1–2 lines): what's at stake / why it's impossible.
4. **Climax / twist** (1–2 lines): the moment. Give it its own beat.
5. **Payoff** (1 line): the "wow" fact afterwards (record, quote, what happened next).
6. **CTA** (last beat, nothing after it): the story's question, or "איזה סיפור אתם רוצים שאספר בפעם הבאה? תכתבו בתגובות!" — this one also gives ideas for the next video.

Optional follow line before the climax (like the WhatsApp line in the predictions videos): "תשימו עוקב לעוד סיפורים כאלה!" — only if the user wants it.

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
Give the user two copy blocks, as in `tips-video.md`: plain text, and an ElevenLabs version (`[excited] [shouting]` before the hook and `[excited]` before the rest — or `[calm]` for a tragedy).

## Story graphics (beat fields, built into overlay.html / build.py)
Put these on beats in `script.json`; `align.py` carries them into `timeline.json`.
- `"dt":"1966 · לונדון"` — date/place chip at the top, pops in on that beat (≥2.2 s, soft pop). Use it on the setup beat.
- `"sc":{"a":"ליברפול","b":"מילאן","ga":0,"gb":3}` — scoreboard at the top. **It stays on screen** from its first beat until a beat sets `"sc":false` (or the video ends), and each new score pops with a sound. Make every score change its own beat ("ג'רארד!" → 1-3, "שמיצר!" → 2-3). Team `a` is on the right. Keep team names short (≤8 letters).
- `"big":"0:149"` — huge text in the middle of the screen (punch-in + deep impact sound) for the climax number or word ("שלושער!", "רמז 1"). A score in `big` uses Hebrew order with a colon, the winner's number on the right ("0:8"). It may share a beat with `sc`, never with `k` (both sit above the captions).
- `"calm":true` on the **hook beat** — slow fade, white glow, no punch zoom. For tragedies also run `build.py ... --calm` (no phonk hit, no pops, no effect sounds).
Tested 5.11.2026 on a 15 s sample (Istanbul 2005): chip, scoreboard 0-0 → 0-3 → 1-3 → 2-3 → 3-3, big "6 דקות", calm hook.

## Formats
- **Regular story** — the structure above, 40–45 s.
- **"בדיוק היום לפני X שנים"** — a story whose date (`CALENDAR.md`) is today or in the next days. Hook: "בדיוק היום לפני 19 שנה..." + the story's hook. Check the date with a quick web search first. Round anniversaries (5, 10, 20, 25…) are marked on the control page.
- **"נחשו מי השחקן"** (`13-guess-who.md`, 20–30 s) — hook "נחשו מי השחקן... ב-4 רמזים!", one beat per clue with `"big":"רמז 1"`…, "יש לכם 3 שניות...", answer beat with a photo (`k`), CTA "באיזה רמז ידעתם? תכתבו בתגובות!".
- **"נחשו מי השחקן לפי הקריירה"** (made 9.11 – Zlatan, 32 s; he liked the idea after asking that the opening EXPLAIN the game clearly). Script: hook "נחשו מי השחקן!!" → explain: "אני אראה לכם את כל הקבוצות ששחקן אחד שיחק בהן, לפי הסדר, מהקבוצה הראשונה שלו ועד האחרונה. אתם צריכים לנחש מי הוא, לפני שאני חושף. מוכנים?" → "קבוצה ראשונה: X." "אחר כך: Y." … one club per sentence → "כבר יודעים? יש לכם שלוש שניות... שלוש, שתיים, אחת!" → "זה <name>!" → "באיזו קבוצה אתם ידעתם? תכתבו בתגובות!". Check the club list with a web search first.
  Visuals (v2, 9.11 — he called the first version's silhouette + crest slots + pop animations "AI slop"; keep it CLEAN): background = fast cuts (~2 s) of random sharp football clips from other teams (not the answer player, so nothing is revealed; reuse checked pieces from earlier projects' hq/), captions as usual, a small static white title "נחשו מי השחקן" at the top, and **each time the voice says a club, only that club's crest appears alone in the middle** (white circle 500 px, crest 350 px, soft shadow, one quick 0.12 s scale-in "boom" + impact sound, stays until the next club). Countdown = plain huge white 3/2/1 in the same spot. No silhouettes, no grids, no bouncy pops, no glow. At the answer: the player's own footage, sharp (letterboxed clips: crop the picture, fit to width on a blurred copy). Phonk at 0 and at the reveal. Overlay: `tools/career_quiz_overlay.html` (edit `CR` = [[crest file, time of the club's name]], `CRE`, `CD`, `QS`/`QE` from timeline.json phrases). Crests: ESPN `https://a.espncdn.com/i/teamlogos/soccer/500/<id>.png` (ids from `site.api.espn.com/apis/site/v2/sports/soccer/<league>/teams`).
- **"נחשו את השחקן לפי קטע קצר"** (9.11, Zlatan, 13.6 s — his own idea, the format he prefers over the career quiz). Voice: "נחשו את השחקן לפי קטע קצר! יש לכם חמש שניות! חמש... ארבע... שלוש... שתיים... אחת! זה <name>! כל מי שהצליח, שיכתוב בתגובות!". Edit: hook over fast random football cuts → insert ~1.3 s of silence after "יש לכם חמש שניות" so the clip plays once → the short goal clip (≈1 s, close shot) **with the player pixelated** (mosaic block 24 px that follows him frame by frame: `tools/censor_player.py` — set keyframe boxes `K={frame:(x0,y0,x1,y1)}` from frame grids, cover the whole body incl. legs and the name on the shirt; check a grid of every 2nd frame) loops while the voice counts 5-4-3-2-1 (huge white numbers with black stroke + green glow below the clip, impact sound on each) → at the name: the same clip uncensored + phonk hit → celebration clip → CTA. Static white title chip "נחשו את השחקן" (green text) at the top during the clip. Captions only for the spoken lines (no captions for the count). Pick clips with few other clues (shirt names, score graphics); avoid betting boards.
- **Two parts** (stories marked `2 חלקים` on the page: `02-1` `02-10` `03-1` `03-11` `05-6` `08-1` `09-3` `10-7`) — part 1 stops right before the climax: "מה קרה אחר כך? חלק 2 כבר בפרופיל, תשימו עוקב!" Part 2 opens with one recap line ("בחלק הקודם: מילאן מובילה שלוש-אפס במחצית…") and goes straight to the climax. Post part 2 within 24 hours.
- **"הידעתם?" ×3** — three facts from one section of `11-did-you-know.md`, 20–25 s.

## Series ideas (offer when the user asks "what to do")
- **"הקאמבק הכי מטורף"** — `02` (one comeback per video, with the scoreboard).
- **"הסיפור שלא ידעתם"** — `06`, `07`.
- **"הסינדרלה של השבוע"** — `03`, plus `10` (Cape Verde, Curaçao, Paraguay).
- **"הכדורגל הישראלי"** — `09` (strong for an Israeli audience).
- **"היום בהיסטוריה"** — from the control page's "השבוע בהיסטוריה" strip.
- **"נחשו מי"** — `13`.
- **Story of the day linked to the fixtures**: before a derby/big game, pick a story about those clubs from `12-clubs.md` (e.g. Liverpool–United → `08-7`; a Milan game → `08-4`; Barça–Real → `01-7` / `08-9`).

## Ready scripts and the plan (`scripts/`)
Each planned video is one JSON file: `{"date","ids","part"?,"title","kind","why","footage","post","beats":[...]}`. `beats` is exactly a `script.json` for the video tools (with `dt`/`sc`/`big`/`k` graphics). The control page shows the plan on top with "העתקת קריינות", "גרסת ElevenLabs" and "טקסט לפוסט" buttons.
- **When the user sends a voiceover + clips for a planned video**: take that file's `beats` as `<proj>/script.json` (fix only the words the voice really says), map the 🎬 footage, build as usual, send it, then mark the story used on the control page.
- **When the plan runs out, or the user asks "תכין תוכנית"**: plan the next two weeks, one video a day: anniversaries from `CALENDAR.md` (round ones first), stories about clubs playing big games that weekend (fixtures from ESPN's open API, see "Checking past predictions" in `tips-video.md` for the URL pattern; then `12-clubs.md`), one Israeli story a week, one "נחשו מי" and one "הידעתם ×3" a week, a two-part story, and skip anything marked used. Verify every date and number with a web search before writing. Then `python3 tools/build_catalog.py`, `python3 tools/build_page.py pages/stories-control.html` (repo root), republish to the page's URL, commit.
- The plan in `scripts/` runs to **3.12.2026** (28 videos). Ideas for the next one: 14.12 Messi's napkin (`01-1`, 26 years); 15.12 Bosman ruling (`07-5`); 18.12 the 2022 final, 4 years (`02-10`, good as two parts); 21.12 Charlton 7-6 (`02-12`); 25.12 the Christmas truce (`05-12`, calm); "Welcome to Hell" (`08-11`) for the next English team at Galatasaray.
- **Voice**: the user records the voiceover in the ElevenLabs app from the "גרסת ElevenLabs" text. Generating through the ElevenLabs connector failed on 5.11.2026 ("Unusual activity has been detected on your account, so Free Tier access has been disabled"), so don't try it again unless the user says the account is paid now; give them the text instead.

## The control page (חדר הסיפורים) — what's used, and the numbers
**URL: https://claude.ai/artifact/FStHaRy3KgKGVPfBfqVpnR** (private to the user). It lists every story with filters (topic, club, unused only, has a date, two parts), the "this week in history" strip, a "השתמשתי" button, view/like/comment/share fields per used story, and a "מה עובד" summary (average views per topic, top videos). Marks live in its database, collection `used`, one document per story id: `{date:"YYYY-MM-DD", by:"claude"|"page", title, cat, video?, views?, likes?, comments?, shares?, statsAt?}`.
- **Before suggesting stories**: read it — `ArtifactData` `list`, url above, collection `used` — and never suggest a used id unless the user asks for a repeat.
- **After every story video Claude makes** (as soon as the final file is sent): mark it — `ArtifactData` `set`, collection `used`, doc_id = story id, data `{date, by:"claude", title, cat, video:"<file name>"}` (no `if_version` when the document is new; if it exists, read it and pass its `version`). For a "הידעתם ×3" video mark all three facts. The user can also press "השתמשתי" on the page.
- **Numbers**: the user types views/comments into the page, or sends a TikTok analytics screenshot — then Claude writes them with `ArtifactData` `update` (`views`, `likes`, `comments`, `shares`, `statsAt`, pinned with `if_version`). Once 5+ stories have numbers, read them before suggesting: favor the topics with the best average views/comments and say why in one line.
- **When stories are added**: run `python3 tools/build_catalog.py`, then `python3 tools/build_page.py pages/stories-control.html` (repo root) and republish to the same URL (`Artifact` publish with `url`; read it first if this conversation hasn't).

## Connected tools (state on 5.11.2026)
- **Upload-Post** is connected (profile `default`) but **no social account is linked** and the plan is the free one. To get TikTok numbers here and to schedule posts: the user links TikTok / Instagram / YouTube at https://app.upload-post.com/manage-users. Publishing to TikTok needs the Basic plan (https://app.upload-post.com/pricing?plan=basic&interval=monthly&autostart=1); Instagram and YouTube work on the free plan within its monthly cap. Once linked: `list_users`, then `get_analytics` / `get_post_analytics` for views and comments → write them to the control page. Cross-post story videos as Reels/Shorts with the `upload-post:schedule-campaign` skill — only when the user asks for that post; the same wording rules apply on every platform.
- **vidIQ** is connected, with **no YouTube channel and 0 credits** (150 renew on 7.11.2026). It reads YouTube channels and Instagram accounts, not TikTok. Useful for trend research: `vidiq_instagram_tiktok_outlier_search` (e.g. "football story", "סיפורי כדורגל", "did you know football") to see which hooks and topics are over-performing now. Check `vidiq_balance` first and tell the user the credit cost before spending.
- No connector reads TikTok directly; the fallback is always the user's screenshot or typing the numbers into the page.

## When the user asks "what's the next story?"
Read the control page's `used` collection, suggest **3 unused stories** from different formats/categories (one with a strong number hook, one Israeli or emotional, one tied to this week's fixtures or a date in the next days), each with its hook line and why (numbers, date, fixture), and let the user pick.

## Adding new stories
Same format (הוק / הסיפור / שאלה לתגובות / 🎬 / ⚠️), into the matching file, next number. Check every number with a web search first; put recent stories in `10-hot-…` with the source links at the bottom; add an exact date to `CALENDAR.md` and the clubs to `12-clubs.md`. Then regenerate `INDEX.md`, `catalog.json` and the control page (see above).

## Keeping this skill up to date
`tips-video.md` started as a copy of `skills/football-tips-video/SKILL.md` (5.11.2026) and is now the master: its overlay.html and build.py also have the story graphics. When a predictions rule changes, update `tips-video.md` here, then re-zip this folder as `football-stories-video.skill.zip` (repo root) and tell the user to re-upload it. On claude.ai, uploaded skills are read-only: what changes between videos (used marks, numbers) lives on the control page, which every conversation can read and write.
