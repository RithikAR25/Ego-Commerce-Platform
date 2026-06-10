/**
 * CreateProductDialog.tsx
 */

import React from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box } from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createProductSchema, type CreateProductFormValues } from '@/schemas/catalog.schemas';
import { useCreateProduct, useAdminCategories } from '@/hooks/useAdminCatalog';
import CategoryDrilldownMenu from './CategoryDrilldownMenu';
import type { CategoryResponse } from '@/types/catalog.types';

interface Props {
  open: boolean;
  onClose: () => void;
}

const CreateProductDialog: React.FC<Props> = ({ open, onClose }) => {
  const { register, handleSubmit, formState: { errors }, reset, control } = useForm<CreateProductFormValues>({
    resolver: zodResolver(createProductSchema),
    defaultValues: { name: '', description: '' }
  });
  
  const { data: categories } = useAdminCategories();
  
  const { mutate: createProduct, isPending } = useCreateProduct();

  const onSubmit = (data: CreateProductFormValues) => {
    createProduct(data, {
      onSuccess: () => {
        reset();
        onClose();
      }
    });
  };

  const getCategoryPath = (leaf: CategoryResponse, allCategories: CategoryResponse[]) => {
    const groupId = leaf.parent?.id;
    const groupNode = allCategories.find((c) => c.id === groupId);

    const rootId = groupNode?.parent?.id;
    const rootNode = allCategories.find((c) => c.id === rootId);

    const rootName = rootNode?.name || 'Unknown';
    const groupName = groupNode?.name || leaf.parent?.name || 'Unknown';

    return `${rootName} → ${groupName} → ${leaf.name}`;
  };

  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle>Create New Product</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField label="Product Name" {...register('name')} error={!!errors.name} helperText={errors.name?.message} fullWidth />
            <Controller
              name="categoryId"
              control={control}
              render={({ field }) => {
                const selectedCat = categories?.find((c) => c.id === field.value);
                const displayValue = selectedCat ? getCategoryPath(selectedCat, categories || []) : '';

                return (
                  <>
                    <TextField
                      label="Category"
                      fullWidth
                      error={!!errors.categoryId}
                      helperText={errors.categoryId?.message}
                      value={displayValue}
                      onClick={(e) => setAnchorEl(e.currentTarget)}
                      slotProps={{
                        input: {
                          readOnly: true,
                          sx: { cursor: 'pointer' },
                        }
                      }}
                      placeholder="Select a category"
                    />
                    <CategoryDrilldownMenu
                      anchorEl={anchorEl}
                      onClose={() => setAnchorEl(null)}
                      categories={categories || []}
                      onSelect={(id) => {
                        field.onChange(id);
                        setAnchorEl(null);
                      }}
                    />
                  </>
                );
              }}
            />
            <TextField label="Description" multiline rows={3} {...register('description')} fullWidth />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={isPending}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isPending}>Create</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default CreateProductDialog;
