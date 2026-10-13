# GESTURE HOST — spec v0.1 (started 2026-10-13)

One app that does two things: **watch gestures** and **run extensions**. Extensions are
plain text, written in the workspace, dropped into a repo folder, hot-reloaded. No Gradle,
no CI, no recompile per feature.

Cloudstream's *pattern*, not its code: host + repo + manifest + plugin unit. The difference
is one deliberate decision, see "Why not DEX" below.

---

## 1. Why not DEX (the Cloudstream-literal route)

Cloudstream's plugin unit is a `.cs3`: a ZIP with `AndroidManifest.xml`, `resources.arsc`
and a `classes.dex` of **compiled Kotlin**, loaded with a `DexClassLoader`, scanned for
`@CloudstreamPlugin` classes, then `Plugin.load(context)` runs and mutates the host's global
API registry. It works, and it is the wrong tool here, for three reasons:

1. **Build cost.** Every extension = a Gradle build. On this tablet that is a GitHub Actions
   round trip of 13-16 minutes, because compiling locally trips Android's LMK and kills the
   session. Ten small features = ten CI cycles. Dev loop is dead on arrival.
2. **Blast radius.** A `.cs3` runs *inside* the host process with the host's permissions. The
   host is an accessibility service, so a plugin inherits the ability to read every screen.
   For a scraper that is a bad day; here it is your OTP, your WhatsApp, your passwords.
3. **Nothing to gain.** These extensions are small: fire a component, hit an endpoint, poke a
   UI node. Bytecode buys us nothing that an interpreter does not.

## 2. Extension = userscript for gestures

One file per feature, metadata in a comment header, body is JavaScript. Same idea as a
Tampermonkey userscript, except the trigger is a gesture instead of a URL match.

```js
// ==GestureScript==
// @name         AB DM: Add Download
// @id           abdm-add-download
// @version      1.0.0
// @api          1
// ==/GestureScript==

const url = args.url || clipboard.text();
if (!url) return ui.toast("No URL on the clipboard");
shell(`am start -n com.abdownloadmanager/...AddDownloadActivity -a android.intent.action.VIEW -d "${url}" -t "${guessMime(url)}"`);
```

Engine: **QuickJS** (`app.cash.quickjs:quickjs-android`), ships prebuilt `arm64-v8a` `.so`,
~1 MB, no NDK build in our pipeline. Fallback if it misbehaves: a headless `WebView` running
the script, which costs ~30 MB RAM we'd rather not spend.

## 3. Trigger config is data too

Not hardcoded. `triggers.json` in app-private storage:

```json
[
  { "id": "right-up", "edge": "right", "gesture": "swipe-up",
    "fromPct": 20, "toPct": 60, "thicknessPx": 24,
    "script": "abdm-add-download", "args": { "url": "https://..." } }
]
```

Gesture vocabulary v1: `tap`, `double-tap`, `long-press`, `swipe-up`, `swipe-down`,
`swipe-left`, `swipe-right`, `swipe-diagonal`. Per-edge zones with start/end percentage so
nothing lands in the system back-gesture strip.

## 4. Bridge API (what a script can reach)

| Call | What it does | Needs |
|---|---|---|
| `shell(cmd)` | runs a shell command | Shizuku |
| `launch("pkg/Activity")` | starts a component, exported or not | Shizuku |
| `shortcut(id)` | fires a registered Android shortcut | - |
| `intent({action, data, pkg})` | raw `am start` | - |
| `clipboard.text()` / `.setText()` / `.setImage(uri)` | clipboard | - |
| `http.get(url)` / `.post(url, body)` | network, no browser window | - |
| `ui.find({text\|id\|desc})` / `.tap(node)` / `.setText` | screen interaction | Accessibility |
| `ui.toast(msg)` / `ui.menu([...])` / `ui.input()` | give feedback, ask for input | - |
| `notify(title, text)` | notification | - |
| `sleep(ms)`, `log(...)`, `store.get/set` | plumbing | - |

`ui.*` is the reason the accessibility service exists. Everything else works without it.

## 5. Repo format (mirrors Cloudstream's shape, because it is a good shape)

```json
{
  "name": "Ammar's Gesture Repo",
  "manifestVersion": 1,
  "plugins": [
    { "id": "abdm-add-download", "name": "AB DM: Add Download", "version": "1.0.0",
      "api": 1, "file": "extensions/abdm-add-download.js", "hash": "sha256:..." }
  ]
}
```

Source of truth is a folder **here in the workspace**, served over local HTTP:

```
cd /workspace/projects/flick/repo && python3 -m http.server 8090
```

App subscribes to `http://127.0.0.1:8090/repo.json`, checks `hash`, pulls on change.
Edit a script, reload the repo, done. No build, no PC, no signing.

## 6. Risks, honestly

1. **LMK kills the service.** 4 GB tablet, ~1.2 GB free. Needs a foreground service with a
   persistent notification plus battery-optimisation exemption, or the gesture layer dies in
   the background and looks broken.
2. **Edge collision.** Android 10+ gesture nav owns the screen edges for back. Two answers:
   keep zones off the back strips, or run 3-button nav and own the edges outright.
3. **Accessibility + sideload.** An accessibility-based host will never survive Play review.
   Fine here, it is a personal build, but it means manual installs forever.
4. **Trust.** Extensions run in-process. Since every extension is written here by us, that is
   currently a non-issue. If a third-party repo ever gets added, it needs signing or a
   sandbox, and that is a v3 conversation.

## 7. Roadmap

- **V1 — prove the loop.** Host skeleton: accessibility service + one edge zone, one hardcoded
  action, fires `AddDownloadActivity`. Confirms the gesture layer survives daily use.
- **V2 — extensions.** QuickJS + metadata parser + `triggers.json` + `repo.json` over
  localhost + 6 bridge calls (`launch`, `shell`, `clipboard`, `http`, `ui.toast`, `ui.find`).
- **V3 — the shop.** In-app list of installed extensions, enable/disable, per-app triggers,
  menu actions (up to N actions per trigger like UbikiTouch Pro does for free).
- **V4 — the other toys.** Snip-region-to-clipboard becomes extension #1 rather than a
  separate app.

## 8. Build pipeline

Reuse the existing playbook, no exceptions:

```
./gradlew :app:assembleDevDebug -Plastchat.abi.splits=true --no-parallel --stacktrace
```
Upload path: `app/build/outputs/apk/dev/debug/*arm64-v8a*.apk`. Never build locally, never
all 8 ABIs. `kotlin.daemon.jvmargs=-Xmx4096m -XX:+UseParallelGC` in `gradle.properties`.
