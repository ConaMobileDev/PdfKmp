package com.conamobile.romchi.di

import com.conamobile.romchi.core.analytics.AnalyticsHelper
import com.conamobile.romchi.data.repository.error_handler.ErrorHandler
import com.conamobile.romchi.features.downloader.services.PayloadDataManager
import com.conamobile.romchi.features.downloader.services.UploadDataManager
import com.conamobile.romchi.platform.ProviderFactory
import org.koin.dsl.module

fun commonModule() = module {
    single { AnalyticsHelper() }
    single { ErrorHandler() }
    single { ProviderFactory() }
    single { UploadDataManager() }
    single { PayloadDataManager() }
}