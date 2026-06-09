import React from 'react';
import { Box, Grid, Skeleton } from '@mui/material';

const QuickViewSkeleton: React.FC = () => {
  return (
    <Grid container spacing={4} sx={{ height: '100%' }}>
      {/* Left Column: Image Skeleton */}
      <Grid size={{ xs: 12, md: 6 }} sx={{ height: { xs: 400, md: '100%' } }}>
        <Skeleton variant="rectangular" width="100%" height="100%" animation="wave" />
      </Grid>

      {/* Right Column: Details Skeleton */}
      <Grid size={{ xs: 12, md: 6 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, pt: { xs: 2, md: 4 }, pb: 4, pr: { md: 4 } }}>
          {/* Title & Price */}
          <Box>
            <Skeleton variant="text" width="80%" height={40} animation="wave" />
            <Skeleton variant="text" width="40%" height={30} animation="wave" sx={{ mt: 1 }} />
          </Box>

          <Skeleton variant="rectangular" width="100%" height={1} animation="wave" />

          {/* Color Selector */}
          <Box>
            <Skeleton variant="text" width={60} height={20} animation="wave" sx={{ mb: 1 }} />
            <Box sx={{ display: 'flex', gap: 1.5 }}>
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} variant="circular" width={40} height={40} animation="wave" />
              ))}
            </Box>
          </Box>

          {/* Size Selector */}
          <Box>
            <Skeleton variant="text" width={40} height={20} animation="wave" sx={{ mb: 1 }} />
            <Box sx={{ display: 'flex', gap: 1.5 }}>
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} variant="rectangular" width={50} height={40} animation="wave" />
              ))}
            </Box>
          </Box>

          {/* Add to Cart Area */}
          <Box sx={{ mt: 'auto', pt: 4 }}>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <Skeleton variant="rectangular" width={100} height={50} animation="wave" />
              <Skeleton variant="rectangular" width="100%" height={50} animation="wave" />
            </Box>
          </Box>
        </Box>
      </Grid>
    </Grid>
  );
};

export default QuickViewSkeleton;
