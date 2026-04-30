package com.conamobile.romchi

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.conamobile.romchi.connection.Connection
import com.conamobile.romchi.core.remote.DataStore
import com.conamobile.romchi.core.remote.ErrorDialogData
import com.conamobile.romchi.core.sync.SyncObserver
import com.conamobile.romchi.data.model.store.UserStore
import com.conamobile.romchi.data.model.type.ThemeMode
import com.conamobile.romchi.features.auth.di.authNavigationGraph
import com.conamobile.romchi.features.company.di.companyNavigation
import com.conamobile.romchi.features.di.employeesNavigationGraph
import com.conamobile.romchi.features.di.homeNavigationGraph
import com.conamobile.romchi.features.di.ordersNavigationGraph
import com.conamobile.romchi.features.di.paymentNavigationGraph
import com.conamobile.romchi.features.di.pricesNavigationGraph
import com.conamobile.romchi.features.di.profileNavigation
import com.conamobile.romchi.features.dillersLoc.di.dillersLocationNavigation
import com.conamobile.romchi.features.downloader.di.downloaderNavigation
import com.conamobile.romchi.features.downloader.services.PayloadDataManager
import com.conamobile.romchi.features.downloader.services.UploadDataManager
import com.conamobile.romchi.features.neworder.di.newOrderNavigationGraph
import com.conamobile.romchi.nav_anim.NavigationTransitions
import com.conamobile.romchi.navigation.AuthNavigationProvider
import com.conamobile.romchi.navigation.HomeNavigationProvider
import com.conamobile.romchi.navigation.replaceAll
import com.conamobile.romchi.ui.designsystem.component.InfoDialog
import com.conamobile.romchi.ui.designsystem.component.LoadingDialog
import com.conamobile.romchi.ui.designsystem.exit_listener.AppExitListener
import com.conamobile.romchi.ui.designsystem.extension.FixedFontScaleWrapper
import com.conamobile.romchi.ui.designsystem.locale.AppWords
import com.conamobile.romchi.ui.designsystem.locale.changeLang
import com.conamobile.romchi.ui.designsystem.theme.AppTheme
import com.conamobile.romchi.features.aiChat.di.aiChatNavigationGraph
import com.conamobile.romchi.warehouse.di.warehouseNavigationGraph
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun App() {
    val userStore = koinInject<UserStore>()
    val manualDarkMode = DataStore.changeDarkMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    var isDark by remember { mutableStateOf(false) }
    val connection = koinInject<Connection>()
    val networkAvailable by connection.observeHasConnection().collectAsState(true)
    val uploadDataManager = koinInject<UploadDataManager>()
    val payloadDataManager = koinInject<PayloadDataManager>()
    val syncObserver = koinInject<SyncObserver>()
    var navigateToDownloader by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(manualDarkMode.value) {
        manualDarkMode.value?.let {
            isDark = when (it) {
                ThemeMode.LIGHT -> false
                ThemeMode.NIGHT -> true
                ThemeMode.SYSTEM -> isSystemDark
            }
        } ?: run {
            isDark = when (userStore.getIsDarkTheme()) {
                ThemeMode.LIGHT -> false
                ThemeMode.NIGHT -> true
                ThemeMode.SYSTEM -> isSystemDark
            }
        }
    }

    LaunchedEffect(isSystemDark) {
        if (userStore.getIsDarkTheme() == ThemeMode.SYSTEM) {
            isDark = isSystemDark
        }
    }

    LaunchedEffect(Unit) {
        syncObserver.start()
        if (DataStore.checkingManually) DataStore.checkingManually = false
        userStore.getLanguage()?.let { changeLang(it) }
        uploadDataManager.addObservable()
        payloadDataManager.collectFirebaseEvents(
            navigateToDownloader = {
                if (userStore.loggedIn()) {
                    navigateToDownloader?.invoke()
                }
            }
        )
        uploadDataManager.deleteCacheProducts()
        AppWords.initialize()
        DataStore.isPremiumActive = userStore.isPremium()
        DataStore.isFreeMode = userStore.isFreeMode()
        DataStore.premiumEndDay = userStore.premiumEndDay()
        DataStore.enableDebugging = userStore.enableDebugging()
        DataStore.useNewSync = userStore.useNewSync()

        DataStore.allOrdersDownloaded = userStore.isSavedAllOrders()
    }

    LaunchedEffect(networkAvailable) {
        if (networkAvailable) {
            if (userStore.loggedIn() && !DataStore.isInDownloaderScreen) {
                Napier.d { "@@@App: checkingManually = true" }
                DataStore.checkingManually = true
                payloadDataManager.checkNonUploadDataManual(
                    navigateToDownloader = {
                        scope.launch(Dispatchers.Main) {
                            navigateToDownloader?.invoke()
                        }
                    }
                )
            }
        }
    }

    AppExitListener {
        DataStore.skipUpdateOneTime = false
    }

    Surface {
        AppTheme(
            isDark = isDark
        ) {
            FixedFontScaleWrapper {
                val navController = rememberNavController()
                LaunchedEffect(Unit) {
                    navController.currentBackStack.collect {
                        if (it.isEmpty()) {
                            navController.navigate(HomeNavigationProvider.Home)
                        }
                    }
                }
//                val isAndroid = (platform == Platform.Android)
                val deviceLimitPending = userStore.loggedIn() && userStore.isDeviceLimitPending()
                Column {
                    NavHost(
                        navController = navController,
                        startDestination = if (userStore.loggedIn() && !deviceLimitPending) HomeNavigationProvider.HomeRoute
                        else AuthNavigationProvider.AuthRoute,
                    enterTransition = NavigationTransitions.enterTransition,
                    exitTransition = NavigationTransitions.exitTransition,
                    popEnterTransition = NavigationTransitions.popEnterTransition,
                    popExitTransition = NavigationTransitions.popExitTransition
                    ) {
                        authNavigationGraph(navController)
                        homeNavigationGraph(navController)
                        newOrderNavigationGraph(navController)
                        companyNavigation(navController)
                        dillersLocationNavigation(navController)
                        downloaderNavigation(navController)
                        ordersNavigationGraph(navController)
                        employeesNavigationGraph(navController)
                        paymentNavigationGraph(navController)
                        profileNavigation(navController)
                        pricesNavigationGraph(navController)
                        warehouseNavigationGraph(navController)
                        aiChatNavigationGraph(navController)
                    }
                    LaunchedEffect(deviceLimitPending) {
                        if (deviceLimitPending) {
                            navController.navigate(AuthNavigationProvider.DeviceLimit)
                        }
                    }
                    LaunchedEffect(Unit) {
                        DataStore.navigateToReferral = { referral, fromStore ->
                            DataStore.pendingDeepLink = null
                            if (userStore.loggedIn()) {
                                navController.navigate(
                                    HomeNavigationProvider.Referral(
                                        referral,
                                        autoConnect = fromStore
                                    )
                                )
                            } else {
                                DataStore.pendingReferral = referral
                                userStore.referralFromStore(fromStore)
                            }
                        }
                        DataStore.navigateToLogin = {
                            if (userStore.loggedIn()) {
                                scope.launch {
                                    uploadDataManager.deleteAllDatabase()
                                    userStore.clearUserData()
                                    delay(200)
                                    navController.replaceAll(AuthNavigationProvider.Country)
                                }
                            }
                        }
                    }
                    navigateToDownloader =
                        remember { { scope.launch(Dispatchers.Main) { navController.navigate("downloader") } } }
                } // Column
            }
            val keyboard = LocalSoftwareKeyboardController.current
            var showErrorDialog by remember { mutableStateOf(false) }
            var showErrorDialogMessage by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                DataStore.showErrorDialog = {
                    showErrorDialogMessage = it.message ?: AppWords.unknownError()
                    showErrorDialog = it.show
                    if (it.show) {
                        keyboard?.hide()
                    }
                }
                DataStore.showLoading = {
                    isLoading = it
                }
            }

            InfoDialog(
                darkMode = isDark,
                message = showErrorDialogMessage,
                show = showErrorDialog,
                onClose = {
                    showErrorDialog = false
                    scope.launch(Dispatchers.Main.immediate) {
                        DataStore.showErrorDialog.invoke(ErrorDialogData())
                    }
                }
            )

            LoadingDialog(show = isLoading)
        }
    }
}
