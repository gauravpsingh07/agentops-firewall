import { UserRole } from './user-role';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthenticatedUser {
  id: string;
  username: string;
  role: UserRole;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthenticatedUser;
}
