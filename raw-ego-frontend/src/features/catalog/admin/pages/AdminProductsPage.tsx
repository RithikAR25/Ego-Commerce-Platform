/**
 * AdminProductsPage.tsx
 *
 * Lists all products for admin management.
 * Provides entry point to create a new product.
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  TablePagination,
  Skeleton,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useAdminProducts } from '@/hooks/useAdminCatalog';
import StatusBadge from '../components/StatusBadge';
import CreateProductDialog from '../components/CreateProductDialog';

const AdminProductsPage: React.FC = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  const { data: pageData, isLoading } = useAdminProducts(page, rowsPerPage);

  const handleChangePage = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', pb: 4, mb: 5, borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
        <Box>
          <Typography
            variant="overline"
            sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.25em', color: 'text.secondary', display: 'block', mb: 1 }}
          >
            ADMIN CONSOLE
          </Typography>
          <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', }}>
            Products
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<AddIcon />}
          onClick={() => setCreateDialogOpen(true)}
          sx={{
            borderRadius: 0,
            borderColor: 'border.default',
            color: 'text.secondary',
            fontFamily: (theme) => theme.typography.fontFamilyUtility,
            fontWeight: 700,
            fontSize: '0.65rem',
            letterSpacing: '0.1em',
            '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'transparent' },
          }}
        >
          Create Product
        </Button>
      </Box>

      <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 60, fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.15em', textTransform: 'uppercase', color: 'text.secondary', bgcolor: 'background.default', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>Image</TableCell>
                <TableCell sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.15em', textTransform: 'uppercase', color: 'text.secondary', bgcolor: 'background.default', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>Name & Slug</TableCell>
                <TableCell sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.15em', textTransform: 'uppercase', color: 'text.secondary', bgcolor: 'background.default', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>Category</TableCell>
                <TableCell sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.15em', textTransform: 'uppercase', color: 'text.secondary', bgcolor: 'background.default', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>Status</TableCell>
                <TableCell align="right" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.15em', textTransform: 'uppercase', color: 'text.secondary', bgcolor: 'background.default', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>Price Range</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
            {isLoading ? (
              Array.from(new Array(5)).map((_, i) => (
                <TableRow key={i} sx={{ '& td': { borderBottom: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary' } }}>
                  <TableCell><Skeleton variant="rectangular" width={40} height={50} sx={{ bgcolor: 'surface.tertiary' }} /></TableCell>
                  <TableCell><Skeleton width="60%" sx={{ bgcolor: 'surface.tertiary' }} /><Skeleton width="40%" height={16} sx={{ bgcolor: 'surface.tertiary' }} /></TableCell>
                  <TableCell><Skeleton width="80%" sx={{ bgcolor: 'surface.tertiary' }} /></TableCell>
                  <TableCell><Skeleton width={80} height={24} sx={{ bgcolor: 'surface.tertiary' }} /></TableCell>
                  <TableCell align="right"><Skeleton width={60} sx={{ ml: 'auto', bgcolor: 'surface.tertiary' }} /></TableCell>
                </TableRow>
              ))
            ) : pageData?.content.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ py: 5, bgcolor: 'surface.secondary', borderBottom: (theme) => `1px solid ${theme.palette.border.default}`, fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontStyle: 'italic' }}>
                  No products found. Create one to get started.
                </TableCell>
              </TableRow>
            ) : (
              pageData?.content.map((product) => (
                <TableRow
                  key={product.id}
                  hover
                  onClick={() => navigate(`/admin/products/${product.slug}`)}
                  sx={{
                    cursor: 'pointer',
                    bgcolor: 'surface.secondary',
                    '& td': { borderBottom: (theme) => `1px solid ${theme.palette.border.default}` },
                    '&:hover': { bgcolor: 'surface.tertiary' },
                  }}
                >
                  <TableCell>
                    {product.primaryImageUrl ? (
                      <Box
                        component="img"
                        src={product.primaryImageUrl}
                        sx={{ width: 40, height: 50, objectFit: 'cover', borderRadius: 0 }}
                      />
                    ) : (
                      <Box sx={{ width: 40, height: 50, bgcolor: 'surface.tertiary' }} />
                    )}
                  </TableCell>
                  <TableCell>
                    <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.primary', }}>
                      {product.name}
                    </Typography>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.72rem', letterSpacing: '0.05em' }}>
                      {product.slug}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.85rem' }}>
                      {product.categoryName}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={product.status} />
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary', }}>
                      {product.minPrice != null && product.maxPrice != null
                        ? product.minPrice === product.maxPrice
                          ? `₹${product.minPrice}`
                          : `₹${product.minPrice} - ₹${product.maxPrice}`
                        : '-'}
                    </Typography>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        </TableContainer>
        <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}` }}>
          <TablePagination
            rowsPerPageOptions={[10, 20, 50]}
            component="div"
            count={pageData?.totalElements ?? 0}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
            sx={{
              color: 'text.secondary',
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              '& .MuiTablePagination-selectLabel, & .MuiTablePagination-displayedRows': {
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontSize: '0.75rem',
              },
              '& .MuiIconButton-root': { color: 'text.secondary' },
            }}
          />
        </Box>
      </Paper>

      <CreateProductDialog
        open={createDialogOpen}
        onClose={() => setCreateDialogOpen(false)}
      />
    </Box>
  );
};

export default AdminProductsPage;
