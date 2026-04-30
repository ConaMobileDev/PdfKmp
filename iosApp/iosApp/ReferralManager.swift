import Foundation
import ComposeApp

class ReferralManager: ObservableObject {
    @Published var lastReferralValue: String?
    
    func handleURL(_ url: URL) {
        let urlString = url.absoluteString
        NSLog("iOS: Kelgan URL: \(urlString)")
        
        // iosMain dagi handleReferralLink funksiyasini chaqirish
        let referralValue = ReferralHandler_iosKt.handleReferralLink(url: urlString)
        
        if let value = referralValue {
            NSLog("iOS: Referral topildi: \(value)")
            DispatchQueue.main.async {
                self.lastReferralValue = value
                self.processReferral(value)
            }
        } else {
            NSLog("iOS: Referral topilmadi")
        }
    }
    
    private func processReferral(_ referralValue: String) {
        NSLog("iOS: Referral ishlov berilmoqda: \(referralValue)")
    }
}
