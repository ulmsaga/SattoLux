import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/context/AuthContext'
import * as authApi from '@/api/auth'

const STEP = { CREDENTIAL: 'credential', OTP: 'otp' }
const PIN_USER = 'ulmsaga'

export default function LoginPage() {
  const navigate = useNavigate()
  const { saveTokens } = useAuth()

  const [step, setStep] = useState(STEP.CREDENTIAL)
  const [userId, setUserId] = useState(() => localStorage.getItem('savedUserId') ?? '')
  const [password, setPassword] = useState('')
  const [pin, setPin] = useState('')
  const [otp, setOtp] = useState('')
  const [authSessionToken, setAuthSessionToken] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [rememberUser, setRememberUser] = useState(() => !!localStorage.getItem('savedUserId'))
  const [pinMode, setPinMode] = useState(false)

  const isPinAvailable = userId === PIN_USER

  function handleUserIdChange(e) {
    const value = e.target.value
    setUserId(value)
    if (value !== PIN_USER) {
      setPinMode(false)
    }
  }

  async function doLogin(sessionId, encryptedValue, isPinLogin) {
    if (isPinLogin) {
      return authApi.pinLogin({ sessionId, userId, encryptedPin: encryptedValue })
    }
    return authApi.login({ sessionId, userId, encryptedPassword: encryptedValue })
  }

  const handleLogin = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const { data: rsaData } = await authApi.getRsaKey()
      const value = pinMode ? pin : password
      const encryptedValue = await authApi.encryptPassword(rsaData.publicKey, value)

      const { data: loginData } = await doLogin(rsaData.sessionId, encryptedValue, pinMode)

      if (rememberUser) {
        localStorage.setItem('savedUserId', userId)
      } else {
        localStorage.removeItem('savedUserId')
      }

      if (loginData.otpEnabled) {
        await authApi.sendOtp({ authSessionToken: loginData.authSessionToken })
        setAuthSessionToken(loginData.authSessionToken)
        setStep(STEP.OTP)
      } else {
        const { data: tokenData } = await authApi.issueToken({
          authSessionToken: loginData.authSessionToken,
        })
        saveTokens(tokenData)
        navigate('/make-week-num', { replace: true })
      }
    } catch (err) {
      setError(err.response?.data?.message ?? '로그인에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const handleOtp = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const { data } = await authApi.verifyOtp({ authSessionToken, code: otp })
      saveTokens(data)
      navigate('/make-week-num', { replace: true })
    } catch (err) {
      setError(err.response?.data?.message ?? 'OTP 인증에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const canSubmit = pinMode
    ? userId && pin.length === 4
    : userId && password

  if (step === STEP.OTP) {
    return (
      <form onSubmit={handleOtp} className="space-y-5">
        <div>
          <h2 className="text-xl font-bold text-slate-900">2차 인증</h2>
          <p className="text-sm text-muted-foreground mt-1">이메일로 발송된 6자리 코드를 입력하세요.</p>
        </div>
        <Input
          type="text"
          inputMode="numeric"
          maxLength={6}
          placeholder="000000"
          className="text-center text-2xl tracking-widest"
          value={otp}
          onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
          autoFocus
        />
        {error && <p className="text-sm text-destructive">{error}</p>}
        <Button type="submit" className="w-full" disabled={loading || otp.length !== 6}>
          {loading && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
          확인
        </Button>
        <button
          type="button"
          onClick={() => { setStep(STEP.CREDENTIAL); setError('') }}
          className="w-full text-sm text-muted-foreground hover:text-foreground text-center"
        >
          뒤로
        </button>
      </form>
    )
  }

  return (
    <form onSubmit={handleLogin} className="space-y-5">
      <div>
        <h2 className="text-xl font-bold text-slate-900">로그인</h2>
        <p className="text-sm text-muted-foreground mt-1">계정 정보를 입력하세요.</p>
      </div>
      <div className="space-y-3">
        <Input
          type="text"
          placeholder="아이디"
          autoComplete="username"
          value={userId}
          onChange={handleUserIdChange}
          autoFocus={!userId}
        />

        {isPinAvailable && (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => { setPinMode(false); setPin('') }}
              className={`flex-1 rounded-xl border py-2 text-xs font-medium transition ${!pinMode ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 text-slate-500 hover:border-slate-400'}`}
            >
              비밀번호
            </button>
            <button
              type="button"
              onClick={() => { setPinMode(true); setPassword('') }}
              className={`flex-1 rounded-xl border py-2 text-xs font-medium transition ${pinMode ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 text-slate-500 hover:border-slate-400'}`}
            >
              PIN 4자리
            </button>
          </div>
        )}

        {pinMode ? (
          <Input
            type="password"
            inputMode="numeric"
            placeholder="PIN 4자리"
            autoComplete="off"
            maxLength={4}
            className="text-center text-2xl tracking-widest"
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, 4))}
            autoFocus
          />
        ) : (
          <Input
            type="password"
            placeholder="비밀번호"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        )}
      </div>

      <label className="flex items-center gap-2 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={rememberUser}
          onChange={(e) => setRememberUser(e.target.checked)}
          className="h-4 w-4 rounded border-slate-300 accent-slate-900"
        />
        <span className="text-sm text-slate-600">아이디 저장</span>
      </label>

      {error && <p className="text-sm text-destructive">{error}</p>}
      <Button type="submit" className="w-full" disabled={loading || !canSubmit}>
        {loading && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
        로그인
      </Button>
    </form>
  )
}
