import UIKit
import AppsFlyerLib
import ComposeApp

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    
    var window: UIWindow?
    
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let _ = (scene as? UIWindowScene) else { return }
        
        // YETISHMAYOTGAN: App launch paytida deep link handle qilish
        // Universal Link orqali ochilgan bo'lsa
        if let userActivity = connectionOptions.userActivities.first {
            print("🚀 App launched via Universal Link")
            handleUserActivity(userActivity)
        }
        
        // URL Scheme orqali ochilgan bo'lsa
        if let urlContext = connectionOptions.urlContexts.first {
            print("🚀 App launched via URL Scheme")
            handleURLContext(urlContext)
        }
    }
    
    // Universal Links handling
    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        print("🔗 Universal Link received in active app")
        AppsFlyerLib.shared().continue(userActivity, restorationHandler: nil)
        handleUserActivity(userActivity)
    }
    
    // URL Scheme handling
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        print("📱 URL Scheme received in active app")
        
        if let urlContext = URLContexts.first {
            let url = urlContext.url
            // MUHIM: GitHub kodida options: nil deprecated, yangi usul:
            AppsFlyerLib.shared().handleOpen(url, options: [:])
            handleURLContext(urlContext)
        }
    }
    
    func sceneDidDisconnect(_ scene: UIScene) {
        // Called as the scene is being released by the system.
    }
    
    func sceneDidBecomeActive(_ scene: UIScene) {
        // MUHIM: Bu GitHub kodida yo'q edi!
        // AppsFlyer'ni har active bo'lganda start qilish kerak
        print("✅ Scene became active - starting AppsFlyer")
        AppsFlyerLib.shared().start()
    }
    
    func sceneWillResignActive(_ scene: UIScene) {
        // Called when the scene will move from an active state to an inactive state.
    }
    
    func sceneWillEnterForeground(_ scene: UIScene) {
        // Called as the scene transitions from the background to the foreground.
        print("🔄 Scene entering foreground - restarting AppsFlyer")
        AppsFlyerLib.shared().start()
    }
    
    func sceneDidEnterBackground(_ scene: UIScene) {
        // Called as the scene transitions from the foreground to the background.
    }
    
    // MARK: - Helper Methods
    
    private func handleUserActivity(_ userActivity: NSUserActivity) {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL else {
            print("⚠️ Invalid user activity or URL")
            return
        }
        
        print("🌐 Processing Universal Link: \(url)")
        
        // Sizning referral logic'ingiz
        let pathComponents = url.pathComponents
        if pathComponents.count >= 3 && pathComponents[1] == "referral" {
            let referralCode = pathComponents[2]
            print("👥 Referral code found: \(referralCode)")
            ReferralHandler_iosKt.handleReferralLink(url: referralCode)
        }
    }
    
    private func handleURLContext(_ urlContext: UIOpenURLContext) {
        let url = urlContext.url
        print("📲 Processing URL Scheme: \(url)")
        
        // Sizning custom URL scheme handling
        if let scheme = url.scheme, let host = url.host {
            print("📋 Scheme: \(scheme), Host: \(host)")
            
            // Misol: yourapp://referral/ABC123
            if host == "referral" {
                let pathComponents = url.pathComponents
                if pathComponents.count >= 2 {
                    let referralCode = pathComponents[1]
                    print("👥 Referral code from URL Scheme: \(referralCode)")
                    ReferralHandler_iosKt.handleReferralLink(url: referralCode)
                }
            }
        }
        
        // Query parameters ham tekshirish
        if let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
           let queryItems = components.queryItems {
            for item in queryItems {
                print("🔍 Query param: \(item.name) = \(item.value ?? "nil")")
                if item.name == "referral_code", let value = item.value {
                    ReferralHandler_iosKt.handleReferralLink(url: value)
                }
            }
        }
    }
}
