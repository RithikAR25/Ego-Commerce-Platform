/**
 * palette.ts
 *
 * EGO Design System — Modern Premium Aesthetic
 */

import type { PaletteOptions } from '@mui/material/styles';

declare module '@mui/material/styles' {
  interface Palette {
    surface: {
      primary: string;
      secondary: string;
      tertiary: string;
    };
    border: {
      default: string;
      subtle: string;
      strong: string;
    };
    brand: {
      primary: string;
      secondary: string;
    };
    state: {
      success: string;
      warning: string;
      error: string;
      info: string;
    };
    overlay: {
      light: string;
      main: string;
      dark: string;
    };
    statusColors: Record<string, string>;
  }
  interface PaletteOptions {
    surface?: {
      primary?: string;
      secondary?: string;
      tertiary?: string;
    };
    border?: {
      default?: string;
      subtle?: string;
      strong?: string;
    };
    brand?: {
      primary?: string;
      secondary?: string;
    };
    state?: {
      success?: string;
      warning?: string;
      error?: string;
      info?: string;
    };
    overlay?: {
      light?: string;
      main?: string;
      dark?: string;
    };
    statusColors?: Record<string, string>;
  }
}

export const darkPalette: PaletteOptions = {
  mode: 'dark',
  background: {
    default: '#121212', // Obsidian
    paper: '#121212',
  },
  text: {
    primary: '#FBF9F4', // Off-White
    secondary: '#8C8375', // Muted Bronze
    disabled: '#5E5E5B', 
  },
  primary: {
    main: '#FBF9F4',
    contrastText: '#121212',
  },
  secondary: {
    main: '#8C8375',
    contrastText: '#FBF9F4',
  },
  error: { main: '#CF6679' },
  warning: { main: '#FFB74D' },
  success: { main: '#81C784' },
  divider: '#3D3D3D',
  
  // Custom Semantic Tokens
  surface: {
    primary: '#121212',
    secondary: '#1A1A1A',
    tertiary: '#2A2A2A',
  },
  border: {
    default: '#3D3D3D',
    subtle: 'rgba(140, 131, 117, 0.15)', // Same as #8C8375 with 15% opacity
    strong: '#FBF9F4',
  },
  brand: {
    primary: '#FBF9F4',
    secondary: '#8C8375',
  },
  state: {
    success: '#6B8E6B', // Olive/Sage
    warning: '#A68A56', // Muted Gold
    error: '#A85C5C',   // Terracotta/Muted Red
    info: '#5C7285',    // Slate Blue
  },
  overlay: {
    light: 'rgba(0, 0, 0, 0.2)',
    main: 'rgba(0, 0, 0, 0.5)',
    dark: 'rgba(0, 0, 0, 0.8)',
  },
  statusColors: {
    PENDING_PAYMENT: '#A68A56',
    REQUESTED:       '#A68A56',
    CONFIRMED:       '#5C7285',
    PROCESSING:      '#7A6F8F',
    SHIPPED:         '#5C8A8A',
    DELIVERED:       '#6B8E6B',
    APPROVED:        '#6B8E6B',
    REFUND_COMPLETED:'#6B8E6B',
    CANCELLED:       '#A85C5C',
    REJECTED:        '#A85C5C',
    REFUNDED:        '#7A7571',
    REFUND_INITIATED:'#7A7571',
    ACTIVE:          '#6B8E6B',
    DRAFT:           '#7A7571',
    ARCHIVED:        '#A68A56',
    OUT_OF_STOCK:    '#A85C5C',
    SUSPENDED:       '#A85C5C',
    INACTIVE:        '#7A7571',
    ADMIN:           '#5C7285',
    USER:            '#8C8375',
  }
};

// Analog Archive Dark is exclusively dark mode
export const lightPalette: PaletteOptions = darkPalette;

export const palette = darkPalette;
