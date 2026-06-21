import assert from 'node:assert/strict';
import test from 'node:test';

import { SyllablePlaybackController } from '../src/student/syllable-playback.ts';

const segment = {
  text: 're',
  ukPhonetic: '/rɪ/',
  usPhonetic: '/rɪ/',
  ukAudioUrl: 'https://audio.test/re-uk.mp3',
  usAudioUrl: 'https://audio.test/re-us.mp3',
};

test('prefers the selected accent audio URL', async () => {
  const calls = [];
  const controller = new SyllablePlaybackController({
    cancel: () => calls.push('cancel'),
    playAudio: async (url) => calls.push(`audio:${url}`),
    speak: async (text) => calls.push(`speech:${text}`),
  });

  await controller.playSegment(segment, 'US', () => undefined);

  assert.deepEqual(calls, ['cancel', 'audio:https://audio.test/re-us.mp3']);
});

test('falls back to speech when audio playback fails', async () => {
  const calls = [];
  const controller = new SyllablePlaybackController({
    cancel: () => calls.push('cancel'),
    playAudio: async () => {
      calls.push('audio');
      throw new Error('unavailable');
    },
    speak: async (text, accent, rate) => calls.push(`speech:${text}:${accent}:${rate}`),
  });

  await controller.playSegment(segment, 'UK', () => undefined);

  assert.deepEqual(calls, ['cancel', 'audio', 'speech:re:UK:0.75']);
});

test('slow spelling reads each syllable before the whole word', async () => {
  const calls = [];
  const controller = new SyllablePlaybackController({
    cancel: () => calls.push('cancel'),
    playAudio: async () => undefined,
    speak: async (text, accent, rate) => calls.push(`${text}:${accent}:${rate}`),
  });

  await controller.playSequence(
    'remember',
    [{ text: 're' }, { text: 'mem' }, { text: 'ber' }],
    'US',
    () => undefined,
  );

  assert.deepEqual(calls, [
    'cancel',
    're:US:0.65',
    'mem:US:0.65',
    'ber:US:0.65',
    'remember:US:0.55',
  ]);
});
