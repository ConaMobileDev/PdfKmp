import ComposeApp
import FBSDKCoreKit
import Foundation

class FacebookEventProvider: TrackFacebookEvent {
    func trackLoginEvent(username: String, isNewUser: String){
        print("tracking login event")
        let parameters: [AppEvents.ParameterName: String] = [
            AppEvents.ParameterName("username"): username,
            AppEvents.ParameterName("isNewUser"): isNewUser
        ]
        AppEvents.shared.logEvent(.init("Logged in"), parameters: parameters)
        AppEvents.shared.flush()
    }
}
