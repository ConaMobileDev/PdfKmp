import PDFKit
import PdfKmp
import SwiftUI

/// Lists every bundled sample document and renders the chosen one in a
/// `PDFView`. PDFKit displays the document as vector — zoom is unlimited and
/// glyph edges stay sharp at any magnification, matching the on-device
/// behaviour of native PDF apps.
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

    var body: some View {
        Group {
            if let document = document {
                PdfPreview(document: document)
            } else {
                ProgressView("Rendering…")
            }
        }
        .onAppear {
            let data = entry.buildPdfData()
            document = PDFDocument(data: data)
        }
    }
}

private struct PdfPreview: UIViewRepresentable {
    let document: PDFDocument

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        uiView.document = document
    }
}
