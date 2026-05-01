import { useEffect, useMemo, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, Loader2, MoveHorizontal, Ticket } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { getCurrentWeekNumbers } from '@/api/makeWeekNum'
import { cn } from '@/lib/utils'

const SLOT_LABELS = ['A', 'B', 'C', 'D', 'E']
const NUMBER_ROWS = [
  [1, 2, 3, 4, 5, 6, 7],
  [8, 9, 10, 11, 12, 13, 14],
  [15, 16, 17, 18, 19, 20, 21],
  [22, 23, 24, 25, 26, 27, 28],
  [29, 30, 31, 32, 33, 34, 35],
  [36, 37, 38, 39, 40, 41, 42],
  [43, 44, 45],
]

function toSlides(sets) {
  return (Array.isArray(sets) ? sets : []).map((set, index) => ({
    ...set,
    sheetNumber: Math.floor(index / 5) + 1,
    slotLabel: SLOT_LABELS[index % 5],
    slideNumber: index + 1,
  }))
}

function formatWeekLabel(slide) {
  if (!slide) return '-'
  return `${slide.targetYear}년 ${slide.targetMonth}월 ${slide.targetWeekOfMonth}주차`
}

function MarkCell({ value, selected = false }) {
  return (
    <div
      className={cn(
        'relative flex h-12 w-10 items-center justify-center overflow-hidden text-base font-semibold transition-colors',
        selected
          ? 'bg-black text-slate-50'
          : 'bg-white text-red-600'
      )}
    >
      <span
        className={cn(
          'pointer-events-none absolute left-0 right-0 top-0 border-t-[3px]',
          selected ? 'border-black' : 'border-red-500'
        )}
      />
      <span
        className={cn(
          'pointer-events-none absolute bottom-0 left-0 right-0 border-b-[3px]',
          selected ? 'border-black' : 'border-red-500'
        )}
      />
      <span
        className={cn(
          'pointer-events-none absolute left-0 top-0 h-[12px] border-l-[3px]',
          selected ? 'border-black' : 'border-red-500'
        )}
      />
      <span
        className={cn(
          'pointer-events-none absolute right-0 top-0 h-[12px] border-r-[3px]',
          selected ? 'border-black' : 'border-red-500'
        )}
      />
      <span
        className={cn(
          'pointer-events-none absolute bottom-0 left-0 h-[12px] border-l-[3px]',
          selected ? 'border-black' : 'border-red-500'
        )}
      />
      <span
        className={cn(
          'pointer-events-none absolute bottom-0 right-0 h-[12px] border-r-[3px]',
          selected ? 'border-black' : 'border-red-500'
        )}
      />
      <span className="relative z-10">{value}</span>
    </div>
  )
}

export default function MarkingSlipPage() {
  const navigate = useNavigate()
  const dragStartXRef = useRef(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [sets, setSets] = useState([])
  const [activeIndex, setActiveIndex] = useState(0)

  const slides = useMemo(() => toSlides(sets), [sets])
  const activeSlide = slides[activeIndex] ?? null

  useEffect(() => {
    async function load() {
      setLoading(true)
      setError('')
      try {
        const { data } = await getCurrentWeekNumbers()
        setSets(Array.isArray(data) ? data : [])
        setActiveIndex(0)
      } catch (err) {
        setError(err.response?.data?.message ?? '마킹 화면용 번호를 불러오지 못했습니다.')
      } finally {
        setLoading(false)
      }
    }

    load()
  }, [])

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'ArrowLeft') {
        setActiveIndex((current) => Math.max(current - 1, 0))
      }
      if (event.key === 'ArrowRight') {
        setActiveIndex((current) => Math.min(current + 1, slides.length - 1))
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [slides.length])

  function handleDragStart(clientX) {
    dragStartXRef.current = clientX
  }

  function handleDragEnd(clientX) {
    const startX = dragStartXRef.current
    dragStartXRef.current = null
    if (startX == null) return

    const diff = clientX - startX
    if (Math.abs(diff) < 40) return

    if (diff < 0) {
      setActiveIndex((current) => Math.min(current + 1, slides.length - 1))
    } else {
      setActiveIndex((current) => Math.max(current - 1, 0))
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex items-center gap-3 rounded-2xl bg-white px-5 py-4 shadow-sm ring-1 ring-slate-200">
          <Loader2 className="h-5 w-5 animate-spin text-red-700" />
          <span className="text-sm font-medium text-slate-600">마킹 화면을 준비하는 중입니다.</span>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">{error}</div>
        <Button type="button" variant="ghost" onClick={() => navigate('/make-week-num')} className="rounded-xl border border-slate-200">
          번호 생성 화면으로 돌아가기
        </Button>
      </div>
    )
  }

  if (slides.length === 0 || !activeSlide) {
    return (
      <div className="space-y-4">
        <div className="rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center shadow-sm">
          <p className="text-lg font-semibold text-slate-900">생성된 번호가 없습니다.</p>
          <p className="mt-2 text-sm text-slate-500">번호 생성 화면에서 이번 주 번호를 먼저 만들어야 마킹 보기를 사용할 수 있습니다.</p>
        </div>
        <Button type="button" onClick={() => navigate('/make-week-num')} className="rounded-xl bg-slate-900 text-white hover:bg-slate-800">
          번호 생성 화면으로 이동
        </Button>
      </div>
    )
  }

  const selectedNumbers = new Set(activeSlide.numbers)

  return (
    <div className="space-y-5 pb-10">
      <section className="overflow-hidden rounded-[28px] bg-[linear-gradient(135deg,_#f8fafc_0%,_#fff7ed_45%,_#fff1f2_100%)] px-5 py-6 shadow-xl ring-1 ring-red-100">
        <div className="flex items-start justify-between gap-4">
          <div>
            <Button
              type="button"
              variant="ghost"
              onClick={() => navigate('/make-week-num')}
              className="-ml-2 mb-3 h-10 rounded-xl px-3 text-red-700 hover:bg-red-50 hover:text-red-800"
            >
              <ChevronLeft className="mr-1 h-4 w-4" />
              이전
            </Button>
            <span className="inline-flex items-center gap-2 rounded-full bg-red-50 px-3 py-1 text-xs font-medium text-red-700 ring-1 ring-red-200">
              <Ticket className="h-3.5 w-3.5" />
              {formatWeekLabel(activeSlide)}
            </span>
            <h1 className="mt-3 text-2xl font-semibold tracking-tight text-slate-900">마킹 보기</h1>
            <p className="mt-2 max-w-md text-sm leading-6 text-slate-600">
              실제 로또 용지처럼 한 구역씩 확인하며 손으로 마킹할 수 있도록 구성했습니다.
            </p>
          </div>
          <div className="rounded-2xl bg-white/90 px-4 py-3 text-right shadow-sm ring-1 ring-red-100">
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">현재 위치</p>
            <p className="mt-1 text-lg font-semibold text-slate-900">{activeSlide.sheetNumber}장 {activeSlide.slotLabel}구역</p>
            <p className="mt-1 text-xs text-slate-500">{activeSlide.slideNumber} / {slides.length}세트</p>
          </div>
        </div>

        <div className="mt-5 flex items-center justify-between gap-3 rounded-2xl bg-white/80 px-4 py-3 ring-1 ring-red-100">
          <div className="flex items-center gap-2 text-sm text-slate-600">
            <MoveHorizontal className="h-4 w-4 text-red-600" />
            좌우 드래그로 다음 세트로 이동합니다.
          </div>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setActiveIndex((current) => Math.max(current - 1, 0))}
              disabled={activeIndex === 0}
              className="rounded-xl border border-red-100 bg-white text-red-700 hover:bg-red-50 disabled:opacity-40"
            >
              <ChevronLeft className="mr-1 h-4 w-4" />
              이전
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setActiveIndex((current) => Math.min(current + 1, slides.length - 1))}
              disabled={activeIndex === slides.length - 1}
              className="rounded-xl border border-red-100 bg-white text-red-700 hover:bg-red-50 disabled:opacity-40"
            >
              다음
              <ChevronRight className="ml-1 h-4 w-4" />
            </Button>
          </div>
        </div>
      </section>

      <section
        className="mx-auto max-w-[430px] touch-pan-y select-none"
        onTouchStart={(event) => handleDragStart(event.changedTouches[0].clientX)}
        onTouchEnd={(event) => handleDragEnd(event.changedTouches[0].clientX)}
        onPointerDown={(event) => handleDragStart(event.clientX)}
        onPointerUp={(event) => handleDragEnd(event.clientX)}
      >
        <article className="overflow-hidden rounded-[28px] border border-red-200 bg-white shadow-[0_24px_60px_rgba(127,29,29,0.12)]">
          <header className="flex items-stretch border-b border-red-200">
            <div className="flex w-16 items-center justify-center border-r border-red-200 bg-white text-2xl font-bold text-red-700">
              {activeSlide.slotLabel}
            </div>
            <div className="flex flex-1 items-center justify-center bg-red-700 px-4 py-5 text-center text-3xl font-semibold tracking-tight text-white">
              1,000원
            </div>
          </header>

          <div className="space-y-3 px-4 py-4">
            {NUMBER_ROWS.map((row, rowIndex) => (
              <div key={`row-${rowIndex}`} className="flex gap-1">
                {row.map((value) => (
                  <MarkCell key={value} value={value} selected={selectedNumbers.has(value)} />
                ))}
              </div>
            ))}
          </div>

          <footer className="flex items-end justify-between px-4 pb-5 pt-2">
            <div className="rounded-full bg-red-50 px-3 py-1 text-xs font-medium text-red-700 ring-1 ring-red-100">
              {activeSlide.methodCode} · {activeSlide.generatorCode}
            </div>
            <div className="rounded-[16px] border-2 border-red-700 px-4 py-2 text-2xl font-bold tracking-tight text-red-700">
              자동
            </div>
          </footer>
        </article>
      </section>
    </div>
  )
}
