import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, Loader2, Plus, Save, Settings2, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { getGenerationRuleConfig, saveGenerationRuleConfig } from '@/api/config'

const DAY_OPTIONS = [
  { value: 1, label: '월요일' },
  { value: 2, label: '화요일' },
  { value: 3, label: '수요일' },
  { value: 4, label: '목요일' },
  { value: 5, label: '금요일' },
]

const METHOD_OPTIONS = [
  { value: 'RANDOM', label: '완전 랜덤' },
  { value: 'HOT_NUMBER', label: '핫넘버' },
]

const SET_TOTAL_OPTIONS = [1, 5, 10, 15, 20]

function createRow(seed = {}) {
  const methodCode = seed.methodCode ?? 'RANDOM'

  return {
    clientId: crypto.randomUUID(),
    ruleId: seed.ruleId ?? null,
    dayOfWeek: seed.dayOfWeek ?? 4,
    methodCode,
    generatorCode: seed.generatorCode ?? (methodCode === 'HOT_NUMBER' ? 'CLAUDE' : 'LOCAL'),
    setCount: seed.setCount ?? 5,
    analysisDrawCount: seed.analysisDrawCount ?? 1000,
    useYn: seed.useYn ?? 'Y',
  }
}

function normalizeRowForMethod(row) {
  if (row.methodCode === 'HOT_NUMBER') {
    return {
      ...row,
      generatorCode: row.generatorCode === 'LOCAL' || row.generatorCode === 'CLAUDE' ? row.generatorCode : 'CLAUDE',
      analysisDrawCount: row.analysisDrawCount || 1000,
    }
  }

  return {
    ...row,
    generatorCode: 'LOCAL',
    analysisDrawCount: 1000,
  }
}

function SelectField({ className, ...props }) {
  return (
    <select
      {...props}
      className={cn(
        'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition focus:border-slate-300 focus:ring-2 focus:ring-ring/20',
        className
      )}
    />
  )
}

function NumberField(props) {
  return (
    <input
      type="number"
      {...props}
      className={cn(
        'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition focus:border-slate-300 focus:ring-2 focus:ring-ring/20',
        props.className
      )}
    />
  )
}

export default function SettingsPage() {
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [rows, setRows] = useState([])
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const activeRows = useMemo(() => rows.filter((row) => row.useYn === 'Y'), [rows])
  const activeSetTotal = activeRows.reduce((sum, row) => sum + (Number(row.setCount) || 0), 0)
  const activeDayLabel = activeRows[0]
    ? DAY_OPTIONS.find((option) => option.value === Number(activeRows[0].dayOfWeek))?.label ?? '-'
    : '-'
  const setTotalValid = SET_TOTAL_OPTIONS.includes(activeSetTotal)
  const dayConsistent = new Set(activeRows.map((row) => Number(row.dayOfWeek))).size <= 1

  useEffect(() => {
    async function loadRules() {
      try {
        const { data } = await getGenerationRuleConfig()
        setRows((Array.isArray(data) ? data : []).map((row) => createRow(row)))
      } catch (err) {
        setError(err.response?.data?.message ?? '설정 조회에 실패했습니다.')
      } finally {
        setLoading(false)
      }
    }

    loadRules()
  }, [])

  function updateRow(clientId, patch) {
    setRows((current) =>
      current.map((row) => {
        if (row.clientId !== clientId) return row
        return normalizeRowForMethod({ ...row, ...patch })
      })
    )
  }

  function handleAddRow() {
    setRows((current) => [...current, createRow({ dayOfWeek: current[0]?.dayOfWeek ?? 4, setCount: 5 })])
    setSuccess('')
    setError('')
  }

  function handleRemoveRow(clientId) {
    setRows((current) => current.filter((row) => row.clientId !== clientId))
    setSuccess('')
    setError('')
  }

  async function handleSave() {
    setSaving(true)
    setSuccess('')
    setError('')

    try {
      const payload = rows.map((row) => ({
        ruleId: row.ruleId,
        dayOfWeek: Number(row.dayOfWeek),
        methodCode: row.methodCode,
        generatorCode: row.generatorCode,
        setCount: Number(row.setCount),
        analysisDrawCount: row.methodCode === 'HOT_NUMBER' ? Number(row.analysisDrawCount) : null,
        useYn: row.useYn,
      }))

      const { data } = await saveGenerationRuleConfig(payload)
      setRows((Array.isArray(data) ? data : []).map((row) => createRow(row)))
      setSuccess('생성 규칙을 저장했습니다.')
    } catch (err) {
      setError(err.response?.data?.message ?? '설정 저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex items-center gap-3 rounded-2xl bg-white px-5 py-4 shadow-sm ring-1 ring-slate-200">
          <Loader2 className="h-5 w-5 animate-spin text-indigo-500" />
          <span className="text-sm font-medium text-slate-600">생성 설정을 불러오는 중입니다.</span>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-5 pb-10">
      <section className="overflow-hidden rounded-[28px] bg-[radial-gradient(circle_at_top_left,_rgba(45,212,191,0.30),_transparent_35%),linear-gradient(135deg,_#134e4a_0%,_#0f172a_45%,_#172554_100%)] px-5 py-6 text-white shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/80 ring-1 ring-white/15">
              <Settings2 className="h-3.5 w-3.5" />
              generation_rule 설정
            </span>
            <h1 className="mt-3 text-2xl font-semibold tracking-tight">번호 생성 설정</h1>
            <p className="mt-2 max-w-md text-sm leading-6 text-slate-300">
              자동 생성에 사용할 요일, 전략, 엔진, 세트 수를 관리합니다. MIXED 전략은 현재 범위에서 제외되어 있습니다.
            </p>
          </div>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-3">
          <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">전체 규칙</p>
            <p className="mt-2 text-2xl font-semibold">{rows.length}</p>
          </div>
          <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">활성 규칙</p>
            <p className="mt-2 text-2xl font-semibold">{activeRows.length}</p>
          </div>
          <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">활성 요일</p>
            <p className="mt-2 text-sm font-medium text-slate-100">{activeDayLabel}</p>
          </div>
          <div className="rounded-2xl bg-white/8 p-4 ring-1 ring-white/10">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">주간 총 세트</p>
            <p className="mt-2 text-sm font-medium text-slate-100">{activeSetTotal}세트</p>
          </div>
        </div>

        <div className="mt-5 flex flex-col gap-3 sm:flex-row">
          <Button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className="h-11 rounded-xl bg-white text-slate-900 hover:bg-slate-100"
          >
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
            설정 저장
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={handleAddRow}
            disabled={saving}
            className="h-11 rounded-xl border border-white/15 bg-white/5 text-white hover:bg-white/10"
          >
            <Plus className="mr-2 h-4 w-4" />
            규칙 추가
          </Button>
        </div>
      </section>

      {(error || success) && (
        <section
          className={cn(
            'rounded-2xl px-4 py-4 text-sm',
            error ? 'border border-red-200 bg-red-50 text-red-700' : 'border border-emerald-200 bg-emerald-50 text-emerald-800'
          )}
        >
          <div className="flex items-start gap-3">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <p>{error || success}</p>
          </div>
        </section>
      )}

      <section className="rounded-[24px] bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">검증 상태</h2>
            <p className="mt-1 text-sm text-slate-500">활성 규칙 저장 전 체크해야 하는 조건입니다.</p>
          </div>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <div className={cn('rounded-2xl p-4 ring-1', dayConsistent ? 'bg-emerald-50 ring-emerald-200' : 'bg-amber-50 ring-amber-200')}>
            <p className="text-sm font-semibold text-slate-900">활성 요일 일치</p>
            <p className="mt-1 text-sm text-slate-600">
              {dayConsistent ? '활성 규칙의 생성 요일이 일치합니다.' : '활성 규칙의 생성 요일을 하나로 맞춰야 합니다.'}
            </p>
          </div>
          <div className={cn('rounded-2xl p-4 ring-1', setTotalValid ? 'bg-emerald-50 ring-emerald-200' : 'bg-amber-50 ring-amber-200')}>
            <p className="text-sm font-semibold text-slate-900">주간 총 세트 수</p>
            <p className="mt-1 text-sm text-slate-600">
              현재 {activeSetTotal}세트
              {setTotalValid ? ' · 저장 가능' : ' · 1 / 5 / 10 / 15 / 20 중 하나여야 합니다.'}
            </p>
          </div>
        </div>
      </section>

      <section className="space-y-4">
        {rows.map((row, index) => (
          <article key={row.clientId} className="rounded-[24px] bg-white p-5 shadow-sm ring-1 ring-slate-200">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-lg font-semibold text-slate-900">Rule {index + 1}</p>
                <p className="mt-1 text-sm text-slate-500">
                  {row.ruleId ? `기존 규칙 #${row.ruleId}` : '새 규칙'}
                </p>
              </div>
              <Button type="button" variant="ghost" size="icon" onClick={() => handleRemoveRow(row.clientId)} className="text-slate-400 hover:text-red-600">
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>

            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <label className="space-y-2">
                <span className="text-sm font-medium text-slate-700">생성 요일</span>
                <SelectField value={row.dayOfWeek} onChange={(e) => updateRow(row.clientId, { dayOfWeek: Number(e.target.value) })}>
                  {DAY_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </SelectField>
              </label>

              <label className="space-y-2">
                <span className="text-sm font-medium text-slate-700">사용 여부</span>
                <SelectField value={row.useYn} onChange={(e) => updateRow(row.clientId, { useYn: e.target.value })}>
                  <option value="Y">사용</option>
                  <option value="N">미사용</option>
                </SelectField>
              </label>

              <label className="space-y-2">
                <span className="text-sm font-medium text-slate-700">생성 전략</span>
                <SelectField value={row.methodCode} onChange={(e) => updateRow(row.clientId, { methodCode: e.target.value })}>
                  {METHOD_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </SelectField>
                <p className="text-xs text-slate-400">MIXED 전략은 차기 업그레이드 예정입니다.</p>
              </label>

              <label className="space-y-2">
                <span className="text-sm font-medium text-slate-700">생성 엔진</span>
                <SelectField
                  value={row.generatorCode}
                  onChange={(e) => updateRow(row.clientId, { generatorCode: e.target.value })}
                  disabled={row.methodCode === 'RANDOM'}
                >
                  {row.methodCode === 'RANDOM' ? (
                    <option value="LOCAL">LOCAL</option>
                  ) : (
                    <>
                      <option value="CLAUDE">CLAUDE</option>
                      <option value="LOCAL">LOCAL</option>
                    </>
                  )}
                </SelectField>
              </label>

              <label className="space-y-2">
                <span className="text-sm font-medium text-slate-700">세트 수</span>
                <NumberField
                  min="1"
                  max="20"
                  value={row.setCount}
                  onChange={(e) => updateRow(row.clientId, { setCount: Number(e.target.value || 0) })}
                />
              </label>

              <label className="space-y-2">
                <span className="text-sm font-medium text-slate-700">분석 회차 수</span>
                <NumberField
                  min="1"
                  step="1"
                  disabled={row.methodCode !== 'HOT_NUMBER'}
                  value={row.methodCode === 'HOT_NUMBER' ? row.analysisDrawCount : 1000}
                  onChange={(e) => updateRow(row.clientId, { analysisDrawCount: Number(e.target.value || 0) })}
                />
              </label>
            </div>
          </article>
        ))}

        {rows.length === 0 && (
          <div className="rounded-[24px] border border-dashed border-slate-200 bg-white px-4 py-10 text-center text-sm text-slate-500">
            아직 생성 규칙이 없습니다. 상단의 `규칙 추가` 버튼으로 시작하세요.
          </div>
        )}
      </section>
    </div>
  )
}
