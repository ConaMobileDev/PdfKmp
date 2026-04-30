import UIKit
import RealityKit
import Combine
import Metal

/// RealityKit asosidagi 3D oyna ko'rinishi
/// Kotlin dan Factory orqali chaqiriladi
@objc(RealityKitWindow3DView)
public class RealityKitWindow3DView: UIView {

    private var arView: ARView!
    private var rootAnchor: AnchorEntity!
    private var modelEntity: Entity?
    private var logoImageView: UIImageView?  // Logo uchun UIImageView (3D sahnadan tashqarida)

    private var rotationX: Float = 0
    private var rotationY: Float = -30
    private var zoom: Float = 1.0
    private var modelWidth: Float = 1000
    private var modelHeight: Float = 1000
    private var modelDepth: Float = 100

    private var openPartEntities: [Int: Entity] = [:]
    private var openPartData: [Int: (axis: SIMD3<Float>, maxAngle: Float, tiltPivot: SIMD3<Float>, tiltAxis: SIMD3<Float>, tiltMaxAngle: Float, tiltPhaseStart: Float, tiltPhaseEnd: Float)] = [:]
    private var isOpen: Bool = false
    private var isAnimating: Bool = false

    private var pendingMeshes: [PendingMesh] = []

    @objc public override init(frame: CGRect) {
        super.init(frame: frame)
        setupARView()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupARView()
    }

    deinit {
        print("🗑️ RealityKitWindow3DView deinit")
        cleanup()
        // Qolgan data larni ham tozalash
        modelEntity = nil
        openPartEntities.removeAll()
        openPartData.removeAll()
        pendingMeshes.removeAll()
        logoImageView?.removeFromSuperview()
        logoImageView = nil
    }

    /// View tozalash - AR ochishdan oldin chaqiriladi
    @objc public func cleanup() {
        print("🧹 RealityKitWindow3DView cleanup - pendingMeshes: \(pendingMeshes.count), size: \(modelWidth)x\(modelHeight)x\(modelDepth)")
        // Anchors ni o'chirish
        arView?.scene.anchors.forEach { anchor in
            arView?.scene.removeAnchor(anchor)
        }
        // ARView ni o'chirish
        arView?.removeFromSuperview()
        arView = nil

        // Model va anchor larni SAQLAYMIZ - refresh da qayta ishlatish uchun
        // modelEntity, openPartEntities, pendingMeshes saqlanadi
        print("🧹 Cleanup done - pendingMeshes preserved: \(pendingMeshes.count)")
    }

    /// ARView mavjudligini tekshirish
    @objc public var needsRefresh: Bool {
        return arView == nil
    }

    /// Assembly animatsiya holatini tekshirish (Kotlin dan chaqiriladi)
    @objc public var assemblyActive: Bool {
        return isAssemblyActive
    }

    /// Presentation animatsiya holatini tekshirish (Kotlin dan chaqiriladi)
    @objc public var presentationActive: Bool {
        return isPresentationActive
    }

    /// AR yopilgandan keyin 3D view ni qayta tiklash
    @objc public func refresh() {
        print("🔄 RealityKitWindow3DView refresh - pendingMeshes: \(pendingMeshes.count), bounds: \(bounds)")

        // Agar arView allaqachon mavjud bo'lsa, hech narsa qilmaymiz
        if arView != nil {
            print("🔄 ARView already exists, skipping refresh")
            return
        }

        print("🔄 Creating new ARView...")

        // ARView ni qayta yaratish
        arView = ARView(frame: bounds, cameraMode: .nonAR, automaticallyConfigureSession: false)
        arView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        arView.gestureRecognizers?.forEach { arView.removeGestureRecognizer($0) }

        // ARView ni qo'shish va fon rangini sozlash
        if let logo = logoImageView {
            // Logo orqada - ARView shaffof bo'lishi kerak
            insertSubview(arView, aboveSubview: logo)
            arView.backgroundColor = .clear
            arView.environment.background = .color(.clear)
            print("🔄 ARView inserted above logo with clear background")
        } else {
            // Logo yo'q - oddiy fon
            addSubview(arView)
            let bgColor = backgroundColor ?? UIColor(red: 0.31, green: 0.31, blue: 0.31, alpha: 1.0)
            arView.backgroundColor = bgColor
            arView.environment.background = .color(.init(cgColor: bgColor.cgColor))
            print("🔄 ARView added as subview with solid background")
        }
 
        // Root anchor qayta yaratish
        rootAnchor = AnchorEntity(world: .zero)
        arView.scene.addAnchor(rootAnchor)
        print("🔄 Root anchor added")

        // Lighting va camera qayta sozlash
        setupLighting()
        setupGestures()
        setupInitialCamera()
        print("🔄 Lighting, gestures, camera setup done")

        // Model ni qayta qurish (agar pendingMeshes mavjud bo'lsa)
        if !pendingMeshes.isEmpty {
            print("🔄 Rebuilding model with \(pendingMeshes.count) meshes, size: \(modelWidth)x\(modelHeight)x\(modelDepth)")
            rebuildModel()
        } else {
            print("⚠️ No pendingMeshes to rebuild!")
        }

        print("✅ RealityKitWindow3DView refreshed, arView.frame: \(arView.frame)")
    }

    /// Model ni qayta qurish
    private func rebuildModel() {
        // buildModel() public method - uni chaqiramiz
        buildModel()
        print("✅ Model rebuilt")
    }

    private var logoEntity: ModelEntity?
    private var logoAnchor: AnchorEntity?

    // Shotlanka gold texture
    private var shotlankaTexture: TextureResource?

    // Profil/shtapik teksturasi (yog'och, metall va h.k.)
    private var profileTexture: TextureResource?

    // Profil rangi (handle va petlya uchun)
    private var profileColor: (r: Float, g: Float, b: Float) = (0.96, 0.96, 0.96)

    private func setupARView() {
        arView = ARView(frame: bounds, cameraMode: .nonAR, automaticallyConfigureSession: false)
        arView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        // Default dark background
        let defaultBgColor = UIColor(red: 0.31, green: 0.31, blue: 0.31, alpha: 1.0)
        backgroundColor = defaultBgColor
        arView.backgroundColor = defaultBgColor
        arView.environment.background = .color(.init(red: 0.31, green: 0.31, blue: 0.31, alpha: 1.0))

        // Disable ARView's built-in gestures
        arView.gestureRecognizers?.forEach { arView.removeGestureRecognizer($0) }

        addSubview(arView)

        rootAnchor = AnchorEntity(world: .zero)
        arView.scene.addAnchor(rootAnchor)

        setupLighting()
        setupGestures()
        setupInitialCamera()
    }

    /// Orqa fon rangini o'rnatish (isDark = true: qorong'u, false: yorug')
    @objc public func setBackgroundColorIsDark(_ isDarkNumber: NSNumber) {
        let isDark = isDarkNumber.boolValue
        isDarkMode = isDark
        let bgColor: UIColor
        if isDark {
            // Dark theme: #4F4F4F
            bgColor = UIColor(red: 0.31, green: 0.31, blue: 0.31, alpha: 1.0)
            // Dark mode - tekis yorug'lik
            cameraLight?.light.intensity = 400
            ambientLight?.light.intensity = 400
        } else {
            // Light theme: #F9FAFC
            bgColor = UIColor(red: 0.976, green: 0.98, blue: 0.988, alpha: 1.0)
            // Light mode - kamroq yorug'lik (oq model oq fonda yaxshi ko'rinishi uchun)
            cameraLight?.light.intensity = 250
            ambientLight?.light.intensity = 250
        }
        // Background rangini o'rnatish
        backgroundColor = bgColor

        // Agar logo mavjud bo'lsa, ARView foni shaffof bo'lishi kerak
        if logoImageView != nil {
            arView.backgroundColor = .clear
            arView.environment.background = .color(.clear)
        } else {
            arView.backgroundColor = bgColor
            arView.environment.background = .color(.init(cgColor: bgColor.cgColor))
        }
    }

    /// Logo rasmini UIImageView sifatida ARView ORTIGA qo'yish
    /// Model ortida ko'rinadi, oyna shaffofligi biroz ta'sir qilishi mumkin
    @objc public func setLogoImage(_ image: UIImage?) {
        // Eski logo entity ni o'chirish (agar mavjud bo'lsa)
        logoEntity?.removeFromParent()
        logoAnchor?.removeFromParent()
        logoEntity = nil
        logoAnchor = nil

        // Eski UIImageView ni o'chirish
        logoImageView?.removeFromSuperview()
        logoImageView = nil

        guard let image = image else { return }

        // UIImageView yaratish (ARView ORTIDA)
        let imageView = UIImageView(image: image)
        imageView.contentMode = .scaleAspectFit
        imageView.alpha = 0.15  // Logo shaffofligi
        imageView.translatesAutoresizingMaskIntoConstraints = false

        // UIImageView ni ARView ORTIGA qo'shish
        insertSubview(imageView, belowSubview: arView)

        // Logo o'lchami va joylashuvi (markazda)
        NSLayoutConstraint.activate([
            imageView.centerXAnchor.constraint(equalTo: centerXAnchor),
            imageView.centerYAnchor.constraint(equalTo: centerYAnchor),
            imageView.widthAnchor.constraint(equalTo: widthAnchor, multiplier: 0.35),
            imageView.heightAnchor.constraint(equalTo: heightAnchor, multiplier: 0.35)
        ])

        logoImageView = imageView

        // ARView fonini shaffof qilish (logo ko'rinishi uchun)
        arView.backgroundColor = .clear
        arView.environment.background = .color(.clear)

        print("=== Logo UIImageView (ORTIDA) qo'shildi ===")
    }

    /// Shotlanka gold teksturasini o'rnatish
    @objc public func setShotlankaTexture(_ image: UIImage?) {
        guard let image = image, let cgImage = image.cgImage else {
            shotlankaTexture = nil
            return
        }

        // Tekstura yaratish
        do {
            shotlankaTexture = try TextureResource.generate(from: cgImage, options: .init(semantic: .color))
            print("=== Shotlanka texture yuklandi ===")
        } catch {
            print("=== Shotlanka texture xatolik: \(error) ===")
            shotlankaTexture = nil
        }
    }

    /// Profil va shtapiklar uchun tekstura o'rnatish (yog'och, metall ranglar)
    @objc public func setProfileTexture(_ image: UIImage?) {
        guard let image = image, let cgImage = image.cgImage else {
            profileTexture = nil
            print("=== Profile texture o'chirildi ===")
            return
        }

        // Tekstura yaratish
        do {
            profileTexture = try TextureResource.generate(from: cgImage, options: .init(semantic: .color))
            print("=== Profile texture yuklandi ===")
        } catch {
            print("=== Profile texture xatolik: \(error) ===")
            profileTexture = nil
        }
    }

    private func setupInitialCamera() {
        let cameraEntity = PerspectiveCamera()
        cameraEntity.camera.fieldOfViewInDegrees = 45

        let cameraAnchor = AnchorEntity(world: [0, 0, 3])
        cameraAnchor.look(at: .zero, from: cameraAnchor.position, relativeTo: nil)
        cameraAnchor.addChild(cameraEntity)
        arView.scene.addAnchor(cameraAnchor)
    }

    private var cameraLightAnchor: AnchorEntity?
    private var cameraLight: DirectionalLight?
    private var ambientLight: DirectionalLight?
    private var isDarkMode: Bool = true

    private func setupLighting() {
        // Tekis yorug'lik - quyosh effektisiz
        arView.environment.lighting.intensityExponent = 1.0

        // Asosiy yorug'lik - faqat yuqoridan (to'qroq havorang)
        cameraLight = DirectionalLight()
        cameraLight?.light.color = UIColor(red: 0.75, green: 0.82, blue: 0.95, alpha: 1.0)  // To'qroq havorang
        cameraLight?.light.intensity = 400
        cameraLight?.shadow = nil

        cameraLightAnchor = AnchorEntity(world: [0, 5, 0])  // Yuqoridan
        cameraLightAnchor?.look(at: .zero, from: cameraLightAnchor!.position, relativeTo: nil)
        cameraLightAnchor?.addChild(cameraLight!)
        arView.scene.addAnchor(cameraLightAnchor!)
    }

    private func updateCameraLight() {
        // Yorug'likni kamera pozitsiyasiga moslashtirish
        guard let cameraLightAnchor = cameraLightAnchor else { return }

        // Kamera pozitsiyasini hisoblash (rotatsiya va zoom asosida)
        let distance: Float = 3.0 * zoom
        let radX = rotationX * .pi / 180
        let radY = rotationY * .pi / 180

        let x = distance * sin(radY) * cos(radX)
        let y = distance * sin(radX)
        let z = distance * cos(radY) * cos(radX)

        cameraLightAnchor.position = [x, y, z]
        cameraLightAnchor.look(at: .zero, from: cameraLightAnchor.position, relativeTo: nil)
    }

    private func setupGestures() {
        isUserInteractionEnabled = true
        arView.isUserInteractionEnabled = true

        let panGesture = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        panGesture.maximumNumberOfTouches = 1
        arView.addGestureRecognizer(panGesture)

        let pinchGesture = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
        arView.addGestureRecognizer(pinchGesture)

        print("=== Gestures setup complete ===")
    }

    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        let translation = gesture.translation(in: arView)
        rotationY += Float(translation.x) * 0.5

        // Model orqaga (180°) aylantirilganda X aylanish yo'nalishini tuzatish
        // cos(rotationY) < 0 bo'lganda (90°-270° orasida) X delta teskari
        let yRadians = rotationY * .pi / 180
        let xMultiplier: Float = cos(yRadians) < 0 ? -1 : 1
        rotationX += Float(translation.y) * 0.3 * xMultiplier

        gesture.setTranslation(.zero, in: arView)
        updateModelRotation()
    }

    @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
        zoom *= Float(gesture.scale)
        zoom = max(0.1, min(15.0, zoom))  // Ko'proq yaqinlashtirish imkoniyati
        gesture.scale = 1.0
        updateModelScale()
    }

    private func updateModelRotation() {
        guard let model = modelEntity else {
            print("=== updateModelRotation: modelEntity is nil! ===")
            return
        }

        // Rotate model around X and Y axes
        let rotX = simd_quatf(angle: rotationX * .pi / 180, axis: [1, 0, 0])
        let rotY = simd_quatf(angle: rotationY * .pi / 180, axis: [0, 1, 0])
        model.transform.rotation = rotY * rotX
        // Zoom ham yangilash
        model.transform.scale = SIMD3<Float>(repeating: zoom)
    }

    private func updateModelScale() {
        guard let model = modelEntity else { return }
        model.transform.scale = SIMD3<Float>(repeating: zoom)
    }

    // MARK: - Public API (Kotlin dan chaqiriladi)

    @objc public func setModelSizeWidth(_ width: Float, height: Float, depth: Float) {
        modelWidth = width
        modelHeight = height
        modelDepth = depth
        updateCameraForModelSize()
    }

    /// Kotlin dan NSDictionary orqali chaqirish uchun
    @objc public func setModelSizeWithDict(_ dict: NSDictionary) {
        modelWidth = (dict["width"] as? NSNumber)?.floatValue ?? 1000
        modelHeight = (dict["height"] as? NSNumber)?.floatValue ?? 1000
        modelDepth = (dict["depth"] as? NSNumber)?.floatValue ?? 100
        updateCameraForModelSize()
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
        // Asosiy animatsiya fazalari
        let phaseStart = (data["phaseStart"] as? NSNumber)?.floatValue ?? 0
        let phaseEnd = (data["phaseEnd"] as? NSNumber)?.floatValue ?? 1

        // Debug: mesh ma'lumotlarini chiqarish
        if openPartIndex >= 0 {
            print("=== addMeshWithData: \(name), openPartIndex=\(openPartIndex), pivot=(\(pivotX),\(pivotY),\(pivotZ)), axis=(\(axisX),\(axisY),\(axisZ)), maxAngle=\(maxAngle), tiltMaxAngle=\(tiltMaxAngle) ===")
        }

        let mesh = PendingMesh(
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

    @objc public func buildModel() {
        print("=== Building model: \(pendingMeshes.count) meshes ===")
        print("=== shotlankaTexture=\(shotlankaTexture != nil), profileTexture=\(profileTexture != nil) ===")

        // Barcha mesh nomlarini chiqarish (shotlanka uchun)
        let shotlankaMeshes = pendingMeshes.filter { $0.name.lowercased().contains("shotlanka") }
        print("=== SHOTLANKA meshes count: \(shotlankaMeshes.count) ===")
        for mesh in shotlankaMeshes {
            print("=== SHOTLANKA mesh: \(mesh.name), vertices=\(mesh.positions.count / 3) ===")
        }

        // Eski modelni o'chirish
        modelEntity?.removeFromParent()
        openPartEntities.removeAll()
        openPartData.removeAll()
        originalPivotPositions.removeAll()

        let newModel = Entity()
        let scale: Float = 0.001
        let centerX = modelWidth * scale / 2
        let centerY = modelHeight * scale / 2
        let centerZ = modelDepth * scale / 2
        let center: SIMD3<Float> = [centerX, centerY, centerZ]

        // Meshlarni turlarga ajratish
        // Frame: 0-999 (petlya openpart bundan mustasno), Front lever: 1000-1999, Back lever: 2000+
        // Petlya open_part: openPartIndex < 1000, lekin o'z pivoti bor - lever kabi ishlaydi
        let isPetlyaOpenPart: (PendingMesh) -> Bool = { $0.name.lowercased().contains("hinge_openpart") }
        let frameMeshes = pendingMeshes.filter { $0.openPartIndex >= 0 && $0.openPartIndex < 1000 && !isPetlyaOpenPart($0) }
        let petlyaMeshes = pendingMeshes.filter { $0.openPartIndex >= 0 && $0.openPartIndex < 1000 && isPetlyaOpenPart($0) }
        let leverMeshes = pendingMeshes.filter { $0.openPartIndex >= 1000 }  // 1000+ va 2000+ ham
        let staticMeshes = pendingMeshes.filter { $0.openPartIndex < 0 }

        print("=== Frame meshes: \(frameMeshes.count), Petlya meshes: \(petlyaMeshes.count), Lever meshes: \(leverMeshes.count), Static: \(staticMeshes.count) ===")

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
                print("=== PROFILE COLOR FOUND: (\(r), \(g), \(b)) from \(meshData.name) ===")
                break
            }
        }

        // 1. FRAME pivotlarni yaratish va meshlarni qo'shish
        for meshData in frameMeshes {
            var entities: [ModelEntity] = []
            let nameLower = meshData.name.lowercased()
            let isShtapik = nameLower.contains("shtapik")
            let isHandle = nameLower.contains("handle")
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

            // Debug: lambri va panel uchun log
            if isLambri || isPanel {
                print("=== LAMBRI/PANEL MESH: \(meshData.name), isLambri=\(isLambri), isPanel=\(isPanel), profileTexture=\(profileTexture != nil), needsProfileTexture=\(needsProfileTexture), entities=\(entities.count) ===")
            }

            // Debug: handle uchun alohida log
            if isHandle {
                print("=== HANDLE MESH: \(meshData.name), entities=\(entities.count), openPartIndex=\(meshData.openPartIndex) ===")
            }

            guard !entities.isEmpty else {
                if isHandle {
                    print("=== HANDLE MESH SKIPPED: entities is empty! ===")
                }
                continue
            }

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
                print("=== Created FRAME pivot \(meshData.openPartIndex) at \(pivot.position), tiltMaxAngle=\(meshData.tiltMaxAngle) ===")
            }

            for entity in entities {
                // MUHIM: Agar mavjud pivot ga qo'shilsa, pivot pozitsiyasini ishlatish kerak
                // Aks holda mesh o'z pivot qiymatidan foydalanadi
                if isNewPivot {
                    entity.position = -(meshData.pivot * scale - center)
                } else {
                    // Mavjud pivot ga qo'shilganda - pivot pozitsiyasini kompensatsiya qilish
                    entity.position = -pivot.position
                }
                pivot.addChild(entity)

                // Debug: handle qo'shildi
                if isHandle {
                    print("=== HANDLE added to pivot \(meshData.openPartIndex), entity.position=\(entity.position), isNewPivot=\(isNewPivot) ===")
                }
            }
        }

        // 2. PETLYA open_part - frame pivot ga to'g'ridan-to'g'ri qo'shiladi
        // Petlya frame bilan birga aylanishi uchun FRAME pivotini ishlatadi
        for meshData in petlyaMeshes {
            var entities: [ModelEntity] = []

            if let entity = createMeshEntity(from: meshData, scale: scale, center: center) {
                entities = [entity]
            }

            guard !entities.isEmpty else { continue }

            let frameIndex = meshData.openPartIndex

            // Frame pivot ga qo'shish (agar mavjud bo'lsa)
            if let framePivot = openPartEntities[frameIndex] {
                // MUHIM: Petlya uchun FRAME pivotini ishlatish (petlya pivoti emas)
                // Bu petlya frame bilan birga to'g'ri aylanishini ta'minlaydi
                for entity in entities {
                    // Frame pivot pozitsiyasini kompensatsiya qilish
                    entity.position = -framePivot.position
                    framePivot.addChild(entity)
                }
                print("=== PETLYA mesh \(meshData.name) -> frame pivot \(frameIndex), using frame pivot offset ===")
            } else {
                // Frame pivot yo'q - model ga qo'shish
                for entity in entities {
                    newModel.addChild(entity)
                }
                print("=== PETLYA mesh \(meshData.name) -> model (no frame pivot) ===")
            }
        }

        // 3. LEVER - frame pivot ICHIDA alohida pivot yaratish
        for meshData in leverMeshes {
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

            // Debug: lever lambri va panel uchun log
            if isLambri || isPanel {
                print("=== LEVER LAMBRI/PANEL: \(meshData.name), isLambri=\(isLambri), isPanel=\(isPanel), profileTexture=\(profileTexture != nil), needsProfileTexture=\(needsProfileTexture), entities=\(entities.count) ===")
            }

            guard !entities.isEmpty else { continue }

            // Frame index: 1000-1999 -> 0-999, 2000-2999 -> 0-999
            let frameIndex = meshData.openPartIndex >= 2000
                ? meshData.openPartIndex - 2000
                : meshData.openPartIndex - 1000
            let leverPivotWorldPos = meshData.pivot * scale - center

            // Lever pivot yaratish
            let leverPivot: Entity
            if let existingPivot = openPartEntities[meshData.openPartIndex] {
                leverPivot = existingPivot
            } else {
                leverPivot = Entity()

                // Frame pivot mavjud bo'lsa, uning ICHIGA qo'shamiz
                if let framePivot = openPartEntities[frameIndex] {
                    let framePivotWorldPos = framePivot.position
                    // Lever pivot pozitsiyasi frame pivot ga NISBATAN (local coordinates)
                    leverPivot.position = leverPivotWorldPos - framePivotWorldPos
                    framePivot.addChild(leverPivot)
                    print("=== LEVER pivot \(meshData.openPartIndex) -> CHILD of frame \(frameIndex), localPos=\(leverPivot.position) ===")
                } else {
                    // Frame pivot yo'q - to'g'ridan-to'g'ri model ga
                    leverPivot.position = leverPivotWorldPos
                    newModel.addChild(leverPivot)
                    print("=== LEVER pivot \(meshData.openPartIndex) -> model (no frame pivot) ===")
                }

                openPartEntities[meshData.openPartIndex] = leverPivot
                openPartData[meshData.openPartIndex] = (meshData.axis, meshData.maxAngle, meshData.tiltPivot, meshData.tiltAxis, meshData.tiltMaxAngle, meshData.tiltPhaseStart, meshData.tiltPhaseEnd)
            }

            // Entity ni lever pivot ga qo'shish
            // Frame mesh bilan bir xil formula ishlatiladi
            for entity in entities {
                entity.position = -(meshData.pivot * scale - center)
                leverPivot.addChild(entity)
            }
            print("=== Added lever mesh \(meshData.name) ===")
        }

        // 4. Statik meshlarni qo'shish
        for meshData in staticMeshes {
            let nameLower = meshData.name.lowercased()

            var entities: [ModelEntity] = []
            let isShtapik = nameLower.contains("shtapik")

            // DEBUG: Har bir mesh nomini va o'lchamlarini chiqarish
            let vertexCount = meshData.positions.count / 3
            var minX: Float = .infinity, maxX: Float = -.infinity
            var minY: Float = .infinity, maxY: Float = -.infinity
            var minZ: Float = .infinity, maxZ: Float = -.infinity
            for i in 0..<vertexCount {
                let x = meshData.positions[i * 3]
                let y = meshData.positions[i * 3 + 1]
                let z = meshData.positions[i * 3 + 2]
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                minZ = min(minZ, z); maxZ = max(maxZ, z)
            }
            let sizeX = maxX - minX
            let sizeY = maxY - minY
            let sizeZ = maxZ - minZ

            // Profile, lambri va panel meshlar - texturani faqat Z yuzalarga qo'yish
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

            // Debug: static lambri va panel uchun log
            if isLambri || isPanel {
                print("=== STATIC LAMBRI/PANEL: \(meshData.name), isLambri=\(isLambri), isPanel=\(isPanel), profileTexture=\(profileTexture != nil), needsProfileTexture=\(needsProfileTexture), entities=\(entities.count) ===")
            }

            for entity in entities {
                newModel.addChild(entity)
            }
        }

        modelEntity = newModel
        rootAnchor.addChild(newModel)
        // pendingMeshes.removeAll() - OLIB TASHLANDI: AR dan qaytganda refresh uchun kerak

        // Dastlabki rotationni qo'yish (Android bilan bir xil)
        updateModelRotation()

        print("=== Build complete. OpenPart entities: \(openPartEntities.keys.sorted()) ===")
        for (index, pivot) in openPartEntities {
            if let parent = pivot.parent {
                let isChildOfFrame = parent != newModel
                print("=== Pivot \(index): parent=\(isChildOfFrame ? "frame pivot" : "model"), pos=\(pivot.position) ===")
            }
        }

        // Update camera position based on model size
        updateCameraForModelSize()

        print("=== Model built successfully ===")
    }

    private func updateCameraForModelSize() {
        let maxDim = max(modelWidth, modelHeight) * 0.001
        // Android dagi kabi - 3.0 bazaviy masofa, model o'lchamiga moslashtirish
        // Android: cameraZ = 3f, iOS: o'sha nisbatni saqlaymiz
        let distance = max(maxDim * 2.0, 2.0)  // Kamida 2m, lekin model o'lchamiga qarab

        // Remove old camera
        arView.scene.anchors.filter { $0.children.contains(where: { $0 is PerspectiveCamera }) }.forEach {
            arView.scene.removeAnchor($0)
        }

        // Add new camera at appropriate distance
        let cameraEntity = PerspectiveCamera()
        cameraEntity.camera.fieldOfViewInDegrees = 45

        let cameraAnchor = AnchorEntity(world: [0, 0, distance])
        cameraAnchor.look(at: .zero, from: cameraAnchor.position, relativeTo: nil)
        cameraAnchor.addChild(cameraEntity)
        arView.scene.addAnchor(cameraAnchor)

        print("=== updateCameraForModelSize: modelSize=\(modelWidth)x\(modelHeight), maxDim=\(maxDim), distance=\(distance) ===")
    }

    private var isTilted: Bool = false

    @objc public func toggleTiltState(_ tiltNumber: NSNumber) {
        let tilt = tiltNumber.boolValue

        // Animatsiya davomida yangi animatsiya boshlash mumkin emas
        guard !isAnimating else {
            print("=== Animation already in progress, ignoring ===")
            return
        }

        print("=== toggleTiltState: tilt=\(tilt), entities=\(openPartEntities.keys.sorted()) ===")
        isTilted = tilt
        isAnimating = true

        // Otkid uchun X o'qi bo'yicha aylantirish (chapga yotqizish)
        let leverIndices = Array(openPartEntities.keys.filter { $0 >= 1000 }.sorted())
        let frameIndices = Array(openPartEntities.keys.filter { $0 < 1000 }.sorted())

        let leverDuration: Double = 0.4
        let frameDuration: Double = 0.6

        if tilt {
            // OTKID OCHISH:
            // 1) Avval lever buriladi (tilt ma'lumotlarini ishlatadi)
            animateTiltPivots(indices: leverIndices, tilt: true, duration: leverDuration)

            // 2) Lever tugagach, frame otkid holatiga o'tadi
            DispatchQueue.main.asyncAfter(deadline: .now() + leverDuration + 0.1) {
                self.animateTiltPivots(indices: frameIndices, tilt: true, duration: frameDuration)

                DispatchQueue.main.asyncAfter(deadline: .now() + frameDuration + 0.1) {
                    self.isAnimating = false
                }
            }
        } else {
            // OTKID YOPISH:
            // 1) Avval frame yopiladi
            animateTiltPivots(indices: frameIndices, tilt: false, duration: frameDuration)

            // 2) Frame tugagach, lever qaytadi
            DispatchQueue.main.asyncAfter(deadline: .now() + frameDuration + 0.1) {
                self.animateTiltPivots(indices: leverIndices, tilt: false, duration: leverDuration)

                DispatchQueue.main.asyncAfter(deadline: .now() + leverDuration + 0.1) {
                    self.isAnimating = false
                }
            }
        }
    }

    // Tilt animatsiya uchun boshlang'ich pozitsiyalarni saqlash
    private var originalPivotPositions: [Int: SIMD3<Float>] = [:]

    private func animateTiltPivots(indices: [Int], tilt: Bool, duration: Double) {
        let scale: Float = 0.001
        let centerX = modelWidth * scale / 2
        let centerY = modelHeight * scale / 2
        let centerZ = modelDepth * scale / 2
        let center: SIMD3<Float> = [centerX, centerY, centerZ]

        for index in indices {
            guard let pivotEntity = openPartEntities[index],
                  let data = openPartData[index] else { continue }

            // Mesh-specific tilt data ishlatamiz
            let tiltMaxAngleDeg = data.tiltMaxAngle
            // Agar tiltMaxAngle 0 bo'lsa, bu mesh tilt qilmaydi
            if tiltMaxAngleDeg == 0 {
                print("=== Tilt skip index \(index): tiltMaxAngle=0 ===")
                continue
            }

            let tiltAngle: Float = tilt ? tiltMaxAngleDeg * .pi / 180 : 0
            let tiltAxis = simd_normalize(data.tiltAxis)

            // Tilt pivot pozitsiyasi (world coordinates)
            let tiltPivotWorld = data.tiltPivot * scale - center

            // Lever (>= 1000) uchun frame-local koordinatalar kerak
            var tiltPivotLocal = tiltPivotWorld
            if index >= 1000 {
                // Lever - frame ning child i
                // Frame index: 1000-1999 -> 0-999, 2000+ -> 0-999
                let frameIndex = index >= 2000 ? index - 2000 : index - 1000
                if let framePivot = openPartEntities[frameIndex] {
                    // Frame-local koordinatalarga o'tkazish
                    tiltPivotLocal = tiltPivotWorld - framePivot.position
                }
            }

            // Boshlang'ich pozitsiyani saqlash (faqat birinchi marta)
            if originalPivotPositions[index] == nil {
                originalPivotPositions[index] = pivotEntity.position
            }

            let openPivotLocal = originalPivotPositions[index] ?? pivotEntity.position

            print("=== Tilt index \(index): angle=\(tiltMaxAngleDeg)°, axis=\(tiltAxis), tiltPivotLocal=\(tiltPivotLocal), openPivotLocal=\(openPivotLocal) ===")

            var transform = pivotEntity.transform

            if tilt {
                // Tilt ochish: open pivot dan tilt pivot ga nisbatan aylanish
                // offset = openPivot - tiltPivot
                let offset = openPivotLocal - tiltPivotLocal

                // Offset vektorini tilt axis atrofida aylantirish
                let rotation = simd_quatf(angle: tiltAngle, axis: tiltAxis)
                let rotatedOffset = rotation.act(offset)

                // Yangi pozitsiya = tiltPivot + rotatedOffset
                let newPosition = tiltPivotLocal + rotatedOffset

                transform.rotation = rotation
                transform.translation = newPosition
            } else {
                // Tilt yopish: boshlang'ich holatga qaytish
                transform.rotation = simd_quatf(angle: 0, axis: [0, 1, 0])
                transform.translation = openPivotLocal
            }

            pivotEntity.move(to: transform, relativeTo: pivotEntity.parent, duration: duration)
        }
    }

    @objc public func toggleOpenState(_ openNumber: NSNumber) {
        let open = openNumber.boolValue

        // Animatsiya davomida yangi animatsiya boshlash mumkin emas
        guard !isAnimating else {
            print("=== Animation already in progress, ignoring ===")
            return
        }

        print("=== toggleOpenState: open=\(open), entities=\(openPartEntities.keys.sorted()) ===")
        isOpen = open
        isAnimating = true

        // Lever indexlari (1000+) va frame indexlari (0-999)
        let leverIndices = Array(openPartEntities.keys.filter { $0 >= 1000 }.sorted())
        let frameIndices = Array(openPartEntities.keys.filter { $0 < 1000 }.sorted())

        print("=== Lever indices: \(leverIndices), Frame indices: \(frameIndices) ===")

        let leverDuration: Double = 0.4
        let frameDuration: Double = 0.6

        // Petlya open_part - frame pivot ning child i, shuning uchun frame aylanganida
        // avtomatik birga aylanadi. Alohida animatsiya kerak emas.

        if open {
            // OCHISH:
            // 1) Avval lever -90° buriladi
            print("=== STEP 1: Rotating lever ===")
            animatePivots(indices: leverIndices, open: true, duration: leverDuration)

            // 2) Lever tugagach, frame ochiladi (petlya avtomatik birga ketadi)
            DispatchQueue.main.asyncAfter(deadline: .now() + leverDuration + 0.1) {
                print("=== STEP 2: Opening frame ===")
                self.animatePivots(indices: frameIndices, open: true, duration: frameDuration)

                DispatchQueue.main.asyncAfter(deadline: .now() + frameDuration + 0.1) {
                    self.isAnimating = false
                    print("=== Animation complete ===")
                }
            }
        } else {
            // YOPISH:
            // 1) Avval frame yopiladi (petlya avtomatik birga ketadi)
            print("=== STEP 1: Closing frame ===")
            animatePivots(indices: frameIndices, open: false, duration: frameDuration)

            // 2) Frame tugagach, lever qaytadi
            DispatchQueue.main.asyncAfter(deadline: .now() + frameDuration + 0.1) {
                print("=== STEP 2: Rotating lever back ===")
                self.animatePivots(indices: leverIndices, open: false, duration: leverDuration)

                DispatchQueue.main.asyncAfter(deadline: .now() + leverDuration + 0.1) {
                    self.isAnimating = false
                    print("=== Animation complete ===")
                }
            }
        }
    }

    private func animatePivots(indices: [Int], open: Bool, duration: Double) {
        for index in indices {
            guard let pivotEntity = openPartEntities[index],
                  let data = openPartData[index] else { continue }

            let angleRad = open ? data.maxAngle * .pi / 180 : 0
            let childCount = pivotEntity.children.count
            print("=== Animate pivot index \(index): axis=\(data.axis), maxAngle=\(data.maxAngle), angleRad=\(angleRad), children=\(childCount) ===")

            var transform = pivotEntity.transform

            // Axis ga qarab aylantirish
            if abs(data.axis.z) > 0.5 {
                // Z o'qi (lever rotation) - axis belgisini saqlash
                let direction: Float = data.axis.z > 0 ? 1 : -1
                transform.rotation = simd_quatf(angle: angleRad * direction, axis: [0, 0, 1])
            } else if abs(data.axis.y) > 0.5 {
                // Y o'qi (gorizontal ochilish)
                let direction: Float = data.axis.y > 0 ? 1 : -1
                transform.rotation = simd_quatf(angle: angleRad * direction, axis: [0, 1, 0])
            } else if abs(data.axis.x) > 0.5 {
                // X o'qi (vertikal ochilish)
                let direction: Float = data.axis.x > 0 ? 1 : -1
                transform.rotation = simd_quatf(angle: angleRad * direction, axis: [1, 0, 0])
            }

            pivotEntity.move(to: transform, relativeTo: pivotEntity.parent, duration: duration)
        }
    }

    @objc public func resetViewState() {
        rotationX = 0
        rotationY = -40
        zoom = 1.0
        isAnimating = false
        isPresentationActive = false
        isAssemblyActive = false
        presentationPhase = 0

        // Stop any running timers
        presentationTimer?.invalidate()
        presentationTimer = nil
        presentationDisplayLink?.invalidate()
        presentationDisplayLink = nil
        presentationOnComplete = nil
        assemblyTimer?.invalidate()
        assemblyTimer = nil

        // Reset model transform - dastlabki holatga (Android bilan bir xil)
        updateModelRotation()
        modelEntity?.transform.scale = SIMD3<Float>(repeating: 1.0)

        // Reset all pivots to original rotation and position
        for (index, pivotEntity) in openPartEntities {
            pivotEntity.transform.rotation = simd_quatf(angle: 0, axis: [0, 1, 0])
            // Tilt dan keyin pozitsiyani ham qaytarish
            if let originalPos = originalPivotPositions[index] {
                pivotEntity.transform.translation = originalPos
            }
        }
        originalPivotPositions.removeAll()

        isOpen = false
        isTilted = false
    }

    // MARK: - Presentation (Mashina taqdimoti kabi har tomondan ko'rsatadi)

    private var isPresentationActive: Bool = false
    private var presentationTimer: Timer?
    private var presentationPhase: Int = 0
    private var presentationAnimationProgress: Float = 0
    private var presentationStartRotX: Float = 0
    private var presentationStartRotY: Float = 0
    private var presentationStartZoom: Float = 0
    private var presentationTargetRotX: Float = 0
    private var presentationTargetRotY: Float = 0
    private var presentationTargetZoom: Float = 0
    private var presentationAnimationDuration: TimeInterval = 0
    private var presentationAnimationStartTime: TimeInterval = 0

    @objc public func startPresentation() {
        guard !isPresentationActive else { return }
        isPresentationActive = true
        presentationPhase = 0

        print("=== startPresentation ===")

        // Darhol boshlang'ich pozitsiyaga o'tkazish
        rotationX = 0
        rotationY = 0
        zoom = 1.0
        updateModelRotation()

        // Animatsiyani darhol boshlash
        runPresentationPhase()
    }

    @objc public func stopPresentation() {
        isPresentationActive = false
        presentationTimer?.invalidate()
        presentationTimer = nil
        presentationDisplayLink?.invalidate()
        presentationDisplayLink = nil
        presentationOnComplete = nil
        presentationPhase = 0
        print("=== stopPresentation ===")
    }

    private func runPresentationPhase() {
        guard isPresentationActive else { return }

        switch presentationPhase {
        case 0:
            // Faza 0: 360° aylanish (to'g'ri turgan holatda)
            animateRotation(
                fromX: 0, fromY: 0, fromZoom: 1.0,
                toX: 0, toY: 360, toZoom: 1.0,
                duration: 8.0
            ) { [weak self] in
                self?.presentationPhase = 1
                self?.runPresentationPhase()
            }

        case 1:
            // Faza 1: Profil va shtapik detallariga yaqinlashtirish
            animateRotation(
                fromX: 0, fromY: 0, fromZoom: 1.0,
                toX: 30, toY: -30, toZoom: 2.5,
                duration: 3.0
            ) { [weak self] in
                self?.presentationPhase = 2
                self?.runPresentationPhase()
            }

        case 2:
            // Faza 2: Ichki qismlar bo'ylab sekin harakatlanish
            animateRotation(
                fromX: 30, fromY: -30, fromZoom: 2.5,
                toX: 35, toY: 55, toZoom: 2.2,
                duration: 3.0
            ) { [weak self] in
                self?.presentationPhase = 3
                self?.runPresentationPhase()
            }

        case 3:
            // Faza 3: Yuqoridan ko'ndalang - oyna va chita ko'rinishi
            animateRotation(
                fromX: 35, fromY: 55, fromZoom: 2.2,
                toX: 40, toY: 80, toZoom: 1.8,
                duration: 2.5
            ) { [weak self] in
                self?.presentationPhase = 4
                self?.runPresentationPhase()
            }

        case 4:
            // Faza 4: Zoom out - to'g'ri qarash (ochilish uchun tayyorgarlik)
            animateRotation(
                fromX: 40, fromY: 80, fromZoom: 1.8,
                toX: 0, toY: 45, toZoom: 1.2,
                duration: 2.5
            ) { [weak self] in
                self?.presentationPhase = 5
                self?.runPresentationPhase()
            }

        case 5:
            // Faza 5: Yonga ochilish
            if !isOpen {
                toggleOpenState(NSNumber(value: true))
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { [weak self] in
                guard let self = self, self.isPresentationActive else { return }
                self.presentationPhase = 6
                self.runPresentationPhase()
            }

        case 6:
            // Faza 6: Ochiq holatda aylanish
            animateRotation(
                fromX: 0, fromY: 45, fromZoom: 1.2,
                toX: 0, toY: 135, toZoom: 1.1,
                duration: 2.5
            ) { [weak self] in
                self?.presentationPhase = 7
                self?.runPresentationPhase()
            }

        case 7:
            // Faza 7: Yopish
            if isOpen {
                toggleOpenState(NSNumber(value: false))
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
                guard let self = self, self.isPresentationActive else { return }
                self.presentationPhase = 8
                self.runPresentationPhase()
            }

        case 8:
            // Faza 8: Old tarafga aylanish
            animateRotation(
                fromX: 0, fromY: 135, fromZoom: 1.1,
                toX: 0, toY: 0, toZoom: 1.0,
                duration: 2.0
            ) { [weak self] in
                self?.presentationPhase = 9
                self?.runPresentationPhase()
            }

        case 9:
            // Faza 9: Tilt ochilish (old tomondan)
            if !isTilted {
                toggleTiltState(NSNumber(value: true))
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { [weak self] in
                guard let self = self, self.isPresentationActive else { return }
                self.presentationPhase = 10
                self.runPresentationPhase()
            }

        case 10:
            // Faza 10: Tilt holatida harakatlanish
            animateRotation(
                fromX: 0, fromY: 0, fromZoom: 1.0,
                toX: 0, toY: 45, toZoom: 1.1,
                duration: 2.0
            ) { [weak self] in
                self?.presentationPhase = 11
                self?.runPresentationPhase()
            }

        case 11:
            // Faza 11: Orqa tarafga aylanish
            animateRotation(
                fromX: 0, fromY: 45, fromZoom: 1.1,
                toX: 0, toY: 180, toZoom: 1.0,
                duration: 2.5
            ) { [weak self] in
                self?.presentationPhase = 12
                self?.runPresentationPhase()
            }

        case 12:
            // Faza 12: Tilt yopish (orqa tomondan)
            if isTilted {
                toggleTiltState(NSNumber(value: false))
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
                guard let self = self, self.isPresentationActive else { return }
                self.presentationPhase = 13
                self.runPresentationPhase()
            }

        case 13:
            // Faza 13: Boshlang'ich holatga qaytish
            animateRotation(
                fromX: 0, fromY: 180, fromZoom: 1.0,
                toX: 0, toY: 360, toZoom: 1.0,
                duration: 3.0
            ) { [weak self] in
                // Loop - boshidan boshlash
                self?.presentationPhase = 0
                self?.runPresentationPhase()
            }

        default:
            presentationPhase = 0
            runPresentationPhase()
        }
    }

    /// Kamera pozitsiyasini smooth animatsiya bilan o'zgartirish
    private func animateRotation(
        fromX: Float, fromY: Float, fromZoom: Float,
        toX: Float, toY: Float, toZoom: Float,
        duration: TimeInterval,
        onComplete: @escaping () -> Void
    ) {
        // Joriy timerni to'xtatish
        presentationTimer?.invalidate()

        // Boshlang'ich qiymatlarni o'rnatish
        rotationX = fromX
        rotationY = fromY
        zoom = fromZoom

        presentationStartRotX = fromX
        presentationStartRotY = fromY
        presentationStartZoom = fromZoom
        presentationTargetRotX = toX
        presentationTargetRotY = toY
        presentationTargetZoom = toZoom
        presentationAnimationDuration = duration
        presentationAnimationStartTime = CACurrentMediaTime()

        updateModelRotation()

        print("=== animateRotation: from(\(fromX), \(fromY), \(fromZoom)) to(\(toX), \(toY), \(toZoom)) duration=\(duration) ===")

        // Animatsiya - DisplayLink ishlatish (Timer o'rniga)
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }

            self.presentationTimer?.invalidate()

            let displayLink = CADisplayLink(target: self, selector: #selector(self.presentationDisplayLinkUpdate))
            displayLink.preferredFramesPerSecond = 60
            displayLink.add(to: .main, forMode: .common)
            self.presentationTimer = nil  // Timer emas, displayLink ishlatamiz
            self.presentationDisplayLink = displayLink
            self.presentationOnComplete = onComplete
            print("=== DisplayLink started ===")
        }
    }

    private var presentationDisplayLink: CADisplayLink?
    private var presentationOnComplete: (() -> Void)?

    @objc private func presentationDisplayLinkUpdate() {
        guard isPresentationActive else {
            presentationDisplayLink?.invalidate()
            presentationDisplayLink = nil
            return
        }

        let elapsed = CACurrentMediaTime() - presentationAnimationStartTime
        let progress = min(Float(elapsed / presentationAnimationDuration), 1.0)

        // Easing - smoothstep
        let t = progress * progress * (3.0 - 2.0 * progress)

        rotationX = presentationStartRotX + (presentationTargetRotX - presentationStartRotX) * t
        rotationY = presentationStartRotY + (presentationTargetRotY - presentationStartRotY) * t
        zoom = presentationStartZoom + (presentationTargetZoom - presentationStartZoom) * t

        updateModelRotation()

        if progress >= 1.0 {
            print("=== Animation complete: rotX=\(rotationX), rotY=\(rotationY), zoom=\(zoom) ===")
            presentationDisplayLink?.invalidate()
            presentationDisplayLink = nil
            // rotationY ni normalize qilish (keyingi faza uchun)
            rotationY = rotationY.truncatingRemainder(dividingBy: 360)
            if rotationY < 0 { rotationY += 360 }
            presentationOnComplete?()
            presentationOnComplete = nil
        }
    }

    // MARK: - Assembly Animation (Android dagi kabi kategoriyalar bo'yicha)

    private var isAssemblyActive: Bool = false
    private var assemblyTimer: Timer?
    private var assemblyDisplayLink: CADisplayLink?
    private var assemblyPhase: Int = 0
    private var assemblyOnComplete: (() -> Void)?
    private var assemblyAnimationStartTime: TimeInterval = 0
    private var assemblyAnimationDuration: TimeInterval = 0
    private var assemblyAnimatingEntities: [Entity] = []
    private var assemblyStartPositions: [SIMD3<Float>] = []
    private var assemblyTargetPositions: [SIMD3<Float>] = []
    private var originalEntityPositions: [Entity: SIMD3<Float>] = [:]

    // Kategoriyalar (Android dagi kabi)
    private enum AssemblyCategory: CaseIterable {
        case shelf, profileFrame, profileInternal, openFrame, glass, lambri, chita, seal, rubber, shtapik, doorLock, petlya, handle
    }

    private func getCategory(for name: String) -> AssemblyCategory {
        let nameLower = name.lowercased()
        if nameLower.contains("handle") || nameLower.contains("ruchka") { return .handle }
        if nameLower.contains("petlya") || nameLower.contains("hinge") { return .petlya }
        if nameLower.contains("door_lock") || nameLower.contains("lock") { return .doorLock }
        if nameLower.contains("shtapik") { return .shtapik }
        if nameLower.contains("glass") || nameLower.contains("oyna") { return .glass }
        if nameLower.contains("chita") { return .chita }
        if nameLower.contains("seal") { return .seal }
        if nameLower.contains("rubber") || nameLower.contains("rezina") { return .rubber }
        if nameLower.contains("lambri") || nameLower.contains("panel") { return .lambri }
        if nameLower.contains("shelf") || nameLower.contains("tokcha") { return .shelf }
        if nameLower.contains("open_part") { return .openFrame }
        if nameLower.contains("internal") || nameLower.contains("impost") { return .profileInternal }
        return .profileFrame
    }

    private func getScatterDirection(for category: AssemblyCategory) -> SIMD3<Float> {
        switch category {
        case .profileFrame, .profileInternal: return [0, 0, -1]  // Orqaga
        case .openFrame: return [0, 0, -0.8]  // Orqaga
        case .shtapik: return [0, 0, 1]  // Oldinga
        case .glass, .lambri: return [0, 0, 0.5]  // Oldinga
        case .handle: return [1, 0, 0]  // O'ngga
        case .petlya: return [-1, 0, 0]  // Chapga
        case .doorLock: return [1, 0, 0]  // O'ngga
        case .shelf: return [0, -1, 0]  // Pastga
        case .chita, .seal, .rubber: return [0, 0, 0.6]  // Oldinga
        }
    }

    private func getScatterDistance(for category: AssemblyCategory) -> Float {
        switch category {
        case .profileFrame: return 0.8
        case .profileInternal: return 0.6
        case .openFrame: return 0.5
        case .glass: return 0.4
        case .lambri: return 0.35
        case .chita: return 0.5
        case .seal: return 0.4
        case .shtapik: return 0.6
        case .rubber: return 0.7
        case .handle: return 0.8
        case .petlya: return 0.7
        case .doorLock: return 0.8
        case .shelf: return 0.9
        }
    }

    @objc public func startAssembly() {
        guard !isAssemblyActive else { return }
        isAssemblyActive = true
        assemblyPhase = 0

        print("=== startAssembly ===")

        // Presentation to'xtatish
        stopPresentation()

        // Original pozitsiyalarni saqlash
        saveOriginalPositions()

        // 1-BOSQICH: Kamerani yaxshi burchakka olib borish
        animateAssemblyCamera(
            toX: 25, toY: 35, toZoom: 1.0,
            duration: 0.8
        ) { [weak self] in
            // 2-BOSQICH: Sochilish animatsiyasi
            self?.animateScatter(duration: 1.5) {
                // 3-BOSQICH: Yig'ish boshlash
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self?.runAssemblyPhase()
                }
            }
        }
    }

    private func saveOriginalPositions() {
        originalEntityPositions.removeAll()
        guard let model = modelEntity else { return }

        var savedCount = 0
        for child in model.children {
            // Barcha child entity pozitsiyalarini saqlash
            originalEntityPositions[child] = child.position
            savedCount += 1

            for subChild in child.children {
                originalEntityPositions[subChild] = subChild.position
                savedCount += 1

                // 3-chi daraja ham saqlash
                for subSubChild in subChild.children {
                    originalEntityPositions[subSubChild] = subSubChild.position
                    savedCount += 1
                }
            }
        }
        print("=== saveOriginalPositions: saved \(savedCount) entities ===")
    }

    private func animateScatter(duration: TimeInterval, onComplete: @escaping () -> Void) {
        guard let model = modelEntity else {
            print("=== animateScatter: modelEntity is nil! ===")
            onComplete()
            return
        }

        var scatteredCount = 0
        // Barcha entitylarni sochish (3 daraja chuqurlikkacha)
        for child in model.children {
            if scatterEntity(child) {
                scatteredCount += 1
            }
            for subChild in child.children {
                if scatterEntity(subChild) {
                    scatteredCount += 1
                }
                for subSubChild in subChild.children {
                    if scatterEntity(subSubChild) {
                        scatteredCount += 1
                    }
                }
            }
        }

        print("=== animateScatter: scattered \(scatteredCount) entities ===")

        // Animatsiya tugashini kutish
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
            onComplete()
        }
    }

    @discardableResult
    private func scatterEntity(_ entity: Entity) -> Bool {
        let name = entity.name
        // Agar nom bo'sh bo'lsa, bu pivot entity - child larini scatter qilish kerak
        if name.isEmpty {
            return false
        }
        let category = getCategory(for: name)
        let direction = getScatterDirection(for: category)
        let distance = getScatterDistance(for: category)

        let offset = direction * distance
        var transform = entity.transform
        transform.translation += offset
        entity.move(to: transform, relativeTo: entity.parent, duration: 1.5)
        print("=== scatterEntity: \(name) -> category=\(category), offset=\(offset) ===")
        return true
    }

    private func runAssemblyPhase() {
        guard isAssemblyActive else { return }

        let categories: [AssemblyCategory] = [
            .shelf, .profileFrame, .profileInternal, .openFrame,
            .glass, .lambri, .chita, .seal, .rubber, .shtapik,
            .doorLock, .petlya, .handle
        ]

        if assemblyPhase < categories.count {
            let category = categories[assemblyPhase]
            print("=== Assembly phase \(assemblyPhase): \(category) ===")

            // Kamera harakati (ba'zi fazalarda)
            if assemblyPhase == 4 {
                animateAssemblyCamera(toX: 30, toY: 45, toZoom: 1.1, duration: 0.8) { [weak self] in
                    self?.assembleCategory(category, duration: 1.0) {
                        self?.assemblyPhase += 1
                        self?.runAssemblyPhase()
                    }
                }
            } else if assemblyPhase == 9 {
                animateAssemblyCamera(toX: 35, toY: 55, toZoom: 1.15, duration: 0.8) { [weak self] in
                    self?.assembleCategory(category, duration: 1.0) {
                        self?.assemblyPhase += 1
                        self?.runAssemblyPhase()
                    }
                }
            } else if assemblyPhase == 11 {
                animateAssemblyCamera(toX: 28, toY: 40, toZoom: 1.1, duration: 0.8) { [weak self] in
                    self?.assembleCategory(category, duration: 1.0) {
                        self?.assemblyPhase += 1
                        self?.runAssemblyPhase()
                    }
                }
            } else {
                assembleCategory(category, duration: getDurationForCategory(category)) { [weak self] in
                    self?.assemblyPhase += 1
                    self?.runAssemblyPhase()
                }
            }
        } else {
            // Final - kamerani boshlang'ich holatga qaytarish
            animateAssemblyCamera(toX: 0, toY: -30, toZoom: 1.0, duration: 1.5) { [weak self] in
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self?.stopAssembly()
                }
            }
        }
    }

    private func getDurationForCategory(_ category: AssemblyCategory) -> TimeInterval {
        switch category {
        case .shelf: return 0.8
        case .profileFrame: return 1.5
        case .profileInternal: return 1.2
        case .openFrame: return 1.3
        case .glass: return 1.5
        case .lambri: return 1.0
        case .chita: return 1.2
        case .seal: return 1.0
        case .rubber: return 1.2
        case .shtapik: return 1.5
        case .doorLock: return 1.0
        case .petlya: return 1.0
        case .handle: return 1.2
        }
    }

    private func assembleCategory(_ category: AssemblyCategory, duration: TimeInterval, onComplete: @escaping () -> Void) {
        guard let model = modelEntity else {
            print("=== assembleCategory: modelEntity is nil! ===")
            onComplete()
            return
        }

        var entitiesToAssemble: [Entity] = []

        for child in model.children {
            let childName = child.name
            if !childName.isEmpty && getCategory(for: childName) == category {
                entitiesToAssemble.append(child)
            }
            for subChild in child.children {
                let subChildName = subChild.name
                if !subChildName.isEmpty && getCategory(for: subChildName) == category {
                    entitiesToAssemble.append(subChild)
                }
                // 3-chi daraja
                for subSubChild in subChild.children {
                    let subSubChildName = subSubChild.name
                    if !subSubChildName.isEmpty && getCategory(for: subSubChildName) == category {
                        entitiesToAssemble.append(subSubChild)
                    }
                }
            }
        }

        print("=== assembleCategory: \(category), found \(entitiesToAssemble.count) entities ===")

        // Original pozitsiyalarga qaytarish
        var assembledCount = 0
        for entity in entitiesToAssemble {
            if let originalPos = originalEntityPositions[entity] {
                var transform = entity.transform
                transform.translation = originalPos
                entity.move(to: transform, relativeTo: entity.parent, duration: duration)
                assembledCount += 1
                print("=== assembleCategory: moving \(entity.name) to \(originalPos) ===")
            } else {
                print("=== assembleCategory: NO original pos for \(entity.name) ===")
            }
        }
        print("=== assembleCategory: assembled \(assembledCount)/\(entitiesToAssemble.count) entities ===")


        DispatchQueue.main.asyncAfter(deadline: .now() + duration + 0.1) {
            onComplete()
        }
    }

    private func animateAssemblyCamera(toX: Float, toY: Float, toZoom: Float, duration: TimeInterval, onComplete: @escaping () -> Void) {
        let fromX = rotationX
        let fromY = rotationY
        let fromZoom = zoom

        presentationStartRotX = fromX
        presentationStartRotY = fromY
        presentationStartZoom = fromZoom
        presentationTargetRotX = toX
        presentationTargetRotY = toY
        presentationTargetZoom = toZoom
        presentationAnimationDuration = duration
        presentationAnimationStartTime = CACurrentMediaTime()

        assemblyDisplayLink?.invalidate()

        let displayLink = CADisplayLink(target: self, selector: #selector(assemblyDisplayLinkUpdate))
        displayLink.preferredFramesPerSecond = 60
        displayLink.add(to: .main, forMode: .common)
        assemblyDisplayLink = displayLink
        assemblyOnComplete = onComplete
    }

    @objc private func assemblyDisplayLinkUpdate() {
        let elapsed = CACurrentMediaTime() - presentationAnimationStartTime
        let progress = min(Float(elapsed / presentationAnimationDuration), 1.0)

        let t = progress * progress * (3.0 - 2.0 * progress)

        rotationX = presentationStartRotX + (presentationTargetRotX - presentationStartRotX) * t
        rotationY = presentationStartRotY + (presentationTargetRotY - presentationStartRotY) * t
        zoom = presentationStartZoom + (presentationTargetZoom - presentationStartZoom) * t

        updateModelRotation()

        if progress >= 1.0 {
            assemblyDisplayLink?.invalidate()
            assemblyDisplayLink = nil
            assemblyOnComplete?()
            assemblyOnComplete = nil
        }
    }

    private func runAssemblyStep() {
        // Eski kod - endi ishlatilmaydi
    }

    private func oldRunAssemblyStep() {
        guard isAssemblyActive else { return }

        // Model entitylarini topish
        guard let model = modelEntity else {
            isAssemblyActive = false
            return
        }

        let allChildren = Array(model.children)
        let totalSteps = allChildren.count + openPartEntities.count

        if assemblyPhase < totalSteps {
            // Har bir qadamda bitta elementni ko'rsatish
            if assemblyPhase < allChildren.count {
                let child = allChildren[assemblyPhase]
                // Animatsiya bilan ko'rsatish
                child.scale = SIMD3<Float>(repeating: 0.01)
                child.isEnabled = true
                var transform = child.transform
                transform.scale = SIMD3<Float>(repeating: 1.0)
                child.move(to: transform, relativeTo: child.parent, duration: 0.3)
            }

            assemblyPhase += 1

            // Keyingi qadam
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [weak self] in
                self?.runAssemblyStep()
            }
        } else {
            // Animatsiya tugadi
            isAssemblyActive = false
            print("=== Assembly complete ===")
        }
    }

    @objc public func stopAssembly() {
        isAssemblyActive = false
        assemblyTimer?.invalidate()
        assemblyTimer = nil
        assemblyDisplayLink?.invalidate()
        assemblyDisplayLink = nil
        assemblyPhase = 0

        guard let model = modelEntity else { return }

        // Barcha animatsiyalarni to'xtatish va original pozitsiyalarga qaytarish
        var restoredCount = 0
        for child in model.children {
            // Avval animatsiyani to'xtatish - stopAllAnimations()
            child.stopAllAnimations()

            // Original pozitsiyaga qaytarish
            if let originalPos = originalEntityPositions[child] {
                child.position = originalPos
                restoredCount += 1
            }

            child.isEnabled = true
            child.scale = SIMD3<Float>(repeating: 1.0)

            for subChild in child.children {
                subChild.stopAllAnimations()

                if let originalPos = originalEntityPositions[subChild] {
                    subChild.position = originalPos
                    restoredCount += 1
                }

                subChild.isEnabled = true
                subChild.scale = SIMD3<Float>(repeating: 1.0)

                // 3-chi daraja ham tekshirish
                for subSubChild in subChild.children {
                    subSubChild.stopAllAnimations()
                    if let originalPos = originalEntityPositions[subSubChild] {
                        subSubChild.position = originalPos
                        restoredCount += 1
                    }
                    subSubChild.isEnabled = true
                    subSubChild.scale = SIMD3<Float>(repeating: 1.0)
                }
            }
        }

        // Kamerani boshlang'ich holatga qaytarish
        rotationX = 0
        rotationY = -30
        zoom = 1.0
        updateModelRotation()

        print("=== stopAssembly: restored \(restoredCount) entities ===")
    }

    @objc public func clearAllMeshes() {
        // Stop any running animations
        stopPresentation()
        stopAssembly()

        modelEntity?.removeFromParent()
        modelEntity = nil
        openPartEntities.removeAll()
        openPartData.removeAll()
        originalPivotPositions.removeAll()
        pendingMeshes.removeAll()
        isOpen = false
        isTilted = false
        isAnimating = false
    }

    // MARK: - Mesh creation

    private func createMeshEntity(from data: PendingMesh, scale: Float, center: SIMD3<Float>) -> ModelEntity? {
        let vertexCount = data.positions.count / 3
        guard vertexCount >= 3 else { return nil }

        var positions: [SIMD3<Float>] = []
        var normals: [SIMD3<Float>] = []
        var texCoords: [SIMD2<Float>] = []
        var hasInvalidData = false

        // texCoords mavjudligini tekshirish
        let hasTexCoords = data.texCoords.count >= vertexCount * 2

        // Maxsus ishlov kerak bo'lgan meshlarni aniqlash
        let nameLower = data.name.lowercased()
        // T-profil va unga o'xshash profillar (shtulp ham T-profil)
        let isTProfile = nameLower.contains("t_profile") || nameLower.contains("internal") || nameLower.contains("shtulp")
        let isPetlya = nameLower.contains("petlya") || nameLower.contains("hinge")
        // Orqa ruchka - normallar mirror qilingan, qayta hisoblash kerak
        let isBackHandle = nameLower.contains("handle_back")
        // Zamok/Lock - normallarni oldga qaratish kerak
        let isLock = nameLower.contains("lock") || nameLower.contains("zamok")

        for i in 0..<vertexCount {
            let x = data.positions[i * 3] * scale - center.x
            let y = data.positions[i * 3 + 1] * scale - center.y
            let z = data.positions[i * 3 + 2] * scale - center.z

            if x.isNaN || y.isNaN || z.isNaN || x.isInfinite || y.isInfinite || z.isInfinite {
                hasInvalidData = true
                positions.append([0, 0, 0])
            } else if abs(x) > 10 || abs(y) > 10 || abs(z) > 10 {
                hasInvalidData = true
                positions.append([0, 0, 0])
            } else {
                positions.append([x, y, z])
            }

            let nx = data.normals[i * 3]
            let ny = data.normals[i * 3 + 1]
            let nz = data.normals[i * 3 + 2]
            normals.append([nx, ny, nz])

            // Texture koordinatalari
            if hasTexCoords {
                let u = data.texCoords[i * 2]
                let v = data.texCoords[i * 2 + 1]
                // Profile uchun UV swap - textura uzunasiga cho'zilishi uchun
                let isProfile = nameLower.contains("profile") || nameLower.contains("profil") ||
                                nameLower.contains("frame") || nameLower.contains("rama") ||
                                nameLower.contains("impost")
                if isProfile {
                    texCoords.append([v, u])  // UV swap
                } else {
                    texCoords.append([u, v])
                }
            }
        }

        if hasInvalidData {
            print("=== MESH \(data.name) HAS INVALID DATA - skipping ===")
            return nil
        }

        // T-profil, petlya, orqa ruchka va zamok uchun normallarni har bir triangle uchun alohida hisoblash
        // faceCulling = .none ishlatilgani uchun yo'nalish muhim emas
        if (isTProfile || isPetlya || isBackHandle || isLock) && positions.count >= 3 {
            print("=== NORMAL RECALC: \(data.name), vertices=\(positions.count) ===")
            var newNormals: [SIMD3<Float>] = Array(repeating: [0, 0, 0], count: positions.count)

            // Har bir triangle uchun normal hisoblash
            for t in stride(from: 0, to: positions.count, by: 3) {
                guard t + 2 < positions.count else { break }

                let p0 = positions[t]
                let p1 = positions[t + 1]
                let p2 = positions[t + 2]

                // Cross product bilan normal hisoblash
                let edge1 = p1 - p0
                let edge2 = p2 - p0
                var normal = simd_cross(edge1, edge2)
                let len = simd_length(normal)
                if len > 0.0001 {
                    normal = normal / len
                }

                newNormals[t] = normal
                newNormals[t + 1] = normal
                newNormals[t + 2] = normal
            }
            normals = newNormals
        }

        let indices: [UInt32] = (0..<UInt32(vertexCount)).map { $0 }

        var descriptor = MeshDescriptor()
        descriptor.positions = MeshBuffer(positions)
        descriptor.normals = MeshBuffer(normals)
        if hasTexCoords && texCoords.count == vertexCount {
            descriptor.textureCoordinates = MeshBuffer(texCoords)
        }
        descriptor.primitives = .triangles(indices)

        guard let mesh = try? MeshResource.generate(from: [descriptor]) else {
            return nil
        }

        let material = createPBRMaterial(for: data.name, colors: data.colors)
        let entity = ModelEntity(mesh: mesh, materials: [material])
        entity.name = data.name  // Nomni o'rnatish (assembly uchun kerak)
        return entity
    }

    /// Profile meshlarni textura bilan yaratish - barcha yuzalarga textura qo'yiladi
    private func createProfileEntities(from data: PendingMesh, scale: Float, center: SIMD3<Float>) -> [ModelEntity] {
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

        // Avval barcha pozitsiyalar va normallarni tayyorlash
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
                let entity = ModelEntity(mesh: mesh, materials: [material])
                entity.name = data.name  // Nomni o'rnatish (assembly uchun kerak)
                result.append(entity)
            }
        }

        return result
    }

    // Shtapik uchun - qora oyoq va oq tana alohida (barcha yuzalarga textura)
    private func createShtapikEntities(from data: PendingMesh, scale: Float, center: SIMD3<Float>) -> [ModelEntity] {
        let vertexCount = data.positions.count / 3
        guard vertexCount >= 3 else { return [] }

        // Dark (rezina) va bright (textura bilan barcha yuzalar) uchun massivlar
        var darkPositions: [SIMD3<Float>] = []
        var darkNormals: [SIMD3<Float>] = []

        var brightPositions: [SIMD3<Float>] = []
        var brightNormals: [SIMD3<Float>] = []
        var brightTexCoords: [SIMD2<Float>] = []

        let nameLower = data.name.lowercased()
        // Left va top shtapiklar uchun normallarni to'liq teskari qilish kerak
        let needsFullNormalFlip = nameLower.contains("left") || nameLower.contains("top")
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

        // Avval profil rangini topish (eng yorug' rang, lekin 1.0 dan past)
        var profileR: Float = 0.5, profileG: Float = 0.5, profileB: Float = 0.5
        var maxProfileBrightness: Float = 0
        for i in 0..<min(data.colors.count / 4, 200) {
            let r = data.colors[i * 4]
            let g = data.colors[i * 4 + 1]
            let b = data.colors[i * 4 + 2]
            let brightness = (r + g + b) / 3.0
            // Rezina emas (brightness > 0.15) va oq emas (brightness < 0.98)
            if brightness > 0.15 && brightness < 0.98 && brightness > maxProfileBrightness {
                maxProfileBrightness = brightness
                profileR = r
                profileG = g
                profileB = b
            }
        }
        // Agar topilmasa, eng yorug' rangni olish
        if maxProfileBrightness < 0.16 {
            for i in 0..<min(data.colors.count / 4, 200) {
                let r = data.colors[i * 4]
                let g = data.colors[i * 4 + 1]
                let b = data.colors[i * 4 + 2]
                let brightness = (r + g + b) / 3.0
                if brightness > maxProfileBrightness {
                    maxProfileBrightness = brightness
                    profileR = r
                    profileG = g
                    profileB = b
                }
            }
        }

        // Trianglelarni rangiga va normalga qarab ajratish
        let triangleCount = vertexCount / 3
        for t in 0..<triangleCount {
            let i0 = t * 3
            let i1 = t * 3 + 1
            let i2 = t * 3 + 2

            // Triangle o'rtacha yorug'ligi
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

            // NaN yoki chegaradan tashqari tekshirish
            var hasInvalidVertex = false
            for vi in [i0, i1, i2] {
                let x = data.positions[vi * 3] * scale - center.x
                let y = data.positions[vi * 3 + 1] * scale - center.y
                let z = data.positions[vi * 3 + 2] * scale - center.z
                if x.isNaN || y.isNaN || z.isNaN || x.isInfinite || y.isInfinite || z.isInfinite ||
                   abs(x) > 10 || abs(y) > 10 || abs(z) > 10 {
                    hasInvalidVertex = true
                    break
                }
            }

            // Noto'g'ri vertex bo'lsa bu triangleni o'tkazib yuborish
            if hasInvalidVertex {
                continue
            }

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

                if needsFullNormalFlip {
                    nx = -nx
                    ny = -ny
                    nz = -nz
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
                let darkEntity = ModelEntity(mesh: darkMesh, materials: [darkMaterial])
                darkEntity.name = data.name + "_dark"  // Nomni o'rnatish
                entities.append(darkEntity)
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
                let brightEntity = ModelEntity(mesh: brightMesh, materials: [brightMaterial])
                brightEntity.name = data.name  // Nomni o'rnatish
                entities.append(brightEntity)
            }
        }

        return entities
    }

    private func createPBRMaterial(for meshName: String, colors: [Float]) -> Material {
        let nameLower = meshName.lowercased()

        // Get base color from first vertex (RGBA format)
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
        // Glass - transparent, yorug'likka bog'liq emas (UnlitMaterial)
        else if nameLower.contains("glass") {
            // Kotlin dan kelgan rang (default: 0.8, 0.9, 0.95, alpha=0.08)
            var glassR: Float = 0.8, glassG: Float = 0.9, glassB: Float = 0.95, glassA: Float = 0.08
            if colors.count >= 4 {
                glassR = min(1.0, max(0.0, colors[0]))
                glassG = min(1.0, max(0.0, colors[1]))
                glassB = min(1.0, max(0.0, colors[2]))
                glassA = min(1.0, max(0.0, colors[3]))
            }
            // UnlitMaterial - yorug'likka reaksiya qilmaydi, rang doim bir xil
            var material = UnlitMaterial()
            material.color = .init(tint: UIColor(red: CGFloat(glassR), green: CGFloat(glassG), blue: CGFloat(glassB), alpha: 1.0))
            // Shaffoflik
            material.blending = .transparent(opacity: .init(floatLiteral: glassA))
            return material
        }
        // Rubber/Seal - dark matte, double-sided
        else if nameLower.contains("rubber") || nameLower.contains("seal") {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: 0.12, green: 0.12, blue: 0.12, alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 0.9)
            material.faceCulling = .none
            return material
        }
        // Chita/Spacer - metallic silver, double-sided
        else if nameLower.contains("chita") || nameLower.contains("spacer") {
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: 0.72, green: 0.72, blue: 0.75, alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.7)
            material.roughness = .init(floatLiteral: 0.2)
            material.faceCulling = .none
            return material
        }
        // Shtapik - faqat profil rangi (texturasiz - UV koordinatalari to'g'ri emas)
        else if nameLower.contains("shtapik") {
            var maxBrightness: Float = 0
            var brightR: Float = 0.96, brightG: Float = 0.96, brightB: Float = 0.96
            let vertexCount = colors.count / 4
            for i in 0..<min(vertexCount, 100) {
                let vr = colors[i * 4]
                let vg = colors[i * 4 + 1]
                let vb = colors[i * 4 + 2]
                let brightness = (vr + vg + vb) / 3.0
                // Rezina emas (> 0.15) va eng yorug'
                if brightness > 0.15 && brightness > maxBrightness {
                    maxBrightness = brightness
                    brightR = vr
                    brightG = vg
                    brightB = vb
                }
            }
            // Shtapik uchun faqat rang - textura qo'yilmaydi
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: CGFloat(brightR), green: CGFloat(brightG), blue: CGFloat(brightB), alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 1.0)
            material.faceCulling = .none
            return material
        }
        // Handle/Ruchka - PROFIL RANGIDA (plastik, mat)
        // MUHIM: "open_part" dan OLDIN tekshirish kerak (door_hinge_openpart uchun)
        else if nameLower.contains("handle") || nameLower.contains("ruchka") {
            print("=== HANDLE MATERIAL: \(meshName), using profileColor=(\(profileColor.r),\(profileColor.g),\(profileColor.b)) ===")
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: CGFloat(profileColor.r), green: CGFloat(profileColor.g), blue: CGFloat(profileColor.b), alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 0.8)
            material.faceCulling = .none
            return material
        }
        // Hinge/Petlya - PROFIL RANGIDA (plastik, mat)
        // MUHIM: "open_part" dan OLDIN tekshirish kerak (door_hinge_openpart uchun)
        else if nameLower.contains("hinge") || nameLower.contains("petlya") {
            print("=== PETLYA MATERIAL: \(meshName), using profileColor=(\(profileColor.r),\(profileColor.g),\(profileColor.b)) ===")
            var material = PhysicallyBasedMaterial()
            material.baseColor = .init(tint: UIColor(red: CGFloat(profileColor.r), green: CGFloat(profileColor.g), blue: CGFloat(profileColor.b), alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.0)
            material.roughness = .init(floatLiteral: 0.8)
            material.faceCulling = .none
            return material
        }
        // Internal T-profiles - double-sided material with optional texture
        // Shtulp ham T-profil (DOOR ochilishda ishlatiladi)
        else if nameLower.contains("internal") || nameLower.contains("t_profile") || nameLower.contains("shtulp") {
            print("=== T-PROFILE MATERIAL: \(meshName), color=(\(r),\(g),\(b)), hasTexture=\(profileTexture != nil) ===")
            // PhysicallyBasedMaterial - to'g'ri sozlamalar bilan
            if let texture = profileTexture {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(texture: .init(texture))
                material.roughness = .init(floatLiteral: 0.35)
                material.metallic = .init(floatLiteral: 0.0)
                material.specular = .init(floatLiteral: 0.5)
                material.faceCulling = .none
                return material
            } else {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
                material.metallic = .init(floatLiteral: 0.0)
                material.roughness = .init(floatLiteral: 1.0)  // Mat plastik
                material.faceCulling = .none  // Ikkala tomonni ko'rsatish - bu asosiy fix!
                return material
            }
        }
        // Open part frame (Z-profile) - double-sided material with optional texture
        else if nameLower.contains("open_part") {
            // PhysicallyBasedMaterial - to'g'ri sozlamalar bilan
            if let texture = profileTexture {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(texture: .init(texture))
                material.roughness = .init(floatLiteral: 0.35)
                material.metallic = .init(floatLiteral: 0.0)
                material.specular = .init(floatLiteral: 0.5)
                material.faceCulling = .none
                return material
            } else {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
                material.metallic = .init(floatLiteral: 0.0)
                material.roughness = .init(floatLiteral: 1.0)  // Maksimal mat
                material.faceCulling = .none  // Ikkala tomonni ko'rsatish
                return material
            }
        }
        // Frame (L-profile) - double-sided material with optional texture
        else if nameLower.contains("frame") {
            // PhysicallyBasedMaterial - to'g'ri sozlamalar bilan
            if let texture = profileTexture {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(texture: .init(texture))
                material.roughness = .init(floatLiteral: 0.35)
                material.metallic = .init(floatLiteral: 0.0)
                material.specular = .init(floatLiteral: 0.5)
                material.faceCulling = .none
                return material
            } else {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
                material.metallic = .init(floatLiteral: 0.0)
                material.roughness = .init(floatLiteral: 1.0)
                material.faceCulling = .none  // Ikkala tomonni ko'rsatish
                return material
            }
        }
        // Shotlanka/Pattern - GOLD tekstura yoki profil RANGI (teksturasiz!)
        else if nameLower.contains("shotlanka") || nameLower.contains("pattern") {
            print("=== SHOTLANKA MATERIAL: \(meshName), hasGoldTexture=\(shotlankaTexture != nil), color=(\(r),\(g),\(b)) ===")

            // Agar GOLD tekstura mavjud bo'lsa - gold metallik material
            if let texture = shotlankaTexture {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(texture: .init(texture))
                material.metallic = .init(floatLiteral: 0.3)  // Kamroq metallik - yorug'roq ko'rinish
                material.roughness = .init(floatLiteral: 0.4)  // O'rtacha yaltiroqlik
                material.specular = .init(floatLiteral: 0.6)  // Yorug'lik uchun specular
                material.faceCulling = .none
                return material
            }
            // Gold emas - faqat profil RANGI (teksturasiz!) - yorug'roq ko'rinish
            else {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
                material.metallic = .init(floatLiteral: 0.0)
                material.roughness = .init(floatLiteral: 0.5)  // Kamroq mat - yorug'roq
                material.specular = .init(floatLiteral: 0.4)   // Yorug'lik uchun
                material.faceCulling = .none
                return material
            }
        }
        // Lock - metallic material (eshik qulfi)
        else if nameLower.contains("lock") || nameLower.contains("zamok") {
            var material = PhysicallyBasedMaterial()
            // Kumush metallik rang
            material.baseColor = .init(tint: UIColor(red: 0.75, green: 0.75, blue: 0.78, alpha: 1.0))
            material.metallic = .init(floatLiteral: 0.9)  // Yuqori metallik
            material.roughness = .init(floatLiteral: 0.15)  // Yaltiroq
            material.faceCulling = .none
            return material
        }
        // All other (profile, etc.) - PVC plastic, double-sided with optional texture
        else {
            // Profil uchun tekstura ishlatish (profile, shtapik va h.k.)
            // Rubber, glass, chita, handle va boshqa maxsus meshlar bu yerga kelmaydi (yuqorida catch qilinadi)
            // PhysicallyBasedMaterial - to'g'ri sozlamalar bilan
            if let texture = profileTexture {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(texture: .init(texture))
                material.roughness = .init(floatLiteral: 0.35)
                material.metallic = .init(floatLiteral: 0.0)
                material.specular = .init(floatLiteral: 0.5)
                material.faceCulling = .none
                return material
            } else {
                var material = PhysicallyBasedMaterial()
                material.baseColor = .init(tint: UIColor(red: CGFloat(r), green: CGFloat(g), blue: CGFloat(b), alpha: 1.0))
                material.metallic = .init(floatLiteral: 0.0)
                material.roughness = .init(floatLiteral: 1.0)  // Mat plastik
                material.faceCulling = .none  // Ikkala tomonni ko'rsatish
                return material
            }
        }
    }
}

// MARK: - Data structures

private struct PendingMesh {
    let name: String
    let positions: [Float]
    let normals: [Float]
    let colors: [Float]
    let texCoords: [Float]  // UV koordinatalari
    let openPartIndex: Int
    let pivot: SIMD3<Float>
    let axis: SIMD3<Float>
    let maxAngle: Float
    // Tilt animatsiya ma'lumotlari
    let tiltPivot: SIMD3<Float>
    let tiltAxis: SIMD3<Float>
    let tiltMaxAngle: Float
    let tiltPhaseStart: Float
    let tiltPhaseEnd: Float
    // Asosiy animatsiya fazalari
    let phaseStart: Float
    let phaseEnd: Float
}

// MARK: - Global factory functions for Kotlin interop

/// Kotlin dan chaqirish uchun global funksiya
@_cdecl("createRealityKitView")
public func createRealityKitView() -> UnsafeMutableRawPointer? {
    let view = RealityKitWindow3DView(frame: .zero)
    return Unmanaged.passRetained(view).toOpaque()
}

/// View ni pointer dan olish
@_cdecl("getRealityKitViewFromPointer")
public func getRealityKitViewFromPointer(_ pointer: UnsafeMutableRawPointer) -> UIView {
    return Unmanaged<UIView>.fromOpaque(pointer).takeUnretainedValue()
}

// MARK: - Factory class for ObjC

@objc(RealityKitViewFactory)
public class RealityKitViewFactory: NSObject {

    @objc public static let shared: RealityKitViewFactory = {
        print("=== RealityKitViewFactory initialized ===")
        return RealityKitViewFactory()
    }()

    public override init() {
        super.init()
        print("=== RealityKitViewFactory instance created ===")
    }

    private var currentView: RealityKitWindow3DView?

    @objc public func createView() -> UIView {
        // Agar mavjud view bo'lsa, uni qaytaramiz (refresh bilan agar kerak bo'lsa)
        if let existingView = currentView {
            print("🔄 RealityKitViewFactory: Existing view found, needsRefresh: \(existingView.needsRefresh)")
            if existingView.needsRefresh {
                print("🔄 Refreshing existing view...")
                existingView.refresh()
            }
            return existingView
        }

        // Yangi view yaratish
        print("🆕 RealityKitViewFactory: Creating new view")
        fullCleanup()

        let view = RealityKitWindow3DView(frame: .zero)
        currentView = view
        return view
    }

    @objc public func getCurrentView() -> UIView? {
        return currentView
    }

    /// 3D view ni tozalash - AR ochishdan oldin chaqirish kerak
    @objc public func cleanup() {
        print("🧹 RealityKitViewFactory cleanup called")
        if let view = currentView {
            view.cleanup()  // ARView ni o'chirish, lekin view instance saqlanadi
            // currentView ni SAQLAYMIZ - refresh() chaqirish uchun
        }
    }

    /// 3D view ni qayta tiklash - AR yopilgandan keyin chaqirish kerak
    @objc public func refresh() {
        print("🔄 RealityKitViewFactory refresh called, currentView: \(currentView != nil ? "exists" : "nil")")
        if let view = currentView {
            print("🔄 Calling view.refresh()...")
            view.refresh()  // ARView ni qayta yaratish
        } else {
            print("⚠️ No currentView to refresh!")
        }
    }

    /// To'liq tozalash - yangi view yaratishdan oldin
    private func fullCleanup() {
        print("🧹 RealityKitViewFactory FULL cleanup")
        if let view = currentView {
            view.cleanup()
            view.removeFromSuperview()
            currentView = nil
        }
    }

    @objc public static func isSupported() -> Bool {
        return true
    }
}
