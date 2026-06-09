import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Dialog, Box, InputBase, IconButton, Divider, Slide } from '@mui/material';
import type { TransitionProps } from '@mui/material/transitions';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CloseIcon from '@mui/icons-material/Close';

import { useAutocomplete } from '@/hooks/useSearch';
import { useDebounce } from '@/hooks/useDebounce';
import { useSearchHistoryStore } from '../store/useSearchHistoryStore';
import { POPULAR_SEARCHES } from '../config/popularSearches';
import SearchSuggestionsPanel from './SearchSuggestionsPanel';

const Transition = React.forwardRef(function Transition(
  props: TransitionProps & { children: React.ReactElement },
  ref: React.Ref<unknown>,
) {
  return <Slide direction="up" ref={ref} {...props} />;
});

interface MobileSearchSheetProps {
  open: boolean;
  onClose: () => void;
}

const MobileSearchSheet: React.FC<MobileSearchSheetProps> = ({ open, onClose }) => {
  const navigate = useNavigate();
  const { addSearch, searches: recentSearches } = useSearchHistoryStore();

  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);

  const debouncedQuery = useDebounce(query, 300);
  const { data: suggestions = [], isLoading } = useAutocomplete(debouncedQuery);

  const isQueryEmpty = query.trim().length === 0;

  const totalItems = isQueryEmpty
    ? recentSearches.length + POPULAR_SEARCHES.length
    : suggestions.length;

  useEffect(() => {
    setActiveIndex(-1);
  }, [query]);

  const handleClose = () => {
    setQuery('');
    setActiveIndex(-1);
    onClose();
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
        if (isQueryEmpty) {
          const selectedTerm = activeIndex < recentSearches.length
            ? recentSearches[activeIndex]
            : POPULAR_SEARCHES[activeIndex - recentSearches.length];
          executeSearch(selectedTerm);
        } else {
          executeSearch(suggestions[activeIndex]);
        }
      } else {
        executeSearch(query);
      }
    }
  };

  return (
    <Dialog
      fullScreen
      open={open}
      onClose={handleClose}
      slots={{ transition: Transition }}
      slotProps={{
        transition: { onEntered: () => inputRef.current?.focus() } as any,
        paper: { sx: { bgcolor: 'background.default' } }
      }}
    >
      {/* Sticky Search Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', p: 1, bgcolor: 'background.paper', position: 'sticky', top: 0, zIndex: 1 }}>
        <IconButton onClick={handleClose} aria-label="Back">
          <ArrowBackIcon />
        </IconButton>
        
        <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', ml: 1, bgcolor: 'grey.100', borderRadius: 2, px: 1.5, py: 0.5 }}>
          <InputBase
            inputRef={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search products..."
            sx={{ ml: 1, flex: 1, fontSize: '1rem' }}
            inputProps={{ 'aria-label': 'Search products', enterKeyHint: 'search' }}
          />
          {query.length > 0 && (
            <IconButton size="small" onClick={() => { setQuery(''); inputRef.current?.focus(); }} aria-label="Clear search">
              <CloseIcon fontSize="small" />
            </IconButton>
          )}
        </Box>
      </Box>

      <Divider />

      {/* Search Panel */}
      <Box sx={{ flex: 1, overflowY: 'auto', bgcolor: 'background.paper' }}>
        <SearchSuggestionsPanel
          query={query}
          suggestions={suggestions}
          activeIndex={activeIndex}
          onSelect={executeSearch}
          isLoading={isLoading}
        />
      </Box>
    </Dialog>
  );
};

export default MobileSearchSheet;
