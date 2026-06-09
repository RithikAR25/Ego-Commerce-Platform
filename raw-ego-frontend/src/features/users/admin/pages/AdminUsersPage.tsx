import React, { useState } from 'react';
import {
  Box,
  Chip,
  TextField,
  InputAdornment,
  Button,
  alpha,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import SearchIcon from '@mui/icons-material/Search';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminTable, { type Column } from '@/components/admin/AdminTable';
import { useAdminUsers } from '@/features/users/admin/hooks/useAdminUsers';
import type { AdminUserResponse } from '@/types/user.types';


// ── Role chip ─────────────────────────────────────────────────────────────────

const RoleChip: React.FC<{ role: string }> = ({ role }) => {
  return (
    <Chip
      label={role}
      size="small"
      sx={{
        bgcolor:    (theme) => alpha(theme.palette.statusColors?.[role === 'ADMIN' ? 'ADMIN' : 'USER'] || theme.palette.text.primary, 0.1),
        color:      (theme) => theme.palette.statusColors?.[role === 'ADMIN' ? 'ADMIN' : 'USER'],
        fontWeight: 600,
        fontSize:   '0.7rem',
        border:     (theme) => `1px solid ${alpha(theme.palette.statusColors?.[role === 'ADMIN' ? 'ADMIN' : 'USER'] || theme.palette.text.primary, 0.25)}`,
        borderRadius: '6px'
      }}
    />
  );
};

// ── Status chip ───────────────────────────────────────────────────────────────

const ActiveChip: React.FC<{ active: boolean }> = ({ active }) => {
  return (
    <Chip
      label={active ? 'Active' : 'Suspended'}
      size="small"
      sx={{
        bgcolor:    (theme) => alpha(theme.palette.statusColors?.[active ? 'ACTIVE' : 'SUSPENDED'] || theme.palette.text.primary, 0.1),
        color:      (theme) => theme.palette.statusColors?.[active ? 'ACTIVE' : 'SUSPENDED'],
        fontWeight: 600,
        fontSize:   '0.7rem',
        border:     (theme) => `1px solid ${alpha(theme.palette.statusColors?.[active ? 'ACTIVE' : 'SUSPENDED'] || theme.palette.text.primary, 0.25)}`,
        borderRadius: '6px'
      }}
    />
  );
};

// ── Page ──────────────────────────────────────────────────────────────────────

const AdminUsersPage: React.FC = () => {
  const navigate = useNavigate();
  const [page,        setPage]        = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [searchInput, setSearchInput] = useState('');
  const [activeSearch, setActiveSearch] = useState<string | undefined>(undefined);

  const { data, isLoading, isError, refetch } = useAdminUsers(
    page,
    rowsPerPage,
    activeSearch,
  );

  const handleSearch = () => {
    setPage(0);
    setActiveSearch(searchInput.trim() || undefined);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSearch();
  };

  const handleClear = () => {
    setSearchInput('');
    setActiveSearch(undefined);
    setPage(0);
  };

  const columns: Column<AdminUserResponse>[] = [
    {
      id:       'id',
      label:    'ID',
      minWidth: 70,
      format:   (val) => `#${val}`,
    },
    {
      id:       'firstName',
      label:    'Name',
      minWidth: 160,
      format:   (_, row) => `${row.firstName} ${row.lastName}`,
    },
    {
      id:       'email',
      label:    'Email',
      minWidth: 220,
    },
    {
      id:       'role',
      label:    'Role',
      minWidth: 110,
      format:   (val) => <RoleChip role={val} />,
    },
    {
      id:       'active',
      label:    'Status',
      minWidth: 110,
      format:   (val) => <ActiveChip active={val} />,
    },
    {
      id:       'createdAt',
      label:    'Joined',
      minWidth: 140,
      format:   (val) =>
        new Date(val).toLocaleDateString(undefined, {
          year:  'numeric',
          month: 'short',
          day:   'numeric',
        }),
    },
    {
      id:    'actions',
      label: '',
      align: 'right',
      format: (_, row) => (
        <Button
          variant="outlined"
          size="small"
          onClick={(e) => {
            e.stopPropagation();
            navigate(`/admin/users/${row.id}`);
          }}
        >
          View
        </Button>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader
        title="Users Management"
        subtitle="Browse, search, and inspect customer and admin accounts."
        action={
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            <TextField
              size="small"
              placeholder="Search by name or email…"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onKeyDown={handleKeyDown}
              sx={{ minWidth: 240, bgcolor: 'background.paper' }}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" sx={{ color: 'text.disabled' }} />
                    </InputAdornment>
                  ),
                }
              }}
            />
            <Button variant="contained" size="small" onClick={handleSearch}>
              Search
            </Button>
            {activeSearch && (
              <Button variant="text" size="small" onClick={handleClear}>
                Clear
              </Button>
            )}
          </Box>
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
        emptyMessage={
          activeSearch
            ? `No users found matching "${activeSearch}".`
            : 'No users found.'
        }
        onRowClick={(row) => navigate(`/admin/users/${row.id}`)}
      />
    </Box>
  );
};

export default AdminUsersPage;
