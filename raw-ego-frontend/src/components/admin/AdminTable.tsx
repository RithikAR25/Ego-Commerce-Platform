import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  TablePagination,
  Typography,
  CircularProgress,
  Button,
  Box,
} from '@mui/material';
import React from 'react';

export interface Column<T> {
  id: string;
  label: string;
  minWidth?: number;
  align?: 'right' | 'left' | 'center';
  format?: (value: any, row: T) => React.ReactNode;
}

interface AdminTableProps<T> {
  columns: Column<T>[];
  data: T[];
  totalElements: number;
  page: number;
  rowsPerPage: number;
  onPageChange: (newPage: number) => void;
  onRowsPerPageChange?: (newRowsPerPage: number) => void;
  isLoading?: boolean;
  isError?: boolean;
  onRetry?: () => void;
  emptyMessage?: string;
  onRowClick?: (row: T) => void;
  dense?: boolean;
}

const AdminTable = <T extends { id?: number | string }>({
  columns,
  data,
  totalElements,
  page,
  rowsPerPage,
  onPageChange,
  onRowsPerPageChange,
  isLoading,
  isError,
  onRetry,
  emptyMessage = 'No records found.',
  onRowClick,
  dense = false,
}: AdminTableProps<T>) => {
  const handleChangePage = (_event: unknown, newPage: number) => {
    onPageChange(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (onRowsPerPageChange) {
      onRowsPerPageChange(+event.target.value);
    }
  };

  return (
    <Paper
      elevation={0}
      sx={{
        width: '100%',
        overflow: 'hidden',
        borderRadius: 0,
        border: (theme) => `1px solid ${theme.palette.border.default}`,
        bgcolor: 'surface.secondary',
      }}
    >
      <TableContainer sx={{ maxHeight: 600 }}>
        <Table stickyHeader aria-label="admin table" size={dense ? 'small' : 'medium'}>
          <TableHead>
            <TableRow>
              {columns.map((column) => (
                <TableCell
                  key={column.id}
                  align={column.align}
                  sx={{
                    minWidth: column.minWidth,
                    fontFamily: (theme) => theme.typography.fontFamilyUtility,
                    fontWeight: 700,
                    fontSize: '0.6rem',
                    letterSpacing: '0.15em',
                    textTransform: 'uppercase',
                    color: 'text.secondary',
                    bgcolor: 'surface.primary',
                    borderBottom: (theme) => `1px solid ${theme.palette.border.default}`,
                    py: 2,
                  }}
                >
                  {column.label}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={columns.length} align="center" sx={{ py: 8, bgcolor: 'surface.secondary', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
                  <CircularProgress size={28} sx={{ color: 'text.secondary' }} />
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyUtility, fontSize: '0.8rem', mt: 2, color: 'text.secondary' }}>
                    Loading data…
                  </Typography>
                </TableCell>
              </TableRow>
            ) : isError ? (
              <TableRow>
                <TableCell colSpan={columns.length} align="center" sx={{ py: 8, bgcolor: 'surface.secondary', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'error.main', mb: 2 }}>
                    Failed to load data.
                  </Typography>
                  {onRetry && (
                    <Button
                      variant="outlined"
                      onClick={onRetry}
                      sx={{
                        borderRadius: 0,
                        borderColor: 'border.default',
                        color: 'text.secondary',
                        fontFamily: (theme) => theme.typography.fontFamilyUtility,
                        fontSize: '0.72rem',
                        letterSpacing: '0.1em',
                        '&:hover': { borderColor: 'border.strong', color: 'text.primary', bgcolor: 'transparent' },
                      }}
                    >
                      Retry
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ) : data.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length} align="center" sx={{ py: 8, bgcolor: 'surface.secondary', borderBottom: (theme) => `1px solid ${theme.palette.border.default}` }}>
                  <Typography sx={{ fontFamily: (theme) => theme.typography.fontFamilyEditorial, color: 'text.secondary', fontStyle: 'italic' }}>
                    {emptyMessage}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              data.map((row, index) => {
                const rowKey = row.id ?? index;
                return (
                  <TableRow
                    key={rowKey}
                    onClick={() => onRowClick && onRowClick(row)}
                    sx={{
                      cursor: onRowClick ? 'pointer' : 'default',
                      bgcolor: 'surface.secondary',
                      '& td': { borderBottom: (theme) => `1px solid ${theme.palette.border.default}` },
                      '&:hover': onRowClick
                        ? { bgcolor: (theme) => `${theme.palette.text.primary}08` }
                        : {},
                    }}
                  >
                    {columns.map((column) => {
                      const value = (row as any)[column.id];
                      return (
                        <TableCell
                          key={column.id}
                          align={column.align}
                          sx={{
                            fontFamily: (theme) => theme.typography.fontFamilyUtility,
                            fontSize: '0.85rem',
                            color: 'text.primary',
                          }}
                        >
                          {column.format ? column.format(value, row) : value}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
      <Box sx={{ borderTop: (theme) => `1px solid ${theme.palette.border.default}` }}>
        <TablePagination
          rowsPerPageOptions={[10, 20, 50]}
          component="div"
          count={totalElements}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
          sx={{
            color: 'text.secondary',
            fontFamily: (theme) => theme.typography.fontFamilyUtility,
            '& .MuiTablePagination-selectLabel, & .MuiTablePagination-displayedRows': {
              fontFamily: (theme) => theme.typography.fontFamilyUtility,
              fontSize: '0.75rem',
            },
            '& .MuiIconButton-root': { color: 'text.secondary' },
            '& .Mui-disabled': { opacity: 0.3 },
          }}
        />
      </Box>
    </Paper>
  );
};

export default AdminTable;
