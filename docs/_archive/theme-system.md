# Theme System — EGO Platform Frontend

> **Library:** Material UI (MUI) v6+  
> **Philosophy:** Fully override MUI defaults — zero corporate/dashboard look  
> **Aesthetic:** Premium streetwear · editorial · Gen-Z · Zara × Bonkers Corner × Myntra

---

## 1. Core Design Tokens

### Color Palette (`theme/palette.ts`)

```typescript
export const palette = {
  // Backgrounds
  background: {
    default: '#FAFAFA',   // Off-white — softer than pure white
    paper:   '#FFFFFF',
    dark:    '#121212',   // Dark mode surface
  },

  // Text
  text: {
    primary:   '#0A0A0A',   // Near-black — editorial Zara feel
    secondary: '#6B6B6B',   // Muted labels
    disabled:  '#BDBDBD',
  },

  // Brand / Action
  primary: {
    main:    '#0A0A0A',   // Black as primary — premium minimal
    light:   '#333333',
    dark:    '#000000',
    contrastText: '#FFFFFF',
  },

  // Hype accent (used sparingly — sale badges, CTA highlights)
  accent: {
    hype:    '#FF3E6C',   // Myntra-esque coral-pink (sale, hot)
    cyber:   '#CCFF00',   // Electric cyber-lime (Bonkers drops)
    success: '#00C853',
  },

  // Borders / Dividers
  divider: '#E8E8E8',   // Ultra-thin — no heavy lines
};
```

### Typography (`theme/typography.ts`)

```typescript
// Import in index.html or via @fontsource
// Syne: editorial headers | Manrope: clean UI body

export const typography = {
  fontFamily: '"Manrope", "Inter", sans-serif',

  h1: { fontFamily: '"Syne", sans-serif', fontWeight: 800, fontSize: 'clamp(2.5rem, 6vw, 5rem)', letterSpacing: '-0.03em', textTransform: 'uppercase' as const },
  h2: { fontFamily: '"Syne", sans-serif', fontWeight: 700, fontSize: 'clamp(2rem, 4vw, 3.5rem)', letterSpacing: '-0.02em' },
  h3: { fontFamily: '"Syne", sans-serif', fontWeight: 700, fontSize: 'clamp(1.5rem, 3vw, 2.5rem)' },
  h4: { fontFamily: '"Syne", sans-serif', fontWeight: 600, fontSize: 'clamp(1.25rem, 2.5vw, 2rem)' },
  h5: { fontFamily: '"Syne", sans-serif', fontWeight: 600, fontSize: '1.25rem' },
  h6: { fontFamily: '"Syne", sans-serif', fontWeight: 600, fontSize: '1rem' },

  subtitle1: { fontWeight: 600, fontSize: '0.875rem', letterSpacing: '0.1em', textTransform: 'uppercase' as const },
  subtitle2: { fontWeight: 500, fontSize: '0.75rem', letterSpacing: '0.08em', textTransform: 'uppercase' as const },

  body1: { fontSize: '1rem',    lineHeight: 1.6 },
  body2: { fontSize: '0.875rem', lineHeight: 1.5 },

  button: { fontWeight: 700, letterSpacing: '0.12em', textTransform: 'uppercase' as const },
  caption: { fontSize: '0.75rem', letterSpacing: '0.04em' },
};
```

---

## 2. MUI Theme Config (`theme/theme.ts`)

```typescript
import { createTheme } from '@mui/material/styles';
import { palette } from './palette';
import { typography } from './typography';

const theme = createTheme({
  palette: {
    mode: 'light',
    background: palette.background,
    text:        palette.text,
    primary:     palette.primary,
    divider:     palette.divider,
  },
  typography,
  shape: { borderRadius: 0 },  // Square corners — editorial, not bubbly

  // Spacing: 1 unit = 8px (MUI default)
  // Use generously — macro-spacing is key to the Gen-Z premium feel

  components: {
    // ── Button ──────────────────────────────────────────────
    MuiButton: {
      defaultProps:  { disableElevation: true, disableRipple: false },
      styleOverrides: {
        root: {
          borderRadius: 0,
          padding:      '14px 32px',
          fontSize:     '0.8rem',
          letterSpacing:'0.15em',
          transition:   'all 0.2s ease',
          '&:hover': { transform: 'translateY(-1px)' },
          '&:active': { transform: 'scale(0.97)' },
        },
        contained: {
          background: palette.primary.main,
          color:      palette.primary.contrastText,
          '&:hover':  { background: palette.primary.light },
        },
        outlined: {
          borderWidth: '1.5px',
          '&:hover':   { borderWidth: '1.5px' },
        },
      },
    },

    // ── Card ────────────────────────────────────────────────
    MuiCard: {
      defaultProps:  { elevation: 0 },
      styleOverrides: {
        root: {
          border:       `1px solid ${palette.divider}`,
          borderRadius: 0,
          overflow:     'hidden',
          transition:   'border-color 0.2s ease',
          '&:hover':    { borderColor: palette.text.primary },
        },
      },
    },

    // ── Input ───────────────────────────────────────────────
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 0,
          '& fieldset': { borderColor: palette.divider },
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: palette.text.primary,
          },
        },
      },
    },

    // ── AppBar ──────────────────────────────────────────────
    MuiAppBar: {
      defaultProps:    { elevation: 0, position: 'sticky' },
      styleOverrides: {
        root: {
          backgroundColor: 'rgba(250,250,250,0.95)',
          backdropFilter:  'blur(10px)',
          borderBottom:    `1px solid ${palette.divider}`,
          color:           palette.text.primary,
        },
      },
    },

    // ── Drawer ──────────────────────────────────────────────
    MuiDrawer: {
      styleOverrides: {
        paper: {
          borderRadius: 0,
          border:       'none',
          boxShadow:    '-4px 0 24px rgba(0,0,0,0.08)',
        },
      },
    },

    // ── Chip ────────────────────────────────────────────────
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: 0,
          fontWeight:   600,
          letterSpacing:'0.05em',
          fontSize:     '0.75rem',
        },
      },
    },

    // ── Divider ─────────────────────────────────────────────
    MuiDivider: {
      styleOverrides: {
        root: { borderColor: palette.divider },
      },
    },

    // ── Remove all default shadows ───────────────────────────
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: {
          backgroundImage: 'none',
        },
      },
    },
  },
});

export default theme;
```

---

## 3. Animation Tokens (Framer Motion)

Framer Motion is used for all transitions and micro-interactions. MUI transitions are minimal (only for MUI components like Drawer, Dialog).

```typescript
// theme/animations.ts
export const fadeUpVariants = {
  hidden:  { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.25, 0.46, 0.45, 0.94] } },
};

export const staggerContainer = {
  visible: { transition: { staggerChildren: 0.07 } },
};

export const scaleOnTap = {
  whileTap: { scale: 0.96 },
  transition: { duration: 0.1 },
};

export const slideInRight = {
  initial:  { x: '100%' },
  animate:  { x: 0, transition: { duration: 0.35, ease: [0.25, 0.46, 0.45, 0.94] } },
  exit:     { x: '100%', transition: { duration: 0.25 } },
};

// Product grid: staggered fade-up on load
export const productGridAnimation = {
  container: staggerContainer,
  item:      fadeUpVariants,
};
```

---

## 4. Responsive Breakpoints

Using MUI's default breakpoints. All layouts are mobile-first.

| Breakpoint | Width | Layout notes |
|---|---|---|
| `xs` | 0px | Single column, full-width nav |
| `sm` | 600px | 2-column product grid |
| `md` | 900px | 3-column grid, filter sidebar shows |
| `lg` | 1200px | 4-column grid, wider margins |
| `xl` | 1536px | Max content width: 1440px |

```typescript
// Max content container: never wider than 1440px
const pageMaxWidth = '1440px';
```

---

## 5. Z-Index Scale

```typescript
export const zIndex = {
  cartDrawer:       1300,
  navbar:           1200,
  modal:            1400,
  toast:            1500,
  razorpayModal:    1600, // Razorpay renders its own iframe
};
```
