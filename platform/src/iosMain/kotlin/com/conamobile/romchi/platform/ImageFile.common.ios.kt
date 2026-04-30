package com.conamobile.romchi.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation

@OptIn(ExperimentalForeignApi::class)
actual suspend fun readBytesFromUri(filePath: String): ByteArray? {
    return try {
        // 1) "file://" prefiksini olib tashlaymiz
        val path = filePath.removePrefix("file://")

        // 2) UIImage bilan yuklash - bu HEIC, JPEG, PNG va boshqa formatlarni qo'llab-quvvatlaydi
        val uiImage = UIImage.imageWithContentsOfFile(path)
        if (uiImage != null) {
            // 3) Orientation ni normalize qilish (EXIF rotation ni apply qilish)
            val normalizedImage = normalizeImageOrientation(uiImage)

            // 4) JPEG formatga convert qilish
            val jpegData = UIImageJPEGRepresentation(normalizedImage, 0.9) // 90% sifat
            jpegData?.let { data ->
                val length = data.length.toInt()
                val ptr = data.bytes?.reinterpret<ByteVar>()
                ptr?.readBytes(length)
            }
        } else {
            // Fallback: to'g'ridan-to'g'ri NSData bilan o'qish
            val data = NSData.dataWithContentsOfFile(path)
            data?.let {
                val length = data.length.toInt()
                val ptr = data.bytes?.reinterpret<ByteVar>()
                ptr?.readBytes(length)
            }
        }
    } catch (e: Exception) {
        println("@@@ readBytesFromUri error: $e")
        null
    }
}

/**
 * EXIF orientation metadata ni actual pixel rotation ga convert qiladi.
 * Bu HEIC va boshqa rasmlar to'g'ri orientation da bo'lishini ta'minlaydi.
 */
@OptIn(ExperimentalForeignApi::class)
private fun normalizeImageOrientation(image: UIImage): UIImage {
    // Agar orientation Up bo'lsa, hech narsa qilish shart emas
    if (image.imageOrientation == UIImageOrientation.UIImageOrientationUp) {
        return image
    }

    // Yangi context yaratib, rasmni to'g'ri orientation da chizish
    val (width, height) = image.size.useContents { width to height }

    UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
    image.drawInRect(CGRectMake(0.0, 0.0, width, height))
    val normalizedImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    return normalizedImage ?: image
}


