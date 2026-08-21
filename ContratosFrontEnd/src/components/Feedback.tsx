import { Alert, Box, CircularProgress, Snackbar } from "@mui/material";

export function PageLoading() { return <Box py={12} display="grid" sx={{ placeItems: "center" }}><CircularProgress /></Box>; }

export function Feedback({ message, error, onClose }: { message: string; error?: boolean; onClose: () => void }) {
  return <Snackbar open={Boolean(message)} autoHideDuration={4500} onClose={onClose} anchorOrigin={{ vertical: "bottom", horizontal: "right" }}><Alert severity={error ? "error" : "success"} variant="filled" onClose={onClose}>{message}</Alert></Snackbar>;
}
