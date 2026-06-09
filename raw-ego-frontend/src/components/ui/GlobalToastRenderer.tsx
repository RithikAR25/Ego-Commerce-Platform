import { Toaster } from 'sonner';
import { useTheme } from '@mui/material';

const GlobalToastRenderer = () => {
  const theme = useTheme();

  return (
    <Toaster 
      position="bottom-center"
      toastOptions={{
        style: {
          background: theme.palette.background.default,
          color: theme.palette.text.primary,
          border: `1px solid ${theme.palette.border.default}`,
          borderRadius: '0px', // Matches EGO's square corner brutalist theme
          fontFamily: theme.typography.fontFamilyUtility,
          fontWeight: 500,
        },
      }}
    />
  );
};

export default GlobalToastRenderer;
