import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  adminListCoupons,
  adminGetCoupon,
  adminCreateCoupon,
  adminUpdateCoupon,
  adminDeactivateCoupon,
} from '@/api/admin.coupon.api';
import type { CreateCouponRequest, UpdateCouponRequest } from '@/types/coupon.types';

export const adminCouponKeys = {
  all: ['adminCoupons'] as const,
  lists: () => [...adminCouponKeys.all, 'list'] as const,
  list: (page: number, size: number) => [...adminCouponKeys.lists(), page, size] as const,
  details: () => [...adminCouponKeys.all, 'detail'] as const,
  detail: (id: number) => [...adminCouponKeys.details(), id] as const,
};

export const useAdminCoupons = (page: number, size: number) => {
  return useQuery({
    queryKey: adminCouponKeys.list(page, size),
    queryFn: () => adminListCoupons(page, size),
    placeholderData: (previousData) => previousData,
  });
};

export const useAdminCoupon = (id: number) => {
  return useQuery({
    queryKey: adminCouponKeys.detail(id),
    queryFn: () => adminGetCoupon(id),
    enabled: !!id,
  });
};

export const useCreateCoupon = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateCouponRequest) => adminCreateCoupon(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminCouponKeys.lists() });
    },
  });
};

export const useUpdateCoupon = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: UpdateCouponRequest }) => adminUpdateCoupon(id, payload),
    onSuccess: (updatedCoupon) => {
      queryClient.invalidateQueries({ queryKey: adminCouponKeys.lists() });
      queryClient.setQueryData(adminCouponKeys.detail(updatedCoupon.id), updatedCoupon);
    },
  });
};

export const useDeactivateCoupon = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => adminDeactivateCoupon(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: adminCouponKeys.lists() });
      queryClient.invalidateQueries({ queryKey: adminCouponKeys.detail(id) });
    },
  });
};
