package com.conamobile.pdfkmp.viewer

import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
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
 * reflection so this file still compiles on non-mac JDKs. The internal
 * package is normally closed, but [openEawtEventPackage] opens it at runtime
 * (no `--add-opens` launch flag required), so pinch works however the app is
 * started — IDE "run", Gradle, or a packaged distributable. Passing
 * `--add-opens java.desktop/com.apple.eawt.event=ALL-UNNAMED` is only an
 * optional fallback for a future JDK that blocks the runtime open.
 *
 * On a non-mac OS, a JDK lacking the package, or if both the runtime open and
 * the flag fail, the whole thing degrades to a no-op via the surrounding
 * `runCatching` (and the on-screen ＋/− zoom controls remain available).
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
    // `com.apple.eawt.event` is an internal, non-open package of java.desktop.
    // Reflective access normally needs `--add-opens` on the command line — but
    // that breaks any launch that doesn't set it (IDE "run main()" gutter,
    // a consumer app that forgot the flag, …). Open it at runtime instead so
    // pinch "just works" regardless of how the app was started. Falls back to
    // requiring the flag if this bootstrap is ever blocked (future JDK).
    openEawtEventPackage()
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

/**
 * Opens `java.desktop/com.apple.eawt.event` to all unnamed modules at runtime
 * so the gesture reflection works WITHOUT a `--add-opens` launch flag.
 *
 * Uses the trusted `MethodHandles.Lookup` (the same bootstrap ByteBuddy /
 * Lombok use) to invoke the package-private
 * `Module.implAddOpensToAllUnnamed`. Best-effort: if a future JDK blocks the
 * `sun.misc.Unsafe` field grab the whole thing is swallowed and pinch simply
 * falls back to needing the command-line flag (the ＋/− buttons still work).
 */
private fun openEawtEventPackage() {
    runCatching {
        val javaDesktop = java.awt.Window::class.java.module
        if (javaDesktop.isOpen("com.apple.eawt.event", TrackpadMagnifyMarker::class.java.module)) {
            return // already open (flag was passed, or a prior call opened it)
        }
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val implLookupField = MethodHandles.Lookup::class.java.getDeclaredField("IMPL_LOOKUP")
        val base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            .invoke(unsafe, implLookupField)
        val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            .invoke(unsafe, implLookupField) as Long
        val trusted = unsafeClass.getMethod("getObject", Any::class.java, java.lang.Long.TYPE)
            .invoke(unsafe, base, offset) as MethodHandles.Lookup
        val moduleClass = Class.forName("java.lang.Module")
        val implAddOpensToAllUnnamed = trusted.findVirtual(
            moduleClass,
            "implAddOpensToAllUnnamed",
            MethodType.methodType(Void.TYPE, String::class.java),
        )
        implAddOpensToAllUnnamed.invoke(javaDesktop, "com.apple.eawt.event")
        if (PINCH_DEBUG) println("PdfKmp pinch: opened com.apple.eawt.event at runtime")
    }.onFailure {
        if (PINCH_DEBUG) println("PdfKmp pinch: runtime open failed ($it) — needs --add-opens")
    }
}

/** Marker type used only to obtain this code's (unnamed) module. */
private class TrackpadMagnifyMarker

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
