package com.conamobile.romchi.di

import com.conamobile.romchi.backend_kmp.di.backendModule
import com.conamobile.romchi.connection.di.connectionModule
import com.conamobile.romchi.core.database.di.databaseModule
import com.conamobile.romchi.core.database.di.romchiDaoModule
import com.conamobile.romchi.core.store.di.storeModule
import com.conamobile.romchi.core.cache.di.cacheModule
import com.conamobile.romchi.core.sync.di.syncModule
import com.conamobile.romchi.data.repository.di.repositoryModule
import com.conamobile.romchi.features.aiChat.di.aiChatModule
import com.conamobile.romchi.features.auth.di.authModule
import com.conamobile.romchi.features.company.di.companyModule
import com.conamobile.romchi.features.di.employeesModule
import com.conamobile.romchi.features.di.homeModule
import com.conamobile.romchi.features.di.ordersModule
import com.conamobile.romchi.features.di.paymentModule
import com.conamobile.romchi.features.di.pricesModule
import com.conamobile.romchi.features.di.profileModule
import com.conamobile.romchi.features.dillersLoc.di.dillersModule
import com.conamobile.romchi.features.downloader.di.downloaderModule
import com.conamobile.romchi.features.neworder.di.newOrderModule
import com.conamobile.romchi.core.sync.di.syncModule
import com.conamobile.romchi.warehouse.di.warehouseModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

val modules = listOf(
    commonModule(),
    storeModule(),
    databaseModule(),
    romchiDaoModule(),
    repositoryModule(),
    connectionModule(),
    syncModule(),
    cacheModule(),
    homeModule(),
    downloaderModule(),
    newOrderModule(),
    profileModule(),
    ordersModule(),
    pricesModule(),
    employeesModule(),
    authModule(),
    backendModule(),
    companyModule(),
    paymentModule(),
    dillersModule(),
    warehouseModule(),
    aiChatModule()
)

fun initKoin(appDeclaration: KoinAppDeclaration = { }) {
    startKoin {
        appDeclaration()
        modules(modules)

    }
}