/**
 * CustomerOrdersPage.tsx
 *
 * Customer order history page.
 * Route: /orders  (protected — requires authentication)
 *
 * Shows all of the logged-in customer's orders, newest first.
 * Click any row to go to the full order detail page.
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Container, Typography, Paper, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow,
  TablePagination, Chip, Button, Skeleton, Alert, useTheme, alpha
} from '@mui/material';
import ShoppingBagOutlinedIcon from '@mui/icons-material/ShoppingBagOutlined';
import { useOrders } from '@/features/orders/hooks/useOrders';
import PageWrapper   from '@/components/layout/PageWrapper';


// ── Status chip ───────────────────────────────────────────────────────────────



const STATUS_LABELS: Record<string, string> = {
  PENDING_PAYMENT: 'Pending Payment',
  CONFIRMED:       'Confirmed',
  PROCESSING:      'Processing',
  SHIPPED:         'Shipped',
  DELIVERED:       'Delivered',
  CANCELLED:       'Cancelled',
  REFUNDED:        'Refunded',
};

const StatusChip: React.FC<{ status: string }> = ({ status }) => {
  const theme = useTheme();
  const color = theme.palette.statusColors?.[status] ?? theme.palette.text.secondary;
  return (
    <Chip
      label={STATUS_LABELS[status] ?? status}
      size="small"
      sx={{
        bgcolor:    alpha(color, 0.1),
        color:      color,
        fontWeight: 700,
        fontSize:   '0.7rem',
        borderRadius: 0,
        border:     `1px solid ${alpha(color, 0.25)}`,
      }}
    />
  );
};

// ── Component ─────────────────────────────────────────────────────────────────

const CustomerOrdersPage: React.FC = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const SIZE = 10;

  const { data, isLoading, isError, refetch } = useOrders(page, SIZE);

  return (
    <PageWrapper>
      <Container maxWidth="lg" sx={{ py: { xs: 6, md: 10 } }}>
        <Box
          sx={{
            pb: 4,
            mb: 6,
            borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
          }}
        >
          <Typography
            variant="overline"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              fontSize: '0.65rem',
              letterSpacing: '0.25em',
              color: 'text.secondary',
              display: 'block',
              mb: 1.5,
            }}
          >
            ACCOUNT
          </Typography>
          <Typography
            variant="h2"
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyDisplay,
              fontWeight: 700,
              fontSize: { xs: '2rem', md: '3rem' },
              color: 'text.primary',
              lineHeight: 1,
              textTransform: 'uppercase',
            }}
          >
            My Orders
          </Typography>
        </Box>

        {isError && (
          <Alert
            severity="error"
            action={
              <Button size="small" color="inherit" onClick={() => refetch()}>
                Retry
              </Button>
            }
            sx={{ mb: 3, borderRadius: 0 }}
          >
            Failed to load your orders.
          </Alert>
        )}

        <Paper
          elevation={0}
          sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, borderRadius: 0, bgcolor: 'surface.secondary' }}
        >
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow sx={{ '& th': { fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.72rem', color: 'text.secondary' } }}>
                  <TableCell>Order</TableCell>
                  <TableCell>Date</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="center">Items</TableCell>
                  <TableCell align="right">Total</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {isLoading
                  ? Array.from({ length: 5 }).map((_, i) => (
                      <TableRow key={i}>
                        {Array.from({ length: 6 }).map((__, j) => (
                          <TableCell key={j}><Skeleton /></TableCell>
                        ))}
                      </TableRow>
                    ))
                  : data?.content.length === 0
                    ? (
                      <TableRow>
                        <TableCell colSpan={6} sx={{ textAlign: 'center', py: 8 }}>
                          <ShoppingBagOutlinedIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 2, display: 'block', mx: 'auto' }} />
                          <Typography variant="body2" color="text.secondary">
                            You haven't placed any orders yet.
                          </Typography>
                          <Button
                            variant="contained"
                            sx={{ mt: 3, borderRadius: 0, bgcolor: 'text.primary', color: 'background.paper', '&:hover': { bgcolor: 'surface.secondary', color: 'text.primary' } }}
                            onClick={() => navigate('/products')}
                          >
                            Start Shopping
                          </Button>
                        </TableCell>
                      </TableRow>
                    )
                    : data?.content.map((order) => (
                      <TableRow
                        key={order.id}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => navigate(`/orders/${order.id}`)}
                      >
                        <TableCell sx={{ fontWeight: 700, fontSize: '0.9rem' }}>
                          #{order.id}
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">
                            {new Date(order.createdAt).toLocaleDateString(undefined, {
                              year: 'numeric', month: 'short', day: 'numeric',
                            })}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <StatusChip status={order.status} />
                        </TableCell>
                        <TableCell align="center">
                          <Typography variant="body2">{order.itemCount}</Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="metadata" >
                            ₹{order.grandTotal.toLocaleString('en-IN')}
                          </Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Button
                            variant="outlined"
                            size="small"
                            onClick={(e) => {
                              e.stopPropagation();
                              navigate(`/orders/${order.id}`);
                            }}
                            sx={{ borderRadius: 0, fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}
                          >
                            View
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))
                }
              </TableBody>
            </Table>
          </TableContainer>

          {data && data.totalElements > SIZE && (
            <TablePagination
              component="div"
              count={data.totalElements}
              page={page}
              rowsPerPage={SIZE}
              rowsPerPageOptions={[SIZE]}
              onPageChange={(_, newPage) => setPage(newPage)}
            />
          )}
        </Paper>
      </Container>
    </PageWrapper>
  );
};

export default CustomerOrdersPage;
