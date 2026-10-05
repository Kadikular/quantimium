# Quantimium wiki

The player-facing wiki, built with MkDocs Material from hand-written pages (`pages/`) and pages
generated from the mod itself. It is the place for what the mod does now.

```bash
python3 -m venv .venv-wiki && .venv-wiki/bin/pip install mkdocs-material pillow   # once, from the repo root
.venv-wiki/bin/python tools/wiki/build.py             # build to wiki/build/site/index.html
.venv-wiki/bin/python tools/wiki/build.py --serve     # live preview
```

`wiki/build/` is generated and not committed. See `pages/guides/writing-the-wiki.md` for the markup and
how pages tie to tests.
