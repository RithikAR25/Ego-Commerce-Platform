/**
 * CreateProductDialog.tsx
 */

import React from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box } from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createProductSchema, type CreateProductFormValues } from '@/schemas/catalog.schemas';
import { useCreateProduct, useAdminCategories } from '@/hooks/useAdminCatalog';
import { MenuItem } from '@mui/material';

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

  const activeSubcategories = categories?.filter(cat => cat.active && cat.parent !== null) || [];

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
              render={({ field }) => (
                <TextField
                  {...field}
                  select
                  label="Category"
                  fullWidth
                  error={!!errors.categoryId}
                  helperText={errors.categoryId?.message}
                  value={field.value || ''}
                >
                  <MenuItem value="">
                    <em>Select a category</em>
                  </MenuItem>
                  {activeSubcategories.map((cat) => (
                    <MenuItem key={cat.id} value={cat.id}>
                      {cat.parent?.name} - {cat.name}
                    </MenuItem>
                  ))}
                </TextField>
              )}
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
