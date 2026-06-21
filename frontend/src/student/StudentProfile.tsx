import { SignOut, Student } from '@phosphor-icons/react';
import type { User } from '../types';

export function StudentProfile({ user, onSignOut }: { user: User; onSignOut: () => void }) {
  return (
    <>
      <div className="section-header"><div><p className="eyebrow">Profile</p><h2>个人中心</h2></div></div>
      <section className="profile-panel">
        <div className="avatar"><Student size={42} /></div>
        <h2>{user.displayName}</h2><p>学生账号 · @{user.username}</p>
        <div className="profile-fields">
          <label>显示名<input disabled value={user.displayName} /></label>
          <label>邮箱<input disabled value={user.email || '未填写'} /></label>
          <label>手机号<input disabled value={user.phone || '未填写'} /></label>
        </div>
      </section>
      <section className="grouped-list profile-meta">
        <div className="list-row list-row--static"><span>用户名</span><strong>{user.username}</strong></div>
        <div className="list-row list-row--static"><span>角色</span><strong>STUDENT</strong></div>
        <div className="list-row list-row--static"><span>状态</span><strong>{user.status === 'ACTIVE' ? '正常' : user.status}</strong></div>
        <div className="list-row list-row--static"><span>最近登录</span><strong>{user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('zh-CN') : '暂无记录'}</strong></div>
      </section>
      <button type="button" className="sign-out-action" onClick={onSignOut}><SignOut size={22} />退出登录</button>
    </>
  );
}
