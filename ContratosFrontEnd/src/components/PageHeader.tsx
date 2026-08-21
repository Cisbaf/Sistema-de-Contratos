import { Box, Button, Stack, Typography } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";

export default function PageHeader({ title, subtitle, action, onAction }: { title: string; subtitle: string; action?: string; onAction?: () => void }) {
  return <Stack direction={{ xs: "column", sm: "row" }} alignItems={{ xs: "stretch", sm: "center" }} justifyContent="space-between" spacing={2} mb={3}>
    <Box><Typography variant="h4">{title}</Typography><Typography color="text.secondary" mt={.5}>{subtitle}</Typography></Box>
    {action && <Button onClick={onAction} variant="contained" startIcon={<AddIcon />} size="large">{action}</Button>}
  </Stack>;
}
