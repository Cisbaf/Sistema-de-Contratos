"use client";

import { createTheme } from "@mui/material/styles";

export const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#155EEF", dark: "#0B3EAD", light: "#E8F0FF" },
    secondary: { main: "#F5B700" },
    background: { default: "#F4F7FB", paper: "#FFFFFF" },
  },
  typography: {
    fontFamily: "Inter, Arial, sans-serif",
    h4: { fontWeight: 750, letterSpacing: "-0.03em" },
    h6: { fontWeight: 700 },
    button: { fontWeight: 700, textTransform: "none" },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiPaper: { styleOverrides: { root: { backgroundImage: "none" } } },
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiTableCell: { styleOverrides: { head: { fontWeight: 700, color: "#526071", background: "#F8FAFC" } } },
  },
});
