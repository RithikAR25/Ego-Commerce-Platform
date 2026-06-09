/**
 * OrderSuccessPage.tsx
 *
 * Payment confirmed — order success celebration screen.
 * Route: /checkout/success/:orderId  (under CheckoutLayout)
 *
 * Only reachable from PaymentVerificationPage after the order reaches
 * CONFIRMED status. Displays the full order summary and CTAs.
 */

import React, { useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';

import {
  Box, Container, Typography, Button, Divider,
  Paper, Chip, Skeleton, useTheme
} from '@mui/material';
import CheckCircleIcon   from '@mui/icons-material/CheckCircle';
import ShoppingBagIcon   from '@mui/icons-material/ShoppingBag';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import { useOrder }      from '@/features/orders/hooks/useOrders';
import { motion }        from 'framer-motion';

// ── Status color helper ───────────────────────────────────────────────────────



// ── Confetti burst (CSS-only, no dependencies) ────────────────────────────────

const ConfettiPiece: React.FC<{ delay: number; x: number; color: string }> = ({
  delay, x, color,
}) => (
  <Box
    sx={{
      position: 'absolute',
      top: 0,
      left: `${x}%`,
      width:  8,
      height: 8,
      bgcolor: color,
      borderRadius: '2px',
      animation: `confettiFall 1.5s ${delay}s ease-in forwards`,
      '@keyframes confettiFall': {
        '0%':   { transform: 'translateY(-20px) rotate(0deg)', opacity: 1 },
        '100%': { transform: 'translateY(300px) rotate(720deg)', opacity: 0 },
      },
    }}
  />
);

const ConfettiBurst: React.FC = () => {
  const theme = useTheme();
  const colors = [
    theme.palette.primary.main,
    theme.palette.warning.main,
    theme.palette.success.main,
    theme.palette.error.main,
    theme.palette.info.main,
    theme.palette.text.secondary,
  ];
  return (
    <Box sx={{ position: 'absolute', top: 0, left: 0, right: 0, overflow: 'hidden', height: 300, pointerEvents: 'none' }}>
      {Array.from({ length: 20 }).map((_, i) => (
        <ConfettiPiece
          key={i}
          delay={i * 0.08}
          x={5 + (i * 4.5) % 90}
          color={colors[i % colors.length]}
        />
      ))}
    </Box>
  );
};

// ── Component ─────────────────────────────────────────────────────────────────

const OrderSuccessPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const numericId   = Number(orderId);

  const { data: order, isLoading } = useOrder(numericId);
  const hasFiredRef = useRef(false);

  // Scroll to top on mount
  useEffect(() => {
    if (!hasFiredRef.current) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
      hasFiredRef.current = true;
    }
  }, []);

  return (
    <Container maxWidth="sm" sx={{ py: { xs: 8, md: 12 }, position: 'relative', bgcolor: 'background.default', minHeight: '100vh' }}>
      <ConfettiBurst />

      <motion.div
        initial={{ opacity: 0, y: 32 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
      >
        {/* ── Success hero ─────────────────────────────────────────── */}
        <Box sx={{ textAlign: 'center', mb: 8 }}>
          <CheckCircleIcon
            sx={{
              fontSize: 56,
              color:    'success.main',
              mb:       4,
              animation: 'popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275)',
              '@keyframes popIn': {
                '0%':   { transform: 'scale(0)', opacity: 0 },
                '100%': { transform: 'scale(1)', opacity: 1 },
              },
            }}
          />

          <Typography
            variant="overline"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              fontSize: '0.65rem',
              letterSpacing: '0.3em',
              color: 'text.secondary',
              display: 'block',
              mb: 2,
            }}
          >
            ORDER CONFIRMED
          </Typography>

          <Typography
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyDisplay,
              fontWeight: 700,
              fontSize: { xs: '2rem', md: '3rem' },
              color: 'text.primary',
              lineHeight: 1.05,
              mb: 2,
            }}
          >
            Thank you for your order.
          </Typography>

          <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 4, }}>
            Your payment was successful. We're preparing your archive.
          </Typography>

          <Typography
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              fontSize: '0.72rem',
              letterSpacing: '0.15em',
              color: 'text.secondary',
              border: (theme) => `1px solid ${theme.palette.border.default}`,
              display: 'inline-block',
              px: 3,
              py: 1,
            }}
          >
            ORDER #{numericId}
          </Typography>
        </Box>

        {/* ── Order detail card ─────────────────────────────────────── */}
        <Paper
          elevation={0}
          sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, p: { xs: 3, sm: 5 }, mb: 5, bgcolor: 'surface.secondary', borderRadius: 0 }}
        >
          {isLoading ? (
            <>
              <Skeleton height={28} sx={{ mb: 1 }} />
              <Skeleton height={28} sx={{ mb: 1 }} />
              <Skeleton height={28} />
            </>
          ) : order ? (
            <>
              {/* Status */}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                <Typography
                  variant="overline"
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.6rem',
                    letterSpacing: '0.2em',
                    color: 'text.secondary',
                  }}
                >
                  STATUS
                </Typography>
                <Chip
                  label={order.status}
                  size="small"
                  sx={{
                    bgcolor:    (theme) => theme.palette.statusColors?.[order.status] ?? theme.palette.statusColors?.INACTIVE,
                    color:      'text.primary',
                    fontWeight: 700,
                    borderRadius: 0,
                  }}
                />
              </Box>

              <Divider sx={{ mb: 2 }} />

              {/* Items */}
              {order.items.map((item) => (
                <Box
                  key={item.id}
                  sx={{ display: 'flex', justifyContent: 'space-between', py: 2, borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}
                >
                  <Box>
                    <Typography
                      variant="body2"
                      sx={{ fontWeight: 600, fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary' }}
                    >
                      {item.productNameSnapshot}
                    </Typography>
                    {item.variantLabelSnapshot && (
                      <Typography variant="caption" color="text.secondary">
                        {item.variantLabelSnapshot}
                      </Typography>
                    )}
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      Qty: {item.quantity}
                    </Typography>
                  </Box>
                  <Typography variant="metadata" sx={{ whiteSpace: 'nowrap' }}>
                    ₹{item.lineTotal.toLocaleString('en-IN')}
                  </Typography>
                </Box>
              ))}

              <Divider sx={{ my: 2 }} />

              {/* Totals */}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" color="text.secondary">Subtotal</Typography>
                <Typography variant="body2">₹{order.subtotal.toLocaleString('en-IN')}</Typography>
              </Box>
              {order.discountAmount > 0 && (
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" color="success.main">
                    Discount{order.couponCodeSnapshot ? ` (${order.couponCodeSnapshot})` : ''}
                  </Typography>
                  <Typography variant="body2" color="success.main">
                    −₹{order.discountAmount.toLocaleString('en-IN')}
                  </Typography>
                </Box>
              )}
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="body2" color="text.secondary">Shipping</Typography>
                <Typography variant="metadata" color="success.main">Free</Typography>
              </Box>

              <Divider sx={{ mb: 2 }} />

              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography
                  variant="subtitle1"
                  sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 800, color: 'text.primary', letterSpacing: '0.05em' }}
                >
                  TOTAL PAID
                </Typography>
                <Typography variant="subtitle1" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 800, color: 'text.primary' }}>
                  ₹{order.grandTotal.toLocaleString('en-IN')}
                </Typography>
              </Box>

              <Divider sx={{ my: 2 }} />

              {/* Shipping address */}
              <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
                <LocalShippingIcon sx={{ fontSize: 18, color: 'text.secondary', mt: 0.3 }} />
                <Box>
                  <Typography variant="metadata" sx={{ color: 'text.secondary', }}>
                    Delivering to
                  </Typography>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', mt: 0.5 }}>
                    {order.shippingAddress}
                  </Typography>
                </Box>
              </Box>
            </>
          ) : null}
        </Paper>

        {/* ── CTAs ────────────────────────────────────────────────────── */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Button
            component={Link}
            to={`/orders/${numericId}`}
            variant="contained"
            size="large"
            startIcon={<ShoppingBagIcon />}
            sx={{ py: 1.75 }}
          >
            View My Order
          </Button>
          <Button
            component={Link}
            to="/products"
            variant="outlined"
            size="large"
            sx={{
              py: 1.75,
              borderColor: 'border.default',
              color: 'text.primary',
              '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'transparent' },
            }}
          >
            Continue Shopping
          </Button>
        </Box>
      </motion.div>
    </Container>
  );
};

export default OrderSuccessPage;
