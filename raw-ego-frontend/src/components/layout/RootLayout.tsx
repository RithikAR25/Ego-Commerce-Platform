/**
 * RootLayout.tsx
 *
 * Main layout for all storefront pages (public + customer).
 * Renders: AnnouncementBar → Navbar → <Outlet> (page content) → Footer
 *
 * The <Outlet> is where React Router renders the matched child route.
 * This layout is shared by home, product listing, product detail,
 * account pages, etc.
 */

import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import AnnouncementBar from './AnnouncementBar';
import Navbar          from './Navbar';
import Footer          from './Footer';
import CartDrawer      from './CartDrawer';
import EmailVerificationBanner from '@/features/auth/components/EmailVerificationBanner';
import QuickViewModal from '@/features/catalog/storefront/components/QuickViewModal';

const RootLayout = () => {
  return (
    <Box
      sx={{
        minHeight:     '100vh',
        display:       'flex',
        flexDirection: 'column',
        bgcolor:       'background.default',
      }}
    >
      <Box
        component="a"
        href="#main-content"
        sx={{
          position: 'absolute',
          top: '-1000px',
          left: '-1000px',
          height: '1px',
          width: '1px',
          overflow: 'hidden',
          '&:focus': {
            position: 'static',
            height: 'auto',
            width: 'auto',
            p: 2,
            bgcolor: 'background.paper',
            color: 'text.primary',
            zIndex: 9999,
          },
        }}
      >
        Skip to main content
      </Box>
      <AnnouncementBar />
      <Navbar />
      <CartDrawer />
      <EmailVerificationBanner />
      <QuickViewModal />

      {/* Page content */}
      <Box component="main" id="main-content" sx={{ flex: 1, '&:focus': { outline: 'none' } }} tabIndex={-1}>
        <Outlet />
      </Box>

      <Footer />
    </Box>
  );
};

export default RootLayout;
