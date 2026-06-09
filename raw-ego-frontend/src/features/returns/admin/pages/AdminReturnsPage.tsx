import React, { useState } from 'react';
import { Box, Button, Select, MenuItem, FormControl, InputLabel } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminTable, { type Column } from '@/components/admin/AdminTable';
import StatusBadge from '@/components/admin/StatusBadge';
import { useAdminReturns } from '@/features/returns/admin/hooks/useAdminReturns';
import type { ReturnStatus, ReturnRequest } from '@/types/return.types';

const STATUS_OPTIONS: { value: ReturnStatus | 'ALL', label: string }[] = [
  { value: 'ALL', label: 'All Returns' },
  { value: 'REQUESTED', label: 'Requested' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'REFUND_INITIATED', label: 'Refund Initiated' },
  { value: 'REFUND_COMPLETED', label: 'Refund Completed' },
];

const AdminReturnsPage: React.FC = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [statusFilter, setStatusFilter] = useState<ReturnStatus | 'ALL'>('ALL');

  const { data, isLoading, isError, refetch } = useAdminReturns(
    statusFilter === 'ALL' ? undefined : statusFilter, 
    page, 
    rowsPerPage
  );

  const columns: Column<ReturnRequest>[] = [
    { id: 'id', label: 'Return ID', minWidth: 100, format: (val) => `#R-${val}` },
    { id: 'orderId', label: 'Order ID', minWidth: 100, format: (val) => `#${val}` },
    { 
      id: 'createdAt', 
      label: 'Requested On', 
      minWidth: 150,
      format: (val) => new Date(val).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) 
    },
    { 
      id: 'reason', 
      label: 'Reason', 
      minWidth: 150,
      format: (val: string) => val.replace(/_/g, ' ')
    },
    { 
      id: 'status', 
      label: 'Status', 
      minWidth: 150,
      format: (val) => <StatusBadge status={val} /> 
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
            navigate(`/admin/returns/${row.id}`);
          }}
        >
          Review
        </Button>
      )
    }
  ];

  return (
    <Box>
      <AdminPageHeader 
        title="Returns Management" 
        subtitle="Review and process customer return requests."
        action={
          <FormControl size="small" sx={{ minWidth: 200, bgcolor: 'background.paper' }}>
            <InputLabel id="return-status-filter-label">Filter by Status</InputLabel>
            <Select
              labelId="return-status-filter-label"
              value={statusFilter}
              label="Filter by Status"
              onChange={(e) => {
                setStatusFilter(e.target.value as ReturnStatus | 'ALL');
                setPage(0);
              }}
            >
              {STATUS_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
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
        emptyMessage="No return requests found."
        onRowClick={(row) => navigate(`/admin/returns/${row.id}`)}
      />
    </Box>
  );
};

export default AdminReturnsPage;
