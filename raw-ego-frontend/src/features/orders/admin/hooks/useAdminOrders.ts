import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminGetOrders, adminGetOrder, adminUpdateOrderStatus } from '@/api/order.api';
import type { OrderStatus, UpdateOrderStatusPayload } from '@/types/order.types';
import { toast } from '@/store/uiStore';

export const adminOrderKeys = {
  all: ['adminOrders'] as const,
  lists: () => [...adminOrderKeys.all, 'list'] as const,
  list: (status?: OrderStatus, page: number = 0) => [...adminOrderKeys.lists(), { status, page }] as const,
  detail: (orderId: number) => [...adminOrderKeys.all, 'detail', orderId] as const,
};

export const useAdminOrder = (orderId: number) => {
  return useQuery({
    queryKey: adminOrderKeys.detail(orderId),
    queryFn: () => adminGetOrder(orderId),
    enabled: orderId > 0,
    staleTime: 30_000,
  });
};

export const useAdminOrders = (status?: OrderStatus, page: number = 0, size: number = 20) => {
  return useQuery({
    queryKey: adminOrderKeys.list(status, page),
    queryFn: () => adminGetOrders(status, page, size),
    staleTime: 30_000, // 30 seconds
  });
};

export const useAdminUpdateOrderStatus = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ orderId, payload }: { orderId: number; payload: UpdateOrderStatusPayload }) => 
      adminUpdateOrderStatus(orderId, payload),
    onSuccess: (updatedOrder) => {
      // Invalidate the admin order lists to reflect changes
      queryClient.invalidateQueries({ queryKey: adminOrderKeys.lists() });
      
      // Update the admin detail cache instantly with the new order data
      queryClient.setQueryData(adminOrderKeys.detail(updatedOrder.id), updatedOrder);
      
      // Also invalidate the customer's specific order cache just in case
      queryClient.invalidateQueries({ queryKey: ['orders', 'detail', updatedOrder.id] });
      
      toast.success(`Order status updated to ${updatedOrder.status}`);
    },
    onError: (error: any) => {
      const message = error.response?.data?.message || 'Failed to update order status';
      toast.error(message);
    }
  });
};
