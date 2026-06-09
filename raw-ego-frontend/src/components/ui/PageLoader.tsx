/**
 * PageLoader.tsx
 *
 * Full-page loading state for Suspense fallbacks (lazy-loaded routes).
 * Shows a minimal, premium loader that matches the brand aesthetic.
 *
 * Usage:
 *   <Suspense fallback={<PageLoader />}>
 *     <LazyPage />
 *   </Suspense>
 */

import { Box } from '@mui/material';
import { motion } from 'framer-motion';

const PageLoader = () => {
  return (
    <Box
      sx={{
        position:       'fixed',
        inset:          0,
        display:        'flex',
        alignItems:     'center',
        justifyContent: 'center',
        backgroundColor: 'background.default',
        zIndex:          9999,
      }}
    >
      <motion.div
        animate={{ opacity: [0.3, 1, 0.3] }}
        transition={{ duration: 1.4, repeat: Infinity, ease: 'easeInOut' }}
      >
        <Box
          component="span"
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyDisplay,
            fontWeight:    800,
            fontSize:      '2rem',
            letterSpacing: '0.15em',
            textTransform: 'uppercase',
            color:         'text.primary',
          }}
        >
          EGO
        </Box>
      </motion.div>
    </Box>
  );
};

export default PageLoader;
