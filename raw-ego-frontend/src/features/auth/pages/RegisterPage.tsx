/**
 * RegisterPage.tsx — Analog Archive Dark redesign
 *
 * Same split-panel pattern as LoginPage. Right panel uses a vertical editorial tagline.
 * Functional logic preserved exactly.
 */

import { Box, Typography, Divider } from '@mui/material';
import { Navigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useState } from 'react';
import { useAuthStore } from '@/store/authStore';
import RegisterForm from '@/features/auth/components/RegisterForm';

const RegisterPage = () => {
  const { isAuthenticated } = useAuthStore();
  const [isPasswordVisible, setIsPasswordVisible] = useState(false);

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
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
      {/* ── Form panel ───────────────────────────────────────────────── */}
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          px: { xs: 4, sm: 7, md: 9, lg: 12 },
          py: { xs: 10, md: 8 },
          bgcolor: 'background.default',
          borderRight: (theme) => ({ md: `1px solid ${theme.palette.border.default}` }),
          overflowY: 'auto',
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
            CREATE ACCOUNT
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
            Join the archive.
          </Typography>

          <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 6, }}>
            Create your EGO account to start shopping.
          </Typography>

          <Divider sx={{ mb: 5, borderColor: 'border.default' }} />

          <RegisterForm onPasswordVisibilityChange={setIsPasswordVisible} />
        </motion.div>
      </Box>

      {/* ── Brand panel (right — desktop only) ───────────────────────── */}
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

        {/* Ghost text diagonal */}
        <Typography variant="hero" sx={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%) rotate(-15deg)', fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'transparent', WebkitTextStroke: (theme) => `1px ${theme.palette.border.default}`, userSelect: 'none', whiteSpace: 'nowrap', }}>
          NEW ERA
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
            EGO ARCHIVE — 2025
          </Typography>
          <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', maxWidth: 360, fontStyle: 'italic', }}>
            Your style.<br />Your rules.
          </Typography>
        </motion.div>
      </Box>
    </Box>
  );
};

export default RegisterPage;
