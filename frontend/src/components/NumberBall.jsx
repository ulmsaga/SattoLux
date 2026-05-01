import { cn } from '@/lib/utils'

function getNumberTone(value) {
  if (value >= 1 && value <= 10) {
    return 'bg-[#f5a000] text-white ring-[#d58800]'
  }
  if (value >= 11 && value <= 20) {
    return 'bg-[#3b78db] text-white ring-[#2f61b1]'
  }
  if (value >= 21 && value <= 30) {
    return 'bg-[#e05a3b] text-white ring-[#bc482c]'
  }
  if (value >= 31 && value <= 40) {
    return 'bg-[#7b828c] text-white ring-[#5f6670]'
  }
  return 'bg-[#2cc13a] text-white ring-[#21992d]'
}

export default function NumberBall({ value, emphasized = false, bonus = false, size = 'md' }) {
  const tone = bonus ? 'bg-[#8b5cf6] text-white ring-[#6d3fe0]' : getNumberTone(value)

  return (
    <span
      className={cn(
        'inline-flex rounded-full p-0.5 transition-shadow',
        emphasized && 'ring-2 ring-amber-400 ring-offset-2 ring-offset-white shadow-[0_0_8px_rgba(251,191,36,0.55)]'
      )}
      title={emphasized ? '강조 번호' : undefined}
    >
      <span
        className={cn(
          'flex items-center justify-center rounded-full font-semibold shadow-sm ring-1',
          size === 'sm' ? 'h-8 w-8 text-xs' : 'h-10 w-10 text-sm',
          tone
        )}
      >
        {value}
      </span>
    </span>
  )
}
