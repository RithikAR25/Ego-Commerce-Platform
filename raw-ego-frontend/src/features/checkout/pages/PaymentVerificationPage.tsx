/**
 * PaymentVerificationPage.tsx
 *
 * Webhook-aware payment verification screen.
 * Route: /checkout/verify/:orderId  (under CheckoutLayout)
 *
 * This page is reached immediately after the Razorpay success callback fires.
 * At this point, the order is STILL PENDING_PAYMENT — the backend webhook
 * has NOT yet fired. We poll GET /orders/{id} every 2s until:
 *
 *  ✅ CONFIRMED  → redirect to /checkout/success/:orderId
 *  ⏳ TIMEOUT    → show "Payment is being processed" (webhook may arrive late)
 *  ❌ ERROR       → show failure message with recovery options
 *
 * Security guarantee:
 *   This page NEVER marks the order as paid. It only reads order status.
 *   The webhook is authoritative.
 */

import React, { useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Box, Typography, Button, LinearProgress, Alert } from '@mui/material';
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined';
import HourglassEmptyIcon      from '@mui/icons-material/HourglassEmpty';
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutlineOutlined";
import { useOrderPolling }     from '@/hooks/useOrderPolling';

// ── Dot animation ─────────────────────────────────────────────────────────────

const PulsingDots: React.FC = () => (
  <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center', mt: 3 }}>
    {[0, 1, 2].map((i) => (
      <Box
        key={i}
        sx={{
          width: 6, height: 6,
          bgcolor: 'text.secondary',
          animation: 'pulse 1.2s ease-in-out infinite',
          animationDelay: `${i * 0.2}s`,
          '@keyframes pulse': {
            '0%, 80%, 100%': { opacity: 0.2, transform: 'scale(0.7)' },
            '40%':            { opacity: 1,   transform: 'scale(1)' },
          },
        }}
      />
    ))}
  </Box>
);

// ── Component ─────────────────────────────────────────────────────────────────

const PaymentVerificationPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate    = useNavigate();
  const numericId   = Number(orderId);

  const { status, order, attempts } = useOrderPolling(numericId);

  // Redirect to success page as soon as CONFIRMED is detected
  useEffect(() => {
    if (status === 'confirmed' || status === 'already_confirmed') {
      navigate(`/checkout/success/${numericId}`, { replace: true });
    }
  }, [status, numericId, navigate]);

  return (
    <Box
      sx={{
        flex:           1,
        display:        'flex',
        flexDirection:  'column',
        alignItems:     'center',
        justifyContent: 'center',
        px:             4,
        textAlign:      'center',
        minHeight:      '70vh',
        bgcolor:        'background.default',
        position:       'relative',
        overflow:       'hidden',
      }}
    >
      {/* Grid texture */}
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          backgroundImage: (theme) =>
            `repeating-linear-gradient(0deg, transparent, transparent 59px, ${theme.palette.border.default} 60px), repeating-linear-gradient(90deg, transparent, transparent 59px, ${theme.palette.border.default} 60px)`,
          opacity: 0.08,
          pointerEvents: 'none',
        }}
      />
      {/* ── Polling state ──────────────────────────────────── */}
      {(status === 'polling' || status === 'idle') && (
        <Box sx={{ position: 'relative', zIndex: 1 }}>
          <Box
            sx={{
              width:          72,
              height:         72,
              border:         (theme) => `1px solid ${theme.palette.border.default}`,
              display:        'flex',
              alignItems:     'center',
              justifyContent: 'center',
              mx:             'auto',
              mb:             4,
            }}
          >
            <HourglassEmptyIcon sx={{ fontSize: 36, color: 'text.secondary', animation: 'spin 2s linear infinite', '@keyframes spin': { '100%': { transform: 'rotate(360deg)' } } }} />
          </Box>

          <Typography
            variant="overline"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              fontSize: '0.65rem',
              letterSpacing: '0.25em',
              color: 'text.secondary',
              display: 'block',
              mb: 2,
            }}
          >
            PAYMENT VERIFICATION
          </Typography>

          <Typography
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyDisplay,
              fontWeight: 700,
              fontSize: { xs: '1.75rem', md: '2.5rem' },
              color: 'text.primary',
              lineHeight: 1.1,
              mb: 2,
            }}
          >
            Verifying your payment…
          </Typography>

          <Typography
            sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 5, maxWidth: 380, mx: 'auto' }}
          >
            We're confirming your payment with our secure payment gateway.
            This usually takes a few seconds.
          </Typography>

          <Box sx={{ width: '100%', maxWidth: 360, mx: 'auto', mb: 1 }}>
            <LinearProgress sx={{ borderRadius: 0, height: 2, bgcolor: 'surface.tertiary', '& .MuiLinearProgress-bar': { bgcolor: 'text.secondary' } }} />
          </Box>

          <PulsingDots />

          {attempts > 3 && (
            <Typography
              variant="caption"
              sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', mt: 3, letterSpacing: '0.1em', display: 'block' }}
            >
              Attempt {attempts} of 15 — please keep this page open
            </Typography>
          )}
        </Box>
      )}

      {/* ── Already confirmed (fast webhook) ────────────────── */}
      {(status === 'confirmed' || status === 'already_confirmed') && (
        <Box sx={{ position: 'relative', zIndex: 1 }}>
          <CheckCircleOutlinedIcon sx={{ fontSize: 52, color: 'success.main', mb: 3 }} />
          <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', }}>
            Payment confirmed! Redirecting…
          </Typography>
        </Box>
      )}

      {/* ── Timeout ─────────────────────────────────────── */}
      {status === 'timeout' && (
        <>
          <HourglassEmptyIcon sx={{ fontSize: 64, color: 'warning.main', mb: 2 }} />

          <Typography variant="metadata" sx={{ mb: 2 }}>
            Payment is being processed
          </Typography>

          <Alert severity="info" sx={{ maxWidth: 480, mb: 4, borderRadius: 0, textAlign: 'left' }}>
            <Typography variant="body2" sx={{ mb: 1 }}>
              Your payment was received but confirmation is taking longer than expected.
              This is normal — the payment will be confirmed within a few minutes.
            </Typography>
            <Typography variant="body2">
              Your order #{numericId} has been created.{' '}
              <strong>Do not pay again.</strong>{' '}
              You will receive a confirmation email once the payment clears.
            </Typography>
          </Alert>

          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'center' }}>
            <Button
              variant="contained"
              component={Link}
              to={`/orders/${numericId}`}
              sx={{ borderRadius: 0, textTransform: 'uppercase', letterSpacing: '0.06em' }}
            >
              View Order #{numericId}
            </Button>
            <Button
              variant="outlined"
              component={Link}
              to="/orders"
              sx={{ borderRadius: 0, textTransform: 'uppercase', letterSpacing: '0.06em' }}
            >
              My Orders
            </Button>
          </Box>
        </>
      )}

      {/* ── Error ───────────────────────────────────────── */}
      {status === 'error' && (
        <>
          <ErrorOutlineIcon sx={{ fontSize: 64, color: 'error.main', mb: 2 }} />

          <Typography variant="metadata" sx={{ mb: 2 }}>
            Verification failed
          </Typography>

          <Alert severity="error" sx={{ maxWidth: 480, mb: 4, borderRadius: 0, textAlign: 'left' }}>
            <Typography variant="body2">
              {order?.status === 'CANCELLED'
                ? 'Your order was cancelled.'
                : 'We could not verify your payment status. If money was deducted, it will be refunded within 5-7 business days.'}
            </Typography>
          </Alert>

          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'center' }}>
            <Button
              variant="contained"
              component={Link}
              to="/orders"
              sx={{ borderRadius: 0, textTransform: 'uppercase', letterSpacing: '0.06em' }}
            >
              My Orders
            </Button>
            <Button
              variant="outlined"
              component={Link}
              to="/products"
              sx={{ borderRadius: 0, textTransform: 'uppercase', letterSpacing: '0.06em' }}
            >
              Continue Shopping
            </Button>
          </Box>
        </>
      )}
    </Box>
  );
};

export default PaymentVerificationPage;
