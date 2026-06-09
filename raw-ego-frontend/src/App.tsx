/**
 * App.tsx
 *
 * Root application component.
 * Renders the router via RouterProvider.
 *
 * All providers (Theme, QueryClient) are in AppProviders.tsx.
 * AppProviders wraps the RouterProvider in main.tsx.
 */

import { RouterProvider } from 'react-router-dom';
import router from '@/router/index';

const App = () => {
  return <RouterProvider router={router} />;
};

export default App;
