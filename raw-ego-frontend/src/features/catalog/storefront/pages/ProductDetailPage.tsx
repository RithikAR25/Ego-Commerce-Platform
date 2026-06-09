/**
 * ProductDetailPage.tsx
 *
 * Storefront Product Detail Page.
 * Implements the locked image behavior defined in docs/frontend/image-rendering.md.
 */

import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Box, Container, Grid, Typography, Button, Divider, ButtonGroup } from '@mui/material';
import AddIcon    from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import { useProductDetail } from '@/hooks/useCatalog';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import type { ProductVariantResponse } from '@/types/catalog.types';
import ProductBreadcrumb from '../components/ProductBreadcrumb';
import ImageGallery from '../components/ImageGallery';
import VariantSelector from '../components/VariantSelector';
import StockUrgencyIndicator from '../components/StockUrgencyIndicator';
import { useAddToCart } from '@/features/cart/hooks/useCart';
import { motion } from 'framer-motion';
import WishlistButton from '@/features/wishlist/components/WishlistButton';
import ProductReviewsSection from '@/features/reviews/components/ProductReviewsSection';
import { Helmet } from 'react-helmet-async';
import { useRecentlyViewed } from '@/hooks/useRecentlyViewed';
import RecentlyViewedSection from '../components/RecentlyViewedSection';
import ProductDetailSkeleton from '../components/skeletons/ProductDetailSkeleton';

const ProductDetailPage: React.FC = () => {
  const { slug } = useParams<{ slug: string }>();
  const { data: product, isLoading } = useProductDetail(slug ?? '');

  const [selectedVariant, setSelectedVariant] = useState<ProductVariantResponse | null>(null);
  const [quantity,         setQuantity]         = useState(1);
  const { mutate: addToCart, isPending: isAdding } = useAddToCart();
  const addViewedItem = useRecentlyViewed((state) => state.addViewedItem);

  // Initialize selected variant when product loads
  useEffect(() => {
    if (product && product.variants.length > 0 && !selectedVariant) {
      const inStock = product.variants.find(v => v.active && v.stockStatus !== 'OUT_OF_STOCK');
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setSelectedVariant(inStock || product.variants[0]);
    }
    // Reset quantity when variant changes
    setQuantity(1);
  }, [product, selectedVariant]);

  // Track Recently Viewed
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

  if (isLoading) {
    return (
      <Container maxWidth="xl" sx={{ py: 6 }}>
        <ProductDetailSkeleton />
      </Container>
    );
  }

  if (!product || !selectedVariant) {
    return (
      <Container sx={{ py: 10, textAlign: 'center' }}>
        <Typography variant="h5">Product not found</Typography>
      </Container>
    );
  }

  const isOutOfStock = selectedVariant.stockStatus === 'OUT_OF_STOCK';

  const baseUrl = window.location.origin;
  const canonicalUrl = `${baseUrl}/products/${product.slug}`;
  const defaultImage = selectedVariant.images[0]?.url || product.galleryImages[0]?.url || '';

  const jsonLd = {
    "@context": "https://schema.org/",
    "@type": "Product",
    "name": product.name,
    "image": defaultImage,
    "description": product.description || product.name,
    "sku": selectedVariant.sku,
    "offers": {
      "@type": "Offer",
      "url": canonicalUrl,
      "priceCurrency": "INR",
      "price": selectedVariant.price,
      "availability": isOutOfStock ? "https://schema.org/OutOfStock" : "https://schema.org/InStock"
    }
  };

  return (
    <Container maxWidth="xl" sx={{ py: { xs: 4, md: 6 } }}>
      <Helmet>
        <title>{product.name} | EGO</title>
        <meta name="description" content={product.description || `Buy ${product.name} at EGO.`} />
        <link rel="canonical" href={canonicalUrl} />
        <meta property="og:title" content={`${product.name} | EGO`} />
        <meta property="og:description" content={product.description || `Buy ${product.name} at EGO.`} />
        <meta property="og:image" content={defaultImage} />
        <meta property="og:url" content={canonicalUrl} />
        <meta property="og:type" content="product" />
        <meta property="product:price:amount" content={selectedVariant.price.toString()} />
        <meta property="product:price:currency" content="INR" />
        <script type="application/ld+json">
          {JSON.stringify(jsonLd)}
        </script>
      </Helmet>

      <ProductBreadcrumb category={product.category} productName={product.name} />

      <Grid
        container
        spacing={8}
        component={motion.div}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: 'easeOut' }}
      >
        {/* Left Column: Images */}
        <Grid size={{ xs: 12, md: 7 }}>
          <ImageGallery
            variantImages={selectedVariant.images}
            galleryImages={product.galleryImages}
          />
        </Grid>

        {/* Right Column: Details & Actions */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Box sx={{ position: 'sticky', top: 100 }}>
            {/* Header */}
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
              <Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
                  <Typography variant="metadata" sx={{ color: 'text.secondary', }}>
                    Archive Index
                  </Typography>
                  <Box sx={{ width: 32, height: '1px', bgcolor: 'divider' }} />
                  <Typography variant="metadata" sx={{ color: 'text.primary', }}>
                    {product.category?.name || 'OBJ.'}
                  </Typography>
                </Box>
                <Typography variant="h1" sx={{ fontWeight: 800, fontSize: { xs: '2rem', md: '3rem' }, lineHeight: 1.1, textTransform: 'uppercase', mb: 2 }}>
                  {product.name}
                </Typography>
                <Typography variant="metadata" sx={{ color: 'text.secondary', }}>
                  EGO ARCHIVE
                </Typography>
              </Box>
              <Box sx={{ pt: 1 }}>
                <WishlistButton variantId={selectedVariant.id} size="large" edge="end" />
              </Box>
            </Box>
            
            <Box sx={{ w: '100%', h: '1px', bgcolor: 'divider', mb: 4, mt: 4 }} />
            
            {/* Price */}
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, mb: 5 }}>
              <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary', }}>
                ₹{selectedVariant.price.toLocaleString('en-IN')}
              </Typography>
              {selectedVariant.compareAtPrice && (
                <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', textDecoration: 'line-through', }}>
                  ₹{selectedVariant.compareAtPrice.toLocaleString('en-IN')}
                </Typography>
              )}
              {selectedVariant.discountPercent && selectedVariant.discountPercent > 0 && (
                <Typography
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.72rem',
                    letterSpacing: '0.1em',
                    color: 'error.main',
                    border: '1px solid',
                    borderColor: 'error.main',
                    px: 1,
                    py: 0.25,
                  }}
                >
                  {selectedVariant.discountPercent}% OFF
                </Typography>
              )}
            </Box>

            <Divider sx={{ mb: 4 }} />

            {/* Variant Selector */}
            <VariantSelector
              variants={product.variants}
              attributeTypes={product.attributeTypes}
              selectedVariant={selectedVariant}
              onSelectVariant={setSelectedVariant}
            />

            {/* Stock Urgency Indicator */}
            <StockUrgencyIndicator variant={selectedVariant} />

            {/* Add to Cart */}
            <Box sx={{ mt: 6, mb: 4 }}>
              {isOutOfStock ? (
                <Button
                  fullWidth
                  variant="contained"
                  size="large"
                  disabled
                  sx={{ py: 2.5, bgcolor: 'divider', color: 'text.secondary', fontWeight: 600, letterSpacing: '0.05em' }}
                >
                  Sold Out
                </Button>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                  {/* Quantity selector */}
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, pb: 3, borderBottom: '1px solid', borderColor: 'divider' }}>
                    <Typography variant="metadata" sx={{ minWidth: 60 }}>QTY</Typography>
                    <ButtonGroup size="small" sx={{ '& .MuiButtonBase-root': { borderColor: 'divider', color: 'text.primary' } }}>
                      <Button onClick={() => setQuantity((q) => Math.max(1, q - 1))} disabled={quantity <= 1}>
                        <RemoveIcon fontSize="small" />
                      </Button>
                      <Button disableRipple sx={{ pointerEvents: 'none', minWidth: 48, fontWeight: 600 }}>
                        {quantity}
                      </Button>
                      <Button
                        onClick={() => setQuantity((q) => Math.min(selectedVariant?.stockStatus !== 'OUT_OF_STOCK' ? 10 : 1, Math.min(10, q + 1)))}
                        disabled={quantity >= (selectedVariant?.stockStatus !== 'OUT_OF_STOCK' ? 10 : 1)}
                      >
                        <AddIcon fontSize="small" />
                      </Button>
                    </ButtonGroup>
                    <Typography variant="metadata" sx={{ color: 'success.main', ml: 'auto' }}>
                      {selectedVariant?.stockStatus !== 'OUT_OF_STOCK' ? 'AVAILABLE' : 'SOLD OUT'}
                    </Typography>
                  </Box>

                  {/* Add to cart button */}
                  <Button
                    fullWidth
                    variant="contained"
                    size="large"
                    disabled={isAdding}
                    onClick={() => {
                      if (!selectedVariant) return;
                      addToCart({ variantId: selectedVariant.id, quantity });
                    }}
                    sx={{
                      py: 2.5,
                      bgcolor: 'primary.main',
                      color: 'primary.contrastText',
                      fontWeight: 600,
                      letterSpacing: '0.05em',
                      textTransform: 'uppercase',
                      transition: 'all 0.3s',
                      display: 'flex',
                      justifyContent: 'space-between',
                      px: 4,
                      '&:hover': { bgcolor: 'primary.dark' },
                      '&.Mui-disabled': { bgcolor: 'divider', color: 'text.disabled', borderColor: 'divider' },
                    }}
                  >
                    <span>{isAdding ? 'ADDING...' : 'ADD TO ARCHIVE'}</span>
                    {!isAdding && <ArrowForwardIcon sx={{ transition: 'transform 0.3s', '.MuiButton-root:hover &': { transform: 'translateX(4px)' } }} />}
                  </Button>
                </Box>
              )}
            </Box>

            {/* Product Details / Specifications */}
            <Box sx={{ mt: 6, borderTop: '1px solid', borderColor: 'divider' }}>
              {product.description && (
                <Box sx={{ py: 3, borderBottom: '1px solid', borderColor: 'divider', display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="metadata" sx={{ width: '30%', color: 'text.secondary' }}>
                    DETAILS
                  </Typography>
                  <Typography variant="body1" sx={{ width: '70%', textAlign: 'right', color: 'text.primary', lineHeight: 1.6 }}>
                    {product.description}
                  </Typography>
                </Box>
              )}

              {product.material && (
                <Box sx={{ py: 3, borderBottom: '1px solid', borderColor: 'divider', display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="metadata" sx={{ width: '30%', color: 'text.secondary' }}>
                    MATERIAL
                  </Typography>
                  <Typography variant="body1" sx={{ width: '70%', textAlign: 'right', color: 'text.primary' }}>
                    {product.material}
                  </Typography>
                </Box>
              )}

              {product.careInstructions && (
                <Box sx={{ py: 3, borderBottom: '1px solid', borderColor: 'divider', display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="metadata" sx={{ width: '30%', color: 'text.secondary' }}>
                    CARE
                  </Typography>
                  <Typography variant="body1" sx={{ width: '70%', textAlign: 'right', color: 'text.primary' }}>
                    {product.careInstructions}
                  </Typography>
                </Box>
              )}
            </Box>
          </Box>
        </Grid>
      </Grid>

      {/* Reviews Section */}
      <Box sx={{ mt: 10 }}>
        <Divider />
        <ProductReviewsSection productId={product.id} />
      </Box>

      {/* Recently Viewed Section */}
      <RecentlyViewedSection currentProductId={product.id} />

      {/* Sticky Mobile Add To Cart Banner */}
      {!isOutOfStock && (
        <Box
          sx={{
            display: { xs: 'flex', md: 'none' },
            position: 'fixed',
            bottom: 0,
            left: 0,
            right: 0,
            p: 2,
            bgcolor: 'background.paper',
            borderTop: '1px solid',
            borderColor: 'divider',
            zIndex: 1000,
            alignItems: 'center',
            gap: 2,
          }}
        >
          <Box sx={{ flex: 1 }}>
            <Typography variant="subtitle2" noWrap>{product.name}</Typography>
            <Typography variant="metadata" >₹{selectedVariant.price}</Typography>
          </Box>
          <Button
            variant="contained"
            disabled={isAdding}
            onClick={() => addToCart({ variantId: selectedVariant.id, quantity })}
            sx={{
              bgcolor: 'primary.main',
              color: 'primary.contrastText',
              fontWeight: 700,
              px: 4,
              py: 1.5,
              textTransform: 'uppercase',
            }}
          >
            {isAdding ? 'Adding...' : 'Add to Cart'}
          </Button>
        </Box>
      )}
    </Container>
  );
};

export default ProductDetailPage;
