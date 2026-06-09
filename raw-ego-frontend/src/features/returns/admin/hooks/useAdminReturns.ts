import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminGetReturns, adminGetReturn, adminReviewReturn } from '@/api/return.api';
import type { ReturnStatus, AdminReviewReturnPayload } from '@/types/return.types';
import { toast } from '@/store/uiStore';

export const adminReturnKeys = {
  all: ['adminReturns'] as const,
  lists: () => [...adminReturnKeys.all, 'list'] as const,
  list: (status?: ReturnStatus, page: number = 0) => [...adminReturnKeys.lists(), { status, page }] as const,
  detail: (returnId: number) => [...adminReturnKeys.all, 'detail', returnId] as const,
};

export const useAdminReturns = (status?: ReturnStatus, page: number = 0, size: number = 20) => {
  return useQuery({
    queryKey: adminReturnKeys.list(status, page),
    queryFn: () => adminGetReturns(status, page, size),
    staleTime: 30_000,
  });
};

export const useAdminReturn = (returnId: number) => {
  return useQuery({
    queryKey: adminReturnKeys.detail(returnId),
    queryFn: () => adminGetReturn(returnId),
    enabled: returnId > 0,
    staleTime: 30_000,
  });
};

export const useAdminReviewReturn = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ returnId, payload }: { returnId: number; payload: AdminReviewReturnPayload }) => 
      adminReviewReturn(returnId, payload),
    onSuccess: (updatedReturn) => {
      queryClient.invalidateQueries({ queryKey: adminReturnKeys.lists() });
      queryClient.setQueryData(adminReturnKeys.detail(updatedReturn.id), updatedReturn);
      toast.success(`Return request ${updatedReturn.status.toLowerCase()}`);
    },
    onError: (error: any, variables) => {
      const message: string = error.response?.data?.message || 'Failed to review return request';
      const isGatewayError = message.includes('Payment gateway error') || message.includes('marked APPROVED');

      if (isGatewayError) {
        // The return IS approved in the DB — refetch so the UI reflects the real state.
        queryClient.invalidateQueries({ queryKey: adminReturnKeys.detail(variables.returnId) });
        queryClient.invalidateQueries({ queryKey: adminReturnKeys.lists() });
        toast.error('Return approved in system, but Razorpay refund failed. Please retry the refund manually.');
      } else {
        toast.error(message);
      }
    }
  });
};
