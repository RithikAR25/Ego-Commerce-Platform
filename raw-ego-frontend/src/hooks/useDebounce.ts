/**
 * useDebounce.ts
 *
 * Returns a debounced copy of `value` that only updates after
 * `delayMs` milliseconds of inactivity.
 *
 * Usage:
 *   const debouncedSearch = useDebounce(searchInput, 350);
 *   // pass debouncedSearch to your query — the server is only
 *   // hit once the user stops typing for 350ms.
 */

import { useState, useEffect } from 'react';

export function useDebounce<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
