/**
 * SetInventoryDialog.tsx
 *
 * Admin dialog for updating a variant's inventory quantity.
 * Field is quantityAvailable — matches UpdateInventoryRequest.java exactly.
 */

import React from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, Typography } from '@mui/material';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { setInventorySchema, type SetInventoryFormValues } from '@/schemas/catalog.schemas';
import { useSetInventory } from '@/hooks/useAdminCatalog';
import type { ProductVariantResponse } from '@/types/catalog.types';

interface Props {
  productSlug: string;
  variant: ProductVariantResponse | null;
  open: boolean;
  onClose: () => void;
}

const SetInventoryDialog: React.FC<Props> = ({ productSlug, variant, open, onClose }) => {
  const { register, handleSubmit, formState: { errors } } = useForm<SetInventoryFormValues>({
    resolver: zodResolver(setInventorySchema),
    defaultValues: { quantityAvailable: 0 }
  });
  
  const { mutate: setInventory, isPending } = useSetInventory(productSlug);

  const onSubmit = (data: SetInventoryFormValues) => {
    if (!variant) return;
    setInventory({ variantId: variant.id, payload: data }, {
      onSuccess: () => { onClose(); }
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle>
          Update Inventory
          <Typography variant="body2" color="text.secondary">{variant?.sku}</Typography>
        </DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField
              label="Available Quantity"
              type="number"
              {...register('quantityAvailable', { valueAsNumber: true })}
              error={!!errors.quantityAvailable}
              helperText={errors.quantityAvailable?.message}
              fullWidth
              slotProps={{ htmlInput: { min: 0 } }}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={isPending}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isPending}>Save</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default SetInventoryDialog;
