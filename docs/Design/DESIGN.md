---
name: Analog Archive Dark
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353534'
  on-surface: '#e5e2e1'
  on-surface-variant: '#c7c7bf'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#90918a'
  outline-variant: '#464742'
  surface-tint: '#c8c6c2'
  primary: '#ffffff'
  on-primary: '#30312e'
  primary-container: '#e4e2dd'
  on-primary-container: '#656461'
  inverse-primary: '#5e5e5b'
  secondary: '#d0c5b5'
  on-secondary: '#363025'
  secondary-container: '#4d463a'
  on-secondary-container: '#beb4a4'
  tertiary: '#ffffff'
  on-tertiary: '#303030'
  tertiary-container: '#e4e2e1'
  on-tertiary-container: '#656464'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e4e2dd'
  primary-fixed-dim: '#c8c6c2'
  on-primary-fixed: '#1b1c19'
  on-primary-fixed-variant: '#474744'
  secondary-fixed: '#ede1d1'
  secondary-fixed-dim: '#d0c5b5'
  on-secondary-fixed: '#201b11'
  on-secondary-fixed-variant: '#4d463a'
  tertiary-fixed: '#e4e2e1'
  tertiary-fixed-dim: '#c8c6c6'
  on-tertiary-fixed: '#1b1c1c'
  on-tertiary-fixed-variant: '#474747'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
typography:
  headline-xl:
    fontFamily: Playfair Display
    fontSize: 64px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Playfair Display
    fontSize: 48px
    fontWeight: '600'
    lineHeight: '1.2'
  headline-lg-mobile:
    fontFamily: Playfair Display
    fontSize: 32px
    fontWeight: '600'
    lineHeight: '1.2'
  headline-md:
    fontFamily: Playfair Display
    fontSize: 32px
    fontWeight: '500'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Source Serif 4
    fontSize: 20px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Source Serif 4
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  label-md:
    fontFamily: Work Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: '1.4'
    letterSpacing: 0.1em
  label-sm:
    fontFamily: Work Sans
    fontSize: 10px
    fontWeight: '400'
    lineHeight: '1.4'
    letterSpacing: 0.05em
spacing:
  unit: 8px
  container-max: 1280px
  gutter: 24px
  margin-desktop: 64px
  margin-mobile: 20px
---

## Brand & Style
The design system is an exercise in digital preservation and high-end editorial aesthetics, reimagined for a low-light environment. It targets a sophisticated audience of collectors, historians, and luxury enthusiasts who value the tactile nature of physical media. 

The style is **Minimalist-Editorial**. It prioritizes extreme white space (negative space), high-contrast typography, and a "paper-like" digital texture. The transition to dark mode moves away from stark blacks toward deep, layered charcoals to maintain a sense of depth and luxury without causing eye strain. The emotional response should be one of quiet authority, timelessness, and focused immersion.

## Colors
The palette is intentionally restricted to evoke a monochromatic, archival feel. 

- **Neutral (Base):** `#121212` (Obsidian) serves as the primary background, providing a deep, non-distracting canvas.
- **Primary (Text/Ink):** `#FBF9F4` (Off-White) is used for all primary headings and body text to maintain high legibility and a classic "ink-on-paper" contrast.
- **Secondary (Accent):** `#8C8375` (Muted Bronze) is used sparingly for metadata, labels, and subtle interactive states, adding a touch of aged luxury.
- **Tertiary (Stroke/Divider):** `#3D3D3D` is used for hairlines, borders, and structural elements to define the layout without breaking the minimalist flow.

## Typography
The typographic hierarchy is the cornerstone of the design system. It uses a high-contrast pairing of a sophisticated serif for narrative content and a functional sans-serif for utility.

- **Headlines:** Playfair Display is utilized for its dramatic strokes and elegant proportions. It should be typeset with tight letter-spacing to emphasize its editorial roots.
- **Body:** Source Serif 4 provides exceptional readability at various sizes, maintaining the "archival" feel while ensuring comfort during long-form reading.
- **Labels/Metadata:** Work Sans is used in all-caps for functional elements, providing a clear distinction between content and interface.

## Layout & Spacing
The layout follows a **Fixed Grid** philosophy on desktop to mimic the structured columns of a premium magazine or art book. 

- **Desktop:** A 12-column grid with a wide 64px margin to frame the content. Gutters are kept generous at 24px to allow elements to "breathe."
- **Mobile:** A 4-column grid with 20px margins. Headlines should scale down significantly to avoid awkward word breaks.
- **Vertical Rhythm:** Spacing between sections should be aggressive (96px+) to signal transitions in narrative, while internal component spacing should adhere to a strict 8px incremental scale.

## Elevation & Depth
In this design system, depth is achieved through **Tonal Layering** and **Low-Contrast Outlines** rather than traditional shadows. 

- **Surfaces:** No heavy drop shadows are permitted. Instead, use a slightly lighter charcoal (`#1A1A1A`) for elevated cards to separate them from the background.
- **Dividers:** Use 1px "Hairline" borders in `#3D3D3D`. This reinforces the structural, grid-based nature of the archive.
- **Interaction:** Hover states should be indicated by a subtle shift in stroke opacity or a transition from `#8C8375` (Bronze) to `#FBF9F4` (Off-White), rather than a change in elevation.

## Shapes
The shape language is strictly **Sharp (0px)**. All containers, buttons, and image frames must have square corners. This reinforces the precision of the design system and mirrors the physical edges of archival photographs, documents, and books. Any "softness" is provided through typography and color rather than geometry.

## Components
- **Buttons:** Primary buttons are solid `#FBF9F4` with `#121212` text. Secondary buttons are 1px outlines in `#3D3D3D` with text in `#FBF9F4`. All buttons use `label-md` typography.
- **Input Fields:** Bottom-border only (hairline style) in `#3D3D3D`. Label floats above in `label-sm`.
- **Cards:** No background by default; defined by 1px top and bottom borders. On hover, the background may transition to `#1A1A1A`.
- **Lists:** Clean rows separated by 1px dividers. Metadata (dates, categories) should be aligned to the right in `label-md` bronze.
- **Chips/Tags:** Minimalist tags using `label-sm` with a 1px border. No background fill.
- **Specialty Component (The Collector's Tray):** A persistent bottom-bar or side-panel used for saving items, featuring a semi-opaque `#121212` background with a `#3D3D3D` top stroke.