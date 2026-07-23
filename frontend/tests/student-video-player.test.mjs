import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const css = readFileSync(new URL('../src/student/student-workspace.css', import.meta.url), 'utf8');
const playerSource = readFileSync(new URL('../src/student/StudentClassrooms.tsx', import.meta.url), 'utf8');

function ruleFor(selector) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = css.match(new RegExp(`${escapedSelector}\\s*\\{([^}]*)\\}`));
  assert.ok(match, `Expected ${selector} rule to exist`);
  return match[1];
}

test('student video player panel fills the playback dialog', () => {
  const playerRule = ruleFor('.student-player');
  const panelRule = ruleFor('.student-player__panel');
  const mediaRule = ruleFor('.student-player__media');

  assert.match(playerRule, /align-items:\s*stretch/);
  assert.match(panelRule, /height:\s*100%/);
  assert.match(panelRule, /display:\s*flex/);
  assert.match(panelRule, /flex-direction:\s*column/);
  assert.match(mediaRule, /flex:\s*1\s+1\s+auto/);
  assert.match(mediaRule, /min-height:\s*0/);
  assert.doesNotMatch(mediaRule, /aspect-ratio:\s*16\s*\/\s*9/);
});

test('student video player uses a landscape toggle next to the close button', () => {
  assert.match(playerSource, /isVideoLandscape/);
  assert.match(playerSource, /student-player__orientation/);
  assert.match(playerSource, /student-player__panel--landscape/);
  assert.match(playerSource, /横屏/);
  assert.match(playerSource, /竖屏/);
  assert.doesNotMatch(playerSource, /requestFullscreen/);
  assert.doesNotMatch(playerSource, /全屏/);
});

test('student video player reports classroom video completion when playback ends', () => {
  assert.match(playerSource, /completeVideoPlayback/);
  assert.match(playerSource, /studentVideoApi\.completeFromClassroomFeed/);
  assert.match(playerSource, /onEnded=\{\(\)\s*=>\s*void completeVideoPlayback\(\)\}/);
});

test('student video player landscape panel uses the portrait height as its width', () => {
  const orientationButtonRule = ruleFor('.student-player__orientation');
  const landscapePanelRule = ruleFor('.student-player__panel--landscape');

  assert.match(orientationButtonRule, /position:\s*absolute/);
  assert.match(orientationButtonRule, /top:\s*10px/);
  assert.match(orientationButtonRule, /right:\s*56px/);
  assert.match(landscapePanelRule, /position:\s*absolute/);
  assert.match(landscapePanelRule, /top:\s*50%/);
  assert.match(landscapePanelRule, /left:\s*50%/);
  assert.match(landscapePanelRule, /transform:\s*translate\(-50%,\s*-50%\)\s*rotate\(90deg\)/);
  assert.match(landscapePanelRule, /width:\s*calc\(100vh\s*-\s*32px\)/);
  assert.match(landscapePanelRule, /height:\s*calc\(100vw\s*-\s*32px\)/);
});
