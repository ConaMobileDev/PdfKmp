package com.conamobile.romchi2

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.conamobile.romchi.App
import com.conamobile.romchi.core.remote.DataStore
import com.conamobile.romchi.data.model.store.UserStore
import com.conamobile.romchi.ui.designsystem.locale.changeLang
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.mmk.kmpnotifier.permission.permissionUtil
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppActivity : ComponentActivity(), KoinComponent {
    private lateinit var referrerClient: InstallReferrerClient
    val permissionUtil by permissionUtil()
    val userStore by inject<UserStore>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppEventsLogger.newLogger(this).logEvent("App Launched")
        handleIncomingIntent(intent)
        setContent {
            App()
        }
        installReferrer()
        DataStore.startOtpAutoFill = {
            autoFillOtpCode()
        }

        permissionUtil.askNotificationPermission()
    }

    private fun installReferrer() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isReferrerProcessed = prefs.getBoolean("install_referrer_processed", false)

        if (!isReferrerProcessed) {
            referrerClient = InstallReferrerClient.newBuilder(this).build()
            referrerClient.startConnection(object : InstallReferrerStateListener {

                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerResponse.OK -> {
                            try {
                                val response: ReferrerDetails = referrerClient.installReferrer
                                val referrerUrl = response.installReferrer

                                Napier.d("InstallReferrer: Raw referrer: $referrerUrl")

                                if (!referrerUrl.isNullOrEmpty()) {
                                    val uri = Uri.parse("https://romchi.uz?$referrerUrl")

                                    val utmSource = uri.getQueryParameter("utm_source")
                                    val utmMedium = uri.getQueryParameter("utm_medium")
                                    val utmContent = uri.getQueryParameter("utm_content")

                                    Napier.d("InstallReferrer: utm_source=$utmSource, utm_medium=$utmMedium, utm_content=$utmContent")

                                    // Faqat birinchi marta ishlatish
                                    if (utmContent != null) {
                                        val usedReferrers =
                                            prefs.getStringSet("used_referrer_codes", emptySet())
                                                ?: emptySet()

                                        if (!usedReferrers.contains(utmContent)) {
                                            DataStore.pendingDeepLink = utmContent
                                            Napier.d { "@@@REFERRAL: (App.android) Processing new utmContent: $utmContent" }
                                            DataStore.navigateToReferral(utmContent, true)

                                            // Referrer ishlatilganini belgilash
                                            val updatedReferrers = usedReferrers.toMutableSet()
                                            updatedReferrers.add(utmContent)
                                            prefs.edit {
                                                putStringSet(
                                                    "used_referrer_codes",
                                                    updatedReferrers
                                                )
                                            }
                                        }
                                    }

                                    prefs.edit {
                                        putBoolean("install_referrer_processed", true)
                                    }
                                }

                            } catch (e: RemoteException) {
                                Napier.e("InstallReferrer: Error: ${e.message}")
                            } finally {
                                referrerClient.endConnection()
                            }
                        }

                        InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                            Napier.w("InstallReferrer: Feature not supported")
                            prefs.edit { putBoolean("install_referrer_processed", true) }
                            referrerClient.endConnection()
                        }

                        InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                            Napier.w("InstallReferrer: Service unavailable")
                            referrerClient.endConnection()
                        }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    Napier.d("InstallReferrer: Service disconnected")
                }
            })
        } else {
            Napier.d("InstallReferrer: Already processed, skipping")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }


    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            delay(2500)
            DataStore.pendingDeepLink?.let {
                Napier.d { "@@@REFERRAL: (App.android) onResume" }
                DataStore.navigateToReferral(it, false)
            }
        }

        userStore.getLanguage()?.let {
            changeLang(it)
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                var referralValue = extractReferralFromUri(uri)
                referralValue?.let {
                    referralValue = it
                    DataStore.pendingDeepLink = it
                    Napier.d { "@@@REFERRAL: (App.android) handleIncomingIntent" }
                    DataStore.navigateToReferral(it, false)
                }
            }
        }
    }

    private fun extractReferralFromUri(uri: Uri): String? {
        return when {
            uri.pathSegments?.size == 2 && uri.pathSegments[0] == "referral" -> {
                uri.pathSegments[1]
            }

            uri.getQueryParameter("code") != null -> {
                uri.getQueryParameter("code")
            }

            else -> null
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun autoFillOtpCode() {
        try {
            SmsRetriever.getClient(this).startSmsUserConsent(null)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                this.registerReceiver(
                    smsVerificationReceiver,
                    intentFilter,
                    SmsRetriever.SEND_PERMISSION,
                    null
                )
            } else {
                val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                this.registerReceiver(
                    smsVerificationReceiver,
                    intentFilter,
                    SmsRetriever.SEND_PERMISSION,
                    null,
                    RECEIVER_EXPORTED
                )
            }
        } catch (e: Exception) {
        }
    }

    private val smsVerificationReceiver = object : BroadcastReceiver() {
        @SuppressLint("UnsafeIntentLaunch")
        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                    val smsRetrieverStatus = intent.extras ?: return
                    when (smsRetrieverStatus.getInt(SmsRetriever.EXTRA_STATUS)) {
                        CommonStatusCodes.SUCCESS -> {
                            val extraIntent =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(
                                        SmsRetriever.EXTRA_CONSENT_INTENT,
                                        Intent::class.java
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(SmsRetriever.EXTRA_CONSENT_INTENT)
                                }

                            extraIntent?.let {
                                launcher.launch(it)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
                    message?.let {
                        val pattern = Regex("\\d{5}")
                        pattern.find(message)?.value?.let { code ->
                            DataStore.listenOtpReceiver(code)
                        }
                    }
                }
            }
        }

}