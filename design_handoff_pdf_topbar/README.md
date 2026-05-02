# Handoff: PDF Viewer Topbar

## Overview

A topbar component for a mobile PDF viewer screen. It contains four primary actions — **Back**, **Title** (file name), **Download**, **Share** — plus an optional **Search** action. A separate floating **Page Indicator** pill sits at the bottom of the viewport above the PDF content.

This handoff provides **two final design directions** for the developer to implement. The product owner should pick ONE of the two for production:

1. **Minimal Mono** — recommended primary direction. Modern, brand-neutral, clear primary action.
2. **Classic iOS Native** — alternative direction. Familiar Apple-style, ideal if the product is iOS-only and wants to match system conventions.

---

## About the Design Files

The files in this bundle are **design references created in HTML/React** — prototypes showing the intended look, layout, spacing, and behavior. They are NOT production code to copy directly.

Your task is to **recreate these designs in the target codebase's existing environment** (e.g. SwiftUI, Jetpack Compose, React Native, Flutter, native UIKit, or web React) using its established patterns, component library, and design tokens. If no environment exists yet, choose the most appropriate framework for the platform and implement there.

The HTML mocks use inline styles for clarity. In production, use the codebase's styling system (StyleSheet, Tailwind, styled-components, theme tokens, etc.).

## Fidelity

**High-fidelity (hifi).** All measurements, colors, typography, and spacing in this README are final and intended for pixel-perfect implementation. The icons are from the Lucide icon family (Lucide-style 24×24, stroke 2) — use the equivalent in your codebase (SF Symbols on iOS, Material icons on Android, Lucide-react on web, etc.).

---

## Screens / Views

There is **one screen**: the PDF viewer. The topbar is the focus of this handoff; the PDF content area below is shown as a placeholder for context only — implement using your existing PDF rendering library (PDFKit, react-pdf, pdf.js, etc.).

### Screen anatomy (top → bottom)

1. **Status bar** — system-provided (time, signal, battery). Don't reimplement; the topbar sits below it.
2. **Topbar** — the component this handoff covers. Two design options below.
3. **PDF content area** — scrollable PDF rendering, full remaining viewport.
4. **Page indicator pill** — floating at bottom-center, overlays the PDF content (~28pt from bottom).

---

## Design Direction 1 — Minimal Mono (PRIMARY, recommended)

### Layout

- Topbar height: **~64pt** (content padded `6pt 8pt 10pt`)
- Background: solid white `#FFFFFF`
- Below the topbar: a 1pt hairline divider in `#F1F1F3`
- Single horizontal row, flex layout with `gap: 4pt`, vertically centered

### Children, left → right

| # | Element | Size | Notes |
|---|---|---|---|
| 1 | Back button (chip) | 38×38 | Circular, gray bg `#F4F4F6`, `arrow-left` icon (Lucide), 20pt, stroke 2, color `#111111` |
| 2 | Title block | flex: 1, min-width: 0, padding `0 6pt` | Two-line stack |
| 3 | Search button (chip) | 38×38 | Same as back chip; `search` icon |
| 4 | Share button (chip) | 38×38 | Same as back chip; `share` icon (iOS-style: square + up arrow) |
| 5 | Download button (PRIMARY) | 38×38 | Filled chip, bg `#111111`, `download` icon, color `#FFFFFF`, stroke 2.2 |

### Title block (#2)

- **Line 1 (filename)**: SF Pro / system semibold, **15pt**, color `#0A0A0A`, letter-spacing −0.2pt, line-height 1.2, single line, truncate with ellipsis (`text-overflow: ellipsis`)
- **Line 2 (meta)**: SF Pro / system medium 500, **11pt**, color `#8E8E93`, letter-spacing 0.2pt, **UPPERCASE**, margin-top 1pt. Format: `PDF · 2.4 MB` (file type + file size). Use a middle-dot `·` separator with a regular space on each side.

### Behavior

- **Back chip** → pop the navigation stack
- **Search chip** → opens an inline search field (replaces the topbar contents); show a Cancel text button to exit. Implementation note: animate width or fade contents over ~200ms.
- **Share chip** → triggers system share sheet with the PDF file
- **Download chip (primary)** → saves file to user's device; show a brief toast/haptic on success
- **Tap states**: scale chip to 0.96 for 100ms, or apply a 0.7 opacity overlay
- **Long-press on title** → reveal the full filename in a tooltip / actionsheet (since title is truncated)

### Why this works
- **Download is the visually dominant action** (filled black chip), matching the product's emphasis hierarchy.
- **Monochrome** lets the topbar adapt to any brand color theme without redesign.
- **Meta line** (PDF · 2.4 MB) gives users useful context without cluttering the title.
- **Equal-sized circular chips** create a consistent rhythm and large tap targets (38pt > 44pt accessible minimum when accounting for surrounding padding).

---

## Design Direction 2 — Classic iOS Native (alternative)

### Layout

- Topbar height: **52pt**
- Background: solid white `#FFFFFF`
- Below the topbar: a 0.5pt hairline divider at `rgba(0,0,0,0.08)`
- CSS Grid layout: `grid-template-columns: 1fr auto 1fr` — equal flexible columns on left/right with the title naturally centered. Padding: `0 8pt`.

### Children

#### Leading (left column)
- Standard iOS back button: `chevron-left` icon **28pt**, stroke 2.4, color **iOS Blue `#0A84FF`** + the parent screen's name as a text label (e.g. "Files") in **17pt regular**, letter-spacing −0.4pt, color `#0A84FF`, gap 2pt between icon and label. The whole thing is a single tappable target.

#### Center (middle column)
- Title text: SF Pro semibold, **17pt**, color `#000000`, letter-spacing −0.4pt, max-width 180pt, single line, truncate with ellipsis. Just the filename without extension (e.g. "Annual Report").

#### Trailing (right column, right-aligned)
- Three icon-only buttons, each 36×36pt, with `gap: 4pt` and `padding-right: 4pt`:
  1. **Search** — `search` icon, 22pt, stroke 2, color `#0A84FF`
  2. **Share** — `share` icon (iOS-style), 22pt, stroke 2, color `#0A84FF`
  3. **Download** — `download` icon, 22pt, stroke 2, color `#0A84FF`

### Behavior

- **Back tap** → pop nav stack (entire icon+label is the hit target)
- Other actions same as Minimal Mono
- All actions use iOS Blue tint, so primary emphasis is conveyed by **position** (rightmost = most-recent action, but no visual hierarchy beyond that).

### Why this works (and tradeoffs)
- **Familiar to iOS users** — matches Mail, Files, Notes, etc.
- All three trailing actions look identical in weight; users may not immediately spot Download as the "primary" action. If your product treats Download as the headline action, prefer Minimal Mono instead.
- The "Files" back-label assumes you can pass the previous screen's name. If you can't, drop the label and use a chevron-only back button.

---

## Shared component: Page Indicator Pill

Both designs include a floating pill at the bottom of the viewport, **overlaying** the PDF content.

| Property | Value |
|---|---|
| Position | absolute, `bottom: 28pt`, horizontally centered |
| Padding | `8pt 14pt` |
| Border radius | 999pt (full pill) |
| Background | `rgba(20, 20, 22, 0.78)` with `backdrop-filter: blur(20pt)` |
| Text color | `#FFFFFF` |
| Font | SF Pro / system semibold, **13pt**, letter-spacing −0.1pt, `font-variant-numeric: tabular-nums` |
| Shadow | `0 4pt 16pt rgba(0,0,0,0.18), 0 1pt 2pt rgba(0,0,0,0.1)` |

**Content**: `<current> / <total>` — e.g. `7 / 24`. The slash `/` is rendered with `opacity: 0.5` and the total with `opacity: 0.6` for a subtle hierarchy.

**Behavior**:
- Always visible while scrolling the PDF
- Optionally tap to open a page-jump sheet (out of scope for this handoff, but recommended)
- Briefly increase opacity / scale on page change (200ms tween) to draw attention

---

## Interactions & Behavior Summary

| Action | Trigger | Result |
|---|---|---|
| Back | Tap back button | Pop navigation stack |
| Open search | Tap search icon | Topbar morphs into inline search field. Keyboard opens. Cancel returns to default topbar. |
| Share | Tap share icon | System share sheet opens with the PDF file attached |
| Download | Tap download icon | File saves to device's downloads / Files app. Show success toast + haptic. |
| Page change | Scroll or programmatic | Page indicator updates with brief animation |

### Animation tokens
- **Tap feedback**: scale 1 → 0.96, 100ms, ease-out
- **Topbar → search transition**: 200ms ease-in-out (fade + width)
- **Page indicator update**: 200ms ease-out

---

## State Management

Minimal state needed:
- `currentPage: number` (drives page indicator + scroll position sync)
- `totalPages: number` (loaded once when PDF opens)
- `searchOpen: boolean` (Minimal Mono only — toggles search field)
- `searchQuery: string` (when search is open)
- `fileMeta: { name: string, sizeBytes: number, type: 'PDF' }` (for the title block)

Async actions:
- Download: trigger native save flow; surface success/error
- Share: trigger system share sheet
- Search: debounced (~200ms) text-search across PDF, highlight results in the rendering layer

---

## Design Tokens

### Colors

| Token | Value | Usage |
|---|---|---|
| `--color-bg` | `#FFFFFF` | Topbar background |
| `--color-text-primary` | `#0A0A0A` | Title (Minimal Mono) |
| `--color-text-primary-pure` | `#000000` | Title (Classic iOS) |
| `--color-text-secondary` | `#8E8E93` | Meta line |
| `--color-icon` | `#111111` | Icons (Minimal Mono) |
| `--color-ios-blue` | `#0A84FF` | Icons + back label (Classic iOS) |
| `--color-chip-bg` | `#F4F4F6` | Chip background (Minimal Mono) |
| `--color-chip-bg-primary` | `#111111` | Download chip (Minimal Mono) |
| `--color-divider` | `#F1F1F3` | Topbar bottom hairline (Minimal Mono) |
| `--color-divider-ios` | `rgba(0,0,0,0.08)` | Topbar bottom hairline (Classic iOS) |
| `--color-overlay-bg` | `rgba(20,20,22,0.78)` | Page indicator pill |

### Spacing

- Base unit: **4pt**
- Topbar horizontal padding: 8pt
- Chip-to-chip gap: 4pt
- Title internal padding: 6pt horizontal
- Page indicator from bottom: 28pt

### Typography (SF Pro / system font)

| Style | Size | Weight | Letter-spacing | Line-height | Usage |
|---|---|---|---|---|---|
| Title (Minimal Mono) | 15pt | 600 | −0.2pt | 1.2 | Filename |
| Title (Classic iOS) | 17pt | 600 | −0.4pt | 1.0 | Filename |
| Back label (Classic iOS) | 17pt | 400 | −0.4pt | 1.0 | "Files" |
| Meta | 11pt | 500 | 0.2pt | 1.0 | "PDF · 2.4 MB" — UPPERCASE |
| Page indicator | 13pt | 600 | −0.1pt | 1.0 | "7 / 24" — tabular-nums |

### Component sizing

- Chip (circular button): **38×38pt**, border-radius 999pt
- iOS trailing icon button: **36×36pt**
- Topbar height (Minimal Mono): ~64pt with internal padding
- Topbar height (Classic iOS): 52pt
- Page indicator pill: padding `8pt 14pt`, border-radius 999pt

### Border radius

- Chips & pills: 999pt (full)
- Square icon button (if needed): 12pt (used in alternative variants — not in final two)

### Shadows

- Page indicator: `0 4pt 16pt rgba(0,0,0,0.18), 0 1pt 2pt rgba(0,0,0,0.1)`
- No shadow on the topbar itself — the hairline divider provides separation.

---

## Icons (Lucide family — 24×24 viewBox, stroke 2 unless specified)

Use your platform's equivalent — names below match Lucide / lucide-react:

| Action | Lucide name | iOS SF Symbol equivalent | Android Material equivalent |
|---|---|---|---|
| Back (Minimal Mono) | `arrow-left` | `arrow.backward` | `arrow_back` |
| Back (Classic iOS) | `chevron-left` | `chevron.backward` | `chevron_left` |
| Search | `search` | `magnifyingglass` | `search` |
| Share | iOS-style square+up arrow | `square.and.arrow.up` | `ios_share` |
| Download | `download` | `arrow.down.to.line` | `download` |

The SVG sources for the icons are inlined in `topbar-variants.jsx` (see the `Ico` object at the top of the file) — copy these paths if your platform doesn't have a matching system icon.

---

## Assets

No external image assets. All icons are inline SVGs — see `topbar-variants.jsx` (object literal `Ico` near the top of the file).

The PDF content itself is rendered by your PDF library and is out of scope for this handoff.

---

## Files in this handoff

- **`README.md`** — this document
- **`preview.html`** — open in any browser to see both variants side-by-side, draggable, focusable. Useful for product/QA review.
- **`topbar-variants.jsx`** — the React component source. Look at `V1_ClassicIOS` and `V2_MinimalMono` — those are the two final designs. Other `V*` functions can be ignored. The shared `StatusBar`, `PdfPage`, `PageIndicator`, `Phone`, and helper styling functions (`btnReset`, `chip`, etc.) at the bottom are the ones used by both finals.
- **`design-canvas.jsx`** — only used by `preview.html` for the side-by-side canvas. Not part of the production design.

---

## Recommended next steps for the developer

1. **Pick ONE direction** with the product owner (Minimal Mono recommended).
2. **Audit your codebase** for:
   - An existing topbar / app-bar component you can extend
   - A button/chip component matching the spec
   - A theme/token file for colors and typography
3. **If those exist**, extend them rather than building from scratch — match the codebase's conventions even if it means slight visual deviation from this doc; design intent > pixel parity.
4. **Implement the topbar and the page indicator separately** — they are independent components.
5. **Wire up state**: `currentPage`, `totalPages`, `fileMeta`, plus the search toggle for Minimal Mono.
6. **Hook actions** to native file/share/download APIs on your platform.
7. **Test with**:
   - Long file names (does ellipsis work?)
   - Small screens (e.g. iPhone SE, 375pt)
   - Right-to-left languages (mirror the layout)
   - Dynamic Type / large accessibility text sizes (typography should scale)
   - Dark mode (if your app supports it — colors above are light-mode only; please derive dark-mode equivalents from your existing theme system)
