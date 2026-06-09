/**
 * CustomerOrderDetailPage.tsx
 *
 * Full order detail page for the customer storefront.
 * Route: /orders/:orderId  (protected — requires authentication)
 *
 * Features:
 *  - Full order items list with snapshots
 *  - Status history timeline (append-only audit trail)
 *  - Pricing breakdown with coupon discount
 *  - "Pay Now" button for PENDING_PAYMENT orders (re-opens payment pipeline)
 *  - "Cancel" button for PENDING_PAYMENT orders
 *  - "Request Return" button for DELIVERED orders
 *  - Razorpay payment re-initiation (idempotent — backend handles duplicate create)
 */

import React, { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box, Container, Grid, Typography, Paper,
  Button, Chip, CircularProgress, Alert, Avatar,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  Collapse, IconButton, useTheme
} from '@mui/material';
import ArrowBackIcon        from '@mui/icons-material/ArrowBack';
import PaymentIcon          from '@mui/icons-material/Payment';
import AssignmentReturnIcon from '@mui/icons-material/AssignmentReturn';
import ExpandMoreIcon       from '@mui/icons-material/ExpandMore';
import ExpandLessIcon       from '@mui/icons-material/ExpandLess';

import PageLoader           from '@/components/ui/PageLoader';
import PageWrapper          from '@/components/layout/PageWrapper';

import OrderTrackingTimeline from '../components/tracking/OrderTrackingTimeline';
import ShipmentTrackingCard from '../components/tracking/ShipmentTrackingCard';
import OrderDeliveryHero from '../components/tracking/OrderDeliveryHero';
import { buildTrackingTimeline } from '../components/tracking/OrderTrackingUtils';

import { useOrder, useCancelOrder, orderKeys } from '@/features/orders/hooks/useOrders';
import { useRazorpay }      from '@/hooks/useRazorpay';
import { createPaymentOrder } from '@/api/payment.api';
import { useInitiateReturn } from '@/features/orders/hooks/useReturns';
import { useQueryClient }   from '@tanstack/react-query';
import { toast }            from '@/store/uiStore';
import type { ReturnReason } from '@/types/return.types';

// ── Constants ─────────────────────────────────────────────────────────────────

const STATUS_LABELS: Record<string, string> = {
  PENDING_PAYMENT:  'Pending Payment',
  CONFIRMED:        'Confirmed',
  PROCESSING:       'Processing',
  SHIPPED:          'Shipped',
  OUT_FOR_DELIVERY: 'Out for Delivery',
  DELIVERED:        'Delivered',
  CANCELLED:        'Cancelled',
  REFUNDED:         'Refunded',
};

const RETURN_REASONS: Array<{ value: ReturnReason; label: string }> = [
  { value: 'DEFECTIVE',       label: 'Defective item' },
  { value: 'WRONG_ITEM',      label: 'Wrong item received' },
  { value: 'SIZE_ISSUE',      label: 'Size / fit issue' },
  { value: 'NOT_AS_DESCRIBED', label: 'Not as described' },
  { value: 'OTHER',           label: 'Other' },
];

// ── Component ─────────────────────────────────────────────────────────────────

const CustomerOrderDetailPage: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate    = useNavigate();
  const numericId   = Number(orderId);

  const { data: order, isLoading, isError, refetch } = useOrder(numericId);
  const { mutate: cancelOrder, isPending: isCancelling }    = useCancelOrder();
  const { mutate: initiateReturn, isPending: isReturning }  = useInitiateReturn();
  const queryClient = useQueryClient();

  const { openPaymentModal, isScriptLoaded, isScriptFailed } = useRazorpay();
  const [isRepaying,    setIsRepaying]    = useState(false);
  const [repayError,    setRepayError]    = useState<string | null>(null);

  const [historyExpanded, setHistoryExpanded] = useState(false);

  const [returnDialogOpen,  setReturnDialogOpen]  = useState(false);
  const [returnReason,      setReturnReason]       = useState<ReturnReason>('DEFECTIVE');
  const [returnDetail,      setReturnDetail]       = useState('');

  const handleInitiateReturn = () => {
    initiateReturn(
      {
        orderId: numericId,
        payload: { reason: returnReason, reasonDetail: returnDetail || undefined },
      },
      {
        onSuccess: () => setReturnDialogOpen(false),
      },
    );
  };

  const handleRepay = useCallback(async () => {
    if (!order || isRepaying || !isScriptLoaded) return;
    setIsRepaying(true);
    setRepayError(null);

    try {
      const paymentOrder = await createPaymentOrder({ orderId: numericId });
      queryClient.invalidateQueries({ queryKey: orderKeys.detail(numericId) });

      openPaymentModal(paymentOrder, {
        onSuccess: () => {
          navigate(`/checkout/verify/${numericId}`);
        },
        onDismiss: () => {
          toast.info('Payment cancelled. You can retry from this page.');
          setIsRepaying(false);
        },
        onError: (msg) => {
          setRepayError(msg);
          setIsRepaying(false);
          toast.error(msg);
        },
      });
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Could not initialise payment. Please try again.';
      setRepayError(msg);
      setIsRepaying(false);
      toast.error(msg);
    }
  }, [order, isRepaying, isScriptLoaded, numericId, openPaymentModal, queryClient, navigate]);

  if (isLoading) {
    return <PageLoader />;
  }

  if (isError || !order) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <Alert
          severity="error"
          action={<Button size="small" onClick={() => refetch()}>Retry</Button>}
          sx={{ borderRadius: 0 }}
        >
          Could not load order details.
        </Alert>
      </Container>
    );
  }

  const theme = useTheme();
  const statusColorHex = theme.palette.statusColors?.[order.status] ?? theme.palette.statusColors?.INACTIVE;

  return (
    <PageWrapper>
      <Container maxWidth="lg" sx={{ py: { xs: 6, md: 10 } }}>

        <Box sx={{ pb: 5, mb: 6, borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
          <Button
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/orders')}
            sx={{
              borderRadius: 0,
              textTransform: 'none',
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 600,
              fontSize: '0.72rem',
              letterSpacing: '0.1em',
              color: 'text.secondary',
              p: 0,
              mb: 3,
              '&:hover': { color: 'text.primary', background: 'none' },
            }}
          >
            ← My Orders
          </Button>

          <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
            <Box>
              <Typography
                variant="overline"
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyUtility,
                  fontWeight: 700,
                  fontSize: '0.65rem',
                  letterSpacing: '0.25em',
                  color: 'text.secondary',
                  display: 'block',
                  mb: 1,
                }}
              >
                ORDER DETAIL
              </Typography>
              <Typography
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyDisplay,
                  fontWeight: 700,
                  fontSize: { xs: '2rem', md: '2.75rem' },
                  color: 'text.primary',
                  lineHeight: 1,
                }}
              >
                #{order.id}
              </Typography>
            </Box>
            <Chip
              label={STATUS_LABELS[order.status] ?? order.status}
              size="small"
              sx={{
                bgcolor: `${statusColorHex}18`,
                color: statusColorHex,
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontWeight: 700,
                fontSize:   '0.65rem',
                letterSpacing: '0.1em',
                borderRadius: 0,
                border:     `1px solid ${statusColorHex}40`,
                height:     24,
              }}
            />
          </Box>
        </Box>

        <Box sx={{ display: 'flex', gap: 2, mb: 4, flexWrap: 'wrap' }}>
          {order.status === 'PENDING_PAYMENT' && (
            <Button
              variant="contained"
              startIcon={isRepaying ? <CircularProgress size={18} color="inherit" /> : <PaymentIcon />}
              onClick={handleRepay}
              disabled={!isScriptLoaded || isRepaying || isScriptFailed}
              sx={{
                borderRadius: 0,
                bgcolor:     'text.primary',
                color:       'background.paper',
                '&:hover':   { bgcolor: 'surface.secondary', color: 'text.primary' },
              }}
            >
              {isRepaying ? 'Opening payment…' : 'Pay Now'}
            </Button>
          )}

          {order.status === 'PENDING_PAYMENT' && (
            <Button
              variant="outlined"
              color="error"
              disabled={isCancelling}
              onClick={() => {
                if (window.confirm('Are you sure you want to cancel this order?')) {
                  cancelOrder(numericId);
                }
              }}
              sx={{ borderRadius: 0 }}
            >
              {isCancelling ? <CircularProgress size={18} /> : 'Cancel Order'}
            </Button>
          )}

          {order.status === 'DELIVERED' && (
            <Button
              variant="outlined"
              startIcon={<AssignmentReturnIcon />}
              onClick={() => setReturnDialogOpen(true)}
              sx={{ borderRadius: 0 }}
            >
              Request Return
            </Button>
          )}
        </Box>

        {(isScriptFailed || repayError) && (
          <Alert severity="error" sx={{ mb: 3, borderRadius: 0 }}>
            {isScriptFailed
              ? 'Payment gateway failed to load. Please refresh the page.'
              : repayError}
          </Alert>
        )}

        {!['PENDING_PAYMENT'].includes(order.status) && (
          <Box sx={{ mb: 6 }}>
            <OrderDeliveryHero order={order} />
            <OrderTrackingTimeline stages={buildTrackingTimeline(order.status, order.statusHistory)} />
          </Box>
        )}

        <Grid container spacing={4}>
          <Grid size={{ xs: 12, md: 8 }}>
            <Paper
              elevation={0}
              sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', p: 3, mb: 3 }}
            >
              <Typography
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyUtility,
                  fontWeight: 700,
                  fontSize: '0.65rem',
                  letterSpacing: '0.25em',
                  color: 'text.secondary',
                  textTransform: 'uppercase',
                  mb: 4,
                  display: 'block',
                }}
              >
                Items
              </Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow
                      sx={{
                        '& th': {
                          fontFamily: (theme) => theme.typography.fontFamilyUtility,
                          fontWeight: 700,
                          fontSize: '0.6rem',
                          color: 'text.secondary',
                          textTransform: 'uppercase',
                          letterSpacing: '0.1em',
                          borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
                          pb: 2,
                        },
                      }}
                    >
                      <TableCell>Product</TableCell>
                      <TableCell align="right">Price</TableCell>
                      <TableCell align="right">Qty</TableCell>
                      <TableCell align="right">Total</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {order.items.map((item) => (
                      <TableRow
                        key={item.id}
                        sx={{ '& td': { borderBottom: (theme) => `1px solid ${theme.palette.border.default}`, py: 2 } }}
                      >
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                            <Avatar
                              src={item.primaryImageUrlSnapshot ?? undefined}
                              variant="square"
                              sx={{ width: 44, height: 44, bgcolor: 'surface.tertiary' }}
                            />
                            <Box>
                              <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.primary', }}>
                                {item.productNameSnapshot}
                              </Typography>
                              {item.variantLabelSnapshot && (
                                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.72rem', letterSpacing: '0.05em' }}>
                                  {item.variantLabelSnapshot}
                                </Typography>
                              )}
                            </Box>
                          </Box>
                        </TableCell>
                        <TableCell align="right" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.85rem' }}>₹{item.unitPrice.toLocaleString('en-IN')}</TableCell>
                        <TableCell align="right" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.85rem' }}>{item.quantity}</TableCell>
                        <TableCell align="right" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, color: 'text.primary', fontSize: '0.9rem' }}>
                          ₹{item.lineTotal.toLocaleString('en-IN')}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>

              <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}`, mt: 3, pt: 3 }}>
                <Box sx={{ maxWidth: 300, ml: 'auto' }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.5 }}>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Subtotal</Typography>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 600, color: 'text.primary' }}>₹{order.subtotal.toLocaleString('en-IN')}</Typography>
                  </Box>
                  {order.discountAmount > 0 && (
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.5 }}>
                      <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'success.main', }}>
                        Discount{order.couponCodeSnapshot ? ` (${order.couponCodeSnapshot})` : ''}
                      </Typography>
                      <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'success.main', }}>
                        −₹{order.discountAmount.toLocaleString('en-IN')}
                      </Typography>
                    </Box>
                  )}
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Shipping</Typography>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, color: 'success.main', fontSize: '0.85rem' }}>Free</Typography>
                  </Box>
                  <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}`, pt: 2, display: 'flex', justifyContent: 'space-between' }}>
                    <Typography
                      sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase' }}
                    >
                      Grand Total
                    </Typography>
                    <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary' }}>
                      ₹{order.grandTotal.toLocaleString('en-IN')}
                    </Typography>
                  </Box>
                </Box>
              </Box>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 4 }}>
            <ShipmentTrackingCard order={order} />

            <Paper
              elevation={0}
              sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', p: 3, mb: 3 }}
            >
              <Typography
                sx={{
                  fontFamily: (theme) => theme.typography.fontFamilyUtility,
                  fontWeight: 700,
                  fontSize: '0.65rem',
                  letterSpacing: '0.25em',
                  color: 'text.secondary',
                  textTransform: 'uppercase',
                  mb: 2,
                  display: 'block',
                }}
              >
                Shipping To
              </Typography>
              <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', whiteSpace: 'pre-wrap' }}>
                {order.shippingAddress}
              </Typography>
            </Paper>

            <Paper
              elevation={0}
              sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', p: 3 }}
            >
              <Box
                sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}
                onClick={() => setHistoryExpanded(!historyExpanded)}
              >
                <Typography
                  sx={{
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.65rem',
                    letterSpacing: '0.25em',
                    color: 'text.secondary',
                    textTransform: 'uppercase',
                  }}
                >
                  Detailed History
                </Typography>
                <IconButton size="small">
                  {historyExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                </IconButton>
              </Box>

              <Collapse in={historyExpanded} timeout="auto" unmountOnExit>
                <Box sx={{ position: 'relative', pl: 2.5, mt: 3, borderLeft: (theme) => `1px solid ${theme.palette.border.default}` }}>
                  {[...order.statusHistory].reverse().map((entry, idx) => {
                    const sc = theme.palette.statusColors?.[entry.status] ?? theme.palette.statusColors?.INACTIVE;
                    return (
                      <Box key={idx} sx={{ mb: 3, position: 'relative' }}>
                        <Box
                          sx={{
                            position: 'absolute',
                            left:     -20,
                            top:      4,
                            width:    8,
                            height:   8,
                            bgcolor:  idx === 0 ? sc : 'surface.tertiary',
                          }}
                        />
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                          <Typography
                            sx={{
                              fontFamily: (theme) => theme.typography.fontFamilyUtility,
                              fontWeight: 700,
                              fontSize: '0.8rem',
                              color: idx === 0 ? 'text.primary' : 'text.secondary',
                            }}
                          >
                            {STATUS_LABELS[entry.status] ?? entry.status}
                          </Typography>
                          <Typography
                            sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.7rem', color: 'text.secondary', ml: 1, whiteSpace: 'nowrap', letterSpacing: '0.05em' }}
                          >
                            {new Date(entry.createdAt).toLocaleDateString(undefined, {
                              month: 'short', day: 'numeric',
                            })}
                          </Typography>
                        </Box>
                        {entry.note && (
                          <Typography
                            sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mt: 0.5, display: 'block', fontSize: '0.85rem', fontStyle: 'italic' }}
                          >
                            {entry.note}
                          </Typography>
                        )}
                      </Box>
                    );
                  })}
                </Box>
              </Collapse>
            </Paper>
          </Grid>
        </Grid>

        {/* ── Return Request Dialog ─────────────────────────────────── */}
        <Dialog
          open={returnDialogOpen}
          onClose={() => setReturnDialogOpen(false)}
          maxWidth="sm"
          fullWidth
          slotProps={{ paper: { sx: { borderRadius: 0 } } }}
        >
          <DialogTitle sx={{ fontWeight: 700 }}>Request a Return</DialogTitle>
          <DialogContent dividers>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Select a reason for returning this order. Our team will review and
              initiate a refund within 2-3 business days.
            </Typography>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, mb: 3 }}>
              {RETURN_REASONS.map((r) => (
                <Box
                  key={r.value}
                  onClick={() => setReturnReason(r.value)}
                  sx={{
                    p:            1.5,
                    border:       '1px solid',
                    borderColor:  returnReason === r.value ? 'primary.main' : 'divider',
                    bgcolor:      returnReason === r.value ? 'action.selected' : 'transparent',
                    cursor:       'pointer',
                    transition:   'all 0.15s',
                  }}
                >
                  <Typography variant="body2" sx={{ fontWeight: returnReason === r.value ? 700 : 400 }}>
                    {r.label}
                  </Typography>
                </Box>
              ))}
            </Box>

            <TextField
              fullWidth
              multiline
              rows={3}
              label="Additional details (optional)"
              value={returnDetail}
              onChange={(e) => setReturnDetail(e.target.value)}
              sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }}
            />
          </DialogContent>
          <DialogActions sx={{ p: 2.5 }}>
            <Button onClick={() => setReturnDialogOpen(false)} sx={{ borderRadius: 0 }}>
              Cancel
            </Button>
            <Button
              variant="contained"
              onClick={handleInitiateReturn}
              disabled={isReturning}
              sx={{ borderRadius: 0, bgcolor: 'text.primary', color: 'background.paper', '&:hover': { bgcolor: 'surface.secondary', color: 'text.primary' } }}
            >
              {isReturning ? <CircularProgress size={18} color="inherit" /> : 'Submit Return'}
            </Button>
          </DialogActions>
        </Dialog>
      </Container>
    </PageWrapper>
  );
};

export default CustomerOrderDetailPage;
