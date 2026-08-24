"""Wildberries: официального API нет, но поисковый JSON-эндпоинт открыт.

Работает обычным requests, без браузера. Формат ответа меняется между
v4/v5 — цену достаём из нескольких возможных полей.
"""
from __future__ import annotations

from typing import Iterable, List
from urllib.parse import quote

import requests

from .common import Listing

_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
    "Accept": "application/json",
}

_SEARCH_URLS = (
    # v5 — актуальный на момент написания; v4 оставлен как запасной.
    "https://search.wb.ru/exactmatch/ru/common/v5/search"
    "?ab_testing=false&appType=1&curr=rub&dest=-1257786&resultset=catalog"
    "&sort=priceup&spp=30&suppressSpellcheck=false&page={page}&query={query}",
    "https://search.wb.ru/exactmatch/ru/common/v4/search"
    "?appType=1&curr=rub&dest=-1257786&resultset=catalog"
    "&sort=priceup&spp=30&page={page}&query={query}",
)


def _product_price_rub(product: dict) -> float:
    sizes = product.get("sizes") or []
    if sizes:
        price = (sizes[0].get("price") or {}).get("total")
        if price:
            return price / 100
    for key in ("salePriceU", "priceU"):
        if product.get(key):
            return product[key] / 100
    return 0.0


def fetch(queries: Iterable[str], pages: int = 2, timeout: int = 20) -> List[Listing]:
    listings: dict[str, Listing] = {}
    session = requests.Session()
    session.headers.update(_HEADERS)
    for query in queries:
        for template in _SEARCH_URLS:
            got_any = False
            for page in range(1, pages + 1):
                url = template.format(page=page, query=quote(query))
                try:
                    resp = session.get(url, timeout=timeout)
                    resp.raise_for_status()
                    products = (resp.json().get("data") or {}).get("products") or []
                except Exception:
                    break  # пробуем следующую версию эндпоинта
                for p in products:
                    pid = str(p.get("id", ""))
                    price = _product_price_rub(p)
                    if not pid or price <= 0:
                        continue
                    got_any = True
                    listings[pid] = Listing(
                        marketplace="wildberries",
                        id=pid,
                        title=f"{p.get('brand', '')} {p.get('name', '')}".strip(),
                        price_rub=price,
                        url=f"https://www.wildberries.ru/catalog/{pid}/detail.aspx",
                    )
                if len(products) == 0:
                    break
            if got_any:
                break  # эта версия эндпоинта работает — v4 не нужен
    return list(listings.values())
