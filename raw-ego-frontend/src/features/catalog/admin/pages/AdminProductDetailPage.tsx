/**
 * AdminProductDetailPage.tsx
 *
 * Core admin page for managing a single product.
 * Contains: Header (status), Variants section, Gallery Images, Variant Images.
 */

import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Button,
  Paper,
  Grid,
  Chip,
  IconButton,
  Menu,
  MenuItem,
  Skeleton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
  alpha,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import AddIcon from '@mui/icons-material/Add';
import DeleteForeverIcon from '@mui/icons-material/DeleteForever';
import {
  useAdminProductDetail,
  useUpdateProductStatus,
  useArchiveProduct,
  useHardDeleteProduct,
} from '@/hooks/useAdminCatalog';
import StatusBadge from '../components/StatusBadge';
import CreateVariantDialog from '../components/CreateVariantDialog';
import UpdateVariantDialog from '../components/UpdateVariantDialog';
import SetInventoryDialog from '../components/SetInventoryDialog';
import ImageUploader from '../components/ImageUploader';
import ImageGrid from '../components/ImageGrid';
import AttributeManagerPanel from '../components/AttributeManagerPanel';
import type { ProductStatus, ProductVariantResponse } from '@/types/catalog.types';

import { toast } from '@/store/uiStore';
import EditIcon from '@mui/icons-material/Edit';
import InventoryIcon from '@mui/icons-material/Inventory';
import AdminProductReviewsPanel from '@/features/reviews/admin/components/AdminProductReviewsPanel';

const AdminProductDetailPage: React.FC = () => {
  const { id: slug } = useParams<{ id: string }>(); // route is /admin/products/:id, but we use slug
  const navigate = useNavigate();

  const { data: product, isLoading } = useAdminProductDetail(slug ?? '');
  const { mutateAsync: updateStatus } = useUpdateProductStatus();
  const { mutateAsync: archiveProduct } = useArchiveProduct();
  const { mutateAsync: hardDeleteProduct, isPending: isDeleting } = useHardDeleteProduct();

  const [createVariantOpen, setCreateVariantOpen] = useState(false);
  const [editingVariant, setEditingVariant] = useState<ProductVariantResponse | null>(null);
  const [inventoryVariant, setInventoryVariant] = useState<ProductVariantResponse | null>(null);

  // Permanent delete confirmation state
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteConfirmCode, setDeleteConfirmCode] = useState('');

  // Status Menu
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const handleStatusClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };
  const handleStatusClose = () => {
    setAnchorEl(null);
  };

  const handleStatusChange = async (newStatus: ProductStatus) => {
    handleStatusClose();
    if (!product) return;
    try {
      await updateStatus({ productId: product.id, payload: { status: newStatus } });
      toast.success(`Status updated to ${newStatus}`);
    } catch {
      toast.error('Failed to update status');
    }
  };

  const handleArchive = async () => {
    handleStatusClose();
    if (!product) return;
    if (!window.confirm('Are you sure you want to archive this product?')) return;
    try {
      await archiveProduct(product.id);
      toast.success('Product archived');
      navigate('/admin/products');
    } catch {
      toast.error('Failed to archive product');
    }
  };

  const handleHardDelete = async () => {
    if (!product) return;
    if (deleteConfirmCode !== product.productCode) {
      toast.error('Product code does not match. Deletion cancelled.');
      return;
    }
    try {
      await hardDeleteProduct(product.id);
      toast.success(`Product "${product.name}" permanently deleted.`);
      navigate('/admin/products');
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Failed to permanently delete product.');
    } finally {
      setDeleteDialogOpen(false);
      setDeleteConfirmCode('');
    }
  };

  if (isLoading) {
    return <Box sx={{ p: 4 }}><Skeleton variant="rectangular" height={200} /></Box>;
  }

  if (!product) {
    return <Typography sx={{ p: 4 }}>Product not found.</Typography>;
  }

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', pb: 10 }}>
      {/* ── Header ── */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 2,
          pb: 4,
          mb: 5,
          borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
        }}
      >
        <IconButton
          onClick={() => navigate('/admin/products')}
          sx={{ mt: 0.5, color: 'text.secondary', '&:hover': { color: 'text.primary', bgcolor: 'transparent' } }}
        >
          <ArrowBackIcon />
        </IconButton>
        <Box sx={{ flex: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
            <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary' }}>
              {product.name}
            </Typography>
            <StatusBadge status={product.status} />
          </Box>
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.8rem', letterSpacing: '0.05em' }}>
            {product.productCode} • {product.category.name} • {product.slug}
          </Typography>
        </Box>
        <Box>
          <Button
            variant="outlined"
            onClick={handleStatusClick}
            endIcon={<MoreVertIcon />}
            sx={{ borderRadius: 0, borderColor: 'border.default', color: 'text.secondary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.1em', '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'transparent' } }}
          >
            Actions
          </Button>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleStatusClose}
            slotProps={{ paper: { sx: { bgcolor: 'surface.secondary', border: 1, borderColor: 'border.default', borderRadius: 0 } } }}
          >
            {product.status === 'DRAFT' && <MenuItem onClick={() => handleStatusChange('ACTIVE')}>Publish (Set Active)</MenuItem>}
            {product.status === 'ACTIVE' && <MenuItem onClick={() => handleStatusChange('DRAFT')}>Unpublish (Set Draft)</MenuItem>}
            {product.status !== 'ARCHIVED' && <MenuItem onClick={handleArchive} sx={{ color: 'error.main' }}>Archive Product</MenuItem>}
            {product.status === 'ARCHIVED' && <MenuItem onClick={() => handleStatusChange('DRAFT')}>Restore to Draft</MenuItem>}
            {product.status === 'ARCHIVED' && (
              <MenuItem
                onClick={() => { handleStatusClose(); setDeleteDialogOpen(true); }}
                sx={{ color: 'error.dark', fontWeight: 700 }}
              >
                <DeleteForeverIcon fontSize="small" sx={{ mr: 1 }} />
                Permanently Delete
              </MenuItem>
            )}
          </Menu>
        </Box>
      </Box>

      {/* ── Attribute Matrix Section ── */}
      <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, mb: 4 }}>
        <Box sx={{ p: 3 }}>
          <AttributeManagerPanel
            productId={product.id}
            productSlug={product.slug}
            attributeTypes={product.attributeTypes}
          />
        </Box>
      </Paper>

      {/* ── Variants Section ── */}
      <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, mb: 4 }}>
        <Box sx={{ p: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase' }}>
            Variants
          </Typography>
          <Button
            variant="outlined"
            size="small"
            startIcon={<AddIcon />}
            onClick={() => setCreateVariantOpen(true)}
            sx={{ borderRadius: 0, borderColor: 'border.default', color: 'text.secondary', fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.6rem', letterSpacing: '0.1em', '&:hover': { borderColor: 'text.primary', color: 'text.primary', bgcolor: 'transparent' } }}
          >
            Add Variant
          </Button>
        </Box>
        <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}` }} />
        <Box sx={{ p: 3 }}>
          {product.variants.length === 0 ? (
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontStyle: 'italic' }}>No variants added yet.</Typography>
          ) : (
            <Grid container spacing={2}>
              {product.variants.map((v) => (
                <Grid size={{ xs: 12, md: 6, lg: 4 }} key={v.id}>
                  <Paper
                    elevation={0}
                    sx={{ p: 2, border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'background.default', borderRadius: 0 }}
                  >
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                      <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, color: 'text.primary', fontSize: '0.85rem' }}>{v.sku}</Typography>
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        <Chip
                          label={v.active ? 'ACTIVE' : 'INACTIVE'}
                          size="small"
                          sx={{ 
                            height: 20, 
                            fontSize: '0.65rem', 
                            mr: 1,
                            bgcolor: (theme) => alpha(theme.palette.statusColors?.[v.active ? 'ACTIVE' : 'INACTIVE'] || theme.palette.text.primary, 0.1),
                            color: (theme) => theme.palette.statusColors?.[v.active ? 'ACTIVE' : 'INACTIVE'],
                            border: (theme) => `1px solid ${alpha(theme.palette.statusColors?.[v.active ? 'ACTIVE' : 'INACTIVE'] || theme.palette.text.primary, 0.25)}`,
                            borderRadius: '6px'
                          }}
                        />
                        <Chip
                          label={v.stockStatus.replace('_', ' ')}
                          size="small"
                          sx={{ 
                            height: 20, 
                            fontSize: '0.65rem',
                            bgcolor: (theme) => alpha(theme.palette.statusColors?.[v.stockStatus === 'IN_STOCK' ? 'ACTIVE' : v.stockStatus === 'LOW_STOCK' ? 'PENDING_PAYMENT' : 'OUT_OF_STOCK'] || theme.palette.text.primary, 0.1),
                            color: (theme) => theme.palette.statusColors?.[v.stockStatus === 'IN_STOCK' ? 'ACTIVE' : v.stockStatus === 'LOW_STOCK' ? 'PENDING_PAYMENT' : 'OUT_OF_STOCK'],
                            border: (theme) => `1px solid ${alpha(theme.palette.statusColors?.[v.stockStatus === 'IN_STOCK' ? 'ACTIVE' : v.stockStatus === 'LOW_STOCK' ? 'PENDING_PAYMENT' : 'OUT_OF_STOCK'] || theme.palette.text.primary, 0.25)}`,
                            borderRadius: '6px'
                          }}
                        />
                      </Box>
                    </Box>
                    <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontSize: '0.8rem', mb: 2 }}>
                      {v.attributeValues.map(av => av.value).join(' • ')}
                    </Typography>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', mb: 2 }}>
                      <Box>
                        <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block' }}>Price</Typography>
                        <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', }}>₹{v.price}</Typography>
                      </Box>
                      <Box sx={{ textAlign: 'right' }}>
                        <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.6rem', letterSpacing: '0.15em', color: 'text.secondary', textTransform: 'uppercase', display: 'block' }}>Stock</Typography>
                        <Typography variant="metadata" sx={{ color: v.stockStatus === 'IN_STOCK' ? 'success.main' : v.stockStatus === 'LOW_STOCK' ? 'warning.main' : 'error.main', }}>
                          {v.stockStatus.replace('_', ' ')}
                        </Typography>
                      </Box>
                    </Box>
                    <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}`, pt: 1, mx: -2, mt: 0, display: 'flex', justifyContent: 'flex-end', gap: 1, px: 2 }}>
                      <IconButton size="small" onClick={() => setEditingVariant(v)} title="Edit Variant">
                        <EditIcon fontSize="small" />
                      </IconButton>
                      <IconButton size="small" onClick={() => setInventoryVariant(v)} title="Update Inventory">
                        <InventoryIcon fontSize="small" />
                      </IconButton>
                    </Box>
                  </Paper>
                </Grid>
              ))}
            </Grid>
          )}
        </Box>
      </Paper>

      {/* ── Gallery Images ── */}
      <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, mb: 4 }}>
        <Box sx={{ p: 3 }}>
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 1, display: 'block' }}>
            Gallery Images
          </Typography>
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontSize: '0.85rem', mb: 3, fontStyle: 'italic' }}>
            These images appear below the main variant gallery on the product detail page.
          </Typography>
          <ImageUploader productId={product.id} nextDisplayOrder={product.galleryImages.length} />
          <Box sx={{ mt: 4 }}>
            <ImageGrid productId={product.id} images={product.galleryImages} />
          </Box>
        </Box>
      </Paper>

      {/* ── Variant Images ── */}
      <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0 }}>
        <Box sx={{ p: 3 }}>
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 1, display: 'block' }}>
            Variant Images
          </Typography>
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontSize: '0.85rem', mb: 3, fontStyle: 'italic' }}>
            Upload specific images for each variant. The primary image here becomes the hero image when this color is selected.
          </Typography>
          
          {product.variants.map((v) => (
            <Box key={v.id} sx={{ mb: 6, '&:last-child': { mb: 0 } }}>
              <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}`, mb: 3 }} />
              <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.primary', mb: 2 }}>
                {v.sku} <Typography component="span" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'text.secondary', fontWeight: 400, fontSize: '0.8rem' }}>({v.attributeValues.map(av => av.value).join(', ')})</Typography>
              </Typography>
              
              <ImageUploader productId={product.id} variantId={v.id} nextDisplayOrder={v.images.length} />
              <Box sx={{ mt: 3 }}>
                <ImageGrid productId={product.id} variantId={v.id} images={v.images} />
              </Box>
            </Box>
          ))}
          {product.variants.length === 0 && (
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontStyle: 'italic' }}>Add variants first to upload variant images.</Typography>
          )}
        </Box>
      </Paper>

      {/* ── Reviews ── */}
      <Paper elevation={0} sx={{ border: (theme) => `1px solid ${theme.palette.border.default}`, bgcolor: 'surface.secondary', borderRadius: 0, mb: 4, mt: 4 }}>
        <Box sx={{ p: 3 }}>
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 700, fontSize: '0.65rem', letterSpacing: '0.2em', color: 'text.secondary', textTransform: 'uppercase', mb: 1, display: 'block' }}>
            Customer Reviews
          </Typography>
          <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontSize: '0.85rem', mb: 3, fontStyle: 'italic' }}>
            Manage and moderate reviews left by customers for this product.
          </Typography>
          <AdminProductReviewsPanel productId={product.id} />
        </Box>
      </Paper>

      <CreateVariantDialog
        open={createVariantOpen}
        onClose={() => setCreateVariantOpen(false)}
        productId={product.id}
        productSlug={product.slug}
        attributeTypes={product.attributeTypes}
      />
      
      <UpdateVariantDialog
        open={!!editingVariant}
        onClose={() => setEditingVariant(null)}
        variant={editingVariant}
        productSlug={product.slug}
      />
      
      <SetInventoryDialog
        open={!!inventoryVariant}
        onClose={() => setInventoryVariant(null)}
        variant={inventoryVariant}
        productSlug={product.slug}
      />

      {/* ── Permanent Delete Confirmation Dialog ───────────────────────────── */}
      <Dialog
        open={deleteDialogOpen}
        onClose={() => { setDeleteDialogOpen(false); setDeleteConfirmCode(''); }}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, color: 'error.dark', fontWeight: 700 }}>
          Permanently Delete Product
        </DialogTitle>
        <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          <Alert severity="error" icon={<DeleteForeverIcon />}>
            <strong>This is irreversible.</strong> All variants, images, attribute types,
            reviews, and search-index entries for <strong>{product.name}</strong> will be
            permanently deleted. This action cannot be undone.
          </Alert>
          <Alert severity="warning">
            Products with <strong>order history</strong> cannot be deleted — the backend
            will reject the request with a 409 error.
          </Alert>
          <Typography variant="body2" color="text.secondary">
            To confirm, type the product code <strong>{product.productCode}</strong> below:
          </Typography>
          <TextField
            label={`Type "${product.productCode}" to confirm`}
            fullWidth
            size="small"
            value={deleteConfirmCode}
            onChange={(e) => setDeleteConfirmCode(e.target.value)}
            error={deleteConfirmCode.length > 0 && deleteConfirmCode !== product.productCode}
            helperText={
              deleteConfirmCode.length > 0 && deleteConfirmCode !== product.productCode
                ? 'Code does not match'
                : ' '
            }
            autoFocus
          />
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <Button
            onClick={() => { setDeleteDialogOpen(false); setDeleteConfirmCode(''); }}
            disabled={isDeleting}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            color="error"
            startIcon={<DeleteForeverIcon />}
            onClick={handleHardDelete}
            disabled={deleteConfirmCode !== product.productCode || isDeleting}
          >
            {isDeleting ? 'Deleting…' : 'Permanently Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default AdminProductDetailPage;
