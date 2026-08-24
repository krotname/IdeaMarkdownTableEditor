"""Ozon: выдача поиска, сортировка по цене. Требует Playwright (антибот)."""
from __future__ import annotations

import re
from typing import Iterable, List
from urllib.parse import quote

from .browser import blocked_by_antibot, open_page
from .common import Listing, parse_price_rub

_COLLECT_JS = """
() => {
  const seen = new Set();
  const out = [];
  for (const a of document.querySelectorAll('a[href*="/product/"]')) {
    const href = a.href.split('?')[0];
    if (seen.has(href)) continue;
    // Поднимаемся к плитке товара, чтобы взять текст с ценой.
    let tile = a;
    for (let i = 0; i < 6 && tile.parentElement; i++) {
      tile = tile.parentElement;
      if (tile.querySelector('span') && /₽/.test(tile.textContent)) break;
    }
    const title = a.textContent.trim() || a.getAttribute('aria-label') || '';
    if (!title || !/₽/.test(tile.textContent)) continue;
    seen.add(href);
    out.push({ href, title, text: tile.textContent.slice(0, 400) });
  }
  return out;
}
"""


def fetch(
    queries: Iterable[str], headless: bool = True, timeout_ms: int = 30000
) -> List[Listing]:
    listings: dict[str, Listing] = {}
    with open_page("ozon", headless=headless) as page:
        for query in queries:
            url = f"https://www.ozon.ru/search/?text={quote(query)}&sorting=price"
            page.goto(url, timeout=timeout_ms, wait_until="domcontentloaded")
            try:
                page.wait_for_selector('a[href*="/product/"]', timeout=timeout_ms)
            except Exception:
                if blocked_by_antibot(page.content()):
                    raise RuntimeError(
                        "Ozon показал антибот-страницу. Запустите один раз с "
                        "headless: false в config.yaml и пройдите проверку — "
                        "профиль сохранится в .profiles/ozon."
                    )
                continue
            page.wait_for_timeout(1000)  # даём выдаче дорисоваться
            for raw in page.evaluate(_COLLECT_JS):
                m = re.search(r"/product/[^/]*?-?(\d+)/?$", raw["href"])
                pid = m.group(1) if m else raw["href"]
                price = parse_price_rub(raw["text"])
                if not price:
                    continue
                listings[pid] = Listing(
                    marketplace="ozon",
                    id=pid,
                    title=raw["title"][:200],
                    price_rub=price,
                    url=raw["href"],
                )
            page.wait_for_timeout(1500)
    return list(listings.values())
