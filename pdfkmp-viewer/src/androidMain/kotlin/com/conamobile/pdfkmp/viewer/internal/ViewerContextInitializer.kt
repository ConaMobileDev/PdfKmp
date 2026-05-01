package com.conamobile.pdfkmp.viewer.internal

import android.content.Context
import androidx.startup.Initializer

/**
 * AndroidX App Startup component that captures the application
 * [Context] for the viewer's renderer to use later.
 *
 * Registered automatically through `AndroidManifest.xml` — no consumer
 * code is required. Runs before the application's first
 * [android.app.Activity] sees `onCreate`, so any subsequent call to
 * [ViewerContextHolder.get] is guaranteed to find a non-null
 * reference.
 */
public class ViewerContextInitializer : Initializer<Context> {

    override fun create(context: Context): Context {
        ViewerContextHolder.set(context)
        return context.applicationContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
