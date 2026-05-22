/**
 * Login gate: blocks the app until the user has a valid Web token.
 * If `whoami` succeeds, renders the children. Otherwise shows instructions
 * for obtaining a link from the in-game terminal.
 */
import React, { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { clearToken, getToken, setUnauthorizedHandler, type WhoAmIResponse } from '../lib/auth';
import { useI18n } from '../lib/i18n';

interface Props {
  children: React.ReactNode;
}

type State =
  | { kind: 'loading' }
  | { kind: 'authed'; me: WhoAmIResponse }
  | { kind: 'unauthed'; reason: string };

export function LoginGate({ children }: Props) {
  const [state, setState] = useState<State>({ kind: 'loading' });
  const { t, language, setLanguage } = useI18n();

  useEffect(() => {
    let cancelled = false;
    const check = async () => {
      try {
        const me = await api.whoami();
        if (!cancelled) setState({ kind: 'authed', me });
      } catch (e) {
        if (cancelled) return;
        const msg = e instanceof Error ? e.message : String(e);
        setState({ kind: 'unauthed', reason: msg });
      }
    };
    check();

    setUnauthorizedHandler(() => {
      setState({ kind: 'unauthed', reason: 'unauthorized' });
    });
    return () => {
      cancelled = true;
      setUnauthorizedHandler(null);
    };
  }, []);

  if (state.kind === 'loading') {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-[#04070e] text-slate-300">
        <div className="text-sm">{t('app.gate.loading')}</div>
      </div>
    );
  }

  if (state.kind === 'unauthed') {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-[#04070e] text-slate-200">
        <div className="relative max-w-md rounded-lg border border-slate-700/60 bg-slate-900/70 p-8 shadow-xl">
          <button
            type="button"
            onClick={() => setLanguage(language === 'zh' ? 'en' : 'zh')}
            className="absolute right-3 top-3 rounded border border-slate-600/60 bg-slate-800/60 px-2 py-1 text-xs text-slate-300 hover:bg-slate-700/60"
            aria-label={t('app.language.aria')}
          >
            {t('app.gate.languageSwitch')}
          </button>
          <h1 className="mb-3 text-xl font-semibold text-cyan-300">{t('app.gate.title')}</h1>
          <p className="mb-4 text-sm leading-relaxed text-slate-300">
            {t('app.gate.intro')}
          </p>
          <ol className="mb-4 list-decimal pl-5 text-sm leading-relaxed text-slate-300 space-y-1">
            <li>{t('app.gate.step1')}</li>
            <li>{t('app.gate.step2')}</li>
            <li>{t('app.gate.step3')}</li>
            <li>{t('app.gate.step4')}</li>
          </ol>
          {getToken() && (
            <p className="mb-3 rounded border border-rose-500/40 bg-rose-500/10 p-2 text-xs text-rose-300">
              {t('app.gate.invalidToken')}
            </p>
          )}
          <button
            onClick={() => {
              clearToken();
              window.location.reload();
            }}
            className="rounded bg-cyan-500/10 px-3 py-1.5 text-sm text-cyan-300 hover:bg-cyan-500/20"
          >
            {t('app.gate.forgetToken')}
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
