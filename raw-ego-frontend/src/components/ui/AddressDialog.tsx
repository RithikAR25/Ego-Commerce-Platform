import React from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions,
  Button, TextField, Box, FormControl, InputLabel, Select, MenuItem,
  Alert, CircularProgress
} from '@mui/material';
import type { AddressRequest, UserAddress } from '@/api/address.api';

interface AddressDialogProps {
  open: boolean;
  onClose: () => void;
  onSave: () => void;
  editAddr: UserAddress | null;
  addrForm: AddressRequest;
  setAddrForm: (v: AddressRequest) => void;
  addrError: string | null;
  isSaving: boolean;
}

const ADDRESS_TYPES: Array<'HOME' | 'WORK' | 'OTHER'> = ['HOME', 'WORK', 'OTHER'];

const AddressDialog: React.FC<AddressDialogProps> = ({
  open, onClose, onSave, editAddr, addrForm, setAddrForm, addrError, isSaving
}) => {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth slotProps={{ paper: { sx: { borderRadius: 0 } } }}>
      <DialogTitle sx={{ fontWeight: 800, letterSpacing: '0.05em' }}>
        {editAddr ? 'Edit Address' : 'Add New Address'}
      </DialogTitle>
      <DialogContent>
        {addrError && (
          <Alert severity="error" sx={{ borderRadius: 0, mb: 2, fontSize: '0.85rem' }}>{addrError}</Alert>
        )}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField label="Full Name" value={addrForm.fullName} onChange={e => setAddrForm({ ...addrForm, fullName: e.target.value })} fullWidth required size="small" sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }} />
            <TextField label="Phone" value={addrForm.phone} onChange={e => setAddrForm({ ...addrForm, phone: e.target.value })} fullWidth required size="small" helperText="10-digit mobile number" sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }} />
          </Box>
          <TextField label="Address Line 1" value={addrForm.addressLine1} onChange={e => setAddrForm({ ...addrForm, addressLine1: e.target.value })} fullWidth required size="small" sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }} />
          <TextField label="Address Line 2 (optional)" value={addrForm.addressLine2 ?? ''} onChange={e => setAddrForm({ ...addrForm, addressLine2: e.target.value })} fullWidth size="small" sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }} />
          <TextField label="Landmark (optional)" value={addrForm.landmark ?? ''} onChange={e => setAddrForm({ ...addrForm, landmark: e.target.value })} fullWidth size="small" sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }} />
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 2 }}>
            <TextField label="PIN Code" value={addrForm.pinCode} onChange={e => setAddrForm({ ...addrForm, pinCode: e.target.value })} fullWidth required size="small" slotProps={{ htmlInput: { maxLength: 6 } }} sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }} />
            <TextField label="City" value={addrForm.city} onChange={e => setAddrForm({ ...addrForm, city: e.target.value })} fullWidth required size="small" sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }} />
            <TextField label="State" value={addrForm.state} onChange={e => setAddrForm({ ...addrForm, state: e.target.value })} fullWidth required size="small" sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }} />
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <FormControl size="small" sx={{ '& .MuiOutlinedInput-root': { borderRadius: 0 } }}>
              <InputLabel>Address Type</InputLabel>
              <Select
                value={addrForm.addressType ?? 'HOME'}
                label="Address Type"
                onChange={e => setAddrForm({ ...addrForm, addressType: e.target.value as 'HOME' | 'WORK' | 'OTHER' })}
              >
                {ADDRESS_TYPES.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl size="small">
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, pt: 1 }}>
                <input
                  type="checkbox"
                  id="set-default-cb"
                  checked={addrForm.setAsDefault ?? false}
                  onChange={e => setAddrForm({ ...addrForm, setAsDefault: e.target.checked })}
                />
                <label htmlFor="set-default-cb" style={{ fontSize: '0.85rem', cursor: 'pointer' }}>
                  Set as default address
                </label>
              </Box>
            </FormControl>
          </Box>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={onClose} sx={{ borderRadius: 0 }}>Cancel</Button>
        <Button
          id="save-address-btn"
          variant="contained"
          onClick={onSave}
          disabled={isSaving}
          sx={{ borderRadius: 0 }}
        >
          {isSaving ? <CircularProgress size={18} color="inherit" /> : (editAddr ? 'Save Changes' : 'Add Address')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default AddressDialog;
