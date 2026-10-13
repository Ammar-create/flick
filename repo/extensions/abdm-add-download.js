// ==GestureScript==
// @name         AB DM: Add Download
// @id           abdm-add-download
// @version      1.0.0
// @api          1
// @author       Ammar
// @description  Opens AB Download Manager's Add Download sheet for a URL, then taps Download.
// @needs        shizuku, accessibility
// @trigger      right-edge swipe-up
// ==/GestureScript==
//
// Verified on-device 2026-10-06: AddDownloadActivity is the EXPORTED VIEW handler.
// The com.abdownloadmanager.android.pages.add.single.* one is NOT exported and dies
// with a SecurityException, so this uses the exported form.

const url = args.url || clipboard.text();

if (!url || !/^https?:\/\//i.test(url)) {
  return ui.toast("No http(s) URL in args or clipboard");
}

function guessMime(u) {
  const ext = (u.split("?")[0].split(".").pop() || "").toLowerCase();
  if (["jpg", "jpeg"].includes(ext)) return "image/jpeg";
  if (ext === "png") return "image/png";
  if (ext === "gif") return "image/gif";
  if (ext === "webp") return "image/webp";
  if (ext === "mp4") return "video/mp4";
  if (ext === "mp3") return "audio/mpeg";
  if (ext === "apk") return "application/vnd.android.package-archive";
  if (ext === "zip") return "application/zip";
  return "application/octet-stream";
}

const mime = guessMime(url);

shell(
  'am start -n com.abdownloadmanager/com.abdownloadmanager.android.pages.add.AddDownloadActivity' +
  ' -a android.intent.action.VIEW' +
  ' -d "' + url + '"' +
  ' -t "' + mime + '"'
);

// The sheet is a UI surface, so wait for it and hit Download.
sleep(2500);
const btn = ui.find({ text: "Download" });
if (btn) {
  ui.tap(btn);
} else {
  ui.toast("Sheet opened, Download button not found");
}
