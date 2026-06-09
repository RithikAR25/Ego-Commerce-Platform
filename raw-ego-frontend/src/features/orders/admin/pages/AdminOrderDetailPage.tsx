import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Box, Typography, Paper, Grid, Button, 
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  CircularProgress, Alert, Avatar, Select, MenuItem, FormControl, InputLabel, TextField
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import StatusBadge from '@/components/admin/StatusBadge';
import { useAdminOrder, useAdminUpdateOrderStatus } from '@/features/orders/admin/hooks/useAdminOrders';
import type { OrderStatus } from '@/types/order.types';

const AVAILABLE_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  PENDING_PAYMENT:  ['CANCELLED'],
  CONFIRMED:        ['PROCESSING', 'CANCELLED'],
  PROCESSING:       ['SHIPPED', 'CANCELLED'],
  SHIPPED:          ['OUT_FOR_DELIVERY', 'DELIVERED'],
  OUT_FOR_DELIVERY: ['DELIVERED'],
  DELIVERED:        [], // Returns handle refund transitions
  CANCELLED:        [],
  REFUNDED:         []
};

const AdminOrderDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const orderId = Number(id);
  const navigate = useNavigate();
  
  const { data: order, isLoading, isError, refetch } = useAdminOrder(orderId);
  const { mutate: updateStatus, isPending: isUpdating } = useAdminUpdateOrderStatus();

  const [newStatus, setNewStatus] = useState<OrderStatus | ''>('');
  const [adminNote, setAdminNote] = useState('');

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 10 }}>
        <CircularProgress sx={{ color: 'text.secondary' }} />
      </Box>
    );
  }

  if (isError || !order) {
    return (
      <Box>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/admin/orders')}
          sx={{ mb: 3, borderRadius: 0, p: 0, color: 'text.secondary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.72rem', letterSpacing: '0.1em', '&:hover': { color: 'text.primary', bgcolor: 'transparent' } }}
        >
          ← Back to Orders
        </Button>
        <Alert severity="error" action={<Button color="inherit" onClick={() => refetch()}>Retry</Button>}>
          Failed to load order details.
        </Alert>
      </Box>
    );
  }

  const allowedNextStatuses = AVAILABLE_TRANSITIONS[order.status] || [];

  const handleUpdateStatus = () => {
    if (!newStatus) return;
    updateStatus({
      orderId,
      payload: {
        status: newStatus,
        note: adminNote || undefined
      }
    }, {
      onSuccess: () => {
        setNewStatus('');
        setAdminNote('');
      }
    });
  };

  return (
    <Box>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate('/admin/orders')}
        sx={{
          mb: 4,
          borderRadius: 0,
          p: 0,
          color: 'text.secondary',
          fontFamily: (theme) => theme.typography.fontFamilyUtility,
          fontSize: '0.72rem',
          letterSpacing: '0.1em',
          fontWeight: 600,
          '&:hover': { color: 'text.primary', bgcolor: 'transparent' },
        }}
      >
        ← Back to Orders
      </Button>

      <AdminPageHeader
        title={`Order #${order.id}`}
        subtitle={`Placed on ${new Date(order.createdAt).toLocaleString()}`}
        action={<StatusBadge status={order.status} />}
      />

      <Grid container spacing={4}>
        {/* Left Column: Order Items & Summary */}
        <Grid size={{ xs: 12, md: 8 }}>
          <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: 3, mb: 4 }}>
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 3, display: 'block' }}>
              Items
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ '& th': { fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.1em', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` } }}>
                    <TableCell>Product</TableCell>
                    <TableCell align="right">Price</TableCell>
                    <TableCell align="right">Qty</TableCell>
                    <TableCell align="right">Total</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {order.items.map((item) => (
                    <TableRow key={item.id} sx={{ '& td': { borderBottom: (theme) => `1px solid ${theme.palette.border.default}`, py: 2 } }}>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center' }}>
                          <Avatar
                            src={item.primaryImageUrlSnapshot || undefined}
                            variant="square"
                            sx={{ width: 48, height: 48, mr: 2, bgcolor: 'surface.tertiary' }}
                          />
                          <Box>
                            <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.primary', }}>{item.productNameSnapshot}</Typography>
                            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.72rem', letterSpacing: '0.05em' }}>
                              {item.skuSnapshot} {item.variantLabelSnapshot ? `• ${item.variantLabelSnapshot}` : ''}
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell align="right" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.85rem' }}>₹{item.unitPrice.toLocaleString()}</TableCell>
                      <TableCell align="right" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.85rem' }}>{item.quantity}</TableCell>
                      <TableCell align="right" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, color: 'text.primary', fontSize: '0.9rem' }}>₹{item.lineTotal.toLocaleString()}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            
            <Box sx={{ mt: 4, ml: 'auto', width: { xs: '100%', sm: '50%' }, borderTop: (theme) => `1px solid ${theme.palette.border.default}`, pt: 3 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.5 }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Subtotal</Typography>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 600, color: 'text.primary' }}>₹{order.subtotal.toLocaleString()}</Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Shipping</Typography>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 600, color: 'text.primary' }}>₹{order.shippingTotal.toLocaleString()}</Typography>
              </Box>
              <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}`, pt: 2, display: 'flex', justifyContent: 'space-between' }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase' }}>Grand Total</Typography>
                <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary' }}>₹{order.grandTotal.toLocaleString()}</Typography>
              </Box>
            </Box>
          </Paper>
          
          <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: 3 }}>
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 3, display: 'block' }}>
              Status History
            </Typography>
            <Box sx={{ position: 'relative', pl: 3, borderLeft: (theme) => `1px solid ${theme.palette.border.default}` }}>
              {order.statusHistory.map((history, idx) => (
                <Box key={idx} sx={{ mb: 3, position: 'relative' }}>
                  <Box sx={{
                    position: 'absolute',
                    left: -22,
                    top: 4,
                    width: 8,
                    height: 8,
                    bgcolor: idx === order.statusHistory.length - 1 ? 'brand.primary' : 'surface.tertiary',
                  }} />
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.5 }}>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.8rem', color: idx === order.statusHistory.length - 1 ? 'text.primary' : 'text.secondary' }}>{history.status}</Typography>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.72rem', color: 'text.secondary', ml: 1, whiteSpace: 'nowrap' }}>
                      {new Date(history.createdAt).toLocaleString()}
                    </Typography>
                  </Box>
                  {history.note && (
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', p: 1.5, bgcolor: (theme) => `${theme.palette.surface.tertiary}66`, mt: 1, fontStyle: 'italic', fontSize: '0.85rem' }}>
                      {history.note}
                    </Typography>
                  )}
                </Box>
              ))}
            </Box>
          </Paper>
        </Grid>

        {/* Right Column: Actions & Info */}
        <Grid size={{ xs: 12, md: 4 }}>
          <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: 3, mb: 4 }}>
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 3, display: 'block' }}>
              Advance Status
            </Typography>

            {allowedNextStatuses.length === 0 ? (
              <Alert severity="info" sx={{ borderRadius: 0, bgcolor: (theme) => `${theme.palette.text.secondary}1A`, color: 'text.secondary', border: (theme) => `1px solid ${theme.palette.border.default}`, '& .MuiAlert-icon': { color: 'text.secondary' } }}>
                No further status transitions are allowed from {order.status}.
              </Alert>
            ) : (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <FormControl fullWidth size="small">
                  <InputLabel sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary' }}>New Status</InputLabel>
                  <Select
                    value={newStatus}
                    label="New Status"
                    onChange={(e) => setNewStatus(e.target.value as OrderStatus)}
                    sx={{ borderRadius: 0, color: 'text.primary', '& .MuiOutlinedInput-notchedOutline': { borderColor: 'border.default' }, '& .MuiSvgIcon-root': { color: 'text.secondary' } }}
                  >
                    {allowedNextStatuses.map(status => (
                      <MenuItem key={status} value={status}>{status}</MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  label="Admin Note (Optional)"
                  multiline
                  rows={2}
                  value={adminNote}
                  onChange={(e) => setAdminNote(e.target.value)}
                  size="small"
                  fullWidth
                  sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0, '& fieldset': { borderColor: 'border.default' } }, '& label': { fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary' }, '& input, & textarea': { fontFamily: '"Source Serif 4", serif', color: 'text.primary' } }}
                />

                <Button
                  variant="outlined"
                  onClick={handleUpdateStatus}
                  disabled={!newStatus || isUpdating}
                  fullWidth
                  sx={{ borderRadius: 0, borderColor: 'border.default', color: 'text.secondary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.1em', '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'transparent' }, '&:not(:disabled)': { borderColor: 'text.primary', color: 'text.primary' } }}
                >
                  {isUpdating ? 'Updating...' : 'Update Status'}
                </Button>
              </Box>
            )}
          </Paper>

          {/* Tracking Info */}
          {(order.trackingNumber || order.courierName || order.estimatedDeliveryAt) && (
            <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: 3, mb: 4 }}>
              <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 2, display: 'block' }}>
                Shipment Tracking
              </Typography>
              {order.courierName && (
                <Box sx={{ mb: 1.5 }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Courier</Typography>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.primary' }}>{order.courierName}</Typography>
                </Box>
              )}
              {order.trackingNumber && (
                <Box sx={{ mb: 1.5 }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Tracking #</Typography>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary', fontSize: '0.85rem' }}>{order.trackingNumber}</Typography>
                </Box>
              )}
              {order.trackingUrl && (
                <Box sx={{ mb: 1.5 }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Tracking Link</Typography>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontSize: '0.85rem' }}>
                    <a href={order.trackingUrl} target="_blank" rel="noopener noreferrer" style={{ color: 'inherit' }}>{order.trackingUrl}</a>
                  </Typography>
                </Box>
              )}
              {order.estimatedDeliveryAt && (
                <Box>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Est. Delivery</Typography>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.primary' }}>{new Date(order.estimatedDeliveryAt).toLocaleDateString()}</Typography>
                </Box>
              )}
            </Paper>
          )}

          <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: 3 }}>
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 2, display: 'block' }}>
              Customer Details
            </Typography>
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 1 }}>
              Shipping Address
            </Typography>
            <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', whiteSpace: 'pre-wrap', }}>
              {order.shippingAddress}
            </Typography>

            {order.razorpayOrderId && (
              <Box sx={{ mt: 3, pt: 2, borderTop: (theme) => `1px solid ${theme.palette.border.default}` }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 1 }}>
                  Payment Reference
                </Typography>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.8rem' }}>
                  {order.razorpayOrderId}
                </Typography>
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default AdminOrderDetailPage;
