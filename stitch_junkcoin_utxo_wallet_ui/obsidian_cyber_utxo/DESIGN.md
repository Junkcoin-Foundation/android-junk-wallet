---
name: Obsidian Cyber-UTXO
colors:
  surface: '#0f131c'
  surface-dim: '#0f131c'
  surface-bright: '#353942'
  surface-container-lowest: '#0a0e16'
  surface-container-low: '#181c24'
  surface-container: '#1c2028'
  surface-container-high: '#262a33'
  surface-container-highest: '#31353e'
  on-surface: '#dfe2ee'
  on-surface-variant: '#bcc9cd'
  inverse-surface: '#dfe2ee'
  inverse-on-surface: '#2c3039'
  outline: '#869397'
  outline-variant: '#3d494c'
  surface-tint: '#4cd7f6'
  primary: '#4cd7f6'
  on-primary: '#003640'
  primary-container: '#06b6d4'
  on-primary-container: '#00424f'
  inverse-primary: '#00687a'
  secondary: '#4edea3'
  on-secondary: '#003824'
  secondary-container: '#00a572'
  on-secondary-container: '#00311f'
  tertiary: '#ffb95f'
  on-tertiary: '#472a00'
  tertiary-container: '#e79400'
  on-tertiary-container: '#563400'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#acedff'
  primary-fixed-dim: '#4cd7f6'
  on-primary-fixed: '#001f26'
  on-primary-fixed-variant: '#004e5c'
  secondary-fixed: '#6ffbbe'
  secondary-fixed-dim: '#4edea3'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#005236'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#0f131c'
  on-background: '#dfe2ee'
  surface-variant: '#31353e'
typography:
  headline-xl:
    fontFamily: Geist
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.025em
  headline-xl-mobile:
    fontFamily: Geist
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Geist
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  mono-lg:
    fontFamily: JetBrains Mono
    fontSize: 15px
    fontWeight: '500'
    lineHeight: 22px
    letterSpacing: -0.01em
  mono-md:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
  mono-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '400'
    lineHeight: 14px
    letterSpacing: 0.02em
  label-caps:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 12px
    letterSpacing: 0.08em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit-2xs: 0.125rem
  unit-xs: 0.25rem
  unit-sm: 0.5rem
  unit-md: 0.75rem
  unit-base: 1rem
  unit-lg: 1.25rem
  unit-xl: 1.5rem
  unit-2xl: 2rem
  unit-3xl: 3rem
  gutter-mobile: 1rem
  margin-mobile: 1rem
  touch-target-min: 3rem
---

## Brand & Style

This design system expresses sovereign financial security, technical velocity, and uncompromising cryptographic precision. Engineered specifically for a native UTXO-based architecture, the interface pairs the discipline of institutional-grade custody tools with the fluid responsiveness of modern Web3 applications.

The design philosophy balances **Deep Space Obsidian Surfaces** with **Surgical Neon Accents**:
- **Tactile Security:** Critical wallet states (seed phrases, cryptographic signatures, fee estimates) communicate weight and immutability through structured modular framing and tactile feedback states.
- **Architectural Utility:** Avoids decorative skeuomorphism in favor of clear information architecture, high-density transaction data, and instantaneous legibility under varied lighting conditions.
- **Visual Cadence:** Uses deep obsidian slate (#0B0F17) layered with translucent surface containers, laser-cut borders (#1E293B), and targeted electroluminescent highlights (cyan for actions, emerald for confirmations/MWEB privacy, and electric amber for unconfirmed mempool or high-gas alerts).

## Colors

The palette is engineered exclusively for a deep dark environment, optimizing OLED battery efficiency and minimizing eye fatigue during continuous on-chain monitoring.

### Palette Architecture
- **Canvas Base (`#0B0F17`):** The foundational obsidian background across all screen scaffolds.
- **Surface Elevation (`#131A29`):** Primary card surfaces, bottom sheets, and nested transaction wrappers.
- **Surface Container High (`#1E293B`):** Ghost borders, structural dividers, chip outlines, and inactive control backgrounds.
- **Primary Cyber Cyan (`#06B6D4`):** Interactive focal points, primary action triggers, active tab indicators, and progress tracks.
- **Secondary Emerald Sentinel (`#10B981`):** Verified balances, confirmed transaction states, UTXO consolidation confirmations, and MWEB privacy shields.
- **Tertiary Amber Catalyst (`#F59E0B`):** 0-confirmation mempool warnings, RBF (Replace-by-Fee) triggers, high network congestion notices, and hardware key pairing alerts.
- **Crimson Hazard (`#EF4444`):** Key sweep warnings, private key exposure modals, and transaction reversion states.
- **Text & Data Tokens:**
  - **High-Emphasis:** `#F8FAFC` (Pure balance numerals, active headers)
  - **Mid-Emphasis:** `#94A3B8` (Labels, metadata, timestamps, address paths)
  - **Muted / Monospace:** `#64748B` (TXID hashes, script designations, UTXO indices)

## Typography

Typography prioritizes high-speed scannability and prevents numerical ambiguity:
- **Display & Financial Balance:** Headings rely on Geist for dense numerical kerning, clean glyph geometry, and authoritative presence.
- **UI Content & Inputs:** Body layers use Inter to preserve legibility across localized labels, instructional alerts, and multi-line recovery phrase flows.
- **Cryptographic & UTXO Telemetry:** JetBrains Mono is strictly enforced for public keys, Bech32/SegWit/Taproot addresses, derivation paths (`m/84'/0'/0'`), sat/vB fees, and TXID digests to guarantee alignment and prevent zero/O confusion.

All balance headlines feature tabular numbers (`tnum`) to eliminate micro-jittering during live fee and price recalculations.

## Layout & Spacing

This layout adheres to a strict 4px/8px incremental rhythm, optimized for one-handed thumb reach on modern handheld devices.

### Mobile Grid & Ergonomic Reach
- **Fluid Screen Columns:** 4-column system on mobile viewports with a persistent `16px` outer margin and `12px` interior gutters.
- **Bottom-Weighted Thumb Zone:** Critical interactions (Send, Receive, Scan QR, Fee Sliders, Confirm Slide) occupy the bottom 40% of the viewport. Balance cards, network status pills, and charts occupy the upper display tiers.
- **Touch Bounds:** All interactive elements must maintain a minimum physical bounding box of `48px x 48px` (`touch-target-min`), regardless of visual size.
- **Card Padding Cadence:** Internal card padding is locked to `16px` (`unit-base`) for balance and transaction summaries, scaling down to `12px` (`unit-md`) for compact UTXO inspector grids.

## Elevation & Depth

Elevation does not use traditional blurry dropshadows, which dilute deep dark interfaces. Instead, visual hierarchy is achieved through **Surface Tiering**, **Micro-Luminescent Glows**, and **Crisp Structural Borders**.

### Depth Layers
1. **Baseplate (`#0B0F17`):** The default window background.
2. **Layer 1 - Cards & Slates (`#131A29`):** Outlined with a 1px border of `rgba(30, 41, 59, 0.8)`. No shadow.
3. **Layer 2 - Active & Interactive Surfaces (`#162032`):** Outlined with a 1px border of `rgba(51, 65, 85, 0.9)`.
4. **Layer 3 - Floating Overlays & Bottom Sheets (`#1A2438`):** Elevated using a 1px top highlight `rgba(255, 255, 255, 0.08)` and an ambient, low-spread dark shadow: `0px 16px 32px -8px rgba(0, 0, 0, 0.75)`.

### Luminescent State Indicators
- **Cyan Focal Glow (Primary CTA):** `0px 0px 16px -2px rgba(6, 182, 212, 0.35)`
- **Emerald Guard (MWEB Privacy / Confirmed):** `0px 0px 12px -2px rgba(16, 185, 129, 0.3)`
- **Amber Warning (Mempool / Stale UTXO):** `0px 0px 12px -2px rgba(245, 158, 11, 0.25)`

## Shapes

The design uses balanced, modern curves that soften technical data while retaining structural rigor.

- **Standard Containers & Cards:** `16px` (`rounded-lg`) curvature provides a refined, friendly enclosure for dense telemetry.
- **Action Buttons & Key Inputs:** `12px` corner radii reinforce a sturdy, reliable target.
- **Status Tags, Protocol Pills, & Script Badges:** `9999px` full pill shapes differentiate non-interactive status badges from tappable modular cards.
- **Inner Nested Elements:** Nested items inside cards strictly employ an `8px` or `10px` radius to maintain concentric perimeter harmony.

## Components

### 1. Primary & Secondary Buttons
- **Primary ("Send", "Approve TX"):** Background of solid Primary Cyan (`#06B6D4`) with `#081018` bold typography. Height `52px`, `rounded-md` (12px), active state scales down to `0.98` with a cyan edge glow (`0px 0px 16px rgba(6, 182, 212, 0.4)`).
- **Secondary / Ghost ("Coin Control", "Cancel"):** Background `rgba(30, 41, 59, 0.4)`, 1px border of `#1E293B`, text in `#F8FAFC`.
- **Slide-to-Confirm:** Full-width friction slider for broadcast actions with an emerald track completion indicator.

### 2. Protocol Badges & Status Chips
- **Format:** Height `24px`, horizontal padding `8px`, typography `label-caps`.
- **Taproot (P2TR):** Surface `rgba(6, 182, 212, 0.1)`, text `#06B6D4`, border `1px solid rgba(6, 182, 212, 0.3)`.
- **SegWit (P2WPKH):** Surface `rgba(30, 41, 59, 0.6)`, text `#94A3B8`, border `1px solid #1E293B`.
- **MWEB Shield (Confidential UTXO):** Pulsing emerald icon, surface `rgba(16, 185, 129, 0.12)`, text `#10B981`, border `1px solid rgba(16, 185, 129, 0.4)`.
- **Mempool / Unconfirmed:** Surface `rgba(245, 158, 11, 0.1)`, text `#F59E0B`, border `1px solid rgba(245, 158, 11, 0.3)`.

### 3. Cards & Transaction List Rows
- **Card Scaffold:** Background `#131A29`, border `1px solid #1E293B`, radius `16px`.
- **Transaction Item:** Height `68px`. Left side displays directional vector glyphs (Incoming, Outgoing, Consolidation) wrapped in a `40px` circular `#1A2438` container. Center shows address/alias and time status. Right side displays tabular balance with colored signs (`+` in Emerald, `-` in High-Emphasis Slate).

### 4. Input Fields & Seed Phrase Tiles
- **Address & Amount Fields:** Height `56px`, background `#0D1420`, border `1px solid #1E293B`. Focused state transitions border to `#06B6D4` with a subtle inner glow. Includes trailing actions (Paste, Scan QR, Max Amount) in mono caps.
- **Recovery Phrase Cell:** Numbered tile (e.g., `01`), `JetBrains Mono` body text, background `#131A29`, border `1px solid #1E293B`. Masked tap-to-reveal state for shoulder-surfing protection.

### 5. Coin Control / UTXO Selector Row
- Compact `44px` selectable rows. Checkbox uses cyan check state with a `4px` radius. Monospace output value, sat/vB fee weight tag, and lock-UTXO quick toggle icon.