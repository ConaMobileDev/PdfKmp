package com.conamobile.pdfkmp.viewer.internal

import android.content.Context

/**
 * Lazily-initialised reference to the application [Context], populated
 * by [ViewerContextInitializer] through AndroidX App Startup.
 *
 * Held independently from `:pdfkmp`'s internal context so a future
 * change to that module's API surface cannot break the viewer's
 * non-composable code paths.
 */
internal object ViewerContextHolder {
    @Volatile
    private var context: Context? = null

    fun set(context: Context) {
        this.context = context.applicationContext
    }

    fun get(): Context = context ?: error(
        "PdfKmp viewer Android context has not been initialised. " +
            "Either rely on AndroidX App Startup (the default) or call " +
            "ViewerContextHolder.set(context) from your Application#onCreate.",
    )
}
