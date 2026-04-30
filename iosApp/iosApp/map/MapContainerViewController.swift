import SwiftUI
import UIKit
import GoogleMaps

class MapContainerViewController: UIHostingController<GoogleMapView> {
    override func viewDidLoad() {
        super.viewDidLoad()
        
        view.isUserInteractionEnabled = true
        
        for recognizer in view.gestureRecognizers ?? [] {
            if recognizer is UIPanGestureRecognizer ||
               recognizer is UIPinchGestureRecognizer ||
               recognizer is UIRotationGestureRecognizer {
                recognizer.cancelsTouchesInView = false
                recognizer.delaysTouchesBegan = false
                recognizer.delaysTouchesEnded = false
                recognizer.requiresExclusiveTouchType = false
            }
        }
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        // Find the actual Google Maps view inside our view hierarchy
        findAndConfigureMapView(in: self.view)
    }
    
    private func findAndConfigureMapView(in view: UIView) {
        // Look for GMSMapView in subviews
        for subview in view.subviews {
            if let mapView = subview as? GMSMapView {
                // Apply special handling to make sure map gestures work
                mapView.isUserInteractionEnabled = true
                mapView.gestureRecognizers?.forEach { recognizer in
                    recognizer.cancelsTouchesInView = false
                    recognizer.delaysTouchesBegan = false
                    recognizer.delaysTouchesEnded = false
                    recognizer.requiresExclusiveTouchType = false
                }
                return
            }
            
            // Recursively check this subview's subviews
            findAndConfigureMapView(in: subview)
        }
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        // Make sure touch events propagate to the map
        super.touchesBegan(touches, with: event)
    }
}
