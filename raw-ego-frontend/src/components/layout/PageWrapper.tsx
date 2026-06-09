/**
 * PageWrapper.tsx
 *
 * Consistent max-width and horizontal padding container for all pages.
 * Ensures content never exceeds 1440px and has breathing room on all screen sizes.
 */

import { Box, type SxProps, type Theme } from '@mui/material';
import type { ReactNode } from 'react';

interface PageWrapperProps {
  children: ReactNode;
  sx?:      SxProps<Theme>;
  /** Remove max-width (for full-bleed sections like hero) */
  fullBleed?: boolean;
}

const PageWrapper = ({ children, sx, fullBleed = false }: PageWrapperProps) => {
  return (
    <Box
      sx={{
        maxWidth:  fullBleed ? 'none' : '1440px',
        mx:        'auto',
        px:        { xs: 2, sm: 3, md: 4, lg: 6, xl: 8 },
        width:     '100%',
        ...sx,
      }}
    >
      {children}
    </Box>
  );
};

export default PageWrapper;
