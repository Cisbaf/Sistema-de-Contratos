export interface Sector { id: number; name: string; usersCount: number }

export interface User {
  id: number;
  name: string;
  email: string;
  cellPhone: string | null;
  sector: { id: number; name: string } | null;
  admin: boolean;
  perfil: "ADMIN" | "CONTROLE_INTERNO" | "FISCAL";
}

export interface Contract {
  id: number;
  numberContract: string;
  numberProcess: string;
  object: string;
  company: string;
  cnpjCpf: string;
  valueGlobal: number;
  valueMensal: number;
  fiscais: User[];
  startDate: string;
  endDate: string;
  font: string | null;
  ta: string | null;
}

export interface AuthStatus {
  valid: boolean;
  username?: string;
  name?: string;
  admin?: boolean;
  perfil?: "ADMIN" | "CONTROLE_INTERNO" | "FISCAL";
}
