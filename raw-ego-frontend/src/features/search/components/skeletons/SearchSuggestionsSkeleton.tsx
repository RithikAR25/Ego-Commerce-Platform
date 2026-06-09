import React from 'react';
import { List, ListItem, Skeleton, Divider } from '@mui/material';

const SearchSuggestionsSkeleton: React.FC = () => {
  return (
    <List disablePadding sx={{ py: 1 }}>
      {Array.from({ length: 4 }).map((_, idx) => (
        <React.Fragment key={`suggest-skeleton-${idx}`}>
          <ListItem disablePadding sx={{ py: 1.25, px: 2, display: 'flex', alignItems: 'center' }}>
            <Skeleton variant="circular" animation="wave" width={20} height={20} sx={{ mr: 2 }} />
            <Skeleton variant="text" animation="wave" width={`${40 + Math.random() * 40}%`} height={20} />
          </ListItem>
          {idx < 3 && <Divider sx={{ mx: 2 }} />}
        </React.Fragment>
      ))}
    </List>
  );
};

export default SearchSuggestionsSkeleton;
