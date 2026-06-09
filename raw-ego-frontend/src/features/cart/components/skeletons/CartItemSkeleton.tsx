import React from 'react';
import { Box, Skeleton } from '@mui/material';

interface Props {
  variant?: 'drawer' | 'page';
}

const CartItemSkeleton: React.FC<Props> = ({ variant = 'page' }) => {
  const isDrawer = variant === 'drawer';

  return (
    <Box sx={{ display: 'flex', gap: isDrawer ? 2 : 3, py: isDrawer ? 2 : 4, mb: isDrawer ? 0 : 4 }}>
      {/* Image Block */}
      <Skeleton 
        variant="rectangular" 
        animation="wave" 
        width={isDrawer ? 80 : 120} 
        height={isDrawer ? 100 : 150} 
      />
      
      {/* Details Block */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        <Skeleton variant="text" animation="wave" width={isDrawer ? '80%' : '70%'} height={isDrawer ? 20 : 28} />
        <Skeleton variant="text" animation="wave" width="40%" height={20} sx={{ mt: isDrawer ? 0 : 1 }} />
        {!isDrawer && <Skeleton variant="text" animation="wave" width="30%" height={20} sx={{ mt: 2 }} />}
        <Skeleton 
          variant="rectangular" 
          animation="wave" 
          width={isDrawer ? '60%' : '50%'} 
          height={isDrawer ? 30 : 44} 
          sx={{ mt: isDrawer ? 2 : 3, borderRadius: 0 }} 
        />
      </Box>
    </Box>
  );
};

export default CartItemSkeleton;
