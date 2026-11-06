// Генератор компактного индекса сохранений из ludusavi-manifest.
// Запуск: node tools/gen_save_manifest.mjs <manifest.yaml> <out.json>
// Источник: https://github.com/mtkennerly/ludusavi-manifest (MIT)
//
// Из каждого игры берутся только пути файлов с тегом "save" (или без тегов),
// адресуемые через Windows-корни ludusavi. Пути переводятся в относительные
// от drive_c контейнера Wine. Ключи поиска: название игры, installDir и
// имена launch-exe — всё в нормализованном виде.
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { basename } from 'node:path';

const require = createRequire(import.meta.url);
// js-yaml ставится рядом со скачанным манифестом, не в проекте
const yaml = require(process.env.JSYAML_PATH || 'js-yaml');

const [manifestPath, outPath] = process.argv.slice(2);
if (!manifestPath || !outPath) {
  console.error('usage: node gen_save_manifest.mjs <manifest.yaml> <out.json>');
  process.exit(1);
}

// Токены ludusavi → путь относительно drive_c
const ROOT_MAP = {
  '<home>': 'users/xuser',
  '<windocuments>': 'users/xuser/Documents',
  '<winappdata>': 'users/xuser/AppData/Roaming',
  '<winlocalappdata>': 'users/xuser/AppData/Local',
  '<winlocalappdatalow>': 'users/xuser/AppData/LocalLow',
  '<winprogramdata>': 'ProgramData',
  '<winpublic>': 'users/Public',
};
const normKey = (s) => s.toLowerCase().replace(/[^a-z0-9а-я]+/g, '');

function mapPath(raw) {
  // Разбираем путь по токенам; незнакомый токен — путь отбрасывается
  let rel = '';
  let rest = raw.replace(/\\/g, '/');
  let matched = false;
  while (rest.startsWith('<')) {
    const end = rest.indexOf('>');
    if (end === -1) return null;
    const token = rest.slice(0, end + 1).toLowerCase();
    const mapped = ROOT_MAP[token];
    if (mapped === undefined) {
      if (!matched) return null; // корень неизвестен — путь не адресуем
      break; // токен в середине: обрезаем до известной части
    }
    rel = mapped;
    rest = rest.slice(end + 1);
    matched = true;
  }
  if (!matched) return null; // относительный путь без корня
  rest = rest.replace(/^\/+/, '');
  // Незнакомый токен в середине пути (<storeUserId> и т.п.) — дальше не адресуемо
  const badToken = rest.indexOf('<');
  if (badToken !== -1) rest = rest.slice(0, badToken);
  // macOS-пути Wine-префикса не касаются
  if (rest.startsWith('Library/')) return null;
  // Голый корень (весь AppData/Documents целиком) — не путь сохранений
  if (rest === '') return null;
  // Отрезаем glob-часть: "*"/"?" в имени файла или папки
  const wild = rest.search(/[*?\[\]]/);
  if (wild !== -1) {
    rest = rest.slice(0, wild);
    const slash = rest.lastIndexOf('/');
    rest = slash === -1 ? '' : rest.slice(0, slash);
  }
  rest = rest.replace(/\/+$/, '');
  const full = rest ? `${rel}/${rest}` : rel;
  return full === 'users/xuser' ? null : full; // весь профиль целиком не даём
}

const index = new Map(); // normKey -> Set<path>
const addKey = (key, paths) => {
  const k = normKey(key);
  if (!k) return;
  let set = index.get(k);
  if (!set) { set = new Set(); index.set(k, set); }
  for (const p of paths) set.add(p);
};

const manifest = yaml.load(readFileSync(manifestPath, 'utf8'));
for (const [gameName, entry] of Object.entries(manifest || {})) {
  if (!entry || typeof entry !== 'object') continue;

  const paths = new Set();
  if (entry.files && typeof entry.files === 'object') {
    for (const [rawPath, opts] of Object.entries(entry.files)) {
      const tags = Array.isArray(opts?.tags) ? opts.tags : [];
      if (tags.length > 0 && !tags.includes('save')) continue;
      const mapped = mapPath(rawPath);
      if (mapped) paths.add(mapped);
    }
  }
  if (paths.size === 0) continue;
  const list = [...paths].sort();

  addKey(gameName, list);
  // alias — «прозвище» игры, указывающее на чужую запись с теми же путями
  if (typeof entry.alias === 'string' && entry.alias.trim()) addKey(entry.alias, list);
  if (entry.installDir && typeof entry.installDir === 'object') {
    for (const dir of Object.keys(entry.installDir)) addKey(dir, list);
  }
  if (entry.launch && typeof entry.launch === 'object') {
    for (const launchPath of Object.keys(entry.launch)) {
      const exe = basename(launchPath.replace(/\\/g, '/'));
      if (exe.toLowerCase().endsWith('.exe')) {
        addKey(exe.slice(0, -4), list);
      }
    }
  }
}

const out = {};
for (const k of [...index.keys()].sort()) out[k] = [...index.get(k)];

writeFileSync(outPath, JSON.stringify(out));
console.log(`games: ${Object.keys(manifest || {}).length}, keys: ${Object.keys(out).length}`);
