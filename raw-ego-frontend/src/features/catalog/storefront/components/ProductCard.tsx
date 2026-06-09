/**
 * ProductCard.tsx
 *
 * Storefront grid card. Shows product image, title, and pricing.
 */

import React from 'react';
import { Link } from 'react-router-dom';
import { Box, Typography, Card, Chip, IconButton } from '@mui/material';
import { motion } from 'framer-motion';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { useQueryClient } from '@tanstack/react-query';
import type { ProductSummaryResponse } from '@/types/catalog.types';
import { productKeys } from '@/hooks/queryKeys';
import { getProductBySlug } from '@/api/catalog.api';
import { useQuickViewStore } from '@/store/quickViewStore';

interface Props {
  product: ProductSummaryResponse;
}

const ProductCard: React.FC<Props> = ({ product }) => {
  const isOutOfStock = product.status === 'OUT_OF_STOCK';
  const queryClient = useQueryClient();
  const openQuickView = useQuickViewStore((s) => s.openQuickView);

  const prefetchProduct = () => {
    queryClient.prefetchQuery({
      queryKey: productKeys.detail(product.slug),
      queryFn: () => getProductBySlug(product.slug),
      staleTime: 2 * 60 * 1000,
    });
  };

  const handleQuickView = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    openQuickView(product.slug);
  };

  return (
    <Card
      elevation={0}
      component={motion.div}
      whileHover={{ y: -4 }}
      transition={{ duration: 0.2 }}
      onMouseEnter={prefetchProduct}
      onTouchStart={prefetchProduct}
      sx={{
        bgcolor: 'background.paper',
        borderRadius: '12px',
        overflow: 'hidden',
        height: '100%',
        display: 'block',
        textDecoration: 'none',
        border: '1px solid',
        borderColor: 'divider',
        '&:hover .product-image': {
          transform: 'scale(1.05)',
        },
        '&:hover .product-actions': {
          opacity: 1,
          transform: 'translateY(0)',
        },
      }}
    >
      <Box
        component={Link}
        to={`/products/${product.slug}`}
        sx={{ display: 'block', textDecoration: 'none', color: 'inherit' }}
      >
        <Box sx={{ width: '100%', position: 'relative', aspectRatio: '3/4', mb: 2, bgcolor: 'background.paper', overflow: 'hidden' }}>
          {product.primaryImageUrl ? (
            <img
              src={product.primaryImageUrl}
              alt={product.name}
              loading="lazy"
              className="product-image"
              style={{
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                opacity: isOutOfStock ? 0.5 : 1,
                transition: 'transform 0.5s ease-in-out',
              }}
            />
          ) : (
            <Box sx={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Typography variant="body2" color="text.secondary">No Image</Typography>
            </Box>
          )}

          {isOutOfStock && (
            <Box sx={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }}>
              <Chip label="SOLD OUT" size="small" sx={{ bgcolor: 'overlay.dark', color: 'background.paper', fontWeight: 600, letterSpacing: '0.05em' }} />
            </Box>
          )}

          {/* Actions Overlay */}
          <Box
            className="product-actions"
            sx={{
              position: 'absolute',
              bottom: { xs: 8, md: 16 },
              right: { xs: 8, md: 16 },
              display: 'flex',
              flexDirection: 'column',
              gap: 1,
              opacity: { xs: 1, md: 0 },
              transform: { xs: 'none', md: 'translateY(10px)' },
              transition: 'all 0.3s ease',
            }}
          >
            <IconButton
              onClick={handleQuickView}
              sx={{ bgcolor: 'background.paper', boxShadow: 1, '&:hover': { bgcolor: 'grey.100' } }}
              size="small"
              aria-label={`Quick view ${product.name}`}
            >
              <VisibilityOutlinedIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>

        </Box>

        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', p: 2 }}>
          <Box>
            <Typography variant="bodyLarge" sx={{ color: 'text.primary', fontFamily: (theme) => theme.typography.fontFamilyDisplay, }}>
              {product.name}
            </Typography>
            <Typography variant="button" sx={{ color: 'secondary.main', mt: 0.5, display: 'block' }}>
              {product.categoryName}
            </Typography>
          </Box>
          <Typography variant="button" sx={{ color: 'secondary.main', textAlign: 'right' }}>
            {product.minPrice != null && product.maxPrice != null
              ? product.minPrice === product.maxPrice
                ? `$${product.minPrice}`
                : `$${product.minPrice} - $${product.maxPrice}`
              : '-'}
          </Typography>
        </Box>
    </Card>
  );
};

export default ProductCard;
