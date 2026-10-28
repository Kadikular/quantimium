#!/usr/bin/env python3
"""
Checks the player-facing text against the writing style: the language file, wiki/data/descriptions.yml and the
wiki pages.

    python3 tools/wiki/lint_text.py              # report, exit 1 if there are errors
    python3 tools/wiki/lint_text.py --warnings   # also list the warnings
    python3 tools/wiki/lint_text.py lang         # one of: lang, descriptions, wiki

Errors are rules with no good exception (British spelling, "beam", filler words, over-long tooltips and
descriptions). Warnings are things to look at (stacked punctuation, long sentences).
"""

from __future__ import annotations

import glob
import json
import os
import re
import sys
from typing import Iterator, List, Tuple

import yaml

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "quantimium", "lang", "en_us.json")
DESCRIPTIONS = os.path.join(ROOT, "wiki", "data", "descriptions.yml")
PAGES = os.path.join(ROOT, "wiki", "pages")

# Limits, in words.
TOOLTIP_MAX = 16          # a hard cap; the aim is about 8
DESCRIPTION_MAX = 20      # whole description
DESCRIPTION_FIRST_MAX = 14
MESSAGE_MAX = 16
SENTENCE_WARN = 30

BRITISH = {
    r"stabilis": "stabiliz", r"materialis": "materializ", r"synchronis": "synchroniz", r"normalis": "normaliz",
    r"optimis": "optimiz", r"minimis": "minimiz", r"maximis": "maximiz", r"utilis": "utiliz", r"crystallis": "crystalliz",
    r"customis": "customiz", r"visualis": "visualiz", r"summaris": "summariz", r"categoris": "categoriz",
    r"initialis": "initializ", r"polaris": "polariz", r"finalis": "finaliz", r"recognis": "recogniz", r"colour": "color", r"\bgrey": "gray", r"centre": "center",
    r"neighbour": "neighbor", r"behaviour": "behavior", r"organis": "organiz", r"\bmetre": "meter",
    r"realis(?:e|ed|ing|ation)": "realiz", r"unrealis": "unrealiz", r"analys(?:e|ed)": "analyz",
    r"favour": "favor", r"centerd": "centered", r"armour": "armor", r"\bwhilst\b": "while", r"\bround\b(?= (?:the|it|them|each|a|an|its|their|his|her|you)\b)": "around",
}
# Names that are spelled the British way in the mod's own vocabulary and kept: none, today.
KEEP: List[str] = []

BANNED = {
    r"\bsimply\b": "filler", r"\bessentially\b": "filler", r"\bbasically\b": "filler", r"\bactually\b": "filler",
    r"\bvarious\b": "filler", r"\ba number of\b": "filler", r"\bin order to\b": "use 'to'",
    r"\ballows? you to\b": "use the verb itself", r"\bletting you\b": "use the verb itself",
    r"\bbeams?\b": "say 'laser'", r"\bmerely\b": "filler", r"\bjust\b(?= (?:a|an|the|one|two|three)\b)": "filler",
}
# Metaphor the style guide rules out (warnings: a few are fine in a quote or a joke).
IMAGERY = [r"\bprowls?\b", r"\bhungry\b", r"\bwound\b", r"\bbills? you\b", r"\bdecided?\b", r"\bweather\b",
           r"\bwhisper", r"\btapestry\b", r"\bdelve", r"\bseamless"]

Finding = Tuple[str, str, str, str]   # level, where, rule, text


def words(text: str) -> int:
    return len(re.findall(r"[A-Za-z0-9%$][\w'%/.\-]*", re.sub(r"%[0-9$]*[sd]", "N", text)))


# Ids, code, links and constants keep their spelling: the mod's ids and class names are British.
PROTECTED = re.compile(r"\[\[.*?\]\]|\{\{.*?\}\}|`[^`\n]*`|\]\([^)]*\)|(?:href|src|class)=\"[^\"]*\"|https?://\S+")


def prose_findings(where: str, text: str, level_for_banned: str = "error") -> Iterator[Finding]:
    shown = text
    text = PROTECTED.sub(" ", text)
    for pattern, replacement in BRITISH.items():
        if re.search(pattern, text, re.I) and not any(re.search(k, text) for k in KEEP):
            yield ("error", where, f"spelling: use American '{replacement}'", shown)
    for pattern, why in BANNED.items():
        if re.search(pattern, text, re.I):
            yield (level_for_banned, where, f"banned word ({why})", text)
    for pattern in IMAGERY:
        if re.search(pattern, text, re.I):
            yield ("warning", where, f"imagery ({pattern})", text)
    stripped = re.sub(r"\[\[[^\]]*\]\]|\{\{[^}]*\}\}|`[^`]*`", "x", text)
    for sentence in re.split(r"(?<=[.!?])\s+", stripped):
        if words(sentence) > SENTENCE_WARN:
            yield ("warning", where, f"sentence over {SENTENCE_WARN} words", sentence[:100])
        if sentence.count(";") + sentence.count(":") >= 3:
            yield ("warning", where, "three or more colons/semicolons in one sentence", sentence[:100])


def lint_lang() -> Iterator[Finding]:
    with open(LANG) as handle:
        strings = json.load(handle)
    for key, value in strings.items():
        where = f"lang:{key.replace('quantimium.', '', 1)}"
        yield from prose_findings(where, value)
        if re.search(r"\.(tip|tooltip)(\.|$)", key) and words(value) > TOOLTIP_MAX:
            yield ("error", where, f"tooltip over {TOOLTIP_MAX} words ({words(value)})", value)
        elif key.startswith("message.") and words(value) > MESSAGE_MAX:
            yield ("warning", where, f"message over {MESSAGE_MAX} words ({words(value)})", value)
        if value.startswith(("The ", "A ")) and re.fullmatch(r"(item|block)\.quantimium\.[a-z0-9_]+", key):
            yield ("error", where, "item or block name starts with an article", value)


def lint_descriptions() -> Iterator[Finding]:
    with open(DESCRIPTIONS) as handle:
        data = yaml.safe_load(handle) or {}
    for name, text in data.items():
        where = f"descriptions:{name}"
        text = str(text).strip()
        yield from prose_findings(where, text)
        if words(text) > DESCRIPTION_MAX:
            yield ("error", where, f"description over {DESCRIPTION_MAX} words ({words(text)})", text)
        first = re.split(r"(?<=[.!?])\s+", text)[0]
        if words(first) > DESCRIPTION_FIRST_MAX:
            yield ("error", where, f"first sentence over {DESCRIPTION_FIRST_MAX} words ({words(first)})", first)


def lint_wiki() -> Iterator[Finding]:
    for path in sorted(glob.glob(os.path.join(PAGES, "**", "*.md"), recursive=True)):
        rel = os.path.relpath(path, PAGES)
        if rel.startswith("guides/writing-"):
            continue   # these pages quote the banned words on purpose
        with open(path) as handle:
            text = handle.read()
        # Code, tables of markup and the page's own tags are not prose.
        text = re.sub(r"```.*?```", "", text, flags=re.S)
        for number, line in enumerate(text.split("\n"), 1):
            if line.lstrip().startswith(("|", "<", "![", "#", "---")):
                continue
            yield from prose_findings(f"wiki:{rel}:{number}", line)


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    show_warnings = "--warnings" in sys.argv
    scopes = {"lang": lint_lang, "descriptions": lint_descriptions, "wiki": lint_wiki}
    chosen = [scopes[a] for a in args] if args else list(scopes.values())
    findings: List[Finding] = [f for scope in chosen for f in scope()]
    errors = [f for f in findings if f[0] == "error"]
    warnings = [f for f in findings if f[0] == "warning"]
    for level, where, rule, text in errors + (warnings if show_warnings else []):
        print(f"{level}: {where}: {rule}\n    {text[:140]}")
    print(f"\n{len(errors)} errors, {len(warnings)} warnings" + ("" if show_warnings else " (--warnings to list)"))
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
