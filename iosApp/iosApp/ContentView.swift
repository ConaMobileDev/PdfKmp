import PDFKit
import PdfKmp
import SwiftUI

/// Lists every bundled sample document and renders the chosen one in a
/// `PDFView`. PDFKit displays the document as vector — zoom is unlimited and
/// glyph edges stay sharp at any magnification, matching the on-device
/// behaviour of native PDF apps.
///
/// The detail screen now mirrors `:pdfkmp-viewer`'s Classic iOS Native
/// topbar variant from `design_handoff_pdf_topbar/`: search via the
/// system `.searchable` modifier (matches typed → next match scrolls
/// into view), download via a custom toolbar button that writes to the
/// app's `Documents` directory, and share via SwiftUI's `ShareLink`
/// with a `.pdf` temp URL — same UX shape as the Compose viewer on
/// Android, expressed in iOS-native primitives.
struct ContentView: View {
    @State private var selected: SampleEntry?

    var body: some View {
        NavigationStack {
            List(samples) { entry in
                Button(entry.title) { selected = entry }
            }
            .navigationTitle("PdfKmp samples")
            .navigationDestination(item: $selected) { entry in
                SamplePreviewView(entry: entry)
                    .navigationTitle(entry.title)
                    .navigationBarTitleDisplayMode(.inline)
            }
        }
    }
}

private struct SampleEntry: Identifiable, Hashable {
    let id: String
    let title: String
    let buildPdfData: () -> Data

    static func == (lhs: SampleEntry, rhs: SampleEntry) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

/// Loads the bundled demo PNG. Returns empty bytes if the asset is missing
/// so image-related samples render as blank placeholders rather than
/// crashing the app.
private func loadSampleImage() -> KotlinByteArray {
    guard let url = Bundle.main.url(forResource: "sample", withExtension: "png"),
          let data = try? Data(contentsOf: url) else {
        return KotlinByteArray(size: 0)
    }
    let bytes = KotlinByteArray(size: Int32(data.count))
    data.withUnsafeBytes { raw in
        let buffer = raw.bindMemory(to: UInt8.self)
        for i in 0..<data.count {
            bytes.set(index: Int32(i), value: Int8(bitPattern: buffer[i]))
        }
    }
    return bytes
}

private let samples: [SampleEntry] = [
    SampleEntry(id: "brochure", title: "⭐ Brochure (README hero)") {
        Samples.shared.brochure().toNSData() as Data
    },
    SampleEntry(id: "hello-world", title: "Hello world") {
        Samples.shared.helloWorld().toNSData() as Data
    },
    SampleEntry(id: "typography", title: "Typography — text + decorations + alignment + rich") {
        Samples.shared.typography().toNSData() as Data
    },
    SampleEntry(id: "row-column", title: "Row & Column with weights") {
        Samples.shared.rowAndColumn().toNSData() as Data
    },
    SampleEntry(id: "space-between", title: "Column SpaceBetween") {
        Samples.shared.columnSpaceBetween().toNSData() as Data
    },
    SampleEntry(id: "table", title: "Tables & lists") {
        Samples.shared.tableShowcase().toNSData() as Data
    },
    SampleEntry(id: "vector", title: "Vectors + circle/ellipse") {
        Samples.shared.vectorShowcase().toNSData() as Data
    },
    SampleEntry(id: "vector-advanced", title: "Vector — gradients + arcs + transforms") {
        Samples.shared.vectorAdvanced().toNSData() as Data
    },
    SampleEntry(id: "custom-designs", title: "Custom designs + decorations (gradients, corners, borders)") {
        Samples.shared.customDesigns(imageBytes: loadSampleImage()).toNSData() as Data
    },
    SampleEntry(id: "page-chrome", title: "Page chrome — header / footer / page# / watermark / links / i18n") {
        Samples.shared.pageChrome().toNSData() as Data
    },
    SampleEntry(id: "long-body", title: "Long body — MoveToNextPage") {
        Samples.shared.longBody().toNSData() as Data
    },
    SampleEntry(id: "sliced-body", title: "Long body — Slice") {
        Samples.shared.slicedBody().toNSData() as Data
    },
    SampleEntry(id: "padding", title: "Custom padding") {
        Samples.shared.customPadding().toNSData() as Data
    },
    SampleEntry(id: "image", title: "Image (Fit + Crop)") {
        Samples.shared.withImage(imageBytes: loadSampleImage()).toNSData() as Data
    },
    SampleEntry(id: "sliced-image", title: "Tall image — sliced") {
        Samples.shared.slicedImage(imageBytes: loadSampleImage()).toNSData() as Data
    },
    SampleEntry(id: "showcase", title: "Showcase — every v1 feature in one PDF") {
        Samples.shared.showcase().toNSData() as Data
    },
]

private struct SamplePreviewView: View {
    let entry: SampleEntry

    @State private var document: PDFDocument?
    @State private var pdfData: Data?

    @State private var searchQuery: String = ""
    @State private var matches: [PDFSelection] = []
    @State private var activeMatchIndex: Int = 0
    @State private var savedAlert: Bool = false

    /// Filename used by both the share sheet and the "save to Files"
    /// destination so the user sees the same name in both flows.
    private var fileName: String { "\(entry.id).pdf" }

    var body: some View {
        Group {
            if let document = document {
                PdfPreview(
                    document: document,
                    activeMatch: matches.indices.contains(activeMatchIndex)
                        ? matches[activeMatchIndex]
                        : nil
                )
            } else {
                ProgressView("Rendering…")
            }
        }
        .searchable(text: $searchQuery, prompt: "Search in document")
        .onChange(of: searchQuery) { _, newValue in
            performSearch(query: newValue)
        }
        .toolbar {
            // Match counter — only visible while there's a query and at
            // least one hit, mirrors the Compose PdfSearchBar's "N / M"
            // badge.
            if !searchQuery.isEmpty {
                ToolbarItem(placement: .principal) {
                    if matches.isEmpty {
                        Text("No matches")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        Text("\(activeMatchIndex + 1) / \(matches.count)")
                            .font(.footnote.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
            }

            // Prev / Next match navigation surfaces only while
            // searching so the toolbar isn't permanently cluttered.
            if !matches.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        navigateMatch(by: -1)
                    } label: {
                        Image(systemName: "chevron.up")
                    }
                    .accessibilityLabel("Previous match")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        navigateMatch(by: 1)
                    } label: {
                        Image(systemName: "chevron.down")
                    }
                    .accessibilityLabel("Next match")
                }
            }

            // Download — writes the PDF into the app's Documents
            // directory, surfaced in the Files app under
            // "On My iPhone / <AppName>". Equivalent of
            // `rememberPdfSaveAction()` on iOS.
            if let data = pdfData {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        saveToDocuments(data: data)
                    } label: {
                        Image(systemName: "arrow.down.to.line")
                    }
                    .accessibilityLabel("Save to Files")
                }

                ToolbarItem(placement: .topBarTrailing) {
                    ShareLink(item: temporaryURL(for: data)) {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel("Share PDF")
                }
            }
        }
        .alert("Saved to Files", isPresented: $savedAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("\(fileName) is now in On My iPhone / iosApp.")
        }
        .onAppear {
            let data = entry.buildPdfData()
            pdfData = data
            document = PDFDocument(data: data)
        }
    }

    // MARK: - Search

    private func performSearch(query: String) {
        guard let document, !query.isEmpty else {
            matches = []
            activeMatchIndex = 0
            return
        }
        matches = document.findString(query, withOptions: [.caseInsensitive])
        activeMatchIndex = 0
    }

    private func navigateMatch(by step: Int) {
        guard !matches.isEmpty else { return }
        let count = matches.count
        activeMatchIndex = ((activeMatchIndex + step) % count + count) % count
    }

    // MARK: - File handling

    private func saveToDocuments(data: Data) {
        guard let documentsURL = FileManager.default.urls(
            for: .documentDirectory, in: .userDomainMask
        ).first else { return }
        let target = documentsURL.appendingPathComponent(fileName)
        do {
            try data.write(to: target, options: .atomic)
            savedAlert = true
        } catch {
            // Failures fall through silently — the user shouldn't see
            // a crash here, and there's no toast equivalent on iOS
            // we want to plumb in for a sample.
        }
    }

    private func temporaryURL(for data: Data) -> URL {
        let target = FileManager.default.temporaryDirectory
            .appendingPathComponent(fileName)
        // Overwrite any stale copy from a previous render so the share
        // sheet always points at fresh bytes.
        try? data.write(to: target, options: .atomic)
        return target
    }
}

private struct PdfPreview: UIViewRepresentable {
    let document: PDFDocument
    let activeMatch: PDFSelection?

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        // Highlight active search hits with a translucent yellow fill so
        // the visual treatment lines up with the Compose viewer's
        // PdfSearchOverlay on Android.
        view.highlightedSelections = nil
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        if uiView.document !== document {
            uiView.document = document
        }
        if let active = activeMatch {
            // Two layers: `highlightedSelections` paints every match
            // with a translucent fill (PDFKit defaults to yellow),
            // `setCurrentSelection` plus `go(to:)` scrolls the page so
            // the active hit lands in view and inverts its colour to
            // call attention to it.
            let highlight = active.copy() as? PDFSelection
            highlight?.color = .systemYellow.withAlphaComponent(0.5)
            uiView.highlightedSelections = highlight.map { [$0] }
            uiView.setCurrentSelection(active, animate: true)
            uiView.go(to: active)
        } else {
            uiView.highlightedSelections = nil
            uiView.clearSelection()
        }
    }
}
