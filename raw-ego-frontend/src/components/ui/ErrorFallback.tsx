/**
 * ErrorFallback.tsx
 *
 * React ErrorBoundary fallback component.
 *
 * Usage:
 *   <ErrorBoundary FallbackComponent={ErrorFallback}>
 *     <SomePage />
 *   </ErrorBoundary>
 *
 * This catches runtime JavaScript errors (not network errors — those are
 * handled by TanStack Query's error state and the Axios interceptor).
 *
 * The "Try again" button calls resetErrorBoundary(), which re-renders
 * the children and attempts recovery.
 */

import { Box, Button, Typography } from '@mui/material';

interface ErrorFallbackProps {
  error:              Error;
  resetErrorBoundary: () => void;
}

const ErrorFallback = ({ error, resetErrorBoundary }: ErrorFallbackProps) => {
  return (
    <Box
      sx={{
        minHeight:      '60vh',
        display:        'flex',
        flexDirection:  'column',
        alignItems:     'center',
        justifyContent: 'center',
        textAlign:      'center',
        px:             4,
        gap:            3,
      }}
    >
      <Typography variant="metadata" >
        SOMETHING WENT WRONG
      </Typography>

      <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 400 }}>
        {error.message || 'An unexpected error occurred. Please try again.'}
      </Typography>

      <Button
        variant="contained"
        onClick={resetErrorBoundary}
        sx={{ mt: 2 }}
      >
        Try Again
      </Button>
    </Box>
  );
};

export default ErrorFallback;
