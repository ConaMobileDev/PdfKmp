import PDFKit
import PdfKmp
import SwiftUI

/// Lists every bundled sample document and renders the chosen one in a
/// `PDFView`. PDFKit displays the document as vector — zoom is unlimited and
/// glyph edges stay sharp at any magnification, matching the on-device
/// behaviour of native PDF apps.
///
/// The detail screen implements `Direction 2 — Classic iOS Native` from
/// `design_handoff_pdf_topbar/`: a 52pt nav bar with a custom
/// chevron + "Samples" back label, a centered 17pt semibold title,
/// and three 22pt iOS-blue trailing icons (search, share, download).
/// Tapping search morphs the toolbar into a `TextField` + `Cancel`
/// button — same pattern as Mail / Files / Notes.
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

/// iOS Blue (#0A84FF) from the design handoff. Hard-coded rather than
/// `.tint` because the spec is explicit about the exact accent.
private let iosBlue = Color(red: 0x0A / 255, green: 0x84 / 255, blue: 0xFF / 255)

private struct SamplePreviewView: View {
    let entry: SampleEntry

    @Environment(\.dismiss) private var dismiss

    @State private var document: PDFDocument?
    @State private var pdfData: Data?

    @State private var searchActive: Bool = false
    @State private var searchQuery: String = ""
    @State private var matches: [PDFSelection] = []
    @State private var activeMatchIndex: Int = 0
    @FocusState private var searchFieldFocused: Bool
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
        .navigationBarBackButtonHidden(true)
        .navigationBarTitleDisplayMode(.inline)
        // Custom 17pt semibold title — matches the handoff exactly so
        // we don't rely on the SwiftUI default that varies per OS
        // version. Hidden during search so the field gets the slot.
        .toolbar {
            toolbarContent
        }
        .safeAreaInset(edge: .bottom) {
            // Match navigation overlay rides above the keyboard while
            // search is active and at least one hit exists. Mirrors
            // the Compose viewer's PdfSearchBar match counter.
            if searchActive {
                searchFooter
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

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        if searchActive {
            // ── Morph: search field replaces the entire topbar.
            ToolbarItem(placement: .principal) {
                searchField
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Cancel") {
                    closeSearch()
                }
                .foregroundStyle(iosBlue)
            }
        } else {
            // ── Default: chevron + back label, centered title, three
            // 22pt trailing icons in iOS Blue.
            ToolbarItem(placement: .topBarLeading) {
                Button(action: { dismiss() }) {
                    HStack(spacing: 2) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 17, weight: .semibold))
                        Text("Samples")
                            .font(.system(size: 17))
                            .kerning(-0.4)
                    }
                    .foregroundStyle(iosBlue)
                }
            }
            ToolbarItem(placement: .principal) {
                Text(entry.title)
                    .font(.system(size: 17, weight: .semibold))
                    .kerning(-0.4)
                    .foregroundStyle(.black)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .frame(maxWidth: 220)
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button(action: openSearch) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 22, weight: .regular))
                }
                .foregroundStyle(iosBlue)

                if let data = pdfData {
                    ShareLink(item: temporaryURL(for: data)) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 22, weight: .regular))
                    }
                    .foregroundStyle(iosBlue)

                    Button(action: { saveToDocuments(data: data) }) {
                        Image(systemName: "arrow.down.to.line")
                            .font(.system(size: 22, weight: .regular))
                    }
                    .foregroundStyle(iosBlue)
                }
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
                .font(.system(size: 14, weight: .regular))
            TextField("Search in document", text: $searchQuery)
                .focused($searchFieldFocused)
                .submitLabel(.search)
                .onSubmit { navigateMatch(by: 1) }
                .textFieldStyle(.plain)
            if !searchQuery.isEmpty {
                Button {
                    searchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Color(uiColor: .tertiarySystemFill))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .frame(minWidth: 220, idealWidth: 280, maxWidth: 320)
        .onChange(of: searchQuery) { _, newValue in
            performSearch(query: newValue)
        }
    }

    private var searchFooter: some View {
        HStack(spacing: 12) {
            if matches.isEmpty {
                Text(searchQuery.isEmpty ? "" : "No matches")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer()
            } else {
                Text("\(activeMatchIndex + 1) of \(matches.count)")
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(.secondary)
                Spacer()
                Button {
                    navigateMatch(by: -1)
                } label: {
                    Image(systemName: "chevron.up")
                        .font(.system(size: 17, weight: .regular))
                }
                .foregroundStyle(iosBlue)
                Button {
                    navigateMatch(by: 1)
                } label: {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 17, weight: .regular))
                }
                .foregroundStyle(iosBlue)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.regularMaterial)
    }

    // MARK: - Search

    private func openSearch() {
        searchActive = true
        // Defer focus-grab until the toolbar morph has had a frame to
        // mount the TextField — focusing too eagerly is a no-op.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            searchFieldFocused = true
        }
    }

    private func closeSearch() {
        searchFieldFocused = false
        searchActive = false
        searchQuery = ""
        matches = []
        activeMatchIndex = 0
    }

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
        view.highlightedSelections = nil
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        if uiView.document !== document {
            uiView.document = document
        }
        if let active = activeMatch {
            // `highlightedSelections` paints every match with a
            // translucent fill (PDFKit defaults to yellow);
            // `setCurrentSelection` + `go(to:)` scrolls the page so
            // the active hit lands in view and inverts its colour
            // to call attention to it.
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
