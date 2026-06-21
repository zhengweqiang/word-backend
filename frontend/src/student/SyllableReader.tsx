import { useEffect, useMemo, useRef, useState } from 'react';
import { PlayCircle, SpeakerHigh } from '@phosphor-icons/react';
import type { SyllableDetail } from '../types';
import { SyllablePlaybackController } from './syllable-playback';
import type { PronunciationAccent } from './student-workspace-state';

interface SyllableReaderProps {
  word: string;
  detail?: SyllableDetail | null;
  onInteraction?: () => void;
}

const ACCENT_STORAGE_KEY = 'student-pronunciation-accent';

function storedAccent(): PronunciationAccent {
  return localStorage.getItem(ACCENT_STORAGE_KEY) === 'UK' ? 'UK' : 'US';
}

export function SyllableReader({ word, detail, onInteraction }: SyllableReaderProps) {
  const [accent, setAccent] = useState<PronunciationAccent>(storedAccent);
  const [playing, setPlaying] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const controller = useMemo(() => new SyllablePlaybackController({
    cancel: () => {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current = null;
      }
      window.speechSynthesis?.cancel();
      setPlaying(null);
    },
    playAudio: (url) => new Promise<void>((resolve, reject) => {
      const audio = new Audio(url);
      audioRef.current = audio;
      audio.onended = () => {
        audioRef.current = null;
        resolve();
      };
      audio.onerror = () => {
        audioRef.current = null;
        reject(new Error('Audio playback failed'));
      };
      void audio.play().catch(reject);
    }),
    speak: (text, selectedAccent, rate) => new Promise<void>((resolve, reject) => {
      if (!window.speechSynthesis || typeof SpeechSynthesisUtterance === 'undefined') {
        reject(new Error('Speech synthesis is unavailable'));
        return;
      }
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = selectedAccent === 'UK' ? 'en-GB' : 'en-US';
      utterance.rate = rate;
      utterance.onend = () => resolve();
      utterance.onerror = () => reject(new Error('Speech synthesis failed'));
      window.speechSynthesis.speak(utterance);
    }),
  }), []);

  useEffect(() => () => controller.cancel(), [controller]);

  if (!detail?.segments.length) {
    return null;
  }

  const runPlayback = async (label: string, play: () => Promise<void>) => {
    onInteraction?.();
    setPlaying(label);
    try {
      await play();
    } finally {
      setPlaying(null);
    }
  };

  const selectAccent = (nextAccent: PronunciationAccent) => {
    controller.cancel();
    setAccent(nextAccent);
    localStorage.setItem(ACCENT_STORAGE_KEY, nextAccent);
    onInteraction?.();
  };

  return (
    <section className="syllable-reader" aria-labelledby="syllable-reader-title">
      <div className="syllable-reader__header">
        <div>
          <p className="eyebrow">Syllable Reading</p>
          <h3 id="syllable-reader-title">按音节拆读</h3>
        </div>
        <div className="accent-switch" aria-label="发音口音">
          {(['US', 'UK'] as const).map((item) => (
            <button
              key={item}
              type="button"
              className={accent === item ? 'accent-switch__button accent-switch__button--active' : 'accent-switch__button'}
              aria-pressed={accent === item}
              onClick={() => selectAccent(item)}
            >
              {item === 'US' ? '美音' : '英音'}
            </button>
          ))}
        </div>
      </div>

      <div className="syllable-reader__word" aria-label={`${word} 的音节拆分`}>
        {detail.segments.map((segment, index) => (
          <span key={`${segment.text}-${index}`}>
            <strong>{segment.text}</strong>
            {index < detail.segments.length - 1 && <i aria-hidden="true">·</i>}
          </span>
        ))}
      </div>

      <div className="syllable-reader__segments">
        {detail.segments.map((segment, index) => {
          const phonetic = accent === 'UK' ? segment.ukPhonetic : segment.usPhonetic;
          return (
            <button
              type="button"
              className="syllable-segment"
              key={`${segment.text}-${index}`}
              onClick={() => void runPlayback(segment.text, () => controller.playSegment(segment, accent, setPlaying))}
            >
              <strong>{segment.text}</strong>
              <span>{phonetic || '点击听音'}</span>
              <span className="syllable-segment__action"><PlayCircle size={18} />播放</span>
            </button>
          );
        })}
      </div>

      <div className="syllable-reader__actions">
        <button type="button" onClick={() => void runPlayback(word, () => controller.playWord(word, accent, setPlaying))}>
          <SpeakerHigh size={20} />整词朗读
        </button>
        <button type="button" onClick={() => void runPlayback('慢速拼读', () => controller.playSequence(word, detail.segments, accent, setPlaying))}>
          <PlayCircle size={20} />慢速拼读
        </button>
      </div>
      <p className="student-sr-status" aria-live="polite">{playing ? `正在播放：${playing}` : ''}</p>
    </section>
  );
}
