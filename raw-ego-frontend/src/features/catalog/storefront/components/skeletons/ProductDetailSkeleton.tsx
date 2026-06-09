import React from 'react';
import { Box, Grid, Skeleton } from '@mui/material';

const ProductDetailSkeleton: React.FC = () => {
  return (
    <Grid container spacing={8}>
      {/* Left Column: Images */}
      <Grid size={{ xs: 12, md: 7 }}>
        <Box sx={{ display: 'flex', flexDirection: { xs: 'column-reverse', md: 'row' }, gap: 2 }}>
          {/* Thumbnails */}
          <Box sx={{ display: 'flex', flexDirection: { xs: 'row', md: 'column' }, gap: 2, flexShrink: 0 }}>
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} variant="rectangular" animation="wave" sx={{ width: { xs: 60, md: 80 }, height: { xs: 80, md: 100 } }} />
            ))}
          </Box>
          {/* Main Image */}
          <Skeleton variant="rectangular" animation="wave" sx={{ flexGrow: 1, aspectRatio: '4/5', minHeight: 400 }} />
        </Box>
      </Grid>

      {/* Right Column: Details & Actions */}
      <Grid size={{ xs: 12, md: 5 }}>
        <Box sx={{ position: 'sticky', top: 100 }}>
          {/* Header */}
          <Skeleton variant="text" animation="wave" width="80%" height={60} sx={{ mb: 1 }} />
          
          {/* Price */}
          <Skeleton variant="text" animation="wave" width="40%" height={40} sx={{ mb: 4 }} />
          
          <Skeleton variant="rectangular" animation="wave" width="100%" height={1} sx={{ mb: 4 }} />

          {/* Variant Selector */}
          <Box sx={{ mb: 4 }}>
            <Skeleton variant="text" animation="wave" width="20%" height={24} sx={{ mb: 1 }} />
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Skeleton variant="circular" animation="wave" width={40} height={40} />
              <Skeleton variant="circular" animation="wave" width={40} height={40} />
              <Skeleton variant="circular" animation="wave" width={40} height={40} />
            </Box>
          </Box>
          <Box sx={{ mb: 4 }}>
            <Skeleton variant="text" animation="wave" width="20%" height={24} sx={{ mb: 1 }} />
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Skeleton variant="rectangular" animation="wave" width={60} height={40} sx={{ borderRadius: 1 }} />
              <Skeleton variant="rectangular" animation="wave" width={60} height={40} sx={{ borderRadius: 1 }} />
              <Skeleton variant="rectangular" animation="wave" width={60} height={40} sx={{ borderRadius: 1 }} />
            </Box>
          </Box>

          {/* Stock Urgency */}
          <Skeleton variant="text" animation="wave" width="50%" height={20} sx={{ mb: 6 }} />

          {/* Add to Cart */}
          <Box sx={{ mt: 6, mb: 4, display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Skeleton variant="text" animation="wave" width={40} height={24} />
              <Skeleton variant="rectangular" animation="wave" width={120} height={40} />
              <Skeleton variant="text" animation="wave" width={80} height={24} />
            </Box>
            <Skeleton variant="rectangular" animation="wave" width="100%" height={56} sx={{ borderRadius: 0 }} />
          </Box>

          {/* Product Details Text */}
          <Box sx={{ mt: 6 }}>
            <Box sx={{ mb: 4 }}>
              <Skeleton variant="text" animation="wave" width="30%" height={24} sx={{ mb: 1 }} />
              <Skeleton variant="text" animation="wave" width="100%" height={20} />
              <Skeleton variant="text" animation="wave" width="95%" height={20} />
              <Skeleton variant="text" animation="wave" width="90%" height={20} />
            </Box>
            <Box sx={{ mb: 4 }}>
              <Skeleton variant="text" animation="wave" width="30%" height={24} sx={{ mb: 1 }} />
              <Skeleton variant="text" animation="wave" width="80%" height={20} />
            </Box>
          </Box>
        </Box>
      </Grid>
    </Grid>
  );
};

export default ProductDetailSkeleton;
