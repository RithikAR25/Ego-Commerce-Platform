/**
 * ForgotPasswordPage.tsx — Analog Archive Dark redesign
 *
 * Same split-panel as Login/Register. All API logic preserved exactly.
 */

import { useState } from 'react';
import {
  Box, Typography, TextField, Button, CircularProgress,
  Alert, Paper, Divider,
} from '@mui/material';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { forgotPassword } from '@/api/auth.api';

const ForgotPasswordPage = () => {
  const [email,   setEmail]   = useState('');
  const [loading, setLoading] = useState(false);
  const [sent,    setSent]    = useState(false);
  const [error,   setError]   = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) { setError('Please enter your email address.'); return; }

    setLoading(true);
    setError(null);
    try {
      await forgotPassword(email.trim().toLowerCase());
      setSent(true);
    } catch {
      setError('Something went wrong. Please try again in a moment.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
        bgcolor: 'background.default',
      }}
    >
      {/* ── Form panel ──────────────────────────────────────────────────── */}
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
          <Typography variant="productTitle" sx={{ mb: 10, fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', }}>
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
            ACCOUNT RECOVERY
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
            Forgot password?
          </Typography>

          <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 6, }}>
            Enter your email and we'll send you a reset link.
          </Typography>

          <Divider sx={{ mb: 5, borderColor: 'border.default' }} />

          {/* ── Success state ──────────────────────────────────────────── */}
          {sent ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.97 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4 }}
            >
              <Paper
                elevation={0}
                sx={{
                  border: '1px solid',
                  borderColor: 'success.main',
                  borderRadius: 0,
                  p: 3,
                  mb: 3,
                  bgcolor: 'transparent',
                }}
              >
                <Typography
                  variant="overline"
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.65rem',
                    letterSpacing: '0.2em',
                    color: 'success.main',
                    display: 'block',
                    mb: 1,
                  }}
                >
                  CHECK YOUR INBOX
                </Typography>
                <Typography
                  variant="body2"
                  sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}
                >
                  If <strong style={{ color: 'var(--mui-palette-text-primary)' }}>{email}</strong> is registered, a password reset link has been sent. The link expires in 1 hour.
                </Typography>
              </Paper>
              <Typography
                variant="body2"
                sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}
              >
                Didn't receive it? Check your spam or{' '}
                <Box
                  component="span"
                  onClick={() => setSent(false)}
                  sx={{
                    fontWeight: 700,
                    cursor: 'pointer',
                    color: 'text.primary',
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontSize: '0.8rem',
                    letterSpacing: '0.05em',
                    '&:hover': { textDecoration: 'underline' },
                  }}
                >
                  try again
                </Box>
                .
              </Typography>
            </motion.div>
          ) : (
            <Box
              component="form"
              onSubmit={handleSubmit}
              noValidate
              sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}
            >
              {error && (
                <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}>
                  <Alert severity="error" sx={{ borderRadius: 0, fontSize: '0.85rem' }}>
                    {error}
                  </Alert>
                </motion.div>
              )}

              <TextField
                id="forgot-password-email"
                label="Email Address"
                type="email"
                autoComplete="email"
                autoFocus
                fullWidth
                value={email}
                onChange={e => setEmail(e.target.value)}
                slotProps={{ htmlInput: { 'aria-label': 'Email Address' } }}
              />

              <motion.div whileTap={{ scale: 0.98 }}>
                <Button
                  id="forgot-password-submit"
                  type="submit"
                  variant="contained"
                  fullWidth
                  disabled={loading}
                  sx={{ py: 1.75, mt: 0.5 }}
                >
                  {loading ? <CircularProgress size={20} color="inherit" /> : 'Send Reset Link'}
                </Button>
              </motion.div>
            </Box>
          )}

          <Box sx={{ mt: 5 }}>
            <Typography
              variant="body2"
              sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}
            >
              Remember your password?{' '}
              <Link to="/login" style={{ textDecoration: 'none' }}>
                <Typography
                  component="span"
                  variant="body2"
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.8rem',
                    letterSpacing: '0.05em',
                    color: 'text.primary',
                    '&:hover': { textDecoration: 'underline' },
                  }}
                >
                  Back to login →
                </Typography>
              </Link>
            </Typography>
          </Box>
        </motion.div>
      </Box>

      {/* ── Brand panel (desktop only) ──────────────────────────────────── */}
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
        <Typography variant="hero" sx={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'transparent', WebkitTextStroke: (theme) => `1px ${theme.palette.border.default}`, userSelect: 'none', whiteSpace: 'nowrap', }}>
          EGO
        </Typography>
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
            ACCOUNT RECOVERY
          </Typography>
          <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', maxWidth: 360, fontStyle: 'italic', }}>
            We'll get you back in.
          </Typography>
        </motion.div>
      </Box>
    </Box>
  );
};

export default ForgotPasswordPage;
