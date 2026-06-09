import React from 'react';
import { IconButton, Tooltip } from '@mui/material';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import FavoriteIcon from '@mui/icons-material/Favorite';
import { useWishlist, useAddToWishlist, useRemoveFromWishlist } from '../hooks/useWishlist';
import { useAuthStore } from '@/store/authStore';
import { useNavigate } from 'react-router-dom';

interface WishlistButtonProps {
  variantId: number;
  size?: 'small' | 'medium' | 'large';
  edge?: 'start' | 'end' | false;
}

const WishlistButton: React.FC<WishlistButtonProps> = ({ variantId, size = 'medium', edge = false }) => {
  const { data: wishlist, isLoading } = useWishlist();
  const { mutate: add, isPending: isAdding } = useAddToWishlist();
  const { mutate: remove, isPending: isRemoving } = useRemoveFromWishlist();
  const { isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  const isWished = wishlist?.items.some((item) => item.variantId === variantId);

  const toggleWishlist = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (!isAuthenticated) {
      navigate('/login');
      return;
    }

    if (isWished) {
      remove(variantId);
    } else {
      add({ variantId });
    }
  };

  const isBusy = isLoading || isAdding || isRemoving;

  return (
    <Tooltip title={isWished ? 'Remove from Wishlist' : 'Add to Wishlist'} placement="top">
      <span>
        <IconButton
          size={size}
          edge={edge}
          onClick={toggleWishlist}
          disabled={isBusy}
          sx={{
            color: isWished ? 'error.main' : 'text.secondary',
            transition: 'all 0.2s',
            '&:hover': {
              transform: 'scale(1.1)',
              color: 'error.main',
            },
          }}
        >
          {isWished ? <FavoriteIcon fontSize="inherit" /> : <FavoriteBorderIcon fontSize="inherit" />}
        </IconButton>
      </span>
    </Tooltip>
  );
};

export default WishlistButton;
