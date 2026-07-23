import { useEffect, useRef, useState } from 'react';
import {
  ArrowRight,
  BookOpen,
  CheckCircle,
  ClipboardText,
  PencilSimple,
  PlayCircle,
  PaperPlaneTilt,
  SpinnerGap,
  UsersThree,
  VideoCamera,
  WarningCircle,
  X,
} from '@phosphor-icons/react';
import { dictionaryApi, studentApi, studentVideoApi } from '../api';
import type { Classroom, ClassroomGroupFeedMessage, VideoAccessResponse } from '../types';

interface StudentClassroomsProps {
  onOpenDictionary: (dictionaryId: number) => void;
  onOpenStudyPlan: () => void;
}

const FEED_PAGE_SIZE = 20;
const POSTED_NOTICE_DURATION_MS = 2200;

function formatDateTime(value?: string | null) {
  if (!value) {
    return '刚刚';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '刚刚';
  }
  return date.toLocaleString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function messageLabel(message: ClassroomGroupFeedMessage) {
  if (message.messageType === 'DICTIONARY') return '词书';
  if (message.messageType === 'STUDY_PLAN') return '学习计划';
  if (message.messageType === 'VIDEO') return '视频';
  return '留言';
}

function resourceAction(message: ClassroomGroupFeedMessage) {
  if (message.messageType === 'DICTIONARY') {
    return { icon: BookOpen, label: '打开词书' };
  }
  if (message.messageType === 'STUDY_PLAN') {
    return { icon: ClipboardText, label: '查看今日任务' };
  }
  return { icon: PlayCircle, label: '播放视频' };
}

export function StudentClassrooms({ onOpenDictionary, onOpenStudyPlan }: StudentClassroomsProps) {
  const [classrooms, setClassrooms] = useState<Classroom[]>([]);
  const [selectedClassroomId, setSelectedClassroomId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ClassroomGroupFeedMessage[]>([]);
  const [classroomsLoading, setClassroomsLoading] = useState(true);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [messageError, setMessageError] = useState<string | null>(null);
  const [postedNotice, setPostedNotice] = useState<string | null>(null);
  const [composeOpen, setComposeOpen] = useState(false);
  const [text, setText] = useState('');
  const [posting, setPosting] = useState(false);
  const [playingMessage, setPlayingMessage] = useState<ClassroomGroupFeedMessage | null>(null);
  const [videoAccess, setVideoAccess] = useState<VideoAccessResponse | null>(null);
  const [videoLoading, setVideoLoading] = useState(false);
  const [videoError, setVideoError] = useState<string | null>(null);
  const [isVideoLandscape, setIsVideoLandscape] = useState(false);
  const composerTextAreaRef = useRef<HTMLTextAreaElement>(null);
  const completedVideoKeysRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    let active = true;
    void Promise.resolve().then(async () => {
      setClassroomsLoading(true);
      setError(null);
      try {
        const nextClassrooms = await studentApi.getMyClassrooms();
        if (!active) return;
        setClassrooms(nextClassrooms);
        setSelectedClassroomId((current) => current ?? nextClassrooms[0]?.id ?? null);
      } catch (loadError) {
        if (active) setError(loadError instanceof Error ? loadError.message : '班级加载失败');
      } finally {
        if (active) setClassroomsLoading(false);
      }
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!selectedClassroomId) {
      setMessages([]);
      return undefined;
    }
    let active = true;
    void Promise.resolve().then(async () => {
      setMessagesLoading(true);
      setMessageError(null);
      try {
        const page = await studentApi.listClassroomGroupFeedMessages(selectedClassroomId, {
          page: 1,
          size: FEED_PAGE_SIZE,
        });
        if (!active) return;
        setMessages(page.content);
      } catch (loadError) {
        if (active) setMessageError(loadError instanceof Error ? loadError.message : '班级群消息加载失败');
      } finally {
        if (active) setMessagesLoading(false);
      }
    });
    return () => { active = false; };
  }, [selectedClassroomId]);

  useEffect(() => {
    if (!postedNotice) {
      return undefined;
    }
    const timer = window.setTimeout(() => setPostedNotice(null), POSTED_NOTICE_DURATION_MS);
    return () => window.clearTimeout(timer);
  }, [postedNotice]);

  useEffect(() => {
    if (!playingMessage) {
      return undefined;
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closeVideo();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [playingMessage]);

  useEffect(() => {
    if (!composeOpen) {
      return undefined;
    }
    const focusTimer = window.setTimeout(() => composerTextAreaRef.current?.focus(), 120);
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !posting) {
        setComposeOpen(false);
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      window.clearTimeout(focusTimer);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [composeOpen, posting]);

  const selectedClassroom = classrooms.find((classroom) => classroom.id === selectedClassroomId) ?? null;

  const refreshMessages = async () => {
    if (!selectedClassroomId) return;
    const page = await studentApi.listClassroomGroupFeedMessages(selectedClassroomId, {
      page: 1,
      size: FEED_PAGE_SIZE,
    });
    setMessages(page.content);
  };

  const postText = async () => {
    const content = text.trim();
    if (!selectedClassroomId || !content) {
      return;
    }
    setPosting(true);
    setMessageError(null);
    setPostedNotice(null);
    try {
      await studentApi.createClassroomGroupFeedTextMessage(selectedClassroomId, { content });
      setText('');
      setPostedNotice('已发布到班级');
      await refreshMessages();
      setComposeOpen(false);
    } catch (postError) {
      setMessageError(postError instanceof Error ? postError.message : '留言发布失败');
    } finally {
      setPosting(false);
    }
  };

  const openDictionary = async (message: ClassroomGroupFeedMessage) => {
    if (!message.resourceId) return;
    setMessageError(null);
    try {
      await dictionaryApi.getById(message.resourceId);
      onOpenDictionary(message.resourceId);
    } catch (openError) {
      setMessageError(openError instanceof Error ? openError.message : '词书暂时无法打开');
    }
  };

  const openVideo = async (message: ClassroomGroupFeedMessage) => {
    if (!message.resourceId || !selectedClassroomId) return;
    setPlayingMessage(message);
    setVideoAccess(null);
    setVideoError(null);
    setIsVideoLandscape(false);
    setVideoLoading(true);
    try {
      setVideoAccess(await studentVideoApi.playFromClassroomFeed(selectedClassroomId, message.resourceId));
    } catch (openError) {
      setVideoError(openError instanceof Error ? openError.message : '视频暂时无法播放');
    } finally {
      setVideoLoading(false);
    }
  };

  const closeVideo = () => {
    setPlayingMessage(null);
    setVideoAccess(null);
    setVideoError(null);
    setIsVideoLandscape(false);
  };

  const completeVideoPlayback = async () => {
    if (!selectedClassroomId || !playingMessage?.resourceId) return;
    const videoKey = `${selectedClassroomId}:${playingMessage.resourceId}`;
    if (completedVideoKeysRef.current.has(videoKey)) return;
    completedVideoKeysRef.current.add(videoKey);
    try {
      await studentVideoApi.completeFromClassroomFeed(selectedClassroomId, playingMessage.resourceId);
    } catch {
      completedVideoKeysRef.current.delete(videoKey);
    }
  };

  const openResource = (message: ClassroomGroupFeedMessage) => {
    if (message.messageType === 'DICTIONARY') {
      void openDictionary(message);
      return;
    }
    if (message.messageType === 'STUDY_PLAN') {
      onOpenStudyPlan();
      return;
    }
    void openVideo(message);
  };

  return (
    <>
      <div className="section-header">
        <div><p className="eyebrow">Classrooms</p><h2>我的班级</h2></div>
      </div>

      {classroomsLoading && (
        <section className="library-empty"><SpinnerGap className="student-spin-icon" size={32} /><h2>正在加载</h2><p>正在同步你的班级。</p></section>
      )}
      {error && <section className="library-empty"><WarningCircle size={32} /><h2>加载失败</h2><p>{error}</p></section>}
      {!classroomsLoading && !error && classrooms.length === 0 && (
        <section className="library-empty"><UsersThree size={32} /><h2>还没有班级</h2><p>老师把你加入班级后，班级消息会出现在这里。</p></section>
      )}

      {!classroomsLoading && !error && classrooms.length > 0 && (
        <>
          {classrooms.length > 1 && (
            <section className="student-classroom-strip" aria-label="班级列表">
              {classrooms.map((classroom) => (
                <button
                  type="button"
                  key={classroom.id}
                  className={classroom.id === selectedClassroomId ? 'is-active' : ''}
                  aria-pressed={classroom.id === selectedClassroomId}
                  onClick={() => setSelectedClassroomId(classroom.id)}
                >
                  <strong>{classroom.name}</strong>
                  <span>{classroom.teacherName || '负责老师'} · {classroom.studentCount} 人</span>
                </button>
              ))}
            </section>
          )}

          {selectedClassroom && (
            <section className="student-classroom-panel">
              <div className="student-classroom-panel__header">
                <div>
                  <p className="eyebrow">Group Feed</p>
                  <h2>{selectedClassroom.name}</h2>
                  <span>{selectedClassroom.description || '班级群消息流'}</span>
                </div>
                <button
                  type="button"
                  className="student-compose-trigger"
                  onClick={() => {
                    setMessageError(null);
                    setComposeOpen(true);
                  }}
                >
                  <PencilSimple size={18} />
                  <span>发布消息</span>
                </button>
              </div>

              {messageError && <p className="inline-error" role="alert">{messageError}</p>}
              {postedNotice && <p className="student-posted-notice" role="status"><CheckCircle size={18} />{postedNotice}</p>}
              {messagesLoading && <p className="inline-note">正在加载班级群消息...</p>}
              {!messagesLoading && messages.length === 0 && <p className="inline-note">这个班级还没有群消息。</p>}

              <div className="student-feed-list">
                {messages.map((message) => {
                  const action = resourceAction(message);
                  const ResourceIcon = action.icon;
                  return (
                    <article key={message.id} className={`student-feed-message student-feed-message--${message.messageType.toLowerCase().replace('_', '-')}`}>
                      <div className="student-feed-message__meta">
                        <span>{messageLabel(message)}</span>
                        <strong>{message.authorName}</strong>
                        <time>{formatDateTime(message.createdAt)}</time>
                      </div>
                      {message.messageType === 'TEXT' ? (
                        <p>{message.content}</p>
                      ) : (
                        <div className="student-feed-resource">
                          <div>
                            <h3>{message.resourceTitle || '学习资源'}</h3>
                            {message.resourceSummary && <p>{message.resourceSummary}</p>}
                          </div>
                          <button
                            type="button"
                            disabled={videoLoading && playingMessage?.id === message.id}
                            onClick={() => openResource(message)}
                          >
                            <ResourceIcon size={18} />
                            {videoLoading && playingMessage?.id === message.id ? '准备播放' : action.label}
                            <ArrowRight size={16} />
                          </button>
                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            </section>
          )}
        </>
      )}

      {composeOpen && (
        <div className="student-compose-layer" role="dialog" aria-modal="true" aria-label="发布班级留言">
          <button
            type="button"
            className="student-compose-layer__scrim"
            aria-label="关闭发布留言"
            disabled={posting}
            onClick={() => setComposeOpen(false)}
          />
          <section className="student-compose-sheet">
            <div className="student-compose-sheet__header">
              <div>
                <p className="eyebrow">Message</p>
                <h2>发布班级留言</h2>
              </div>
              <button
                type="button"
                className="student-compose-sheet__close"
                aria-label="关闭"
                disabled={posting}
                onClick={() => setComposeOpen(false)}
              >
                <X size={18} />
              </button>
            </div>
            <textarea
              ref={composerTextAreaRef}
              aria-label="班级留言"
              placeholder="给班级发一条留言"
              value={text}
              onChange={(event) => setText(event.target.value)}
            />
            {messageError && <p className="inline-error" role="alert">{messageError}</p>}
            <div className="student-compose-sheet__actions">
              <button type="button" disabled={posting} onClick={() => setComposeOpen(false)}>取消</button>
              <button type="button" disabled={posting || !text.trim()} onClick={() => void postText()}>
                <PaperPlaneTilt size={18} />
                {posting ? '发布中' : '发布'}
              </button>
            </div>
          </section>
        </div>
      )}

      {playingMessage && (
        <div className="student-player" role="dialog" aria-modal="true" aria-label={playingMessage.resourceTitle || '学习视频'} onClick={closeVideo}>
          <section
            className={`student-player__panel ${isVideoLandscape ? 'student-player__panel--landscape' : ''}`}
            onClick={(event) => event.stopPropagation()}
          >
            <div className="student-player__media">
              {videoLoading ? (
                <div className="student-player__loading"><SpinnerGap size={34} />正在获取播放地址</div>
              ) : videoAccess ? (
                <video
                  controls
                  autoPlay
                  playsInline
                  poster={videoAccess.coverUrl ?? undefined}
                  src={videoAccess.url}
                  onEnded={() => void completeVideoPlayback()}
                />
              ) : (
                <div className="student-player__loading"><VideoCamera size={34} />{videoError || '无法播放这个视频'}</div>
              )}
              <button
                type="button"
                className="student-player__orientation"
                onClick={() => setIsVideoLandscape((current) => !current)}
                aria-label={isVideoLandscape ? '切换为竖屏播放' : '切换为横屏播放'}
              >
                {isVideoLandscape ? '竖屏' : '横屏'}
              </button>
              <button type="button" className="student-player__close" onClick={closeVideo} aria-label="关闭视频">
                <X size={20} />
              </button>
            </div>
            <div className="student-player__body">
              <h3>{playingMessage.resourceTitle || '学习视频'}</h3>
              <p>{playingMessage.resourceSummary || '这个视频还没有简介。'}</p>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
