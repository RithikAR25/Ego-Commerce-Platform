/**
 * ForbiddenPage.tsx — 403 — Analog Archive Dark redesign
 * Shown when an authenticated CUSTOMER tries to access an ADMIN route.
 */
import { Box, Typography, Button } from '@mui/material';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';

const ForbiddenPage = () => (
  <Box
    sx={{
      minHeight: '80vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      textAlign: 'center',
      px: 4,
      bgcolor: 'background.default',
      position: 'relative',
      overflow: 'hidden',
    }}
  >
    {/* Grid texture */}
    <Box
      sx={{
        position: 'absolute',
        inset: 0,
        backgroundImage: (theme) =>
          `repeating-linear-gradient(0deg, transparent, transparent 59px, ${theme.palette.border.default} 60px), repeating-linear-gradient(90deg, transparent, transparent 59px, ${theme.palette.border.default} 60px)`,
        opacity: 0.1,
        pointerEvents: 'none',
      }}
    />

    <motion.div
      initial={{ opacity: 0, y: 32 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, ease: 'easeOut' }}
      style={{ position: 'relative', zIndex: 1 }}
    >
      {/* Ghost 403 */}
      <Typography
        sx={{
          fontFamily: (theme) => theme.typography.fontFamilyDisplay,
          fontWeight: 800,
          fontSize: { xs: '8rem', md: '16rem' },
          lineHeight: 0.85,
          color: 'transparent',
          WebkitTextStroke: (theme) => ({ xs: `1px ${theme.palette.surface.tertiary}`, md: `2px ${theme.palette.surface.tertiary}` }),
          userSelect: 'none',
        }}
      >
        403
      </Typography>

      <Typography
        variant="overline"
        sx={{
          fontFamily: (theme) => theme.typography.fontFamilyUtility,
          fontWeight: 700,
          fontSize: '0.65rem',
          letterSpacing: '0.3em',
          color: 'text.secondary',
          display: 'block',
          mt: 3,
          mb: 2,
        }}
      >
        ACCESS DENIED
      </Typography>

      <Typography
        sx={{
          fontFamily: (theme) => theme.typography.fontFamilyDisplay,
          fontWeight: 700,
          fontSize: { xs: '1.6rem', md: '2.5rem' },
          color: 'text.primary',
          lineHeight: 1.1,
          mb: 3,
        }}
      >
        You can't be here.
      </Typography>

      <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 6, maxWidth: 400, mx: 'auto', }}>
        You don't have permission to view this page.
      </Typography>

      <Button
        component={Link}
        to="/"
        variant="contained"
        size="large"
        sx={{ px: 6, py: 1.75 }}
      >
        Back to Home
      </Button>
    </motion.div>
  </Box>
);

export default ForbiddenPage;
