package com.conamobile.romchi2.referral

import android.content.Intent
import android.net.Uri
import com.conamobile.romchi.core.remote.DataStore
import io.github.aakira.napier.Napier
import java.net.URLDecoder

fun handleIntent(intent: Intent): String? {
    val data: Uri? = intent.data
    return data?.let {
        parseReferralLink(it.toString())?.let {
            DataStore.navigateToReferral(it, false)
        }
        parseReferralLink(it.toString())
    }
}

private fun parseReferralLink(url: String): String? {
    if (!url.contains("romchi.uz/referral/")) {
        return null
    }

    val referralValue = url.substringAfter("romchi.uz/referral/")
        .substringBefore("?")
        .substringBefore("#")

    if (referralValue.isEmpty()) {
        return null
    }

    // URL decode qilish
    val decodedValue = try {
        URLDecoder.decode(referralValue, "UTF-8")
    } catch (e: Exception) {
        referralValue
    }

    Napier.d { "@@@REFERRAL: (App.android) parseReferralLink" }
    DataStore.navigateToReferral(decodedValue, false)
    return decodedValue
}