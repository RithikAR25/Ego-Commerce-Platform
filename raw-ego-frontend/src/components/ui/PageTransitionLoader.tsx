import { useEffect } from 'react';
import { useNavigation } from 'react-router-dom';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import { useTheme } from '@mui/material';

// Configure NProgress globally
NProgress.configure({ showSpinner: false, speed: 400, minimum: 0.1 });

const PageTransitionLoader = () => {
  const navigation = useNavigation();
  const theme = useTheme();

  useEffect(() => {
    // Inject custom color for nprogress based on theme
    const style = document.createElement('style');
    style.innerHTML = `
      #nprogress .bar {
        background: ${theme.palette.primary.main} !important;
        height: 3px !important;
      }
      #nprogress .peg {
        box-shadow: 0 0 10px ${theme.palette.primary.main}, 0 0 5px ${theme.palette.primary.main} !important;
      }
    `;
    document.head.appendChild(style);
    return () => {
      document.head.removeChild(style);
    };
  }, [theme]);

  useEffect(() => {
    if (navigation.state === 'loading') {
      NProgress.start();
    } else {
      NProgress.done();
    }
    
    // Cleanup if unmounted while loading
    return () => {
      NProgress.done();
    };
  }, [navigation.state]);

  return null;
};

export default PageTransitionLoader;
