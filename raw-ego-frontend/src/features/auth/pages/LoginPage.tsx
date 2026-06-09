/**
 * LoginPage.tsx — Analog Archive Dark redesign
 *
 * Layout: Full-viewport split. Left = Obsidian form panel. Right = Carbon brand panel (desktop only).
 * Functional logic preserved exactly — only visual layer changed.
 */

import { Box, Typography, Divider } from '@mui/material';
import { Navigate, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useState } from 'react';
import { useAuthStore } from '@/store/authStore';
import LoginForm from '@/features/auth/components/LoginForm';

const LoginPage = () => {
  const { isAuthenticated } = useAuthStore();
  const [searchParams] = useSearchParams();
  const redirectTo = searchParams.get('redirect') ?? '/';
  const [isPasswordVisible, setIsPasswordVisible] = useState(false);

  if (isAuthenticated) {
    return <Navigate to={decodeURIComponent(redirectTo)} replace />;
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
        bgcolor: 'background.default',
      }}
    >
      {/* ── Form panel (left) ─────────────────────────────────────────── */}
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          px: { xs: 4, sm: 7, md: 9, lg: 12 },
          py: { xs: 10, md: 8 },
          bgcolor: 'background.default',
          borderRight: (theme) => ({ md: `1px solid ${theme.palette.border.default}` }),
        }}
      >
        <motion.div
          initial={{ opacity: 0, y: 28 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: [0.25, 0.46, 0.45, 0.94] }}
        >
          {/* Logo */}
          <Typography variant="productTitle" sx={{ mb: 10, fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', display: 'inline-block', transition: 'transform 300ms ease', transform: isPasswordVisible ? 'rotate(8deg) scale(1.4)' : 'none', transformOrigin: 'center left', }}>
            EGO
          </Typography>

          <Typography
            variant="overline"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 600,
              fontSize: '0.65rem',
              letterSpacing: '0.25em',
              color: 'text.secondary',
              display: 'block',
              mb: 2,
            }}
          >
            MEMBER ACCESS
          </Typography>

          <Typography
            variant="h2"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyDisplay,
              fontWeight: 700,
              fontSize: { xs: '2.2rem', md: '3rem' },
              color: 'text.primary',
              lineHeight: 1.05,
              mb: 1.5,
            }}
          >
            Welcome back.
          </Typography>

          <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 6, }}>
            Sign in to access your archive.
          </Typography>

          <Divider sx={{ mb: 5, borderColor: 'border.default' }} />

          <LoginForm onPasswordVisibilityChange={setIsPasswordVisible} />
        </motion.div>
      </Box>

      {/* ── Brand panel (right — desktop only) ────────────────────────── */}
      <Box
        sx={{
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'flex-end',
          bgcolor: 'surface.secondary',
          p: 8,
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
            opacity: 0.15,
            pointerEvents: 'none',
          }}
        />

        {/* Ghost EGO */}
        <Typography variant="hero" sx={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'transparent', WebkitTextStroke: (theme) => `1px ${theme.palette.border.default}`, userSelect: 'none', whiteSpace: 'nowrap', }}>
          EGO
        </Typography>

        {/* Bottom quote */}
        <motion.div
          initial={{ opacity: 0, x: 24 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 0.4, duration: 0.6 }}
        >
          <Typography
            variant="overline"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 600,
              fontSize: '0.6rem',
              letterSpacing: '0.25em',
              color: 'text.secondary',
              display: 'block',
              mb: 2,
            }}
          >
            ARCHIVE COLLECTION — 2025
          </Typography>
          <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', maxWidth: 360, fontStyle: 'italic', }}>
            Wear what moves you.
          </Typography>
        </motion.div>
      </Box>
    </Box>
  );
};

export default LoginPage;
