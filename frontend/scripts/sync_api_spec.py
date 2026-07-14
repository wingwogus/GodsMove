#!/usr/bin/env python3
"""
Notion API 명세서 -> 로컬 마크다운 동기화.

공개 Notion 사이트(다른 워크스페이스)라 공식 API/MCP로는 DB 행을 못 가져와서,
비공식 web API(loadPageChunk / queryCollection)로 페이지+하위페이지+DB 행을
전부 크롤링해 마크다운으로 저장한다. 인증/토큰 불필요 (공개 페이지 한정).

사용:
    python3 sync_api_spec.py [--out <dir>]
"""

import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

ROOT_PAGE_ID = "d5b9e2d9-4405-8337-a774-015bc187a6aa"
SPACE_ID = "8481065c-0346-4855-acff-d7dbd5f6eea8"
BASE = "https://www.notion.so/api/v3"

HEADERS = {
    "Content-Type": "application/json",
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) sync-api-spec",
}


_MIN_INTERVAL = 1.2   # 요청 간 최소 간격(초) - Notion 비공식 API 레이트리밋 회피
_last_call = [0.0]
CACHE_DIR = None      # --cache 지정 시 HTTP 응답 디스크 캐시 (개발용)


def _cache_path(path, payload):
    key = hashlib.sha1((path + json.dumps(payload, sort_keys=True)).encode()).hexdigest()
    return os.path.join(CACHE_DIR, key + ".json")


def _post(path, payload, tries=7):
    if CACHE_DIR:
        cp = _cache_path(path, payload)
        if os.path.exists(cp):
            with open(cp, encoding="utf-8") as f:
                return json.load(f)
    data = json.dumps(payload).encode("utf-8")
    last = None
    for i in range(tries):
        # throttle
        wait = _MIN_INTERVAL - (time.time() - _last_call[0])
        if wait > 0:
            time.sleep(wait)
        _last_call[0] = time.time()
        try:
            req = urllib.request.Request(f"{BASE}/{path}", data=data, headers=HEADERS, method="POST")
            with urllib.request.urlopen(req, timeout=30) as r:
                out = json.loads(r.read().decode("utf-8"))
            if CACHE_DIR:
                with open(_cache_path(path, payload), "w", encoding="utf-8") as f:
                    json.dump(out, f)
            return out
        except urllib.error.HTTPError as e:  # noqa: PERF203
            last = e
            if e.code == 429:
                time.sleep(min(60, 8.0 * (i + 1)))  # 429는 길게 대기
            else:
                time.sleep(1.5 * (i + 1))
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (i + 1))
    raise RuntimeError(f"POST {path} failed: {last}")


def dashify(uuid):
    u = uuid.replace("-", "")
    if len(u) != 32:
        return uuid
    return f"{u[0:8]}-{u[8:12]}-{u[12:16]}-{u[16:20]}-{u[20:32]}"


# --------------------------------------------------------------------------- #
# Notion fetch helpers
# --------------------------------------------------------------------------- #

def load_page_chunks(page_id):
    """페이지의 모든 블록을 recordMap.block 하나로 합쳐 반환 (청크 페이지네이션)."""
    blocks = {}
    cursor = {"stack": []}
    chunk = 0
    while True:
        res = _post("loadPageChunk", {
            "pageId": dashify(page_id),
            "limit": 100,
            "cursor": cursor,
            "chunkNumber": chunk,
            "verticalColumns": False,
        })
        rm = res.get("recordMap", {})
        blocks.update(rm.get("block", {}))
        cursor = res.get("cursor", {})
        if not cursor.get("stack"):
            break
        chunk += 1
    return blocks


def query_collection(collection_id, view_id):
    """컬렉션(DB) 행 반환: (schema dict, [row_block_value...], recordMap.block)."""
    res = _post("queryCollection?src=initial_load", {
        "collection": {"id": collection_id, "spaceId": SPACE_ID},
        "collectionView": {"id": view_id, "spaceId": SPACE_ID},
        "loader": {
            "type": "reducer",
            "reducers": {"collection_group_results": {"type": "results", "limit": 500}},
            "searchQuery": "",
            "userTimeZone": "Asia/Seoul",
        },
    })
    rm = res.get("recordMap", {})
    coll = rm.get("collection", {}).get(dashify(collection_id), {})
    schema = coll.get("value", {}).get("value", {}).get("schema", {})
    blocks = rm.get("block", {})
    result = res.get("result", {}) or {}
    order = []
    for rg in result.get("reducerResults", {}).get("collection_group_results", {}).get("blockIds", []) or []:
        order.append(rg)
    if not order:
        # fallback: any page block in map
        order = [bid for bid, r in blocks.items()
                 if r.get("value", {}).get("value", {}).get("type") == "page"]
    return schema, order, blocks


# --------------------------------------------------------------------------- #
# value accessors (blocks come as {"value": {"value": {...}}} or {"value": {...}})
# --------------------------------------------------------------------------- #

def bval(rec):
    v = rec.get("value", {})
    if isinstance(v, dict) and "value" in v and isinstance(v["value"], dict):
        return v["value"]
    return v


def title_of(v):
    props = v.get("properties", {})
    t = props.get("title")
    return rich_to_text(t) if t else ""


# --------------------------------------------------------------------------- #
# rich text
# --------------------------------------------------------------------------- #

def rich_to_text(arr):
    if not arr:
        return ""
    out = []
    for seg in arr:
        out.append(seg[0] if seg else "")
    return "".join(out)


def rich_to_md(arr, id2slug=None, id2title=None):
    if not arr:
        return ""
    out = []
    for seg in arr:
        text = seg[0] if seg else ""
        fmts = seg[1] if len(seg) > 1 else []
        page_ref = None
        url = None
        b = i = c = s = False
        for f in fmts:
            tag = f[0]
            if tag == "p":
                page_ref = f[1]
            elif tag == "a":
                url = f[1]
            elif tag == "b":
                b = True
            elif tag == "i":
                i = True
            elif tag == "c":
                c = True
            elif tag == "s":
                s = True
        if page_ref:
            pid = dashify(page_ref)
            title = (id2title or {}).get(pid) or "링크된 페이지"
            slug = (id2slug or {}).get(pid)
            text = f"[{title}]({slug})" if slug else f"**{title}**"
            out.append(text)
            continue
        t = text
        if c:
            t = f"`{t}`"
        if b:
            t = f"**{t}**"
        if i:
            t = f"*{t}*"
        if s:
            t = f"~~{t}~~"
        if url:
            t = f"[{t}]({url})"
        out.append(t)
    return "".join(out)


# --------------------------------------------------------------------------- #
# crawl (discover all pages + collections)
# --------------------------------------------------------------------------- #

class Node:
    def __init__(self, pid, title, kind, parent=None):
        self.pid = pid
        self.title = title
        self.kind = kind  # 'root' | 'subpage' | 'row'
        self.parent = parent
        self.blocks = None
        self.children = []      # child page ids (sub-pages / rows)
        self.collections = []   # (collection_id, view_id) referenced on this page


def crawl(root_id):
    nodes = {}          # pid -> Node
    order = []          # discovery order

    def visit(pid, title, kind, parent):
        pid = dashify(pid)
        if pid in nodes:
            return
        node = Node(pid, title, kind, parent)
        nodes[pid] = node
        order.append(pid)
        node.blocks = load_page_chunks(pid)
        # find child page blocks + collection views within this page's subtree
        rootv = node.blocks.get(pid)
        content_ids = bval(rootv).get("content", []) if rootv else []
        # walk full subtree of THIS page (non-page descendants) to catch nested collection_views + child pages
        stack = list(content_ids)
        seen = set()
        while stack:
            bid = stack.pop(0)
            if bid in seen:
                continue
            seen.add(bid)
            rec = node.blocks.get(bid)
            if not rec:
                # 부모 청크에 없는 블록(중첩 collection_view_page 등)은 지연 로드
                try:
                    node.blocks.update(load_page_chunks(bid))
                except Exception:  # noqa: BLE001
                    pass
                rec = node.blocks.get(bid)
                if not rec:
                    continue
            v = bval(rec)
            t = v.get("type")
            if t == "page":
                child_title = title_of(v)
                node.children.append(dashify(bid))
                visit(bid, child_title, "subpage", pid)
                continue  # don't descend into sub-page content here
            if t in ("collection_view", "collection_view_page"):
                cid = v.get("collection_id")
                vids = v.get("view_ids") or []
                if cid and vids:
                    node.collections.append((dashify(cid), vids[0]))
                    schema, row_ids, cblocks = query_collection(cid, vids[0])
                    node._coll_cache = getattr(node, "_coll_cache", {})
                    node._coll_cache[dashify(cid)] = (schema, row_ids, cblocks)
                    for rid in row_ids:
                        rrec = cblocks.get(rid)
                        if not rrec:
                            continue
                        rv = bval(rrec)
                        if rv.get("type") != "page":
                            continue
                        rtitle = title_of(rv)
                        node.children.append(dashify(rid))
                        visit(rid, rtitle, "row", pid)
                continue
            # descend into container blocks (toggle, columns, list, etc.)
            for ch in v.get("content", []) or []:
                stack.append(ch)

    root_blocks_probe = load_page_chunks(root_id)
    rtitle = title_of(bval(root_blocks_probe.get(dashify(root_id), {}))) or "API 명세서"
    visit(root_id, rtitle, "root", None)
    return nodes, order


# --------------------------------------------------------------------------- #
# slug / filenames
# --------------------------------------------------------------------------- #

def slugify(title, fallback):
    t = (title or "").strip()
    if not t:
        t = fallback
    t = t.replace("/", "／").replace("\\", "＼")
    t = re.sub(r"\s+", "-", t)
    t = re.sub(r'[:*?"<>|]', "", t)
    return t[:80] or fallback


def assign_slugs(nodes, order):
    id2slug = {}
    id2title = {}
    used = set()
    for pid in order:
        n = nodes[pid]
        id2title[pid] = n.title or "(제목 없음)"
        if n.kind == "root":
            slug = "README.md"
        else:
            base = slugify(n.title, pid[:8])
            slug = f"{base}.md"
            k = 2
            while slug.lower() in used:
                slug = f"{base}-{k}.md"
                k += 1
        used.add(slug.lower())
        id2slug[pid] = slug
    return id2slug, id2title


# --------------------------------------------------------------------------- #
# render
# --------------------------------------------------------------------------- #

def render_block(bid, blocks, id2slug, id2title, indent=0, num_ctx=None):
    rec = blocks.get(bid)
    if not rec:
        return []
    v = bval(rec)
    t = v.get("type")
    props = v.get("properties", {})
    pad = "  " * indent
    lines = []

    def txt(key="title"):
        return rich_to_md(props.get(key, []), id2slug, id2title)

    if t == "text":
        s = txt()
        lines.append(pad + s if s else "")
    elif t == "header":
        lines.append(f"## {txt()}")
    elif t == "sub_header":
        lines.append(f"### {txt()}")
    elif t == "sub_sub_header":
        lines.append(f"#### {txt()}")
    elif t == "bulleted_list":
        lines.append(f"{pad}- {txt()}")
    elif t == "numbered_list":
        lines.append(f"{pad}1. {txt()}")
    elif t == "to_do":
        checked = props.get("checked", [["No"]])[0][0] == "Yes"
        lines.append(f"{pad}- [{'x' if checked else ' '}] {txt()}")
    elif t == "toggle":
        lines.append(f"{pad}<details><summary>{txt()}</summary>\n")
        for ch in v.get("content", []) or []:
            lines += render_block(ch, blocks, id2slug, id2title, indent + 1)
        lines.append(f"{pad}</details>")
        return lines
    elif t == "quote":
        lines.append(f"> {txt()}")
    elif t == "callout":
        lines.append(f"> {txt()}")
    elif t == "code":
        lang = rich_to_text(props.get("language", [["text"]])).lower()
        lines.append(f"```{lang}\n{rich_to_text(props.get('title', []))}\n```")
    elif t == "divider":
        lines.append("---")
    elif t == "header_toggle" or t == "toggle_heading":
        lines.append(f"## {txt()}")
    elif t in ("column_list",):
        for ch in v.get("content", []) or []:
            lines += render_block(ch, blocks, id2slug, id2title, indent)
        return lines
    elif t == "column":
        for ch in v.get("content", []) or []:
            lines += render_block(ch, blocks, id2slug, id2title, indent)
        return lines
    elif t == "page":
        pid = dashify(bid)
        title = id2title.get(pid, title_of(v))
        slug = id2slug.get(pid)
        if slug:
            lines.append(f"{pad}- 📄 [{title}]({slug})")
        else:
            lines.append(f"{pad}- 📄 {title}")
        return lines
    elif t in ("collection_view", "collection_view_page"):
        # handled by caller via node cache; placeholder
        lines.append("<!-- collection rendered separately -->")
        return lines
    elif t == "image":
        src = ""
        fmt = v.get("format", {})
        src = fmt.get("display_source") or ""
        if src:
            lines.append(f"![image]({src})")
    else:
        s = txt()
        if s:
            lines.append(pad + s)

    # generic children (for list items with nested content)
    if t in ("bulleted_list", "numbered_list", "to_do"):
        for ch in v.get("content", []) or []:
            lines += render_block(ch, blocks, id2slug, id2title, indent + 1)
    return lines


def render_collection_table(node, cid, id2slug, id2title):
    schema, row_ids, cblocks = node._coll_cache[cid]
    # column order: non-title first by schema order, title last-ish -> keep title first
    cols = []
    title_key = None
    for k, meta in schema.items():
        if meta.get("type") == "title":
            title_key = k
        else:
            cols.append((k, meta.get("name", k)))
    header = ["이름"] + [c[1] for c in cols]
    lines = ["| " + " | ".join(header) + " |",
             "| " + " | ".join(["---"] * len(header)) + " |"]
    for rid in row_ids:
        rrec = cblocks.get(rid)
        if not rrec:
            continue
        rv = bval(rrec)
        if rv.get("type") != "page":
            continue
        rprops = rv.get("properties", {})
        pid = dashify(rid)
        title = id2title.get(pid, title_of(rv))
        slug = id2slug.get(pid)
        name_cell = f"[{title}]({slug})" if slug else title
        cells = [name_cell]
        for k, _ in cols:
            val = rich_to_md(rprops.get(k, []), id2slug, id2title)
            val = val.replace("\n", "<br>").replace("|", "\\|")
            cells.append(val)
        lines.append("| " + " | ".join(cells) + " |")
    return lines


def render_page(node, id2slug, id2title):
    blocks = node.blocks
    out = []
    out.append(f"# {node.title or '(제목 없음)'}")
    out.append("")
    if node.parent:
        pslug = id2slug.get(node.parent)
        ptitle = id2title.get(node.parent, "상위")
        if pslug:
            out.append(f"> ⬆ 상위: [{ptitle}]({pslug})")
            out.append("")

    rootv = bval(blocks.get(node.pid, {}))
    content_ids = rootv.get("content", []) if rootv else []

    for bid in content_ids:
        rec = blocks.get(bid)
        if not rec:
            continue
        v = bval(rec)
        t = v.get("type")
        if t in ("collection_view", "collection_view_page"):
            cid = dashify(v.get("collection_id"))
            if getattr(node, "_coll_cache", None) and cid in node._coll_cache:
                out.append("")
                out += render_collection_table(node, cid, id2slug, id2title)
                out.append("")
            continue
        if t == "toggle":
            # toggle may contain a collection
            out += render_toggle_with_coll(node, bid, id2slug, id2title, 0)
            continue
        out += render_block(bid, blocks, id2slug, id2title)

    # trailing: sub-page/row links not already inline (safety)
    text = "\n".join(out)
    return text.rstrip() + "\n"


def render_toggle_with_coll(node, bid, id2slug, id2title, indent):
    blocks = node.blocks
    v = bval(blocks.get(bid, {}))
    props = v.get("properties", {})
    summary = rich_to_md(props.get("title", []), id2slug, id2title)
    lines = [f"<details><summary>{summary}</summary>\n"]
    for ch in v.get("content", []) or []:
        cv = bval(blocks.get(ch, {}))
        ct = cv.get("type")
        if ct in ("collection_view", "collection_view_page"):
            cid = dashify(cv.get("collection_id"))
            if getattr(node, "_coll_cache", None) and cid in node._coll_cache:
                lines += render_collection_table(node, cid, id2slug, id2title)
            continue
        lines += render_block(ch, blocks, id2slug, id2title, indent + 1)
    lines.append("\n</details>")
    return lines


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="out")
    ap.add_argument("--cache", metavar="DIR", default=None,
                    help="HTTP 응답 디스크 캐시 디렉토리 (개발용; 지정 시 재실행이 빠르고 429 회피)")
    args = ap.parse_args()

    global CACHE_DIR
    if args.cache:
        CACHE_DIR = args.cache
        os.makedirs(CACHE_DIR, exist_ok=True)

    print("crawl 시작...", file=sys.stderr)
    nodes, order = crawl(ROOT_PAGE_ID)
    print(f"페이지 {len(order)}개 발견", file=sys.stderr)
    id2slug, id2title = assign_slugs(nodes, order)

    stamp = time.strftime("%Y-%m-%d %H:%M")
    banner = (
        "<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->\n"
        "<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->\n"
        f"<!-- 마지막 동기화: {stamp} -->\n\n"
    )

    # 이전 실행 산출물 정리 (스테일 파일 제거) 후 재생성
    if os.path.isdir(args.out):
        for fn in os.listdir(args.out):
            if fn.endswith(".md"):
                os.remove(os.path.join(args.out, fn))
    os.makedirs(args.out, exist_ok=True)
    for pid in order:
        node = nodes[pid]
        md = render_page(node, id2slug, id2title)
        path = os.path.join(args.out, id2slug[pid])
        with open(path, "w", encoding="utf-8") as f:
            f.write(banner + md)
        print(f"  {id2slug[pid]}  ({node.kind})", file=sys.stderr)
    print(f"완료: {len(order)}개 파일 -> {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
