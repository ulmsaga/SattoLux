import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from '@/context/AuthContext'
import LoginLayer from '@/layouts/LoginLayer'
import MainLayer from '@/layouts/MainLayer'
import LoginPage from '@/pages/LoginPage'
import MakeWeekNumPage from '@/pages/MakeWeekNumPage'

function AppLoader() {
  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center">
      <div className="w-8 h-8 rounded-full border-4 border-indigo-500 border-t-transparent animate-spin" />
    </div>
  )
}

function RequireAuth({ children }) {
  const { isAuthenticated, ready } = useAuth()
  if (!ready) return <AppLoader />
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function RedirectIfAuth({ children }) {
  const { isAuthenticated, ready } = useAuth()
  if (!ready) return <AppLoader />
  return isAuthenticated ? <Navigate to="/make-week-num" replace /> : children
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={
            <RedirectIfAuth>
              <LoginLayer><LoginPage /></LoginLayer>
            </RedirectIfAuth>
          } />
          <Route element={<RequireAuth><MainLayer /></RequireAuth>}>
            <Route index element={<Navigate to="/make-week-num" replace />} />
            <Route path="/make-week-num" element={<MakeWeekNumPage />} />
            <Route path="/result" element={<div className="p-8 text-center text-muted-foreground">결과 비교 — Phase 5</div>} />
            <Route path="/settings" element={<div className="p-8 text-center text-muted-foreground">설정 — Phase 4</div>} />
          </Route>
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
