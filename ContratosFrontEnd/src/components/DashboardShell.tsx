"use client";

import { getJson, postJson } from "@/lib/api";
import type { AuthStatus } from "@/types";
import ApartmentOutlinedIcon from "@mui/icons-material/ApartmentOutlined";
import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import LogoutOutlinedIcon from "@mui/icons-material/LogoutOutlined";
import MenuIcon from "@mui/icons-material/Menu";
import PeopleAltOutlinedIcon from "@mui/icons-material/PeopleAltOutlined";
import { AppBar, Avatar, Box, CircularProgress, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Stack, Toolbar, Tooltip, Typography } from "@mui/material";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createContext, type ReactNode, useContext, useEffect, useState } from "react";

const drawerWidth = 252;
const AuthContext = createContext<AuthStatus>({ valid: false });
export const useAuth = () => useContext(AuthContext);

const items = [
  { href: "/dashboard/contracts", label: "Contratos", icon: <DescriptionOutlinedIcon />, hideFromFiscal: false },
  { href: "/dashboard/users", label: "Usuários", icon: <PeopleAltOutlinedIcon />, hideFromFiscal: true },
  { href: "/dashboard/sectors", label: "Setores", icon: <ApartmentOutlinedIcon />, hideFromFiscal: true },
];

export default function DashboardShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [auth, setAuth] = useState<AuthStatus | null>(null);

  useEffect(() => {
    getJson<AuthStatus>("/auth/validate").then(status => {
      if (!status.valid) router.replace("/login"); else setAuth(status);
    }).catch(() => router.replace("/login"));
  }, [router]);

  async function logout() {
    await postJson("/auth/logout").catch(() => undefined);
    router.replace("/login"); router.refresh();
  }

  const visibleItems = items.filter(
    items => !(auth?.perfil === "FISCAL" && items.hideFromFiscal),
  )

  const profileLabels = {
    ADMIN: "Administrador",
    CONTROLE_INTERNO: "Controle Interno",
    FISCAL: "Fiscal",
  } as const;

  const drawer = <Stack height="100%">
    <Toolbar sx={{ px: 2.5 }}>
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Avatar variant="rounded" sx={{ bgcolor: "secondary.main", color: "#17233C", fontWeight: 900 }}>C</Avatar>
        <Box>
          <Typography fontWeight={800} lineHeight={1.1}>CISBAF</Typography>
          <Typography variant="caption" color="text.secondary">Gestão de contratos</Typography>
        </Box>
      </Stack>
    </Toolbar>
    <Divider />
    <List sx={{ px: 1.5, py: 2, flex: 1 }}>{visibleItems.map(item =>

      <ListItemButton key={item.href} component={Link} href={item.href} selected={pathname === item.href}
        onClick={() => setMobileOpen(false)} sx={{ borderRadius: 2, mb: .5 }}>
        <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
        <ListItemText primary={item.label} primaryTypographyProps={{ fontWeight: pathname === item.href ? 700 : 500 }} /></ListItemButton>
    )}
    </List>
    <Divider />
    <Stack direction="row" alignItems="center" spacing={1.5} p={2}>
      <Avatar sx={{ width: 38, height: 38 }}>{auth?.name?.charAt(0) ?? "U"}</Avatar>
      <Box minWidth={0} flex={1}>
        <Typography variant="body2" fontWeight={700} noWrap>{auth?.name ?? "Usuário"}</Typography>
        <Typography variant="caption" color="text.secondary">{auth?.perfil
          ? profileLabels[auth.perfil]
          : auth?.admin
            ? "Administrador"
            : "Usuário"}</Typography>
      </Box>
      <Tooltip title="Sair">
        <IconButton onClick={logout} size="small">
          <LogoutOutlinedIcon />
        </IconButton>
      </Tooltip>
    </Stack>
  </Stack>;

  if (!auth) return <Box minHeight="100vh" display="grid" sx={{ placeItems: "center" }}><CircularProgress /></Box>;

  return <AuthContext.Provider value={auth}><Box sx={{ display: "flex", minHeight: "100vh" }}>
    <AppBar position="fixed" color="inherit" elevation={0} sx={{ display: { md: "none" }, borderBottom: "1px solid #E4EAF2" }}>
      <Toolbar>
        <IconButton edge="start" onClick={() => setMobileOpen(true)}>
          <MenuIcon />
        </IconButton>
        <Typography fontWeight={800}>Controle de Contratos</Typography>
      </Toolbar>
    </AppBar>
    <Box component="nav" sx={{ width: { md: drawerWidth }, flexShrink: { md: 0 } }}>
      <Drawer variant="temporary" open={mobileOpen} onClose={() => setMobileOpen(false)} ModalProps={{ keepMounted: true }} sx={{ display: { xs: "block", md: "none" }, "& .MuiDrawer-paper": { width: drawerWidth } }}>{drawer}</Drawer>
      <Drawer variant="permanent" open sx={{ display: { xs: "none", md: "block" }, "& .MuiDrawer-paper": { width: drawerWidth, borderRight: "1px solid #E4EAF2" } }}>{drawer}</Drawer>
    </Box>
    <Box component="main" sx={{ flex: 1, minWidth: 0, pt: { xs: 10, md: 0 }, p: { xs: 2, md: 4 } }}>{children}</Box>
  </Box></AuthContext.Provider>;
}
