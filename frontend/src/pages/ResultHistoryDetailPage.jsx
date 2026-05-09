import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Loader2, Trophy } from 'lucide-react'
import NumberBall from '@/components/NumberBall'
import { getResultHistoryDetail } from '@/api/result'
import { cn } from '@/lib/utils'

const RANK_LABEL = { 1: '1등', 2: '2등', 3: '3등', 4: '4등', 5: '5등' }
const RANK_TONE = {
  1: 'bg-amber-100 text-amber-800 ring-amber-200',
  2: 'bg-slate-200 text-slate-800 ring-slate-300',
  3: 'bg-orange-100 text-orange-800 ring-orange-200',
  4: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
  5: 'bg-sky-100 text-sky-800 ring-sky-200',
}

export default function ResultHistoryDetailPage() {
  const { year, month, week } = useParams()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  useEffect(() => {
    async function load() {
      try {
        const { data } = await getResultHistoryDetail(year, month, week)
        setResult(data)
      } catch {
        setError('상세 결과를 불러오는 데 실패했습니다.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [year, month, week])

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex items-center gap-3 rounded-2xl bg-white px-5 py-4 shadow-sm ring-1 ring-slate-200">
          <Loader2 className="h-5 w-5 animate-spin text-indigo-500" />
          <span className="text-sm font-medium text-slate-600">결과를 불러오는 중입니다.</span>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-5 pb-10">
      {/* 헤더 */}
      <section className="overflow-hidden rounded-[28px] bg-[radial-gradient(circle_at_top_left,_rgba(251,191,36,0.28),_transparent_35%),linear-gradient(135deg,_#0f172a_0%,_#1e293b_45%,_#172554_100%)] px-5 py-6 text-white shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1">
            <button
              type="button"
              onClick={() => navigate('/result-history')}
              className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/80 ring-1 ring-white/15 transition hover:bg-white/20"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              지난 결과
            </button>
            <h1 className="mt-3 text-2xl font-semibold tracking-tight">
              {year}년 {month}월 {week}주차
            </h1>
            <p className="mt-2 text-sm leading-6 text-slate-300">당첨된 세트만 표시합니다.</p>
          </div>
          <Trophy className="mt-1 h-8 w-8 shrink-0 text-amber-300" />
        </div>

        {result?.drawNo ? (
          <div className="mt-5 grid gap-3">
            <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-400">당첨 회차</p>
              <p className="mt-2 text-2xl font-semibold">{result.drawNo}회</p>
              <p className="mt-1 text-sm text-slate-300">{result.drawDate}</p>
            </div>
            <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-400">당첨 번호</p>
              <div className="mt-3 flex flex-wrap gap-2">
                {(result.winningNumbers ?? []).map((n) => (
                  <NumberBall key={`win-${n}`} value={n} emphasized />
                ))}
                {result.bonusNo != null && <NumberBall value={result.bonusNo} bonus />}
              </div>
            </div>
          </div>
        ) : (
          <div className="mt-5 rounded-2xl bg-white/8 p-4 ring-1 ring-white/10 text-sm text-slate-200">
            이 주차의 추첨 결과 정보가 없습니다.
          </div>
        )}
      </section>

      {error && (
        <section className="rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
          {error}
        </section>
      )}

      {/* 당첨 세트 */}
      <section className="rounded-[24px] bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-900">당첨 세트</h2>
          <span className="shrink-0 whitespace-nowrap rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
            {result?.items?.length ?? 0}세트
          </span>
        </div>

        <div className="mt-4 space-y-3">
          {(result?.items?.length ?? 0) === 0 && (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
              당첨된 세트가 없습니다.
            </div>
          )}

          {(result?.items ?? []).map((item, index) => (
            <article key={item.setId} className="rounded-3xl border border-slate-200 bg-slate-50 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-slate-900">SET {index + 1}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Rule #{item.ruleId} · {item.methodCode} · {item.generatorCode}
                  </p>
                </div>
                <span className={cn('rounded-full px-2.5 py-1 text-xs font-medium ring-1', RANK_TONE[item.rank])}>
                  {RANK_LABEL[item.rank]}
                </span>
              </div>

              <div className="mt-3 flex flex-wrap gap-2">
                {item.numbers.map((n) => (
                  <NumberBall
                    key={`${item.setId}-${n}`}
                    value={n}
                    emphasized={result?.winningNumbers?.includes(n)}
                  />
                ))}
              </div>

              <div className="mt-3 flex gap-2 text-xs text-slate-500">
                <span>일치 {item.matchCount}개</span>
                <span>·</span>
                <span>보너스 {item.bonusMatch ? '일치' : '불일치'}</span>
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  )
}
