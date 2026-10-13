# flick

An Android app that turns edge gestures into anything, without ever being rebuilt.

Two layers, and the whole project is about keeping them apart:

1. **The host** — compiled once. An accessibility service plus edge zones: left, right and
   bottom strips, each with a start %, end % and thickness, each catching tap, double-tap,
   long-press, swipe-up, swipe-down and swipe-in. Same trigger model UbikiTouch uses.
2. **The plugins** — never compiled. One text file per feature, dropped into `repo/extensions/`,
   hot-loaded over localhost. Swipe left-to-right, a plugin runs. Swipe up the right edge,
   a different plugin runs.

New feature = new file. New *capability* = the host learns a new bridge call, rebuilds once,
and is free forever after.

## Layout

```
flick/
├── README.md
├── AGENTS.md                 build + code rules for this repo, read before touching anything
├── docs/
│   ├── SPEC.md               architecture, plugin format, bridge API, roadmap, risks
│   └── ci-workflow.template.yml   arm64-only CI, moves to .github/workflows/ in V1
├── repo/                     the plugin repo the app subscribes to
│   ├── repo.json             manifest: id, version, api, file, hash
│   └── extensions/
│       └── abdm-add-download.js   plugin #1
├── triggers.json             gesture -> plugin map
└── scripts/
    ├── serve-repo.sh         serve repo/ on http://127.0.0.1:8090
    └── repo_hash.py          recompute extension hashes into repo.json
```

## Serve the repo

```sh
./scripts/serve-repo.sh
```

The app subscribes to `http://127.0.0.1:8090/repo.json`, hash-checks each extension, and pulls
what changed. Edit a plugin, reload, swipe. No build in the loop.

## Status

- [x] design: `docs/SPEC.md`
- [x] V1 — host skeleton: accessibility service, edge zones, fires `AddDownloadActivity` fallback.
- [x] V2 — QuickJS runtime, `triggers.json`, repo fetch & SHA-256 validation over localhost, bridge calls (`launch`, `shell`, `clipboard`, `http`, `ui.toast`, `ui.find`, `ui.tap`).
- [ ] V3 — in-app plugin list & management, per-app triggers, multi-action menus
- [ ] V4 — snip-region-to-clipboard as a plugin, not a separate app
