---
name: football-videos
description: "All of the user's vertical TikTok football videos for his page יום המזל, in one skill. (1) The daily predictions video (סרטון תחזיות כדורגל): built from his clips, match details, slips and voiceover, including writing the voiceover script, our own match images, and checking past predictions (\"כמה פגענו\"). (2) Football story videos (סרטון סיפור כדורגל, \"הידעתם?\", \"נחשו מי השחקן\", \"היום לפני X שנים\") from a checked Hebrew knowledge base of 157 stories, 60 short facts and 20 quizzes, with ready scripts and a content plan. Use for any predictions video, voiceover script, football story, story idea, \"מה הסיפור הבא\", content plan, or a promo/edit for the page."
---

# סרטוני כדורגל: תחזיות + סיפורים (football-videos)

He makes vertical 9:16 TikTok football videos for his page **יום המזל** in two formats. One house style, one set of tools, one skill. Talk to him in short, simple, everyday Hebrew.

## Which file to read
| He asks for / sends | Read fully, then follow exactly |
|---|---|
| **Predictions video** (סרטון תחזיות): clips, match images, slips, a voiceover, "תכתוב קריינות", "כמה פגענו", a match image | `predictions.md` |
| **Story video** (סרטון סיפור / "הידעתם?" / "נחשו מי"): a story, story ideas, a story script, "מה הסיפור הבא", "תכין תוכנית" | `story-videos.md` **and** `predictions.md` (the house style and the build steps live there) |
| **Promo, beat-synced edit, "crazy" collage edit** | `predictions.md` → "Special one-off projects" |

Never build from memory of an older conversation: the rules change often and these files hold the latest version.

## Rules that hold in every video
1. **TikTok-safe wording.** "תחזית/תחזיות" only. Never "המלצה", "הימור/הימורים", "יחס", "ווינר", "טוטו", "betting" — in the voiceover, captions, cards, post text, on every platform. No betting brands on screen, no slip screenshots, no odds.
2. **Facts.** Predictions videos: opinion and atmosphere only, no invented stats. Story videos: facts only from `stories/` or checked with a web search that day; a legend stays "לפי הסיפור".
3. **House style.** Captions Heebo ExtraBold 80px, max 3 words per chunk, middle of the screen. No zoom on clips, no slow motion, no repeated frames, no blurry close-ups, best quality. No logo in the video. The video ends on the comments CTA + 0.7 s, nothing after it.
4. **First 2 seconds.** Sharp, energetic, colourful first frame with the hook text on it. Offer 4–5 hook options when writing a script.
5. **Cards are design 6 "pitch"** (since 9.11; design 5 retired): a bright mowed football pitch with white lines inside a dark frame with green edge glow — the match image (`tools/matchcard.py`), the prediction card and the WhatsApp card (`tools/overlay.html`). Details in `predictions.md` → "Match images".
6. **Voice.** He records every voiceover himself in ElevenLabs and sends the MP3. Never generate it with the ElevenLabs connector. Give him two copy blocks: plain text and an ElevenLabs version with tags.
7. **Footage.** Claude can't download match footage or songs; say exactly which clips to bring. Never pass off another identifiable team or match as the one in the video.
8. **Music.** Deliver one version without music and suggest one song from his approved list (`predictions.md`).
9. **Tragedies** (story videos): calm tone, calm hook, `--calm` build, no effect sounds, no jokes.
10. **Ask before building** when something that changes the result is unclear (which clip is which team, missing clip, wording).

Length: predictions ≤ 40 s. Stories 25–45 s (ask before going over 50 s). "הידעתם?" 10–20 s.

## One build pipeline for both formats
```
bash <skill folder>/tools/setup.sh <name>          # tools → /home/claude/fv/tools, sounds, font, project dir
cd /home/claude/fv                                 # then put script.json, plan.json, vo_raw.mp3, img/ in <name>/
python3 tools/align.py <name> 39                   # voice timing → timeline.json
node tools/render.js <name>/timeline.json <name>/ov <end+0.7> 30
python3 tools/build.py <name> <name>/video_final.mp4 [--calm]     # ~10 min at best quality: run in the background
```
`script.json` is a list of beats. Beat types: `hook`, `line`, `match`, `tip`, `outro`. Fields on a beat:

| Field | Shows | Format |
|---|---|---|
| `vo` | The words the voice says (drives the captions and the timing) | both |
| `hl` | Words coloured green | both |
| `m` | Match image | predictions |
| `p` | Prediction card `{match, pick}` — text must match the slip exactly | predictions |
| `wa` | WhatsApp card | predictions |
| `k` | Photo with a green frame | both |
| `dt` | Date/place chip, e.g. `"1966 · לונדון"` | stories |
| `sc` | Scoreboard `{a,b,ga,gb}`, stays until `"sc":false` | stories |
| `big` | Huge number or word in the middle | stories |
| `calm` | Calm hook (on the hook beat) | stories |

Details and timing of each field: `predictions.md` (m, p, wa, k) and `story-videos.md` → "Story graphics" (dt, sc, big, calm).

## Before sending any video
- Frame grid: hook, every card and graphic, the final CTA. Read the image.
- Length within the limit; the shot list printed by `build.py` shows every slowdown factor ≤ 1.05.
- No zoom (compare a frame of each shot with its source frame), no flash frames, no blurry shots, no betting boards.
- Captions readable, cards on time, Hebrew rendered right.
- Send one file without music, with: what's in it, what to check, a ready post text (no hashtags), one song suggestion.

## After sending
- **Predictions video** → add one line suggesting a story video about one of that day's clubs (`stories/12-clubs.md`, skip used stories).
- **Story video** → mark it used on the stories control page right away (`story-videos.md` → "The control page").

## The two control pages
| Page | URL | Holds |
|---|---|---|
| Studio (predictions style picks) | https://claude.ai/artifact/8zmzrbfAPGTgjxvBUFEysS — db collection `studio`, doc `v2` | His saved style choices. The rules in `predictions.md` win over older values there. |
| חדר הסיפורים (stories) | https://claude.ai/artifact/FStHaRy3KgKGVPfBfqVpnR — db collection `used` | Which stories were used, view/like/comment numbers, the content plan. Read it before suggesting a story. |

## Files
| Path | Contents |
|---|---|
| `predictions.md` | Predictions videos: fixed rules, lessons, our match images, editing style, workflow, special projects |
| `story-videos.md` | Story videos: rules, script structure, story graphics, formats, series ideas, the plan, the control page, connected tools |
| `tools/` | `setup.sh`, `align.py`, `render.js`, `overlay.html`, `build.py`, `sfx.py`, `matchcard.py` (video), `upscale.py` (Real-ESRGAN), `censor_player.py` + `career_quiz_overlay.html` (quiz videos), `matchcard_d5.py`/`overlay_d5.html` (old design 5), `build_catalog.py`, `build_page.py`, `control_page.template.html` (stories control page) |
| `assets/` | `clover_logo.webp` (promo videos only); `logo.png` is reference only, never in a video |
| `stories/INDEX.md` | Every story with its id (`06-1` = file 06, story 1) |
| `stories/01-legends.md` … `10-hot-2025-2026.md` | The stories: legends, comebacks, underdogs, scandals, emotional, weird, transfers, derbies, Israel, recent (with sources) |
| `stories/11-did-you-know.md` | 60 short facts |
| `stories/12-clubs.md` | Per club: stable facts + the ids of its stories |
| `stories/13-guess-who.md` | 20 "נחשו מי השחקן" quizzes |
| `stories/CALENDAR.md` | Exact dates, for "בדיוק היום לפני X שנים" |
| `stories/catalog.json` | Everything above as one JSON (built by `tools/build_catalog.py`) |
| `scripts/` | 28 ready-to-record story scripts, one per planned day, up to 3.12.2026 |

## Keeping this skill up to date
This skill replaces `football-tips-video` and `football-stories-video` (merged 10.11.2026; where they disagreed, the newer rule from 8.11 won; the 9.11 changes — design 6, the quiz formats, censor and AI-upscale tools — were added on install). An uploaded skill is read-only: what changes between videos (used marks, numbers, style picks) lives on the two control pages. When he changes a rule, write it into the right `.md` in a copy of this folder, zip the folder as `football-videos` and tell him to replace the skill.
