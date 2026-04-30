package com.conamobile.romchi.referral

import com.conamobile.romchi.core.remote.DataStore
import io.github.aakira.napier.Napier

fun handleReferralLink(url: String): String? {
    return parseReferralLink(url)
}

private fun parseReferralLink(url: String): String? {
    if (!url.contains("romchi.uz/referral/")) {
        return null
    }

    val components = url.split("romchi.uz/referral/")
    if (components.size < 2) return null

    var referralValue = components[1]

    // Query va fragment ni olib tashlash
    val queryIndex = referralValue.indexOf("?")
    if (queryIndex != -1) {
        referralValue = referralValue.substring(0, queryIndex)
    }

    val fragmentIndex = referralValue.indexOf("#")
    if (fragmentIndex != -1) {
        referralValue = referralValue.substring(0, fragmentIndex)
    }

    if (referralValue.isEmpty()) return null

    // URL decode qilish
    val decodedValue = referralValue.decodeUrl() ?: referralValue

    Napier.d("@@@iosMain handleReferralLink: Referral value: $decodedValue")
    DataStore.navigateToReferral(decodedValue, false)
    return decodedValue
}

private fun String.decodeUrl(): String? {
    return this.replace("%40", "@")
        .replace("%2B", "+")
        .replace("%20", " ")
        .replace("%2F", "/")
}