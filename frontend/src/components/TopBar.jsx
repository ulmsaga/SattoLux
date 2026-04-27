import { Menu, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'

export default function TopBar({ onMenuClick }) {
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
    </header>
  )
}
