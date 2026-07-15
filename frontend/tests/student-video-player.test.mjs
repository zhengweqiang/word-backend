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

test('student video player uses fullscreen entry instead of orientation toggle text', () => {
  assert.match(playerSource, /student-player__fullscreen/);
  assert.match(playerSource, /requestFullscreen/);
  assert.match(playerSource, /fullscreenchange/);
  assert.match(playerSource, /全屏/);
  assert.doesNotMatch(playerSource, /横屏/);
  assert.doesNotMatch(playerSource, /竖屏/);
});

test('student video player rotates the fullscreen panel ninety degrees', () => {
  const fullscreenButtonRule = ruleFor('.student-player__fullscreen');
  const fullscreenLandscapeRule = ruleFor('.student-player__panel--fullscreen-landscape');

  assert.match(fullscreenButtonRule, /position:\s*absolute/);
  assert.match(fullscreenButtonRule, /top:\s*10px/);
  assert.match(fullscreenButtonRule, /left:\s*10px/);
  assert.match(fullscreenLandscapeRule, /transform:\s*rotate\(90deg\)/);
  assert.match(fullscreenLandscapeRule, /width:\s*100vh/);
  assert.match(fullscreenLandscapeRule, /height:\s*100vw/);
});
