// ==GestureScript==
// @name         Snip to Clipboard
// @id           snip-to-clipboard
// @version      0.0.1
// @api          1
// @author       Ammar
// @description  STUB. Region capture straight to the clipboard, no file in the gallery.
// @needs        capture
// @trigger      left-edge double-tap
// ==/GestureScript==
//
// Blocked on a host bridge call that does not exist yet: `capture.region()`.
//
// Why it cannot be faked with what we have: `screencap` writes a PNG to disk, and any path
// under /sdcard/Pictures gets indexed by MediaStore, which is exactly the gallery entry we
// are trying to avoid. The host has to grab the frame with MediaProjection, keep it in
// app-private storage, and hand the script a URI that never touches a scanned folder.
//
// Sketch of the finished plugin, once capture.region() lands:
//
//   const shot = capture.region();          // drag a rectangle, returns { uri, width, height }
//   if (!shot) return ui.toast("Cancelled");
//   clipboard.setImage(shot.uri);           // paste straight into WhatsApp / Telegram / Discord
//   ui.toast("Snip on the clipboard");
//
// The temporarily-copied file lives in app-private storage and gets purged when the clip
// changes, so nothing ever shows up in the gallery.

ui.toast("Snip to Clipboard needs the capture.region() bridge call (V4)");
