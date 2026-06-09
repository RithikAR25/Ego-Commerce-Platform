/**
 * CheckoutLayout.tsx
 *
 * Distraction-free checkout layout.
 * Design principle: Remove ALL navigation during checkout to prevent cart abandonment.
 * The user should only see the logo and the checkout steps.
 *
 * Used by: /checkout, /checkout/success/:id
 */

import { Box, Typography } from '@mui/material';
import { Outlet, Link } from 'react-router-dom';
import { palette } from '@/theme/palette';

const CheckoutLayout = () => {
  return (
    <Box
      sx={{
        minHeight:     '100vh',
        display:       'flex',
        flexDirection: 'column',
        bgcolor:       'background.default',
      }}
    >
      {/* Minimal header — logo only */}
      <Box
        component="header"
        sx={{
          borderBottom: `1px solid ${palette.divider}`,
          py:           2,
          px:           { xs: 2, md: 4 },
          display:      'flex',
          alignItems:   'center',
          justifyContent: 'center',
        }}
      >
        <Typography variant="productTitle" component={Link} to="/" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', textDecoration:'none', }}>
          EGO
        </Typography>
      </Box>

      {/* Checkout content */}
      <Box component="main" sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        <Outlet />
      </Box>
    </Box>
  );
};

export default CheckoutLayout;
