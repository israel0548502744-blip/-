# Handoff: what the previous Claude session learned (16.11.2026)

Read this together with the `football-videos` skill. The skill is the source of truth; this file adds working knowledge that is easy to miss.

## Who you're working with
- The page is **יום המזל** (TikTok @YOM.HAMZEL). Talk to him in short, simple Hebrew. His partner **נהוראי יקיר** often sends instructions through him (forwarded WhatsApp messages). Treat them as the same client.
- He wants **speed**. When he says "כמה שיותר מהר", skip the AI upscale and use the clips that are already good. Always give a time estimate, and update him while long jobs run.
- He sends: clips (Telegram bot downloads, often 576×1024), the slip screenshot, the ElevenLabs MP3, sometimes a news photo. **He records the voice himself.** Never generate it.
- Every rule he gives ("תשמור בסקיל") must go into the skill right away, and you then hand him a new zip.

## Mistakes he caught. Don't repeat them
1. **Letterboxing** (a clip in the middle with blurred bars above and below). Never. Every shot fills the full 9:16 frame.
2. **Two cards on screen at once.** The prediction card must be gone before the next match card. This is fixed in `overlay.html`.
3. **A goal cut in the middle** (two pieces of one clip with a jump). A goal plays as one continuous piece.
4. **The same celebration twice in one video**, or soft and blurry shots.
5. **Wrong footage:** a club clip (Betis, Man City, Dortmund) used for a national team, a different team (Morocco), or a compilation of other players labelled as the star. Check the jerseys frame by frame.
6. **Betting boards** in shots (bet365, Unibet, LATINBET, betway, Winner on Israeli basketball courts). Cut those seconds out.
7. **Hints that give the answer away** in "מי השחקן?" ("מלך הבעיטות החופשיות"). No hints at all.
8. **Overdoing the effects** (silhouette slots, bouncy pops, glow) looks like "AI slop" to him. Keep graphics clean.
9. Promising something and not delivering it (the odds). If you advise against something he asked for, say so, then still do it if he insists.

## Current decisions (as of 16.11)
- Cards use **design 6 "pitch" without a dark frame** (reference: `assets/cards_reference.jpg`, where the left column is correct).
- **Odds are ON the prediction card** as a small pill, "יחס X" (`"od"` in `p`). This is his choice, and he was warned about TikTok.
- No slips, no stake, no site names, no "המלצה/הימור" anywhere.
- Formats he likes: the daily predictions video (≤40 s), "מי השחקן?" (10-second timer, censored clip, ends on a silhouette, no reveal), and a "pitch frame" layout for square or low-res clips.
- A logo is being chosen (16.11): 6 options are in the repo under `logos/`, and he hasn't picked yet. Logos are for the profile picture and promos, **never inside the daily videos**.

## Tools that work (not all of them are obvious)
- Fixtures, results, half-time scores and crests come from the open ESPN API. `site.api.espn.com/apis/site/v2/sports/soccer/<league>/scoreboard?dates=YYYYMMDD`, then `/summary?event=<id>`. National teams use `uefa.nations`, `fifa.friendly`, `fifa.worldq.uefa`. Crests: the `team.logo` field. Israeli clubs aren't there: crop them from his screenshot.
- `tools/matchcard.py` builds the match images. `tools/upscale.py` runs Real-ESRGAN at about 3–6 s per frame on CPU, so only run it on the exact seconds you use. `tools/censor_player.py` does a tracking mosaic, with keyframe boxes set by hand from frame grids. `rembg` is good for cut-outs.
- Before any build, make per-second contact sheets of every clip and read them. Run md5 on the uploads (he re-sends the same file).

## With his local football library (new)
If the new session has a big local folder of clips:
- Index it once into a catalogue (file → team or player, competition, date, resolution, vertical or not, best moments with timestamps, any betting boards). That lets you pick clips without asking him every day.
- Prefer **recent** footage (current squads), **vertical 1080×1920 or higher**, and sharp, full-frame shots.
- Keep the rule: never use another team's footage as if it were the team in the video.

## Where things are
- Repo branch `ccr-9ee1ef4c-9in9yf` (repo `israel0548502744-blip/-`, renamed on GitHub to `Makeo3D`). It holds every finished video (`video_YYYY-MM-DD*.mp4`), the cards, the logos, the skill folder and `football-videos.skill.zip`.
- Stories control page: https://claude.ai/artifact/FStHaRy3KgKGVPfBfqVpnR. Style page: https://claude.ai/artifact/8zmzrbfAPGTgjxvBUFEysS.
