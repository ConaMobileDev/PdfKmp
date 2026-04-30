package com.conamobile.romchi.platform.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.conamobile.romchi.platform.startup.applicationContext

@RequiresApi(Build.VERSION_CODES.DONUT)
actual fun openInMaps(latitude: Double, longitude: Double, label: String) {
    // geo: with query for label
    val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
    val intent = Intent(Intent.ACTION_VIEW, geoUri)

    // Build a chooser so user can pick any map/navigation app
    val chooser = Intent.createChooser(intent, "Open with")
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    // start it from your Application context
    val ctx: Context = applicationContext
    ctx.startActivity(chooser)
}