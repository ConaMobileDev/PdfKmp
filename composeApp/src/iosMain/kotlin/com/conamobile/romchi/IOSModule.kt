package com.conamobile.romchi

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import com.conamobile.romchi.di.initKoin
import org.koin.core.component.KoinComponent

class IOSModule : KoinComponent {

    fun initKoin() {
        Napier.base(DebugAntilog())
        initKoin { }
    }
}