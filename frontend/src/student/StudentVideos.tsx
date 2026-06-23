import { useEffect, useState } from 'react';
import { PlayCircle, SpinnerGap, VideoCamera, WarningCircle, X } from '@phosphor-icons/react';
import { studentVideoApi } from '../api';
import type { StudentVideo, VideoAccessResponse } from '../types';

function formatDuration(seconds?: number | null) {
  if (!seconds || seconds <= 0) {
    return '--:--';
  }
  const minutes = Math.floor(seconds / 60);
  const remainder = Math.floor(seconds % 60);
  return `${minutes}:${String(remainder).padStart(2, '0')}`;
}

function formatDate(value?: string | null) {
  if (!value) {
    return '未记录';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '未记录';
  }
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

export function StudentVideos() {
  const [videos, setVideos] = useState<StudentVideo[]>([]);
  const [loading, setLoading] = useState(true);
  const [playingVideo, setPlayingVideo] = useState<StudentVideo | null>(null);
  const [access, setAccess] = useState<VideoAccessResponse | null>(null);
  const [playLoading, setPlayLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [playError, setPlayError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(async () => {
      setLoading(true);
      setError(null);
      try {
        const page = await studentVideoApi.list(1, 50);
        if (!active) return;
        setVideos(page.content);
      } catch (loadError) {
        if (active) setError(loadError instanceof Error ? loadError.message : '视频列表加载失败');
      } finally {
        if (active) setLoading(false);
      }
    });
    return () => { active = false; };
  }, []);

  const openPlayer = async (video: StudentVideo) => {
    setPlayingVideo(video);
    setAccess(null);
    setPlayError(null);
    setPlayLoading(true);
    try {
      setAccess(await studentVideoApi.play(video.id));
    } catch (loadError) {
      setPlayError(loadError instanceof Error ? loadError.message : '播放地址获取失败');
    } finally {
      setPlayLoading(false);
    }
  };

  const closePlayer = () => {
    setPlayingVideo(null);
    setAccess(null);
    setPlayError(null);
  };

  return (
    <>
      <div className="section-header">
        <div><p className="eyebrow">Video Library</p><h2>学习视频</h2></div>
        <span className="subtle-count">{videos.length} 个</span>
      </div>

      {loading && <section className="library-empty"><SpinnerGap className="student-spin-icon" size={32} /><h2>正在加载</h2><p>正在整理老师发布的视频。</p></section>}
      {error && <section className="library-empty"><WarningCircle size={32} /><h2>加载失败</h2><p>{error}</p></section>}
      {!loading && !error && videos.length === 0 && (
        <section className="library-empty"><VideoCamera size={32} /><h2>还没有学习视频</h2><p>老师发布视频后，会自动出现在这里。</p></section>
      )}

      {!loading && !error && videos.length > 0 && (
        <section className="student-video-list">
          {videos.map((video) => (
            <article key={video.id} className="student-video-card">
              <button type="button" className="student-video-card__poster" onClick={() => void openPlayer(video)} aria-label={`播放 ${video.title}`}>
                {video.coverUrl ? <img src={video.coverUrl} alt="" /> : <VideoCamera size={42} />}
                <span><PlayCircle size={34} weight="fill" /></span>
                <small>{formatDuration(video.durationSeconds)}</small>
              </button>
              <div className="student-video-card__body">
                <h3>{video.title}</h3>
                <p>{video.description || '这个视频还没有简介。'}</p>
                <div>
                  <span>{video.createdByDisplayName}</span>
                  <span>{formatDate(video.publishedAt)}</span>
                </div>
              </div>
            </article>
          ))}
        </section>
      )}

      {playingVideo && (
        <div className="student-player" role="dialog" aria-modal="true" aria-label={playingVideo.title}>
          <section className="student-player__panel">
            <div className="student-player__media">
              {playLoading ? (
                <div className="student-player__loading"><SpinnerGap size={34} />正在获取播放地址</div>
              ) : access ? (
                <video controls autoPlay playsInline poster={access.coverUrl ?? playingVideo.coverUrl ?? undefined} src={access.url} />
              ) : (
                <div className="student-player__loading"><WarningCircle size={34} />{playError || '无法播放这个视频'}</div>
              )}
              <button type="button" className="student-player__close" onClick={closePlayer} aria-label="关闭视频">
                <X size={20} />
              </button>
            </div>
            <div className="student-player__body">
              <h3>{playingVideo.title}</h3>
              <p>{playingVideo.description || '这个视频还没有简介。'}</p>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
