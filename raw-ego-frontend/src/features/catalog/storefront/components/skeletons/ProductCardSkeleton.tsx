import React from 'react';
import { Box, Card, Skeleton } from '@mui/material';

const ProductCardSkeleton: React.FC = () => {
  return (
    <Card
      elevation={0}
      sx={{
        bgcolor: 'transparent',
        borderRadius: 0,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
        {/* Image Box matching 4/5 aspect ratio */}
        <Box sx={{ width: '100%', position: 'relative', aspectRatio: '4/5', mb: 1 }}>
          <Skeleton 
            variant="rectangular" 
            animation="wave" 
            sx={{ width: '100%', height: '100%' }} 
          />
        </Box>

        {/* Text Block */}
        <Box sx={{ width: '100%', px: 0.5 }}>
          {/* Category */}
          <Skeleton variant="text" animation="wave" width="40%" height={16} sx={{ mb: 0.5 }} />
          {/* Title */}
          <Skeleton variant="text" animation="wave" width="80%" height={20} sx={{ mb: 0.5 }} />
          {/* Price */}
          <Skeleton variant="text" animation="wave" width="30%" height={20} />
        </Box>
      </Box>
    </Card>
  );
};

export default ProductCardSkeleton;
