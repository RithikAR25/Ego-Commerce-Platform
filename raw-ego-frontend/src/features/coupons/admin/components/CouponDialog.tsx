import React, { useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Grid,
} from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import type { CouponResponse, CreateCouponRequest, UpdateCouponRequest, DiscountType } from '@/types/coupon.types';
import { useCreateCoupon, useUpdateCoupon } from '../hooks/useAdminCoupons';
import { toast } from '@/store/uiStore';

interface CouponDialogProps {
  open: boolean;
  onClose: () => void;
  coupon?: CouponResponse | null;
}

interface CouponFormData {
  code: string;
  description: string;
  discountType: DiscountType;
  discountValue: string;
  maxDiscountAmount: string;
  minOrderAmount: string;
  maxUses: string;
  expiresAt: string;
}

const CouponDialog: React.FC<CouponDialogProps> = ({ open, onClose, coupon }) => {
  const isEditing = !!coupon;
  const { mutateAsync: createCoupon, isPending: isCreating } = useCreateCoupon();
  const { mutateAsync: updateCoupon, isPending: isUpdating } = useUpdateCoupon();

  const { control, handleSubmit, reset, watch, formState: { errors } } = useForm<CouponFormData>({
    defaultValues: {
      code: '',
      description: '',
      discountType: 'FLAT',
      discountValue: '',
      maxDiscountAmount: '',
      minOrderAmount: '',
      maxUses: '',
      expiresAt: '',
    },
  });

  const discountType = watch('discountType');

  useEffect(() => {
    if (open) {
      if (coupon) {
        reset({
          code: coupon.code,
          description: coupon.description || '',
          discountType: coupon.discountType,
          discountValue: coupon.discountValue.toString(),
          maxDiscountAmount: coupon.maxDiscountAmount?.toString() || '',
          minOrderAmount: coupon.minOrderAmount?.toString() || '',
          maxUses: coupon.maxUses?.toString() || '',
          expiresAt: coupon.expiresAt ? coupon.expiresAt.substring(0, 16) : '',
        });
      } else {
        reset({
          code: '',
          description: '',
          discountType: 'FLAT',
          discountValue: '',
          maxDiscountAmount: '',
          minOrderAmount: '',
          maxUses: '',
          expiresAt: '',
        });
      }
    }
  }, [open, coupon, reset]);

  const onSubmit = async (data: CouponFormData) => {
    try {
      if (isEditing) {
        const payload: UpdateCouponRequest = {
          description: data.description || undefined,
          maxDiscountAmount: data.maxDiscountAmount ? parseFloat(data.maxDiscountAmount) : null,
          minOrderAmount: data.minOrderAmount ? parseFloat(data.minOrderAmount) : null,
          maxUses: data.maxUses ? parseInt(data.maxUses, 10) : null,
          expiresAt: data.expiresAt ? new Date(data.expiresAt).toISOString() : null,
        };
        await updateCoupon({ id: coupon!.id, payload });
        toast.success('Coupon updated successfully');
      } else {
        const payload: CreateCouponRequest = {
          code: data.code.toUpperCase(),
          description: data.description || undefined,
          discountType: data.discountType,
          discountValue: parseFloat(data.discountValue),
          maxDiscountAmount: data.maxDiscountAmount ? parseFloat(data.maxDiscountAmount) : undefined,
          minOrderAmount: data.minOrderAmount ? parseFloat(data.minOrderAmount) : undefined,
          maxUses: data.maxUses ? parseInt(data.maxUses, 10) : undefined,
          expiresAt: data.expiresAt ? new Date(data.expiresAt).toISOString() : undefined,
        };
        await createCoupon(payload);
        toast.success('Coupon created successfully');
      }
      onClose();
    } catch (err: any) {
      const msg = err.response?.data?.errors?.[0]?.message || err.response?.data?.message || 'Failed to save coupon';
      toast.error(msg);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(onSubmit)}>
        <DialogTitle>{isEditing ? 'Edit Coupon' : 'Create New Coupon'}</DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={3}>
            <Grid size={{ xs: 12 }}>
              <Controller
                name="code"
                control={control}
                rules={{ 
                  required: 'Coupon code is required',
                  pattern: { value: /^[A-Za-z0-9_\-]+$/, message: 'Only letters, numbers, hyphens, and underscores' }
                }}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Coupon Code"
                    fullWidth
                    disabled={isEditing}
                    error={!!errors.code}
                    helperText={errors.code?.message}
                    slotProps={{ htmlInput: { style: { textTransform: 'uppercase' } } }}
                  />
                )}
              />
            </Grid>

            <Grid size={{ xs: 12 }}>
              <Controller
                name="description"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Description (Optional)"
                    fullWidth
                    multiline
                    rows={2}
                  />
                )}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <FormControl fullWidth disabled={isEditing}>
                <InputLabel>Discount Type</InputLabel>
                <Controller
                  name="discountType"
                  control={control}
                  render={({ field }) => (
                    <Select {...field} label="Discount Type">
                      <MenuItem value="FLAT">Flat Amount (₹)</MenuItem>
                      <MenuItem value="PERCENTAGE">Percentage (%)</MenuItem>
                    </Select>
                  )}
                />
              </FormControl>
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller
                name="discountValue"
                control={control}
                rules={{ 
                  required: 'Value is required',
                  min: { value: 0.01, message: 'Must be greater than 0' }
                }}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Discount Value"
                    type="number"
                    fullWidth
                    disabled={isEditing}
                    error={!!errors.discountValue}
                    helperText={errors.discountValue?.message}
                  />
                )}
              />
            </Grid>

            {discountType === 'PERCENTAGE' && (
              <Grid size={{ xs: 12, sm: 6 }}>
                <Controller
                  name="maxDiscountAmount"
                  control={control}
                  rules={{ min: { value: 0.01, message: 'Must be greater than 0' } }}
                  render={({ field }) => (
                    <TextField
                      {...field}
                      label="Max Discount Cap (₹) (Optional)"
                      type="number"
                      fullWidth
                      error={!!errors.maxDiscountAmount}
                      helperText={errors.maxDiscountAmount?.message}
                    />
                  )}
                />
              </Grid>
            )}

            <Grid size={{ xs: 12, sm: discountType === 'PERCENTAGE' ? 6 : 12 }}>
              <Controller
                name="minOrderAmount"
                control={control}
                rules={{ min: { value: 0.01, message: 'Must be greater than 0' } }}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Min Subtotal Required (₹) (Optional)"
                    type="number"
                    fullWidth
                    error={!!errors.minOrderAmount}
                    helperText={errors.minOrderAmount?.message}
                  />
                )}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller
                name="maxUses"
                control={control}
                rules={{ min: { value: 1, message: 'Must be at least 1' } }}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Max Uses (Optional)"
                    type="number"
                    fullWidth
                    error={!!errors.maxUses}
                    helperText={errors.maxUses?.message}
                  />
                )}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6 }}>
              <Controller
                name="expiresAt"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Expiration Date (Optional)"
                    type="datetime-local"
                    fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                )}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose} disabled={isCreating || isUpdating}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isCreating || isUpdating}>
            {isEditing ? 'Save Changes' : 'Create Coupon'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default CouponDialog;
