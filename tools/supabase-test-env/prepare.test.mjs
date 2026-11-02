import test from 'node:test';
import assert from 'node:assert/strict';
import { buildBundle } from './prepare.mjs';

test('rejects the existing application project and malformed targets', async () => {
  for (const projectRef of ['ymafgxjevciiwkhtnzya', '', 'https://example.com', "abc';drop schema public"]) {
    await assert.rejects(() => buildBundle({ projectRef, ownerEmail: 'owner@example.invalid' }));
  }
});

test('builds one atomic empty-project bootstrap and matching public configuration', async () => {
  const bundle = await buildBundle({ projectRef: 'abcdefghijklmnopqrst', ownerEmail: "qa'owner@example.invalid" });
  assert.match(bundle.sql, /qa''owner@example\.invalid/);
  assert.doesNotMatch(bundle.sql, /pengyuwang777|CREATE ROLE|INSERT INTO auth\.users/i);
  assert.equal((bundle.sql.match(/^BEGIN;$/gm) || []).length, 1);
  assert.equal((bundle.sql.match(/^COMMIT;$/gm) || []).length, 1);
  assert.match(bundle.sql, /Expected an empty application schema/);
  assert.match(bundle.sql, /revoke truncate, references, trigger/i);
  assert.match(bundle.android, /https:\/\/abcdefghijklmnopqrst\.supabase\.co/);
  assert.match(bundle.web, /NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=/);
});

import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('rejects invalid owner identities', async () => {
  for (const ownerEmail of ['', 'owner', 'owner@example.invalid\nDROP TABLE leads', 'qa$$@example.invalid', 'qa' + String.fromCharCode(0) + '@example.invalid', null]) {
    await assert.rejects(() => buildBundle({projectRef:'abcdefghijklmnopqrst', ownerEmail}));
  }
});

test('CLI generates local files and refuses to overwrite an existing directory', async () => {
  const parent = await mkdtemp(join(tmpdir(), 'autotrack-test-env-'));
  const output = join(parent, 'bundle');
  const script = fileURLToPath(new URL('./prepare.mjs', import.meta.url));
  const args = [script, 'abcdefghijklmnopqrst', 'owner@example.invalid', output];
  try {
    const first = spawnSync(process.execPath, args, {encoding:'utf8'});
    assert.equal(first.status, 0, first.stderr);
    const original = await readFile(join(output, 'bootstrap.sql'), 'utf8');
    assert.match(original, /owner@example\.invalid/);
    assert.match(await readFile(join(output, 'android.properties.example'), 'utf8'), /SUPABASE_URL=/);
    assert.match(await readFile(join(output, 'web.env.example'), 'utf8'), /NEXT_PUBLIC_SUPABASE_URL=/);
    const second = spawnSync(process.execPath, args, {encoding:'utf8'});
    assert.notEqual(second.status, 0);
    assert.equal(await readFile(join(output, 'bootstrap.sql'), 'utf8'), original);
  } finally { await rm(parent, {recursive:true,force:true}); }
});


