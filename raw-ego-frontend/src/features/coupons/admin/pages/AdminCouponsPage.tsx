import React, { useState } from 'react';
import { Box, Button, Chip, IconButton, alpha } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import BlockIcon from '@mui/icons-material/Block';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminTable, { type Column } from '@/components/admin/AdminTable';
import { useAdminCoupons, useDeactivateCoupon } from '../hooks/useAdminCoupons';
import type { CouponResponse } from '@/types/coupon.types';
import CouponDialog from '../components/CouponDialog';


const AdminCouponsPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingCoupon, setEditingCoupon] = useState<CouponResponse | null>(null);

  const { data, isLoading, isError, refetch } = useAdminCoupons(page, rowsPerPage);
  const { mutate: deactivateCoupon } = useDeactivateCoupon();

  const handleCreateNew = () => {
    setEditingCoupon(null);
    setDialogOpen(true);
  };

  const handleEdit = (coupon: CouponResponse) => {
    setEditingCoupon(coupon);
    setDialogOpen(true);
  };

  const handleDeactivate = (coupon: CouponResponse) => {
    if (window.confirm(`Are you sure you want to deactivate coupon ${coupon.code}?`)) {
      deactivateCoupon(coupon.id);
    }
  };

  const columns: Column<CouponResponse>[] = [
    {
      id: 'code',
      label: 'Code',
      minWidth: 120,
      format: (val) => val,
    },
    {
      id: 'active',
      label: 'Status',
      minWidth: 100,
      format: (val) => {
        return (
          <Chip
            label={val ? 'Active' : 'Inactive'}
            size="small"
            sx={{
              bgcolor: (theme) => alpha(theme.palette.statusColors?.[val ? 'ACTIVE' : 'INACTIVE'] || theme.palette.text.primary, 0.1),
              color: (theme) => theme.palette.statusColors?.[val ? 'ACTIVE' : 'INACTIVE'],
              fontWeight: 600,
              fontSize: '0.7rem',
              border: (theme) => `1px solid ${alpha(theme.palette.statusColors?.[val ? 'ACTIVE' : 'INACTIVE'] || theme.palette.text.primary, 0.25)}`,
              borderRadius: '6px'
            }}
          />
        );
      },
    },
    {
      id: 'discountType',
      label: 'Discount',
      minWidth: 150,
      format: (_, row) => row.discountType === 'FLAT' 
        ? `₹${row.discountValue}` 
        : `${row.discountValue}%`,
    },
    {
      id: 'currentUses',
      label: 'Usage',
      minWidth: 100,
      align: 'center',
      format: (val, row) => `${val} / ${row.maxUses === null ? '∞' : row.maxUses}`,
    },
    {
      id: 'expiresAt',
      label: 'Expires',
      minWidth: 150,
      format: (val) => val ? new Date(val).toLocaleDateString() : 'Never',
    },
    {
      id: 'actions',
      label: '',
      align: 'right',
      format: (_, row) => (
        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
          <IconButton size="small" onClick={() => handleEdit(row)} title="Edit">
            <EditIcon fontSize="small" />
          </IconButton>
          {row.active && (
            <IconButton 
              size="small" 
              color="error" 
              onClick={() => handleDeactivate(row)} 
              title="Deactivate"
            >
              <BlockIcon fontSize="small" />
            </IconButton>
          )}
        </Box>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader
        title="Coupon Management"
        subtitle="Create and manage discount codes for the storefront."
        action={
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={handleCreateNew}
          >
            Create Coupon
          </Button>
        }
      />

      <AdminTable
        columns={columns}
        data={data?.content ?? []}
        totalElements={data?.totalElements ?? 0}
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
        emptyMessage="No coupons found."
      />

      <CouponDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        coupon={editingCoupon}
      />
    </Box>
  );
};

export default AdminCouponsPage;
