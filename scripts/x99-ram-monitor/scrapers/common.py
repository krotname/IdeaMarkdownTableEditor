"""Общие типы и разбор заголовков объявлений про DDR4-память."""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Optional

# Размеры планок/комплектов, которые считаем правдоподобными (ГБ).
PLAUSIBLE_GB = {4, 8, 16, 32, 64, 128, 256}

# «4x16Gb», «4х16 ГБ» (латинская x, кириллическая х, знак ×), «2 X 16G»
_KIT_RE = re.compile(
    r"(\d{1,2})\s*[xх×]\s*(\d{1,3})\s*(?:gb|гб|g\b)", re.IGNORECASE
)
# Одиночная ёмкость: число, сразу за которым идёт единица (GB/ГБ) —
# так «DDR4» и «PC4-17000» не дают ложных срабатываний.
_SINGLE_RE = re.compile(r"(\d{1,3})\s*(?:gb|гб)\b", re.IGNORECASE)


@dataclass
class Listing:
    """Нормализованное предложение с любого маркетплейса."""

    marketplace: str
    id: str
    title: str
    price_rub: float
    url: str
    extra: str = ""  # город, продавец и т.п.
    capacity_gb: Optional[int] = field(default=None)
    price_per_gb: Optional[float] = field(default=None)

    @property
    def key(self) -> str:
        return f"{self.marketplace}:{self.id}"


def parse_capacity_gb(title: str) -> Optional[int]:
    """Вытащить суммарный объём в ГБ из заголовка.

    Комплект «4x16Gb» имеет приоритет над одиночным числом, потому что в
    заголовках вида «64 ГБ (4х16Gb)» комплект описывает те же 64 ГБ точнее.
    """
    m = _KIT_RE.search(title)
    if m:
        count, size = int(m.group(1)), int(m.group(2))
        total = count * size
        if size in PLAUSIBLE_GB and 0 < count <= 16:
            return total

    candidates = [int(x) for x in _SINGLE_RE.findall(title)]
    candidates = [c for c in candidates if c in PLAUSIBLE_GB]
    if candidates:
        # В «64 ГБ (4х16Gb)» без kit-ветки взяли бы max — он и есть суммарный.
        return max(candidates)
    return None


def parse_price_rub(text: str) -> Optional[float]:
    """Достать первую цену в рублях из текста плитки товара."""
    m = re.search(r"(\d[\d\s   ]*)\s*(?:₽|руб)", text)
    if not m:
        return None
    digits = re.sub(r"\D", "", m.group(1))
    return float(digits) if digits else None


def title_matches(title: str, filters: dict) -> bool:
    """Фильтр по заголовку: include + require_any - exclude (см. config)."""
    low = title.lower()
    include = filters.get("include")
    if include and not re.search(include, low):
        return False
    require_any = filters.get("require_any") or []
    if require_any and not any(w.lower() in low for w in require_any):
        return False
    for w in filters.get("exclude") or []:
        if w.lower() in low:
            return False
    return True


def enrich(listing: Listing, min_capacity_gb: int) -> Optional[Listing]:
    """Заполнить объём и ₽/ГБ; отбросить мусор без объёма/цены."""
    cap = parse_capacity_gb(listing.title)
    if cap is None or cap < min_capacity_gb or listing.price_rub <= 0:
        return None
    listing.capacity_gb = cap
    listing.price_per_gb = round(listing.price_rub / cap, 1)
    return listing
