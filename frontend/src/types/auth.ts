// バックエンドの AuthLoginRequest / AuthLoginResponse に対応する型
export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
}
