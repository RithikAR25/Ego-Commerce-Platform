import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Box, Typography, Paper, Grid, Button, 
  CircularProgress, Alert, TextField, InputAdornment,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import StatusBadge from '@/components/admin/StatusBadge';
import { useAdminReturn, useAdminReviewReturn } from '@/features/returns/admin/hooks/useAdminReturns';
import { useAdminOrder } from '@/features/orders/admin/hooks/useAdminOrders';

const AdminReturnDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const returnId = Number(id);
  const navigate = useNavigate();
  
  const { data: returnReq, isLoading: isLoadingReturn, isError: isErrorReturn } = useAdminReturn(returnId);
  const { data: order, isLoading: isLoadingOrder } = useAdminOrder(returnReq?.orderId || 0);
  
  const { mutate: reviewReturn, isPending } = useAdminReviewReturn();

  const [refundAmount, setRefundAmount] = useState<string>('');
  const [adminNotes, setAdminNotes] = useState<string>('');

  if (isLoadingReturn || isLoadingOrder) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 10 }}>
        <CircularProgress sx={{ color: 'text.secondary' }} />
      </Box>
    );
  }

  if (isErrorReturn || !returnReq) {
    return (
      <Box>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/admin/returns')}
          sx={{ mb: 3, borderRadius: 0, p: 0, color: 'text.secondary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.72rem', letterSpacing: '0.1em', fontWeight: 600, '&:hover': { color: 'text.primary', bgcolor: 'transparent' } }}
        >
          ← Back to Returns
        </Button>
        <Alert severity="error">Failed to load return details.</Alert>
      </Box>
    );
  }

  const handleReview = (approve: boolean) => {
    reviewReturn({
      returnId,
      payload: {
        approve,
        refundAmount: approve ? Number(refundAmount) : undefined,
        adminNotes: adminNotes || undefined,
      }
    });
  };

  const isRequested = returnReq.status === 'REQUESTED' || returnReq.status === 'APPROVED';

  const setFullRefund = () => {
    if (order) {
      setRefundAmount(order.grandTotal.toString());
    }
  };

  return (
    <Box>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate('/admin/returns')}
        sx={{ mb: 4, borderRadius: 0, p: 0, color: 'text.secondary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.72rem', letterSpacing: '0.1em', fontWeight: 600, '&:hover': { color: 'text.primary', bgcolor: 'transparent' } }}
      >
        ← Back to Returns
      </Button>

      <AdminPageHeader
        title={`Return #R-${returnReq.id}`}
        subtitle={`Requested on ${new Date(returnReq.createdAt).toLocaleString()}`}
        action={<StatusBadge status={returnReq.status} />}
      />

      <Grid container spacing={4}>
        {/* Left Column: Details */}
        <Grid size={{ xs: 12, md: 7 }}>
          <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: 3, mb: 4 }}>
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 3, display: 'block' }}>
              Return Information
            </Typography>

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Reason</Typography>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, fontWeight: 600, color: 'text.primary', mb: 2 }}>
                  {returnReq.reason.replace(/_/g, ' ')}
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Order ID</Typography>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, color: 'text.primary', mb: 2 }}>
                  #{returnReq.orderId}
                </Typography>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 1 }}>Customer's Detail</Typography>
                <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', p: 2, bgcolor: 'surface.tertiary', minHeight: '60px', whiteSpace: 'pre-wrap', fontStyle: 'italic', }}>
                  {returnReq.reasonDetail || 'No additional details provided.'}
                </Typography>
              </Grid>
            </Grid>
          </Paper>

          {/* Reference to Order */}
          <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: 3 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
              <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase' }}>
                Original Order
              </Typography>
              <Button
                size="small"
                onClick={() => navigate(`/admin/orders/${returnReq.orderId}`)}
                sx={{ borderRadius: 0, borderColor: 'border.default', color: 'text.secondary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.1em', border: '1px solid', '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'transparent' } }}
              >
                View Full Order
              </Button>
            </Box>

            {order ? (
              <Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', py: 1.5, borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Order Total</Typography>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, fontWeight: 700, color: 'text.primary' }}>₹{order.grandTotal.toLocaleString()}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', py: 1.5, borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Items</Typography>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary' }}>{order.items.length} items</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', py: 1.5, alignItems: 'center' }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary' }}>Current Status</Typography>
                  <StatusBadge status={order.status} />
                </Box>
              </Box>
            ) : (
              <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontStyle: 'italic' }}>Loading order details...</Typography>
            )}
          </Paper>
        </Grid>

        {/* Right Column: Admin Actions */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, p: 3 }}>
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 3, display: 'block' }}>
              Review Action
            </Typography>

            {!isRequested ? (
              <Box>
                <Alert
                  severity={returnReq.status.includes('REJECTED') ? 'error' : 'success'}
                  sx={{ mb: 3, borderRadius: 0 }}
                >
                  This request has been processed.
                </Alert>
                {returnReq.refundAmount !== null && (
                  <Box sx={{ mb: 2 }}>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Approved Refund</Typography>
                    <Typography variant="sectionTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary' }}>₹{returnReq.refundAmount.toLocaleString()}</Typography>
                  </Box>
                )}
                {returnReq.adminNotes && (
                  <Box>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Admin Notes</Typography>
                    <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mt: 0.5 }}>{returnReq.adminNotes}</Typography>
                  </Box>
                )}
                {returnReq.razorpayRefundId && (
                  <Box sx={{ mt: 2, pt: 2, borderTop: (theme) => `1px solid ${theme.palette.border.default}` }}>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block', mb: 0.5 }}>Razorpay Refund ID</Typography>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.8rem' }}>{returnReq.razorpayRefundId}</Typography>
                  </Box>
                )}
              </Box>
            ) : (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                <Box>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', mb: 1 }}>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.65rem', letterSpacing: '0.1em', color: 'text.secondary', textTransform: 'uppercase' }}>Refund Amount (₹)</Typography>
                    {order && (
                      <Button
                        size="small"
                        onClick={setFullRefund}
                        sx={{ p: 0, minWidth: 'auto', textTransform: 'none', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.65rem', color: 'text.secondary', '&:hover': { color: 'text.primary', bgcolor: 'transparent' } }}
                      >
                        Set Full Refund
                      </Button>
                    )}
                  </Box>
                  <TextField
                    fullWidth
                    size="small"
                    type="number"
                    value={refundAmount}
                    onChange={(e) => setRefundAmount(e.target.value)}
                    slotProps={{
                      input: {
                        startAdornment: <InputAdornment position="start" sx={{ color: 'text.secondary' }}>₹</InputAdornment>
                      }
                    }}
                    placeholder={order ? `Max ₹${order.grandTotal}` : 'Amount'}
                    helperText="Required for approval. Can be a partial refund."
                    sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0, '& fieldset': { borderColor: 'border.default' }, '&:hover fieldset': { borderColor: 'text.secondary' } }, '& input': { fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary' }, '& .MuiFormHelperText-root': { fontFamily: '"Work Sans", sans-serif', color: 'text.secondary', fontSize: '0.7rem' } }}
                  />
                </Box>

                <TextField
                  fullWidth
                  size="small"
                  label="Admin Notes to Customer"
                  multiline
                  rows={3}
                  value={adminNotes}
                  onChange={(e) => setAdminNotes(e.target.value)}
                  placeholder="e.g. Approved. Please allow 5-7 days for bank processing."
                  sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0, '& fieldset': { borderColor: 'border.default' }, '&:hover fieldset': { borderColor: 'text.secondary' } }, '& label': { fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary' }, '& textarea': { fontFamily: '"Source Serif 4", serif', color: 'text.primary' } }}
                />

                <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}`, pt: 3 }} />

                <Box sx={{ display: 'flex', gap: 2 }}>
                  <Button
                    variant="outlined"
                    color="error"
                    fullWidth
                    disabled={isPending}
                    onClick={() => handleReview(false)}
                    sx={{ borderRadius: 0, fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.1em' }}
                  >
                    Reject
                  </Button>
                  <Button
                    variant="outlined"
                    fullWidth
                    disabled={isPending || !refundAmount || Number(refundAmount) <= 0 || (order != null && Number(refundAmount) > order.grandTotal)}
                    onClick={() => handleReview(true)}
                    sx={{ borderRadius: 0, borderColor: 'text.primary', color: 'text.primary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.1em', '&:hover': { bgcolor: 'text.primary', color: 'background.default' }, '&.Mui-disabled': { borderColor: 'border.default', color: 'text.disabled' } }}
                  >
                    Approve
                  </Button>
                </Box>
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
};

export default AdminReturnDetailPage;
