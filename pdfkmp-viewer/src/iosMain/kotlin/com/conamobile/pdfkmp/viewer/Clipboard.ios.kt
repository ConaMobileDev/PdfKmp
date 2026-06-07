package com.conamobile.pdfkmp.viewer

import platform.UIKit.UIPasteboard

/**
 * iOS clipboard write via the general [UIPasteboard]. Setting `string`
 * replaces the pasteboard contents with [text], which is what a "Copy"
 * affordance over selected PDF text should do.
 *
 * NOTE: targets the Apple toolchain — cannot be compiled on a non-macOS
 * host; needs verification on macOS / a simulator.
 */
public actual fun pdfViewerCopyToClipboard(text: String) {
    if (text.isBlank()) return
    UIPasteboard.generalPasteboard.string = text
}
