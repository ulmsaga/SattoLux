import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { History, ChevronRight, Loader2 } from 'lucide-react'
import { getResultHistory } from '@/api/result'
import { cn } from '@/lib/utils'

const RANK_BADGE = {
  1: 'bg-amber-100 text-amber-800 ring-1 ring-amber-200',
  2: 'bg-slate-200 text-slate-800 ring-1 ring-slate-300',
  3: 'bg-orange-100 text-orange-800 ring-1 ring-orange-200',
  4: 'bg-emerald-100 text-emerald-800 ring-1 ring-emerald-200',
  5: 'bg-sky-100 text-sky-800 ring-1 ring-sky-200',
}

export default function ResultHistoryPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [history, setHistory] = useState([])

  useEffect(() => {
    async function load() {
      try {
        const { data } = await getResultHistory()
        setHistory(data)
      } catch {
        setError('이력을 불러오는 데 실패했습니다.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex items-center gap-3 rounded-2xl bg-white px-5 py-4 shadow-sm ring-1 ring-slate-200">
          <Loader2 className="h-5 w-5 animate-spin text-indigo-500" />
          <span className="text-sm font-medium text-slate-600">이력을 불러오는 중입니다.</span>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-5 pb-10">
      {/* 헤더 */}
      <section className="overflow-hidden rounded-[28px] bg-[linear-gradient(135deg,_#0f172a_0%,_#1e293b_45%,_#172554_100%)] px-5 py-6 text-white shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/80 ring-1 ring-white/15">
              <History className="h-3.5 w-3.5" />
              전체 이력
            </span>
            <h1 className="mt-3 text-2xl font-semibold tracking-tight">지난 결과</h1>
            <p className="mt-2 text-sm leading-6 text-slate-300">
              주차별 추첨 결과 이력을 확인합니다.
            </p>
          </div>
          <History className="mt-1 h-8 w-8 shrink-0 text-indigo-300" />
        </div>
      </section>

      {error && (
        <section className="rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
          {error}
        </section>
      )}

      {/* 이력 목록 */}
      <section className="rounded-[24px] bg-white shadow-sm ring-1 ring-slate-200 overflow-hidden">
        {history.length === 0 && !loading && (
          <div className="px-4 py-10 text-center text-sm text-slate-500">
            아직 생성된 번호 이력이 없습니다.
          </div>
        )}

        <ul className="divide-y divide-slate-100">
          {history.map((item) => {
            const label = `${item.targetYear}년 ${item.targetMonth}월 ${item.targetWeekOfMonth}주차`
            return (
              <li key={`${item.targetYear}-${item.targetMonth}-${item.targetWeekOfMonth}`}>
                {item.hasMatch ? (
                  <button
                    type="button"
                    onClick={() => navigate(`/result-history/${item.targetYear}/${item.targetMonth}/${item.targetWeekOfMonth}`)}
                    className="flex w-full items-center gap-2 px-4 py-3.5 text-left transition hover:bg-slate-50 active:bg-slate-100"
                  >
                    <span className="shrink-0 text-sm font-medium text-slate-800">{label}</span>
                    <span className="flex flex-1 flex-wrap gap-1.5">
                      {item.rankSummary.map(({ rank, count }) => (
                        <span key={rank} className={cn('rounded-full px-2 py-0.5 text-xs font-medium', RANK_BADGE[rank])}>
                          {rank}등 {count}
                        </span>
                      ))}
                    </span>
                    <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-medium text-indigo-600 ring-1 ring-indigo-100">
                      상세
                      <ChevronRight className="h-3 w-3" />
                    </span>
                  </button>
                ) : (
                  <div className="flex items-center gap-2 px-4 py-3.5">
                    <span className="text-sm font-medium text-slate-400">{label}</span>
                    <span className="text-xs text-slate-300">미당첨</span>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      </section>
    </div>
  )
}
