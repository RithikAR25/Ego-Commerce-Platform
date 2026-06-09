/**
 * AttributeManagerPanel.tsx
 *
 * Admin UI component for managing product attribute types and values.
 *
 * This is STEP 2 & 3 of the product lifecycle:
 *   STEP 1: Create Product
 *   STEP 2: Add Attribute Types ("Color", "Size")  ← this component handles this
 *   STEP 3: Add Attribute Values ("Black", "M")    ← this component handles this
 *   STEP 4: Create Variants (Color + Size combos)
 *
 * Attribute types and values must exist before any variant can be created.
 */

import React, { useState } from 'react';
import {
  Box, Typography, Chip, Button, Divider, TextField,
  Dialog, DialogTitle, DialogContent, DialogActions,
  CircularProgress, Alert, Stack, Tooltip, alpha
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import PaletteIcon from '@mui/icons-material/Palette';
import StraightenIcon from '@mui/icons-material/Straighten';
import { useCreateAttributeType, useCreateAttributeValue } from '@/hooks/useAdminCatalog';
import type { AttributeTypeResponse, AttributeValueSummary } from '@/types/catalog.types';

interface Props {
  productId: number;
  productSlug: string;
  /** attributeTypes comes from the product detail response — no extra fetch needed */
  attributeTypes: AttributeTypeResponse[];
}

// ── Add Attribute Type Dialog ─────────────────────────────────────────────────

interface AddTypeDialogProps {
  open: boolean;
  onClose: () => void;
  productId: number;
  productSlug: string;
}

const AddAttributeTypeDialog: React.FC<AddTypeDialogProps> = ({ open, onClose, productId, productSlug }) => {
  const [name, setName] = useState('');
  const { mutate, isPending, error } = useCreateAttributeType(productId, productSlug);

  const handleSubmit = () => {
    if (!name.trim()) return;
    mutate({ name: name.trim() }, {
      onSuccess: () => { setName(''); onClose(); }
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Add Attribute Type</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          An attribute type defines a dimension of variation (e.g. <strong>Color</strong> or <strong>Size</strong>).
          You need both before you can create variants.
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2, fontSize: '0.82rem' }}>
            {(error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to create attribute type.'}
          </Alert>
        )}
        <TextField
          label="Attribute Type Name"
          placeholder="e.g. Color, Size, Fit"
          value={name}
          onChange={(e) => setName(e.target.value)}
          fullWidth
          autoFocus
          variant="outlined"
          slotProps={{ htmlInput: { maxLength: 50 } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={isPending}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={isPending || !name.trim()}>
          {isPending ? <CircularProgress size={18} /> : 'Create'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Add Attribute Value Dialog ────────────────────────────────────────────────

interface AddValueDialogProps {
  open: boolean;
  onClose: () => void;
  attributeType: AttributeTypeResponse;
  productId: number;
  productSlug: string;
}

const AddAttributeValueDialog: React.FC<AddValueDialogProps> = ({
  open, onClose, attributeType, productId, productSlug
}) => {
  const [value, setValue] = useState('');
  const [code, setCode] = useState('');
  const [hexColor, setHexColor] = useState('');
  const { mutate, isPending, error } = useCreateAttributeValue(productId, productSlug);

  const isColorType = attributeType.name.toLowerCase() === 'color';

  const handleSubmit = () => {
    if (!value.trim() || !code.trim()) return;
    const payload = {
      value: value.trim(),
      code: code.trim().toUpperCase(),
      hexColor: hexColor.trim() || undefined,
    };
    mutate({ attributeTypeId: attributeType.id, payload }, {
      onSuccess: () => { setValue(''); setCode(''); setHexColor(''); onClose(); }
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Add Value to "{attributeType.name}"</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {isColorType
            ? 'Add a color option. The code (e.g. BLK) becomes part of the permanent SKU.'
            : 'Add a size option. The code (e.g. M, XL) becomes part of the permanent SKU.'}
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2, fontSize: '0.82rem' }}>
            {(error as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Failed to add value.'}
          </Alert>
        )}
        <Stack spacing={2}>
          <TextField
            label="Display Label"
            placeholder={isColorType ? 'e.g. Jet Black' : 'e.g. Medium'}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            fullWidth
            autoFocus
            variant="outlined"
            slotProps={{ htmlInput: { maxLength: 100 } }}
          />
          <TextField
            label="Code (used in SKU)"
            placeholder={isColorType ? 'e.g. BLK' : 'e.g. M'}
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            fullWidth
            variant="outlined"
            slotProps={{ htmlInput: { maxLength: 10 } }}
            helperText="Uppercase letters and digits only. Cannot be changed after variants are created."
          />
          {isColorType && (
            <TextField
              label="Hex Color (for swatch)"
              placeholder="e.g. Hex"
              value={hexColor}
              onChange={(e) => setHexColor(e.target.value)}
              fullWidth
              variant="outlined"
              slotProps={{
                htmlInput: { maxLength: 7 },
                input: {
                  startAdornment: hexColor ? (
                    <Box sx={{ width: 20, height: 20, borderRadius: '50%', bgcolor: hexColor, mr: 1, flexShrink: 0 }} />
                  ) : undefined,
                }
              }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={isPending}>Cancel</Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={isPending || !value.trim() || !code.trim()}
        >
          {isPending ? <CircularProgress size={18} /> : 'Add Value'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── Main Panel ────────────────────────────────────────────────────────────────

const AttributeManagerPanel: React.FC<Props> = ({ productId, productSlug, attributeTypes }) => {
  const [addTypeOpen, setAddTypeOpen] = useState(false);
  const [addValueTarget, setAddValueTarget] = useState<AttributeTypeResponse | null>(null);

  const hasColor = attributeTypes.some(t => t.name.toLowerCase() === 'color');
  const hasSize = attributeTypes.some(t => t.name.toLowerCase() === 'size');
  const canCreateVariants = hasColor && hasSize &&
    attributeTypes.find(t => t.name.toLowerCase() === 'color')!.values.length > 0 &&
    attributeTypes.find(t => t.name.toLowerCase() === 'size')!.values.length > 0;

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Box>
          <Typography variant="metadata" >
            Attribute Matrix
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Define Color and Size options before creating variants
          </Typography>
        </Box>
        <Button variant="outlined" size="small" startIcon={<AddIcon />} onClick={() => setAddTypeOpen(true)}>
          Add Attribute Type
        </Button>
      </Box>

      {/* Readiness Alert */}
      {!canCreateVariants && (
        <Alert severity="warning" sx={{ mb: 2, fontSize: '0.82rem' }}>
          {attributeTypes.length === 0
            ? 'No attribute types defined yet. Add "Color" and "Size" attribute types, then add their values before creating variants.'
            : !hasColor
            ? 'Missing "Color" attribute type. Add it and define color values to enable variant creation.'
            : !hasSize
            ? 'Missing "Size" attribute type. Add it and define size values to enable variant creation.'
            : 'Add at least one value to each attribute type before creating variants.'}
        </Alert>
      )}
      {canCreateVariants && (
        <Alert severity="success" sx={{ mb: 2, fontSize: '0.82rem' }}>
          Attribute matrix is ready. You can now create Color × Size variant combinations.
        </Alert>
      )}

      {/* Attribute Types */}
      {attributeTypes.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, border: '1px dashed', borderColor: 'divider', borderRadius: 2 }}>
          <Typography color="text.secondary" variant="body2">
            No attribute types defined yet. Start by adding "Color" and "Size".
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {attributeTypes.map((type) => (
            <Box key={type.id} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  {type.name.toLowerCase() === 'color' ? (
                    <PaletteIcon fontSize="small" color="action" />
                  ) : (
                    <StraightenIcon fontSize="small" color="action" />
                  )}
                  <Typography variant="metadata" >{type.name}</Typography>
                  <Chip label={`${type.values.length} value${type.values.length !== 1 ? 's' : ''}`} size="small" variant="outlined" />
                </Box>
                <Button size="small" startIcon={<AddIcon />} onClick={() => setAddValueTarget(type)}>
                  Add Value
                </Button>
              </Box>
              <Divider sx={{ mb: 1.5 }} />
              {type.values.length === 0 ? (
                <Typography variant="caption" color="text.secondary">
                  No values yet. Add at least one to enable variant creation.
                </Typography>
              ) : (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                  {type.values.map((val: AttributeValueSummary) => (
                    <Tooltip key={val.id} title={`Code: ${val.code}`} placement="top">
                      <Chip
                        label={val.value}
                        size="small"
                        avatar={val.hexColor ? (
                          <Box
                            component="span"
                            sx={{
                              width: 14, height: 14, borderRadius: '50%',
                              bgcolor: val.hexColor,
                              border: (theme) => `1px solid ${alpha(theme.palette.text.primary, 0.15)}`,
                              display: 'inline-block',
                            }}
                          />
                        ) : undefined}
                        sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility }}
                      />
                    </Tooltip>
                  ))}
                </Box>
              )}
            </Box>
          ))}
        </Stack>
      )}

      {/* Dialogs */}
      <AddAttributeTypeDialog
        open={addTypeOpen}
        onClose={() => setAddTypeOpen(false)}
        productId={productId}
        productSlug={productSlug}
      />
      {addValueTarget && (
        <AddAttributeValueDialog
          open={Boolean(addValueTarget)}
          onClose={() => setAddValueTarget(null)}
          attributeType={addValueTarget}
          productId={productId}
          productSlug={productSlug}
        />
      )}
    </Box>
  );
};

export default AttributeManagerPanel;
