import { useState } from 'react'
import { Bell, Menu, Sparkles } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'

export default function TopBar({ onMenuClick }) {
  const navigate = useNavigate()
  const { unreadCount, notifications, markNotificationRead } = useAuth()
  const [open, setOpen] = useState(false)

  async function handleNotificationClick(notification) {
    if (notification.readYn === 'N') {
      await markNotificationRead(notification.notificationId).catch(() => {})
    }
    setOpen(false)
    navigate(`/result?year=${notification.targetYear}&month=${notification.targetMonth}&week=${notification.targetWeekOfMonth}`)
  }

  return (
    <header className="fixed top-0 left-0 right-0 z-30 h-14 bg-slate-900 flex items-center px-4 shadow-md">
      <Button variant="ghost" size="icon" onClick={onMenuClick}
        className="text-slate-300 hover:text-white hover:bg-slate-700 mr-3">
        <Menu className="w-5 h-5" />
      </Button>
      <div className="flex items-center gap-1.5">
        <Sparkles className="w-4 h-4 text-indigo-400" />
        <span className="text-white font-semibold text-lg tracking-tight">SattoLux</span>
      </div>
      <div className="ml-auto relative">
        <Button
          variant="ghost"
          size="icon"
          onClick={() => setOpen((current) => !current)}
          className="text-slate-300 hover:text-white hover:bg-slate-700"
        >
          <Bell className="w-5 h-5" />
          {unreadCount > 0 && (
            <span className="absolute -right-0.5 -top-0.5 min-w-[18px] rounded-full bg-red-500 px-1.5 text-[10px] font-semibold leading-[18px] text-white">
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </Button>

        {open && (
          <div className="absolute right-0 mt-2 w-80 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl">
            <div className="border-b border-slate-100 px-4 py-3">
              <p className="text-sm font-semibold text-slate-900">알림</p>
              <p className="mt-1 text-xs text-slate-500">최근 결과 도착 알림</p>
            </div>
            <div className="max-h-96 overflow-y-auto">
              {notifications.length === 0 && (
                <div className="px-4 py-8 text-center text-sm text-slate-500">새 알림이 없습니다.</div>
              )}
              {notifications.map((notification) => (
                <button
                  key={notification.notificationId}
                  type="button"
                  onClick={() => handleNotificationClick(notification)}
                  className="w-full border-b border-slate-100 px-4 py-3 text-left hover:bg-slate-50"
                >
                  <div className="flex items-start gap-3">
                    <span className={`mt-1 h-2.5 w-2.5 rounded-full ${notification.readYn === 'N' ? 'bg-red-500' : 'bg-slate-300'}`} />
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-slate-900">{notification.title}</p>
                      <p className="mt-1 text-sm text-slate-600">{notification.message}</p>
                      <p className="mt-1 text-xs text-slate-400">
                        {notification.targetYear}년 {notification.targetMonth}월 {notification.targetWeekOfMonth}주차
                      </p>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </header>
  )
}
