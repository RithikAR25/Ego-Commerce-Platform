import React from 'react';
import { Box, Paper, Skeleton, Divider } from '@mui/material';

const CartSummarySkeleton: React.FC = () => {
  return (
    <Paper
      elevation={0}
      sx={{
        border: '1.5px solid',
        borderColor: 'divider',
        borderRadius: 0,
        p: 3,
        position: { md: 'sticky' },
        top: { md: 96 },
      }}
    >
      <Skeleton variant="text" animation="wave" width="60%" height={32} sx={{ mb: 3 }} />
      
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Skeleton variant="text" animation="wave" width="40%" height={24} />
          <Skeleton variant="text" animation="wave" width="30%" height={24} />
        </Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
          <Skeleton variant="text" animation="wave" width="30%" height={24} />
          <Skeleton variant="rectangular" animation="wave" width={50} height={20} sx={{ borderRadius: 1 }} />
        </Box>
      </Box>

      <Divider sx={{ my: 3 }} />

      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 4 }}>
        <Skeleton variant="text" animation="wave" width="30%" height={32} />
        <Skeleton variant="text" animation="wave" width="40%" height={40} />
      </Box>

      <Skeleton variant="rectangular" animation="wave" width="100%" height={56} sx={{ borderRadius: 0, mb: 2 }} />
      
      <Skeleton variant="text" animation="wave" width="80%" height={20} sx={{ mx: 'auto' }} />
    </Paper>
  );
};

export default CartSummarySkeleton;
