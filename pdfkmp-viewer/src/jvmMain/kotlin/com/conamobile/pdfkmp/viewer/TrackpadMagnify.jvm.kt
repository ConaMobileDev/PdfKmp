package com.conamobile.pdfkmp.viewer

import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.lang.reflect.Proxy
import javax.swing.JComponent
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/** Verbose logging for diagnosing the gesture channel. Off in production. */
private const val PINCH_DEBUG: Boolean = false

/**
 * macOS trackpad pinch (magnify) → zoom.
 *
 * A bare trackpad pinch is delivered by macOS as an `NSEventTypeMagnify`
 * gesture, NOT a scroll/wheel event, so it never reaches Compose's pointer
 * pipeline. The only hook in a plain JDK is Apple's
 * `com.apple.eawt.event.MagnificationListener`, reached here purely through
 * reflection so this file still compiles on non-mac JDKs. At runtime the host
 * must open the package:
 *
 * ```
 * --add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED
 * ```
 *
 * Without that flag (or on a non-mac OS / a JDK lacking the package) the whole
 * thing degrades to a no-op via the surrounding `runCatching`.
 *
 * Each callback delivers a small signed magnification delta (~±0.0x) which the
 * caller accumulates onto its existing zoom state.
 */
internal actual fun installTrackpadPinchZoom(onPinchDelta: (Float) -> Unit): () -> Unit {
    if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return {}

    val noop: () -> Unit = {}
    var disposer: () -> Unit = noop
    val install = Runnable {
        disposer = runCatching { attachMagnify(onPinchDelta) }.getOrElse { error ->
            if (PINCH_DEBUG) println("PdfKmp pinch: install failed — $error")
            noop
        }
    }
    // Gesture wiring touches Swing; keep it on the EDT (also Compose's UI thread on Desktop).
    if (SwingUtilities.isEventDispatchThread()) install.run() else SwingUtilities.invokeLater(install)
    return { runCatching { disposer() } }
}

private fun attachMagnify(onPinchDelta: (Float) -> Unit): () -> Unit {
    val window = activeComposeWindow() ?: run {
        if (PINCH_DEBUG) println("PdfKmp pinch: no active window to attach to")
        return {}
    }
    val cl = window.javaClass.classLoader
    val magIface = Class.forName("com.apple.eawt.event.MagnificationListener", true, cl)
    val gestIface = Class.forName("com.apple.eawt.event.GestureListener", true, cl)
    val getMagnification = Class.forName("com.apple.eawt.event.MagnificationEvent", true, cl)
        .getMethod("getMagnification").apply { isAccessible = true }
    val gestureUtilities = Class.forName("com.apple.eawt.event.GestureUtilities", true, cl)
    val addTo = gestureUtilities.getMethod("addGestureListenerTo", JComponent::class.java, gestIface)
        .apply { isAccessible = true }
    val removeFrom = gestureUtilities.getMethod("removeGestureListenerFrom", JComponent::class.java, gestIface)
        .apply { isAccessible = true }

    val proxy = Proxy.newProxyInstance(cl, arrayOf(magIface, gestIface)) { _, method, args ->
        if (method.name == "magnify" && !args.isNullOrEmpty()) {
            val delta = getMagnification.invoke(args[0]) as Double
            if (PINCH_DEBUG) println("PdfKmp MAGNIFY delta=$delta")
            onPinchDelta(delta.toFloat())
        }
        null
    }

    // Gestures bubble UP to the JRootPane from the Skiko layer child, so the
    // root pane is the single, stable attach point. Attaching to BOTH the
    // root pane and the Skiko layer would deliver every gesture twice and
    // double the zoom rate — confirmed during instrumentation — so attach to
    // exactly one, preferring the public root pane.
    val target: JComponent = (window as? RootPaneContainer)?.rootPane
        ?: findSkiaLayer(window)
        ?: run {
            if (PINCH_DEBUG) println("PdfKmp pinch: no attach target")
            return {}
        }
    if (PINCH_DEBUG) println("PdfKmp pinch: attaching to ${target.javaClass.name} on ${window.javaClass.name}")
    addTo.invoke(null, target, proxy)
    return { runCatching { removeFrom.invoke(null, target, proxy) } }
}

/** The currently active/focused window, falling back to any shown window. */
private fun activeComposeWindow(): Window? {
    val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    return kfm.activeWindow
        ?: kfm.focusedWindow
        ?: Window.getWindows().firstOrNull { it.isShowing }
}

/** Depth-first search for Skiko's render surface component (a [JComponent]). */
private fun findSkiaLayer(component: Component): JComponent? {
    if (component is JComponent && component.javaClass.name.contains("Skia", ignoreCase = true)) {
        return component
    }
    if (component is Container) {
        for (child in component.components) {
            findSkiaLayer(child)?.let { return it }
        }
    }
    return null
}
