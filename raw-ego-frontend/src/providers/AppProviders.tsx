import { ThemeProvider, CssBaseline } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useMemo } from 'react';
import getEgoTheme from '@/theme/theme';
import { useThemeStore } from '@/store/themeStore';
import { extractApiError } from '@/utils/apiError';
import GlobalToastRenderer from '@/components/ui/GlobalToastRenderer';

// ── QueryClient singleton ─────────────────────────────────────────────────────

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime:            2 * 60_000,    // 2 min
      gcTime:               10 * 60_000,   // 10 min
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Don't retry on 4xx errors — they won't succeed on retry
        const { statusCode } = extractApiError(error);
        if (statusCode && statusCode >= 400 && statusCode < 500) return false;
        return failureCount < 2;
      },
    },
    mutations: {
      // Global mutation error handler — individual mutations can override
      onError: (error) => {
        const { message } = extractApiError(error);
        // Only show generic toast if error hasn't been handled at mutation level
        // Individual mutations should handle field errors themselves
        console.error('[Mutation Error]', message);
      },
    },
  },
});

// ── AppProviders ──────────────────────────────────────────────────────────────

interface AppProvidersProps {
  children: React.ReactNode;
}

export const AppProviders = ({ children }: AppProvidersProps) => {
  const mode = useThemeStore((state) => state.mode);
  const theme = useMemo(() => getEgoTheme(mode), [mode]);

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
        {/* Global toast notifications — reads from uiStore, always mounted */}
        <GlobalToastRenderer />
      </ThemeProvider>
    </QueryClientProvider>
  );
};
