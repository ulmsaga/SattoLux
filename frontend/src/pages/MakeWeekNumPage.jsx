import { useEffect, useRef, useState } from 'react'
import { AlertCircle, CalendarDays, Cpu, Loader2, RefreshCcw, Sparkles, Ticket, Wand2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import NumberBall from '@/components/NumberBall'
import { cn } from '@/lib/utils'
import {
  generateManualCurrentWeekNumbers,
  getCurrentWeekNumbers,
  getGenerationRules,
  getCurrentWeekStatus,
} from '@/api/makeWeekNum'

const DAY_LABEL = {
  1: '월요일',
  2: '화요일',
  3: '수요일',
  4: '목요일',
  5: '금요일',
}

const METHOD_LABEL = {
  RANDOM: '완전 랜덤',
  HOT_NUMBER: '핫넘버',
  MIXED: '혼합 전략',
}

const ENGINE_LABEL = {
  LOCAL: 'LOCAL',
  CLAUDE: 'CLAUDE',
}

const METHOD_TONE = {
  RANDOM: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  HOT_NUMBER: 'bg-amber-50 text-amber-700 ring-amber-200',
  MIXED: 'bg-slate-100 text-slate-600 ring-slate-200',
}

const ENGINE_TONE = {
  LOCAL: 'bg-slate-900 text-white',
  CLAUDE: 'bg-indigo-600 text-white',
}

function weekLabel(set) {
  if (!set) return '-'
  return `${set.targetYear}년 ${set.targetMonth}월 ${set.targetWeekOfMonth}주차`
}

function formatCreatedAt(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function groupSetsByRule(sets) {
  const groupedMap = new Map()

  for (const set of Array.isArray(sets) ? sets : []) {
    if (!groupedMap.has(set.ruleId)) {
      groupedMap.set(set.ruleId, {
        ruleId: set.ruleId,
        methodCode: set.methodCode,
        generatorCode: set.generatorCode,
        createdAt: set.createdAt,
        items: [],
      })
    }

    const group = groupedMap.get(set.ruleId)
    group.items.push(set)

    if (set.createdAt && (!group.createdAt || set.createdAt > group.createdAt)) {
      group.createdAt = set.createdAt
    }
  }

  return Array.from(groupedMap.values()).sort((left, right) => {
    if (left.ruleId == null) return 1
    if (right.ruleId == null) return -1
    return left.ruleId - right.ruleId
  })
}

export default function MakeWeekNumPage() {
  const navigate = useNavigate()
  const requestSeqRef = useRef(0)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [rules, setRules] = useState([])
  const [numberSets, setNumberSets] = useState([])
  const [weekStatus, setWeekStatus] = useState(null)
  const [error, setError] = useState('')
  const [generateResult, setGenerateResult] = useState(null)

  const plannedSetCount = (Array.isArray(rules) ? rules : []).reduce(
    (sum, rule) => sum + (Number(rule.setCount) || 0),
    0
  )
  const groupedSets = groupSetsByRule(numberSets)
  const firstSet = numberSets[0]
  const latestCreatedAt = (Array.isArray(numberSets) ? numberSets : []).reduce((latest, current) => {
    if (!current.createdAt) return latest
    if (!latest || current.createdAt > latest) return current.createdAt
    return latest
  }, '')
  const manualAvailable = !!weekStatus?.manualGenerationAvailable
  const manualReason = weekStatus?.manualGenerationReason ?? '수동 생성 조건을 확인할 수 없습니다.'
  const scheduleInfo = weekStatus
    ? `${weekStatus.schedulerTime} · ${weekStatus.schedulerZone}`
    : '-'

  async function loadPageData(showSpinner = false) {
    const requestSeq = ++requestSeqRef.current
    if (showSpinner) setRefreshing(true)
    else setLoading(true)

    try {
      setError('')
      const [{ data: rulesData }, { data: setsData }, { data: statusData }] = await Promise.all([
        getGenerationRules(),
        getCurrentWeekNumbers(),
        getCurrentWeekStatus(),
      ])
      if (requestSeq !== requestSeqRef.current) return
      setRules(Array.isArray(rulesData) ? rulesData : [])
      setNumberSets(Array.isArray(setsData) ? setsData : [])
      setWeekStatus(statusData ?? null)
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return
      setError(err.response?.data?.message ?? '번호 조회에 실패했습니다.')
    } finally {
      if (requestSeq !== requestSeqRef.current) return
      if (showSpinner) setRefreshing(false)
      else setLoading(false)
    }
  }

  useEffect(() => {
    loadPageData()
  }, [])

  async function handleGenerate() {
    setGenerating(true)
    setGenerateResult(null)
    setError('')

    try {
      const { data } = await generateManualCurrentWeekNumbers()
      setGenerateResult(data)
      setNumberSets(Array.isArray(data.generatedSets) ? data.generatedSets : [])
      await loadPageData(true)
    } catch (err) {
      setError(err.response?.data?.message ?? '번호 생성에 실패했습니다.')
    } finally {
      setGenerating(false)
    }
  }

  async function handleRefresh() {
    if (refreshing || generating) return
    await loadPageData(true)
  }

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex items-center gap-3 rounded-2xl bg-white px-5 py-4 shadow-sm ring-1 ring-slate-200">
          <Loader2 className="h-5 w-5 animate-spin text-indigo-500" />
          <span className="text-sm font-medium text-slate-600">이번 주 번호를 불러오는 중입니다.</span>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-5 pb-10">
      <section className="overflow-hidden rounded-[28px] bg-[radial-gradient(circle_at_top_left,_rgba(129,140,248,0.35),_transparent_38%),linear-gradient(135deg,_#0f172a_0%,_#111827_45%,_#1e293b_100%)] px-5 py-6 text-white shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-3">
            <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/80 ring-1 ring-white/15">
              <CalendarDays className="h-3.5 w-3.5" />
              {weekLabel(firstSet)}
            </span>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">번호 생성 / 조회</h1>
              <p className="mt-2 max-w-md text-sm leading-6 text-slate-300">
                설정된 규칙 기준으로 이번 주 번호를 확인하고, 필요하면 수동으로 다시 생성할 수 있습니다.
              </p>
            </div>
          </div>
          <Sparkles className="mt-1 h-8 w-8 shrink-0 text-indigo-300" />
        </div>

        <div className="mt-5 grid grid-cols-2 gap-3">
          <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">활성 규칙</p>
            <p className="mt-2 text-2xl font-semibold">{rules.length}</p>
          </div>
          <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">이번 주 세트</p>
            <p className="mt-2 text-2xl font-semibold">{numberSets.length}</p>
          </div>
          <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">계획 세트</p>
            <p className="mt-2 text-2xl font-semibold">{plannedSetCount}</p>
          </div>
          <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">최종 생성</p>
            <p className="mt-2 text-sm font-medium text-slate-100">{formatCreatedAt(latestCreatedAt)}</p>
          </div>
          <div className="col-span-2 rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">자동 생성 시각</p>
            <p className="mt-2 text-sm font-medium text-slate-100">{scheduleInfo}</p>
          </div>
        </div>

        <div className="mt-5 flex flex-col gap-3 sm:flex-row">
          <Button
            type="button"
            onClick={handleGenerate}
            disabled={generating || !manualAvailable}
            className="h-11 rounded-xl bg-white text-slate-900 hover:bg-slate-100"
            title={manualReason}
          >
            {generating ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Wand2 className="mr-2 h-4 w-4" />}
            수동 생성
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={handleRefresh}
            disabled={refreshing || generating}
            className="h-11 rounded-xl border border-white/15 bg-white/5 text-white hover:bg-white/10"
          >
            {refreshing ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCcw className="mr-2 h-4 w-4" />}
            새로고침
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => navigate('/marking-slip')}
            disabled={numberSets.length === 0}
            className="h-11 rounded-xl border border-white/15 bg-white/5 text-white hover:bg-white/10"
          >
            <Ticket className="mr-2 h-4 w-4" />
            마킹 보기
          </Button>
        </div>

        <div className="mt-4 rounded-2xl bg-white/8 px-4 py-3 ring-1 ring-white/10">
          <p className="text-xs uppercase tracking-[0.2em] text-slate-400">수동 생성 상태</p>
          <p className="mt-2 text-sm text-slate-100">
            {manualAvailable ? '자동 생성 누락 상태입니다. 수동 생성 버튼을 사용할 수 있습니다.' : manualReason}
          </p>
          <p className="mt-2 text-xs text-slate-400">
            번호 색상은 구간을 뜻하고, 이중 링은 강조 번호를 의미합니다.
          </p>
        </div>
      </section>

      {error && (
        <section className="rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
          <div className="flex items-start gap-3">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <p>{error}</p>
          </div>
        </section>
      )}

      {generateResult && (
        <section className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-4 text-sm text-emerald-800">
          <p className="font-medium">
            {generateResult.targetYear}년 {generateResult.targetMonth}월 {generateResult.targetWeekOfMonth}주차 번호를
            {` ${generateResult.generatedCount}세트`} 생성했습니다.
          </p>
          {generateResult.skippedRules?.length > 0 && (
            <div className="mt-3 space-y-2">
              {generateResult.skippedRules.map((rule) => (
                <p key={`${rule.ruleId}-${rule.methodCode}`} className="text-xs text-emerald-900/80">
                  rule #{rule.ruleId} · {METHOD_LABEL[rule.methodCode] ?? rule.methodCode} · {rule.reason}
                </p>
              ))}
            </div>
          )}
        </section>
      )}

      <section className="rounded-[24px] bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">활성 생성 규칙</h2>
            <p className="mt-1 text-sm text-slate-500">현재 로그인 계정에 연결된 번호 생성 규칙입니다.</p>
          </div>
          <Cpu className="h-5 w-5 text-slate-400" />
        </div>

        <div className="mt-4 space-y-3">
          {rules.length === 0 && (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
              활성화된 생성 규칙이 없습니다.
            </div>
          )}

          {rules.map((rule) => (
            <div key={rule.ruleId} className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-slate-900">
                    Rule #{rule.ruleId} · {DAY_LABEL[rule.dayOfWeek] ?? `요일 ${rule.dayOfWeek}`}
                  </p>
                  <p className="mt-1 text-sm text-slate-500">
                    {rule.setCount}세트 생성
                    {rule.analysisDrawCount ? ` · 최근 ${rule.analysisDrawCount}회 분석` : ''}
                  </p>
                </div>
                <div className="flex flex-wrap justify-end gap-2">
                  <span className={cn('rounded-full px-2.5 py-1 text-xs font-medium ring-1', METHOD_TONE[rule.methodCode])}>
                    {METHOD_LABEL[rule.methodCode] ?? rule.methodCode}
                  </span>
                  <span className={cn('rounded-full px-2.5 py-1 text-xs font-semibold', ENGINE_TONE[rule.generatorCode])}>
                    {ENGINE_LABEL[rule.generatorCode] ?? rule.generatorCode}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-[24px] bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">이번 주 생성 번호</h2>
            <p className="mt-1 text-sm text-slate-500">규칙별로 묶어 현재 저장된 번호를 보여줍니다.</p>
          </div>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600">
            총 {numberSets.length}세트
          </span>
        </div>

        <div className="mt-4 space-y-4">
          {groupedSets.length === 0 && (
            <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
              아직 생성된 번호가 없습니다. 상단의 버튼으로 이번 주 번호를 생성하세요.
            </div>
          )}

          {groupedSets.map((group) => (
            <article key={`group-${group.ruleId}`} className="overflow-hidden rounded-3xl border border-slate-200 bg-slate-50">
              <header className="flex items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
                <div>
                  <p className="text-sm font-semibold text-slate-900">
                    Rule #{group.ruleId} · {METHOD_LABEL[group.methodCode] ?? group.methodCode}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {ENGINE_LABEL[group.generatorCode] ?? group.generatorCode} · {group.items.length}세트 · {formatCreatedAt(group.createdAt)}
                  </p>
                </div>
                <span className={cn('rounded-full px-2.5 py-1 text-xs font-medium ring-1', METHOD_TONE[group.methodCode])}>
                  {group.methodCode}
                </span>
              </header>

              <div className="space-y-3 p-4">
                {group.items.map((set, index) => (
                  <div key={set.setId} className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-medium text-slate-700">SET {index + 1}</p>
                      <p className="text-xs text-slate-400">{formatCreatedAt(set.createdAt)}</p>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {set.numbers.map((number, numberIndex) => (
                        <NumberBall
                          key={`${set.setId}-${number}`}
                          value={number}
                          emphasized={group.generatorCode === 'CLAUDE' && numberIndex < 2}
                        />
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  )
}
