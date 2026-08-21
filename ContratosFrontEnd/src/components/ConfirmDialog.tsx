import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from "@mui/material";

export default function ConfirmDialog({ open, title, text, onClose, onConfirm }: { open: boolean; title: string; text: string; onClose: () => void; onConfirm: () => void }) {
  return <Dialog open={open} onClose={onClose}><DialogTitle>{title}</DialogTitle><DialogContent><DialogContentText>{text}</DialogContentText></DialogContent><DialogActions><Button onClick={onClose}>Cancelar</Button><Button onClick={onConfirm} color="error" variant="contained">Excluir</Button></DialogActions></Dialog>;
}
