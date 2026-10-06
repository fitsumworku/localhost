// Shapes match the Spring DTOs in team/src/main/java/com/neueda/leap/team/dto

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  name: string;
  email: string;
  password: string;
}

// UserDto
export interface User {
  userId: number;
  name: string;
  email: string;
  createdDate: string;
  status: string;
}

// Returned by POST /auth/login
export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
  user: User;
}
