import SwiftUI
import GoogleMaps
import CoreLocation

struct LocationData: Equatable {
    let name: String
    let latitude: Double
    let longitude: Double
    
    var coordinate: CLLocationCoordinate2D {
        return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
    
    static func == (lhs: LocationData, rhs: LocationData) -> Bool {
        return lhs.name == rhs.name &&
               lhs.latitude == rhs.latitude &&
               lhs.longitude == rhs.longitude
    }
}

// Custom InfoWindow View
class CustomInfoWindow: UIView {
    private let titleLabel = UILabel()
    private let snippetLabel = UILabel()
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }
    
    private func setupView() {
        backgroundColor = UIColor.white
        layer.cornerRadius = 8
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOffset = CGSize(width: 0, height: 2)
        layer.shadowOpacity = 0.3
        layer.shadowRadius = 4
        
        // Title label
        titleLabel.font = UIFont.boldSystemFont(ofSize: 14)
        titleLabel.textColor = UIColor.black
        titleLabel.numberOfLines = 0
        titleLabel.textAlignment = .center
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(titleLabel)
        
        snippetLabel.font = UIFont.systemFont(ofSize: 12)
        snippetLabel.textColor = UIColor.darkGray
        snippetLabel.numberOfLines = 0
        snippetLabel.translatesAutoresizingMaskIntoConstraints = false
        snippetLabel.isHidden = true
        addSubview(snippetLabel)
        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            titleLabel.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
            
            widthAnchor.constraint(lessThanOrEqualToConstant: 200),
            heightAnchor.constraint(greaterThanOrEqualToConstant: 32)
        ])
    }
    
    func configure(title: String, snippet: String) {
        titleLabel.text = title
        snippetLabel.text = snippet
    }
}

struct GoogleMapView: UIViewRepresentable {
    var onTap: (CLLocationCoordinate2D) -> Void
    @State private var locationData: [LocationData] = []
    @State private var isDillersMode = false
    @State private var isInitialized = false
    
    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }
    
    func makeUIView(context: Context) -> UIView {
        let containerView = UIView()

        let loadingIndicator = UIActivityIndicatorView(style: .large)
        loadingIndicator.translatesAutoresizingMaskIntoConstraints = false
        loadingIndicator.startAnimating()
        containerView.addSubview(loadingIndicator)

        NSLayoutConstraint.activate([
            loadingIndicator.centerXAnchor.constraint(equalTo: containerView.centerXAnchor),
            loadingIndicator.centerYAnchor.constraint(equalTo: containerView.centerYAnchor)
        ])

        let mapView = createMapView(context: context)
        mapView.isHidden = true
        containerView.addSubview(mapView)

        mapView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            mapView.topAnchor.constraint(equalTo: containerView.topAnchor),
            mapView.bottomAnchor.constraint(equalTo: containerView.bottomAnchor),
            mapView.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
            mapView.trailingAnchor.constraint(equalTo: containerView.trailingAnchor)
        ])
        
        context.coordinator.containerView = containerView
        context.coordinator.loadingIndicator = loadingIndicator
        context.coordinator.mapView = mapView
        
        return containerView
    }
    
    private func createMapView(context: Context) -> GMSMapView {
        let camera = GMSCameraPosition.camera(withLatitude: 41.3111, longitude: 69.2797, zoom: 12)
        let mapView = GMSMapView(frame: .zero, camera: camera)

        // Map settings
        mapView.settings.scrollGestures = true
        mapView.settings.zoomGestures = true
        mapView.settings.tiltGestures = true
        mapView.settings.rotateGestures = true
        mapView.settings.myLocationButton = false

        // Request location permission and enable my location on first load
        context.coordinator.requestLocationPermissions()
        mapView.isMyLocationEnabled = true

        // Gesture settings
        mapView.gestureRecognizers?.forEach { recognizer in
            recognizer.cancelsTouchesInView = false
        }
        mapView.isUserInteractionEnabled = true
        mapView.padding = UIEdgeInsets(top: 80, left: 0, bottom: 100, right: 10)

        mapView.delegate = context.coordinator
        context.coordinator.setupNotificationListener()

        return mapView
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {
        // Updates are handled through the coordinator
    }
    
    class Coordinator: NSObject, GMSMapViewDelegate, CLLocationManagerDelegate {
        let parent: GoogleMapView
        let locationManager = CLLocationManager()
        var containerView: UIView?
        var loadingIndicator: UIActivityIndicatorView?
        weak var mapView: GMSMapView?
        var hasCenteredOnce = false
        var userLocationMarker: GMSMarker?
        var dillersMarkers: [GMSMarker] = []
        var customInfoWindows: [GMSMarker: CustomInfoWindow] = [:]
        var notificationObserver: NSObjectProtocol?
        var isInitialLoad = true
        var shouldZoomToDillers = false
        var selectedDealerName: String?
        var lastZoomLevel: Float = 0

        init(parent: GoogleMapView) {
            self.parent = parent
            super.init()
            locationManager.delegate = self
        }
        
        func setupNotificationListener() {
            notificationObserver = NotificationCenter.default.addObserver(
                forName: NSNotification.Name("DillersLocationData"),
                object: nil,
                queue: .main
            ) { [weak self] notification in
                self?.handleDillersNotification(notification)
            }

            NotificationCenter.default.addObserver(
                forName: NSNotification.Name("ZoomToUserLocation"),
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.zoomToUserLocation()
            }

            NotificationCenter.default.addObserver(
                forName: NSNotification.Name("SelectedDealerChanged"),
                object: nil,
                queue: .main
            ) { [weak self] notification in
                self?.handleSelectedDealerChanged(notification)
            }

            // Monitor when app becomes active (user returns from Settings)
            NotificationCenter.default.addObserver(
                forName: UIApplication.didBecomeActiveNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.checkLocationServicesAndUpdate()
            }
        }

        private func checkLocationServicesAndUpdate() {
            guard CLLocationManager.locationServicesEnabled(),
                  locationManager.authorizationStatus == .authorizedWhenInUse ||
                  locationManager.authorizationStatus == .authorizedAlways else {
                return
            }

            // Location services are now enabled and we have permission
            // Reset zoom flag to allow zooming to user location
            hasCenteredOnce = false
            locationManager.startUpdatingLocation()
        }

        private func zoomToUserLocation() {
            guard let mapView = mapView else { return }

            if let location = mapView.myLocation {
                let camera = GMSCameraPosition.camera(
                    withTarget: location.coordinate,
                    zoom: 14
                )
                mapView.animate(to: camera)
            } else if let location = locationManager.location {
                let camera = GMSCameraPosition.camera(
                    withTarget: location.coordinate,
                    zoom: 14
                )
                mapView.animate(to: camera)
            }
        }

        private func handleSelectedDealerChanged(_ notification: Notification) {
            guard let userInfo = notification.userInfo,
                  let name = userInfo["name"] as? String else {
                selectedDealerName = nil
                updateInfoWindowsVisibility()
                return
            }

            selectedDealerName = name
            updateInfoWindowsVisibility()
        }

        private func updateInfoWindowsVisibility() {
            for (marker, infoWindow) in customInfoWindows {
                if let locationData = marker.userData as? LocationData {
                    infoWindow.isHidden = (selectedDealerName != locationData.name)
                }
            }
        }

        private func handleDillersNotification(_ notification: Notification) {
            guard let data = notification.userInfo?["locations"] as? [[String: Any]] else {
                showGoogleMapMode()
                return
            }

            let newLocationData = data.compactMap { location -> LocationData? in
                guard let name = location["name"] as? String,
                      let latitude = location["latitude"] as? Double,
                      let longitude = location["longitude"] as? Double else {
                    return nil
                }
                return LocationData(name: name, latitude: latitude, longitude: longitude)
            }

            DispatchQueue.main.async {
                self.parent.locationData = newLocationData
                self.parent.isDillersMode = !newLocationData.isEmpty
                self.parent.isInitialized = true

                self.hideLoadingAndShowMap()

                if let mapView = self.mapView {
                    self.updateMapMode(
                        mapView: mapView,
                        locationData: newLocationData,
                        isDillersMode: self.parent.isDillersMode
                    )
                }
            }
        }
        
        private func hideLoadingAndShowMap() {
            loadingIndicator?.stopAnimating()
            loadingIndicator?.isHidden = true
            mapView?.isHidden = false
        }
        
        func updateMapMode(mapView: GMSMapView, locationData: [LocationData], isDillersMode: Bool) {
            if isDillersMode && !locationData.isEmpty {
                showDillersMarkers(mapView: mapView, locationData: locationData)
            } else {
                showGoogleMapMode(mapView: mapView)
            }
        }
        
        private func createCustomInfoWindow(for marker: GMSMarker, title: String, snippet: String) -> CustomInfoWindow {
            let infoWindow = CustomInfoWindow()
            infoWindow.configure(title: title, snippet: snippet)
            
            // Text kengligini o'lchash
            let titleSize = (title as NSString).size(withAttributes: [
                .font: UIFont.boldSystemFont(ofSize: 14)
            ])
            
            // Padding va margins hisobga olgan holda optimal kengligi
            let optimalWidth = max(80, min(250, titleSize.width + 24)) // 24 = 12px * 2 (left + right padding)
            let optimalHeight = max(32, titleSize.height + 16) // 16 = 8px * 2 (top + bottom padding)
            
            // Frame ni optimal o'lchamga sozlash
            infoWindow.frame = CGRect(
                origin: .zero,
                size: CGSize(width: optimalWidth, height: optimalHeight)
            )
            
            return infoWindow
        }
        
        private func showDillersMarkers(mapView: GMSMapView, locationData: [LocationData]) {
            clearAllMarkers(mapView: mapView)

            requestLocationPermissions()
            mapView.isMyLocationEnabled = true
            mapView.settings.myLocationButton = false

            var bounds = GMSCoordinateBounds()
            dillersMarkers = []

            let currentZoom = mapView.camera.zoom
            lastZoomLevel = currentZoom
            let clusters = clusterLocations(locationData, zoom: currentZoom)

            for cluster in clusters {
                if cluster.locations.count > 1 {
                    let marker = GMSMarker(position: cluster.center)
                    marker.title = ""
                    marker.snippet = ""
                    marker.icon = createClusterIcon(count: cluster.locations.count)
                    marker.map = mapView
                    marker.userData = cluster.locations
                    dillersMarkers.append(marker)
                    bounds = bounds.includingCoordinate(cluster.center)
                } else if let location = cluster.locations.first {
                    // Create individual marker
                    let marker = GMSMarker(position: location.coordinate)
                    marker.title = ""
                    marker.snippet = ""
                    marker.icon = GMSMarker.markerImage(with: .systemRed)
                    marker.map = mapView
                    marker.userData = location

                    let infoWindow = createCustomInfoWindow(
                        for: marker,
                        title: location.name,
                        snippet: "Lat: \(String(format: "%.4f", location.latitude)), Lng: \(String(format: "%.4f", location.longitude))"
                    )
                    infoWindow.isHidden = true // Initially hidden
                    mapView.addSubview(infoWindow)
                    customInfoWindows[marker] = infoWindow

                    dillersMarkers.append(marker)
                    bounds = bounds.includingCoordinate(location.coordinate)
                }
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                self.updateInfoWindowPositions()
                if self.isInitialLoad {
                    self.isInitialLoad = false
                }
            }
        }

        // Simple clustering algorithm
        private func clusterLocations(_ locations: [LocationData], zoom: Float) -> [LocationGroup] {
            // Disable clustering at zoom >= 14 to show individual markers
            if zoom >= 14 {
                print("🗺️ Clustering disabled at zoom \(zoom) - showing all individual markers")
                return locations.map { location in
                    LocationGroup(locations: [location], center: location.coordinate)
                }
            }

            // Distance threshold based on zoom level (in degrees)
            // Larger thresholds at low zoom for better clustering at world/country/city views
            let threshold: Double
            switch zoom {
            case 12..<14:  // City detailed view
                threshold = 0.002      // ~222 meters
            case 10..<12:  // City view
                threshold = 0.01       // ~1.1 km
            case 8..<10:   // Metropolitan area
                threshold = 0.05       // ~5.5 km
            case 6..<8:    // Region/State
                threshold = 0.2        // ~22 km
            case 4..<6:    // Country
                threshold = 1.0        // ~111 km
            case 2..<4:    // Continent
                threshold = 5.0        // ~555 km
            default:       // World view (< 2)
                threshold = 20.0       // ~2220 km
            }

            print("🗺️ Clustering: zoom=\(zoom), threshold=\(threshold), locations=\(locations.count)")

            var clusters: [LocationGroup] = []
            var remaining = locations

            while !remaining.isEmpty {
                let current = remaining.removeFirst()
                var group = [current]

                remaining = remaining.filter { location in
                    let distance = sqrt(
                        pow(location.latitude - current.latitude, 2) +
                        pow(location.longitude - current.longitude, 2)
                    )

                    if distance < threshold {
                        print("🔗 Clustering: '\(location.name)' grouped with '\(current.name)' (distance: \(distance))")
                        group.append(location)
                        return false
                    }
                    return true
                }

                let centerLat = group.map { $0.latitude }.reduce(0, +) / Double(group.count)
                let centerLon = group.map { $0.longitude }.reduce(0, +) / Double(group.count)

                if group.count > 1 {
                    print("📦 Created cluster with \(group.count) locations at (\(centerLat), \(centerLon))")
                }

                clusters.append(LocationGroup(
                    locations: group,
                    center: CLLocationCoordinate2D(latitude: centerLat, longitude: centerLon)
                ))
            }

            print("✅ Clustering complete: \(clusters.count) clusters/markers")
            return clusters
        }

        // Create custom cluster icon with count
        private func createClusterIcon(count: Int) -> UIImage {
            let size = CGSize(width: 50, height: 50)
            let renderer = UIGraphicsImageRenderer(size: size)

            return renderer.image { context in
                // Draw circle
                let circlePath = UIBezierPath(ovalIn: CGRect(x: 5, y: 5, width: 40, height: 40))
                UIColor(red: 0.0, green: 0.47, blue: 1.0, alpha: 1.0).setFill()
                circlePath.fill()

                // Draw count text
                let text = "\(count)"
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.boldSystemFont(ofSize: 16),
                    .foregroundColor: UIColor.white
                ]
                let textSize = text.size(withAttributes: attrs)
                let textRect = CGRect(
                    x: (size.width - textSize.width) / 2,
                    y: (size.height - textSize.height) / 2,
                    width: textSize.width,
                    height: textSize.height
                )
                text.draw(in: textRect, withAttributes: attrs)
            }
        }

        // Helper struct for clustering
        struct LocationGroup {
            let locations: [LocationData]
            let center: CLLocationCoordinate2D
        }
        
        private func updateInfoWindowPositions() {
            guard let mapView = mapView else { return }

            for (marker, infoWindow) in customInfoWindows {
                let point = mapView.projection.point(for: marker.position)
                let adjustedPoint = CGPoint(x: point.x - infoWindow.bounds.width / 2, y: point.y - infoWindow.bounds.height - 40)
                infoWindow.frame.origin = adjustedPoint
                // Don't change visibility here - it's managed by updateInfoWindowsVisibility
            }
        }
        
        private func showGoogleMapMode(mapView: GMSMapView? = nil) {
            let targetMapView = mapView ?? self.mapView
            guard let targetMapView = targetMapView else { return }

            hideLoadingAndShowMap()
            clearDillersMarkers()

            targetMapView.isMyLocationEnabled = true
            targetMapView.settings.myLocationButton = true

            hasCenteredOnce = false
            requestLocationPermissions()

            if locationManager.authorizationStatus == .authorizedWhenInUse ||
               locationManager.authorizationStatus == .authorizedAlways {
                locationManager.startUpdatingLocation()
            }
        }
        
        private func clearAllMarkers(mapView: GMSMapView) {
            for (_, infoWindow) in customInfoWindows {
                infoWindow.removeFromSuperview()
            }
            customInfoWindows.removeAll()

            mapView.clear()
            dillersMarkers.removeAll()
            userLocationMarker = nil
        }

        private func clearDillersMarkers() {
            for (_, infoWindow) in customInfoWindows {
                infoWindow.removeFromSuperview()
            }
            customInfoWindows.removeAll()

            dillersMarkers.forEach { $0.map = nil }
            dillersMarkers.removeAll()
        }
        
        func requestLocationPermissions() {
            locationManager.requestWhenInUseAuthorization()
        }
        
        private func updateUserLocationMarker(at coordinate: CLLocationCoordinate2D) {
            guard let mapView = mapView, !parent.isDillersMode else { return }

            if let existing = userLocationMarker {
                existing.position = coordinate
            } else {
                let newMarker = GMSMarker(position: coordinate)
                newMarker.title = "Tanlangan joy"
                newMarker.snippet = "Lat: \(String(format: "%.4f", coordinate.latitude)), Lng: \(String(format: "%.4f", coordinate.longitude))"
                newMarker.icon = GMSMarker.markerImage(with: .systemBlue)
                newMarker.map = mapView
                userLocationMarker = newMarker
            }
        }
        
        // MARK: - CLLocationManagerDelegate
        func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
            guard status == .authorizedWhenInUse || status == .authorizedAlways else { return }

            // Check if location services are enabled
            guard CLLocationManager.locationServicesEnabled() else {
                // Location services are disabled, show alert
                showLocationServicesDisabledAlert()
                return
            }

            if !parent.isDillersMode {
                mapView?.isMyLocationEnabled = true
                mapView?.settings.myLocationButton = true
            } else if isInitialLoad {
                mapView?.isMyLocationEnabled = true
            }

            manager.startUpdatingLocation()
        }

        private func showLocationServicesDisabledAlert() {
            DispatchQueue.main.async {
                guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                      let rootViewController = windowScene.windows.first?.rootViewController else {
                    return
                }

                let alert = UIAlertController(
                    title: "Location Services Disabled",
                    message: "Please enable Location Services in Settings to use this feature.",
                    preferredStyle: .alert
                )

                alert.addAction(UIAlertAction(title: "Settings", style: .default) { _ in
                    if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(settingsUrl)
                    }
                })

                alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))

                rootViewController.present(alert, animated: true)
            }
        }
        
        func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
            guard let loc = locations.first, let map = mapView else { return }

            if !hasCenteredOnce {
                map.animate(to: GMSCameraPosition.camera(withTarget: loc.coordinate, zoom: 14))
                hasCenteredOnce = true
            }

            if !parent.isDillersMode {
                updateUserLocationMarker(at: loc.coordinate)
                NotificationCenter.default.post(
                    name: .locationDidPick,
                    object: nil,
                    userInfo: ["lat": loc.coordinate.latitude, "lon": loc.coordinate.longitude]
                )
                parent.onTap(loc.coordinate)
            }

            manager.stopUpdatingLocation()
        }
        
        // MARK: - GMSMapViewDelegate
        func didTapMyLocationButton(for mapView: GMSMapView) -> Bool {
            guard let coord = mapView.myLocation?.coordinate, !parent.isDillersMode else {
                return false
            }
            
            mapView.animate(toLocation: coord)
            updateUserLocationMarker(at: coord)
            
            NotificationCenter.default.post(
                name: .locationDidPick,
                object: nil,
                userInfo: ["lat": coord.latitude, "lon": coord.longitude]
            )
            parent.onTap(coord)
            return false
        }
        
        func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) {
            if parent.isDillersMode {
                NotificationCenter.default.post(
                    name: Notification.Name("DillersMarkerTapped"),
                    object: nil,
                    userInfo: [
                        "name": "",
                        "latitude": 0.0,
                        "longitude": 0.0
                    ]
                )
            } else {
                updateUserLocationMarker(at: coordinate)
                NotificationCenter.default.post(
                    name: .locationDidPick,
                    object: nil,
                    userInfo: ["lat": coordinate.latitude, "lon": coordinate.longitude]
                )
                parent.onTap(coordinate)
            }
        }
        
        func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
            if parent.isDillersMode {
                if let clusterLocations = marker.userData as? [LocationData] {
                    if clusterLocations.count > 1 {
                        let newZoom = min(mapView.camera.zoom + 2, 20)
                        let camera = GMSCameraPosition.camera(
                            withLatitude: marker.position.latitude,
                            longitude: marker.position.longitude,
                            zoom: newZoom
                        )
                        mapView.animate(to: camera)
                        return true
                    }
                } else if let tappedLocation = marker.userData as? LocationData {
                    print("📍 iOS: Marker tapped - \(tappedLocation.name)")
                    NotificationCenter.default.post(
                        name: Notification.Name("DillersMarkerTapped"),
                        object: nil,
                        userInfo: [
                            "name": tappedLocation.name,
                            "latitude": tappedLocation.latitude,
                            "longitude": tappedLocation.longitude
                        ]
                    )
                    return true
                }
            }
            return false
        }
        
        func mapView(_ mapView: GMSMapView, didChange position: GMSCameraPosition) {
            if parent.isDillersMode {
                if !customInfoWindows.isEmpty {
                    updateInfoWindowPositions()
                }

                let currentZoom = position.zoom
                if abs(currentZoom - lastZoomLevel) > 1.5 && !parent.locationData.isEmpty {
                    lastZoomLevel = currentZoom
                    showDillersMarkers(mapView: mapView, locationData: parent.locationData)
                }
            }

            NotificationCenter.default.post(
                name: Notification.Name("MapCameraPositionChanged"),
                object: nil,
                userInfo: [
                    "latitude": position.target.latitude,
                    "longitude": position.target.longitude,
                    "zoom": Double(position.zoom)
                ]
            )
        }
    }
}

// MARK: - Extensions
extension Notification.Name {
    static let locationDidPick = Notification.Name("LocationDidPick")
}

extension View {
    func mapGesturesPriority() -> some View {
        return self
            .contentShape(Rectangle())
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in }
                    .onEnded { _ in },
                including: .subviews
            )
    }
}

// MARK: - NotificationCenter Helper
extension GoogleMapView {
    
    static func sendLocationData(_ locations: [(String, Double, Double)]) {
        let locationData = locations.map { (name, lat, lng) in
            return [
                "name": name,
                "latitude": lat,
                "longitude": lng
            ]
        }

        NotificationCenter.default.post(
            name: NSNotification.Name("DillersLocationData"),
            object: nil,
            userInfo: ["locations": locationData]
        )
    }

    static func clearLocationData() {
        NotificationCenter.default.post(
            name: NSNotification.Name("DillersLocationData"),
            object: nil,
            userInfo: ["locations": []]
        )
    }
    
    static func testWithSampleData() {
        sendLocationData([
            ("Toshkent Markazi", 41.2995, 69.2401),
            ("Chilonzor filiali", 41.2844, 69.2034),
            ("Yunusobod filiali", 41.3775, 69.2904)
        ])
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
            clearLocationData()
        }
    }
}
