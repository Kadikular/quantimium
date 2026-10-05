#!/usr/bin/env bash
# Captures every machine GUI into wiki/pages/assets/gui by starting the game, building each machine
# in a fresh creative flat world and screenshotting its screen (see client/dev/WikiScreenshots.java).
# Needs a display; the window opens and closes by itself in about a minute.
set -euo pipefail
cd "$(dirname "$0")/.."

game=runs-26.1/wikishots
rm -rf "$game/saves/wiki_shots"
mkdir -p "$game"
# No partner mods: the shots are of Quantimium's own screens (AE2, which the ME Superposition Crafter
# needs, is part of every dev run), and other mods' load warnings would stop it at a warning screen.
rm -rf "$game/mods"
mkdir -p "$game/mods"
# Skip the first-launch accessibility screen and pin the GUI scale the crops are sized for.
cat > "$game/options.txt" <<'OPTIONS'
onboardAccessibility:false
guiScale:2
tutorialStep:none
joinedFirstServer:true
narrator:0
soundCategory_master:0.0
pauseOnLostFocus:false
OPTIONS

./gradlew runWikiShots
ls -1 wiki/pages/assets/gui
