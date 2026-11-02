import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const read = path => readFile(new URL(path, import.meta.url), 'utf8');
const unwrap = sql => sql.replace(/^\uFEFF/, '').replace(/^\s*(begin|commit);\s*$/gmi, '');

export async function buildBundle({ projectRef, ownerEmail }) {
  if (!/^[a-z]{20}$/.test(projectRef || '') || projectRef === 'ymafgxjevciiwkhtnzya') {
    throw new Error('Use a new dedicated test project reference; the existing application project is forbidden.');
  }
  if (typeof ownerEmail !== 'string' || /[\x00-\x20\x7f$]/.test(ownerEmail) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(ownerEmail)) {
    throw new Error('Supply the email of an existing dedicated Supabase Auth test account.');
  }
  const [base, ownership, hardening, sync] = await Promise.all([
    read('./base.sql'), read('./ownership.sql'), read('./hardening.sql'),
    read('../../supabase/migrations/20261101010000_offline_lead_sync.sql'),
  ]);
  const guard = `DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public') THEN
      RAISE EXCEPTION 'Expected an empty application schema; refusing to modify existing tables.';
    END IF;
  END $$;`;
  const sql = `-- Dedicated test bootstrap for ${projectRef}. Verify the Dashboard project before running.
-- Local generation does not authorize deployment. Requires explicit operator confirmation.
BEGIN;
SET LOCAL standard_conforming_strings = on;
${guard}
${unwrap(base)}
${unwrap(ownership).replaceAll('__TEST_OWNER_EMAIL__', ownerEmail.replaceAll("'", "''"))}
${unwrap(hardening)}
${unwrap(sync)}
-- The private receipt table remains inaccessible to client roles. Its owner
-- executes the security-definer sync function and is not subject to RLS.
ALTER TABLE autotrack_sync.operations ENABLE ROW LEVEL SECURITY;
NOTIFY pgrst, 'reload schema';
COMMIT;
`;
  const url = `https://${projectRef}.supabase.co`;
  return {
    sql,
    android: `SUPABASE_URL=${url}\nSUPABASE_PUBLISHABLE_KEY=<test-project-publishable-key>\n`,
    web: `NEXT_PUBLIC_SUPABASE_URL=${url}\nNEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=<test-project-publishable-key>\n`,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [projectRef, ownerEmail, output] = process.argv.slice(2);
    if (!output || process.argv.length !== 5) {
      throw new Error('Usage: node tools/supabase-test-env/prepare.mjs <test-project-ref> <test-owner-email> <new-output-directory>');
    }
    const bundle = await buildBundle({ projectRef, ownerEmail });
    const directory = resolve(output);
    await mkdir(directory, { recursive: false });
    for (const [name, value] of Object.entries({ 'bootstrap.sql': bundle.sql,
      'android.properties.example': bundle.android, 'web.env.example': bundle.web })) {
      await writeFile(resolve(directory, name), value, { flag: 'wx' });
    }
    console.log(`Prepared local files in ${directory}. No network calls or deployments were performed.`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
