/**
 * ResetPasswordPage.tsx — Analog Archive Dark redesign
 *
 * All password validation logic, token handling, and API calls preserved exactly.
 * Only visual layer changed to match design system.
 */

import { useState } from 'react';
import {
  Box, Typography, TextField, Button, CircularProgress,
  Alert, Divider, LinearProgress,
} from '@mui/material';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useTheme } from '@mui/material';
import { resetPassword } from '@/api/auth.api';

// ── Password strength scorer ───────────────────────────────────────────────────
const scorePassword = (pw: string): { score: number; label: string; colorPath: string } => {
  if (!pw) return { score: 0, label: '', colorPath: 'transparent' };
  let score = 0;
  if (pw.length >= 8)  score++;
  if (pw.length >= 12) score++;
  if (/[0-9]/.test(pw)) score++;
  if (/[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(pw)) score++;
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;

  if (score <= 1) return { score: 20,  label: 'Weak',        colorPath: 'error.main' };
  if (score === 2) return { score: 40,  label: 'Fair',        colorPath: 'warning.main' };
  if (score === 3) return { score: 65,  label: 'Good',        colorPath: 'warning.main' };
  if (score === 4) return { score: 85,  label: 'Strong',      colorPath: 'success.main' };
  return               { score: 100, label: 'Very strong', colorPath: 'success.main' };
};

// Shared brand panel (reused across all auth pages)
const BrandPanel = ({ quote }: { quote: string }) => (
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
        EGO ARCHIVE — 2025
      </Typography>
      <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', maxWidth: 360, fontStyle: 'italic', }}>
        {quote}
      </Typography>
    </motion.div>
  </Box>
);

const ResetPasswordPage = () => {
  const [searchParams]         = useSearchParams();
  const navigate               = useNavigate();
  const theme = useTheme();
  const token                  = searchParams.get('token') ?? '';

  const [newPassword,     setNewPassword]     = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading,         setLoading]         = useState(false);
  const [error,           setError]           = useState<string | null>(null);
  const [fieldError,      setFieldError]      = useState<string | null>(null);

  const strength = scorePassword(newPassword);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldError(null);

    if (!token) { setError('Invalid reset link. Please request a new one.'); return; }
    if (newPassword.length < 8) { setFieldError('Password must be at least 8 characters.'); return; }
    if (!/[0-9!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(newPassword)) {
      setFieldError('Password must include at least one digit or special character.');
      return;
    }
    if (newPassword !== confirmPassword) { setFieldError('Passwords do not match.'); return; }

    setLoading(true);
    try {
      await resetPassword(token, newPassword);
      navigate('/login?reset=success', { replace: true });
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      const msg = axiosErr?.response?.data?.message ?? 'Something went wrong. Please try again.';
      if (msg.toLowerCase().includes('expired') || msg.toLowerCase().includes('invalid')) {
        setError('This reset link has expired or is invalid. Please request a new one.');
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  // No token in URL at all
  if (!token) {
    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: 'background.default',
          p: 4,
        }}
      >
        <Box
          sx={{
            border: (theme) => `1px solid ${theme.palette.border.default}`,
            bgcolor: 'surface.secondary',
            p: 6,
            maxWidth: 440,
            textAlign: 'center',
            width: '100%',
          }}
        >
          <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', mb: 2, }}>
            Invalid link
          </Typography>
          <Typography
            sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 4 }}
          >
            This reset link is missing a token. Please request a new password reset.
          </Typography>
          <Button component={Link} to="/auth/forgot-password" variant="contained">
            Request New Link
          </Button>
        </Box>
      </Box>
    );
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
            PASSWORD RESET
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
            Set new password.
          </Typography>

          <Typography
            variant="body1"
            sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 6 }}
          >
            Choose a strong password for your EGO account.
          </Typography>

          <Divider sx={{ mb: 5, borderColor: 'border.default' }} />

          <Box
            component="form"
            onSubmit={handleSubmit}
            noValidate
            sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}
          >
            {error && (
              <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}>
                <Alert severity="error" sx={{ borderRadius: 0, fontSize: '0.85rem' }}>
                  {error}{' '}
                  {error.includes('expired') && (
                    <Link to="/auth/forgot-password" style={{ fontWeight: 700, color: 'inherit' }}>
                      Request a new link
                    </Link>
                  )}
                </Alert>
              </motion.div>
            )}

            {/* New password */}
            <Box>
              <TextField
                id="reset-new-password"
                label="New Password"
                type="password"
                autoComplete="new-password"
                fullWidth
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                error={!!fieldError && (fieldError.includes('8 characters') || fieldError.includes('digit'))}
                slotProps={{ htmlInput: { 'aria-label': 'New Password' } }}
              />
              {newPassword && (
                <Box sx={{ mt: 1 }}>
                  <LinearProgress
                    variant="determinate"
                    value={strength.score}
                    sx={{
                      height: 2,
                      borderRadius: 0,
                      backgroundColor: 'surface.tertiary',
                      '& .MuiLinearProgress-bar': { backgroundColor: strength.colorPath.includes('.') ? strength.colorPath.split('.').reduce((o: any, i: string) => o[i], theme.palette) : strength.colorPath, borderRadius: 0 },
                    }}
                  />
                  <Typography
                    variant="caption"
                    sx={{
                      fontFamily: (theme) => theme.typography.fontFamilyUtility,
                      fontWeight: 700,
                      fontSize: '0.65rem',
                      letterSpacing: '0.1em',
                      color: strength.colorPath,
                      mt: 0.5,
                      display: 'block',
                    }}
                  >
                    {strength.label}
                  </Typography>
                </Box>
              )}
            </Box>

            {/* Confirm password */}
            <TextField
              id="reset-confirm-password"
              label="Confirm Password"
              type="password"
              autoComplete="new-password"
              fullWidth
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              error={!!fieldError && fieldError.includes('match')}
              helperText={fieldError ?? undefined}
              slotProps={{ htmlInput: { 'aria-label': 'Confirm Password' } }}
            />

            <motion.div whileTap={{ scale: 0.98 }}>
              <Button
                id="reset-password-submit"
                type="submit"
                variant="contained"
                fullWidth
                disabled={loading}
                sx={{ py: 1.75, mt: 0.5 }}
              >
                {loading ? <CircularProgress size={20} color="inherit" /> : 'Update Password'}
              </Button>
            </motion.div>
          </Box>

          <Box sx={{ mt: 5 }}>
            <Link to="/login" style={{ textDecoration: 'none' }}>
              <Typography
                variant="body2"
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyUtility,
                  fontWeight: 600,
                  fontSize: '0.8rem',
                  letterSpacing: '0.05em',
                  color: 'text.secondary',
                  '&:hover': { color: 'text.primary' },
                  transition: 'color 0.2s',
                }}
              >
                ← Back to login
              </Typography>
            </Link>
          </Box>
        </motion.div>
      </Box>

      {/* ── Brand panel ─────────────────────────────────────────────── */}
      <BrandPanel quote="A fresh start." />
    </Box>
  );
};

export default ResetPasswordPage;
