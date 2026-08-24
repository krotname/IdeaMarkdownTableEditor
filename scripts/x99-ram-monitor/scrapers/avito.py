"""Avito: главный источник дешёвых б/у серверных планок.

Сортировка s=104 (сначала новые) — именно то, что нужно для мониторинга
свежих выгодных объявлений. region — слаг из URL Авито («all» — вся
Россия, «moskva», «sankt-peterburg», ...).
"""
from __future__ import annotations

from typing import Iterable, List
from urllib.parse import quote

from .browser import blocked_by_antibot, open_page
from .common import Listing, parse_price_rub

_COLLECT_JS = """
() => [...document.querySelectorAll('[data-marker="item"]')].map(el => {
  const a = el.querySelector('a[data-marker="item-title"], a[itemprop="url"]');
  const priceMeta = el.querySelector('meta[itemprop="price"]');
  const priceEl = el.querySelector('[data-marker="item-price"]');
  return {
    id: el.getAttribute('data-item-id') || (a ? a.href : ''),
    title: a ? a.textContent.trim() : '',
    href: a ? a.href : '',
    price: priceMeta ? priceMeta.getAttribute('content')
                     : (priceEl ? priceEl.textContent : ''),
    extra: (el.querySelector('[data-marker="item-address"], [class*="geo"]') || {}).textContent || ''
  };
})
"""


def fetch(
    queries: Iterable[str],
    region: str = "all",
    headless: bool = True,
    timeout_ms: int = 30000,
) -> List[Listing]:
    listings: dict[str, Listing] = {}
    with open_page("avito", headless=headless) as page:
        for query in queries:
            url = f"https://www.avito.ru/{region}?q={quote(query)}&s=104"
            page.goto(url, timeout=timeout_ms, wait_until="domcontentloaded")
            try:
                page.wait_for_selector('[data-marker="item"]', timeout=timeout_ms)
            except Exception:
                if blocked_by_antibot(page.content()):
                    raise RuntimeError(
                        "Avito показал антибот-страницу. Запустите один раз с "
                        "headless: false в config.yaml, пройдите проверку — "
                        "профиль сохранится в .profiles/avito."
                    )
                continue  # по запросу просто ничего не нашлось
            for raw in page.evaluate(_COLLECT_JS):
                price = None
                if raw.get("price"):
                    p = str(raw["price"])
                    price = float(p) if p.replace(".", "", 1).isdigit() else parse_price_rub(p)
                if not raw.get("id") or not raw.get("title") or not price:
                    continue
                lid = str(raw["id"])
                listings[lid] = Listing(
                    marketplace="avito",
                    id=lid,
                    title=raw["title"],
                    price_rub=price,
                    url=raw.get("href") or url,
                    extra=(raw.get("extra") or "").strip(),
                )
            page.wait_for_timeout(1500)  # не частить между запросами
    return list(listings.values())
