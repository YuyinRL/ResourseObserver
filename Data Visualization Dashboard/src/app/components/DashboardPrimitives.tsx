import React, { useEffect, useRef, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';
import { X } from 'lucide-react';

export const Card: React.FC<{ children: React.ReactNode; className?: string }> = ({
  children,
  className = '',
}) => (
  <div className={`bg-slate-900/80 border border-slate-800 rounded-xl backdrop-blur-sm shadow-xl ${className}`}>
    {children}
  </div>
);

export const ProgressBar: React.FC<{
  value: number;
  max: number;
  colorClass?: string;
  height?: string;
}> = ({ value, max, colorClass = 'bg-cyan-500', height = 'h-2' }) => {
  const pct = max <= 0 ? 0 : Math.min(100, Math.max(0, (value / max) * 100));
  return (
    <div className={`w-full ${height} bg-slate-800 rounded-full overflow-hidden shadow-inner`}>
      <div className={`h-full ${colorClass} transition-all duration-500`} style={{ width: `${pct}%` }} />
    </div>
  );
};

export const SectionHeader: React.FC<{
  title: string;
  subtitle?: string;
  icon?: LucideIcon;
  action?: React.ReactNode;
}> = ({ title, subtitle, icon: Icon, action }) => (
  <div className="flex items-start justify-between gap-4">
    <div>
      <h3 className="text-sm font-bold text-slate-300 uppercase tracking-widest flex items-center gap-2">
        {Icon ? <Icon size={16} className="text-cyan-400" /> : null}
        {title}
      </h3>
      {subtitle ? (
        <p className="text-[11px] text-slate-500 mt-1 uppercase tracking-[0.18em]">{subtitle}</p>
      ) : null}
    </div>
    {action}
  </div>
);

const HOVER_CARD_LAYER_ID = 'resourceobserver-hover-card-layer';

function getHoverCardLayer(): HTMLElement | null {
  if (typeof document === 'undefined') return null;
  let layer = document.getElementById(HOVER_CARD_LAYER_ID);
  if (layer) return layer;
  layer = document.createElement('div');
  layer.id = HOVER_CARD_LAYER_ID;
  layer.style.position = 'fixed';
  layer.style.inset = '0';
  layer.style.zIndex = '2147483647';
  layer.style.pointerEvents = 'none';
  layer.style.isolation = 'isolate';
  document.body.appendChild(layer);
  return layer;
}

/**
 * 鼠标悬浮门户卡片：把 tooltip 通过 React Portal 渲染到独立顶层容器，
 * 用 fixed 定位和最高 z-index 避免父容器裁切或被其它卡片遮挡。
 */
export const HoverCard: React.FC<{
  trigger: (props: {
    ref: React.Ref<HTMLDivElement>;
    onMouseEnter: () => void;
    onMouseLeave: () => void;
  }) => React.ReactNode;
  children: () => React.ReactNode;
  width?: number;
  className?: string;
}> = ({ trigger, children, width = 240, className = '' }) => {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null);

  const measure = () => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const margin = 8;
    const estHeight = 160;
    const flip = r.bottom + estHeight + margin > window.innerHeight;
    let left = r.left;
    if (left + width + margin > window.innerWidth) left = window.innerWidth - width - margin;
    if (left < margin) left = margin;
    const top = flip ? Math.max(margin, r.top - estHeight - margin) : r.bottom + margin;
    setPos({ left, top });
  };

  useEffect(() => {
    if (!pos) return;
    const close = () => setPos(null);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [pos]);

  return (
    <>
      {trigger({ ref, onMouseEnter: measure, onMouseLeave: () => setPos(null) })}
      {pos
        ? createPortal(
            <div
              style={{ position: 'fixed', left: pos.left, top: pos.top, width, zIndex: 2147483647 }}
              className={`pointer-events-none rounded-xl border border-slate-600 bg-slate-950 p-3 shadow-[0_20px_60px_rgba(0,0,0,0.65)] ring-1 ring-cyan-500/20 ${className}`}
            >
              {children()}
            </div>,
            getHoverCardLayer() ?? ref.current?.ownerDocument.body ?? document.body,
          )
        : null}
    </>
  );
};

const toneMap = {
  cyan: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
  amber: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
  emerald: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  rose: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
  blue: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  slate: 'bg-slate-800 text-slate-300 border-slate-700',
} as const;

export const StatusPill: React.FC<{
  tone?: keyof typeof toneMap;
  children: React.ReactNode;
}> = ({ tone = 'slate', children }) => (
  <span className={`inline-flex items-center gap-1 rounded-md border px-2 py-1 text-[10px] font-bold uppercase tracking-widest ${toneMap[tone]}`}>
    {children}
  </span>
);

export const KpiCard: React.FC<{
  label: string;
  value: string;
  suffix?: string;
  helper?: string;
  icon: LucideIcon;
  tone?: keyof typeof toneMap;
  onClick?: () => void;
}> = ({ label, value, suffix, helper, icon: Icon, tone = 'cyan', onClick }) => {
  // 对齐 ModernUI 的 hover/press 缩放反馈与点击下钻行为
  const interactive = Boolean(onClick);
  const base = 'p-5 flex flex-col gap-4 relative overflow-hidden group';
  const hover = interactive
    ? 'cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:border-cyan-500/30 hover:shadow-[0_0_0_1px_rgba(34,211,238,0.25)] active:scale-[0.985]'
    : '';
  return (
    <Card className={`${base} ${hover}`.trim()}>
      <button
        type="button"
        onClick={onClick}
        disabled={!interactive}
        className={`absolute inset-0 z-20 ${interactive ? 'cursor-pointer' : 'cursor-default pointer-events-none'}`}
        aria-label={interactive ? label : undefined}
      />
      <div className={`absolute top-0 right-0 h-28 w-28 rounded-full blur-2xl -mr-8 -mt-8 opacity-80 ${toneMap[tone].split(' ')[0].replace('/10', '/5')}`} />
      <div className="flex items-start justify-between gap-4 relative z-10">
        <div className="min-w-0">
          <p className="text-sm text-slate-500 font-medium uppercase tracking-wider">{label}</p>
          <div className="mt-1 flex items-baseline gap-1">
            <span className="text-2xl font-bold text-white font-mono">{value}</span>
            {suffix ? <span className="text-sm text-slate-500 font-medium">{suffix}</span> : null}
          </div>
        </div>
        <div className={`rounded-xl border p-2.5 shadow-inner ${toneMap[tone]}`}>
          <Icon size={20} />
        </div>
      </div>
      {helper ? <p className="text-xs font-semibold text-slate-500 relative z-10">{helper}</p> : null}
    </Card>
  );
};

export const SegmentedControl: React.FC<{
  value: string;
  onChange: (value: string) => void;
  items: Array<{ value: string; label: string }>;
}> = ({ value, onChange, items }) => (
  <div className="inline-flex rounded-xl border border-slate-800 bg-slate-950/90 p-1 shadow-inner">
    {items.map((item) => (
      <button
        key={item.value}
        onClick={() => onChange(item.value)}
        className={`rounded-lg px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.2em] transition-all ${
          value === item.value
            ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20'
            : 'text-slate-500 hover:text-slate-300'
        }`}
      >
        {item.label}
      </button>
    ))}
  </div>
);

export const IconSegmentedControl: React.FC<{
  value: string;
  onChange: (value: string) => void;
  items: Array<{ value: string; label: string; icon: LucideIcon }>;
}> = ({ value, onChange, items }) => (
  <div className="inline-flex rounded-xl border border-slate-800 bg-slate-950/90 p-1 shadow-inner">
    {items.map((item) => {
      const Icon = item.icon;
      const active = value === item.value;
      return (
        <button
          key={item.value}
          onClick={() => onChange(item.value)}
          title={item.label}
          aria-label={item.label}
          className={`rounded-lg p-2 transition-all ${
            active
              ? 'border border-cyan-500/20 bg-cyan-500/10 text-cyan-400'
              : 'text-slate-500 hover:text-slate-300'
          }`}
        >
          <Icon size={15} />
        </button>
      );
    })}
  </div>
);

// ========================================================================
// ModernDialog —— 对齐游戏内 ModernUI 弹窗形态
// 结构：半透明遮罩 + 居中面板；面板含标题栏（标题 + ✕）与可滚动正文，
// 正文顶部/底部带渐隐遮罩；入场 scale 0.8→1 + 透明度渐入（overshoot 回弹），
// 关闭 scale 1→0.85 + 渐出。点击遮罩或 ✕ 或按下 Esc 键关闭。
// ========================================================================
export const ModernDialog: React.FC<{
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  width?: number;
  height?: number;
}> = ({ open, onClose, title, children, width = 420, height = 340 }) => {
  const bodyRef = useRef<HTMLDivElement>(null);
  const [edges, setEdges] = useState({ top: false, bottom: false });

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', handler);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  const updateEdges = () => {
    const el = bodyRef.current;
    if (!el) return;
    const atTop = el.scrollTop > 4;
    const atBottom = el.scrollTop + el.clientHeight < el.scrollHeight - 4;
    setEdges((s) => (s.top === atTop && s.bottom === atBottom ? s : { top: atTop, bottom: atBottom }));
  };

  useEffect(() => {
    if (open) {
      // 等待面板挂载后测量滚动容器
      requestAnimationFrame(updateEdges);
    }
  }, [open, children]);

  if (typeof document === 'undefined') return null;

  return createPortal(
    <AnimatePresence>
      {open ? (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center p-5"
          role="dialog"
          aria-modal="true"
          aria-label={title}
        >
          {/* 半透明遮罩，对应 ModernUI 0xAA04070E */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="absolute inset-0 bg-[rgb(4,7,14)]/70 backdrop-blur-sm"
            onClick={onClose}
          />
          {/* 对话框面板 */}
          <motion.div
            initial={{ opacity: 0, scale: 0.8 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.85 }}
            transition={{
              opacity: { duration: 0.2 },
              scale: { type: 'spring', stiffness: 360, damping: 18, mass: 0.7 },
            }}
            style={{ width, maxWidth: '100%', height, maxHeight: '100%' }}
            className="relative flex flex-col overflow-hidden rounded-xl border border-slate-700/80 bg-slate-950/95 shadow-[0_30px_80px_-20px_rgba(0,0,0,0.7)] backdrop-blur-md"
          >
            {/* 标题栏 */}
            <div className="flex items-center gap-3 border-b border-slate-800/80 bg-slate-900/60 px-4 py-3">
              <h3 className="flex-1 truncate text-sm font-bold uppercase tracking-[0.18em] text-white">{title}</h3>
              <button
                type="button"
                onClick={onClose}
                className="flex h-7 w-7 items-center justify-center rounded-md border border-slate-700/70 bg-slate-900/80 text-slate-400 transition-all hover:scale-105 hover:border-cyan-500/40 hover:text-cyan-300 active:scale-95"
                aria-label="Close"
              >
                <X size={14} />
              </button>
            </div>
            {/* 可滚动正文 + 上下渐隐遮罩 */}
            <div className="relative flex-1 overflow-hidden">
              <div
                ref={bodyRef}
                onScroll={updateEdges}
                className="h-full overflow-y-auto px-4 py-3 text-slate-300"
              >
                {children}
              </div>
              {/* 顶部渐隐 */}
              <div
                className={`pointer-events-none absolute left-0 right-0 top-0 h-6 bg-gradient-to-b from-slate-950/95 to-transparent transition-opacity duration-200 ${edges.top ? 'opacity-100' : 'opacity-0'}`}
              />
              {/* 底部渐隐 */}
              <div
                className={`pointer-events-none absolute left-0 right-0 bottom-0 h-6 bg-gradient-to-t from-slate-950/95 to-transparent transition-opacity duration-200 ${edges.bottom ? 'opacity-100' : 'opacity-0'}`}
              />
            </div>
          </motion.div>
        </div>
      ) : null}
    </AnimatePresence>,
    document.body,
  );
};

// 弹窗正文中通用的分节标题
export const DialogSectionTitle: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <h4 className="text-[10px] font-bold uppercase tracking-[0.2em] text-slate-500">{children}</h4>
);

// 弹窗正文中通用的 label / value 行
// 同时兼容两种用法：
//   1) <DialogRow label="..." value={...} tone="cyan" />
//   2) <DialogRow>{任意子节点 flex 排布}</DialogRow>
export const DialogRow: React.FC<{
  label?: string;
  value?: React.ReactNode;
  tone?: keyof typeof toneMap;
  children?: React.ReactNode;
}> = ({ label, value, tone, children }) => {
  if (children !== undefined) {
    return (
      <div className="flex items-center justify-between gap-3 py-1.5">{children}</div>
    );
  }
  return (
    <div className="flex items-center justify-between gap-4 py-1.5">
      <span className="text-xs font-medium uppercase tracking-wider text-slate-500">{label}</span>
      <span className={`font-mono text-sm font-bold ${tone ? toneMap[tone].split(' ')[1] : 'text-white'}`}>{value}</span>
    </div>
  );
};

export const DialogDivider: React.FC = () => <div className="my-3 h-px bg-slate-800/80" />;
