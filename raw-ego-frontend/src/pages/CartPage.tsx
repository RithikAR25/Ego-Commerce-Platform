/**
 * CartPage.tsx
 *
 * Dedicated full-page cart view accessible at /cart.
 *
 * Layout:
 *   Left column (2/3): scrollable cart item list with qty controls and remove buttons
 *   Right column (1/3): sticky order summary — subtotal, free shipping, coupon input placeholder, checkout CTA
 *
 * All data comes from the same useCart / useUpdateCartItem / useRemoveCartItem hooks
 * used by CartDrawer — no API duplication.
 *
 * Route: /cart  (ProtectedRoute — requires authentication)
 */

import React from 'react';
import {
  Box, Typography, Button, Chip,
  Container, Grid, Skeleton, alpha
} from '@mui/material';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  useCart,
  useRemoveCartItem,
} from '@/features/cart/hooks/useCart';
import CartItemSkeleton from '@/features/cart/components/skeletons/CartItemSkeleton';
import CartSummarySkeleton from '@/features/cart/components/skeletons/CartSummarySkeleton';

// ── Animation variants ────────────────────────────────────────────────────────

const itemVariants = {
  initial:  { opacity: 0, y: 16 },
  animate:  { opacity: 1, y: 0, transition: { duration: 0.25 } },
  exit:     { opacity: 0, x: -32, transition: { duration: 0.2 } },
};

// ── Component ─────────────────────────────────────────────────────────────────

const CartPage: React.FC = () => {
  const navigate = useNavigate();
  const { data: cart, isLoading } = useCart();
  const { mutate: removeItem,    isPending: isRemoving  } = useRemoveCartItem();

  const hasOutOfStockItems = cart?.items.some(
    (item) => item.stockStatus === 'OUT_OF_STOCK',
  ) ?? false;

  const subtotal = cart?.subtotal ?? 0;
  const itemCount = cart?.itemCount ?? 0;

  // ── Loading skeleton ───────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <Container maxWidth="lg" sx={{ py: 6 }}>
        <Skeleton width={220} height={36} sx={{ mb: 4 }} />
        <Grid container spacing={4}>
          <Grid size={{ xs: 12, md: 8 }}>
            {[1, 2, 3].map((i) => (
              <CartItemSkeleton key={i} variant="page" />
            ))}
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <CartSummarySkeleton />
          </Grid>
        </Grid>
      </Container>
    );
  }

  // ── Empty state ────────────────────────────────────────────────────────────

  if (!cart || cart.items.length === 0) {
    return (
      <Container maxWidth="sm" sx={{ py: 14, textAlign: 'center', bgcolor: 'background.default', minHeight: '80vh' }}>
        <Typography
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyDisplay,
            fontWeight: 800,
            fontSize: { xs: '6rem', md: '10rem' },
            lineHeight: 0.85,
            color: 'transparent',
            WebkitTextStroke: (theme) => `1px ${theme.palette.border.default}`,
            userSelect: 'none',
            mb: 4,
          }}
        >
          0
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
            mb: 2,
          }}
        >
          EMPTY ARCHIVE
        </Typography>
        <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', mb: 2, }}>
          Your cart is empty.
        </Typography>
        <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mb: 6, }}>
          Explore the collection and add pieces to your archive.
        </Typography>
        <Button
          component={Link}
          to="/products"
          variant="contained"
          size="large"
          sx={{ px: 6, py: 1.75 }}
        >
          Shop Now
        </Button>
      </Container>
    );
  }

  // ── Main layout ────────────────────────────────────────────────────────────

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', pb: 12 }}>
      <Container maxWidth="lg">
        {/* Page header */}
        <Box
          component="header"
          sx={{
            pt: 8,
            pb: 5,
            mb: 6,
            borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
          }}
        >
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
            SHOPPING BAG
          </Typography>
          <Typography
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyDisplay,
              fontWeight: 700,
              fontSize: { xs: '2.5rem', md: '4rem' },
              color: 'text.primary',
              lineHeight: 1,
              textTransform: 'uppercase',
            }}
          >
            Your Archive
          </Typography>
          <Typography
            variant="overline"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 600,
              fontSize: '0.65rem',
              letterSpacing: '0.2em',
              color: 'text.secondary',
              display: 'block',
              mt: 2,
            }}
          >
            {itemCount} {itemCount === 1 ? 'ITEM' : 'ITEMS'}
          </Typography>
        </Box>

        <Grid container spacing={5} sx={{ alignItems: 'flex-start' }}>

          {/* ── Left: Item list ─────────────────────────────────────────────── */}
          <Grid size={{ xs: 12, md: 8 }}>

            {/* Out-of-stock banner */}
            {hasOutOfStockItems && (
              <Box
                sx={{
                  display: 'flex', alignItems: 'center', gap: 1.5,
                  bgcolor: (theme) => alpha(theme.palette.error.main, 0.08), 
                  border: (theme) => `1px solid ${alpha(theme.palette.error.main, 0.3)}`,
                  p: 2, mb: 4,
                }}
              >
                <WarningAmberIcon sx={{ color: 'error.main', flexShrink: 0 }} />
                <Typography variant="body2" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.primary' }}>
                  One or more items are out of stock. Please remove them before proceeding to checkout.
                </Typography>
              </Box>
            )}

            {/* Items */}
            <AnimatePresence initial={false}>
              {cart.items.map((item) => (
                <motion.div
                  key={item.variantId}
                  variants={itemVariants}
                  initial="initial"
                  animate="animate"
                  exit="exit"
                  layout
                >
                  <Box
                    sx={{
                      display: 'flex',
                      flexDirection: { xs: 'column', md: 'row' },
                      gap: { xs: 3, md: 5 },
                      py: 5,
                      borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
                      opacity: item.stockStatus === 'OUT_OF_STOCK' ? 0.5 : 1,
                    }}
                  >
                    {/* Image */}
                    <Box
                      component={Link}
                      to={`/products/${item.sku.split('-').slice(0, 3).join('-').toLowerCase()}`}
                      sx={{
                        width: { xs: '100%', md: 256 },
                        aspectRatio: '3/4',
                        bgcolor: 'background.paper',
                        flexShrink: 0,
                        overflow: 'hidden',
                        display: 'block',
                        position: 'relative',
                        '&:hover img': { transform: 'scale(1.05)', opacity: 1 }
                      }}
                    >
                      <Box
                        component="img"
                        src={item.primaryImageUrl ?? 'https://via.placeholder.com/256x341?text=No+Image'}
                        alt={item.productName}
                        sx={{ width: '100%', height: '100%', objectFit: 'cover', opacity: 0.9, transition: 'all 0.5s ease' }}
                      />
                      {item.stockStatus === 'OUT_OF_STOCK' && (
                        <Box
                          sx={{
                            position: 'absolute', inset: 0,
                            bgcolor: 'overlay.main',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                          }}
                        >
                          <Typography variant="buttonLabel" sx={{ color: 'primary.main', }}>
                            Sold Out
                          </Typography>
                        </Box>
                      )}
                    </Box>

                    {/* Details */}
                    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                      <Box>
                        <Typography
                          variant="overline"
                          sx={{
                            fontFamily: (theme) => theme.typography.fontFamilyUtility,
                            fontWeight: 600,
                            fontSize: '0.6rem',
                            letterSpacing: '0.2em',
                            color: 'text.secondary',
                            display: 'block',
                            mb: 1.5,
                          }}
                        >
                          REF: {item.sku}
                        </Typography>
                        <Typography
                          component={Link}
                          to={`/products/${item.sku.split('-').slice(0, 3).join('-').toLowerCase()}`}
                          sx={{
                            fontFamily: (theme) => theme.typography.fontFamilyDisplay,
                            fontWeight: 700,
                            fontSize: { xs: '1.25rem', md: '1.6rem' },
                            color: 'text.primary',
                            textDecoration: 'none',
                            lineHeight: 1.1,
                            display: 'block',
                            mb: 2,
                            '&:hover': { color: 'text.secondary' },
                          }}
                        >
                          {item.productName}
                        </Typography>
                        <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', }}>
                          {item.variantLabel}
                        </Typography>
                      </Box>

                      {/* Out-of-stock chip */}
                      {item.stockStatus === 'OUT_OF_STOCK' && (
                        <Chip
                          icon={<WarningAmberIcon />}
                          label="Out of stock — please remove"
                          size="small"
                          sx={{
                            mt: 0.5,
                            alignSelf: 'flex-start',
                            fontSize: '0.7rem',
                            fontWeight: 600,
                            bgcolor: (theme) => alpha(theme.palette.error.main, 0.08),
                            color: 'error.main',
                            border: (theme) => `1px solid ${alpha(theme.palette.error.main, 0.3)}`,
                            borderRadius: '4px',
                            '& .MuiChip-icon': { color: 'error.main', fontSize: '0.9rem' },
                          }}
                        />
                      )}

                      <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', mt: { xs: 4, md: 0 } }}>
                        <Typography
                          variant="overline"
                          sx={{
                            fontFamily: (theme) => theme.typography.fontFamilyUtility,
                            fontWeight: 600,
                            fontSize: '0.65rem',
                            letterSpacing: '0.15em',
                            color: 'text.secondary',
                          }}
                        >
                          QTY: {item.quantity}
                        </Typography>

                        {/* Price & Remove */}
                        <Box sx={{ textAlign: 'right' }}>
                          <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary', mb: 1, }}>
                            ₹{(item.price * item.quantity).toLocaleString('en-IN')}
                          </Typography>
                          <Button
                            variant="text"
                            size="small"
                            onClick={() => removeItem(item.variantId)}
                            disabled={isRemoving}
                            sx={{
                              p: 0, minWidth: 0,
                              fontFamily: (theme) => theme.typography.fontFamilyUtility,
                              fontWeight: 700,
                              fontSize: '0.65rem',
                              letterSpacing: '0.15em',
                              color: 'text.secondary',
                              borderRadius: 0,
                              '&:hover': { color: 'error.main', bgcolor: 'transparent' },
                            }}
                          >
                            REMOVE
                          </Button>
                        </Box>
                      </Box>
                    </Box>
                  </Box>
                </motion.div>
              ))}
            </AnimatePresence>

            {/* Continue shopping */}
            <Box sx={{ mt: 4 }}>
              <Button
                component={Link}
                to="/products"
                variant="text"
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyUtility,
                  fontWeight: 600,
                  fontSize: '0.72rem',
                  letterSpacing: '0.1em',
                  color: 'text.secondary',
                  p: 0,
                  '&:hover': { color: 'text.primary', background: 'none' },
                }}
              >
                ← Continue Shopping
              </Button>
            </Box>
          </Grid>

          {/* ── Right: Order summary ─────────────────────────────────────────── */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Box
              sx={{
                border: (theme) => `1px solid ${theme.palette.border.default}`,
                bgcolor: 'surface.secondary',
                p: { xs: 4, md: 5 },
                position: { md: 'sticky' },
                top: { md: 120 },
              }}
            >
              <Typography
                variant="overline"
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyUtility,
                  fontWeight: 700,
                  fontSize: '0.65rem',
                  letterSpacing: '0.25em',
                  color: 'text.secondary',
                  display: 'block',
                  mb: 5,
                }}
              >
                ORDER SUMMARY
              </Typography>

              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2.5 }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Subtotal</Typography>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 600, color: 'text.primary' }}>
                  ₹{subtotal.toLocaleString('en-IN')}
                </Typography>
              </Box>

              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 5, pb: 5, borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Shipping</Typography>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 600, color: 'text.secondary', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
                  Calculated at checkout
                </Typography>
              </Box>

              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', mb: 6 }}>
                <Typography
                  variant="overline"
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.65rem',
                    letterSpacing: '0.25em',
                    color: 'text.secondary',
                  }}
                >
                  TOTAL
                </Typography>
                <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', }}>
                  ₹{subtotal.toLocaleString('en-IN')}
                </Typography>
              </Box>

              <Button
                fullWidth
                variant="contained"
                size="large"
                onClick={() => navigate('/checkout')}
                disabled={hasOutOfStockItems || cart.items.length === 0}
                id="cart-checkout-btn"
                sx={{ py: 2.25 }}
              >
                {hasOutOfStockItems ? 'REMOVE SOLD OUT ITEMS' : 'PROCEED TO CHECKOUT'}
              </Button>

              <Typography
                variant="caption"
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyEditorial,
                  color: 'text.secondary',
                  display: 'block',
                  textAlign: 'center',
                  mt: 3,
                  lineHeight: 1.6,
                }}
              >
                Taxes and duties are calculated based on your shipping destination.
              </Typography>
            </Box>
          </Grid>

        </Grid>
      </Container>
    </Box>
  );
};

export default CartPage;
