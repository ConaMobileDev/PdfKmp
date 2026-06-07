package com.conamobile.pdfkmp.viewer

/**
 * Copies [text] to the host platform's system clipboard.
 *
 * The selectable text overlay ([PdfViewer]'s `textSelectable`) wraps each
 * page in a Compose `SelectionContainer`, whose built-in "Copy" menu item
 * already routes through Compose's own clipboard abstraction on every
 * platform — long-press selection + the system copy menu therefore work on
 * Android, iOS, and Desktop alike, since the overlay is pure common Compose.
 *
 * This function is the explicit, platform-native primitive underneath that —
 * Android [android.content.ClipboardManager], iOS
 * [platform.UIKit.UIPasteboard.generalPasteboard], Desktop AWT
 * [java.awt.Toolkit] clipboard — for hosts that surface their own "copy"
 * affordance (e.g. a "Copy all text" button) or that need a guaranteed
 * pasteboard write on iOS independent of Compose's selection menu.
 *
 * No-ops on a blank [text].
 *
 * NOTE: the iOS actual targets `UIPasteboard` and cannot be compiled on a
 * non-macOS host — it needs verification on macOS / a simulator.
 */
public expect fun pdfViewerCopyToClipboard(text: String)
