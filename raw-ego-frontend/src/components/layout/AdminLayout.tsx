/**
 * AdminLayout.tsx
 *
 * Admin panel layout — completely separate from the storefront.
 * Uses a left sidebar for navigation + top bar for context.
 *
 * Phase 6 will flesh this out with full sidebar links.
 * Phase 1: minimal structure that routes can plug into.
 */

import { Box, Typography, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, AppBar, Toolbar, Chip } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { Outlet, Link, useLocation } from 'react-router-dom';
import DashboardOutlinedIcon   from '@mui/icons-material/DashboardOutlined';
import InventoryOutlinedIcon   from '@mui/icons-material/InventoryOutlined';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import AssignmentReturnOutlinedIcon from '@mui/icons-material/AssignmentReturnOutlined';
import WarningAmberOutlinedIcon from '@mui/icons-material/WarningAmberOutlined';
import DiscountOutlinedIcon from '@mui/icons-material/DiscountOutlined';
import PersonOutlinedIcon      from '@mui/icons-material/PersonOutlined';
import LogoutIcon              from '@mui/icons-material/Logout';
import { useLogout }           from '@/hooks/useAuth';
import { useAuthStore }        from '@/store/authStore';
import { palette }             from '@/theme/palette';

const SIDEBAR_WIDTH = 240;

const ADMIN_NAV = [
  { label: 'Dashboard',  href: '/admin',             icon: <DashboardOutlinedIcon fontSize="small" /> },
  { label: 'Orders',     href: '/admin/orders',      icon: <ShoppingCartOutlinedIcon fontSize="small" /> },
  { label: 'Returns',    href: '/admin/returns',     icon: <AssignmentReturnOutlinedIcon fontSize="small" /> },
  { label: 'Products',   href: '/admin/products',    icon: <InventoryOutlinedIcon fontSize="small" /> },
  { label: 'Categories', href: '/admin/categories',  icon: <InventoryOutlinedIcon fontSize="small" /> },
  { label: 'Inventory',  href: '/admin/inventory',   icon: <WarningAmberOutlinedIcon fontSize="small" /> },
  { label: 'Coupons',    href: '/admin/coupons',     icon: <DiscountOutlinedIcon fontSize="small" /> },
  { label: 'Users',      href: '/admin/users',       icon: <PersonOutlinedIcon fontSize="small" /> },
] as const;


const AdminLayout = () => {
  const { user }                  = useAuthStore();
  const { mutate: logout }        = useLogout();
  const location                  = useLocation();

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      {/* Sidebar */}
      <Drawer
        variant="permanent"
        sx={{
          width:           SIDEBAR_WIDTH,
          flexShrink:      0,
          '& .MuiDrawer-paper': {
            width:           SIDEBAR_WIDTH,
            boxSizing:       'border-box',
            borderRight:     `1px solid ${palette.divider}`,
            bgcolor:         'surface.secondary', // Carbon surface
            color:           'text.primary', // Light text
          },
        }}
      >
        {/* Logo */}
        <Box sx={{ p: 3, borderBottom: (theme) => `1px solid ${alpha(theme.palette.text.primary, 0.1)}` }}>
          <Typography variant="productTitle" component={Link} to="/" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, textDecoration: 'none', color: 'inherit', }}>
            EGO
          </Typography>
          <Chip
            label="Admin"
            size="small"
            sx={{
              mt:             0.5,
              fontSize:       '0.6rem',
              height:         '18px',
              borderRadius:   0,
              bgcolor:        (theme) => alpha(theme.palette.text.primary, 0.15),
              color:          (theme) => alpha(theme.palette.text.primary, 0.8),
              letterSpacing:  '0.08em',
            }}
          />
        </Box>

        {/* Nav */}
        <List sx={{ pt: 2, px: 1 }}>
          {ADMIN_NAV.map((item) => {
            const active = location.pathname === item.href ||
              (item.href !== '/admin' && location.pathname.startsWith(item.href));
            return (
              <ListItem key={item.label} disablePadding sx={{ mb: 0.5 }}>
                <ListItemButton
                  component={Link}
                  to={item.href}
                  sx={{
                    borderRadius: 0,
                    py:           1.25,
                    px:           2,
                    bgcolor:      active ? (theme) => alpha(theme.palette.text.primary, 0.15) : 'transparent',
                    '&:hover':    { bgcolor: (theme) => alpha(theme.palette.text.primary, 0.1) },
                    transition:   'background-color 0.2s ease',
                  }}
                >
                  <ListItemIcon sx={{ color: 'inherit', minWidth: 36 }}>
                    {item.icon}
                  </ListItemIcon>
                  <ListItemText>
                    <Typography variant="metadata" sx={{ fontWeight: active ? 700 : 400 }}>
                      {item.label}
                    </Typography>
                  </ListItemText>
                </ListItemButton>
              </ListItem>
            );
          })}
        </List>

        {/* Logout at bottom */}
        <Box sx={{ mt: 'auto', p: 2, borderTop: (theme) => `1px solid ${alpha(theme.palette.text.primary, 0.1)}` }}>
          <ListItemButton
            onClick={() => logout()}
            sx={{ borderRadius: 0, py: 1, '&:hover': { bgcolor: (theme) => alpha(theme.palette.text.primary, 0.1) } }}
          >
            <ListItemIcon sx={{ color: 'inherit', minWidth: 36 }}>
              <LogoutIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>
              <Typography variant="metadata" >
                Logout
              </Typography>
            </ListItemText>
          </ListItemButton>
        </Box>
      </Drawer>

      {/* Main content */}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {/* Top bar */}
        <AppBar
          position="sticky"
          sx={{ bgcolor: 'surface.secondary', borderBottom: `1px solid ${palette.divider}`, zIndex: 1 }}
        >
          <Toolbar>
            <Typography variant="subtitle2" color="text.secondary" sx={{ flex: 1 }}>
              ADMIN PANEL
            </Typography>
            <Typography variant="metadata" >
              {user?.firstName} {user?.lastName}
            </Typography>
          </Toolbar>
        </AppBar>

        {/* Page content */}
        <Box component="main" sx={{ flex: 1, p: { xs: 2, md: 4 }, bgcolor: 'background.default' }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
};

export default AdminLayout;
