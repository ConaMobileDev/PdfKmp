import ComposeApp
import FirebaseCore
import FirebaseMessaging
import GoogleSignIn
import SwiftUI
import UIKit
import GoogleMaps
import FBSDKCoreKit
import AppTrackingTransparency
import AdSupport
import AppsFlyerLib
import OneSignalFramework

class AppDelegate: NSObject, UIApplicationDelegate {
    
    private let shared = IOSModule()
    
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        
        // ⭐️ 1. BIRINCHI - Firebase configure (eng birinchi bo'lishi kerak!)
        FirebaseApp.configure()
        
        // ⭐️ 2. Messaging delegate'ni DARHOL set qilish (critical!)
        Messaging.messaging().delegate = self
        
        OneSignal.Debug.setLogLevel(.LL_VERBOSE)
        
        OneSignal.initialize("1aa5b45f-515f-470b-a2dc-b1efefe97045", withLaunchOptions: launchOptions)

        OneSignal.Notifications.requestPermission({ accepted in
                print("User accepted notifications: \(accepted)")
              }, fallbackToSettings: false)
        
        // 3. Koin initialize
        shared.doInitKoin()
        
        // 4. Notification setup
        setupNotifications()
        
        // 5. Facebook SDK
        ApplicationDelegate.shared.application(
            application,
            didFinishLaunchingWithOptions: launchOptions
        )
        
        // 6. Google Maps
        GMSServices.provideAPIKey("AIzaSyDh70hX3i1NyVqhm2FYWvFSSjg9cT1FvBs")
        
        return true
    }
    
    private func setupNotifications() {
        autoreleasepool {
            NotifierManager.shared.initialize(
                configuration: NotificationPlatformConfigurationIos(
                    showPushNotification: true,
                    askNotificationPermissionOnStart: true,
                    notificationSoundName: nil
                )
            )
        }
        
        // UNUserNotificationCenter delegate
        if #available(iOS 10.0, *) {
            let center = UNUserNotificationCenter.current()
            center.delegate = self
            
            center.requestAuthorization(options: [.badge, .alert, .sound]) { granted, error in
                if let error = error {
                    print("❌ Push notification authorization error: \(error)")
                    return
                }
                
                print("✅ Push notification permission: \(granted)")
                
                if granted {
                    DispatchQueue.main.async {
                        UIApplication.shared.registerForRemoteNotifications()
                    }
                }
            }
        }
    }
    
    func applicationDidBecomeActive(_ application: UIApplication) {
        // Facebook events
        AppEvents.shared.activateApp()
        
        // ATT permission so'rash (iOS 14+)
        if #available(iOS 14, *) {
            ATTrackingManager.requestTrackingAuthorization { status in
                switch status {
                case .authorized:
                    print("✅ Tracking authorized")
                case .denied:
                    print("❌ Tracking denied")
                case .notDetermined:
                    print("⚠️ Tracking not determined")
                case .restricted:
                    print("⚠️ Tracking restricted")
                @unknown default:
                    break
                }
            }
        }
    }
    
    // Universal Link handling
    func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {
        
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL else {
            return false
        }
        
        let pathComponents = url.pathComponents
        if pathComponents.count >= 3 && pathComponents[1] == "referral" {
            let referralCode = pathComponents[2]
            ReferralHandler_iosKt.handleReferralLink(url: referralCode)
            return true
        }
        
        return false
    }
    
    // URL Scheme handling
    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey : Any] = [:]
    ) -> Bool {
        
        // Facebook URL handling
        let facebookHandled = ApplicationDelegate.shared.application(
            app,
            open: url,
            sourceApplication: options[UIApplication.OpenURLOptionsKey.sourceApplication] as? String,
            annotation: options[UIApplication.OpenURLOptionsKey.annotation]
        )
        
        return facebookHandled
    }
    
    // ⭐️ APNS token olish (CRITICAL!)
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        print("📱 APNS Token received")
        
        // ⭐️ 1. BIRINCHI - Firebase'ga APNS token berish
        Messaging.messaging().apnsToken = deviceToken
        
        // 2. Token'ni string formatda ko'rish (debug uchun)
        let tokenParts = deviceToken.map { data in String(format: "%02.2hhx", data) }
        let token = tokenParts.joined()
        print("📱 APNS Device Token: \(token)")
        
        // ⭐️ 3. FCM token avtomatik keladi messaging(_:didReceiveRegistrationToken:) orqali
    }
    
    // ⭐️ Push notification token olishda xatolik
    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("❌ Failed to register for remote notifications: \(error)")
    }
    
    // ⭐️ Push notification received
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        
        // Firebase Analytics'ga event yuborish
        if let messageID = userInfo["gcm.message_id"] {
            print("📬 Message ID: \(messageID)")
        }
        
        print("📬 Notification received: \(userInfo)")
        
        // Notification manager
        NotifierManager.shared.onApplicationDidReceiveRemoteNotification(userInfo: userInfo)
        
        completionHandler(.newData)
    }
}

// ⭐️ MARK: - UNUserNotificationCenterDelegate
extension AppDelegate: UNUserNotificationCenterDelegate {
    
    // Foreground'da notification kelganda
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo

        print("📬 Foreground notification received: \(userInfo)")

        // kmpnotifier'ga payload yuborish (LOGOUT va boshqa eventlar uchun)
        NotifierManager.shared.onApplicationDidReceiveRemoteNotification(userInfo: userInfo)

        // Notification'ni ko'rsatish
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound, .badge])
        }
    }
    
    // Notification'ga bosilganda
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo

        print("👆 Notification tapped: \(userInfo)")

        // kmpnotifier'ga payload yuborish (LOGOUT va boshqa eventlar uchun)
        NotifierManager.shared.onApplicationDidReceiveRemoteNotification(userInfo: userInfo)

        completionHandler()
    }
}

// ⭐️ MARK: - MessagingDelegate (CRITICAL!)
extension AppDelegate: MessagingDelegate {
    
    // FCM token yangilanganda - APNS token berilgandan keyin avtomatik chaqiriladi
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else {
            print("❌ FCM token is nil")
            return
        }
        
        print("✅ ========================================")
        print("✅ FCM Token:: \(token)")
        print("✅ ========================================")
        
        // ⭐️ Bu token'ni backend'ga yuboring!
        // sendFCMTokenToBackend(token)
        
        // UserDefaults'ga saqlash (optional)
        UserDefaults.standard.set(token, forKey: "fcmToken")
        
        // Token data dictionary
        let dataDict: [String: String] = ["token": token]
        NotificationCenter.default.post(
            name: Notification.Name("FCMToken"),
            object: nil,
            userInfo: dataDict
        )
    }
}

// MARK: - SwiftUI App Structure
@main
struct iOSApp: App {
    init() {
        FacebookLoginEvent_iosKt.trackFacebookEvent = FacebookEventProvider()
    }
    
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @StateObject private var referralManager = ReferralManager()
    
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .onOpenURL { url in
                referralManager.handleURL(url)
            }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainKt.MainViewController(
            mapUIViewController: { () -> UIViewController in
                return UIHostingController(
                    rootView: GoogleMapView(
                        onTap: { coord in
                            print("Tapped: \(coord.latitude), \(coord.longitude)")
                        }
                    )
                    .mapGesturesPriority()
                    .ignoresSafeArea()
                )
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
