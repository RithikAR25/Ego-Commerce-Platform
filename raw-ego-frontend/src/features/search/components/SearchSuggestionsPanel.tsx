import React from 'react';
import { Box, Typography, List, ListItem, ListItemButton, IconButton, Divider } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import HistoryIcon from '@mui/icons-material/History';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import CloseIcon from '@mui/icons-material/Close';

import { useSearchHistoryStore } from '../store/useSearchHistoryStore';
import { POPULAR_SEARCHES } from '../config/popularSearches';
import SearchSuggestionsSkeleton from './skeletons/SearchSuggestionsSkeleton';

interface SearchSuggestionsPanelProps {
  query: string;
  suggestions: string[];
  activeIndex: number;
  onSelect: (term: string) => void;
  isLoading?: boolean;
}

const SearchSuggestionsPanel: React.FC<SearchSuggestionsPanelProps> = ({
  query,
  suggestions,
  activeIndex,
  onSelect,
  isLoading
}) => {
  const { searches: recentSearches, removeSearch, clearHistory } = useSearchHistoryStore();

  const isQueryEmpty = query.trim().length === 0;

  // ── STATE A: Empty Query (Recent + Popular) ─────────────────────────────────
  if (isQueryEmpty) {
    // Total items for activeIndex calculation in STATE A
    // Layout: [Recent 0..N], [Popular 0..M]
    let currentIdx = 0;

    return (
      <Box sx={{ py: 1 }}>
        {recentSearches.length > 0 && (
          <Box sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 2, mb: 1 }}>
              <Typography variant="metadata" color="text.secondary">
                Recent Searches
              </Typography>
              <Typography
                variant="caption"
                sx={{ cursor: 'pointer', color: 'text.secondary', '&:hover': { color: 'text.primary', textDecoration: 'underline' } }}
                onClick={(e) => { e.stopPropagation(); clearHistory(); }}
                role="button"
                tabIndex={0}
                aria-label="Clear all recent searches"
              >
                Clear All
              </Typography>
            </Box>
            <List disablePadding>
              {recentSearches.map((term) => {
                const isSelected = activeIndex === currentIdx++;
                return (
                  <ListItem
                    key={`recent-${term}`}
                    disablePadding
                    secondaryAction={
                      <IconButton
                        edge="end"
                        size="small"
                        onClick={(e) => { e.stopPropagation(); removeSearch(term); }}
                        aria-label={`Remove ${term} from history`}
                      >
                        <CloseIcon fontSize="small" />
                      </IconButton>
                    }
                  >
                    <ListItemButton
                      selected={isSelected}
                      onClick={() => onSelect(term)}
                      sx={{ py: 1.25, px: 2 }}
                    >
                      <HistoryIcon sx={{ mr: 2, color: 'text.secondary', fontSize: 20 }} />
                      <Typography variant="body2">{term}</Typography>
                    </ListItemButton>
                  </ListItem>
                );
              })}
            </List>
          </Box>
        )}

        <Box>
          <Typography variant="metadata" color="text.secondary" sx={{ px: 2, display: 'block', mb: 1 }}>
            Popular Searches
          </Typography>
          <List disablePadding>
            {POPULAR_SEARCHES.map((term) => {
              const isSelected = activeIndex === currentIdx++;
              return (
                <ListItem key={`popular-${term}`} disablePadding>
                  <ListItemButton
                    selected={isSelected}
                    onClick={() => onSelect(term)}
                    sx={{ py: 1.25, px: 2 }}
                  >
                    <TrendingUpIcon sx={{ mr: 2, color: 'text.secondary', fontSize: 20 }} />
                    <Typography variant="body2">{term}</Typography>
                  </ListItemButton>
                </ListItem>
              );
            })}
          </List>
        </Box>
      </Box>
    );
  }

  // ── STATE B/C: Typing (Autocomplete or No Results) ──────────────────────────

  if (isLoading && !isQueryEmpty) {
    return <SearchSuggestionsSkeleton />;
  }

  if (!isLoading && query.length >= 2 && suggestions.length === 0) {
    // STATE C: No results
    return (
      <Box sx={{ p: 4, textAlign: 'center' }}>
        <SearchIcon sx={{ fontSize: 32, color: 'text.disabled', mb: 1 }} />
        <Typography variant="body2" color="text.secondary">
          No matching suggestions
        </Typography>
      </Box>
    );
  }

  // STATE B: Suggestions
  return (
    <List disablePadding sx={{ py: 1 }}>
      {suggestions.map((term, idx) => (
        <React.Fragment key={`suggest-${term}`}>
          <ListItem disablePadding>
            <ListItemButton
              selected={activeIndex === idx}
              onClick={() => onSelect(term)}
              sx={{ py: 1.25, px: 2 }}
            >
              <SearchIcon sx={{ mr: 2, color: 'text.disabled', fontSize: 20 }} />
              <Typography variant="body2">{term}</Typography>
            </ListItemButton>
          </ListItem>
          {idx < suggestions.length - 1 && <Divider sx={{ mx: 2 }} />}
        </React.Fragment>
      ))}
    </List>
  );
};

export default SearchSuggestionsPanel;
