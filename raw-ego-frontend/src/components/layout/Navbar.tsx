/**
 * Navbar.tsx
 *
 * Main navigation bar with live Elasticsearch autocomplete search
 * and a full-width 3-level enterprise mega-menu (ROOT → GROUP → LEAF).
 *
 * Desktop:
 *   - Frosted glass background (backdrop-filter blur) that transitions on scroll
 *   - Left: ROOT category triggers → hover opens full-width MegaMenu panel
 *   - Center: EGO wordmark logo
 *   - Right: Search bar, Account, Wishlist, Cart
 *
 * Mobile:
 *   - Hamburger menu (MUI Drawer from left)
 *   - 3-level accordion: ROOT → expand GROUP list → expand LEAF list
 *   - Centered logo
 *   - Search icon + Cart badge + Account icon on right
 */

import {
  AppBar,
  Box,
  Toolbar,
  IconButton,
  Typography,
  Button,
  Avatar,
  Menu,
  MenuItem,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  Collapse,
  Badge,
} from '@mui/material';
import React, { useState, useCallback, useRef } from 'react';
import MenuIcon                   from '@mui/icons-material/Menu';
import ShoppingBagOutlinedIcon    from '@mui/icons-material/ShoppingBagOutlined';
import PersonOutlinedIcon         from '@mui/icons-material/PersonOutlined';
import FavoriteBorderOutlinedIcon from '@mui/icons-material/FavoriteBorderOutlined';
import CloseIcon                  from '@mui/icons-material/Close';
import SearchIcon                 from '@mui/icons-material/Search';
import ExpandMoreIcon             from '@mui/icons-material/ExpandMore';
import ExpandLessIcon             from '@mui/icons-material/ExpandLess';
import { Link, useNavigate }      from 'react-router-dom';
import { useAuthStore }           from '@/store/authStore';
import { useTheme }               from '@mui/material/styles';
import { useLogout }              from '@/hooks/useAuth';
import { useUiStore }             from '@/store/uiStore';
import { useNavigationTree }      from '@/hooks/useCatalog';
import DesktopSearch              from '@/features/search/components/DesktopSearch';
import MobileSearchSheet          from '@/features/search/components/MobileSearchSheet';
import MegaMenu                   from '@/features/navigation/components/MegaMenu';
import type { CategoryTreeResponse } from '@/types/catalog.types';
import { motion }                 from 'framer-motion';

// ── Navbar ────────────────────────────────────────────────────────────────────

const Navbar = () => {
  const navigate      = useNavigate();
  const theme         = useTheme();
  const { isAuthenticated, user } = useAuthStore();
  const { mutate: logout, isPending: isLoggingOut } = useLogout();
  const { openCartDrawer } = useUiStore();
  const { data: treeData } = useNavigationTree();

  // ── User account menu ──────────────────────────────────────────────────────
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const userMenuOpen = Boolean(anchorEl);
  const handleUserMenuOpen  = (e: React.MouseEvent<HTMLElement>) => setAnchorEl(e.currentTarget);
  const handleUserMenuClose = () => setAnchorEl(null);
  const handleLogout = () => { handleUserMenuClose(); logout(); };

  // ── Mobile drawer ──────────────────────────────────────────────────────────
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);

  // Mobile accordion state: which ROOT and GROUP are expanded
  const [expandedRootId, setExpandedRootId] = useState<number | null>(null);
  const [expandedGroupId, setExpandedGroupId] = useState<number | null>(null);

  // ── Desktop mega-menu state ────────────────────────────────────────────────
  // activeRootSlug drives which ROOT panel is open; managed via hover with delay
  const [activeMegaRoot, setActiveMegaRoot] = useState<CategoryTreeResponse | null>(null);
  const hideTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const openMegaMenu = useCallback((root: CategoryTreeResponse) => {
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    setActiveMegaRoot(root);
  }, []);

  const scheduleMegaMenuClose = useCallback(() => {
    hideTimerRef.current = setTimeout(() => {
      setActiveMegaRoot(null);
    }, 150); // short delay so cursor can move from button to panel
  }, []);

  const cancelMegaMenuClose = useCallback(() => {
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
  }, []);

  const closeMegaMenu = useCallback(() => {
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    setActiveMegaRoot(null);
  }, []);

  return (
    <>
      <AppBar
        position="sticky"
        sx={{
          top: 0,
          borderBottom: `1px solid ${theme.palette.divider}`,
          transition: 'none',
          zIndex: (theme) => theme.zIndex.appBar,
          bgcolor: 'background.default',
        }}
      >
        <Toolbar sx={{ gap: 2 }}>
          {/* Mobile hamburger */}
          <IconButton
            onClick={() => setMobileDrawerOpen(true)}
            sx={{ display: { md: 'none' }, mr: 1 }}
            aria-label="Open menu"
          >
            <MenuIcon />
          </IconButton>

          {/* Desktop nav links (left) — ROOT triggers */}
          <Box sx={{ display: { xs: 'none', md: 'flex' }, gap: 3, flex: 1 }}>
            {treeData?.map((root) => (
              <Box
                key={root.id}
                onMouseEnter={() => openMegaMenu(root)}
                onMouseLeave={scheduleMegaMenuClose}
              >
                <Button
                  component={Link}
                  to={`/products?category=${root.slug}`}
                  onClick={closeMegaMenu}
                  sx={{
                    color:         'text.primary',
                    fontWeight:    600,
                    textTransform: 'uppercase',
                    px: 2,
                    py: 1,
                    transition: 'all 0.2s ease',
                    '&:hover': {
                      bgcolor: 'primary.main',
                      color: 'background.default',
                    },
                    ...(activeMegaRoot?.id === root.id && {
                      borderBottom: '2px solid',
                      borderColor: 'primary.main',
                      pb: '6px', // adjust for border
                    }),
                  }}
                >
                  {root.name}
                </Button>
              </Box>
            ))}
          </Box>

          {/* Center logo */}
          <Box sx={{ flex: { xs: 1, md: 'none' }, display: 'flex', justifyContent: { xs: 'flex-start', md: 'center' } }}>
            <motion.div whileTap={{ scale: 0.97 }}>
              <Typography variant="sectionTitle" component={Link} to="/" onClick={closeMegaMenu} sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', textDecoration: 'none', }}>
                EGO
              </Typography>
            </motion.div>
          </Box>

          {/* Right actions */}
          <Box sx={{ flex: { xs: 'none', md: 1 }, display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.5 }}>

            {/* ── Desktop Search Bar ───────────────────────────────────── */}
            <DesktopSearch />

            {/* Mobile search icon */}
            <IconButton
              sx={{ display: { md: 'none' }, color: 'text.primary', borderRadius: 0, transition: 'none', '&:hover': { bgcolor: 'primary.main', color: 'background.default' } }}
              aria-label="Search"
              onClick={() => setMobileSearchOpen(true)}
            >
              <SearchIcon />
            </IconButton>


            {/* Wishlist */}
            <IconButton
              component={Link}
              to="/wishlist"
              aria-label="Wishlist"
              sx={{ color: 'text.primary', borderRadius: 0, transition: 'none', '&:hover': { bgcolor: 'primary.main', color: 'background.default' } }}
              onClick={closeMegaMenu}
            >
              <FavoriteBorderOutlinedIcon />
            </IconButton>

            {/* Account */}
            {isAuthenticated ? (
              <>
                <IconButton
                  onClick={handleUserMenuOpen}
                  aria-label="Account menu"
                  aria-haspopup="true"
                  sx={{ borderRadius: 0, transition: 'none', '&:hover': { bgcolor: 'primary.main', color: 'background.default' } }}
                >
                  <Avatar
                    sx={{
                      width:   24,
                      height:  24,
                      fontSize: '0.875rem',
                      fontWeight: 600,
                      bgcolor: 'transparent',
                      color:   'inherit',
                      borderRadius: 0,
                    }}
                  >
                    {user?.firstName?.[0]?.toUpperCase() ?? 'U'}
                  </Avatar>
                </IconButton>

                <Menu
                  anchorEl={anchorEl}
                  open={userMenuOpen}
                  onClose={handleUserMenuClose}
                  slotProps={{
                    paper: {
                      elevation: 0,
                      sx: {
                        border:       `1px solid ${theme.palette.divider}`,
                        borderRadius: 0,
                        bgcolor:      'background.paper',
                        minWidth:     160,
                        mt:           1,
                      },
                    }
                  }}
                  transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                  anchorOrigin={  { horizontal: 'right', vertical: 'bottom' }}
                >
                  <MenuItem onClick={() => { handleUserMenuClose(); navigate('/account'); }} sx={{ fontWeight: 500, fontSize: '0.875rem', py: 1.5 }}>
                    MY ACCOUNT
                  </MenuItem>
                  <MenuItem onClick={() => { handleUserMenuClose(); navigate('/wishlist'); }} sx={{ fontWeight: 500, fontSize: '0.875rem', py: 1.5 }}>
                    MY WISHLIST
                  </MenuItem>
                  <MenuItem onClick={() => { handleUserMenuClose(); navigate('/orders'); }} sx={{ fontWeight: 500, fontSize: '0.875rem', py: 1.5 }}>
                    ORDERS
                  </MenuItem>
                  {user?.role === 'ADMIN' && (
                    <MenuItem onClick={() => { handleUserMenuClose(); navigate('/admin'); }} sx={{ fontWeight: 500, fontSize: '0.875rem', py: 1.5, color: 'text.secondary' }}>
                      ADMIN PANEL
                    </MenuItem>
                  )}
                  <MenuItem onClick={handleLogout} disabled={isLoggingOut} sx={{ fontWeight: 600, fontSize: '0.875rem', py: 1.5, color: 'error.main' }}>
                    {isLoggingOut ? 'LOGGING OUT...' : 'LOGOUT'}
                  </MenuItem>
                </Menu>
              </>
            ) : (
              <IconButton component={Link} to="/login" aria-label="Login" sx={{ color: 'text.primary', borderRadius: 0, transition: 'none', '&:hover': { bgcolor: 'primary.main', color: 'background.default' } }}>
                <PersonOutlinedIcon />
              </IconButton>
            )}

            {/* Cart */}
            <IconButton onClick={openCartDrawer} aria-label="Open cart" sx={{ color: 'text.primary', borderRadius: 0, transition: 'none', '&:hover': { bgcolor: 'primary.main', color: 'background.default' } }}>
              <Badge badgeContent={0} color="primary" showZero={false}>
                <ShoppingBagOutlinedIcon />
              </Badge>
            </IconButton>
          </Box>
        </Toolbar>

        {/* ── Desktop Mega Menu Panel ──────────────────────────────────────────── */}
        {activeMegaRoot && (
          <MegaMenu
            root={activeMegaRoot}
            onMouseEnter={cancelMegaMenuClose}
            onMouseLeave={scheduleMegaMenuClose}
            onNavigate={closeMegaMenu}
          />
        )}
      </AppBar>

      {/* ── Mobile Drawer ───────────────────────────────────────────────────── */}
      <Drawer
        anchor="left"
        open={mobileDrawerOpen}
        onClose={() => setMobileDrawerOpen(false)}
        slotProps={{ paper: { sx: { width: '80vw', maxWidth: 320 } } }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
          <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, }}>
            EGO
          </Typography>
          <IconButton onClick={() => setMobileDrawerOpen(false)}>
            <CloseIcon />
          </IconButton>
        </Box>

        <List sx={{ px: 1 }}>
          {treeData?.map((root) => (
            <React.Fragment key={root.id}>
              {/* ROOT item — expandable */}
              <ListItem disablePadding>
                <ListItemButton
                  onClick={() => {
                    setExpandedRootId(expandedRootId === root.id ? null : root.id);
                    setExpandedGroupId(null);
                  }}
                  sx={{ py: 1.5, borderRadius: 1 }}
                >
                  <Typography variant="buttonLabel" sx={{ flex: 1, }}>
                    {root.name}
                  </Typography>
                  {expandedRootId === root.id ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                </ListItemButton>
              </ListItem>

              {/* GROUP items — visible when ROOT is expanded */}
              <Collapse in={expandedRootId === root.id} timeout="auto" unmountOnExit>
                <List disablePadding>
                  {root.groups.map((group) => (
                    <React.Fragment key={group.id}>
                      {/* GROUP label — clickable and expandable */}
                      <ListItem disablePadding>
                        <ListItemButton
                          onClick={() => setExpandedGroupId(expandedGroupId === group.id ? null : group.id)}
                          sx={{ pl: 3, py: 1, borderRadius: 1 }}
                        >
                          <Typography variant="metadata" sx={{ flex: 1, color: 'text.secondary' }}>
                            {group.resolvedLabel}
                          </Typography>
                          {group.leafCategories.length > 0 && (
                            expandedGroupId === group.id ? <ExpandLessIcon fontSize="small" sx={{ color: 'text.secondary' }} /> : <ExpandMoreIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                          )}
                        </ListItemButton>
                      </ListItem>

                      {/* LEAF items — visible when GROUP is expanded */}
                      <Collapse in={expandedGroupId === group.id} timeout="auto" unmountOnExit>
                        <List disablePadding>
                          {group.leafCategories.map((leaf) => (
                            <ListItem disablePadding key={leaf.id}>
                              <ListItemButton
                                component={Link}
                                to={`/products?category=${leaf.slug}`}
                                onClick={() => setMobileDrawerOpen(false)}
                                sx={{ pl: 5, py: 0.75, borderRadius: 1 }}
                              >
                                <Typography variant="body2" sx={{ color: 'text.primary' }}>
                                  {leaf.resolvedLabel}
                                </Typography>
                              </ListItemButton>
                            </ListItem>
                          ))}
                        </List>
                      </Collapse>
                    </React.Fragment>
                  ))}

                  {/* "View all" link for the ROOT */}
                  <ListItem disablePadding>
                    <ListItemButton
                      component={Link}
                      to={`/products?category=${root.slug}`}
                      onClick={() => setMobileDrawerOpen(false)}
                      sx={{ pl: 3, py: 1, borderRadius: 1 }}
                    >
                      <Typography variant="metadata" sx={{ color: 'primary.main' }}>
                        View All {root.name}
                      </Typography>
                    </ListItemButton>
                  </ListItem>
                </List>
              </Collapse>
            </React.Fragment>
          ))}

          {/* Auth actions */}
          {isAuthenticated ? (
            <ListItem disablePadding sx={{ mt: 2 }}>
              <ListItemButton onClick={() => { setMobileDrawerOpen(false); logout(); }} sx={{ py: 2 }}>
                <Typography variant="subtitle2" color="error">LOGOUT</Typography>
              </ListItemButton>
            </ListItem>
          ) : (
            <ListItem disablePadding sx={{ mt: 2 }}>
              <ListItemButton component={Link} to="/login" onClick={() => setMobileDrawerOpen(false)} sx={{ py: 2 }}>
                <Typography variant="subtitle2">LOGIN</Typography>
              </ListItemButton>
            </ListItem>
          )}
        </List>
      </Drawer>

      <MobileSearchSheet open={mobileSearchOpen} onClose={() => setMobileSearchOpen(false)} />
    </>
  );
};

export default Navbar;
