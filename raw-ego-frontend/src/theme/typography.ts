/**
 * typography.ts
 *
 * EGO Design System — Typography Scale
 *
 * Font choices:
 *   Playfair Display → Primary Display Serif (Hero Headings)
 *   Source Serif 4   → Secondary Editorial Serif (Body text, stories)
 *   Work Sans        → Primary Utility Sans-Serif (UI, Buttons, Metadata)
 */

import type { TypographyVariantsOptions } from '@mui/material/styles';

declare module '@mui/material/styles' {
  interface TypographyVariants {
    fontFamilyDisplay: React.CSSProperties['fontFamily'];
    fontFamilyEditorial: React.CSSProperties['fontFamily'];
    fontFamilyUtility: React.CSSProperties['fontFamily'];
    hero: React.CSSProperties;
    pageTitle: React.CSSProperties;
    sectionTitle: React.CSSProperties;
    productTitle: React.CSSProperties;
    bodyLarge: React.CSSProperties;
    metadata: React.CSSProperties;
    tableHeader: React.CSSProperties;
    badge: React.CSSProperties;
    buttonLabel: React.CSSProperties;
    navigationLabel: React.CSSProperties;
  }
  interface TypographyVariantsOptions {
    fontFamilyDisplay?: React.CSSProperties['fontFamily'];
    fontFamilyEditorial?: React.CSSProperties['fontFamily'];
    fontFamilyUtility?: React.CSSProperties['fontFamily'];
    hero?: React.CSSProperties;
    pageTitle?: React.CSSProperties;
    sectionTitle?: React.CSSProperties;
    productTitle?: React.CSSProperties;
    bodyLarge?: React.CSSProperties;
    metadata?: React.CSSProperties;
    tableHeader?: React.CSSProperties;
    badge?: React.CSSProperties;
    buttonLabel?: React.CSSProperties;
    navigationLabel?: React.CSSProperties;
  }
}

declare module '@mui/material/Typography' {
  interface TypographyPropsVariantOverrides {
    hero: true;
    pageTitle: true;
    sectionTitle: true;
    productTitle: true;
    bodyLarge: true;
    body: true;
    metadata: true;
    tableHeader: true;
    badge: true;
    buttonLabel: true;
    navigationLabel: true;
  }
}

export const typography: TypographyVariantsOptions = {
  fontFamily: '"Source Serif 4", serif',
  fontFamilyDisplay: '"Playfair Display", serif',
  fontFamilyEditorial: '"Source Serif 4", serif',
  fontFamilyUtility: '"Work Sans", sans-serif',

  // ── Display Layer (Playfair & Work Sans Mix) ──────────
  hero: {
    fontFamily: '"Playfair Display", serif',
    fontWeight: 700,
    fontSize: 'clamp(2.5rem, 5vw, 4rem)',
    lineHeight: 1.1,
    letterSpacing: '-0.02em',
  },
  pageTitle: {
    fontFamily: '"Playfair Display", serif',
    fontWeight: 700,
    fontSize: '2rem',
    lineHeight: 1.2,
    letterSpacing: '-0.01em',
  },
  sectionTitle: {
    fontFamily: '"Playfair Display", serif',
    fontWeight: 700,
    fontSize: '1.5rem',
    lineHeight: 1.2,
  },
  productTitle: {
    fontFamily: '"Work Sans", sans-serif',
    fontWeight: 700,
    fontSize: '1.25rem',
    lineHeight: 1.2,
    letterSpacing: '0.05em',
  },

  // ── Content Layer (Source Serif 4 & Work Sans) ────────
  bodyLarge: {
    fontFamily: '"Source Serif 4", serif',
    fontWeight: 400,
    fontSize: '1.25rem',
    lineHeight: 1.6,
  },
  body1: {
    fontFamily: '"Source Serif 4", serif',
    fontSize: '1rem',
    lineHeight: 1.6,
    fontWeight: 400,
  },
  body2: {
    fontFamily: '"Source Serif 4", serif',
    fontSize: '0.875rem',
    lineHeight: 1.6,
    fontWeight: 400,
  },
  metadata: {
    fontFamily: '"Work Sans", sans-serif',
    fontWeight: 600,
    fontSize: '0.85rem',
    lineHeight: 1.5,
    letterSpacing: '0.05em',
  },
  caption: {
    fontFamily: '"Work Sans", sans-serif',
    fontWeight: 500,
    fontSize: '0.75rem',
    lineHeight: 1.4,
    letterSpacing: '0.05em',
  },

  // ── UI Layer (Work Sans) ──────────────────────────────
  tableHeader: {
    fontFamily: '"Work Sans", sans-serif',
    fontWeight: 600,
    fontSize: '0.875rem',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  badge: {
    fontFamily: '"Work Sans", sans-serif',
    fontWeight: 700,
    fontSize: '0.65rem',
    textTransform: 'uppercase',
    letterSpacing: '0.2em',
  },
  buttonLabel: {
    fontFamily: '"Work Sans", sans-serif',
    fontWeight: 600,
    fontSize: '0.75rem',
    textTransform: 'uppercase',
    letterSpacing: '0.1em',
  },
  navigationLabel: {
    fontFamily: '"Work Sans", sans-serif',
    fontWeight: 600,
    fontSize: '0.875rem',
    letterSpacing: '0.05em',
  },

  // (Optional Fallbacks / MUI defaults that remain)
  h1: undefined,
  h2: undefined,
  h3: undefined,
  h4: undefined,
  h5: undefined,
  h6: undefined,
  subtitle1: undefined,
  subtitle2: undefined,
  button: undefined,
  overline: undefined,
};
