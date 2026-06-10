import React, { useState, useEffect } from 'react';
import {
  Popover,
  Box,
  Typography,
  List,
  ListItemButton,
  ListItemText,
  Divider,
} from '@mui/material';
import { ChevronRight, ArrowBack } from '@mui/icons-material';
import type { CategoryResponse } from '@/types/catalog.types';

interface Props {
  anchorEl: HTMLElement | null;
  onClose: () => void;
  categories: CategoryResponse[];
  onSelect: (categoryId: number) => void;
}

type ViewState = 'ROOT' | 'GROUP' | 'LEAF';

const CategoryDrilldownMenu: React.FC<Props> = ({ anchorEl, onClose, categories, onSelect }) => {
  const [view, setView] = useState<ViewState>('ROOT');
  const [selectedRootId, setSelectedRootId] = useState<number | null>(null);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);

  const open = Boolean(anchorEl);

  // Reset state when menu opens
  useEffect(() => {
    if (open) {
      setView('ROOT');
      setSelectedRootId(null);
      setSelectedGroupId(null);
    }
  }, [open]);

  const roots = categories.filter((c) => c.active && c.level === 'ROOT').sort((a, b) => a.displayOrder - b.displayOrder);
  const groups = categories.filter((c) => c.active && c.level === 'GROUP' && c.parent?.id === selectedRootId).sort((a, b) => a.displayOrder - b.displayOrder);
  const leafs = categories.filter((c) => c.active && c.level === 'LEAF' && c.parent?.id === selectedGroupId).sort((a, b) => a.displayOrder - b.displayOrder);

  const selectedRootName = categories.find((c) => c.id === selectedRootId)?.name;
  const selectedGroupName = categories.find((c) => c.id === selectedGroupId)?.name;

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
      transformOrigin={{
        vertical: 'top',
        horizontal: 'left',
      }}
      slotProps={{
        paper: {
          sx: { 
            width: anchorEl ? anchorEl.clientWidth : 300, 
            maxHeight: 400,
            mt: 1 
          }
        }
      }}
    >
      <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Header for Back Navigation */}
        {view !== 'ROOT' && (
          <ListItemButton 
            onClick={() => setView(view === 'LEAF' ? 'GROUP' : 'ROOT')}
            sx={{ bgcolor: 'action.hover' }}
          >
            <ArrowBack fontSize="small" sx={{ mr: 1, color: 'text.secondary' }} />
            <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
              {view === 'GROUP' ? 'Back to Categories' : `Back to ${selectedRootName}`}
            </Typography>
          </ListItemButton>
        )}
        
        {view !== 'ROOT' && <Divider />}

        {/* Header Title */}
        <Box sx={{ px: 2, py: 1.5, bgcolor: 'background.default', borderBottom: 1, borderColor: 'divider' }}>
          <Typography variant="overline" color="text.secondary">
            {view === 'ROOT' && 'Select Category'}
            {view === 'GROUP' && selectedRootName}
            {view === 'LEAF' && selectedGroupName}
          </Typography>
        </Box>

        {/* Content List */}
        <List sx={{ p: 0 }}>
          {view === 'ROOT' &&
            roots.map((cat) => (
              <ListItemButton
                key={cat.id}
                onClick={() => {
                  setSelectedRootId(cat.id);
                  setView('GROUP');
                }}
                sx={{ py: 1.5 }}
              >
                <ListItemText primary={<Typography variant="body2">{cat.name}</Typography>} />
                <ChevronRight fontSize="small" color="action" />
              </ListItemButton>
            ))}

          {view === 'GROUP' &&
            groups.map((cat) => (
              <ListItemButton
                key={cat.id}
                onClick={() => {
                  setSelectedGroupId(cat.id);
                  setView('LEAF');
                }}
                sx={{ py: 1.5 }}
              >
                <ListItemText primary={<Typography variant="body2">{cat.name}</Typography>} />
                <ChevronRight fontSize="small" color="action" />
              </ListItemButton>
            ))}

          {view === 'LEAF' &&
            leafs.map((cat) => (
              <ListItemButton
                key={cat.id}
                onClick={() => {
                  onSelect(cat.id);
                }}
                sx={{ py: 1.5 }}
              >
                <ListItemText primary={<Typography variant="body2">{cat.name}</Typography>} />
              </ListItemButton>
            ))}
            
          {/* Empty States */}
          {view === 'GROUP' && groups.length === 0 && (
            <Box sx={{ p: 3, textAlign: 'center' }}>
              <Typography variant="body2" color="text.secondary">No groups available.</Typography>
            </Box>
          )}
          {view === 'LEAF' && leafs.length === 0 && (
            <Box sx={{ p: 3, textAlign: 'center' }}>
              <Typography variant="body2" color="text.secondary">No subcategories available.</Typography>
            </Box>
          )}
        </List>
      </Box>
    </Popover>
  );
};

export default CategoryDrilldownMenu;
