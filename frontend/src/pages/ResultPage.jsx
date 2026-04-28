import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { BarChart2, BellRing, Loader2, Sparkles, RefreshCcw, Trophy } from 'lucide-react'
import { replayResultReadyNotification } from '@/api/notifications'
import { getWeekResult, prepareLatestResultManualTest, runLatestResultManualTest } from '@/api/result'
import { useAuth } from '@/context/AuthContext'
import { cn } from '@/lib/utils'

const RANK_LABEL = {
  1: '1등',
  2: '2등',
  3: '3등',
  4: '4등',
  5: '5등',
}

const RANK_TONE = {
  1: 'bg-amber-100 text-amber-800 ring-amber-200',
  2: 'bg-slate-200 text-slate-800 ring-slate-300',
  3: 'bg-orange-100 text-orange-800 ring-orange-200',
  4: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
  5: 'bg-sky-100 text-sky-800 ring-sky-200',
}

function NumberBall({ value, highlight = false, bonus = false }) {
  return (
    <span
      className={cn(
        'flex h-10 w-10 items-center justify-center rounded-full text-sm font-semibold shadow-sm ring-1',
        bonus && 'bg-amber-400 text-slate-950 ring-amber-300',
        !bonus && highlight && 'bg-slate-900 text-white ring-slate-900',
        !bonus && !highlight && 'bg-white text-slate-700 ring-slate-200'
      )}
    >
      {value}
    </span>
  )
}

export default function ResultPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { accessToken } = useAuth()
  const [loading, setLoading] = useState(true)
  const [preparing, setPreparing] = useState(false)
  const [testing, setTesting] = useState(false)
  const [replaying, setReplaying] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [result, setResult] = useState(null)

  const isAdmin = (() => {
    if (!accessToken) return false
    try {
      const payload = JSON.parse(atob(accessToken.split('.')[1]))
      return payload?.role === 'ADMIN'
    } catch {
      return false
    }
  })()

  useEffect(() => {
    async function load() {
      setLoading(true)
      setError('')
      setNotice('')
      try {
        const { data } = await getWeekResult({
          year: searchParams.get('year'),
          month: searchParams.get('month'),
          week: searchParams.get('week'),
        })
        setResult(data)
      } catch (err) {
        setError(err.response?.data?.message ?? '결과 조회에 실패했습니다.')
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [searchParams])

  async function handleManualTest() {
    setTesting(true)
    setError('')
    setNotice('')
    try {
      const { data } = await runLatestResultManualTest()
      setNotice(
        `${data.drawNo}회 결과를 ${data.targetYear}년 ${data.targetMonth}월 ${data.targetWeekOfMonth}주차 기준으로 비교했습니다. ` +
        `총 ${data.comparedSetCount}세트를 확인했습니다.`
      )
      setSearchParams({
        year: String(data.targetYear),
        month: String(data.targetMonth),
        week: String(data.targetWeekOfMonth),
      })
    } catch (err) {
      setError(err.response?.data?.message ?? '수동 테스트 실행에 실패했습니다.')
    } finally {
      setTesting(false)
    }
  }

  async function handlePrepareManualTest() {
    setPreparing(true)
    setError('')
    setNotice('')
    try {
      const { data } = await prepareLatestResultManualTest()
      setNotice(
        `${data.targetYear}년 ${data.targetMonth}월 ${data.targetWeekOfMonth}주차 기준 테스트 세트 ${data.generatedSetCount}건을 준비했습니다. ` +
        `이제 지난주 결과 테스트를 실행하면 됩니다.`
      )
      setSearchParams({
        year: String(data.targetYear),
        month: String(data.targetMonth),
        week: String(data.targetWeekOfMonth),
      })
    } catch (err) {
      setError(err.response?.data?.message ?? '테스트 세트 준비에 실패했습니다.')
    } finally {
      setPreparing(false)
    }
  }

  async function handleReplayNotification() {
    const year = result?.targetYear ?? searchParams.get('year')
    const month = result?.targetMonth ?? searchParams.get('month')
    const week = result?.targetWeekOfMonth ?? searchParams.get('week')

    if (!year || !month || !week) {
      setError('재전송할 결과 주차 정보가 없습니다.')
      return
    }

    setReplaying(true)
    setError('')
    setNotice('')
    try {
      await replayResultReadyNotification({ year, month, week })
      setNotice(`${year}년 ${month}월 ${week}주차 결과 알림을 SSE로 다시 전송했습니다.`)
    } catch (err) {
      setError(err.response?.data?.message ?? 'SSE 알림 재전송에 실패했습니다.')
    } finally {
      setReplaying(false)
    }
  }

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
      <section className="overflow-hidden rounded-[28px] bg-[radial-gradient(circle_at_top_left,_rgba(251,191,36,0.28),_transparent_35%),linear-gradient(135deg,_#0f172a_0%,_#1e293b_45%,_#172554_100%)] px-5 py-6 text-white shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/80 ring-1 ring-white/15">
              <BarChart2 className="h-3.5 w-3.5" />
              {result?.targetYear}년 {result?.targetMonth}월 {result?.targetWeekOfMonth}주차
            </span>
            <h1 className="mt-3 text-2xl font-semibold tracking-tight">추첨 결과 비교</h1>
            <p className="mt-2 max-w-md text-sm leading-6 text-slate-300">
              생성된 번호와 실제 추첨 결과를 같은 주차 기준으로 비교합니다.
            </p>
          </div>
          <div className="flex items-start gap-2">
            {isAdmin && (
              <>
                <button
                  type="button"
                  onClick={handlePrepareManualTest}
                  disabled={preparing}
                  className="inline-flex items-center gap-2 rounded-full bg-white/12 px-3 py-2 text-xs font-medium text-white ring-1 ring-white/15 transition hover:bg-white/18 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {preparing ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                  테스트 세트 준비
                </button>
                <button
                  type="button"
                  onClick={handleManualTest}
                  disabled={testing}
                  className="inline-flex items-center gap-2 rounded-full bg-white/12 px-3 py-2 text-xs font-medium text-white ring-1 ring-white/15 transition hover:bg-white/18 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {testing ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RefreshCcw className="h-3.5 w-3.5" />}
                  지난주 결과 테스트
                </button>
                <button
                  type="button"
                  onClick={handleReplayNotification}
                  disabled={replaying}
                  className="inline-flex items-center gap-2 rounded-full bg-white/12 px-3 py-2 text-xs font-medium text-white ring-1 ring-white/15 transition hover:bg-white/18 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {replaying ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <BellRing className="h-3.5 w-3.5" />}
                  SSE 알림 재전송
                </button>
              </>
            )}
            <Trophy className="mt-1 h-8 w-8 shrink-0 text-amber-300" />
          </div>
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
                {(result.winningNumbers ?? []).map((number) => (
                  <NumberBall key={`win-${number}`} value={number} highlight />
                ))}
                {result.bonusNo != null && <NumberBall value={result.bonusNo} bonus />}
              </div>
            </div>
          </div>
        ) : (
          <div className="mt-5 rounded-2xl bg-white/8 p-4 ring-1 ring-white/10 text-sm text-slate-200">
            아직 이 주차의 추첨 결과가 수집되지 않았습니다.
          </div>
        )}
      </section>

      {error && (
        <section className="rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
          {error}
        </section>
      )}

      {notice && (
        <section className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-4 text-sm text-emerald-700">
          {notice}
        </section>
      )}

      <section className="rounded-[24px] bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">내 번호 비교 결과</h2>
            <p className="mt-1 text-sm text-slate-500">해당 주차에 생성된 세트를 등수와 함께 보여줍니다.</p>
          </div>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
            총 {result?.items?.length ?? 0}세트
          </span>
        </div>

        <div className="mt-4 space-y-3">
          {(result?.items?.length ?? 0) === 0 && (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
              해당 주차에 비교할 번호가 없습니다.
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
                {item.rank ? (
                  <span className={cn('rounded-full px-2.5 py-1 text-xs font-medium ring-1', RANK_TONE[item.rank])}>
                    {RANK_LABEL[item.rank]}
                  </span>
                ) : (
                  <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-500 ring-1 ring-slate-200">
                    미당첨
                  </span>
                )}
              </div>

              <div className="mt-3 flex flex-wrap gap-2">
                {item.numbers.map((number) => (
                  <NumberBall key={`${item.setId}-${number}`} value={number} highlight={result?.winningNumbers?.includes(number)} />
                ))}
              </div>

              <div className="mt-3 flex gap-2 text-xs text-slate-500">
                <span>일치 {item.matchCount ?? 0}개</span>
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
