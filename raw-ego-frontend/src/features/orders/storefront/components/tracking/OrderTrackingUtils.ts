import type { OrderStatus, OrderStatusHistory } from '@/types/order.types';

export interface TrackingStage {
  id: OrderStatus;
  label: string;
  isCompleted: boolean;
  isCurrent: boolean;
  timestamp?: string; // ISO date string if completed
}

// The core 5 stages of our visual tracking timeline
export const TRACKING_STAGES: OrderStatus[] = [
  'CONFIRMED',        // Placed
  'PROCESSING',       // Packed
  'SHIPPED',          // Shipped
  'OUT_FOR_DELIVERY', // Out For Delivery
  'DELIVERED',        // Delivered
];

export const STAGE_LABELS: Record<OrderStatus, string> = {
  PENDING_PAYMENT: 'Pending Payment',
  CONFIRMED: 'Placed',
  PROCESSING: 'Packed',
  SHIPPED: 'Shipped',
  OUT_FOR_DELIVERY: 'Out for Delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
};

/**
 * Maps the current status and history to an array of TrackingStage objects
 * to power the visual timeline.
 */
export const buildTrackingTimeline = (
  currentStatus: OrderStatus,
  history: OrderStatusHistory[]
): TrackingStage[] => {
  // Find the index of the current status in our linear sequence
  let currentIndex = TRACKING_STAGES.indexOf(currentStatus);

  // If the order is cancelled/refunded/pending, we might not want to show the standard timeline as active.
  // But for the standard flow, currentIndex >= 0
  if (currentIndex === -1) {
     // Handle edge cases like PENDING_PAYMENT or CANCELLED if we want to show an alternate view.
     // For now, if it's PENDING_PAYMENT, we just show Placed as not completed.
     if (currentStatus === 'PENDING_PAYMENT') currentIndex = -1;
     // CANCELLED or REFUNDED orders typically don't show the active timeline, or they freeze it.
  }

  return TRACKING_STAGES.map((stageId, index) => {
    // Find if this stage exists in history to get its timestamp
    const historyEntry = history.find(h => h.status === stageId);
    
    const isCompleted = currentIndex >= index;
    const isCurrent = currentIndex === index;

    return {
      id: stageId,
      label: STAGE_LABELS[stageId],
      isCompleted,
      isCurrent,
      timestamp: historyEntry?.createdAt,
    };
  });
};

/**
 * Returns premium delivery messaging based on the current status.
 */
export const getDeliveryMessage = (
  status: OrderStatus, 
  deliveredAt?: string
): string => {
  switch (status) {
    case 'PENDING_PAYMENT':
      return 'Your order is awaiting payment.';
    case 'CONFIRMED':
      return 'We have received your order.';
    case 'PROCESSING':
      return 'Your order is being prepared.';
    case 'SHIPPED':
      return 'Your package is on the way.';
    case 'OUT_FOR_DELIVERY':
      return 'Your package is out for delivery today.';
    case 'DELIVERED': {
      if (deliveredAt) {
        const dateStr = new Date(deliveredAt).toLocaleDateString(undefined, { 
          month: 'long', day: 'numeric' 
        });
        return `Your order was successfully delivered on ${dateStr}.`;
      }
      return 'Your order was successfully delivered.';
    }
    case 'CANCELLED':
      return 'Your order has been cancelled.';
    case 'REFUNDED':
      return 'Your order has been returned and refunded.';
    default:
      return 'Tracking your order...';
  }
};
