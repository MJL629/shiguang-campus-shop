// AI-assisted regression tests for a timezone defect found during browser verification.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { formatTime } from '../src/lib/format.js'
test('zoned SMTP timestamps display in Shanghai time', () => {
  assert.equal(formatTime('2026-11-06T10:37:00.000+00:00'), '2026-11-06 18:37')
  assert.equal(formatTime('2026-11-06T16:10:00Z'), '2026-11-07 00:10')
})
test('naive order timestamps keep their server wall-clock value', () => {
  assert.equal(formatTime('2026-11-06T17:35:00'), '2026-11-06 17:35')
  assert.equal(formatTime(null), '—')
})
