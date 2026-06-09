/**
 * VerifyEmailPage.tsx — Analog Archive Dark redesign
 *
 * Full-viewport centered confirmation/error state page.
 * All API logic (verifyEmail, resendVerification, getMe) preserved exactly.
 */

import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Box, Typography, Button, CircularProgress } from '@mui/material';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import ErrorOutlinedIcon from '@mui/icons-material/ErrorOutlined';
import { motion } from 'framer-motion';
import { verifyEmail, resendVerification, getMe } from '@/api/auth.api';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/store/uiStore';

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const { setUser, isAuthenticated } = useAuthStore();

  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [isResending, setIsResending] = useState(false);

  useEffect(() => {
    if (!token) { setStatus('error'); return; }

    const doVerify = async () => {
      try {
        await verifyEmail(token);
        if (isAuthenticated) {
          const res = await getMe();
          setUser(res.data.data);
        }
        setStatus('success');
      } catch {
        setStatus('error');
      }
    };

    doVerify();
  }, [token, isAuthenticated, setUser]);

  const handleResend = async () => {
    if (!isAuthenticated) {
      toast.error('You must be logged in to resend the verification email.');
      navigate('/login');
      return;
    }
    try {
      setIsResending(true);
      await resendVerification();
      toast.success('Verification email sent! Please check your inbox.');
    } catch {
      // API client interceptor handles the error toast
    } finally {
      setIsResending(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        px: 4,
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Grid texture background */}
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
        style={{ position: 'relative', zIndex: 1, width: '100%', maxWidth: 480 }}
      >
        {/* EGO brand mark */}
        <Typography
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyDisplay,
            fontWeight: 700,
            fontSize: '1.1rem',
            letterSpacing: '0.3em',
            textTransform: 'uppercase',
            color: 'text.secondary',
            display: 'block',
            mb: 8,
            textAlign: 'center',
          }}
        >
          EGO
        </Typography>

        {/* Card */}
        <Box
          sx={{
            border: (theme) => `1px solid ${theme.palette.border.default}`,
            bgcolor: 'surface.secondary',
            p: { xs: 5, sm: 7 },
            textAlign: 'center',
          }}
        >
          {/* ── LOADING ── */}
          {status === 'loading' && (
            <Box sx={{ py: 4 }}>
              <CircularProgress
                size={40}
                sx={{ color: 'text.secondary', mb: 4 }}
              />
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
                VERIFYING
              </Typography>
              <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', }}>
                Verifying your email...
              </Typography>
            </Box>
          )}

          {/* ── SUCCESS ── */}
          {status === 'success' && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4 }}
            >
              <CheckCircleOutlinedIcon
                sx={{ fontSize: 52, color: 'success.main', mb: 3 }}
              />
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
                CONFIRMED
              </Typography>
              <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', mb: 2, }}>
                Email Verified.
              </Typography>
              <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 6, }}>
                Your email address has been successfully verified. You can now check out and access all features.
              </Typography>
              <Button
                variant="contained"
                size="large"
                onClick={() => navigate('/products')}
                sx={{ px: 6, py: 1.75 }}
              >
                Continue Shopping
              </Button>
            </motion.div>
          )}

          {/* ── ERROR ── */}
          {status === 'error' && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.4 }}
            >
              <ErrorOutlinedIcon
                sx={{ fontSize: 52, color: 'error.main', mb: 3 }}
              />
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
                LINK EXPIRED
              </Typography>
              <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', mb: 2, }}>
                Verification Failed.
              </Typography>
              <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 6, }}>
                The verification link is invalid or has expired. Links expire after 24 hours.
              </Typography>
              <Box sx={{ display: 'flex', gap: 2, justifyContent: 'center', flexWrap: 'wrap' }}>
                <Button
                  variant="outlined"
                  onClick={handleResend}
                  disabled={isResending}
                  sx={{ px: 4, py: 1.5, borderRadius: 0, borderColor: 'border.default', color: 'text.primary' }}
                >
                  {isResending ? 'Sending...' : 'Resend Email'}
                </Button>
                <Button
                  variant="contained"
                  onClick={() => navigate('/')}
                  sx={{ px: 4, py: 1.5 }}
                >
                  Go Home
                </Button>
              </Box>
            </motion.div>
          )}
        </Box>
      </motion.div>
    </Box>
  );
}
