import React, { useState } from 'react';
import { Box, Button, Select, MenuItem, FormControl, InputLabel, FormControlLabel, Switch } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminTable, { type Column } from '@/components/admin/AdminTable';
import StatusBadge from '@/components/admin/StatusBadge';
import { useAdminOrders } from '@/features/orders/admin/hooks/useAdminOrders';
import type { OrderStatus, OrderSummary } from '@/types/order.types';

const STATUS_OPTIONS: { value: OrderStatus | 'ALL', label: string }[] = [
  { value: 'ALL',              label: 'All Orders' },
  { value: 'PENDING_PAYMENT',  label: 'Pending Payment' },
  { value: 'CONFIRMED',        label: 'Confirmed' },
  { value: 'PROCESSING',       label: 'Processing' },
  { value: 'SHIPPED',          label: 'Shipped' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { value: 'DELIVERED',        label: 'Delivered' },
  { value: 'CANCELLED',        label: 'Cancelled' },
  { value: 'REFUNDED',         label: 'Refunded' },
];

const AdminOrdersPage: React.FC = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [statusFilter, setStatusFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [dense, setDense] = useState(false);

  const { data, isLoading, isError, refetch } = useAdminOrders(
    statusFilter === 'ALL' ? undefined : statusFilter, 
    page, 
    rowsPerPage
  );

  const columns: Column<OrderSummary>[] = [
    { id: 'id', label: 'Order ID', minWidth: 100, format: (val) => `#${val}` },
    { 
      id: 'createdAt', 
      label: 'Date', 
      minWidth: 150,
      format: (val) => new Date(val).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) 
    },
    { 
      id: 'status', 
      label: 'Status', 
      minWidth: 150,
      format: (val) => <StatusBadge status={val} /> 
    },
    { id: 'itemCount', label: 'Items', minWidth: 100, align: 'center' },
    { 
      id: 'grandTotal', 
      label: 'Total', 
      minWidth: 120, 
      align: 'right',
      format: (val) => `₹${Number(val).toLocaleString()}` 
    },
    {
      id: 'actions',
      label: '',
      align: 'right',
      format: (_, row) => (
        <Button 
          variant="outlined" 
          size="small"
          onClick={(e) => {
            e.stopPropagation();
            navigate(`/admin/orders/${row.id}`);
          }}
        >
          View
        </Button>
      )
    }
  ];

  return (
    <Box>
      <AdminPageHeader 
        title="Orders Management" 
        subtitle="Manage customer orders, track fulfillment, and update statuses."
        action={
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
            <FormControlLabel
              control={<Switch checked={dense} onChange={(e) => setDense(e.target.checked)} size="small" />}
              label="Dense padding"
              sx={{ typography: 'body2' }}
            />
            <FormControl size="small" sx={{ minWidth: 200, bgcolor: 'background.paper' }}>
              <InputLabel id="status-filter-label">Filter by Status</InputLabel>
              <Select
                labelId="status-filter-label"
                value={statusFilter}
                label="Filter by Status"
                onChange={(e) => {
                  setStatusFilter(e.target.value as OrderStatus | 'ALL');
                  setPage(0); // Reset page on filter change
                }}
              >
                {STATUS_OPTIONS.map((opt) => (
                  <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>
        }
      />

      <AdminTable
        columns={columns}
        data={data?.content || []}
        totalElements={data?.totalElements || 0}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={setPage}
        onRowsPerPageChange={(newSize) => {
          setRowsPerPage(newSize);
          setPage(0);
        }}
        isLoading={isLoading}
        isError={isError}
        onRetry={() => refetch()}
        emptyMessage="No orders found."
        onRowClick={(row) => navigate(`/admin/orders/${row.id}`)}
        dense={dense}
      />
    </Box>
  );
};

export default AdminOrdersPage;
