import { Chip, useTheme, alpha } from '@mui/material';
import type { ChipProps } from '@mui/material';
import React from 'react';


// Maps to both OrderStatus and ReturnStatus strings
export type StatusString = 
  | 'PENDING_PAYMENT' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REFUNDED'
  | 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'REFUND_INITIATED' | 'REFUND_COMPLETED'
  | string;

interface StatusBadgeProps extends Omit<ChipProps, 'color'> {
  status: StatusString;
}

const StatusBadge: React.FC<StatusBadgeProps> = ({ status, ...props }) => {
  const theme = useTheme();
  const color = theme.palette.statusColors?.[status] ?? theme.palette.text.secondary;

  // Make the label pretty (e.g. "PENDING_PAYMENT" -> "Pending Payment")
  const prettyLabel = status
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');

  return (
    <Chip 
      label={prettyLabel} 
      size="small" 
      sx={{ 
        fontWeight: 600, 
        letterSpacing: '0.02em',
        borderRadius: '6px',
        bgcolor: alpha(color, 0.09), // 0.09 is approx 18 in hex
        color: color,
        border: `1px solid ${alpha(color, 0.25)}`, // 0.25 is 40 in hex
      }} 
      {...props} 
    />
  );
};

export default StatusBadge;
