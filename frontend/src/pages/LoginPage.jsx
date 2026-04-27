import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/context/AuthContext'
import * as authApi from '@/api/auth'

const STEP = { CREDENTIAL: 'credential', OTP: 'otp' }

export default function LoginPage() {
  const navigate = useNavigate()
  const { saveTokens } = useAuth()

  const [step, setStep] = useState(STEP.CREDENTIAL)
  const [userId, setUserId] = useState('')
  const [password, setPassword] = useState('')
  const [otp, setOtp] = useState('')
  const [authSessionToken, setAuthSessionToken] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleLogin = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const { data: rsaData } = await authApi.getRsaKey()
      const encryptedPassword = await authApi.encryptPassword(rsaData.publicKey, password)

      const { data: loginData } = await authApi.login({
        sessionId: rsaData.sessionId,
        userId,
        encryptedPassword,
      })

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
          onChange={(e) => setUserId(e.target.value)}
          autoFocus
        />
        <Input
          type="password"
          placeholder="비밀번호"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
      <Button type="submit" className="w-full" disabled={loading || !userId || !password}>
        {loading && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
        로그인
      </Button>
    </form>
  )
}
