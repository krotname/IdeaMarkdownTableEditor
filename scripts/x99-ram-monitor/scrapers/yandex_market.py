"""Яндекс Маркет: самый агрессивный SmartCaptcha, поэтому выключен по
умолчанию (markets.yandex_market.enabled: false). Селекторы выдачи ЯМ
меняются часто — парсим максимально обобщённо: ссылки на карточки + цена
рядом.
"""
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
  const links = document.querySelectorAll(
    'a[href*="/product--"], a[href*="/card/"], [data-auto="snippet-link"]');
  for (const a of links) {
    const href = a.href ? a.href.split('?')[0] : '';
    if (!href || seen.has(href)) continue;
    let tile = a;
    for (let i = 0; i < 8 && tile.parentElement; i++) {
      tile = tile.parentElement;
      if (/₽/.test(tile.textContent)) break;
    }
    const title = a.textContent.trim();
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
    with open_page("yandex_market", headless=headless) as page:
        for query in queries:
            url = f"https://market.yandex.ru/search?text={quote(query)}&how=aprice"
            page.goto(url, timeout=timeout_ms, wait_until="domcontentloaded")
            page.wait_for_timeout(3000)
            content = page.content()
            if blocked_by_antibot(content):
                raise RuntimeError(
                    "Яндекс Маркет показал капчу. Запустите один раз с "
                    "headless: false, пройдите её — профиль сохранится в "
                    ".profiles/yandex_market."
                )
            for raw in page.evaluate(_COLLECT_JS):
                m = re.search(r"(\d+)/?$", raw["href"])
                pid = m.group(1) if m else raw["href"]
                price = parse_price_rub(raw["text"])
                if not price:
                    continue
                listings[pid] = Listing(
                    marketplace="yandex_market",
                    id=pid,
                    title=raw["title"][:200],
                    price_rub=price,
                    url=raw["href"],
                )
            page.wait_for_timeout(1500)
    return list(listings.values())
