package com.conamobile.pdfkmp.platform

import android.content.Context
import androidx.startup.Initializer

/**
 * AndroidX App Startup component that captures the application [Context] for
 * the library to use later.
 *
 * Registered automatically through `AndroidManifest.xml` — no consumer code
 * is required. The initializer runs before the application's first
 * [android.app.Activity] sees `onCreate`, so any subsequent call to
 * [androidApplicationContext] is guaranteed to find a non-null reference.
 */
public class AndroidContextInitializer : Initializer<Context> {

    override fun create(context: Context): Context {
        AndroidContextHolder.set(context)
        return context.applicationContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
