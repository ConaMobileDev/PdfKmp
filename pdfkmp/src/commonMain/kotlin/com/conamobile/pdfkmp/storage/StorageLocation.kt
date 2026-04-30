package com.conamobile.pdfkmp.storage

/**
 * Cross-platform location to save a PDF document.
 *
 * Each variant maps to a sensible directory on every platform — see the
 * KDoc on each member for the exact path on Android / iOS. Use the variant
 * that matches the user's expectation rather than the platform's
 * filesystem layout: `Downloads` always lands in a place users can find
 * from a file manager, even when the platform's "Downloads" doesn't exist
 * in the traditional sense.
 *
 * On Android, writing to public locations (`Downloads`, `Documents`) uses
 * `MediaStore` on API 29+ (no permission required) and falls back to raw
 * filesystem on older releases (which requires the app to declare
 * `WRITE_EXTERNAL_STORAGE` and obtain runtime permission).
 *
 * On iOS, only the app's sandbox is reachable. `Downloads` therefore maps
 * to the Documents directory; if the app advertises `UIFileSharingEnabled`
 * + `LSSupportsOpeningDocumentsInPlace` in its `Info.plist` the user can
 * pick the file up from the Files app.
 */
public sealed interface StorageLocation {

    /**
     * App-private cache directory. Files here may be removed by the OS
     * when the device runs low on space. Best for previews, throwaway
     * temporary documents, or anything the user does not need to persist.
     *
     * - **Android:** `context.cacheDir` — `/data/data/<pkg>/cache/`.
     * - **iOS:** `NSCachesDirectory` — `<sandbox>/Library/Caches/`.
     */
    public data object Cache : StorageLocation

    /**
     * App-private persistent storage. Files persist for the lifetime of
     * the app installation but are not visible to the user. Best for
     * documents the app needs to keep but the user shouldn't manage.
     *
     * - **Android:** `context.filesDir` — `/data/data/<pkg>/files/`.
     * - **iOS:** `NSApplicationSupportDirectory` — `<sandbox>/Library/Application Support/`.
     */
    public data object AppFiles : StorageLocation

    /**
     * App-specific external storage. On Android this is visible from the
     * File Manager under `Android/data/<pkg>/files/` and survives app
     * uninstallation only on Android 11 and below. iOS does not have this
     * concept; files land in the standard Documents directory there.
     *
     * Useful when you want the user to be able to copy the file out of
     * the app via USB or a file manager but still have it disappear on
     * uninstall.
     */
    public data object AppExternalFiles : StorageLocation

    /**
     * Public Downloads folder.
     *
     * - **Android:** the device's Downloads collection. API 29+ uses
     *   `MediaStore.Downloads` (no permission). API 26-28 falls back to
     *   `Environment.DIRECTORY_DOWNLOADS` and requires
     *   `WRITE_EXTERNAL_STORAGE` runtime permission.
     * - **iOS:** the app's Documents directory. iOS has no public
     *   Downloads — surface the file via a share sheet or by enabling
     *   file sharing in `Info.plist`.
     */
    public data object Downloads : StorageLocation

    /**
     * Public Documents folder. Same Android / iOS mapping as
     * [Downloads], targeting `MediaStore.Files` with the
     * `Documents/` relative path on Android.
     */
    public data object Documents : StorageLocation

    /**
     * Temporary directory. On iOS this is `NSTemporaryDirectory()` and is
     * cleared by the system across launches; on Android it shares
     * [Cache] semantics.
     */
    public data object Temp : StorageLocation

    /**
     * Caller-supplied absolute path. Two shapes are accepted:
     *
     * - **Full file path** — pass an empty string for `filename` in
     *   [PdfDocument.save][com.conamobile.pdfkmp.storage.save]. The
     *   library writes the bytes to [path] verbatim.
     * - **Directory path** — pass a non-empty `filename`. The library
     *   joins `path` and `filename` with a `/` and writes there.
     *
     * Parent directories are created automatically if missing. Use this
     * when a path comes from a system picker (`ACTION_CREATE_DOCUMENT`
     * on Android, `UIDocumentPickerViewController` on iOS) or from
     * configuration.
     *
     * @property path absolute file or directory path.
     */
    public data class Custom(val path: String) : StorageLocation
}

/**
 * Result of saving a PDF through
 * [com.conamobile.pdfkmp.PdfDocument.save].
 *
 * @property path absolute filesystem path the bytes were written to.
 *   Always populated, even on Android Q+ MediaStore writes (in which case
 *   it is the resolved file path behind the content URI).
 * @property uri the content URI on Android Q+ MediaStore writes (`null`
 *   on iOS and on legacy Android writes). Pass this to
 *   `Intent.ACTION_VIEW` directly without going through `FileProvider`.
 */
public data class SavedPdf(
    val path: String,
    val uri: String? = null,
)
