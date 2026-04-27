import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import TopBar from '@/components/TopBar'
import SideMenu from '@/components/SideMenu'

export default function MainLayer() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="min-h-screen bg-slate-50">
      <TopBar onMenuClick={() => setMenuOpen(true)} />
      <SideMenu open={menuOpen} onClose={() => setMenuOpen(false)} />
      <main className="pt-14 min-h-screen">
        <div className="max-w-2xl mx-auto px-4 py-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
