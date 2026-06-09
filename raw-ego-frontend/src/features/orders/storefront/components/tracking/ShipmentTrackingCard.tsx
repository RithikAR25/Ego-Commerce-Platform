import React from 'react';
import { Box, Typography, Paper, Button } from '@mui/material';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import type { OrderDetail } from '@/types/order.types';

interface ShipmentTrackingCardProps {
  order: OrderDetail;
}

const ShipmentTrackingCard: React.FC<ShipmentTrackingCardProps> = ({ order }) => {
  // Only render if we have tracking info
  if (!order.trackingNumber && !order.courierName && !order.trackingUrl) {
    return null;
  }

  return (
    <Paper 
      elevation={0} 
      sx={{ 
        border: '1px solid', 
        borderColor: 'divider', 
        p: 3, 
        mb: 3,
        bgcolor: 'surface.secondary',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 3, gap: 1 }}>
        <LocalShippingOutlinedIcon sx={{ color: 'text.secondary' }} />
        <Typography variant="metadata" >
          Shipment Tracking
        </Typography>
      </Box>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {order.courierName && (
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
              Courier
            </Typography>
            <Typography variant="metadata" >
              {order.courierName}
            </Typography>
          </Box>
        )}

        {order.trackingNumber && (
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
              Tracking Number
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: (theme) => theme.typography.fontFamilyUtility, letterSpacing: '0.02em' }}>
              {order.trackingNumber}
            </Typography>
          </Box>
        )}

        {order.trackingUrl && (
          <Button
            variant="contained"
            color="primary"
            endIcon={<OpenInNewIcon fontSize="small" />}
            href={order.trackingUrl}
            target="_blank"
            rel="noopener noreferrer"
            sx={{ 
              mt: 1, 
              borderRadius: 0, 
              textTransform: 'none', 
              bgcolor: 'text.primary',
              '&:hover': { bgcolor: 'surface.tertiary' }, 
            }}
            fullWidth
          >
            Track Shipment
          </Button>
        )}
      </Box>
    </Paper>
  );
};

export default ShipmentTrackingCard;
