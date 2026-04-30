package com.conamobile.romchi.platform.share

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.conamobile.romchi.platform.startup.applicationContext

actual fun shareImageUrl(url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    val chooser = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    applicationContext.startActivity(chooser)
}

actual fun downloadImageUrl(url: String, fileName: String) {
    val ctx: Context = applicationContext
    val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setMimeType("image/jpeg")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, fileName)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    manager.enqueue(request)
    Toast.makeText(ctx, "Yuklab olish boshlandi", Toast.LENGTH_SHORT).show()
}
