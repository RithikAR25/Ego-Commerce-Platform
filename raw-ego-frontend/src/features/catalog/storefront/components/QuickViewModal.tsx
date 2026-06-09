import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Dialog,
  DialogContent,
  IconButton,
  Box,
  Typography,
  Grid,
  Button,
  useMediaQuery,
  useTheme,
  SwipeableDrawer,
  Divider,
  alpha,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import GppGoodOutlinedIcon from '@mui/icons-material/GppGoodOutlined';
import CreditCardOutlinedIcon from '@mui/icons-material/CreditCardOutlined';
import BoltIcon from '@mui/icons-material/Bolt';
import { toast } from 'sonner';

import { useQuickViewStore } from '@/store/quickViewStore';
import { useProductDetail } from '@/hooks/useCatalog';
import { useAddToCart } from '@/features/cart/hooks/useCart';
import { useRecentlyViewed } from '@/hooks/useRecentlyViewed';
import {
  trackQuickViewOpened,
  trackQuickViewAddToCart,
} from '@/utils/analytics';

import QuickViewSkeleton from './QuickViewSkeleton';
import VariantSelector from './VariantSelector';
import StockUrgencyIndicator from './StockUrgencyIndicator';
import ImageGallery from './ImageGallery';
import WishlistButton from '@/features/wishlist/components/WishlistButton';
import type { ProductVariantResponse } from '@/types/catalog.types';

const QuickViewModal: React.FC = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const navigate = useNavigate();

  const { isOpen, productSlug, closeQuickView } = useQuickViewStore();
  const { data: product, isLoading } = useProductDetail(productSlug ?? '');
  const { mutate: addToCart, isPending: isAdding } = useAddToCart({ suppressToast: true });
  const addViewedItem = useRecentlyViewed((state) => state.addViewedItem);

  const [selectedVariant, setSelectedVariant] = useState<ProductVariantResponse | null>(null);

  // Initialize and track opened
  useEffect(() => {
    if (isOpen && productSlug) {
      trackQuickViewOpened(productSlug);
    }
  }, [isOpen, productSlug]);

  useEffect(() => {
    if (product && product.variants.length > 0 && !selectedVariant) {
      const inStock = product.variants.find((v) => v.active && v.stockStatus !== 'OUT_OF_STOCK');
      setSelectedVariant(inStock || product.variants[0]);
    }
  }, [product, selectedVariant]);

  // Track recently viewed
  useEffect(() => {
    if (product && selectedVariant) {
      addViewedItem({
        id: product.id,
        name: product.name,
        slug: product.slug,
        price: selectedVariant.price,
        compareAtPrice: selectedVariant.compareAtPrice,
        imageUrl: selectedVariant.images[0]?.url || product.galleryImages[0]?.url,
      });
    }
  }, [product, selectedVariant, addViewedItem]);

  const handleClose = () => {
    setSelectedVariant(null);
    closeQuickView();
  };

  const handleAddToCart = () => {
    if (!selectedVariant || !product) return;

    addToCart(
      { variantId: selectedVariant.id, quantity: 1 },
      {
        onSuccess: () => {
          trackQuickViewAddToCart(product.slug, selectedVariant.id, 1);
          toast.success('Added to cart!', {
            action: {
              label: 'View Cart',
              onClick: () => {
                handleClose();
                navigate('/cart');
              },
            },
            cancel: {
              label: 'Checkout',
              onClick: () => {
                handleClose();
                navigate('/checkout');
              }
            }
          });
        },
      }
    );
  };

  const renderContent = () => {
    if (isLoading || !product) {
      return <QuickViewSkeleton />;
    }

    const isOutOfStock = product.status === 'OUT_OF_STOCK';

    return (
      <Grid container spacing={0} sx={{ height: '100%', m: 0 }}>
        {/* Image Gallery (Left) */}
        <Grid size={{ xs: 12, md: 6 }} sx={{ bgcolor: 'surface.secondary', position: 'relative' }}>
          <Box sx={{ height: { xs: 400, md: '100%' }, overflow: 'hidden' }}>
            <ImageGallery
              galleryImages={product.galleryImages}
              variantImages={selectedVariant?.images || []}
            />
          </Box>
        </Grid>

        {/* Details (Right) */}
        <Grid size={{ xs: 12, md: 6 }} sx={{ height: { md: '100%' }, p: { xs: 3, md: 4 }, pt: { xs: 4, md: 4 }, display: 'flex', flexDirection: 'column' }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
            <Typography variant="h4" component="h2" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 600, textTransform: 'uppercase', lineHeight: 1.1 }}>
              {product.name}
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', ml: 1 }}>
              {selectedVariant && (
                <WishlistButton variantId={selectedVariant.id} size="large" />
              )}
              {!isMobile && (
                <IconButton onClick={handleClose} size="large" aria-label="close" sx={{ ml: 1, color: 'text.primary' }}>
                  <CloseIcon />
                </IconButton>
              )}
            </Box>
          </Box>

          <Box sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
              <Typography variant="metadata" >
                ₹{selectedVariant?.price ?? product.minPrice}
              </Typography>
              {selectedVariant?.compareAtPrice && (
                <Typography variant="body1" color="text.secondary" sx={{ textDecoration: 'line-through' }}>
                  ₹{selectedVariant.compareAtPrice}
                </Typography>
              )}
              {selectedVariant?.discountPercent && (
                <Box sx={{ bgcolor: 'primary.main', color: 'primary.contrastText', px: 1, py: 0.25, borderRadius: 0.5, fontSize: '0.75rem', fontWeight: 600 }}>
                  {selectedVariant.discountPercent}% OFF
                </Box>
              )}
            </Box>
            <Typography variant="metadata" color="text.secondary">
              Inclusive of all taxes
            </Typography>
          </Box>

          <Divider sx={{ mb: 1 }} />

          {selectedVariant && (
            <VariantSelector
              variants={product.variants}
              attributeTypes={product.attributeTypes}
              selectedVariant={selectedVariant}
              onSelectVariant={setSelectedVariant}
            />
          )}

          {selectedVariant && (
            <Box>
              <StockUrgencyIndicator variant={selectedVariant} />
            </Box>
          )}

          {/* Trust Badges */}
          <Box sx={{ mt: 2, display: 'flex', justifyContent: 'space-between', bgcolor: 'surface.tertiary', p: 1.5, borderRadius: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Inventory2OutlinedIcon sx={{ fontSize: 20, color: 'text.secondary' }} />
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>7 Days<br />Easy Returns</Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <GppGoodOutlinedIcon sx={{ fontSize: 20, color: 'text.secondary' }} />
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>100% Original<br />Products</Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <CreditCardOutlinedIcon sx={{ fontSize: 20, color: 'text.secondary' }} />
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>Secure<br />Payments</Typography>
            </Box>
          </Box>

          {/* Action Footer */}
          <Box sx={{ mt: 'auto', pt: 2 }}>
            <Button
              variant="contained"
              fullWidth
              size="large"
              onClick={handleAddToCart}
              disabled={isOutOfStock || isAdding}
              sx={{
                py: 1.5,
                mb: 1.5,
                borderRadius: 0,
                bgcolor: 'text.primary',
                color: 'background.paper',
                fontWeight: 700,
                letterSpacing: '0.06em',
                textTransform: 'uppercase',
                '&:hover': { bgcolor: 'surface.secondary' },
                '&.Mui-disabled': { bgcolor: (theme) => alpha(theme.palette.text.primary, 0.12), color: (theme) => alpha(theme.palette.text.primary, 0.26) },
              }}
            >
              {isAdding ? 'Adding...' : isOutOfStock ? 'Sold Out' : 'Add To Cart'}
            </Button>

            <Button
              variant="outlined"
              fullWidth
              size="large"
              onClick={() => {
                if (!isOutOfStock && !isAdding) {
                  handleAddToCart();
                  navigate('/checkout'); // Ideally we should use a proper Buy Now flow but this is acceptable for now
                }
              }}
              disabled={isOutOfStock || isAdding}
              startIcon={<BoltIcon />}
              sx={{
                py: 1.5,
                borderRadius: 0,
                borderColor: 'text.primary',
                color: 'text.primary',
                fontWeight: 700,
                letterSpacing: '0.06em',
                textTransform: 'uppercase',
                '&:hover': { borderColor: 'text.primary', bgcolor: 'surface.secondary' },
                '&.Mui-disabled': { borderColor: (theme) => alpha(theme.palette.text.primary, 0.12), color: (theme) => alpha(theme.palette.text.primary, 0.26) },
              }}
            >
              Buy Now
            </Button>
          </Box>
        </Grid>
      </Grid>
    );
  };

  // Mobile Bottom Sheet
  if (isMobile) {
    return (
      <SwipeableDrawer
        anchor="bottom"
        open={isOpen}
        onClose={handleClose}
        onOpen={() => { }}
        disableSwipeToOpen
        slotProps={{
          paper: {
            sx: {
              maxHeight: '90vh',
              borderTopLeftRadius: 16,
              borderTopRightRadius: 16,
              overflow: 'hidden',
            },
          },
        }}
      >
        <Box sx={{ position: 'sticky', top: 0, zIndex: 2, display: 'flex', justifyContent: 'center', pt: 1.5, pb: 1, bgcolor: 'background.paper' }}>
          <Box sx={{ width: 40, height: 4, borderRadius: 2, bgcolor: 'grey.300' }} />
          <IconButton
            onClick={handleClose}
            sx={{ position: 'absolute', right: 8, top: 8 }}
            size="small"
            aria-label="close quick view"
          >
            <CloseIcon fontSize="small" />
          </IconButton>
        </Box>
        <Box sx={{ overflowY: 'auto', flex: 1 }}>
          {renderContent()}
        </Box>
      </SwipeableDrawer>
    );
  }

  // Desktop Modal
  return (
    <Dialog
      open={isOpen}
      onClose={handleClose}
      maxWidth="md"
      fullWidth
      slotProps={{
        paper: {
          sx: {
            height: '600px',
            borderRadius: 2,
            overflow: 'hidden',
            m: 2,
          },
        },
      }}
    >
      <DialogContent sx={{ p: 0, overflow: 'hidden' }}>
        {renderContent()}
      </DialogContent>
    </Dialog>
  );
};

export default QuickViewModal;
