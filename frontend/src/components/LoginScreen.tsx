import { useState } from 'react';
import { Eye, EyeSlash } from '@phosphor-icons/react';
import type { FamousQuote } from '../types';

const INSIGHT_COPY = [
  '管理员进入后台总览与系统配置',
  '教师进入后台管理班级、词书和学习计划',
  '学生进入专属学习台完成每日任务',
];

interface LoginScreenProps {
  loading: boolean;
  error: string | null;
  quote: FamousQuote;
  onSubmit: (username: string, password: string) => Promise<void>;
}

export function LoginScreen({ loading, error, quote, onSubmit }: LoginScreenProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    await onSubmit(username.trim(), password);
  };

  return (
    <div className="auth-shell">
      <section className="auth-shell__hero">
        <div className="auth-shell__hero-copy">
          <p className="auth-shell__eyebrow">Word Atelier Access</p>
          <h1 className="auth-shell__title">统一入口，自动进入你的工作台。</h1>
          <p className="auth-shell__subtitle">
            管理员和老师登录后进入后台工作台，学生登录后进入学习台。账号角色由系统自动识别。
          </p>
        </div>

        <div className="auth-shell__insights">
          {INSIGHT_COPY.map((item, index) => (
            <div key={item} className="auth-shell__insight">
              <span className="auth-shell__insight-index">0{index + 1}</span>
              <span>{item}</span>
            </div>
          ))}
        </div>

        <div className="auth-shell__quote">
          <p className="auth-shell__quote-text">“{quote.text}”</p>
          <p className="auth-shell__quote-translation">{quote.translation}</p>
          <p className="auth-shell__quote-author">{quote.author}</p>
        </div>
      </section>

      <section className="auth-shell__panel">
        <div className="auth-shell__panel-header">
          <div>
            <p className="auth-shell__eyebrow auth-shell__eyebrow--panel">Sign In</p>
            <h2 className="auth-shell__panel-title">登录 Word Atelier</h2>
            <p className="auth-shell__panel-subtitle">一个登录页，按角色自动跳转。</p>
          </div>
          <span className="auth-shell__badge">Unified</span>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-form__field">
            <span className="form__label">用户名</span>
            <input
              className="form__input"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="输入用户名"
              autoComplete="username"
              disabled={loading}
              autoFocus
            />
          </label>

          <label className="auth-form__field">
            <span className="form__label">密码</span>
            <span className="auth-form__password">
              <input
                className="form__input auth-form__password-input"
                type={passwordVisible ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="输入密码"
                autoComplete="current-password"
                disabled={loading}
              />
              <button
                type="button"
                className="auth-form__password-toggle"
                aria-label={passwordVisible ? '隐藏密码' : '显示密码'}
                aria-pressed={passwordVisible}
                onClick={() => setPasswordVisible((visible) => !visible)}
                disabled={loading}
              >
                {passwordVisible ? <EyeSlash size={18} weight="bold" /> : <Eye size={18} weight="bold" />}
              </button>
            </span>
          </label>

          {error && <div className="form__error">{error}</div>}

          <button className="btn btn--primary auth-form__submit" type="submit" disabled={loading}>
            {loading ? '正在登录...' : '登录并进入对应主页'}
          </button>
        </form>

        <div className="auth-shell__demo">
          <p className="auth-shell__demo-label">默认测试账号</p>
          <p className="auth-shell__demo-value">admin / admin123456</p>
        </div>
      </section>
    </div>
  );
}
