import React from 'react';
import {
  Drawer, Box, Typography, IconButton, Button,
  List, ListItem, Divider, Chip, LinearProgress
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ShoppingBagOutlinedIcon from '@mui/icons-material/ShoppingBagOutlined';
import { useUiStore } from '@/store/uiStore';
import { useCart, useUpdateCartItem, useRemoveCartItem } from '@/features/cart/hooks/useCart';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import CartItemSkeleton from '@/features/cart/components/skeletons/CartItemSkeleton';

const CartDrawer: React.FC = () => {
  const { cartDrawerOpen, closeCartDrawer } = useUiStore();
  const { data: cart, isLoading } = useCart();
  const { mutate: updateQuantity, isPending: isUpdating } = useUpdateCartItem();
  const { mutate: removeItem, isPending: isRemoving } = useRemoveCartItem();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();

  // True if any cart item is currently out of stock — blocks checkout
  const hasOutOfStockItems = cart?.items.some(
    (item) => item.stockStatus === 'OUT_OF_STOCK'
  ) ?? false;

  const FREE_SHIPPING_THRESHOLD = 1500;
  const progress = cart ? Math.min(100, (cart.subtotal / FREE_SHIPPING_THRESHOLD) * 100) : 0;
  const amountToFreeShipping = cart ? Math.max(0, FREE_SHIPPING_THRESHOLD - cart.subtotal) : FREE_SHIPPING_THRESHOLD;

  const handleCheckout = () => {
    closeCartDrawer();
    navigate('/checkout');
  };

  const handleClose = () => {
    closeCartDrawer();
  };

  return (
    <Drawer
      anchor="right"
      open={cartDrawerOpen}
      onClose={handleClose}
      slotProps={{
        paper: {
          sx: {
            width: { xs: '100%', sm: 400 },
            display: 'flex',
            flexDirection: 'column',
            bgcolor: 'background.paper',
          }
        }
      }}
    >
      {/* Header */}
      <Box sx={{ p: 3, pb: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: cart ? 'none' : '1px solid', borderColor: 'divider' }}>
        <Typography variant="metadata" >
          Your Cart {cart?.itemCount ? `(${cart.itemCount})` : ''}
        </Typography>
        <IconButton onClick={handleClose}>
          <CloseIcon />
        </IconButton>
      </Box>

      {/* Free Shipping Progress */}
      {cart && cart.items.length > 0 && isAuthenticated && (
        <Box sx={{ px: 3, pb: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
          <Typography variant="metadata" sx={{ display: 'block', mb: 1 }}>
            {amountToFreeShipping > 0 
              ? `You're ₹${amountToFreeShipping.toLocaleString('en-IN')} away from Free Shipping!` 
              : 'You have unlocked Free Shipping!'}
          </Typography>
          <LinearProgress 
            variant="determinate" 
            value={progress} 
            sx={{ 
              height: 4, 
              borderRadius: 0, 
              bgcolor: 'grey.200',
              '& .MuiLinearProgress-bar': {
                bgcolor: progress === 100 ? 'success.main' : 'text.primary'
              }
            }} 
          />
        </Box>
      )}

      {/* Body */}
      <Box sx={{ flex: 1, overflowY: 'auto', p: 3 }}>
        {!isAuthenticated ? (
          <Box sx={{ textAlign: 'center', mt: 10 }}>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
              Please sign in to view your cart.
            </Typography>
            <Button
              component={Link}
              to="/login"
              variant="contained"
              onClick={handleClose}
              sx={{ borderRadius: 0, bgcolor: 'text.primary', color: 'background.paper', '&:hover': { bgcolor: 'primary.dark' } }}
            >
              Sign In
            </Button>
          </Box>
        ) : isLoading ? (
          <Box sx={{ display: 'flex', flexDirection: 'column' }}>
            {[1, 2, 3].map(i => (
              <CartItemSkeleton key={i} variant="drawer" />
            ))}
          </Box>
        ) : !cart || cart.items.length === 0 ? (
          <Box sx={{ textAlign: 'center', mt: 10, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Box sx={{ 
              width: 120, height: 120, mb: 3, 
              bgcolor: 'background.default', borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              border: '1px solid', borderColor: 'divider'
            }}>
              <ShoppingBagOutlinedIcon sx={{ fontSize: 60, color: 'text.secondary' }} />
            </Box>
            <Typography variant="metadata" sx={{ mb: 1, }}>
              Your cart is empty
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 4, maxWidth: '80%', mx: 'auto' }}>
              Looks like you haven't added anything to your cart yet.
            </Typography>
            <Button
              component={Link}
              to="/products"
              variant="contained"
              onClick={handleClose}
              sx={{ 
                borderRadius: 0, 
                bgcolor: 'primary.main', 
                color: 'primary.contrastText', 
                px: 4, py: 1.5, 
                fontWeight: 600, 
                border: '1px solid', borderColor: 'primary.main',
                '&:hover': { bgcolor: 'transparent', color: 'primary.main' } 
              }}
            >
              START SHOPPING
            </Button>
          </Box>
        ) : (
          <List disablePadding>
            {cart.items.map((item) => (
              <React.Fragment key={item.variantId}>
                <ListItem disablePadding sx={{ py: 3, display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                  {/* Image */}
                  <Box
                    component={Link}
                    to={`/products/${item.sku.split('-').slice(0, 3).join('-').toLowerCase()}`}
                    onClick={handleClose}
                    sx={{ width: 80, height: 100, bgcolor: 'background.default', flexShrink: 0, position: 'relative' }}
                  >
                    <Box
                      component="img"
                      src={item.primaryImageUrl || 'https://via.placeholder.com/80x100?text=No+Image'}
                      alt={item.productName}
                      sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  </Box>

                  {/* Details */}
                  <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <Box>
                        <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', mb: 0.5, letterSpacing: '0.05em' }}>
                          REF: {item.sku.split('-')[0]}
                        </Typography>
                        <Typography
                          variant="subtitle2"
                          component={Link}
                          to={`/products/${item.sku.split('-').slice(0, 3).join('-').toLowerCase()}`}
                          onClick={handleClose}
                          sx={{ fontWeight: 600, color: 'text.primary', textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                        >
                          {item.productName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                          {item.variantLabel}
                        </Typography>
                        {item.stockStatus === 'OUT_OF_STOCK' && (
                          <Chip
                            icon={<WarningAmberIcon fontSize="small" />}
                            label="Out of stock"
                            size="small"
                            sx={{
                              mt: 0.75,
                              height: 22,
                              fontSize: '0.7rem',
                              fontWeight: 600,
                              bgcolor: 'error.main',
                              color: 'error.contrastText',
                              border: 'none',
                              borderRadius: '4px',
                              '& .MuiChip-icon': { color: 'error.contrastText', fontSize: '0.85rem' }
                            }}
                          />
                        )}
                      </Box>
                      <IconButton size="small" onClick={() => removeItem(item.variantId)} disabled={isRemoving} sx={{ ml: 1, mt: -1, mr: -1 }}>
                        <DeleteOutlinedIcon fontSize="small" />
                      </IconButton>
                    </Box>

                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', mt: 2 }}>
                      {/* Quantity Control */}
                      <Box sx={{ display: 'flex', alignItems: 'center', border: '1px solid', borderColor: 'divider' }}>
                        <IconButton
                          size="small"
                          onClick={() => updateQuantity({ variantId: item.variantId, payload: { quantity: item.quantity - 1 } })}
                          disabled={item.quantity <= 1 || isUpdating}
                          sx={{ borderRadius: 0, p: 0.5 }}
                        >
                          <RemoveIcon fontSize="small" />
                        </IconButton>
                        <Typography variant="body2" sx={{ width: 32, textAlign: 'center' }}>
                          {item.quantity}
                        </Typography>
                        <IconButton
                          size="small"
                          onClick={() => updateQuantity({ variantId: item.variantId, payload: { quantity: item.quantity + 1 } })}
                          disabled={isUpdating} // Should also check stock available but omit for now
                          sx={{ borderRadius: 0, p: 0.5 }}
                        >
                          <AddIcon fontSize="small" />
                        </IconButton>
                      </Box>

                      {/* Price */}
                      <Typography variant="metadata" >
                        ${(item.price * item.quantity).toLocaleString('en-US')}
                      </Typography>
                    </Box>
                  </Box>
                </ListItem>
                <Divider />
              </React.Fragment>
            ))}
          </List>
        )}
      </Box>

      {/* Footer */}
      {cart && cart.items.length > 0 && isAuthenticated && (
        <Box sx={{ p: 3, borderTop: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}>
          {/* Out-of-stock warning banner */}
          {hasOutOfStockItems && (
            <Box sx={{
              display: 'flex', alignItems: 'center', gap: 1,
              bgcolor: 'surface.secondary', border: '1px solid', borderColor: 'error.main',
              borderRadius: '4px', p: 1.5, mb: 2
            }}>
              <WarningAmberIcon sx={{ color: 'error.main', fontSize: 18, flexShrink: 0 }} />
              <Typography variant="metadata" sx={{ color: 'error.main', }}>
                Some items are out of stock. Remove them to proceed to checkout.
              </Typography>
            </Box>
          )}

          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
            <Typography variant="subtitle1">Subtotal</Typography>
            <Typography variant="subtitle1">
              ${cart.subtotal.toLocaleString('en-US')}
            </Typography>
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 3 }}>
            Shipping & taxes calculated at checkout
          </Typography>
          <Button
            fullWidth
            variant="contained"
            size="large"
            onClick={handleCheckout}
            disabled={hasOutOfStockItems}
            sx={{
              py: 2,
              bgcolor: 'primary.main',
              color: 'primary.contrastText',
              fontWeight: 600,
              letterSpacing: '0.05em',
              textTransform: 'uppercase',
              transition: 'all 0.3s',
              '&:hover': { bgcolor: 'primary.dark' },
              '&.Mui-disabled': { bgcolor: 'divider', color: 'text.disabled', borderColor: 'divider' }
            }}
          >
            {hasOutOfStockItems ? 'REMOVE OUT-OF-STOCK ITEMS' : 'CHECKOUT'}
          </Button>
        </Box>
      )}
    </Drawer>
  );
};

export default CartDrawer;
