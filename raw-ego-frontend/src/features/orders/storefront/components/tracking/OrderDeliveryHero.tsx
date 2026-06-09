import React from 'react';
import { Box, Typography } from '@mui/material';
import { getDeliveryMessage } from './OrderTrackingUtils';
import type { OrderDetail } from '@/types/order.types';

interface OrderDeliveryHeroProps {
  order: OrderDetail;
}

const OrderDeliveryHero: React.FC<OrderDeliveryHeroProps> = ({ order }) => {
  // Find deliveredAt from history if status is DELIVERED
  let deliveredAt: string | undefined;
  if (order.status === 'DELIVERED') {
    const deliveredEntry = order.statusHistory.find(h => h.status === 'DELIVERED');
    deliveredAt = deliveredEntry?.createdAt;
  }

  const message = getDeliveryMessage(order.status, deliveredAt);

  return (
    <Box sx={{ mb: { xs: 4, md: 6 }, textAlign: { xs: 'left', md: 'center' } }}>
      <Typography variant="metadata" sx={{ mb: 1, color: 'text.primary' }}>
        {message}
      </Typography>

      {/* Show estimated delivery if available and not yet delivered/cancelled */}
      {order.estimatedDeliveryAt && !['DELIVERED', 'CANCELLED', 'REFUNDED'].includes(order.status) && (
        <Typography variant="metadata" sx={{ color: 'text.secondary', }}>
          Estimated delivery:{' '}
          <Box component="span" sx={{ color: 'text.primary', fontWeight: 700 }}>
            {new Date(order.estimatedDeliveryAt).toLocaleDateString(undefined, {
              weekday: 'long', month: 'long', day: 'numeric'
            })}
          </Box>
        </Typography>
      )}
    </Box>
  );
};

export default OrderDeliveryHero;
