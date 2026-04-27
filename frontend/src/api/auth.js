import client from './client'

export const getRsaKey = () => client.get('/auth/rsa-key')
export const login = (data) => client.post('/auth/login', data)
export const issueToken = (data) => client.post('/auth/token', data)
export const sendOtp = (data) => client.post('/auth/otp/send', data)
export const verifyOtp = (data) => client.post('/auth/otp/verify', data)
export const refreshToken = (data, config) => client.post('/auth/refresh', data, config)
export const logout = (data) => client.post('/auth/logout', data)

// Web Crypto API (RSA-OAEP / SHA-256) 로 비밀번호 암호화
export async function encryptPassword(base64PublicKey, plaintext) {
  const binaryDer = Uint8Array.from(atob(base64PublicKey), (c) => c.charCodeAt(0))
  const cryptoKey = await crypto.subtle.importKey(
    'spki',
    binaryDer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  )
  const encoded = new TextEncoder().encode(plaintext)
  const encrypted = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, cryptoKey, encoded)
  return btoa(String.fromCharCode(...new Uint8Array(encrypted)))
}
