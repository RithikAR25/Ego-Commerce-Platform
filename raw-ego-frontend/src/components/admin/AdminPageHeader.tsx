import { Box, Typography } from '@mui/material';
import React from 'react';

interface AdminPageHeaderProps {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}

const AdminPageHeader: React.FC<AdminPageHeaderProps> = ({ title, subtitle, action }) => {
  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-end',
        pb: 4,
        mb: 5,
        borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
      }}
    >
      <Box>
        <Typography
          variant="overline"
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyUtility,
            fontWeight: 700,
            fontSize: '0.6rem',
            letterSpacing: '0.25em',
            color: 'text.secondary',
            display: 'block',
            mb: 1,
          }}
        >
          ADMIN CONSOLE
        </Typography>
        <Typography
          sx={{
            fontFamily: (theme) => theme.typography.fontFamilyDisplay,
            fontWeight: 700,
            fontSize: { xs: '1.75rem', md: '2.25rem' },
            color: 'text.primary',
            lineHeight: 1.1,
            textTransform: 'uppercase',
          }}
        >
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body" sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', mt: 1, fontStyle: 'italic', }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {action && <Box>{action}</Box>}
    </Box>
  );
};

export default AdminPageHeader;
