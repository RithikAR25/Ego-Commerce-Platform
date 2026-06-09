/**
 * UpdateVariantDialog.tsx
 */

import React, { useEffect } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, FormControlLabel, Switch } from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { updateVariantSchema, type UpdateVariantFormValues } from '@/schemas/catalog.schemas';
import { useUpdateVariant } from '@/hooks/useAdminCatalog';
import type { ProductVariantResponse } from '@/types/catalog.types';

interface Props {
  productSlug: string;
  variant: ProductVariantResponse | null;
  open: boolean;
  onClose: () => void;
}

const UpdateVariantDialog: React.FC<Props> = ({ productSlug, variant, open, onClose }) => {
  const { register, handleSubmit, formState: { errors }, reset, control } = useForm<UpdateVariantFormValues>({
    resolver: zodResolver(updateVariantSchema),
    defaultValues: { price: 0, active: true }
  });
  
  useEffect(() => {
    if (variant && open) {
      reset({
        price: variant.price,
        compareAtPrice: variant.compareAtPrice ?? undefined,
        costPrice: undefined, // Cost price is not returned in ProductVariantResponse typically, so leave blank or undefined
        weightGrams: variant.weightGrams ?? undefined,
        active: variant.active,
      });
    }
  }, [variant, open, reset]);

  const { mutate: updateVariant, isPending } = useUpdateVariant(productSlug);

  const onSubmit = (data: UpdateVariantFormValues) => {
    if (!variant) return;
    updateVariant({ variantId: variant.id, payload: data }, {
      onSuccess: () => {
        onClose();
      }
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle>Edit Variant: {variant?.sku}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Price" type="number" {...register('price', { valueAsNumber: true })} error={!!errors.price} helperText={errors.price?.message} fullWidth />
              <TextField label="Compare At Price" type="number" {...register('compareAtPrice', { valueAsNumber: true })} error={!!errors.compareAtPrice} helperText={errors.compareAtPrice?.message} fullWidth />
            </Box>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Cost Price" type="number" {...register('costPrice', { valueAsNumber: true })} error={!!errors.costPrice} helperText={errors.costPrice?.message} fullWidth />
              <TextField label="Weight (g)" type="number" {...register('weightGrams', { valueAsNumber: true })} error={!!errors.weightGrams} helperText={errors.weightGrams?.message} fullWidth />
            </Box>
            <Box>
              <Controller
                name="active"
                control={control}
                render={({ field }) => (
                  <FormControlLabel
                    control={<Switch checked={field.value} onChange={field.onChange} />}
                    label="Active (Visible to customers)"
                  />
                )}
              />
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={isPending}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isPending}>Save Changes</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default UpdateVariantDialog;
