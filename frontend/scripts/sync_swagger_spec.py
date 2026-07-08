#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen


DEFAULT_URL = "https://chamchamcham.jaehyuns.com/v3/api-docs"


def sort_json(value):
    if isinstance(value, dict):
        return {key: sort_json(value[key]) for key in sorted(value)}
    if isinstance(value, list):
        return [sort_json(item) for item in value]
    return value


def load_remote(url):
    request = Request(
        url,
        headers={
            "Accept": "application/json",
            "User-Agent": "ChamChamCham-Swagger-Contract-Watch/1.0",
        },
    )
    with urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def render_summary(spec, digest):
    paths = spec.get("paths", {})
    schemas = spec.get("components", {}).get("schemas", {})
    operations = []
    for path in sorted(paths):
        for method in sorted(paths[path]):
            operation = paths[path][method]
            operations.append((method.upper(), path, operation.get("operationId", "")))

    lines = [
        "# Swagger API Summary",
        "",
        f"- Source title: `{spec.get('info', {}).get('title', '')}`",
        f"- Version: `{spec.get('info', {}).get('version', '')}`",
        f"- SHA-256: `{digest}`",
        f"- Paths: `{len(paths)}`",
        f"- Operations: `{len(operations)}`",
        f"- Schemas: `{len(schemas)}`",
        "",
        "## Operations",
        "",
        "| Method | Path | operationId |",
        "| --- | --- | --- |",
    ]
    lines.extend(f"| {method} | `{path}` | `{operation_id}` |" for method, path, operation_id in operations)
    lines.extend(["", "## Schemas", ""])
    lines.extend(f"- `{name}`" for name in sorted(schemas))
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=DEFAULT_URL)
    parser.add_argument("--out", default="docs/swagger")
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    out_dir = Path(args.out)
    spec = sort_json(load_remote(args.url))
    body = json.dumps(spec, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    digest = hashlib.sha256(body.encode("utf-8")).hexdigest()
    summary = render_summary(spec, digest)

    openapi_path = out_dir / "openapi.json"
    old_body = openapi_path.read_text(encoding="utf-8") if openapi_path.exists() else ""
    changed = old_body != body

    if args.write or args.check:
        out_dir.mkdir(parents=True, exist_ok=True)
        openapi_path.write_text(body, encoding="utf-8")
        (out_dir / "openapi.sha256").write_text(digest + "\n", encoding="utf-8")
        (out_dir / "summary.md").write_text(summary, encoding="utf-8")

    if args.check and changed:
        raise SystemExit(2)

    print(digest)


if __name__ == "__main__":
    main()
