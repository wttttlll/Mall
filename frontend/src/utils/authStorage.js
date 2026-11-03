const ACCESS_TOKEN_KEY = 'mallagent_access_token'
const REFRESH_TOKEN_KEY = 'mallagent_refresh_token'

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function saveTokens(tokens) {
  if (tokens?.accessToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  }
  if (tokens?.refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
  }
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}
