/**
 * AdminCategoriesPage.tsx
 *
 * Full category management page for the 3-level enterprise hierarchy (ROOT → GROUP → LEAF).
 *
 * Features:
 *  - Create category (ROOT, GROUP, or LEAF) with optional single parent
 *  - Level badge: ROOT / GROUP / LEAF (sourced from CategoryResponse.level)
 *  - Edit category details (name, description, imageUrl, displayOrder, slug override)
 *  - Manage Parents (cross-list to additional parents — for LEAF cross-listing)
 *  - Deactivate / Reactivate / Hard Delete category
 */

import React, { useState, useEffect } from 'react';
import { useForm, Controller }    from 'react-hook-form';
import { zodResolver }            from '@hookform/resolvers/zod';
import {
  Box, Typography, Button, Paper, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, FormControl,
  InputLabel, Select, MenuItem, FormHelperText, Chip, IconButton,
  Tooltip, Alert, Divider, CircularProgress, Stack,
} from '@mui/material';
import AddIcon                        from '@mui/icons-material/Add';
import DeleteIcon                      from '@mui/icons-material/Delete';
import DeleteForeverIcon               from '@mui/icons-material/DeleteForever';
import EditIcon                        from '@mui/icons-material/Edit';
import AccountTreeIcon                 from '@mui/icons-material/AccountTree';
import StarIcon                        from '@mui/icons-material/Star';
import StarBorderIcon                  from '@mui/icons-material/StarBorder';
import RemoveCircleOutlinedIcon        from '@mui/icons-material/RemoveCircleOutlined';
import CheckCircleOutlinedIcon         from '@mui/icons-material/CheckCircleOutlined';

import {
  useAdminCategories,
  useCreateCategory,
  useUpdateCategory,
  useDeactivateCategory,
  useActivateCategory,
  useHardDeleteCategory,
  useAdminParentLinks,
  useAddParentToCategory,
  useRemoveParentFromCategory,
  useSetPrimaryParent,
} from '@/hooks/useAdminCatalog';
import {
  createCategorySchema,
  updateCategorySchema,
  type CreateCategoryFormValues,
  type UpdateCategoryFormValues,
} from '@/schemas/catalog.schemas';
import { toast }                       from '@/store/uiStore';
import type { CategoryResponse, CategoryHierarchyLinkResponse, CreateCategoryPayload } from '@/types/catalog.types';
import StatusBadge from '@/components/admin/StatusBadge';

// ── Level Chip ────────────────────────────────────────────────────────────────

const LEVEL_CONFIG: Record<string, { label: string; color: 'default' | 'primary' | 'secondary' | 'success' | 'info' }> = {
  ROOT:  { label: 'ROOT',  color: 'default'   },
  GROUP: { label: 'GROUP', color: 'info'      },
  LEAF:  { label: 'LEAF',  color: 'success'   },
};

const LevelChip: React.FC<{ level: string | undefined }> = ({ level }) => {
  const cfg = LEVEL_CONFIG[level ?? 'ROOT'] ?? LEVEL_CONFIG.ROOT;
  return (
    <Chip
      size="small"
      label={cfg.label}
      color={cfg.color}
      variant="outlined"
      sx={{ fontWeight: 700, letterSpacing: '0.05em', fontSize: '0.65rem' }}
    />
  );
};

// ── Manage Parents Dialog ─────────────────────────────────────────────────────

interface ManageParentsDialogProps {
  category:  CategoryResponse;
  /** All categories that are valid parents for this category.
   *  For GROUP → valid parents are ROOT categories.
   *  For LEAF  → valid parents are GROUP categories.
   */
  validParents:  CategoryResponse[];
  onClose:   () => void;
}

const ManageParentsDialog: React.FC<ManageParentsDialogProps> = ({ category, validParents, onClose }) => {
  const { data: links = [], isLoading } = useAdminParentLinks(category.id);
  const { mutateAsync: addParent, isPending: isAdding }    = useAddParentToCategory();
  const { mutateAsync: removeParent, isPending: isRemoving } = useRemoveParentFromCategory();
  const { mutateAsync: setPrimary, isPending: isSettingPrimary } = useSetPrimaryParent();

  const [selectedParentId, setSelectedParentId] = useState<number | ''>('');

  // IDs already linked to this category
  const linkedParentIds = links.map(l => l.parentId);
  // Valid parents not yet linked
  const availableParents = validParents.filter(p => !linkedParentIds.includes(p.id));

  const handleAdd = async () => {
    if (!selectedParentId) return;
    try {
      await addParent({ childId: category.id, payload: { parentId: selectedParentId as number } });
      toast.success('Parent added');
      setSelectedParentId('');
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Failed to add parent');
    }
  };

  const handleRemove = async (parentId: number) => {
    if (!window.confirm('Remove this parent? (The category must retain at least one.)')) return;
    try {
      await removeParent({ childId: category.id, parentId });
      toast.success('Parent removed');
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Failed to remove parent');
    }
  };

  const handleSetPrimary = async (parentId: number) => {
    try {
      await setPrimary({ childId: category.id, parentId });
      toast.success('Primary parent updated');
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Failed to set primary parent');
    }
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility }}>
        Manage Parents — <span style={{ fontWeight: 400 }}>{category.name}</span>
        <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mt: 0.5 }}>
          Level: <strong>{category.level}</strong>
          {category.level === 'LEAF' && ' — Cross-list under additional GROUP categories'}
          {category.level === 'GROUP' && ' — Cross-list under additional ROOT categories'}
        </Typography>
      </DialogTitle>
      <DialogContent dividers>

        {/* Current parent links */}
        <Typography variant="metadata" sx={{ mb: 1, }}>Current Parents</Typography>
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={24} />
          </Box>
        ) : links.length === 0 ? (
          <Alert severity="warning" sx={{ mb: 2 }}>No parent links found. This is a root category.</Alert>
        ) : (
          <Stack spacing={1} sx={{ mb: 3 }}>
            {(links as CategoryHierarchyLinkResponse[]).map(link => (
              <Paper
                key={link.id}
                variant="outlined"
                sx={{ px: 2, py: 1.5, borderRadius: 2, display: 'flex', alignItems: 'center', gap: 2 }}
              >
                <Box sx={{ flex: 1 }}>
                  <Typography variant="metadata" >{link.parentName}</Typography>
                  {link.navigationLabel && (
                    <Typography variant="caption" color="text.secondary">
                      Label: "{link.navigationLabel}"
                    </Typography>
                  )}
                </Box>
                {link.primary && (
                  <Chip size="small" label="Canonical" color="primary" variant="filled" />
                )}
                <Tooltip title={link.primary ? 'Already the primary parent' : 'Set as canonical primary parent'}>
                  <span>
                    <IconButton
                      size="small"
                      color={link.primary ? 'primary' : 'default'}
                      disabled={link.primary || isSettingPrimary}
                      onClick={() => handleSetPrimary(link.parentId)}
                    >
                      {link.primary ? <StarIcon fontSize="small" /> : <StarBorderIcon fontSize="small" />}
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title={links.length <= 1 ? 'Cannot remove the last parent' : 'Remove parent'}>
                  <span>
                    <IconButton
                      size="small"
                      color="error"
                      disabled={links.length <= 1 || isRemoving}
                      onClick={() => handleRemove(link.parentId)}
                    >
                      <RemoveCircleOutlinedIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              </Paper>
            ))}
          </Stack>
        )}

        <Divider sx={{ mb: 2 }} />

        {/* Add a new parent */}
        <Typography variant="metadata" sx={{ mb: 1.5, }}>Add Parent</Typography>
        {availableParents.length === 0 ? (
          <Alert severity="info">This category is already linked to all available parent categories.</Alert>
        ) : (
          <Box sx={{ display: 'flex', gap: 2, alignItems: 'flex-start' }}>
            <FormControl fullWidth size="small">
              <InputLabel id="add-parent-label">Select parent category</InputLabel>
              <Select
                labelId="add-parent-label"
                label="Select parent category"
                value={selectedParentId}
                onChange={e => setSelectedParentId(e.target.value as number)}
              >
                {availableParents.map(p => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.name}
                    <Typography component="span" variant="caption" sx={{ ml: 1, color: 'text.secondary' }}>
                      ({p.level})
                    </Typography>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Button
              variant="contained"
              onClick={handleAdd}
              disabled={!selectedParentId || isAdding}
              sx={{ whiteSpace: 'nowrap', minWidth: 100 }}
            >
              {isAdding ? 'Adding…' : 'Add'}
            </Button>
          </Box>
        )}

        <Alert severity="info" sx={{ mt: 2 }}>
          <strong>Note:</strong> Adding a parent cross-lists this category in that parent's navigation section.
          The <strong>canonical</strong> (⭐) parent is used for breadcrumbs and SKU code derivation.
        </Alert>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Main Page ─────────────────────────────────────────────────────────────────

const AdminCategoriesPage: React.FC = () => {
  const { data: categories, isLoading } = useAdminCategories();
  const { mutateAsync: createCategory, isPending: isCreating } = useCreateCategory();
  const { mutateAsync: updateCategory, isPending: isUpdating } = useUpdateCategory();
  const { mutateAsync: deactivateCategory }                    = useDeactivateCategory();
  const { mutateAsync: activateCategory }                      = useActivateCategory();
  const { mutateAsync: hardDeleteCategory, isPending: isHardDeleting } = useHardDeleteCategory();

  // ── Dialog state ───────────────────────────────────────────────────────────
  const [createOpen, setCreateOpen]     = useState(false);
  const [editTarget, setEditTarget]     = useState<CategoryResponse | null>(null);
  const [parentsTarget, setParentsTarget] = useState<CategoryResponse | null>(null);
  const [hardDeleteTarget, setHardDeleteTarget] = useState<CategoryResponse | null>(null);

  // Derived category sets for parent pickers
  const rootCategories  = (categories ?? []).filter((c: CategoryResponse) => c.level === 'ROOT');
  const groupCategories = (categories ?? []).filter((c: CategoryResponse) => c.level === 'GROUP');

  // All non-LEAF categories (valid as parents for any child)
  const potentialParents = (categories ?? []).filter((c: CategoryResponse) => c.level !== 'LEAF');

  // Valid parents for the manage-parents dialog, based on the target's level
  const validParentsForTarget = parentsTarget
    ? (parentsTarget.level === 'GROUP' ? rootCategories : parentsTarget.level === 'LEAF' ? groupCategories : [])
    : [];

  // ── Create form ────────────────────────────────────────────────────────────
  const createForm = useForm<CreateCategoryFormValues>({
    resolver: zodResolver(createCategorySchema),
    defaultValues: { name: '', code: '', description: '', parentIds: [] },
  });

  // ── Edit form ──────────────────────────────────────────────────────────────
  const editForm = useForm<UpdateCategoryFormValues>({
    resolver: zodResolver(updateCategorySchema),
  });

  // Populate edit form when a target is selected
  useEffect(() => {
    if (editTarget) {
      editForm.reset({
        name:         editTarget.name,
        description:  editTarget.description ?? '',
        imageUrl:     editTarget.imageUrl    ?? '',
        displayOrder: editTarget.displayOrder,
        slug:         '',   // leave blank = keep current / auto-regen on name change
      });
    }
  }, [editTarget, editForm]);

  // ── Create submit ──────────────────────────────────────────────────────────
  const onCreateSubmit = async (data: CreateCategoryFormValues) => {
    try {
      const payload: CreateCategoryPayload = {
        name:        data.name,
        code:        data.code,
        description: data.description,
        parentIds:   data.parentIds && data.parentIds.length > 0 ? data.parentIds : undefined,
      };
      await createCategory(payload);
      toast.success('Category created');
      setCreateOpen(false);
      createForm.reset();
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Failed to create category');
    }
  };

  // ── Edit submit ────────────────────────────────────────────────────────────
  const onEditSubmit = async (data: UpdateCategoryFormValues) => {
    if (!editTarget) return;
    try {
      await updateCategory({
        id: editTarget.id,
        payload: {
          name:         data.name,
          description:  data.description || undefined,
          imageUrl:     data.imageUrl    || undefined,
          displayOrder: data.displayOrder,
          slug:         data.slug        || undefined,
        },
      });
      toast.success('Category updated');
      setEditTarget(null);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Failed to update category');
    }
  };

  // ── Deactivate ─────────────────────────────────────────────────────────────
  const handleDeactivate = async (id: number, name: string) => {
    if (!window.confirm(`Deactivate "${name}"? It will be hidden from the storefront.`)) return;
    try {
      await deactivateCategory(id);
      toast.success('Category deactivated');
    } catch {
      toast.error('Failed to deactivate category');
    }
  };

  // ── Reactivate ─────────────────────────────────────────────────────────────
  const handleActivate = async (id: number, name: string) => {
    if (!window.confirm(`Reactivate "${name}"? It will reappear on the storefront.`)) return;
    try {
      await activateCategory(id);
      toast.success('Category reactivated');
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Failed to reactivate category');
    }
  };

  // ── Hard delete ───────────────────────────────────────────
  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    try {
      await hardDeleteCategory(hardDeleteTarget.id);
      toast.success(`"${hardDeleteTarget.name}" permanently deleted`);
      setHardDeleteTarget(null);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      toast.error(err.response?.data?.message ?? 'Failed to permanently delete category');
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <Box sx={{ maxWidth: 1100, mx: 'auto' }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Box>
          <Typography variant="h4" sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontWeight: 600 }}>
            Categories
          </Typography>
          <Typography variant="body2" color="text.secondary">
            3-level hierarchy: ROOT → GROUP → LEAF. Products may only be assigned to LEAF categories.
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => { createForm.reset(); setCreateOpen(true); }}
          sx={{ borderRadius: 2 }}
        >
          Create Category
        </Button>
      </Box>

      {/* Level legend */}
      <Box sx={{ display: 'flex', gap: 1, mb: 3, flexWrap: 'wrap', alignItems: 'center' }}>
        <Typography variant="caption" color="text.secondary" sx={{ mr: 1 }}>Levels:</Typography>
        <LevelChip level="ROOT" />
        <Typography variant="caption" color="text.disabled">→</Typography>
        <LevelChip level="GROUP" />
        <Typography variant="caption" color="text.disabled">→</Typography>
        <LevelChip level="LEAF" />
        <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>(Products assigned to LEAF only)</Typography>
      </Box>

      <TableContainer component={Paper} variant="outlined" sx={{ borderRadius: 2 }}>
        <Table>
          <TableHead sx={{ bgcolor: 'surface.secondary' }}>
            <TableRow>
              <TableCell width={60}>ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Code</TableCell>
              <TableCell>Slug</TableCell>
              <TableCell>Level</TableCell>
              <TableCell>Parent</TableCell>
              <TableCell align="center">Products</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow><TableCell colSpan={9} align="center"><CircularProgress size={24} /></TableCell></TableRow>
            ) : categories?.length === 0 ? (
              <TableRow><TableCell colSpan={9} align="center">No categories found.</TableCell></TableRow>
            ) : (
              categories?.map((cat: CategoryResponse) => (
                <TableRow key={cat.id} hover>
                  <TableCell sx={{ color: 'text.disabled' }}>{cat.id}</TableCell>
                  <TableCell>
                    {/* Indentation: ROOT = no indent, GROUP = 1 level, LEAF = 2 levels */}
                    {cat.level === 'GROUP' && <span style={{ color: 'var(--mui-palette-text-disabled)', marginRight: 8 }}>↳</span>}
                    {cat.level === 'LEAF'  && <span style={{ color: 'var(--mui-palette-text-disabled)', marginRight: 8, marginLeft: 16 }}>↳</span>}
                    <Typography component="span" sx={{ fontWeight: cat.level === 'ROOT' ? 700 : cat.level === 'GROUP' ? 600 : 400 }}>
                      {cat.name}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip size="small" label={cat.code} variant="outlined" />
                  </TableCell>
                  <TableCell sx={{ color: 'text.secondary', fontSize: '0.8rem' }}>{cat.slug}</TableCell>
                  <TableCell>
                    <LevelChip level={cat.level} />
                  </TableCell>

                  {/* Parent column — shows canonical primary parent name */}
                  <TableCell sx={{ fontSize: '0.8rem', color: 'text.secondary' }}>
                    {cat.parent ? cat.parent.name : '—'}
                  </TableCell>

                  {/* Product count — only meaningful for LEAF */}
                  <TableCell align="center">
                    {cat.level === 'LEAF' ? (
                      <Chip
                        size="small"
                        label={cat.productCount ?? 0}
                        variant={(cat.productCount ?? 0) > 0 ? 'filled' : 'outlined'}
                        color="default"
                        sx={{
                          minWidth: 36,
                          fontVariantNumeric: 'tabular-nums',
                          fontWeight: (cat.productCount ?? 0) > 0 ? 600 : 400,
                          opacity: (cat.productCount ?? 0) === 0 ? 0.5 : 1,
                        }}
                      />
                    ) : (
                      <Typography variant="caption" color="text.disabled">—</Typography>
                    )}
                  </TableCell>

                  <TableCell>
                    <StatusBadge status={cat.active ? 'ACTIVE' : 'INACTIVE'} />
                  </TableCell>
                  <TableCell align="right">
                    <Box sx={{ display: 'flex', gap: 0.5, justifyContent: 'flex-end' }}>
                      <Tooltip title="Edit details">
                        <IconButton size="small" onClick={() => setEditTarget(cat)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      {/* Manage parents — available for GROUP (cross-list under multiple ROOTs)
                          and LEAF (cross-list under multiple GROUPs) */}
                      {cat.level !== 'ROOT' && (
                        <Tooltip title="Manage parents (cross-listing)">
                          <IconButton size="small" color="info" onClick={() => setParentsTarget(cat)}>
                            <AccountTreeIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {cat.active ? (
                        <Tooltip title="Deactivate">
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() => handleDeactivate(cat.id, cat.name)}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      ) : (
                        <Tooltip title="Reactivate">
                          <IconButton
                            size="small"
                            sx={{ color: 'success.main' }}
                            onClick={() => handleActivate(cat.id, cat.name)}
                          >
                            <CheckCircleOutlinedIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}

                      {/* Permanently Delete — only shown for inactive categories */}
                      {!cat.active && (
                        <Tooltip title="Permanently delete (irreversible)">
                          <IconButton
                            size="small"
                            sx={{ color: 'error.dark' }}
                            onClick={() => setHardDeleteTarget(cat)}
                          >
                            <DeleteForeverIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* ── Hard Delete Confirmation Dialog ────────────────────────────────── */}
      <Dialog
        open={!!hardDeleteTarget}
        onClose={() => setHardDeleteTarget(null)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, display: 'flex', alignItems: 'center', gap: 1, color: 'error.main' }}>
          <DeleteForeverIcon />
          Permanently Delete
        </DialogTitle>
        <DialogContent dividers>
          <Alert severity="error" sx={{ mb: 2 }}>
            This action is <strong>irreversible</strong>. The category will be removed from the database permanently.
          </Alert>
          <Typography variant="body2" sx={{ mb: 1 }}>
            You are about to permanently delete:
          </Typography>
          <Typography variant="metadata" sx={{ mb: 2 }}>
            {hardDeleteTarget?.name}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            This will only succeed if: LEAF categories have no products assigned;
            GROUP categories have no child LEAFs; ROOT categories have no child GROUPs.
            The system will return an error if these conditions are not met.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setHardDeleteTarget(null)} disabled={isHardDeleting}>
            Cancel
          </Button>
          <Button
            variant="contained"
            color="error"
            startIcon={isHardDeleting ? <CircularProgress size={16} color="inherit" /> : <DeleteForeverIcon />}
            disabled={isHardDeleting}
            onClick={handleHardDelete}
          >
            {isHardDeleting ? 'Deleting…' : 'Permanently Delete'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Create Dialog ────────────────────────────────────────────────────── */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility }}>Create Category</DialogTitle>
        <form onSubmit={createForm.handleSubmit(onCreateSubmit)}>
          <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            <Alert severity="info">
              <strong>3-level hierarchy:</strong> Leave "Parent Categories" empty to create a ROOT.
              Select a ROOT to create a GROUP. Select a GROUP to create a LEAF.
              Products can only be assigned to LEAF categories.
            </Alert>
            <TextField
              label="Name"
              fullWidth
              {...createForm.register('name')}
              error={!!createForm.formState.errors.name}
              helperText={createForm.formState.errors.name?.message}
            />
            <TextField
              label="Code (e.g. MEN, HOD, TSH)"
              fullWidth
              {...createForm.register('code')}
              error={!!createForm.formState.errors.code}
              helperText={createForm.formState.errors.code?.message ?? 'Short uppercase code. Cannot be changed after creation (embedded in SKUs).'}
              sx={{ '& input': { textTransform: 'uppercase' } }}
            />
            <TextField
              label="Description (Optional)"
              fullWidth
              multiline
              rows={2}
              {...createForm.register('description')}
              error={!!createForm.formState.errors.description}
              helperText={createForm.formState.errors.description?.message}
            />
            <FormControl fullWidth error={!!createForm.formState.errors.parentIds}>
              <InputLabel id="create-parent-label">Parent Category (Optional)</InputLabel>
              <Controller
                name="parentIds"
                control={createForm.control}
                render={({ field }) => (
                  <Select
                    {...field}
                    multiple
                    labelId="create-parent-label"
                    label="Parent Category (Optional)"
                    value={field.value ?? []}
                    renderValue={(selected) =>
                      (selected as number[])
                        .map(id => potentialParents.find(p => p.id === id)?.name ?? id)
                        .join(', ')
                    }
                  >
                    <MenuItem disabled value="">
                      <em>── ROOT categories ──</em>
                    </MenuItem>
                    {rootCategories.map((root: CategoryResponse) => (
                      <MenuItem key={root.id} value={root.id}>
                        <LevelChip level="ROOT" />
                        <Typography sx={{ ml: 1 }}>{root.name}</Typography>
                      </MenuItem>
                    ))}
                    <MenuItem disabled value="">
                      <em>── GROUP categories ──</em>
                    </MenuItem>
                    {groupCategories.map((group: CategoryResponse) => (
                      <MenuItem key={group.id} value={group.id}>
                        <LevelChip level="GROUP" />
                        <Typography sx={{ ml: 1 }}>{group.name}</Typography>
                        {group.parent && (
                          <Typography component="span" variant="caption" sx={{ ml: 0.5, color: 'text.secondary' }}>
                            (under {group.parent.name})
                          </Typography>
                        )}
                      </MenuItem>
                    ))}
                  </Select>
                )}
              />
              <FormHelperText>
                {createForm.formState.errors.parentIds?.message
                  ?? 'Empty = ROOT. Select ROOT = creates GROUP. Select GROUP = creates LEAF. First selected = canonical parent.'}
              </FormHelperText>
            </FormControl>
          </DialogContent>
          <DialogActions sx={{ p: 2 }}>
            <Button onClick={() => setCreateOpen(false)} disabled={isCreating}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={isCreating}>
              {isCreating ? 'Creating…' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* ── Edit Details Dialog ────────────────────────────────────────────── */}
      <Dialog open={!!editTarget} onClose={() => setEditTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility }}>
          Edit Category — <span style={{ fontWeight: 400 }}>{editTarget?.name}</span>
          {editTarget && (
            <Box sx={{ mt: 0.5 }}>
              <LevelChip level={editTarget.level} />
            </Box>
          )}
        </DialogTitle>
        <form onSubmit={editForm.handleSubmit(onEditSubmit)}>
          <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            <Alert severity="info">
              <strong>Code ({editTarget?.code}) is not editable</strong> — it is embedded in all variant SKUs.
            </Alert>
            <TextField
              label="Name"
              fullWidth
              {...editForm.register('name')}
              error={!!editForm.formState.errors.name}
              helperText={editForm.formState.errors.name?.message}
            />
            <TextField
              label="Description"
              fullWidth
              multiline
              rows={2}
              {...editForm.register('description')}
              error={!!editForm.formState.errors.description}
              helperText={editForm.formState.errors.description?.message}
            />
            <TextField
              label="Image URL"
              fullWidth
              {...editForm.register('imageUrl')}
              error={!!editForm.formState.errors.imageUrl}
              helperText={editForm.formState.errors.imageUrl?.message}
            />
            <TextField
              label="Display Order"
              fullWidth
              type="number"
              {...editForm.register('displayOrder', { valueAsNumber: true })}
              error={!!editForm.formState.errors.displayOrder}
              helperText={editForm.formState.errors.displayOrder?.message}
            />
            <TextField
              label="Slug override (optional)"
              fullWidth
              {...editForm.register('slug')}
              error={!!editForm.formState.errors.slug}
              helperText={
                editForm.formState.errors.slug?.message
                  ?? `Current slug: "${editTarget?.slug}". Leave blank to auto-generate from new name. WARNING: changing slug breaks existing URLs.`
              }
              placeholder="e.g. oversized-tees"
            />
          </DialogContent>
          <DialogActions sx={{ p: 2 }}>
            <Button onClick={() => setEditTarget(null)} disabled={isUpdating}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={isUpdating}>
              {isUpdating ? 'Saving…' : 'Save Changes'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* ── Manage Parents Dialog ─────────────────────────────────────────── */}
      {parentsTarget && (
        <ManageParentsDialog
          category={parentsTarget}
          validParents={validParentsForTarget}
          onClose={() => setParentsTarget(null)}
        />
      )}
    </Box>
  );
};

export default AdminCategoriesPage;
