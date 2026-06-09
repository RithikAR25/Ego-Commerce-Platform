/**
 * AdminInventoryPage.tsx
 *
 * Live admin inventory dashboard.
 * Data comes from GET /api/v1/admin/inventory (paginated, filterable).
 * Inline editing hits PUT /admin/inventory/{id} and PATCH /admin/inventory/{id}/threshold.
 */

import React, { useState, useCallback } from 'react';
import {
  Box,
  Typography,
  TextField,
  Tab,
  Tabs,
  Chip,
  IconButton,
  Tooltip,
  InputAdornment,
  Skeleton,
  Alert,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import EditIcon from '@mui/icons-material/Edit';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminTable from '@/components/admin/AdminTable';
import type { Column } from '@/components/admin/AdminTable';
import { useInventory, useSetInventory, useUpdateThreshold } from '@/hooks/useAdminCatalog';
import type { InventoryItemResponse, InventoryStatus, InventoryFilterParams } from '@/types/catalog.types';
import { toast } from '@/store/uiStore';
import { useDebounce } from '@/hooks/useDebounce';

// ── Status tab configuration /hooks/useDebounce' ──────────────────────────────────────────────────

const TABS: { label: string; status: InventoryStatus | ''; color: string }[] = [
  { label: 'All Variants', status: '', color: 'default' },
  { label: 'In Stock', status: 'IN_STOCK', color: 'success' },
  { label: 'Low Stock', status: 'LOW_STOCK', color: 'warning' },
  { label: 'Out of Stock', status: 'OUT_OF_STOCK', color: 'error' },
];

// ── Status badge ──────────────────────────────────────────────────────────────

const statusChip = (status: InventoryStatus) => {
  const map: Record<InventoryStatus, { label: string; color: 'success' | 'warning' | 'error' }> = {
    IN_STOCK: { label: 'In Stock', color: 'success' },
    LOW_STOCK: { label: 'Low Stock', color: 'warning' },
    OUT_OF_STOCK: { label: 'Out of Stock', color: 'error' },
  };
  const cfg = map[status] ?? { label: status, color: 'error' };
  return (
    <Chip
      label={cfg.label}
      color={cfg.color}
      size="small"
      variant="outlined"
      sx={{ fontWeight: 600, minWidth: 110 }}
    />
  );
};

// ── Inline number editor ──────────────────────────────────────────────────────

interface InlineEditorProps {
  value: number;
  min?: number;
  onSave: (val: number) => Promise<void>;
  disabled?: boolean;
}

const InlineEditor: React.FC<InlineEditorProps> = ({ value, min = 0, onSave, disabled }) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(String(value));
  const [loading, setLoading] = useState(false);

  const handleSave = async () => {
    const num = parseInt(draft, 10);
    if (isNaN(num) || num < min) return;
    setLoading(true);
    try {
      await onSave(num);
      setEditing(false);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => { setEditing(false); setDraft(String(value)); };

  if (!editing) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5 }}>
        <Typography variant="metadata" sx={{ minWidth: 28, textAlign: 'center' }}>
          {value}
        </Typography>
        {!disabled && (
          <Tooltip title="Edit">
            <IconButton size="small" onClick={() => { setDraft(String(value)); setEditing(true); }}>
              <EditIcon sx={{ fontSize: 14 }} />
            </IconButton>
          </Tooltip>
        )}
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5 }}>
      <TextField
        size="small"
        type="number"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        disabled={loading}
        autoFocus
        slotProps={{ htmlInput: { min, style: { width: 56, textAlign: 'center', padding: '4px 6px' } } }}
        sx={{ '& .MuiOutlinedInput-root': { borderRadius: 1.5 } }}
      />
      <Tooltip title="Save">
        <span>
          <IconButton size="small" color="success" onClick={handleSave} disabled={loading}>
            <CheckIcon sx={{ fontSize: 14 }} />
          </IconButton>
        </span>
      </Tooltip>
      <Tooltip title="Cancel">
        <IconButton size="small" onClick={handleCancel} disabled={loading}>
          <CloseIcon sx={{ fontSize: 14 }} />
        </IconButton>
      </Tooltip>
    </Box>
  );
};

// ── Main page ─────────────────────────────────────────────────────────────────

const AdminInventoryPage: React.FC = () => {
  const [tabIdx, setTabIdx] = useState(0);
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [sortBy] = useState<InventoryFilterParams['sortBy']>('sku');
  const [sortDir] = useState<'asc' | 'desc'>('asc');

  const debouncedSearch = useDebounce(searchInput, 350);

  const currentStatus = TABS[tabIdx].status;

  const filterParams: InventoryFilterParams = {
    status: currentStatus || undefined,
    search: debouncedSearch || undefined,
    page,
    size: rowsPerPage,
    sortBy,
    sortDir,
  };

  const { data, isLoading, isError } = useInventory(filterParams);

  const { mutateAsync: setInventory } = useSetInventory();
  const { mutateAsync: updateThreshold } = useUpdateThreshold();

  const handleSetQty = useCallback(
    async (variantId: number, quantityAvailable: number) => {
      try {
        await setInventory({ variantId, payload: { quantityAvailable } });
        toast.success('Stock updated');
      } catch {
        toast.error('Failed to update stock');
      }
    },
    [setInventory]
  );

  const handleSetThreshold = useCallback(
    async (variantId: number, threshold: number) => {
      try {
        await updateThreshold({ variantId, threshold });
        toast.success('Threshold updated');
      } catch {
        toast.error('Failed to update threshold');
      }
    },
    [updateThreshold]
  );


  const columns: Column<InventoryItemResponse & { id: number }>[] = [
    {
      id: 'productName',
      label: 'Product',
      minWidth: 220,
      format: (_, row) => (
        <Box>
          <Typography variant="metadata" >
            {row.productName}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {row.variantLabel}
          </Typography>
        </Box>
      ),
    },
    {
      id: 'sku',
      label: 'SKU',
      minWidth: 180,
      format: (val) => (
        <Typography variant="caption" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.75rem' }}>
          {val}
        </Typography>
      ),
    },
    {
      id: 'stockStatus',
      label: 'Status',
      minWidth: 130,
      format: (val) => statusChip(val as InventoryStatus),
    },
    {
      id: 'quantityAvailable',
      label: 'Available',
      minWidth: 120,
      align: 'center',
      format: (val, row) => (
        <InlineEditor
          value={val}
          min={0}
          onSave={(qty) => handleSetQty(row.variantId, qty)}
        />
      ),
    },
    {
      id: 'quantityReserved',
      label: 'Reserved',
      minWidth: 100,
      align: 'center',
      format: (val) => (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center' }}>
          {val}
        </Typography>
      ),
    },
    {
      id: 'lowStockThreshold',
      label: 'Alert Threshold',
      minWidth: 140,
      align: 'center',
      format: (val, row) => (
        <InlineEditor
          value={val}
          min={1}
          onSave={(threshold) => handleSetThreshold(row.variantId, threshold)}
        />
      ),
    },
    {
      id: 'updatedAt',
      label: 'Last Updated',
      minWidth: 150,
      format: (val) => (
        <Typography variant="caption" color="text.secondary">
          {new Date(val).toLocaleString()}
        </Typography>
      ),
    },
  ];

  const rows = data?.content.map(r => ({ ...r, id: r.variantId })) ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <Box>
      <AdminPageHeader
        title="Inventory Management"
        subtitle="Monitor and manage real-time stock levels across all product variants."
      />

      {/* Status tabs */}
      <Tabs
        value={tabIdx}
        onChange={(_, v) => { setTabIdx(v); setPage(0); }}
        sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}
      >
        {TABS.map((t) => (
          <Tab
            key={t.label}
            label={t.label}
            sx={{ fontWeight: 600, textTransform: 'none', fontSize: '0.875rem' }}
          />
        ))}
      </Tabs>

      {/* Search bar */}
      <Box sx={{ mb: 3 }}>
        <TextField
          placeholder="Search by product name or SKU…"
          value={searchInput}
          onChange={(e) => { setSearchInput(e.target.value); setPage(0); }}
          size="small"
          sx={{ width: { xs: '100%', sm: 380 } }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" color="action" />
                </InputAdornment>
              ),
            }
          }}
        />
      </Box>

      {/* Error */}
      {isError && (
        <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>
          Failed to load inventory data. Make sure the backend is running.
        </Alert>
      )}

      {/* Loading skeleton */}
      {isLoading && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} variant="rectangular" height={52} sx={{ borderRadius: 1 }} />
          ))}
        </Box>
      )}

      {/* Table */}
      {!isLoading && (
        <AdminTable
          columns={columns}
          data={rows}
          totalElements={total}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={setPage}
          onRowsPerPageChange={(s) => { setRowsPerPage(s); setPage(0); }}
          emptyMessage={
            debouncedSearch
              ? `No variants matching "${debouncedSearch}"`
              : currentStatus
                ? `No variants with status ${currentStatus.replace('_', ' ')}`
                : 'No inventory records found.'
          }
        />
      )}
    </Box>
  );
};

export default AdminInventoryPage;
