import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { setTokenGetter } from '@/api/client'
import { logout as apiLogout, refreshToken as apiRefresh } from '@/api/auth'
import { getNotifications, markNotificationRead as apiMarkNotificationRead } from '@/api/notifications'

const AuthContext = createContext(null)
const RT_KEY = 'sattolux_rt'

// 모듈 레벨 guard: StrictMode의 두 번째 effect 실행을 차단 (useRef/state는 remount 시 초기화되지만 모듈 변수는 유지됨)
let _initialRefreshDone = false

function hasStoredToken() {
  return !!sessionStorage.getItem(RT_KEY)
}

export function AuthProvider({ children }) {
  // RT가 있으면 ready=false(로더), 없으면 ready=true(즉시 로그인) — 첫 렌더부터 올바른 상태
  const [auth, setAuth] = useState(() => ({
    accessToken: null,
    ready: !hasStoredToken(),
  }))
  const refreshTimerRef = useRef(null)
  const eventSourceRef = useRef(null)
  const [notificationState, setNotificationState] = useState({
    unreadCount: 0,
    notifications: [],
  })

  useEffect(() => {
    setTokenGetter(() => auth.accessToken)
  }, [auth.accessToken])

  const parseExp = (token) => {
    try { return JSON.parse(atob(token.split('.')[1])).exp } catch { return null }
  }

  const scheduleRefresh = useCallback((token) => {
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current)
    const exp = parseExp(token)
    if (!exp) return
    const delay = exp * 1000 - Date.now() - 60_000
    if (delay <= 0) return
    refreshTimerRef.current = setTimeout(async () => {
      const rt = sessionStorage.getItem(RT_KEY)
      if (!rt) return
      try {
        const { data } = await apiRefresh({ refreshToken: rt })
        saveTokens(data)
      } catch {
        clearAuth()
      }
    }, delay)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const saveTokens = useCallback(({ accessToken: at, refreshToken: rt } = {}) => {
    setAuth({ accessToken: at ?? null, ready: true })
    if (rt) sessionStorage.setItem(RT_KEY, rt)
    if (at) scheduleRefresh(at)
  }, [scheduleRefresh])

  const clearAuth = useCallback(() => {
    setAuth({ accessToken: null, ready: true })
    sessionStorage.removeItem(RT_KEY)
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current)
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    setNotificationState({ unreadCount: 0, notifications: [] })
  }, [])

  const logout = useCallback(async () => {
    const rt = sessionStorage.getItem(RT_KEY)
    if (rt) await apiLogout({ refreshToken: rt }).catch(() => {})
    clearAuth()
  }, [clearAuth])

  // 페이지 새로고침: RT가 있을 때만 실행, _initialRefreshDone으로 StrictMode 이중 실행 차단
  useEffect(() => {
    const rt = sessionStorage.getItem(RT_KEY)
    if (!rt) return
    if (_initialRefreshDone) return
    _initialRefreshDone = true

    apiRefresh({ refreshToken: rt })
      .then(({ data }) => saveTokens(data))
      .catch(() => {
        sessionStorage.removeItem(RT_KEY)
        setAuth({ accessToken: null, ready: true })
      })
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const refreshNotifications = useCallback(async () => {
    if (!auth.accessToken) return
    try {
      const { data } = await getNotifications()
      setNotificationState({
        unreadCount: data?.unreadCount ?? 0,
        notifications: Array.isArray(data?.notifications) ? data.notifications : [],
      })
    } catch {
      // ignore notification fetch failures to avoid breaking auth flow
    }
  }, [auth.accessToken])

  const markNotificationRead = useCallback(async (notificationId) => {
    await apiMarkNotificationRead(notificationId)
    setNotificationState((current) => {
      const notifications = current.notifications.map((notification) =>
        notification.notificationId === notificationId ? { ...notification, readYn: 'Y' } : notification
      )
      const unreadCount = notifications.filter((notification) => notification.readYn === 'N').length
      return { unreadCount, notifications }
    })
  }, [])

  useEffect(() => {
    if (!auth.accessToken) return
    refreshNotifications()
  }, [auth.accessToken, refreshNotifications])

  useEffect(() => {
    if (!auth.accessToken) return

    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }

    const eventSource = new EventSource(`/api/auth/sse?accessToken=${encodeURIComponent(auth.accessToken)}`)
    eventSourceRef.current = eventSource

    eventSource.addEventListener('notification', (event) => {
      try {
        const payload = JSON.parse(event.data)
        setNotificationState((current) => {
          const nextNotifications = [
            {
              notificationId: payload.notificationId,
              typeCode: payload.type,
              title: payload.title,
              message: payload.message,
              targetYear: payload.targetYear,
              targetMonth: payload.targetMonth,
              targetWeekOfMonth: payload.targetWeekOfMonth,
              drawNo: payload.drawNo,
              readYn: 'N',
              createdAt: payload.createdAt,
            },
            ...current.notifications.filter((notification) => notification.notificationId !== payload.notificationId),
          ].slice(0, 10)

          return {
            unreadCount: current.unreadCount + 1,
            notifications: nextNotifications,
          }
        })
      } catch {
        // ignore malformed notification payload
      }
    })

    return () => {
      eventSource.close()
      if (eventSourceRef.current === eventSource) {
        eventSourceRef.current = null
      }
    }
  }, [auth.accessToken])

  return (
    <AuthContext.Provider value={{
      accessToken: auth.accessToken,
      isAuthenticated: !!auth.accessToken,
      ready: auth.ready,
      saveTokens,
      logout,
      unreadCount: notificationState.unreadCount,
      notifications: notificationState.notifications,
      refreshNotifications,
      markNotificationRead,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
