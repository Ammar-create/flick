// ==GestureScript==
// @name         Floating Toolbox
// @id           floating-toolbox
// @version      1.0.0
// @api          1
// @author       Ammar
// @description  On-screen floating quick action panel for screenshots, Ask LastChat, and tools.
// @needs        ui
// @trigger      swipe-in
// ==/GestureScript==

ui.overlay({
    title: "Flick Toolbox",
    subtitle: "Select a tool to launch",
    items: [
        {
            id: "snip",
            title: "Snip Screen",
            subtitle: "In-memory crop & copy to clipboard",
            action: "snip-to-clipboard"
        },
        {
            id: "ask",
            title: "Ask LastChat",
            subtitle: "Send selected text or clipboard to AI",
            action: "ask-lastchat"
        },
        {
            id: "download",
            title: "Download Link",
            subtitle: "Send clipboard URL to AB Download Manager",
            action: "abdm-add-download"
        }
    ]
});
