import type { SyllableSegment } from '../types';
import type { PronunciationAccent } from './student-workspace-state';

export interface PlaybackDependencies {
  cancel: () => void;
  playAudio: (url: string) => Promise<void>;
  speak: (text: string, accent: PronunciationAccent, rate: number) => Promise<void>;
}

export type PlaybackStateListener = (activeText: string | null) => void;

export class SyllablePlaybackController {
  private readonly dependencies: PlaybackDependencies;
  private generation = 0;

  constructor(dependencies: PlaybackDependencies) {
    this.dependencies = dependencies;
  }

  cancel() {
    this.generation += 1;
    this.dependencies.cancel();
  }

  async playSegment(
    segment: SyllableSegment,
    accent: PronunciationAccent,
    onState: PlaybackStateListener,
  ) {
    const generation = this.begin();
    onState(segment.text);
    await this.playUnit(segment, accent, 0.75);
    if (generation === this.generation) {
      onState(null);
    }
  }

  async playWord(
    word: string,
    accent: PronunciationAccent,
    onState: PlaybackStateListener,
  ) {
    const generation = this.begin();
    onState(word);
    await this.dependencies.speak(word, accent, 0.9);
    if (generation === this.generation) {
      onState(null);
    }
  }

  async playSequence(
    word: string,
    segments: SyllableSegment[],
    accent: PronunciationAccent,
    onState: PlaybackStateListener,
  ) {
    const generation = this.begin();
    for (const segment of segments) {
      if (generation !== this.generation) {
        return;
      }
      onState(segment.text);
      await this.playUnit(segment, accent, 0.65);
    }
    if (generation !== this.generation) {
      return;
    }
    onState(word);
    await this.dependencies.speak(word, accent, 0.55);
    if (generation === this.generation) {
      onState(null);
    }
  }

  private begin() {
    this.cancel();
    return this.generation;
  }

  private async playUnit(
    segment: SyllableSegment,
    accent: PronunciationAccent,
    speechRate: number,
  ) {
    const audioUrl = accent === 'US' ? segment.usAudioUrl : segment.ukAudioUrl;
    if (audioUrl) {
      try {
        await this.dependencies.playAudio(audioUrl);
        return;
      } catch {
        // A broken remote clip should not block the browser voice fallback.
      }
    }
    await this.dependencies.speak(segment.text, accent, speechRate);
  }
}
