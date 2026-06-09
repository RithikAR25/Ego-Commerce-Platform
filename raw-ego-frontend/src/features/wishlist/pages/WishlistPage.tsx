import React from 'react';
import { Box, Container, Typography, Grid, Paper, Button, IconButton, Skeleton, Alert, alpha } from '@mui/material';
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import ShoppingBagOutlinedIcon from '@mui/icons-material/ShoppingBagOutlined';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import { Link } from 'react-router-dom';
import { useWishlist, useRemoveFromWishlist, useClearWishlist } from '../hooks/useWishlist';
import { useAddToCart } from '@/features/cart/hooks/useCart';
import PageWrapper from '@/components/layout/PageWrapper';

const WishlistPage: React.FC = () => {
  const { data: wishlist, isLoading, isError } = useWishlist();
  const { mutate: remove, isPending: isRemoving } = useRemoveFromWishlist();
  const { mutate: clear, isPending: isClearing } = useClearWishlist();
  const { mutate: addToCart, isPending: isAdding } = useAddToCart();

  if (isLoading) {
    return (
      <PageWrapper>
        <Container maxWidth="lg" sx={{ py: 8 }}>
          <Typography variant="h4" sx={{ mb: 6 }}><Skeleton width={200} /></Typography>
          <Grid container spacing={4}>
            {[1, 2, 3, 4].map((i) => (
              <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={i}>
                <Skeleton variant="rectangular" height={300} />
                <Skeleton width="80%" sx={{ mt: 2 }} />
                <Skeleton width="40%" />
              </Grid>
            ))}
          </Grid>
        </Container>
      </PageWrapper>
    );
  }

  if (isError) {
    return (
      <PageWrapper>
        <Container maxWidth="lg" sx={{ py: 8 }}>
          <Alert severity="error" sx={{ borderRadius: 0 }}>Failed to load your wishlist.</Alert>
        </Container>
      </PageWrapper>
    );
  }

  const isEmpty = !wishlist?.items || wishlist.items.length === 0;

  return (
    <PageWrapper>
      <Container maxWidth="lg" sx={{ py: { xs: 4, md: 8 } }}>
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-end',
            pb: 4,
            mb: 6,
            borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
          }}
        >
          <Box>
            <Typography
              variant="overline"
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontWeight: 700,
                fontSize: '0.65rem',
                letterSpacing: '0.25em',
                color: 'text.secondary',
                display: 'block',
                mb: 1.5,
              }}
            >
              ACCOUNT
            </Typography>
            <Typography
              variant="h2"
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyDisplay,
                fontWeight: 700,
                fontSize: { xs: '2rem', md: '3rem' },
                color: 'text.primary',
                lineHeight: 1,
                textTransform: 'uppercase',
              }}
            >
              My Wishlist
            </Typography>
          </Box>
          {!isEmpty && (
            <Button
              variant="outlined"
              disabled={isClearing}
              onClick={() => {
                if (window.confirm('Are you sure you want to clear your entire wishlist?')) {
                  clear();
                }
              }}
              sx={{
                borderRadius: 0,
                textTransform: 'uppercase',
                letterSpacing: '0.1em',
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontWeight: 600,
                fontSize: '0.7rem',
                borderColor: 'border.default',
                color: 'text.primary',
                '&:hover': { borderColor: 'text.primary', bgcolor: 'transparent' },
              }}
            >
              Clear All
            </Button>
          )}
        </Box>

        {isEmpty ? (
          <Box sx={{ textAlign: 'center', py: 10 }}>
            <FavoriteBorderIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
            <Typography variant="h6" color="text.secondary" sx={{ mb: 3 }}>
              Your wishlist is currently empty.
            </Typography>
            <Button
              component={Link}
              to="/products"
              variant="contained"
              sx={{ borderRadius: 0, bgcolor: 'text.primary', color: 'background.paper', px: 4, py: 1.5, '&:hover': { bgcolor: 'surface.secondary', color: 'text.primary' } }}
            >
              Continue Shopping
            </Button>
          </Box>
        ) : (
          <Grid container spacing={4}>
            {wishlist.items.map((item) => {
              const isOutOfStock = item.stockStatus === 'OUT_OF_STOCK' || item.quantityAvailable <= 0;

              return (
                <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={item.variantId}>
                  <Paper
                    elevation={0}
                    sx={{
                      height: '100%',
                      display: 'flex',
                      flexDirection: 'column',
                      border: (theme) => `1px solid ${theme.palette.border.default}`,
                      bgcolor: 'surface.secondary',
                      position: 'relative',
                      transition: 'border-color 0.25s ease',
                      '&:hover': {
                        borderColor: 'text.primary',
                      },
                    }}
                  >
                    {/* Delete button (top right) */}
                    <IconButton
                      size="small"
                      disabled={isRemoving}
                      onClick={() => remove(item.variantId)}
                      sx={{
                        position: 'absolute',
                        top: 8,
                        right: 8,
                        bgcolor: (theme) => alpha(theme.palette.background.default, 0.8),
                        '&:hover': { bgcolor: 'error.main', color: 'primary.main' },
                        zIndex: 2,
                      }}
                    >
                      <DeleteOutlinedIcon fontSize="small" />
                    </IconButton>

                    {/* Image */}
                    <Box
                      component={Link}
                      to={`/products/${item.sku.split('-').slice(0, 3).join('-').toLowerCase()}`} // Simple assumption for slug
                      sx={{
                        display: 'block',
                        pt: '125%',
                        position: 'relative',
                        bgcolor: 'background.default',
                        overflow: 'hidden',
                        textDecoration: 'none',
                      }}
                    >
                      <Box
                        component="img"
                        src={item.primaryImageUrl || 'https://via.placeholder.com/400x500?text=No+Image'}
                        alt={item.productName}
                        sx={{
                          position: 'absolute',
                          top: 0,
                          left: 0,
                          width: '100%',
                          height: '100%',
                          objectFit: 'cover',
                          opacity: isOutOfStock ? 0.6 : 1,
                        }}
                      />
                      {item.discountPercent && item.discountPercent > 0 && !isOutOfStock && (
                        <Box
                          sx={{
                            position: 'absolute',
                            top: 8,
                            left: 8,
                            bgcolor: 'error.main',
                            color: 'primary.main',
                            px: 1,
                            py: 0.5,
                            fontSize: '0.7rem',
                            fontWeight: 700,
                          }}
                        >
                          {item.discountPercent}% OFF
                        </Box>
                      )}
                      {isOutOfStock && (
                        <Box
                          sx={{
                            position: 'absolute',
                            top: '50%',
                            left: '50%',
                            transform: 'translate(-50%, -50%)',
                            bgcolor: (theme) => alpha(theme.palette.background.default, 0.7),
                            color: 'primary.main',
                            px: 2,
                            py: 1,
                            fontWeight: 700,
                            letterSpacing: '0.05em',
                          }}
                        >
                          SOLD OUT
                        </Box>
                      )}
                    </Box>

                    {/* Content */}
                    <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', flex: 1 }}>
                      <Typography
                        variant="body2"
                        component={Link}
                        to={`/products/${item.sku.split('-').slice(0, 3).join('-').toLowerCase()}`}
                        sx={{
                          fontWeight: 700,
                          textDecoration: 'none',
                          color: 'inherit',
                          mb: 0.5,
                          '&:hover': { textDecoration: 'underline' },
                        }}
                      >
                        {item.productName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
                        {item.variantLabel}
                      </Typography>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2, mt: 'auto' }}>
                        <Typography variant="metadata" >
                          ₹{item.price.toLocaleString('en-IN')}
                        </Typography>
                        {item.compareAtPrice && (
                          <Typography variant="caption" color="text.secondary" sx={{ textDecoration: 'line-through' }}>
                            ₹{item.compareAtPrice.toLocaleString('en-IN')}
                          </Typography>
                        )}
                      </Box>

                      <Button
                        fullWidth
                        variant="contained"
                        disabled={isOutOfStock || isAdding}
                        startIcon={<ShoppingBagOutlinedIcon />}
                        onClick={() => {
                          addToCart(
                            { variantId: item.variantId, quantity: 1 },
                            {
                              onSuccess: () => remove(item.variantId),
                            }
                          );
                        }}
                        sx={{
                          borderRadius: 0,
                          py: 1.25,
                          fontFamily: (theme) => theme.typography.fontFamilyUtility,
                          fontWeight: 700,
                          fontSize: '0.72rem',
                          letterSpacing: '0.1em',
                          textTransform: 'uppercase',
                        }}
                      >
                        {isOutOfStock ? 'Sold Out' : 'Move to Cart'}
                      </Button>
                    </Box>
                  </Paper>
                </Grid>
              );
            })}
          </Grid>
        )}
      </Container>
    </PageWrapper>
  );
};

export default WishlistPage;
