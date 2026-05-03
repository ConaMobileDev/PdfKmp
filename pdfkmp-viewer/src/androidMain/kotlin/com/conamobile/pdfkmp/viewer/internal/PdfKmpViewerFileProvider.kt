package com.conamobile.pdfkmp.viewer.internal

import androidx.core.content.FileProvider

/**
 * Dedicated [FileProvider] subclass declared in the viewer's manifest.
 *
 * The only reason this class exists is to give the provider a unique
 * `android:name` so the Android manifest merger does not collapse it
 * into the consumer app's own `androidx.core.content.FileProvider`
 * declaration. Manifest merger uses `android:name` as the merge key
 * for `<provider>` elements, so when both the library and the host
 * app declare a provider named `androidx.core.content.FileProvider`,
 * the higher-priority host wins and the library's authority + path
 * meta-data are dropped — which then makes
 * `FileProvider.getUriForFile(...)` throw
 * `IllegalArgumentException: Couldn't find meta-data for provider with
 * authority …` from the share sheet at runtime.
 *
 * Subclassing sidesteps the collision entirely: the merge key is now
 * this class's fully qualified name, which no consumer app will ever
 * declare.
 */
public class PdfKmpViewerFileProvider : FileProvider()
