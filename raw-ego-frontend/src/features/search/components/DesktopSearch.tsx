import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Box, InputBase, IconButton, Paper } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import { useTheme } from '@mui/material/styles';

import { useAutocomplete } from '@/hooks/useSearch';
import { useDebounce } from '@/hooks/useDebounce';
import { useSearchHistoryStore } from '../store/useSearchHistoryStore';
import { POPULAR_SEARCHES } from '../config/popularSearches';
import SearchSuggestionsPanel from './SearchSuggestionsPanel';

const DesktopSearch: React.FC = () => {
  const navigate = useNavigate();
  const theme = useTheme();
  const { addSearch, searches: recentSearches } = useSearchHistoryStore();

  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const debouncedQuery = useDebounce(query, 300);
  const { data: suggestions = [], isLoading } = useAutocomplete(debouncedQuery);

  const isQueryEmpty = query.trim().length === 0;

  // Calculate total navigable items
  const totalItems = isQueryEmpty
    ? recentSearches.length + POPULAR_SEARCHES.length
    : suggestions.length;

  useEffect(() => {
    // Reset active index when query changes
    setActiveIndex(-1);
  }, [query]);

  // Click outside to close
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
        setQuery('');
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleOpen = () => {
    setIsOpen(true);
    setTimeout(() => inputRef.current?.focus(), 50);
  };

  const handleClose = () => {
    setIsOpen(false);
    setQuery('');
    setActiveIndex(-1);
  };

  const executeSearch = (term: string) => {
    const trimmed = term.trim();
    if (!trimmed) return;
    addSearch(trimmed);
    handleClose();
    navigate(`/products?query=${encodeURIComponent(trimmed)}`);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      handleClose();
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((prev) => (prev < totalItems - 1 ? prev + 1 : prev));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((prev) => (prev > -1 ? prev - 1 : -1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (activeIndex >= 0) {
        // Select from list
        if (isQueryEmpty) {
          const selectedTerm = activeIndex < recentSearches.length
            ? recentSearches[activeIndex]
            : POPULAR_SEARCHES[activeIndex - recentSearches.length];
          executeSearch(selectedTerm);
        } else {
          executeSearch(suggestions[activeIndex]);
        }
      } else {
        // Search typed query
        executeSearch(query);
      }
    }
  };

  return (
    <Box ref={containerRef} sx={{ position: 'relative', display: { xs: 'none', md: 'flex' }, alignItems: 'center' }}>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          overflow: 'hidden',
          width: isOpen ? 280 : 0,
          transition: 'width 0.3s cubic-bezier(0.4,0,0.2,1)',
          borderBottom: isOpen ? `1px solid ${theme.palette.divider}` : 'none',
        }}
      >
        <InputBase
          inputRef={inputRef}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Search for items, brands, or inspiration..."
          sx={{ fontSize: '0.85rem', flex: 1, px: 1, py: 0.5 }}
          inputProps={{ 'aria-label': 'Search products', role: 'searchbox' }}
        />
        {isOpen && (
          <IconButton size="small" onClick={handleClose} sx={{ p: 0.5 }} aria-label="Clear search">
            <CloseIcon sx={{ fontSize: 16 }} />
          </IconButton>
        )}
      </Box>

      <IconButton
        aria-label="Search"
        onClick={isOpen && query.trim() ? () => executeSearch(query) : handleOpen}
        sx={{ color: 'text.primary' }}
      >
        <SearchIcon />
      </IconButton>

      {/* Dropdown Panel */}
      {isOpen && (
        <Paper
          elevation={4}
          sx={{
            position: 'absolute',
            top: '100%',
            right: 0,
            width: 320,
            zIndex: 1400,
            mt: 0.5,
            borderRadius: 2,
            overflow: 'hidden',
          }}
        >
          <SearchSuggestionsPanel
            query={query}
            suggestions={suggestions}
            activeIndex={activeIndex}
            onSelect={executeSearch}
            isLoading={isLoading}
          />
        </Paper>
      )}
    </Box>
  );
};

export default DesktopSearch;
