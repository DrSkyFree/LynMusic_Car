#!/usr/bin/env python3
"""Validate UI localization without launching any application or UI test.

The shared Compose XML resources are authoritative. Official Compose resource
accessors and assets are generated from them for Android and JVM desktops.
Only application UI literals are checked, never music, lyrics, logs, or protocols.
"""
from pathlib import Path
import re
from ui_resource_keys import resource_key_errors
HAN=re.compile('[\u3400-\u4dbf\u4e00-\u9fff]')
# Parse Kotlin interpolation expressions as code, including nested strings/comments.
def quoted(s,i):
 raw=s.startswith('"""',i); q='"""' if raw else '"'; j=i+len(q); parts=[]; args=[]; start=j
 while j<len(s):
  if s.startswith(q,j):
   parts.append(s[start:j]);return j+len(q),parts,args,raw
  if not raw and s[j]=='\\': j+=2;continue
  if s[j]=='$' and j+1<len(s):
   if s[j+1]=='{':
    k=j+2;level=1
    while k<len(s) and level:
     if s[k]=='"':k=quoted(s,k)[0];continue
     if s.startswith('//',k): k=s.find('\n',k);continue
     if s[k]=='{':level+=1
     if s[k]=='}':level-=1
     k+=1
    parts.append(s[start:j]);args.append(s[j+2:k-1]);j=k;start=j;continue
   m=re.match(r'[A-Za-z_][A-Za-z_0-9]*',s[j+1:])
   if m:
    k=j+1+len(m[0]);parts.append(s[start:j]);args.append(m[0]);j=k;start=j;continue
  j+=1
 raise ValueError(s[i:i+120])
def unescape(s,raw=False):
 if raw:return s
 return re.sub(r'\\(u[0-9a-fA-F]{4}|.)',lambda m:chr(int(m[1][1:],16)) if m[1].startswith('u') else {'n':'\n','r':'\r','t':'\t','b':'\b','f':'\f','"':'"',"'":"'",'\\':'\\','$':'$'}.get(m[1],m[0]),s)
def literals(s):
 i=0
 while i<len(s):
  if s.startswith('//',i):i=s.find('\n',i);i=len(s) if i<0 else i;continue
  if s.startswith('/*',i):
   j=s.find('*/',i+2);i=len(s) if j<0 else j+2;continue
  if s[i]=="'":
   i+=1
   while i<len(s):
    if s[i]=='\\':i+=2;continue
    if s[i]=="'":i+=1;break
    i+=1
   continue
  if s[i]=='"':
   end,parts,args,raw=quoted(s,i)
   literal=''.join(unescape(p,raw) for p in parts)
   if HAN.search(literal):
    template=''.join(unescape(p,raw)+(f'%{j+1}$s' if j<len(args) else '') for j,p in enumerate(parts))
    yield i,end,template,args,raw
   i=end;continue
  i+=1

import sys
import xml.etree.ElementTree as ET
from collections import Counter
ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / "shared/resources/src/commonMain/composeResources"
LOCALES = ("values", "values-zh", "values-zh-rTW")
errors = []
catalogs = {}
argument_pattern = re.compile(r"%([1-9][0-9]*)\$([sd])")
def validate_resource_key(key, location):
    errors.extend(resource_key_errors(key, location))

for locale in LOCALES:
    catalog = {}
    for path in sorted((RESOURCE_ROOT / locale).glob("*.xml")):
        for element in ET.parse(path).getroot():
            key = element.attrib["name"]
            if key in catalog:
                errors.append(f"{locale}: duplicate key {key}")
            validate_resource_key(key, locale)
            values = ({item.attrib["quantity"]: item.text or "" for item in element}
                      if element.tag == "plurals" else {"string": element.text or ""})
            if element.tag == "plurals" and set(values) != {"one", "other"}:
                errors.append(f"{locale}: {key} needs one/other quantities")
            for text in values.values():
                slots = {int(index) for index, _ in argument_pattern.findall(text)}
                if slots and slots != set(range(1, max(slots) + 1)):
                    errors.append(f"{locale}: {key} has nonconsecutive arguments")
            catalog[key] = element.tag, values
    catalogs[locale] = catalog

# Handwritten Android resources follow the same naming rule as the shared XML.
for relative in ("android/runtime/src/main/res",
                 "automotiveApp/src/main/res"):
    for path in (ROOT / relative).glob("values*/*.xml"):
        for element in ET.parse(path).getroot():
            if element.tag in {"string", "string-array", "plurals"}:
                validate_resource_key(element.attrib["name"], path.relative_to(ROOT))

base = catalogs["values"]
for locale in LOCALES[1:]:
    if catalogs[locale].keys() != base.keys():
        errors.append(f"{locale}: translation keys differ")
    for key in base.keys() & catalogs[locale].keys():
        kind, patterns = base[key]
        translated_kind, translated = catalogs[locale][key]
        if kind != translated_kind or patterns.keys() != translated.keys():
            errors.append(f"{locale}: {key} resource/quantity types differ")
            continue
        for quantity, text in patterns.items():
            if Counter(argument_pattern.findall(text)) != Counter(argument_pattern.findall(translated[quantity])):
                errors.append(f"{locale}: {key}/{quantity} argument number/type differs")

# Static key references, including non-Compose business messages.
for module in ("player", "shared", "app", "ui", "android", "automotiveApp", "cast"):
    for path in (ROOT / module).rglob("*.kt"):
        if "build" in path.parts or any(part.endswith("Test") for part in path.parts):
            continue
        source = path.read_text(encoding="utf-8")
        for kind, key in re.findall(r'\bRes\.(string|plurals)\.([a-z0-9_]+)', source):
            if key not in base:
                errors.append(f"{path.relative_to(ROOT)}: missing resource {key}")
            elif (kind == "plurals") != (base[key][0] == "plurals"):
                errors.append(f"{path.relative_to(ROOT)}: incorrect resource type {key}")
        if re.search(r'(?:uiString|uiText|uiPlural|nativeUiString)\(\s*"[a-z0-9_]+"', source):
            errors.append(f"{path.relative_to(ROOT)}: obsolete string-key resource call")
        if path.name != "UiResourceEnvironment.kt" and re.search(r'INVISIBLE_(?:REFERENCE|MEMBER)|InternalResourceApi|LocalComposeEnvironment|LanguageQualifier|RegionQualifier', source):
            errors.append(f"{path.relative_to(ROOT)}: resource internals must stay in the language adapter")

# Explicit UI-only scan. These remaining Chinese literals are diagnostic invariants.
allowed_diagnostics = {
    "ArtworkBitmap.jvm.kt": {"远端封面读取失败。"},
    "LibraryNavigation.kt": {"在线导航目标应在本地导航解析前处理。"},
    "AndroidOfflineDownloadGateway.kt": {"离线文件不存在。"},
    "OfflineDownloadRepositories.kt": {"上次下载未完成。", "下载失败。", "离线文件不存在。"},
    "AndroidWebDavSupport.kt": {"读取失败。"},
    "AndroidDesktopLyricsOverlayService.kt": {"保存桌面歌词位置失败。"},
}
ui_paths = []
ui_paths += [p for p in (ROOT / "ui").rglob("*.kt")
             if "build" not in p.parts and any(part.endswith("Main") for part in p.parts)]
ui_paths += [ROOT / "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform" / name
             for name in ("AndroidEqualizerActivity.kt", "AndroidLocalFolderPickerActivity.kt", "AndroidWebDavSupport.kt", "AndroidAudioTagEditorPlatformService.kt", "AndroidSambaSupport.kt", "AndroidPlaybackErrorText.kt")]
ui_paths += [ROOT / "cast/api/src/commonMain/kotlin/top/iwesley/lyn/music/cast/CastModels.kt"]
ui_paths += [ROOT / path for path in (
    "shared/core/src/commonMain/kotlin/top/iwesley/lyn/music/core/model/OfflineDownloadFailureText.kt",
    "shared/core/src/commonMain/kotlin/top/iwesley/lyn/music/core/model/WebDav.kt",
    "shared/core/src/commonMain/kotlin/top/iwesley/lyn/music/core/model/SambaUiText.kt",
    "shared/data/src/commonMain/kotlin/top/iwesley/lyn/music/platform/SambaPlaybackSupport.kt",
    "shared/data/src/commonMain/kotlin/top/iwesley/lyn/music/data/repository/OfflineDownloadRepositories.kt",
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/AndroidOfflineDownloadGateway.kt",
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/AndroidDesktopLyricsOverlayService.kt",
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/AndroidDesktopLyricsLanguageRefresh.kt",
    "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/SambaCastProxyResource.kt",
    "shared/data/src/commonMain/kotlin/top/iwesley/lyn/music/domain/WorkflowLyricsEngine.kt",
)]
for path in ui_paths:
    source = path.read_text(encoding="utf-8")
    # Enum entries live for the process lifetime; only resource keys may be cached there.
    for entries in re.findall(r'enum\s+class\s+\w+\s*(?:\([^{}]*\))?\s*\{([^;{}]*)(?:;|\})', source, re.S):
        if re.search(r'\b(?:uiString|nativeUiString|resolveUiText)\s*\(', entries):
            errors.append(f"{path.relative_to(ROOT)}: enum entries cache translated UI strings")
    for start, _, template, _, _ in literals(source):
        if template not in allowed_diagnostics.get(path.name, set()):
            errors.append(f"{path.relative_to(ROOT)}:{source.count(chr(10), 0, start) + 1}: hardcoded UI text {template}")

# Connection-test failures are displayed directly; other platform sections include excluded logs and metadata.
relative_path = "android/runtime/src/main/kotlin/top/iwesley/lyn/music/platform/AndroidPlatform.kt"
source = (ROOT / relative_path).read_text(encoding="utf-8")
start = source.index("override suspend fun testSamba(")
end = source.index("override suspend fun scanSamba(", start)
for offset, _, template, _, _ in literals(source[start:end]):
    errors.append(f"{relative_path}:{source.count(chr(10), 0, start + offset) + 1}: hardcoded Samba connection UI text {template}")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"UI localization checks passed: {len(base)} shared keys, "
      f"{sum(kind == 'plurals' for kind, _ in base.values())} plurals, "
      "3 complete languages.")
