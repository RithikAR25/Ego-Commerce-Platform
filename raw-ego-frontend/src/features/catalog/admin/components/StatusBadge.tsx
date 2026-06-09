/**
 * StatusBadge.tsx
 */

import React from 'react';
import { Chip, alpha, useTheme } from '@mui/material';
import type { ProductStatus } from '@/types/catalog.types';


interface Props {
  status: ProductStatus;
}

const StatusBadge: React.FC<Props> = ({ status }) => {
  const theme = useTheme();
  const color = theme.palette.statusColors?.[status] ?? theme.palette.text.secondary;
  
  // Make the label pretty (e.g. "OUT_OF_STOCK" -> "Out Of Stock")
  const prettyLabel = status
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');

  return (
    <Chip
      label={prettyLabel}
      size="small"
      sx={{
        bgcolor:    (theme) => alpha(theme.palette.statusColors?.[status] || theme.palette.text.secondary, 0.1),
        color:      color,
        fontWeight: 600,
        fontSize:   '0.7rem',
        border:     (theme) => `1px solid ${alpha(theme.palette.statusColors?.[status] || theme.palette.text.secondary, 0.25)}`,
        borderRadius: '6px'
      }}
    />
  );
};

export default StatusBadge;
