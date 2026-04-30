package com.conamobile.pdfkmp.platform

import android.content.Context

/**
 * Lazily-initialised reference to the application [Context].
 *
 * The reference is set exactly once by [AndroidContextInitializer], which is
 * registered via the AndroidX `App Startup` library so the library users do
 * not need to call any explicit `init()` method. Accessing the context
 * before initialisation throws — practically this can only happen inside a
 * unit test that doesn't run through the AndroidX startup machinery.
 */
internal object AndroidContextHolder {
    @Volatile
    private var context: Context? = null

    fun set(context: Context) {
        this.context = context.applicationContext
    }

    fun get(): Context = context ?: error(
        "PdfKmp Android context has not been initialised. " +
            "Either rely on AndroidX App Startup (the default) or call " +
            "AndroidContextHolder.set(context) from your application's onCreate.",
    )
}

/** Convenience accessor used by the Android backend. */
internal fun androidApplicationContext(): Context = AndroidContextHolder.get()
