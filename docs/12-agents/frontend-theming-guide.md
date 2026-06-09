# Frontend Theming & Design Modification Guide

> **Target Audience:** AI Agents (LLMs) tasked with redesigning or modifying the frontend theme of the EGO E-Commerce platform.

## 1. Architectural Overview

The frontend is built with **React 19, Vite, and Material UI (MUI) v6+**. 
It **does NOT use Tailwind CSS**. All styling is controlled via MUI's `ThemeProvider` and CSS-in-JS (Emotion).

If you are asked to "change the theme", "make it look different", or "redesign the app", **do not manually add `className` or inline styles to hundreds of components**. Instead, modify the centralized theme token files.

## 2. The Theme Directory (`src/theme/`)

The entire visual identity of the app is controlled by four files in `raw-ego-frontend/src/theme/`:

1. **`palette.ts`**: Defines all colors.
2. **`typography.ts`**: Defines font families, sizes, weights, and scale (`h1`-`h6`, `body1`, `button`).
3. **`theme.ts`**: The master file. It imports palette and typography, overrides MUI default behaviors (like `elevation`), and contains **Component Style Overrides** for every MUI component.
4. **`constants.ts`**: Stores fixed measurements (e.g., `NAVBAR_HEIGHT`, `SIDEBAR_WIDTH`).

## 3. Current Design Philosophy (The "EGO" Look)

Before changing things, understand the current baseline. EGO is designed as a **premium streetwear brand** (think Zara, Off-White, Balenciaga).
- **Square Corners:** `shape: { borderRadius: 0 }`. Everything is sharp.
- **No Shadows:** `elevation: 0` is set globally. Shadows look dated; borders (`1px solid #E8E8E8`) are used for separation instead.
- **Monochrome Dominant:** Primary color is Black (`#0A0A0A`). Secondary is Off-White (`#FAFAFA`).
- **Typography:** `Syne` for headers (wide, bold, uppercase). `Manrope` for body text.

## 4. How to Completely Redesign the App

To execute a global redesign, follow these steps in order:

### Step 1: Change the Colors (`palette.ts`)
Modify `palette.ts`. 
- To make it a "dark mode" app, swap `background.default` to `#121212`, `text.primary` to `#FFFFFF`, and adjust the `primary` object.
- To make it "playful", change the `primary.main` to a vibrant color (e.g., Purple or Blue).
- **Important:** Ensure `contrastText` always maintains accessibility (use white text on dark primary, black text on light primary).

### Step 2: Change the Fonts (`typography.ts`)
Modify `typography.ts`.
- Update `fontFamily` strings. (Note: You must also update the Google Fonts import in `raw-ego-frontend/index.html` or `src/index.css` to load the new web fonts).
- Adjust the `clamp()` values for responsive headers.
- Change `textTransform: 'uppercase'` to `'none'` if the new design should be standard sentence-case.

### Step 3: Change the Shapes & Shadows (`theme.ts`)
Modify the `createTheme` object in `theme.ts`.
- **To make it "bubbly" / friendly:** Change `shape: { borderRadius: 0 }` to `shape: { borderRadius: 12 }` or `16`.
- **To add depth/shadows:** Remove `defaultProps: { elevation: 0 }` from `MuiCard`, `MuiPaper`, and `MuiAppBar`. Add custom `boxShadow` overrides if standard MUI shadows are too heavy.

### Step 4: Override Component Specifics (`theme.ts`)
Look inside the `components: { ... }` block in `theme.ts`.
- **Buttons (`MuiButton`):** Change the padding, borders, or hover states. Remove the uppercase transform if needed.
- **Inputs (`MuiOutlinedInput`):** Change how text fields look (e.g., make them filled instead of outlined by overriding `MuiFilledInput` and changing default props).
- **App Bar (`MuiAppBar`):** Change the backdrop blur or border bottom.

## 5. Anti-Patterns (What NOT to do)

- **❌ DO NOT inject Tailwind.** The project does not have Tailwind configured. Relying on utility classes will break consistency.
- **❌ DO NOT use inline styles.** Example: `style={{ backgroundColor: 'red' }}`. Always use the `sx` prop linked to the theme: `sx={{ bgcolor: 'error.main' }}`.
- **❌ DO NOT manually style `<button>` or `<input>`.** Always use MUI `<Button>` and `<TextField>`. The theme engine will automatically style them.
- **❌ DO NOT hardcode colors in components.** Use `palette.text.secondary` instead of `color: '#6B6B6B'`.

## 6. Example: Switching from "Streetwear" to "Friendly SaaS"

If requested to make the app look like a modern, friendly SaaS platform, an LLM should immediately:
1. Open `theme.ts` → Set `borderRadius: 8`.
2. Open `palette.ts` → Change `primary.main` to a bright blue (`#2563EB`) and `background.default` to pure white (`#FFFFFF`).
3. Open `typography.ts` → Remove `Syne`, use `Inter` globally. Remove `textTransform: 'uppercase'` from buttons and headers.
4. Open `theme.ts` → Add a soft `boxShadow: '0 4px 12px rgba(0,0,0,0.05)'` to `MuiCard` and `MuiPaper`, removing the hard borders.

By following this guide, a complete visual overhaul can be achieved by editing just 3-4 files in the `src/theme/` directory.
