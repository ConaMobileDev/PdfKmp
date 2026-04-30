package com.conamobile.romchi.platform.share

expect fun shareImageUrl(url: String)

expect fun downloadImageUrl(url: String, fileName: String = "romchi_ai_image.jpg")
