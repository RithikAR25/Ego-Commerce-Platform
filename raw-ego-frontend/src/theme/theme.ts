import { createTheme } from '@mui/material/styles';
import type { PaletteMode } from '@mui/material/styles';
import { darkPalette, lightPalette } from './palette';
import { typography } from './typography';

export const getEgoTheme = (mode: PaletteMode) => {
  const currentPalette = mode === 'dark' ? darkPalette : lightPalette;

  const baseTheme = createTheme({
    palette: currentPalette,
    typography,
    shape: { borderRadius: 0 }, // Sharp corners
  });

  return createTheme(baseTheme, {
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          '*': {
            boxSizing: 'border-box',
            margin: 0,
            padding: 0,
          },
          html: { scrollBehavior: 'smooth' },
          body: {
            backgroundColor: baseTheme.palette.background.default,
            color: baseTheme.palette.text.primary,
            WebkitFontSmoothing: 'antialiased',
            MozOsxFontSmoothing: 'grayscale',
          },
          'ul, ol': { listStyle: 'none' },
          '::-webkit-scrollbar': { width: '6px', height: '6px' },
          '::-webkit-scrollbar-track': { background: 'transparent' },
          '::-webkit-scrollbar-thumb': {
            background: baseTheme.palette.divider,
            borderRadius: '0px',
          },
          '::-webkit-scrollbar-thumb:hover': {
            background: baseTheme.palette.text.secondary,
          },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: {
            borderRadius: '0px',
            padding: '12px 24px',
            textTransform: 'uppercase',
            fontWeight: 500,
            fontFamily: '"Work Sans", sans-serif',
            letterSpacing: '0.1em',
            transition: 'all 0.2s ease-in-out',
            boxShadow: 'none',
            '&:hover': {
              boxShadow: 'none',
              transform: 'none',
            },
          },
          containedPrimary: {
            background: baseTheme.palette.primary.main,
            color: baseTheme.palette.primary.contrastText,
            '&:hover': {
              background: '#E4E2DD',
            },
          },
          outlined: {
            borderColor: baseTheme.palette.divider,
            color: baseTheme.palette.text.primary,
            '&:hover': {
              borderColor: baseTheme.palette.text.primary,
              background: 'transparent',
            },
          },
          sizeLarge: { padding: '16px 32px', fontSize: '0.875rem' },
          sizeSmall: { padding: '8px 16px', fontSize: '0.75rem' },
        },
      },
      MuiIconButton: {
        styleOverrides: {
          root: {
            borderRadius: '0px',
            transition: 'all 0.2s ease-in-out',
          },
        },
      },
      MuiCard: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            borderRadius: '0px',
            background: 'transparent',
            border: 'none',
            borderTop: `1px solid ${baseTheme.palette.divider}`,
            borderBottom: `1px solid ${baseTheme.palette.divider}`,
            boxShadow: 'none',
            transition: 'background 0.2s ease-in-out',
            '&:hover': {
              background: '#1A1A1A', // Tonal shift
              boxShadow: 'none',
              transform: 'none',
            },
          },
        },
      },
      MuiCardContent: {
        styleOverrides: {
          root: {
            padding: '24px',
            '&:last-child': { paddingBottom: '24px' },
          },
        },
      },
      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            borderRadius: '0px',
            backgroundImage: 'none',
            boxShadow: 'none',
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: '0px',
            transition: 'all 0.2s ease-in-out',
            backgroundColor: 'transparent',
            '& .MuiOutlinedInput-notchedOutline': {
              borderColor: baseTheme.palette.divider,
              borderWidth: '0 0 1px 0', // bottom border only
            },
            '&:hover .MuiOutlinedInput-notchedOutline': {
              borderColor: baseTheme.palette.text.primary,
            },
            '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
              borderColor: baseTheme.palette.primary.main,
              borderWidth: '0 0 2px 0',
            },
          },
          input: {
            padding: '16px 0px 8px 0px',
          },
        },
      },
      MuiInputLabel: {
        styleOverrides: {
          root: {
            fontFamily: '"Work Sans", sans-serif',
            fontSize: '0.75rem',
            letterSpacing: '0.05em',
            textTransform: 'uppercase',
            transform: 'translate(0, 16px) scale(1)', // Align with standard input
            '&.MuiInputLabel-shrink': {
              transform: 'translate(0, -4px) scale(0.85)',
            },
          },
        },
      },
      MuiAppBar: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            backgroundColor: baseTheme.palette.background.default,
            backdropFilter: 'none',
            borderBottom: `1px solid ${baseTheme.palette.divider}`,
            color: baseTheme.palette.text.primary,
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            borderRadius: '0px',
            fontFamily: '"Work Sans", sans-serif',
            fontWeight: 500,
            fontSize: '0.625rem',
            letterSpacing: '0.05em',
            textTransform: 'uppercase',
            backgroundColor: 'transparent',
            border: `1px solid ${baseTheme.palette.divider}`,
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: '0px',
            padding: '24px',
            boxShadow: 'none',
            border: `1px solid ${baseTheme.palette.divider}`,
            backgroundColor: baseTheme.palette.background.default,
          },
        },
      },
      MuiDialogTitle: {
        styleOverrides: {
          root: {
            fontFamily: '"Playfair Display", serif',
            fontWeight: 600,
          },
        },
      },
      MuiDrawer: {
        styleOverrides: {
          paper: {
            borderRadius: '0px',
            border: 'none',
            borderLeft: `1px solid ${baseTheme.palette.divider}`,
            backgroundColor: baseTheme.palette.background.default,
          },
        },
      },
    },
  });
};

export default getEgoTheme;
