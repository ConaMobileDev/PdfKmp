import UIKit
import RealityKit
import ARKit
import Combine
import Metal

/// RealityKit asosidagi AR oyna ko'rinishi
/// Kotlin dan Factory orqali chaqiriladi
@objc(RealityKitWindowARView)
public class RealityKitWindowARView: UIView, ARSessionDelegate {

    private var arView: ARView!
    private var modelAnchor: AnchorEntity?
    private var modelEntity: Entity?

    private var modelWidth: Float = 1000
    private var modelHeight: Float = 1000
    private var modelDepth: Float = 100

    private var openPartEntities: [Int: Entity] = [:]
    private var openPartData: [Int: (axis: SIMD3<Float>, maxAngle: Float, tiltPivot: SIMD3<Float>, tiltAxis: SIMD3<Float>, tiltMaxAngle: Float, tiltPhaseStart: Float, tiltPhaseEnd: Float)] = [:]
    private var isOpen: Bool = false
    private var isTilted: Bool = false
    private var isAnimating: Bool = false

    private var pendingMeshes: [PendingMeshAR] = []

    // Textures
    private var profileTexture: TextureResource?
    private var shotlankaTexture: TextureResource?

    // Profil rangi (handle va petlya uchun)
    private var profileColor: (r: Float, g: Float, b: Float) = (0.96, 0.96, 0.96)

    // AR state
    private var isPlaced: Bool = false
    private var planeDetected: Bool = false
    private var modelRotationAngle: Float = 0
    private var sessionStarted: Bool = false
    private var placedOnVerticalPlane: Bool = true  // Qaysi yuzaga joylashtirilgan
    private var planeRightVector: SIMD3<Float> = [1, 0, 0]  // Yuza bo'ylab o'ngga
    private var planeUpVector: SIMD3<Float> = [0, 1, 0]     // Yuza bo'ylab yuqoriga

    // Plane detection - yaxshilangan
    private var detectedPlanes: [UUID: ARPlaneAnchor] = [:]
    private var bestPlaneAnchor: ARPlaneAnchor?
    private var autoPlaceEnabled: Bool = true  // Avtomatik joylashtirish
    private var minPlaneSize: Float = 0.3  // Minimum 30cm x 30cm

    // Callbacks
    private var onStatusChange: ((String) -> Void)?
    private var onPlaneDetected: (() -> Void)?
    private var onModelPlaced: (() -> Void)?
    private var onPlaneNotDetected: (() -> Void)?  // 30 sek ichida yuza topilmasa

    // Plane not detected state (Kotlin polling uchun)
    private var planeNotDetectedTriggered: Bool = false

    // Timer
    private var planeDetectionTimer: Timer?
    private let planeDetectionTimeout: TimeInterval = 30.0

    @objc public override init(frame: CGRect) {
        super.init(frame: frame)
        setupARView()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupARView()
    }

    deinit {
        print("🗑️ RealityKitWindowARView deinit")
        cleanup()
    }

    /// AR view ni tozalash
    @objc public func cleanup() {
        print("🧹 RealityKitWindowARView cleanup")

        // Timer ni to'xtatish
        stopPlaneDetectionTimer()

        // Session to'xtatish
        arView?.session.pause()

        // Anchors ni o'chirish
        arView?.scene.anchors.forEach { anchor in
            arView?.scene.removeAnchor(anchor)
        }

        // ARView ni superview dan o'chirish
        arView?.removeFromSuperview()
        arView = nil

        // Data tozalash
        modelAnchor = nil
        modelEntity = nil
        openPartEntities.removeAll()
        openPartData.removeAll()
        pendingMeshes.removeAll()

        // Plane detection state
        detectedPlanes.removeAll()
        bestPlaneAnchor = nil
    }

    private func setupARView() {
        // ARView - .ar mode, manual session configuration (black screen fix)
        arView = ARView(frame: bounds, cameraMode: .ar, automaticallyConfigureSession: false)
        arView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        arView.session.delegate = self
        self.backgroundColor = .black

        // === PERFORMANCE OPTIMIZATIONS ===

        // 1. Rendering options - barcha qo'shimcha effektlarni o'chirish
        arView.renderOptions = [
            .disableMotionBlur,
            .disableDepthOfField,
            .disableCameraGrain,        // Kamera shovqini
            .disableHDR,                // HDR
            .disableGroundingShadows,   // Yer soyasi
            .disableFaceMesh,           // Face mesh
            .disablePersonOcclusion,    // Odam okkluziyasi
            .disableAREnvironmentLighting  // AR environment lighting
        ]

        // 2. Content scale - past resolution = yaxshi performance
        // 1.0 = native, 0.75 = 75% resolution
        arView.contentScaleFactor = UIScreen.main.scale * 0.85

        // 3. Environment texturing o'chirish
        arView.environment.background = .cameraFeed()

        addSubview(arView)

        // Pinch gesture for scaling
        let pinchGesture = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
        arView.addGestureRecognizer(pinchGesture)

        // 1 barmoq pan gesture - modelni ko'chirish
        let singlePanGesture = UIPanGestureRecognizer(target: self, action: #selector(handleSinglePan(_:)))
        singlePanGesture.minimumNumberOfTouches = 1
        singlePanGesture.maximumNumberOfTouches = 1
        arView.addGestureRecognizer(singlePanGesture)

        // 2 barmoq pan gesture - aylantirish
        let panGesture = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        panGesture.minimumNumberOfTouches = 2
        arView.addGestureRecognizer(panGesture)
    }

    public override func layoutSubviews() {
        super.layoutSubviews()
        arView.frame = bounds
    }

    public override func didMoveToWindow() {
        super.didMoveToWindow()
        // ViewController boshqaradi - bu yerda hech narsa qilmaymiz
    }

    // MARK: - AR Session

    @objc public func startARSession() {
        guard !sessionStarted else { return }
        guard ARWorldTrackingConfiguration.isSupported else { return }

        let configuration = ARWorldTrackingConfiguration()
        configuration.planeDetection = [.vertical, .horizontal]

        // Tezkor boshlash uchun minimal konfiguratsiya
        configuration.frameSemantics = []
        configuration.environmentTexturing = .none
        configuration.worldAlignment = .gravity

        // Session boshlash - resetTracking black screen muammosini hal qiladi
        arView.session.run(configuration, options: [.resetTracking, .removeExistingAnchors])
        sessionStarted = true
        onStatusChange?("Devorni qidiring...")

        // 30 sekund timer boshlash
        startPlaneDetectionTimer()
    }

    private func startPlaneDetectionTimer() {
        // Main thread da ishlashi kerak
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            // Avvalgi timer ni bekor qilish
            self.planeDetectionTimer?.invalidate()

            print("⏱️ Starting 30 second plane detection timer...")

            // Yangi timer boshlash
            self.planeDetectionTimer = Timer.scheduledTimer(withTimeInterval: self.planeDetectionTimeout, repeats: false) { [weak self] _ in
                guard let self = self else {
                    print("⏱️ Timer fired but self is nil")
                    return
                }
                print("⏱️ Timer fired! planeDetected=\(self.planeDetected), isPlaced=\(self.isPlaced)")
                // Agar hali yuza topilmagan bo'lsa
                if !self.planeDetected && !self.isPlaced {
                    print("⏱️ Showing plane not detected alert...")
                    // Kotlin polling uchun flag o'rnatish
                    self.planeNotDetectedTriggered = true
                    // Callback mavjud bo'lsa ham chaqirish
                    self.onPlaneNotDetected?()
                }
            }

            // Timer ni common run loop ga qo'shish (scroll paytida ham ishlashi uchun)
            RunLoop.current.add(self.planeDetectionTimer!, forMode: .common)
        }
    }

    private func stopPlaneDetectionTimer() {
        planeDetectionTimer?.invalidate()
        planeDetectionTimer = nil
    }

    @objc public func pauseARSession() {
        print("⏸️ AR Session pausing...")
        arView.session.pause()
        sessionStarted = false
    }

    @objc public func resumeARSession() {
        guard !sessionStarted else { return }
        print("▶️ AR Session resuming...")

        if isPlaced {
            // Model mavjud - tracking davom etsin
            let configuration = ARWorldTrackingConfiguration()
            configuration.planeDetection = []
            configuration.frameSemantics = []
            configuration.environmentTexturing = .none
            arView.session.run(configuration)
            sessionStarted = true
        } else {
            startARSession()
        }
    }

    @objc public func stopPlaneDetection() {
        // Plane detection o'chirish - lekin tracking saqlansin
        let configuration = ARWorldTrackingConfiguration()
        configuration.planeDetection = []
        configuration.frameSemantics = []
        configuration.environmentTexturing = .none
        // resetTracking ISHLATILMAYDI - model pozitsiyasi saqlansin
        arView.session.run(configuration, options: [])
    }

    // MARK: - ARSessionDelegate

    public func session(_ session: ARSession, didAdd anchors: [ARAnchor]) {
        // Joylashtirilgan bo'lsa - hech narsa qilmaslik
        if isPlaced { return }

        for anchor in anchors {
            if let planeAnchor = anchor as? ARPlaneAnchor {
                // Plane ni saqlash
                detectedPlanes[planeAnchor.identifier] = planeAnchor
                print("📐 Plane topildi: \(planeAnchor.alignment == .vertical ? "VERTICAL" : "horizontal"), size: \(planeAnchor.extent.x)x\(planeAnchor.extent.z)m")

                // Eng yaxshi plane ni tanlash (vertikal afzal)
                updateBestPlane()
            }
        }
    }

    public func session(_ session: ARSession, didUpdate anchors: [ARAnchor]) {
        // Joylashtirilgan bo'lsa - hech narsa qilmaslik
        if isPlaced { return }

        for anchor in anchors {
            if let planeAnchor = anchor as? ARPlaneAnchor {
                // Plane ni yangilash
                detectedPlanes[planeAnchor.identifier] = planeAnchor

                // Eng yaxshi plane ni qayta tanlash
                updateBestPlane()
            }
        }
    }

    public func session(_ session: ARSession, didRemove anchors: [ARAnchor]) {
        for anchor in anchors {
            if let planeAnchor = anchor as? ARPlaneAnchor {
                detectedPlanes.removeValue(forKey: planeAnchor.identifier)
                if bestPlaneAnchor?.identifier == planeAnchor.identifier {
                    bestPlaneAnchor = nil
                    updateBestPlane()
                }
            }
        }
    }

    /// Eng yaxshi plane ni tanlash va auto-place
    private func updateBestPlane() {
        guard !isPlaced else { return }

        // Vertikal plane larni afzal ko'rish (devor uchun)
        let verticalPlanes = detectedPlanes.values.filter { $0.alignment == .vertical }
        let horizontalPlanes = detectedPlanes.values.filter { $0.alignment == .horizontal }

        // Eng katta vertikal plane ni topish
        var bestPlane: ARPlaneAnchor? = nil
        var bestSize: Float = 0

        for plane in verticalPlanes {
            let size = plane.extent.x * plane.extent.z
            if size > bestSize && plane.extent.x >= minPlaneSize && plane.extent.z >= minPlaneSize {
                bestSize = size
                bestPlane = plane
            }
        }

        // Agar vertikal topilmasa, horizontal dan eng kattasini olish
        if bestPlane == nil {
            for plane in horizontalPlanes {
                let size = plane.extent.x * plane.extent.z
                if size > bestSize && plane.extent.x >= minPlaneSize && plane.extent.z >= minPlaneSize {
                    bestSize = size
                    bestPlane = plane
                }
            }
        }

        // Agar yaxshi plane topilsa
        if let plane = bestPlane {
            bestPlaneAnchor = plane
            let planeType = plane.alignment == .vertical ? "Devor" : "Pol"
            let sizeStr = String(format: "%.1fx%.1fm", plane.extent.x, plane.extent.z)

            if !planeDetected {
                planeDetected = true
                stopPlaneDetectionTimer()  // Timer ni to'xtatish
                DispatchQueue.main.async { [weak self] in
                    self?.onPlaneDetected?()
                }
            }

            DispatchQueue.main.async { [weak self] in
                self?.onStatusChange?("\(planeType) topildi (\(sizeStr))")
            }

            // AUTO-PLACE: Agar yetarlicha katta vertikal plane topilsa
            if autoPlaceEnabled && plane.alignment == .vertical && bestSize >= 0.5 {
                // 0.5 m² dan katta vertikal plane - avtomatik joylashtirish
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                    guard let self = self, !self.isPlaced, self.autoPlaceEnabled else { return }
                    print("🎯 Auto-placing on vertical plane")
                    self.placeModelOnPlane(plane)
                }
            }
        }
    }

    /// Model ni plane ustiga joylashtirish
    private func placeModelOnPlane(_ planeAnchor: ARPlaneAnchor) {
        guard !isPlaced else { return }
        guard let model = buildModelEntity() else { return }
        guard let cameraTransform = arView.session.currentFrame?.camera.transform else { return }

        let planeTransform = planeAnchor.transform
        let planePosition = SIMD3<Float>(planeTransform.columns.3.x, planeTransform.columns.3.y, planeTransform.columns.3.z)

        // Yuza turini saqlash
        placedOnVerticalPlane = (planeAnchor.alignment == .vertical)

        // Yuza vektorlarini hisoblash
        if placedOnVerticalPlane {
            // VERTIKAL (devor): X bo'ylab chapga/o'ngga, Y bo'ylab yuqoriga/pastga
            let planeX = SIMD3<Float>(planeTransform.columns.0.x, planeTransform.columns.0.y, planeTransform.columns.0.z)
            planeRightVector = simd_normalize(planeX)
            planeUpVector = SIMD3<Float>(0, 1, 0)  // Gravitatsiya bo'yicha yuqori
        } else {
            // GORIZONTAL (pol): X va Z bo'ylab
            let planeX = SIMD3<Float>(planeTransform.columns.0.x, planeTransform.columns.0.y, planeTransform.columns.0.z)
            let planeZ = SIMD3<Float>(planeTransform.columns.2.x, planeTransform.columns.2.y, planeTransform.columns.2.z)
            planeRightVector = simd_normalize(planeX)
            planeUpVector = simd_normalize(planeZ)  // Polda "oldinga" = ekranda "yuqoriga"
        }

        // Kameraning faqat gorizontal yo'nalishini olish (Y o'qi bo'yicha aylanish)
        let cameraForward = SIMD3<Float>(-cameraTransform.columns.2.x, 0, -cameraTransform.columns.2.z)
        let yAngle = atan2(cameraForward.x, cameraForward.z)

        // Gravitatsiyaga nisbatan tik anchor yaratish
        var anchorTransform = matrix_identity_float4x4
        anchorTransform.columns.3 = SIMD4<Float>(planePosition.x, planePosition.y, planePosition.z, 1)

        let anchor = AnchorEntity(world: anchorTransform)

        // Kameraga qarashi uchun Y o'qi bo'yicha aylanish
        modelRotationAngle = yAngle + .pi  // Boshlang'ich burchakni saqlash
        model.orientation = simd_quatf(angle: modelRotationAngle, axis: [0, 1, 0])

        anchor.addChild(model)
        arView.scene.addAnchor(anchor)
        modelAnchor = anchor
        modelEntity = model

        isPlaced = true
        autoPlaceEnabled = false

        stopPlaneDetection()
        onModelPlaced?()
        onStatusChange?("Oyna joylashtirildi!")

        print("✅ Model placed on \(planeAnchor.alignment == .vertical ? "vertical" : "horizontal") plane, yAngle: \(yAngle * 180 / .pi)°")
    }


    public func session(_ session: ARSession, didFailWithError error: Error) {
        print("❌ AR Session failed: \(error.localizedDescription)")
        sessionStarted = false

        if let arError = error as? ARError {
            switch arError.code {
            case .cameraUnauthorized:
                onStatusChange?("Kamera ruxsati kerak")
            case .sensorFailed:
                onStatusChange?("Sensor xatosi")
                // Qayta urinish
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                    self?.startARSession()
                }
            case .sensorUnavailable:
                onStatusChange?("Sensor mavjud emas")
            case .worldTrackingFailed:
                onStatusChange?("Tracking xatosi - qayta urinilmoqda...")
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                    self?.startARSession()
                }
            default:
                onStatusChange?("AR xatosi: \(arError.localizedDescription)")
            }
        }
    }

    public func sessionWasInterrupted(_ session: ARSession) {
        print("⚠️ AR Session interrupted")
        onStatusChange?("AR to'xtatildi...")
    }

    public func sessionInterruptionEnded(_ session: ARSession) {
        print("✅ AR Session interruption ended")
        // Kamera stabilligini ta'minlash uchun kichik kechikish
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self = self else { return }
            self.sessionStarted = false

            if self.isPlaced {
                // Model mavjud - tracking davom etsin
                let configuration = ARWorldTrackingConfiguration()
                configuration.planeDetection = []
                configuration.frameSemantics = []
                configuration.environmentTexturing = .none
                self.arView.session.run(configuration, options: [])
                self.sessionStarted = true
                self.onStatusChange?("AR qayta tiklandi")
            } else {
                self.startARSession()
            }
        }
    }

    // Tracking holati o'zgarganda
    public func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
        switch camera.trackingState {
        case .notAvailable:
            print("⚠️ Tracking not available")
        case .limited(let reason):
            print("⚠️ Tracking limited: \(reason)")
            switch reason {
            case .initializing:
                if !isPlaced {
                    onStatusChange?("AR yuklanmoqda...")
                }
            case .excessiveMotion:
                onStatusChange?("Sekinroq harakat qiling")
            case .insufficientFeatures:
                onStatusChange?("Koʻproq yorugʻlik kerak")
            case .relocalizing:
                onStatusChange?("Qayta kalibrlash...")
            @unknown default:
                break
            }
        case .normal:
            if !isPlaced && planeDetected {
                onStatusChange?("Yuza topildi!")
            } else if !isPlaced {
                onStatusChange?("Yuzalarni qidiring...")
            }
        }
    }

    // MARK: - Gestures

    @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
        guard isPlaced, let model = modelEntity else { return }

        let scale = Float(gesture.scale)
        model.transform.scale *= scale
        gesture.scale = 1.0
    }

    @objc private func handleSinglePan(_ gesture: UIPanGestureRecognizer) {
        guard isPlaced, let anchor = modelAnchor else { return }

        let translation = gesture.translation(in: arView)

        // Ekran harakatini 3D harakatga aylantirish
        let moveSpeed: Float = 0.001
        let moveX = Float(translation.x) * moveSpeed    // Ekranda chapga/o'ngga
        let moveY = Float(-translation.y) * moveSpeed   // Ekranda yuqoriga/pastga

        // Yuza bo'ylab harakat (saqlangan vektorlar bo'yicha)
        let movement = planeRightVector * moveX + planeUpVector * moveY

        // Anchor pozitsiyasini yangilash
        var newTransform = anchor.transform
        newTransform.translation += movement
        anchor.transform = newTransform

        gesture.setTranslation(.zero, in: arView)
    }

    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        guard isPlaced, let model = modelEntity else { return }

        let translation = gesture.translation(in: arView)

        // Y o'qi atrofida aylantirish (gorizontal harakatga qarab)
        modelRotationAngle += Float(translation.x) * 0.01

        // Faqat Y o'qi bo'yicha aylanish (model tik turgan holda aylanadi)
        model.orientation = simd_quatf(angle: modelRotationAngle, axis: [0, 1, 0])

        gesture.setTranslation(.zero, in: arView)
    }

    // MARK: - Model Placement

    private func placeModelAt(transform: simd_float4x4) {
        guard let model = buildModelEntity() else { return }
        guard let cameraTransform = arView.session.currentFrame?.camera.transform else { return }

        let position = SIMD3<Float>(transform.columns.3.x, transform.columns.3.y, transform.columns.3.z)

        // Kameraning gorizontal yo'nalishini olish
        let cameraForward = SIMD3<Float>(-cameraTransform.columns.2.x, 0, -cameraTransform.columns.2.z)
        let yAngle = atan2(cameraForward.x, cameraForward.z)

        // Yuza vektorlarini sozlash (kameraga parallel vertikal yuza deb hisoblaymiz)
        placedOnVerticalPlane = true
        let cameraRight = SIMD3<Float>(cameraTransform.columns.0.x, 0, cameraTransform.columns.0.z)
        planeRightVector = simd_normalize(cameraRight)
        planeUpVector = SIMD3<Float>(0, 1, 0)

        // Anchor - faqat pozitsiya
        var anchorTransform = matrix_identity_float4x4
        anchorTransform.columns.3 = SIMD4<Float>(position.x, position.y, position.z, 1)

        let anchor = AnchorEntity(world: anchorTransform)

        // Y o'qi bo'yicha aylanish
        modelRotationAngle = yAngle + .pi
        model.orientation = simd_quatf(angle: modelRotationAngle, axis: [0, 1, 0])

        anchor.addChild(model)
        arView.scene.addAnchor(anchor)

        modelAnchor = anchor
        modelEntity = model
        isPlaced = true

        stopPlaneDetection()
        onModelPlaced?()
        onStatusChange?("Oyna joylashtirildi!")
    }

    private func placeModelInFront() {
        guard let model = buildModelEntity() else { return }
        guard let cameraTransform = arView.session.currentFrame?.camera.transform else { return }

        // Kamera oldida 1.5 metr
        var translation = matrix_identity_float4x4
        translation.columns.3.z = -1.5
        let finalTransform = simd_mul(cameraTransform, translation)
        let position = SIMD3<Float>(finalTransform.columns.3.x, finalTransform.columns.3.y, finalTransform.columns.3.z)

        // Kameraning gorizontal yo'nalishini olish
        let cameraForward = SIMD3<Float>(-cameraTransform.columns.2.x, 0, -cameraTransform.columns.2.z)
        let yAngle = atan2(cameraForward.x, cameraForward.z)

        // Yuza vektorlarini sozlash (kameraga parallel vertikal yuza deb hisoblaymiz)
        placedOnVerticalPlane = true
        let cameraRight = SIMD3<Float>(cameraTransform.columns.0.x, 0, cameraTransform.columns.0.z)
        planeRightVector = simd_normalize(cameraRight)
        planeUpVector = SIMD3<Float>(0, 1, 0)

        // Anchor - faqat pozitsiya
        var anchorTransform = matrix_identity_float4x4
        anchorTransform.columns.3 = SIMD4<Float>(position.x, position.y, position.z, 1)

        let anchor = AnchorEntity(world: anchorTransform)

        // Y o'qi bo'yicha aylanish
        modelRotationAngle = yAngle + .pi
        model.orientation = simd_quatf(angle: modelRotationAngle, axis: [0, 1, 0])

        anchor.addChild(model)
        arView.scene.addAnchor(anchor)

        modelAnchor = anchor
        modelEntity = model
        isPlaced = true

        stopPlaneDetection()
        onModelPlaced?()
        onStatusChange?("Oyna joylashtirildi!")
    }

    @objc public func resetPlacement() {
        modelAnchor?.removeFromParent()
        modelAnchor = nil
        modelEntity = nil
        isPlaced = false
        isOpen = false
        isTilted = false
        planeDetected = false
        modelRotationAngle = 0
        openPartEntities.removeAll()
        openPartData.removeAll()
        originalPivotPositions.removeAll()

        // Plane detection state ni tozalash
        detectedPlanes.removeAll()
        bestPlaneAnchor = nil
        autoPlaceEnabled = true  // Qayta auto-place yoqish

        // Session ni qayta boshlash
        sessionStarted = false
        startARSession()
    }

    // MARK: - Callbacks

    @objc public func setOnStatusChange(_ callback: @escaping (String) -> Void) {
        self.onStatusChange = callback
    }

    @objc public func setOnPlaneDetected(_ callback: @escaping () -> Void) {
        self.onPlaneDetected = callback
    }

    @objc public func setOnModelPlaced(_ callback: @escaping () -> Void) {
        self.onModelPlaced = callback
    }

    @objc public func setOnPlaneNotDetected(_ callback: @escaping () -> Void) {
        print("✅ setOnPlaneNotDetected callback set")
        self.onPlaneNotDetected = callback
    }

    // MARK: - Public API (Kotlin dan chaqiriladi)

    /// Auto-place ni yoqish/o'chirish
    @objc public func setAutoPlaceEnabled(_ enabled: NSNumber) {
        autoPlaceEnabled = enabled.boolValue
        print("🎯 Auto-place: \(autoPlaceEnabled ? "enabled" : "disabled")")
    }

    /// Topilgan plane soni
    @objc public func getDetectedPlanesCount() -> NSNumber {
        return NSNumber(value: detectedPlanes.count)
    }

    /// Vertikal plane topildimi
    @objc public func hasVerticalPlane() -> NSNumber {
        let hasVertical = detectedPlanes.values.contains { $0.alignment == .vertical }
        return NSNumber(value: hasVertical)
    }

    @objc public func setModelSizeWithDict(_ dict: NSDictionary) {
        modelWidth = (dict["width"] as? NSNumber)?.floatValue ?? 1000
        modelHeight = (dict["height"] as? NSNumber)?.floatValue ?? 1000
        modelDepth = (dict["depth"] as? NSNumber)?.floatValue ?? 100
    }

    @objc public func addMeshWithData(_ data: NSDictionary) {
        guard let name = data["name"] as? String,
              let positionsArray = data["positions"] as? [NSNumber],
              let normalsArray = data["normals"] as? [NSNumber],
              let colorsArray = data["colors"] as? [NSNumber] else {
            print("Invalid mesh data")
            return
        }

        let positions = positionsArray.map { $0.floatValue }
        let normals = normalsArray.map { $0.floatValue }
        let colors = colorsArray.map { $0.floatValue }
        let texCoordsArray = data["texCoords"] as? [NSNumber] ?? []
        let texCoords = texCoordsArray.map { $0.floatValue }

        let openPartIndex = (data["openPartIndex"] as? NSNumber)?.intValue ?? -1
        let pivotX = (data["pivotX"] as? NSNumber)?.floatValue ?? 0
        let pivotY = (data["pivotY"] as? NSNumber)?.floatValue ?? 0
        let pivotZ = (data["pivotZ"] as? NSNumber)?.floatValue ?? 0
        let axisX = (data["axisX"] as? NSNumber)?.floatValue ?? 0
        let axisY = (data["axisY"] as? NSNumber)?.floatValue ?? 1
        let axisZ = (data["axisZ"] as? NSNumber)?.floatValue ?? 0
        let maxAngle = (data["maxAngle"] as? NSNumber)?.floatValue ?? 90

        // Tilt animatsiya ma'lumotlari
        let tiltPivotX = (data["tiltPivotX"] as? NSNumber)?.floatValue ?? 0
        let tiltPivotY = (data["tiltPivotY"] as? NSNumber)?.floatValue ?? 0
        let tiltPivotZ = (data["tiltPivotZ"] as? NSNumber)?.floatValue ?? 0
        let tiltAxisX = (data["tiltAxisX"] as? NSNumber)?.floatValue ?? 1
        let tiltAxisY = (data["tiltAxisY"] as? NSNumber)?.floatValue ?? 0
        let tiltAxisZ = (data["tiltAxisZ"] as? NSNumber)?.floatValue ?? 0
        let tiltMaxAngle = (data["tiltMaxAngle"] as? NSNumber)?.floatValue ?? 0
        let tiltPhaseStart = (data["tiltPhaseStart"] as? NSNumber)?.floatValue ?? 0
        let tiltPhaseEnd = (data["tiltPhaseEnd"] as? NSNumber)?.floatValue ?? 1
        let phaseStart = (data["phaseStart"] as? NSNumber)?.floatValue ?? 0
        let phaseEnd = (data["phaseEnd"] as? NSNumber)?.floatValue ?? 1

        let mesh = PendingMeshAR(
            name: name,
            positions: positions,
            normals: normals,
            colors: colors,
            texCoords: texCoords,
            openPartIndex: openPartIndex,
            pivot: [pivotX, pivotY, pivotZ],
            axis: [axisX, axisY, axisZ],
            maxAngle: maxAngle,
            tiltPivot: [tiltPivotX, tiltPivotY, tiltPivotZ],
            tiltAxis: [tiltAxisX, tiltAxisY, tiltAxisZ],
            tiltMaxAngle: tiltMaxAngle,
            tiltPhaseStart: tiltPhaseStart,
            tiltPhaseEnd: tiltPhaseEnd,
            phaseStart: phaseStart,
            phaseEnd: phaseEnd
        )
        pendingMeshes.append(mesh)
    }

    @objc public func getIsPlaced() -> NSNumber {
        return NSNumber(value: isPlaced)
    }

    @objc public func getPlaneDetected() -> NSNumber {
        return NSNumber(value: planeDetected)
    }

    @objc public func getHasStandardOpening() -> NSNumber {
        return NSNumber(value: pendingMeshes.contains { $0.maxAngle != 0 && $0.openPartIndex >= 0 })
    }

    @objc public func getHasTiltOpening() -> NSNumber {
        return NSNumber(value: pendingMeshes.contains { $0.tiltMaxAngle != 0 && $0.openPartIndex >= 0 })
    }

    /// 30 sek o'tib yuza topilmadimi tekshirish (Kotlin polling)
    @objc public func getPlaneNotDetectedTriggered() -> NSNumber {
        return NSNumber(value: planeNotDetectedTriggered)
    }

    /// Qidrishni davom ettirish (timer reset)
    @objc public func continueSearching() {
        print("🔄 Continue searching for planes...")
        planeNotDetectedTriggered = false  // Flag ni reset qilish
        startPlaneDetectionTimer()  // Timer ni qayta boshlash
    }

    /// Modelni kamera oldiga joylashtirish (manual fallback)
    @objc public func placeModelManually() {
        print("📍 Manual placement - placing in front of camera")
        planeNotDetectedTriggered = false  // Flag ni reset qilish
        stopPlaneDetectionTimer()
        placeModelInFront()
    }

    // MARK: - Texture Setup

    @objc public func setProfileTexture(_ image: UIImage?) {
        guard let image = image, let cgImage = image.cgImage else {
            profileTexture = nil
            return
        }
        do {
            profileTexture = try TextureResource.generate(from: cgImage, options: .init(semantic: .color))
        } catch {
            print("❌ AR Profile texture load error: \(error)")
            profileTexture = nil
        }
    }

    @objc public func setShotlankaTexture(_ image: UIImage?) {
        guard let image = image, let cgImage = image.cgImage else {
            shotlankaTexture = nil
            return
        }
        do {
            shotlankaTexture = try TextureResource.generate(from: cgImage, options: .init(semantic: .color))
        } catch {
            print("❌ AR Shotlanka texture load error: \(error)")
            shotlankaTexture = nil
        }
    }

    // MARK: - Model Building

    private func buildModelEntity() -> Entity? {
        let newModel = Entity()
        let scale: Float = 0.001
        let centerX = modelWidth * scale / 2
        let centerY = modelHeight * scale / 2
        let centerZ = modelDepth * scale / 2
        let center: SIMD3<Float> = [centerX, centerY, centerZ]

        openPartEntities.removeAll()
        openPartData.removeAll()
        originalPivotPositions.removeAll()

        // Meshlarni turlarga ajratish
        let frameMeshes = pendingMeshes.filter { $0.openPartIndex >= 0 && $0.openPartIndex < 1000 }
        let leverMeshes = pendingMeshes.filter { $0.openPartIndex >= 1000 }
        let staticMeshes = pendingMeshes.filter { $0.openPartIndex < 0 }

        // Profil rangini topish (birinchi frame/profil meshdan)
        // Handle va petlya uchun ishlatiladi
        for meshData in pendingMeshes {
            let nameLower = meshData.name.lowercased()
            let isProfileMesh = nameLower.contains("profile") || nameLower.contains("profil") ||
                               nameLower.contains("frame") || nameLower.contains("rama")
            // Handle, petlya, glass, rubber, shtapik emas
            let isNotAccessory = !nameLower.contains("handle") && !nameLower.contains("petlya") &&
                                !nameLower.contains("hinge") && !nameLower.contains("glass") &&
                                !nameLower.contains("rubber") && !nameLower.contains("shtapik")
            if isProfileMesh && isNotAccessory && meshData.colors.count >= 4 {
                let r = min(1.0, max(0.0, meshData.colors[0]))
                let g = min(1.0, max(0.0, meshData.colors[1]))
                let b = min(1.0, max(0.0, meshData.colors[2]))
                profileColor = (r, g, b)
                print("=== AR PROFILE COLOR FOUND: (\(r), \(g), \(b)) from \(meshData.name) ===")
                break
            }
        }

        // 1. FRAME pivotlarni yaratish
        for meshData in frameMeshes {
            var entities: [ModelEntity] = []
            let nameLower = meshData.name.lowercased()
            let isShtapik = nameLower.contains("shtapik")
            let isLambri = nameLower.contains("lambri")
            let isPanel = nameLower.contains("panel")
            // Profile, lambri va panel meshlar - texturani faqat Z yuzalarga qo'yish
            let isProfile = (nameLower.contains("profile") || nameLower.contains("profil") ||
                            nameLower.contains("frame") || nameLower.contains("rama") ||
                            nameLower.contains("impost")) && profileTexture != nil
            let needsProfileTexture = (isProfile || isLambri || isPanel) && profileTexture != nil

            if isShtapik {
                entities = createShtapikEntities(from: meshData, scale: scale, center: center)
            } else if needsProfileTexture {
                entities = createProfileEntities(from: meshData, scale: scale, center: center)
            } else if let entity = createMeshEntity(from: meshData, scale: scale, center: center) {
                entities = [entity]
            }

            guard !entities.isEmpty else { continue }

            // Pivot yaratish yoki mavjudini olish
            let pivot: Entity
            let isNewPivot: Bool
            if let existingPivot = openPartEntities[meshData.openPartIndex] {
                pivot = existingPivot
                isNewPivot = false
            } else {
                pivot = Entity()
                pivot.position = meshData.pivot * scale - center
                newModel.addChild(pivot)
                openPartEntities[meshData.openPartIndex] = pivot
                openPartData[meshData.openPartIndex] = (meshData.axis, meshData.maxAngle, meshData.tiltPivot, meshData.tiltAxis, meshData.tiltMaxAngle, meshData.tiltPhaseStart, meshData.tiltPhaseEnd)
                isNewPivot = true
            }

            for entity in entities {
                if isNewPivot {
                    entity.position = -(meshData.pivot * scale - center)
                } else {
                    entity.position = -pivot.position
                }
                pivot.addChild(entity)
            }
        }

        // 2. LEVER pivotlarni yaratish (frame ichida)
        for meshData in leverMeshes {
            var entities: [ModelEntity] = []
            let nameLower = meshData.name.lowercased()
            let isShtapik = nameLower.contains("shtapik")
            let isLambri = nameLower.contains("lambri")
            let isPanel = nameLower.contains("panel")
            let isProfile = (nameLower.contains("profile") || nameLower.contains("profil") ||
                            nameLower.contains("frame") || nameLower.contains("rama") ||
                            nameLower.contains("impost")) && profileTexture != nil
            let needsProfileTexture = (isProfile || isLambri || isPanel) && profileTexture != nil

            if isShtapik {
                entities = createShtapikEntities(from: meshData, scale: scale, center: center)
            } else if needsProfileTexture {
                entities = createProfileEntities(from: meshData, scale: scale, center: center)
            } else if let entity = createMeshEntity(from: meshData, scale: scale, center: center) {
                entities = [entity]
            }

            guard !entities.isEmpty else { continue }

            let frameIndex = meshData.openPartIndex >= 2000
                ? meshData.openPartIndex - 2000
                : meshData.openPartIndex - 1000
            let leverPivotWorldPos = meshData.pivot * scale - center

            let leverPivot: Entity
            if let existingPivot = openPartEntities[meshData.openPartIndex] {
                leverPivot = existingPivot
            } else {
                leverPivot = Entity()

                if let framePivot = openPartEntities[frameIndex] {
                    let framePivotWorldPos = framePivot.position
                    leverPivot.position = leverPivotWorldPos - framePivotWorldPos
                    framePivot.addChild(leverPivot)
                } else {
                    leverPivot.position = leverPivotWorldPos
                    newModel.addChild(leverPivot)
                }

                openPartEntities[meshData.openPartIndex] = leverPivot
                openPartData[meshData.openPartIndex] = (meshData.axis, meshData.maxAngle, meshData.tiltPivot, meshData.tiltAxis, meshData.tiltMaxAngle, meshData.tiltPhaseStart, meshData.tiltPhaseEnd)
            }

            for entity in entities {
                entity.position = -(meshData.pivot * scale - center)
                leverPivot.addChild(entity)
            }
        }

        // 3. Statik meshlar
        for meshData in staticMeshes {
            var entities: [ModelEntity] = []
            let nameLower = meshData.name.lowercased()
            let isShtapik = nameLower.contains("shtapik")
            let isLambri = nameLower.contains("lambri")
            let isPanel = nameLower.contains("panel")
            let needsProfileTexture = (nameLower.contains("profile") || nameLower.contains("profil") ||
                            nameLower.contains("frame") || nameLower.contains("rama") ||
                            nameLower.contains("impost") || isLambri || isPanel) && profileTexture != nil

            if isShtapik {
                entities = createShtapikEntities(from: meshData, scale: scale, center: center)
            } else if needsProfileTexture {
                entities = createProfileEntities(from: meshData, scale: scale, center: center)
            } else if let entity = createMeshEntity(from: meshData, scale: scale, center: center) {
                entities = [entity]
            }

            for entity in entities {
                newModel.addChild(entity)
            }
        }

        return newModel
    }

    // MARK: - Mesh Creation

    private func createMeshEntity(from data: PendingMeshAR, scale: Float, center: SIMD3<Float>) -> ModelEntity? {
        let vertexCount = data.positions.count / 3
        guard vertexCount >= 3 else { return nil }

        var positions: [SIMD3<Float>] = []
        var normals: [SIMD3<Float>] = []
        var texCoords: [SIMD2<Float>] = []
        let hasTexCoords = data.texCoords.count >= vertexCount * 2
        let nameLower = data.name.lowercased()
        let isProfile = nameLower.contains("profile") || nameLower.contains("profil") ||
                        nameLower.contains("frame") || nameLower.contains("rama") ||
                        nameLower.contains("impost")

        for i in 0..<vertexCount {
            let x = data.positions[i * 3] * scale - center.x
            let y = data.positions[i * 3 + 1] * scale - center.y
            let z = data.positions[i * 3 + 2] * scale - center.z

            // Validate coordinates
            if x.isNaN || y.isNaN || z.isNaN || abs(x) > 10 || abs(y) > 10 || abs(z) > 10 {
                continue
            }

            positions.append([x, y, z])

            let nx = data.normals[i * 3]
            let ny = data.normals[i * 3 + 1]
            let nz = data.normals[i * 3 + 2]
            normals.append([nx, ny, nz])

            if hasTexCoords {
                let u = data.texCoords[i * 2]
                let v = data.texCoords[i * 2 + 1]
                // Profile uchun UV swap - textura uzunasiga cho'zilishi uchun
                if isProfile {
                    texCoords.append([v, u])  // UV swap
                } else {
                    texCoords.append([u, v])
                }
            }
        }

        guard positions.count >= 3 else { return nil }

        var descriptor = MeshDescriptor()
        descriptor.positions = MeshBuffer(positions)
        descriptor.normals = MeshBuffer(normals)
        if hasTexCoords && texCoords.count == positions.count {
            descriptor.textureCoordinates = MeshBuffer(texCoords)
        }
        descriptor.primitives = .triangles((0..<UInt32(positions.count)).map { $0 })

        guard let mesh = try? MeshResource.generate(from: [descriptor]) else { return nil }

        let material = createPBRMaterial(for: data.name, colors: data.colors)
        return ModelEntity(mesh: mesh, materials: [material])
    }

    /// Profile meshlarni Z yuzalari (texturali) va yon yuzalari (rangli) ga bo'lish
    /// Bu texturani faqat old/orqa yuzalarga qo'yadi - real ishlab chiqarishdek
    /// Profile meshlarni textura bilan yaratish - barcha yuzalarga textura qo'yiladi
    private func createProfileEntities(from data: PendingMeshAR, scale: Float, center: SIMD3<Float>) -> [ModelEntity] {
        let vertexCount = data.positions.count / 3
        guard vertexCount >= 3 else { return [] }

        // Barcha yuzalar uchun massivlar
        var allFacePositions: [SIMD3<Float>] = []
        var allFaceNormals: [SIMD3<Float>] = []
        var allFaceTexCoords: [SIMD2<Float>] = []

        let hasTexCoords = data.texCoords.count >= vertexCount * 2

        // Profil yo'nalishini aniqlash (gorizontal yoki vertikal)
        let nameLower = data.name.lowercased()
        let isPanel = nameLower.contains("panel")
        let isHorizontal = !isPanel && (nameLower.contains("top") || nameLower.contains("bottom") ||
                          nameLower.contains("impost_h") || nameLower.contains("horizontal") ||
                          nameLower.contains("t_profile_h") || nameLower.contains("lambri_h") ||
                          nameLower.contains("_h_"))

        // Avval barcha pozitsiyalarni tayyorlash
        var allPositions: [SIMD3<Float>] = []

        for i in 0..<vertexCount {
            let x = data.positions[i * 3] * scale - center.x
            let y = data.positions[i * 3 + 1] * scale - center.y
            let z = data.positions[i * 3 + 2] * scale - center.z

            if x.isNaN || y.isNaN || z.isNaN || abs(x) > 10 || abs(y) > 10 || abs(z) > 10 {
                allPositions.append([0, 0, 0])
            } else {
                allPositions.append([x, y, z])
            }
        }

        // Bounding box
        var minX: Float = .infinity, maxX: Float = -.infinity
        var minY: Float = .infinity, maxY: Float = -.infinity
        var minZ: Float = .infinity, maxZ: Float = -.infinity
        for pos in allPositions {
            if !pos.x.isNaN && !pos.y.isNaN && !pos.z.isNaN && abs(pos.x) < 10 && abs(pos.y) < 10 && abs(pos.z) < 10 {
                minX = min(minX, pos.x)
                maxX = max(maxX, pos.x)
                minY = min(minY, pos.y)
                maxY = max(maxY, pos.y)
                minZ = min(minZ, pos.z)
                maxZ = max(maxZ, pos.z)
            }
        }

        // Textura o'lchami (metrda) - har 150mm da 1 ta textura
        let textureSizeMeters: Float = 0.15

        // Har bir triangle uchun
        let triangleCount = vertexCount / 3
        for t in 0..<triangleCount {
            let i0 = t * 3
            let i1 = t * 3 + 1
            let i2 = t * 3 + 2

            guard i2 < allPositions.count else { continue }

            let p0 = allPositions[i0]
            let p1 = allPositions[i1]
            let p2 = allPositions[i2]

            // Triangle normal hisoblash
            let edge1 = p1 - p0
            let edge2 = p2 - p0
            var faceNormal = simd_cross(edge1, edge2)
            let len = simd_length(faceNormal)
            if len > 0.0001 {
                faceNormal = faceNormal / len
            }

            // Barcha yuzalarni qo'shish
            allFacePositions.append(contentsOf: [p0, p1, p2])
            allFaceNormals.append(contentsOf: [faceNormal, faceNormal, faceNormal])

            let absNx = abs(faceNormal.x)
            let absNy = abs(faceNormal.y)
            let absNz = abs(faceNormal.z)

            // UV koordinatalarni normal yo'nalishiga qarab hisoblash
            for idx in [i0, i1, i2] {
                var useKotlinUV = false
                if hasTexCoords && idx * 2 + 1 < data.texCoords.count {
                    let u = data.texCoords[idx * 2]
                    let v = data.texCoords[idx * 2 + 1]
                    if u != 0 || v != 0 {
                        allFaceTexCoords.append([u, v])
                        useKotlinUV = true
                    }
                }
                if !useKotlinUV {
                    let pos = allPositions[idx]
                    let texU: Float
                    let texV: Float

                    // Normal yo'nalishiga qarab UV hisoblash
                    if absNz > absNx && absNz > absNy {
                        // Z-facing (old/orqa) - X va Y dan UV
                        if isHorizontal {
                            texU = (pos.y - minY) / textureSizeMeters
                            texV = (pos.x - minX) / textureSizeMeters
                        } else {
                            texU = (pos.x - minX) / textureSizeMeters
                            texV = (pos.y - minY) / textureSizeMeters
                        }
                    } else if absNx > absNy && absNx > absNz {
                        // X-facing (chap/o'ng yon) - Y va Z dan UV
                        texU = (pos.z - minZ) / textureSizeMeters
                        texV = (pos.y - minY) / textureSizeMeters
                    } else {
                        // Y-facing (tepa/past) - X va Z dan UV
                        texU = (pos.x - minX) / textureSizeMeters
                        texV = (pos.z - minZ) / textureSizeMeters
                    }
                    allFaceTexCoords.append([texU, texV])
                }
            }
        }

        var result: [ModelEntity] = []

        // Profil rangi - colors dan olish
        var r: Float = 0.96, g: Float = 0.96, b: Float = 0.96
        if data.colors.count >= 4 {
            r = min(1.0, max(0.0, data.colors[0]))
            g = min(1.0, max(0.0, data.colors[1]))
            b = min(1.0, max(0.0, data.colors[2]))
        }

        // Bitta mesh - barcha yuzalar textura bilan
        if allFacePositions.count >= 3 {
            var descriptor = MeshDescriptor()
            descriptor.positions = MeshBuffer(allFacePositions)
            descriptor.normals = MeshBuffer(allFaceNormals)
            if allFaceTexCoords.count == allFacePositions.count {
                descriptor.textureCoordinates = MeshBuffer(allFaceTexCoords)
            }
            descriptor.primitives = .triangles((0..<UInt32(allFacePositions.count)).map { $0 })

            if let mesh = try? MeshResource.generate(from: [descriptor]) {
                let material: Material
                if let texture = profileTexture {
                    var mat = PhysicallyBasedMaterial()
                    let samplerDesc = MTLSamplerDescriptor()
                    samplerDesc.sAddressMode = .repeat
                    samplerDesc.tAddressMode = .repeat
                    samplerDesc.minFilter = .linear
                    samplerDesc.magFilter = .linear
                    samplerDesc.mipFilter = .linear
                    mat.baseColor = .init(texture: .init(texture, sampler: .init(samplerDesc)))
                    mat.roughness = .init(floatLiteral: 0.35)
                    mat.metallic = .init(floatLiteral: 0.0)
                    mat.specular = .init(floatLiteral: 0.5)
                    mat.faceCulling = .none
                    material = mat
                } else {
                    var mat = PhysicallyBasedMaterial()
                    mat.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
                    mat.metallic = .init(floatLiteral: 0.0)
                    mat.roughness = .init(floatLiteral: 0.35)
                    mat.faceCulling = .none
                    material = mat
                }
                result.append(ModelEntity(mesh: mesh, materials: [material]))
            }
        }

        return result
    }

    // Shtapik uchun - qora oyoq va oq tana alohida (barcha yuzalarga textura)
    private func createShtapikEntities(from data: PendingMeshAR, scale: Float, center: SIMD3<Float>) -> [ModelEntity] {
        let vertexCount = data.positions.count / 3
        guard vertexCount >= 3 else { return [] }

        // Dark (rezina) va bright (textura bilan barcha yuzalar) uchun massivlar
        var darkPositions: [SIMD3<Float>] = []
        var darkNormals: [SIMD3<Float>] = []

        var brightPositions: [SIMD3<Float>] = []
        var brightNormals: [SIMD3<Float>] = []
        var brightTexCoords: [SIMD2<Float>] = []

        let nameLower = data.name.lowercased()
        let needsNormalFix = nameLower.contains("left") || nameLower.contains("top")
        let hasTexCoords = data.texCoords.count >= vertexCount * 2

        // Shtapik bounding box hisoblash (vertex pozitsiyalaridan)
        var minX: Float = .infinity, maxX: Float = -.infinity
        var minY: Float = .infinity, maxY: Float = -.infinity
        var minZ: Float = .infinity, maxZ: Float = -.infinity
        for i in 0..<vertexCount {
            let x = data.positions[i * 3] * scale - center.x
            let y = data.positions[i * 3 + 1] * scale - center.y
            let z = data.positions[i * 3 + 2] * scale - center.z
            if !x.isNaN && !y.isNaN && !z.isNaN && abs(x) < 10 && abs(y) < 10 && abs(z) < 10 {
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                minZ = min(minZ, z)
                maxZ = max(maxZ, z)
            }
        }
        // Textura o'lchami
        let textureSizeMeters: Float = 0.15  // 150mm per texture

        // Profil rangini topish
        var profileR: Float = 0.96, profileG: Float = 0.96, profileB: Float = 0.96
        for i in 0..<min(data.colors.count / 4, 100) {
            let r = data.colors[i * 4]
            let g = data.colors[i * 4 + 1]
            let b = data.colors[i * 4 + 2]
            let brightness = (r + g + b) / 3.0
            if brightness >= 0.2 && brightness < 0.98 {
                profileR = r; profileG = g; profileB = b
                break
            }
        }

        let triangleCount = vertexCount / 3
        for t in 0..<triangleCount {
            let i0 = t * 3
            let i1 = t * 3 + 1
            let i2 = t * 3 + 2

            var avgBrightness: Float = 0
            for vi in [i0, i1, i2] {
                if vi * 4 + 2 < data.colors.count {
                    let r = data.colors[vi * 4]
                    let g = data.colors[vi * 4 + 1]
                    let b = data.colors[vi * 4 + 2]
                    avgBrightness += (r + g + b) / 3.0
                }
            }
            avgBrightness /= 3.0

            // Rezina qismi - faqat juda qora (brightness < 0.2)
            let isDark = avgBrightness < 0.2

            var hasInvalidVertex = false
            for vi in [i0, i1, i2] {
                let x = data.positions[vi * 3] * scale - center.x
                let y = data.positions[vi * 3 + 1] * scale - center.y
                let z = data.positions[vi * 3 + 2] * scale - center.z
                if x.isNaN || y.isNaN || z.isNaN || abs(x) > 10 || abs(y) > 10 || abs(z) > 10 {
                    hasInvalidVertex = true
                    break
                }
            }

            if hasInvalidVertex { continue }

            // Pozitsiyalarni hisoblash
            var positions: [SIMD3<Float>] = []
            var normals: [SIMD3<Float>] = []

            for vi in [i0, i1, i2] {
                let x = data.positions[vi * 3] * scale - center.x
                let y = data.positions[vi * 3 + 1] * scale - center.y
                let z = data.positions[vi * 3 + 2] * scale - center.z
                var nx = data.normals[vi * 3]
                var ny = data.normals[vi * 3 + 1]
                var nz = data.normals[vi * 3 + 2]

                if needsNormalFix {
                    nx = -nx; ny = -ny; nz = -nz
                }

                positions.append([x, y, z])
                normals.append([nx, ny, nz])
            }

            // Triangle bounding box - lokal yo'nalishni aniqlash uchun
            let p0 = positions[0]
            let p1 = positions[1]
            let p2 = positions[2]
            let triMinX = min(p0.x, min(p1.x, p2.x))
            let triMaxX = max(p0.x, max(p1.x, p2.x))
            let triMinY = min(p0.y, min(p1.y, p2.y))
            let triMaxY = max(p0.y, max(p1.y, p2.y))
            let triWidth = triMaxX - triMinX
            let triHeight = triMaxY - triMinY

            // Triangle markazini hisoblash
            let triCenterX = (p0.x + p1.x + p2.x) / 3.0
            let triCenterY = (p0.y + p1.y + p2.y) / 3.0

            // Gorizontal/vertikal aniqlash - pozitsiyaga asoslangan
            let distToTop = abs(triCenterY - maxY)
            let distToBottom = abs(triCenterY - minY)
            let distToLeft = abs(triCenterX - minX)
            let distToRight = abs(triCenterX - maxX)

            let minHorizontalDist = min(distToTop, distToBottom)
            let minVerticalDist = min(distToLeft, distToRight)

            let isTriangleHorizontal = minHorizontalDist < minVerticalDist

            // Face normal hisoblash
            let edge1 = p1 - p0
            let edge2 = p2 - p0
            var faceNormal = simd_cross(edge1, edge2)
            let faceLen = simd_length(faceNormal)
            if faceLen > 0.0001 {
                faceNormal = faceNormal / faceLen
            }

            let absNx = abs(faceNormal.x)
            let absNy = abs(faceNormal.y)
            let absNz = abs(faceNormal.z)

            if isDark {
                // Qora rezina qismi
                darkPositions.append(contentsOf: positions)
                darkNormals.append(contentsOf: normals)
            } else {
                // Yorug' qism - barcha yuzalarga textura
                brightPositions.append(contentsOf: positions)
                brightNormals.append(contentsOf: [faceNormal, faceNormal, faceNormal])

                // UV koordinatalarni normal yo'nalishiga qarab hisoblash
                for (localIdx, vi) in [i0, i1, i2].enumerated() {
                    var useKotlinUV = false
                    if hasTexCoords && vi * 2 + 1 < data.texCoords.count {
                        let u = data.texCoords[vi * 2]
                        let v = data.texCoords[vi * 2 + 1]
                        if u != 0 || v != 0 {
                            brightTexCoords.append([u, v])
                            useKotlinUV = true
                        }
                    }
                    if !useKotlinUV {
                        let pos = positions[localIdx]
                        let texU: Float
                        let texV: Float

                        // Normal yo'nalishiga qarab UV hisoblash
                        if absNz > absNx && absNz > absNy {
                            // Z-facing (old/orqa) - X va Y dan UV
                            if isTriangleHorizontal {
                                texU = (pos.y - minY) / textureSizeMeters
                                texV = (pos.x - minX) / textureSizeMeters
                            } else {
                                texU = (pos.x - minX) / textureSizeMeters
                                texV = (pos.y - minY) / textureSizeMeters
                            }
                        } else if absNx > absNy && absNx > absNz {
                            // X-facing (chap/o'ng yon) - Y va Z dan UV
                            texU = (pos.z - minZ) / textureSizeMeters
                            texV = (pos.y - minY) / textureSizeMeters
                        } else {
                            // Y-facing (tepa/past) - X va Z dan UV
                            texU = (pos.x - minX) / textureSizeMeters
                            texV = (pos.z - minZ) / textureSizeMeters
                        }
                        brightTexCoords.append([texU, texV])
                    }
                }
            }
        }

        var entities: [ModelEntity] = []

        // 1. Qora rezina qismi
        if darkPositions.count >= 3 {
            var darkDescriptor = MeshDescriptor()
            darkDescriptor.positions = MeshBuffer(darkPositions)
            darkDescriptor.normals = MeshBuffer(darkNormals)
            darkDescriptor.primitives = .triangles((0..<UInt32(darkPositions.count)).map { $0 })

            if let darkMesh = try? MeshResource.generate(from: [darkDescriptor]) {
                var darkMaterial = PhysicallyBasedMaterial()
                darkMaterial.baseColor = .init(tint: UIColor(red: 0.12, green: 0.12, blue: 0.12, alpha: 1.0))
                darkMaterial.metallic = .init(floatLiteral: 0.0)
                darkMaterial.roughness = .init(floatLiteral: 0.9)
                darkMaterial.faceCulling = .none
                entities.append(ModelEntity(mesh: darkMesh, materials: [darkMaterial]))
            }
        }

        // 2. Yorug' qism - barcha yuzalarga textura
        if brightPositions.count >= 3 {
            var brightDescriptor = MeshDescriptor()
            brightDescriptor.positions = MeshBuffer(brightPositions)
            brightDescriptor.normals = MeshBuffer(brightNormals)
            if brightTexCoords.count == brightPositions.count {
                brightDescriptor.textureCoordinates = MeshBuffer(brightTexCoords)
            }
            brightDescriptor.primitives = .triangles((0..<UInt32(brightPositions.count)).map { $0 })

            if let brightMesh = try? MeshResource.generate(from: [brightDescriptor]) {
                let brightMaterial: Material
                if let texture = profileTexture {
                    var mat = PhysicallyBasedMaterial()
                    let samplerDesc = MTLSamplerDescriptor()
                    samplerDesc.sAddressMode = .repeat
                    samplerDesc.tAddressMode = .repeat
                    samplerDesc.minFilter = .linear
                    samplerDesc.magFilter = .linear
                    samplerDesc.mipFilter = .linear
                    mat.baseColor = .init(texture: .init(texture, sampler: .init(samplerDesc)))
                    mat.roughness = .init(floatLiteral: 0.35)
                    mat.metallic = .init(floatLiteral: 0.0)
                    mat.specular = .init(floatLiteral: 0.5)
                    mat.faceCulling = .none
                    brightMaterial = mat
                } else {
                    var mat = PhysicallyBasedMaterial()
                    mat.baseColor = .init(tint: UIColor(red: CGFloat(profileR), green: CGFloat(profileG), blue: CGFloat(profileB), alpha: 1.0))
                    mat.metallic = .init(floatLiteral: 0.0)
                    mat.roughness = .init(floatLiteral: 0.35)
                    mat.faceCulling = .none
                    brightMaterial = mat
                }
                entities.append(ModelEntity(mesh: brightMesh, materials: [brightMaterial]))
            }
        }

        return entities
    }

    private func createPBRMaterial(for meshName: String, colors: [Float]) -> Material {
        let nameLower = meshName.lowercased()

        var r: Float = 0.96, g: Float = 0.96, b: Float = 0.96, a: Float = 1.0
        if colors.count >= 4 {
            r = min(1.0, max(0.0, colors[0]))
            g = min(1.0, max(0.0, colors[1]))
            b = min(1.0, max(0.0, colors[2]))
            a = min(1.0, max(0.0, colors[3]))
        }

        // Panel - opaque, profil rangida (teksturasiz, faqat rang)
        if nameLower.contains("panel") {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 1.0)  // Mat plastik
            material.faceCulling = .none
            return material
        }
        // Lambri - opaque, profil rangida (teksturasiz, faqat rang)
        else if nameLower.contains("lambri") {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 1.0)  // Mat plastik
            material.faceCulling = .none
            return material
        }
        // Glass - transparent (AR uchun juda shaffof - shotlanka ko'rinishi uchun)
        else if nameLower.contains("glass") || nameLower.contains("oyna") {
            var glassR: Float = 0.9, glassG: Float = 0.95, glassB: Float = 1.0, glassA: Float = 0.08
            if colors.count >= 4 {
                glassR = min(1.0, max(0.0, colors[0]))
                glassG = min(1.0, max(0.0, colors[1]))
                glassB = min(1.0, max(0.0, colors[2]))
            }
            // AR uchun juda shaffof oyna (8%) - shotlanka yaxshi ko'rinadi
            var material = UnlitMaterial()
            material.color = .init(tint: UIColor(red: CGFloat(glassR), green: CGFloat(glassG), blue: CGFloat(glassB), alpha: 1.0))
            material.blending = .transparent(opacity: .init(floatLiteral: glassA))
            return material
        }
        // Rubber/Seal
        else if nameLower.contains("rubber") || nameLower.contains("seal") {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: 0.12, green: 0.12, blue: 0.12, alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 0.9)
            material.faceCulling = .none
            return material
        }
        // Chita/Spacer - metallic
        else if nameLower.contains("chita") || nameLower.contains("spacer") {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: 0.72, green: 0.72, blue: 0.75, alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.7)
            material.roughness = .init(floatLiteral: 0.2)
            material.faceCulling = .none
            return material
        }
        // Shotlanka/Pattern - opaque, gold texture or profile color
        else if nameLower.contains("shotlanka") || nameLower.contains("pattern") {
            if let texture = shotlankaTexture {
                // Gold texture - yorug'roq ko'rinish uchun kamroq metallik
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(texture: .init(texture))
                material.metallic = .init(floatLiteral: 0.3)  // Kamroq metallik
                material.roughness = .init(floatLiteral: 0.4)
                material.specular = .init(floatLiteral: 0.6)
                material.faceCulling = .none
                return material
            } else {
                // Profile color - yorug'roq ko'rinish
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
                material.metallic = .init(floatLiteral: 0.0)
                material.roughness = .init(floatLiteral: 0.5)  // Kamroq mat
                material.specular = .init(floatLiteral: 0.4)   // Yorug'lik uchun
                material.faceCulling = .none
                return material
            }
        }
        // Handle/Ruchka - PROFIL RANGIDA (plastik, mat)
        else if nameLower.contains("handle") || nameLower.contains("ruchka") {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: CGFloat(profileColor.r), green: CGFloat(profileColor.g), blue: CGFloat(profileColor.b), alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 0.8)
            material.faceCulling = .none
            return material
        }
        // Hinge/Petlya - PROFIL RANGIDA (plastik, mat)
        else if nameLower.contains("hinge") || nameLower.contains("petlya") {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: CGFloat(profileColor.r), green: CGFloat(profileColor.g), blue: CGFloat(profileColor.b), alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 0.8)
            material.faceCulling = .none
            return material
        }
        // Profile with texture
        else if nameLower.contains("profile") || nameLower.contains("profil") ||
                nameLower.contains("frame") || nameLower.contains("rama") ||
                nameLower.contains("impost") {
            if let texture = profileTexture {
                // UnlitMaterial for brighter textures
                var material = UnlitMaterial()
                material.color = .init(texture: .init(texture))
                // faceCulling faqat iOS 18+ da mavjud, UnlitMaterial uchun ishlatmaymiz
                return material
            } else {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
                material.metallic = .init(floatLiteral: 0.0)
                material.roughness = .init(floatLiteral: 1.0)
                material.faceCulling = .none
                return material
            }
        }
        // Default - PVC plastic
        else {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 1.0)
            material.faceCulling = .none
            return material
        }
    }

    // MARK: - Animations

    private var originalPivotPositions: [Int: SIMD3<Float>] = [:]

    @objc public func toggleOpenState(_ openNumber: NSNumber) {
        let open = openNumber.boolValue
        guard !isAnimating else { return }

        isOpen = open
        isAnimating = true

        let leverIndices = Array(openPartEntities.keys.filter { $0 >= 1000 }.sorted())
        let frameIndices = Array(openPartEntities.keys.filter { $0 < 1000 }.sorted())

        let leverDuration: Double = 0.4
        let frameDuration: Double = 0.6

        if open {
            // OCHISH: avval lever, keyin frame
            animateOpenPivots(indices: leverIndices, open: true, duration: leverDuration)

            DispatchQueue.main.asyncAfter(deadline: .now() + leverDuration + 0.1) {
                self.animateOpenPivots(indices: frameIndices, open: true, duration: frameDuration)

                DispatchQueue.main.asyncAfter(deadline: .now() + frameDuration + 0.1) {
                    self.isAnimating = false
                }
            }
        } else {
            // YOPISH: avval frame, keyin lever
            animateOpenPivots(indices: frameIndices, open: false, duration: frameDuration)

            DispatchQueue.main.asyncAfter(deadline: .now() + frameDuration + 0.1) {
                self.animateOpenPivots(indices: leverIndices, open: false, duration: leverDuration)

                DispatchQueue.main.asyncAfter(deadline: .now() + leverDuration + 0.1) {
                    self.isAnimating = false
                }
            }
        }
    }

    private func animateOpenPivots(indices: [Int], open: Bool, duration: Double) {
        for index in indices {
            guard let pivotEntity = openPartEntities[index],
                  let data = openPartData[index] else { continue }

            let maxAngle = data.maxAngle * .pi / 180
            let angle: Float = open ? maxAngle : 0
            let axis = simd_normalize(data.axis)

            var transform = pivotEntity.transform
            transform.rotation = simd_quatf(angle: angle, axis: axis)

            pivotEntity.move(to: transform, relativeTo: pivotEntity.parent, duration: duration)
        }
    }

    @objc public func toggleTiltState(_ tiltNumber: NSNumber) {
        let tilt = tiltNumber.boolValue
        guard !isAnimating else { return }

        isTilted = tilt
        isAnimating = true

        let leverIndices = Array(openPartEntities.keys.filter { $0 >= 1000 }.sorted())
        let frameIndices = Array(openPartEntities.keys.filter { $0 < 1000 }.sorted())

        let leverDuration: Double = 0.4
        let frameDuration: Double = 0.6

        if tilt {
            animateTiltPivots(indices: leverIndices, tilt: true, duration: leverDuration)

            DispatchQueue.main.asyncAfter(deadline: .now() + leverDuration + 0.1) {
                self.animateTiltPivots(indices: frameIndices, tilt: true, duration: frameDuration)

                DispatchQueue.main.asyncAfter(deadline: .now() + frameDuration + 0.1) {
                    self.isAnimating = false
                }
            }
        } else {
            animateTiltPivots(indices: frameIndices, tilt: false, duration: frameDuration)

            DispatchQueue.main.asyncAfter(deadline: .now() + frameDuration + 0.1) {
                self.animateTiltPivots(indices: leverIndices, tilt: false, duration: leverDuration)

                DispatchQueue.main.asyncAfter(deadline: .now() + leverDuration + 0.1) {
                    self.isAnimating = false
                }
            }
        }
    }

    private func animateTiltPivots(indices: [Int], tilt: Bool, duration: Double) {
        let scale: Float = 0.001
        let centerX = modelWidth * scale / 2
        let centerY = modelHeight * scale / 2
        let centerZ = modelDepth * scale / 2
        let center: SIMD3<Float> = [centerX, centerY, centerZ]

        for index in indices {
            guard let pivotEntity = openPartEntities[index],
                  let data = openPartData[index] else { continue }

            let tiltMaxAngleDeg = data.tiltMaxAngle
            if tiltMaxAngleDeg == 0 { continue }

            let tiltAngle: Float = tilt ? tiltMaxAngleDeg * .pi / 180 : 0
            let tiltAxis = simd_normalize(data.tiltAxis)

            let tiltPivotWorld = data.tiltPivot * scale - center

            var tiltPivotLocal = tiltPivotWorld
            if index >= 1000 {
                let frameIndex = index >= 2000 ? index - 2000 : index - 1000
                if let framePivot = openPartEntities[frameIndex] {
                    tiltPivotLocal = tiltPivotWorld - framePivot.position
                }
            }

            if originalPivotPositions[index] == nil {
                originalPivotPositions[index] = pivotEntity.position
            }

            let openPivotLocal = originalPivotPositions[index] ?? pivotEntity.position

            var transform = pivotEntity.transform

            if tilt {
                let offset = openPivotLocal - tiltPivotLocal
                let rotation = simd_quatf(angle: tiltAngle, axis: tiltAxis)
                let rotatedOffset = rotation.act(offset)
                let newPosition = tiltPivotLocal + rotatedOffset

                transform.rotation = rotation
                transform.translation = newPosition
            } else {
                transform.rotation = simd_quatf(angle: 0, axis: [0, 1, 0])
                transform.translation = openPivotLocal
            }

            pivotEntity.move(to: transform, relativeTo: pivotEntity.parent, duration: duration)
        }
    }
}

// MARK: - Data structures

private struct PendingMeshAR {
    let name: String
    let positions: [Float]
    let normals: [Float]
    let colors: [Float]
    let texCoords: [Float]  // UV koordinatalari
    let openPartIndex: Int
    let pivot: SIMD3<Float>
    let axis: SIMD3<Float>
    let maxAngle: Float
    let tiltPivot: SIMD3<Float>
    let tiltAxis: SIMD3<Float>
    let tiltMaxAngle: Float
    let tiltPhaseStart: Float
    let tiltPhaseEnd: Float
    let phaseStart: Float
    let phaseEnd: Float
}

// MARK: - AR ViewController

@objc(WindowARViewController)
public class WindowARViewController: UIViewController {

    private var arContentView: RealityKitWindowARView!

    // UI Elements
    private var closeButton: UIButton!
    private var statusLabel: UILabel!
    private var statusContainer: UIView!
    private var controlsStack: UIStackView!
    private var openButton: UIButton!
    private var tiltButton: UIButton!
    private var resetButton: UIButton!

    // State
    private var isOpen = false
    private var isTilted = false
    private var isPlaced = false

    // Callbacks
    private var onDismiss: (() -> Void)?

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        setupARView()
        setupUI()
    }

    private func setupARView() {
        arContentView = RealityKitWindowARView(frame: view.bounds)
        arContentView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(arContentView)

        // Status callback
        arContentView.setOnStatusChange { [weak self] status in
            self?.statusLabel.text = status
        }

        // Plane detected callback
        arContentView.setOnPlaneDetected { [weak self] in
            // Plane topildi
        }

        // Model placed callback
        arContentView.setOnModelPlaced { [weak self] in
            self?.isPlaced = true
            self?.updateControlsVisibility()
        }

        // 30 sek ichida yuza topilmasa dialog
        arContentView.setOnPlaneNotDetected { [weak self] in
            self?.showPlaneNotDetectedAlert()
        }
    }

    private func showPlaneNotDetectedAlert() {
        print("🚨 showPlaneNotDetectedAlert() called!")

        let alert = UIAlertController(
            title: "⚠️ Yuzalar topilmadi",
            message: "Afsuski devor yuzalari aniqlanmadi.\nModelni kamera oldiga joylashtirishni xohlaysizmi?",
            preferredStyle: .alert
        )

        // Davom etish - qidrishni davom ettirish
        alert.addAction(UIAlertAction(title: "Davom etish", style: .default) { [weak self] _ in
            self?.arContentView.continueSearching()
            self?.statusLabel.text = "Kamerani devorga qarating..."
        })

        // Joylash - kamera oldiga joylashtirish
        alert.addAction(UIAlertAction(title: "Joylash", style: .default) { [weak self] _ in
            self?.arContentView.placeModelManually()
            self?.isPlaced = true
            self?.updateControlsVisibility()
            self?.statusLabel.text = "Model joylashtirildi"
        })

        present(alert, animated: true)
    }

    private func setupUI() {
        // Close button
        closeButton = UIButton(type: .system)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.setTitle("✕", for: .normal)
        closeButton.setTitleColor(.white, for: .normal)
        closeButton.titleLabel?.font = .systemFont(ofSize: 18, weight: .bold)
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        closeButton.layer.cornerRadius = 18
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        view.addSubview(closeButton)

        // Title label
        let titleLabel = UILabel()
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.text = "AR Koʻrinish"
        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 18, weight: .bold)
        view.addSubview(titleLabel)

        // Status container
        statusContainer = UIView()
        statusContainer.translatesAutoresizingMaskIntoConstraints = false
        statusContainer.backgroundColor = UIColor(white: 0.2, alpha: 0.8)
        statusContainer.layer.cornerRadius = 20
        view.addSubview(statusContainer)

        statusLabel = UILabel()
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.text = "Devorni qidiring..."
        statusLabel.textColor = UIColor(red: 0.3, green: 0.8, blue: 0.4, alpha: 1.0)
        statusLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        statusLabel.textAlignment = .center
        statusContainer.addSubview(statusLabel)

        // Controls stack
        controlsStack = UIStackView()
        controlsStack.translatesAutoresizingMaskIntoConstraints = false
        controlsStack.axis = .vertical
        controlsStack.spacing = 12
        controlsStack.isHidden = true
        view.addSubview(controlsStack)

        // Open button
        openButton = createControlButton(title: "⊞", action: #selector(openTapped))
        controlsStack.addArrangedSubview(openButton)

        // Tilt button
        tiltButton = createControlButton(title: "⊟", action: #selector(tiltTapped))
        controlsStack.addArrangedSubview(tiltButton)

        // Reset button
        resetButton = createControlButton(title: "↺", action: #selector(resetTapped))
        controlsStack.addArrangedSubview(resetButton)

        // Constraints
        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            closeButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            closeButton.widthAnchor.constraint(equalToConstant: 36),
            closeButton.heightAnchor.constraint(equalToConstant: 36),

            titleLabel.centerYAnchor.constraint(equalTo: closeButton.centerYAnchor),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),

            statusContainer.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),
            statusContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            statusContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            statusContainer.heightAnchor.constraint(equalToConstant: 56),

            statusLabel.centerXAnchor.constraint(equalTo: statusContainer.centerXAnchor),
            statusLabel.centerYAnchor.constraint(equalTo: statusContainer.centerYAnchor),

            controlsStack.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            controlsStack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16)
        ])
    }

    private func createControlButton(title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.setTitle(title, for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 24)
        button.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        button.layer.cornerRadius = 24
        button.addTarget(self, action: action, for: .touchUpInside)

        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 48),
            button.heightAnchor.constraint(equalToConstant: 48)
        ])

        return button
    }

    private func updateControlsVisibility() {
        controlsStack.isHidden = !isPlaced

        // Check which buttons should be visible
        let hasStandard = arContentView.getHasStandardOpening().boolValue
        let hasTilt = arContentView.getHasTiltOpening().boolValue

        openButton.isHidden = !hasStandard
        tiltButton.isHidden = !hasTilt
    }

    @objc private func closeTapped() {
        arContentView.pauseARSession()
        dismiss(animated: true) { [weak self] in
            self?.onDismiss?()
        }
    }

    @objc private func openTapped() {
        if isTilted {
            isTilted = false
            arContentView.toggleTiltState(NSNumber(value: false))
        }
        isOpen = !isOpen
        arContentView.toggleOpenState(NSNumber(value: isOpen))
        updateButtonStates()
    }

    @objc private func tiltTapped() {
        if isOpen {
            isOpen = false
            arContentView.toggleOpenState(NSNumber(value: false))
        }
        isTilted = !isTilted
        arContentView.toggleTiltState(NSNumber(value: isTilted))
        updateButtonStates()
    }

    @objc private func resetTapped() {
        isOpen = false
        isTilted = false
        isPlaced = false
        arContentView.resetPlacement()
        updateControlsVisibility()
        updateButtonStates()
    }

    private func updateButtonStates() {
        openButton.backgroundColor = isOpen
            ? UIColor(red: 0.3, green: 0.8, blue: 0.4, alpha: 0.3)
            : UIColor.black.withAlphaComponent(0.5)
        openButton.layer.borderWidth = isOpen ? 2 : 0
        openButton.layer.borderColor = UIColor(red: 0.3, green: 0.8, blue: 0.4, alpha: 1.0).cgColor

        tiltButton.backgroundColor = isTilted
            ? UIColor(red: 0.3, green: 0.8, blue: 0.4, alpha: 0.3)
            : UIColor.black.withAlphaComponent(0.5)
        tiltButton.layer.borderWidth = isTilted ? 2 : 0
        tiltButton.layer.borderColor = UIColor(red: 0.3, green: 0.8, blue: 0.4, alpha: 1.0).cgColor
    }

    public override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        arContentView.startARSession()

        // App lifecycle observers - black screen fix
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillResignActive),
            name: UIApplication.willResignActiveNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        arContentView.pauseARSession()

        // Remove observers
        NotificationCenter.default.removeObserver(self, name: UIApplication.willResignActiveNotification, object: nil)
        NotificationCenter.default.removeObserver(self, name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    @objc private func appWillResignActive() {
        print("📱 App will resign active - pausing AR")
        arContentView.pauseARSession()
    }

    @objc private func appDidBecomeActive() {
        print("📱 App did become active - resuming AR")
        // Kichik kechikish - kamera tayyor bo'lishi uchun
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.arContentView.resumeARSession()
        }
    }

    @objc public func getARView() -> RealityKitWindowARView {
        return arContentView
    }

    @objc public func setOnDismiss(_ callback: @escaping () -> Void) {
        self.onDismiss = callback
    }

    @objc public func dismissAR() {
        dismiss(animated: true) { [weak self] in
            self?.onDismiss?()
        }
    }
}

// MARK: - Factory for Kotlin

@objc(RealityKitARViewFactory)
public class RealityKitARViewFactory: NSObject {

    @objc public static let shared = RealityKitARViewFactory()

    private var currentViewController: WindowARViewController?
    private var currentView: RealityKitWindowARView?

    @objc public func createARView() -> RealityKitWindowARView {
        print("🎬 RealityKitARViewFactory.createARView() called")

        // ⚠️ MUHIM: Avval 3D view ni tozalash - kamera konfliktini oldini olish
        RealityKitViewFactory.shared.cleanup()

        // Avvalgi AR view ni ham tozalash
        cleanup()

        // Thread.sleep olib tashlandi - tezroq ochilishi uchun

        let view = RealityKitWindowARView(frame: .zero)
        currentView = view
        print("✅ RealityKitWindowARView created")
        return view
    }

    @objc public func getCurrentView() -> RealityKitWindowARView? {
        return currentView ?? currentViewController?.getARView()
    }

    /// AR view ni tozalash
    @objc public func cleanup() {
        print("🧹 RealityKitARViewFactory cleanup called")
        if let view = currentView {
            view.cleanup()  // View ning o'z cleanup methodini chaqirish
            view.removeFromSuperview()
            currentView = nil
        }
        if let vc = currentViewController {
            vc.dismiss(animated: false)
            currentViewController = nil
        }
    }

    /// AR ViewController ni modal ochish
    @objc public func presentARViewController(from presenter: UIViewController, completion: @escaping () -> Void) {
        // Avval 3D view ni tozalash
        RealityKitViewFactory.shared.cleanup()

        let arVC = WindowARViewController()
        arVC.modalPresentationStyle = .fullScreen
        arVC.setOnDismiss(completion)
        currentViewController = arVC
        presenter.present(arVC, animated: true)
    }

    /// Hozirgi AR ViewController
    @objc public func getCurrentViewController() -> WindowARViewController? {
        return currentViewController
    }

    /// AR ni yopish
    @objc public func dismissARViewController() {
        currentViewController?.dismissAR()
        currentViewController = nil
    }

    /// AR yopilganda 3D view ni qayta tiklash
    @objc public func refresh3DView() {
        print("🔄 RealityKitARViewFactory refresh3DView called")
        // Avval AR ni tozalash
        cleanup()
        // Keyin 3D ni refresh qilish
        RealityKitViewFactory.shared.refresh()
    }

    @objc public static func isSupported() -> Bool {
        return ARWorldTrackingConfiguration.isSupported
    }
}
