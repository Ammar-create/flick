// ==GestureScript==
// @name         Ask LastChat
// @id           ask-lastchat
// @version      1.0.0
// @api          1
// @author       Ammar
// @description  Send selected text or clipboard to LastChat assistant.
// @needs        ui,intent,clipboard
// @trigger      swipe-hold
// ==/GestureScript==

const text = ui.selectedText() || clipboard.text();
if (!text || text.trim().length === 0) {
    ui.toast("Select text first or copy text to clipboard");
} else {
    ui.toast("Sending to LastChat...");
    intent.processText(text, "lastchat.rikkafork.cocolal", "me.rerere.rikkahub.ui.activity.TextSelectionActivity");
}
