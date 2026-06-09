import { Card, CardContent, Typography, Box, Skeleton } from '@mui/material';
import React from 'react';

interface DataCardProps {
  title: string;
  value: string | number;
  icon?: React.ReactNode;
  trend?: {
    value: string | number;
    isPositive: boolean;
    label: string;
  };
  isLoading?: boolean;
  onClick?: () => void;
}

const DataCard: React.FC<DataCardProps> = ({ title, value, icon, trend, isLoading, onClick }) => {
  return (
    <Card
      onClick={onClick}
      elevation={0}
      sx={{
        bgcolor: 'surface.secondary',
        border: (theme) => `1px solid ${theme.palette.border.default}`,
        borderRadius: 0,
        cursor: onClick ? 'pointer' : 'default',
        transition: 'border-color 0.2s',
        '&:hover': onClick ? { borderColor: 'border.strong' } : {},
      }}
    >
      <CardContent sx={{ p: 3, '&:last-child': { pb: 3 } }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
          <Typography
            sx={{
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontWeight: 700,
              fontSize: '0.6rem',
              letterSpacing: '0.2em',
              color: 'text.secondary',
              textTransform: 'uppercase',
            }}
          >
            {title}
          </Typography>
          {icon && (
            <Box sx={{ color: 'text.secondary', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {icon}
            </Box>
          )}
        </Box>

        {isLoading ? (
          <Skeleton variant="text" width="60%" height={48} sx={{ bgcolor: 'surface.tertiary' }} />
        ) : (
          <Typography variant="pageTitle" sx={{ fontFamily: (theme) => theme.typography.fontFamilyDisplay, color: 'text.primary', mb: 1, }}>
            {value}
          </Typography>
        )}

        {trend && !isLoading && (
          <Box sx={{ display: 'flex', alignItems: 'center', mt: 2, pt: 2, borderTop: (theme) => `1px solid ${theme.palette.border.default}` }}>
            <Typography
              sx={{
                fontFamily: (theme) => theme.typography.fontFamilyUtility,
                fontWeight: 700,
                fontSize: '0.75rem',
                color: trend.isPositive ? 'state.success' : 'state.error',
                display: 'flex',
                alignItems: 'center',
                mr: 1,
              }}
            >
              {trend.isPositive ? '↑' : '↓'} {trend.value}
            </Typography>
            <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.72rem', color: 'text.secondary' }}>
              {trend.label}
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  );
};

export default DataCard;
