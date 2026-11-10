#!/usr/bin/env python3
"""
Переводы Solara: применяет пачку записей к проекту.

Запуск из корня репозитория:  python3 tools/i18n_apply.py файл_с_записями.txt [...]

Формат строки записи (в конце строки обязателен ' |||'):
    русский текст ||| English ||| Українська |||
    Треков: {0} ||| Tracks: {0} ||| Треків: {0} |||
Плейсхолдеры {0}, {1}... соответствуют ${...} / $имя в исходной строке по порядку.
Строка с '!' в начале (только два текста) = просто заменить русский текст на английский на месте
(для логов и отладки, без таблицы переводов):
    !ошибка {0} ||| error {0} |||

Что делает:
  1) дописывает новые записи в shared/.../i18n/Strings.kt (русский текст хранится в комментарии, по нему и ищутся повторы);
  2) заменяет в исходниках все обычные строки "..." с русским текстом на S.ключ или S.ключ(аргументы);
  3) добавляет import com.unknowstorm.musicapp.i18n.S;
  4) печатает, что осталось непереведённым и что пропущено.
Строки в тройных кавычках (юридические тексты) не трогает.
"""
import json, re, sys
from pathlib import Path

ROOTS = ["shared/src", "androidApp/src"]
STRINGS = Path("shared/src/commonMain/kotlin/com/unknowstorm/musicapp/i18n/Strings.kt")
MARK = "    // <<< ENTRIES >>>"
IMPORT_S = "import com.unknowstorm.musicapp.i18n.S"
CYR = re.compile(r'[\u0400-\u04FF]')
KEYWORDS = {"as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
            "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
            "val", "var", "when", "while", "tr", "toString", "equals", "hashCode"}

def scan(text):
    """[(start, end, body, raw)] — все строковые литералы Kotlin (кроме вложенных в ${...}); комментарии пропускаются."""
    out = []; n = len(text); i = 0

    def skip_char(i):
        i += 1
        i += 2 if (i < n and text[i] == '\\') else 1
        while i < n and text[i] != "'": i += 1
        return i + 1

    def skip_expr(i):
        depth = 1
        while i < n and depth:
            c = text[i]
            if c == '"': i = read_string(i)[1]; continue
            if c == "'": i = skip_char(i); continue
            if c == '{': depth += 1
            elif c == '}': depth -= 1
            i += 1
        return i

    def read_string(i):
        if text.startswith('"""', i):
            j = i + 3
            while j < n and not text.startswith('"""', j):
                if text.startswith('${', j): j = skip_expr(j + 2); continue
                j += 1
            while text.startswith('""""', j): j += 1
            return text[i + 3:j], j + 3, True
        j = i + 1
        while j < n and text[j] != '"':
            if text[j] == '\\': j += 2; continue
            if text.startswith('${', j): j = skip_expr(j + 2); continue
            j += 1
        return text[i + 1:j], j + 1, False

    while i < n:
        c = text[i]
        if text.startswith('//', i):
            while i < n and text[i] != '\n': i += 1
            continue
        if text.startswith('/*', i):
            depth = 1; i += 2
            while i < n and depth:
                if text.startswith('/*', i): depth += 1; i += 2
                elif text.startswith('*/', i): depth -= 1; i += 2
                else: i += 1
            continue
        if c == "'": i = skip_char(i); continue
        if c == '"':
            body, j, raw = read_string(i)
            out.append((i, j, body, raw)); i = j; continue
        i += 1
    return out

def to_template(body):
    """Исходный текст литерала -> (шаблон с {0},{1}, [выражения], [исходные плейсхолдеры])."""
    tmpl = []; exprs = []; origs = []; i = 0; n = len(body)
    while i < n:
        if body[i] == '\\':
            tmpl.append(body[i:i + 2]); i += 2; continue
        if body.startswith('${', i):
            depth = 1; j = i + 2
            while j < n and depth:
                if body[j] == '{': depth += 1
                elif body[j] == '}': depth -= 1
                j += 1
            exprs.append(body[i + 2:j - 1]); origs.append(body[i:j]); tmpl.append('{%d}' % (len(exprs) - 1)); i = j; continue
        m = re.match(r'\$([A-Za-z_][A-Za-z0-9_]*)', body[i:])
        if m:
            exprs.append(m.group(1)); origs.append(m.group(0)); tmpl.append('{%d}' % (len(exprs) - 1)); i += m.end(); continue
        tmpl.append(body[i]); i += 1
    return ''.join(tmpl), exprs, origs

def kt_text(tmpl, params=None):
    """Текст записи -> содержимое Kotlin-строки. {i} -> ${p_i}; кавычки и $ экранируются."""
    out = []
    for part in re.split(r'(\{\d+\})', tmpl):
        m = re.fullmatch(r'\{(\d+)\}', part)
        if m:
            out.append('${%s}' % (params[int(m.group(1))] if params else 'p' + m.group(1)))
        else:
            out.append(re.sub(r'(?<!\\)"', '\\"', part).replace('$', '\\$'))
    return ''.join(out)

def make_key(en, used):
    words = re.findall(r'[A-Za-z0-9]+', re.sub(r'\\.', ' ', re.sub(r'\{\d+\}', ' ', en)))[:5] or ['text']
    k = words[0].lower() + ''.join(w.capitalize() for w in words[1:])
    if k[0].isdigit(): k = 's' + k
    if k in KEYWORDS: k += 'Text'
    base, i = k, 2
    while k in used: k = f"{base}{i}"; i += 1
    return k

def load_entries():
    s = STRINGS.read_text(encoding='utf-8')
    mapping = {}
    for m in re.finditer(r'    // ru: (".*")\n    (?:val|fun) (\w+)', s):
        mapping[json.loads(m.group(1))] = m.group(2)
    return s, mapping

def parse_batch(path):
    entries, inplace = [], {}
    for ln, line in enumerate(Path(path).read_text(encoding='utf-8').split('\n'), 1):
        if not line.strip() or line.lstrip().startswith('#'): continue
        if not line.endswith(' |||'):
            sys.exit(f"{path}:{ln}: строка должна заканчиваться ' |||'")
        fields = line[:-4].split(' ||| ')
        if line.startswith('!'):
            if len(fields) != 2: sys.exit(f"{path}:{ln}: для '!' нужно два текста")
            inplace[fields[0][1:]] = fields[1]
        else:
            if len(fields) != 3: sys.exit(f"{path}:{ln}: нужно три текста (ru ||| en ||| uk), найдено {len(fields)}")
            entries.append((fields[0], fields[1], fields[2]))
    return entries, inplace

def add_entries(entries):
    s, mapping = load_entries()
    used = set(re.findall(r'    (?:val|fun) (\w+)', s))
    new = []
    for ru, en, uk in entries:
        if ru in mapping: continue
        n_args = len(set(re.findall(r'\{(\d+)\}', ru)))
        for lang, t in (('en', en), ('uk', uk)):
            if len(set(re.findall(r'\{(\d+)\}', t))) != n_args:
                print(f"  ! предупреждение: в {lang}-тексте другое число {{N}}, чем в русском: {ru!r}")
        key = make_key(en, used); used.add(key); mapping[ru] = key
        params = ', '.join(f'p{i}: Any?' for i in range(n_args))
        if n_args:
            new.append(f'    // ru: {json.dumps(ru, ensure_ascii=False)}\n    fun {key}({params}): String = tr("{kt_text(en)}", "{kt_text(uk)}")\n')
        else:
            new.append(f'    // ru: {json.dumps(ru, ensure_ascii=False)}\n    val {key}: String get() = tr("{kt_text(en)}", "{kt_text(uk)}")\n')
    if new:
        if MARK not in s: sys.exit(f"В {STRINGS} нет строки-маркера {MARK!r}")
        STRINGS.write_text(s.replace(MARK, ''.join(new) + MARK), encoding='utf-8')
    return mapping, len(new)

def rewrite(text, mapping, inplace):
    lits = scan(text); edits = []; skipped = []; code = False
    for start, end, body, raw in lits:
        if raw or not CYR.search(body): continue
        tmpl, exprs, origs = to_template(body)
        line_start = text.rfind('\n', 0, start) + 1
        prefix = text[line_start:start]
        if tmpl in inplace:
            en = inplace[tmpl]
            res = re.sub(r'\{(\d+)\}', lambda m: '${%s}' % exprs[int(m.group(1))] if origs[int(m.group(1))].startswith('$') and not origs[int(m.group(1))].startswith('${') else origs[int(m.group(1))], en)
            res = re.sub(r'(?<!\\)"', '\\"', res)
            edits.append((start, end, '"' + res + '"')); continue
        if tmpl not in mapping: continue
        if re.search(r'\bconst\s+val\b', prefix) or re.fullmatch(r'(?:private |internal |public )?val \w+(?:\s*:\s*[\w<>?]+)?\s*=\s*', prefix):
            skipped.append((text.count('\n', 0, start) + 1, body)); continue
        key = mapping[tmpl]
        edits.append((start, end, f"S.{key}({', '.join(exprs)})" if exprs else f"S.{key}")); code = True
    for start, end, repl in sorted(edits, reverse=True):
        text = text[:start] + repl + text[end:]
    return text, len(edits), skipped, code

def main():
    if len(sys.argv) < 2: sys.exit(__doc__)
    if not STRINGS.is_file(): sys.exit(f"Не найден {STRINGS}: запустите из корня репозитория после установки каркаса")
    entries, inplace = [], {}
    for p in sys.argv[1:]:
        e, i = parse_batch(p); entries += e; inplace.update(i)
    mapping, added = add_entries(entries)
    print(f"Новых записей в Strings.kt: {added}")
    total = 0; skipped_all = []
    for root in ROOTS:
        for p in sorted(Path(root).rglob('*.kt')):
            if '/build/' in str(p) or '/i18n/' in str(p): continue
            raw = p.read_text(encoding='utf-8')
            crlf = '\r\n' in raw
            text = raw.replace('\r\n', '\n'); code_any = False; count = 0
            for _ in range(4):
                text, n, skipped, code = rewrite(text, mapping, inplace)
                count += n; code_any |= code
                if not n: break
            if skipped:
                for ln, b in skipped: skipped_all.append((str(p), ln, b))
            if code_any and IMPORT_S not in text and 'package com.unknowstorm.musicapp.i18n' not in text:
                m = re.search(r'^import ', text, flags=re.M)
                if m: text = text[:m.start()] + IMPORT_S + '\n' + text[m.start():]
                else:
                    m = re.search(r'^package .*\n', text, flags=re.M)
                    text = text[:m.end()] + '\n' + IMPORT_S + '\n' + text[m.end():]
            if count:
                p.write_text(text.replace('\n', '\r\n') if crlf else text, encoding='utf-8', newline='')
                print(f"  {p}: {count}")
                total += count
    print(f"Всего замен в исходниках: {total}")
    if skipped_all:
        print("\nПРОПУЩЕНО (const val или глобальная val: значение вычисляется один раз, нужна ручная правка):")
        for f, ln, b in skipped_all: print(f"  {f}:{ln}: {b}")
    left = {}; raws = {}
    for root in ROOTS:
        for p in sorted(Path(root).rglob('*.kt')):
            if '/build/' in str(p) or '/i18n/' in str(p): continue
            for start, end, body, raw in scan(p.read_text(encoding='utf-8')):
                if CYR.search(body):
                    (raws if raw else left).setdefault(str(p), []).append(body)
    n_left = sum(len(v) for v in left.values())
    print(f"\nОСТАЛОСЬ непереведённых обычных строк: {n_left}; в файлах: {len(left)}")
    for f, v in left.items(): print(f"  {f}: {len(v)}")
    if raws: print("Строки \"\"\"...\"\"\" с русским текстом (делаются вручную): " + ', '.join(f"{Path(f).name}({len(v)})" for f, v in raws.items()))

if __name__ == '__main__':
    main()
