import { Sparkles } from 'lucide-react'

export default function LoginLayer({ children }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 flex items-center justify-center p-4">
      <div className="w-full max-w-sm">
        {/* 로고 */}
        <div className="flex flex-col items-center mb-8">
          <div className="flex items-center gap-2 mb-2">
            <Sparkles className="w-8 h-8 text-indigo-400" />
            <span className="text-3xl font-bold text-white tracking-tight">SattoLux</span>
          </div>
          <p className="text-slate-400 text-sm">토요일 밤, 별빛처럼 쏟아지는 행운</p>
        </div>

        {/* 카드 */}
        <div className="bg-white rounded-2xl shadow-2xl p-8">
          {children}
        </div>
      </div>
    </div>
  )
}
