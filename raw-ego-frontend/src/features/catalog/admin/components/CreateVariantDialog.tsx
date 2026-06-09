/**
 * CreateVariantDialog.tsx
 *
 * Admin dialog for creating a product variant (Color × Size combination).
 *
 * Architectural notes:
 * - Variants are NOT standalone objects. They are linked to attribute values
 *   through the variant_attribute_values join table in the database.
 * - The backend requires exactly colorAttributeValueId + sizeAttributeValueId,
 *   both of which must belong to this product's attribute types.
 * - Color and Size dropdowns are populated dynamically from product.attributeTypes.
 * - If attributeTypes is empty or missing Color/Size, this dialog blocks submission
 *   and directs the admin to first set up the attribute matrix.
 */

import React from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button,
  TextField, Box, FormControl, InputLabel, Select, MenuItem,
  FormHelperText, Typography, Alert, CircularProgress, Divider, alpha,
  Stack, Chip,
} from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { createVariantSchema, type CreateVariantFormValues } from '@/schemas/catalog.schemas';
import { useCreateVariant } from '@/hooks/useAdminCatalog';
import type { AttributeTypeResponse, AttributeValueSummary } from '@/types/catalog.types';

interface Props {
  productSlug:    string;
  productId:      number;
  attributeTypes: AttributeTypeResponse[];
  open:           boolean;
  onClose:        () => void;
}

const CreateVariantDialog: React.FC<Props> = ({ productSlug, productId, attributeTypes, open, onClose }) => {
  // Dynamically identify Color and Size from the product's attribute types
  const colorType = attributeTypes.find(t => t.name.toLowerCase() === 'color');
  const sizeType  = attributeTypes.find(t => t.name.toLowerCase() === 'size');

  // Determine readiness — both types must exist AND have at least one value
  const isReady = Boolean(colorType?.values.length) && Boolean(sizeType?.values.length);

  const { control, handleSubmit, formState: { errors }, reset } = useForm<CreateVariantFormValues>({
    resolver: zodResolver(createVariantSchema),
    defaultValues: {
      initialStock:      0,
      lowStockThreshold: 5,
    },
  });

  const { mutate: createVariant, isPending, error: mutationError } = useCreateVariant(productSlug);

  const onSubmit = (data: CreateVariantFormValues) => {
    createVariant({ productId, payload: data }, {
      onSuccess: () => { reset(); onClose(); }
    });
  };

  const handleClose = () => { reset(); onClose(); };

  // Parse backend error message for display
  const apiError = mutationError
    ? ((mutationError as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Failed to create variant. Please try again.')
    : null;

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle sx={{ pb: 1 }}>
          <Typography variant="metadata" >Add Variant</Typography>
          <Typography variant="body2" color="text.secondary">
            Creating a purchasable Color × Size combination
          </Typography>
        </DialogTitle>

        <DialogContent>
          {/* ── Not Ready: missing attribute types ── */}
          {!isReady && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              <Typography variant="metadata" sx={{ mb: 0.5 }}>
                Attribute matrix not ready
              </Typography>
              <Typography variant="body2">
                {attributeTypes.length === 0
                  ? 'This product has no attribute types defined. Go to the Attribute Matrix section and add "Color" and "Size" types with their values first.'
                  : !colorType
                  ? 'Missing "Color" attribute type. Add it with at least one color value.'
                  : !sizeType
                  ? 'Missing "Size" attribute type. Add it with at least one size value.'
                  : !colorType.values.length
                  ? `The "Color" type exists but has no values. Add at least one color value (e.g. Black/BLK).`
                  : `The "Size" type exists but has no values. Add at least one size value (e.g. M, L).`}
              </Typography>
            </Alert>
          )}

          {/* ── API Error ── */}
          {apiError && (
            <Alert severity="error" sx={{ mb: 2, fontSize: '0.82rem' }}>
              {apiError}
            </Alert>
          )}

          {/* ── Color + Size Selectors ── */}
          <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
            {/* Color */}
            <FormControl fullWidth error={!!errors.colorAttributeValueId} disabled={!colorType?.values.length}>
              <InputLabel>Color *</InputLabel>
              <Controller
                name="colorAttributeValueId"
                control={control}
                render={({ field }) => (
                  <Select
                    {...field}
                    label="Color *"
                    value={field.value ?? ''}
                    onChange={(e) => field.onChange(Number(e.target.value))}
                  >
                    {(colorType?.values ?? []).map((v: AttributeValueSummary) => (
                      <MenuItem key={v.id} value={v.id}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          {v.hexColor && (
                            <Box sx={{
                              width: 16, height: 16, borderRadius: '50%',
                              bgcolor: v.hexColor,
                              border: (theme) => `1px solid ${alpha(theme.palette.text.primary, 0.2)}`,
                              flexShrink: 0,
                            }} />
                          )}
                          {v.value}
                          <Chip label={v.code} size="small" variant="outlined" sx={{ ml: 0.5, height: 18, fontSize: '0.65rem' }} />
                        </Box>
                      </MenuItem>
                    ))}
                  </Select>
                )}
              />
              {errors.colorAttributeValueId && (
                <FormHelperText>{errors.colorAttributeValueId.message}</FormHelperText>
              )}
            </FormControl>

            {/* Size */}
            <FormControl fullWidth error={!!errors.sizeAttributeValueId} disabled={!sizeType?.values.length}>
              <InputLabel>Size *</InputLabel>
              <Controller
                name="sizeAttributeValueId"
                control={control}
                render={({ field }) => (
                  <Select
                    {...field}
                    label="Size *"
                    value={field.value ?? ''}
                    onChange={(e) => field.onChange(Number(e.target.value))}
                  >
                    {(sizeType?.values ?? []).map((v: AttributeValueSummary) => (
                      <MenuItem key={v.id} value={v.id}>
                        {v.value}
                        <Chip label={v.code} size="small" variant="outlined" sx={{ ml: 0.5, height: 18, fontSize: '0.65rem' }} />
                      </MenuItem>
                    ))}
                  </Select>
                )}
              />
              {errors.sizeAttributeValueId && (
                <FormHelperText>{errors.sizeAttributeValueId.message}</FormHelperText>
              )}
            </FormControl>
          </Box>

          <Divider sx={{ my: 2 }}>
            <Typography variant="caption" color="text.secondary">Pricing</Typography>
          </Divider>

          {/* ── Pricing ── */}
          <Stack spacing={2}>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <Controller
                name="price"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Price *"
                    type="number"
                    value={field.value ?? ''}
                    onChange={(e) => field.onChange(e.target.value === '' ? undefined : Number(e.target.value))}
                    error={!!errors.price}
                    helperText={errors.price?.message}
                    fullWidth
                    slotProps={{ 
                      htmlInput: { min: 0.01, step: 0.01 },
                      input: { startAdornment: <Typography sx={{ mr: 0.5 }}>₹</Typography> } 
                    }}
                  />
                )}
              />
              <Controller
                name="compareAtPrice"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Compare Price"
                    type="number"
                    value={field.value ?? ''}
                    onChange={(e) => field.onChange(e.target.value === '' ? undefined : Number(e.target.value))}
                    error={!!errors.compareAtPrice}
                    helperText={errors.compareAtPrice?.message ?? 'Optional — crossed-out price'}
                    fullWidth
                    slotProps={{ 
                      htmlInput: { min: 0.01, step: 0.01 },
                      input: { startAdornment: <Typography sx={{ mr: 0.5 }}>₹</Typography> } 
                    }}
                  />
                )}
              />
            </Box>

            <Divider>
              <Typography variant="caption" color="text.secondary">Inventory</Typography>
            </Divider>

            <Box sx={{ display: 'flex', gap: 2 }}>
              <Controller
                name="initialStock"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Initial Stock"
                    type="number"
                    value={field.value ?? 0}
                    onChange={(e) => field.onChange(Number(e.target.value))}
                    error={!!errors.initialStock}
                    helperText={errors.initialStock?.message}
                    fullWidth
                    slotProps={{ htmlInput: { min: 0 } }}
                  />
                )}
              />
              <Controller
                name="lowStockThreshold"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Low Stock Alert At"
                    type="number"
                    value={field.value ?? 5}
                    onChange={(e) => field.onChange(Number(e.target.value))}
                    error={!!errors.lowStockThreshold}
                    helperText={errors.lowStockThreshold?.message ?? 'Alert when stock drops below this'}
                    fullWidth
                    slotProps={{ htmlInput: { min: 1 } }}
                  />
                )}
              />
            </Box>
          </Stack>
        </DialogContent>

        <DialogActions>
          <Button onClick={handleClose} disabled={isPending}>Cancel</Button>
          <Button
            type="submit"
            variant="contained"
            disabled={isPending || !isReady}
          >
            {isPending ? <CircularProgress size={18} /> : 'Create Variant'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default CreateVariantDialog;
