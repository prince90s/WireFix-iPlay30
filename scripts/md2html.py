import markdown, re, sys, pathlib

SRC = pathlib.Path(r"C:/Users/Q/WorkBuddy/2026-10-19-11-56-41/WireFix-kit/docs/3.5mm音频修复教程.md")
DST = pathlib.Path(r"C:/Users/Q/WorkBuddy/2026-10-19-11-56-41/WireFix-kit/docs/3.5mm音频修复教程.html")

md_text = SRC.read_text(encoding="utf-8")

# 提取原目录（## 目录 到下一个 --- 之间），稍后替换为带锚点的可点击目录
toc_entries = re.findall(r"^\d+\.\s+\[([^\]]+)\]\(#[^)]*\)", md_text, re.M)

# 去掉目录里的锚点链接文字形式，改为纯文本占位
md_text = re.sub(r"\[([^\]]+)\]\(#[^)]*\)", r"\1", md_text)

html_body = markdown.markdown(
    md_text,
    extensions=["fenced_code", "tables", "toc", "codehilite", "sane_lists", "attr_list"],
    extension_configs={
        "codehilite": {"css_class": "hl", "guess_lang": False},
        "toc": {"permalink": False},
    },
)

TPL = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>3.5mm 有线耳机无声修复完全指南</title>
<style>
:root {{
  --bg: #ffffff;
  --fg: #1f2328;
  --muted: #59636e;
  --border: #d8dee4;
  --accent: #0969da;
  --code-bg: #f6f8fa;
  --warn-bg: #fff8e6;
  --warn-bd: #f0b429;
}}
* {{ box-sizing: border-box; }}
body {{
  margin: 0;
  background: var(--bg);
  color: var(--fg);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
               "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  font-size: 15px;
  line-height: 1.75;
}}
.wrap {{ max-width: 860px; margin: 0 auto; padding: 48px 24px 120px; }}

h1 {{ font-size: 30px; font-weight: 700; margin: 0 0 8px; letter-spacing: -0.3px; }}
h2 {{
  font-size: 22px; font-weight: 650; margin: 56px 0 16px;
  padding-bottom: 8px; border-bottom: 1px solid var(--border);
}}
h3 {{ font-size: 17px; font-weight: 650; margin: 32px 0 12px; }}
h4 {{ font-size: 15px; font-weight: 650; margin: 24px 0 8px; color: var(--muted); }}

p {{ margin: 12px 0; }}
a {{ color: var(--accent); text-decoration: none; }}
a:hover {{ text-decoration: underline; }}

blockquote {{
  margin: 16px 0; padding: 12px 18px;
  background: var(--warn-bg); border-left: 4px solid var(--warn-bd);
  border-radius: 0 6px 6px 0; color: #4a3800;
}}
blockquote p {{ margin: 6px 0; }}

code {{
  background: var(--code-bg); padding: 2px 6px; border-radius: 4px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px; border: 1px solid rgba(0,0,0,0.04);
}}
pre {{
  background: #f6f8fa; border: 1px solid var(--border); border-radius: 8px;
  padding: 16px 18px; overflow-x: auto; margin: 16px 0;
  font-size: 13px; line-height: 1.6;
}}
pre code {{ background: none; padding: 0; border: none; font-size: 13px; }}

table {{
  border-collapse: collapse; width: 100%; margin: 18px 0;
  font-size: 14px; display: block; overflow-x: auto;
}}
th, td {{ border: 1px solid var(--border); padding: 9px 13px; text-align: left; vertical-align: top; }}
th {{ background: #f6f8fa; font-weight: 650; }}
tr:nth-child(even) td {{ background: #fcfcfd; }}

hr {{ border: none; border-top: 1px solid var(--border); margin: 44px 0; }}

ul, ol {{ padding-left: 26px; margin: 12px 0; }}
li {{ margin: 6px 0; }}

.toc {{ background: #f6f8fa; border: 1px solid var(--border); border-radius: 8px; padding: 18px 24px; margin: 28px 0; }}
.toc > ul {{ list-style: none; padding-left: 0; margin: 0; }}
.toc li {{ margin: 5px 0; }}

.diagram {{
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px; line-height: 1.35; white-space: pre; overflow-x: auto;
  background: #fafbfc; border: 1px solid var(--border); border-radius: 8px;
  padding: 18px; margin: 18px 0;
}}

.meta {{ color: var(--muted); font-size: 14px; margin-bottom: 4px; }}
.badge {{
  display: inline-block; background: #ddf4ff; color: #0969da;
  border-radius: 20px; padding: 2px 12px; font-size: 12px;
  font-weight: 600; margin-right: 6px;
}}

@media print {{
  .wrap {{ max-width: none; padding: 0; }}
  h2 {{ page-break-after: avoid; }}
  pre, table, blockquote {{ page-break-inside: avoid; }}
}}
</style>
</head>
<body>
<div class="wrap">
{body}
</div>
</body>
</html>
"""

html = TPL.format(body=html_body)

# 把「目录」下的纯文本 ol 替换成可点击的锚点目录
def build_nav(_m):
    items = []
    for i, title in enumerate(toc_entries, 1):
        slug = re.sub(r"[^\w\u4e00-\u9fff]", "", title)
        items.append(f'<li><a href="#{slug}">{title}</a></li>')
    return '<nav class="toc"><ol>' + "".join(items) + "</ol></nav>"

html = re.sub(
    r'(<h2 id="_1">目录</h2>\s*)<ol>.*?</ol>',
    lambda m: m.group(1) + '<nav class="toc"><ol>' + build_nav(m).replace('<nav class="toc"><ol>', "").replace("</ol></nav>", "") + "</ol></nav>",
    html,
    flags=re.S,
)

# 给 h2 加上与目录一致的 id
def add_ids(m):
    num, title = m.group(1), m.group(2)
    slug = re.sub(r"[^\w\u4e00-\u9fff]", "", title)
    return f'<h2 id="{slug}">{num}. {title}</h2>'

html = re.sub(r'<h2 id="[^"]*">(\d+)\.\s*([^<]+)</h2>', add_ids, html)

DST.write_text(html, encoding="utf-8")
print("written:", DST)
print("size:", DST.stat().st_size)
print("toc entries:", len(toc_entries))
