#!/bin/bash
# One-time setup per session, plus an optional new project folder.
# usage: bash <skill>/tools/setup.sh [project-name]
# Copies the tools to /home/claude/fv/tools (the skill folder is read-only), makes the sound effects,
# downloads the Heebo font, and (with a name) creates /home/claude/fv/<name>/ ready for script.json / plan.json.
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"; FV=/home/claude/fv; T=$FV/tools
mkdir -p "$T"
cp "$SRC"/align.py "$SRC"/render.js "$SRC"/overlay.html "$SRC"/build.py "$SRC"/sfx.py "$SRC"/matchcard.py "$SRC"/matchcard_d5.py "$SRC"/upscale.py "$SRC"/censor_player.py "$SRC"/career_quiz_overlay.html "$SRC"/overlay_d5.html "$T"/
[ -f "$T/sfx/phonk.wav" ] || python3 "$T/sfx.py"
if [ ! -s "$T/Heebo.ttf" ]; then
  curl -sSL -o "$T/Heebo.ttf" "https://raw.githubusercontent.com/google/fonts/main/ofl/heebo/Heebo%5Bwght%5D.ttf"
fi
[ "$(wc -c < "$T/Heebo.ttf")" -gt 50000 ] || { echo "Heebo.ttf download failed"; exit 1; }
if [ -n "$1" ]; then
  P=$FV/$1; mkdir -p "$P/img" "$P/fonts" "$P/clips"
  cp "$T/Heebo.ttf" "$P/fonts/Heebo.ttf"; cp "$T/overlay.html" "$P/overlay.html"
  echo "project ready: $P"
fi
echo "tools ready: $T"
