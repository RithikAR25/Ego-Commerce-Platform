/**
 * Footer.tsx
 *
 * Modern Premium Footer for EGO E-commerce
 */

import { Box, Typography, Container, Grid, Link as MuiLink } from '@mui/material';
import { Link } from 'react-router-dom';

const Footer = () => {
  return (
    <Box
      component="footer"
      sx={{
        mt: 'auto',
        w: '100%',
        py: { xs: 8, md: 12 },
        px: { xs: 2.5, md: 8 },
        borderTop: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.default',
        color: 'text.secondary',
      }}
    >
      <Container maxWidth="xl">
        <Grid container spacing={{ xs: 4, md: 3 }} sx={{ justifyContent: 'space-between' }}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Typography
              variant="h3"
              sx={{
                color: 'text.primary',
                mb: 2,
              }}
            >
              EGO
            </Typography>
            <Typography variant="body1" sx={{ maxWidth: 300, mb: 3 }}>
              Premium streetwear for the modern individual. Elevate your aesthetic with our curated collections.
            </Typography>
          </Grid>

          <Grid size={{ xs: 12, sm: 4, md: 2 }}>
            <Typography variant="metadata" sx={{ color: 'text.primary', mb: 2 }}>
              Shop
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <MuiLink component={Link} to="/products" color="inherit" underline="hover">All Products</MuiLink>
              <MuiLink component={Link} to="/products?category=new-arrivals" color="inherit" underline="hover">New Arrivals</MuiLink>
              <MuiLink component={Link} to="/products?category=best-sellers" color="inherit" underline="hover">Best Sellers</MuiLink>
            </Box>
          </Grid>

          <Grid size={{ xs: 12, sm: 4, md: 2 }}>
            <Typography variant="metadata" sx={{ color: 'text.primary', mb: 2 }}>
              Support
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <MuiLink component={Link} to="/contact" color="inherit" underline="hover">Contact Us</MuiLink>
              <MuiLink component={Link} to="/faq" color="inherit" underline="hover">FAQ</MuiLink>
              <MuiLink component={Link} to="/shipping" color="inherit" underline="hover">Shipping & Returns</MuiLink>
            </Box>
          </Grid>

          <Grid size={{ xs: 12, sm: 4, md: 2 }}>
            <Typography variant="metadata" sx={{ color: 'text.primary', mb: 2 }}>
              Legal
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <MuiLink component={Link} to="/privacy" color="inherit" underline="hover">Privacy Policy</MuiLink>
              <MuiLink component={Link} to="/terms" color="inherit" underline="hover">Terms of Service</MuiLink>
            </Box>
          </Grid>
        </Grid>

        <Box sx={{ mt: 8, pt: 3, borderTop: '1px solid', borderColor: 'divider', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
          <Typography variant="body2">
            © {new Date().getFullYear()} EGO. All rights reserved.
          </Typography>
        </Box>
      </Container>
    </Box>
  );
};

export default Footer;
