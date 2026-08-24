#!/usr/bin/env python3
"""Мониторинг дешёвой DDR4 ECC REG памяти для X99 на WB/Ozon/Авито/ЯМ.

«Выгоднее рынка» — это предложение, у которого цена за гигабайт ниже
абсолютного порога из config.yaml (target.max_price_per_gb, для Авито —
свой порог в overrides) ИЛИ ниже медианы текущей выдачи с дисконтом
(target.median_discount). Уже показанные объявления запоминаются в
state.json и повторно не присылаются, пока цена не упадёт ещё на
renotify_drop_pct процентов.

Примеры:
    python monitor.py --once            # один проход
    python monitor.py --loop 900        # каждые ~15 минут
    python monitor.py --once -m wildberries,avito
    python monitor.py --selftest        # проверка логики без сети
"""
from __future__ import annotations

import argparse
import json
import random
import statistics
import sys
import time
from pathlib import Path

import yaml

import notify
from scrapers import avito, ozon, wildberries, yandex_market
from scrapers.common import Listing, enrich, title_matches

BASE_DIR = Path(__file__).resolve().parent

_FETCHERS = {
    "wildberries": lambda cfg, q: wildberries.fetch(
        q, pages=cfg.get("pages", 2)
    ),
    "ozon": lambda cfg, q: ozon.fetch(q, headless=cfg.get("headless", True)),
    "avito": lambda cfg, q: avito.fetch(
        q, region=cfg.get("region", "all"), headless=cfg.get("headless", True)
    ),
    "yandex_market": lambda cfg, q: yandex_market.fetch(
        q, headless=cfg.get("headless", True)
    ),
}


def load_config(path: Path) -> dict:
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f)


def collect(config: dict, only_markets: list[str] | None) -> list[Listing]:
    queries = config["queries"]
    out: list[Listing] = []
    for name, mcfg in (config.get("markets") or {}).items():
        if only_markets and name not in only_markets:
            continue
        if not mcfg.get("enabled", False) and not only_markets:
            continue
        fetcher = _FETCHERS.get(name)
        if not fetcher:
            print(f"! Неизвестный маркетплейс в конфиге: {name}")
            continue
        try:
            found = fetcher(mcfg, queries)
            print(f"  {name}: {len(found)} предложений")
            out.extend(found)
        except Exception as e:
            print(f"! {name}: {e}")
    return out


def select_deals(listings: list[Listing], config: dict) -> list[Listing]:
    """Фильтрация по заголовку, обогащение ₽/ГБ и отбор «дешевле рынка»."""
    filters = config.get("filters") or {}
    target = config.get("target") or {}
    min_cap = int(target.get("min_capacity_gb", 8))

    valid: list[Listing] = []
    for l in listings:
        if not title_matches(l.title, filters):
            continue
        if enrich(l, min_cap):
            valid.append(l)

    per_gb = [l.price_per_gb for l in valid if l.price_per_gb]
    median = statistics.median(per_gb) if per_gb else None
    discount = float(target.get("median_discount", 0.75))
    overrides = target.get("overrides") or {}

    deals = []
    for l in valid:
        cap_price = float(
            (overrides.get(l.marketplace) or {}).get(
                "max_price_per_gb", target.get("max_price_per_gb", 200)
            )
        )
        by_absolute = l.price_per_gb <= cap_price
        by_median = median is not None and l.price_per_gb <= median * discount
        if by_absolute or by_median:
            deals.append(l)
    deals.sort(key=lambda l: l.price_per_gb)
    if median:
        print(f"  медиана выдачи: {median:.0f} ₽/ГБ, годных позиций: {len(valid)}")
    return deals


def filter_new(deals: list[Listing], state_path: Path, drop_pct: float) -> list[Listing]:
    """Оставить только новые объявления или заметно подешевевшие старые."""
    state = {}
    if state_path.exists():
        state = json.loads(state_path.read_text(encoding="utf-8"))
    fresh = []
    now = int(time.time())
    for d in deals:
        prev = state.get(d.key)
        if prev is None or d.price_rub <= prev["price"] * (1 - drop_pct / 100):
            fresh.append(d)
            state[d.key] = {"price": d.price_rub, "ts": now, "title": d.title[:80]}
    state_path.write_text(
        json.dumps(state, ensure_ascii=False, indent=1), encoding="utf-8"
    )
    return fresh


def run_once(config: dict, args) -> None:
    print(f"— проход {time.strftime('%Y-%m-%d %H:%M:%S')}")
    only = args.markets.split(",") if args.markets else None
    listings = collect(config, only)
    deals = select_deals(listings, config)
    if args.dry_run:
        notify.send_console(deals)
        return
    fresh = filter_new(
        deals,
        BASE_DIR / config.get("state_file", "state.json"),
        float(config.get("renotify_drop_pct", 10)),
    )
    if not fresh:
        print("  нового ничего: все выгодные позиции уже присылались")
        return
    notify.send_console(fresh)
    if (config.get("notify") or {}).get("telegram"):
        notify.send_telegram(fresh)


def selftest() -> int:
    """Проверка логики разбора/отбора на синтетике — без сети."""
    from scrapers.common import parse_capacity_gb, parse_price_rub

    cases = {
        "Оперативная память ddr4 64 ГБ (4х16Gb) 2133 МГц ECC For X99": 64,
        "Atermiter DDR4 16GB 2133MHz PC4-17000 ECC REG": 16,
        "Samsung 2 x 16GB DDR4 2400T RDIMM серверная": 32,
        "DDR4 8Гб 2666 ECC REG Hynix": 8,
        "Комплект 4X32GB DDR4 PC4-2133P": 128,
        "Xeon E5-2670v3 процессор": None,
    }
    ok = True
    for title, want in cases.items():
        got = parse_capacity_gb(title)
        status = "ok" if got == want else "FAIL"
        ok = ok and got == want
        print(f"  [{status}] {title!r} -> {got} (ждали {want})")

    assert parse_price_rub("18 672 ₽ 29 010 ₽") == 18672
    assert parse_price_rub("цена 3500 руб.") == 3500

    config = {
        "filters": {
            "include": "ddr4",
            "require_any": ["ecc", "reg", "сервер"],
            "exclude": ["ddr3", "sodimm", "ноутбук"],
        },
        "target": {
            "max_price_per_gb": 220,
            "median_discount": 0.75,
            "min_capacity_gb": 8,
            "overrides": {"avito": {"max_price_per_gb": 160}},
        },
    }
    listings = [
        Listing("ozon", "1", "Atermiter DDR4 64 ГБ (4х16Gb) 2133 ECC REG X99", 18672, "u1"),
        Listing("ozon", "2", "Samsung DDR4 16GB 2133 ECC REG", 9219, "u2"),
        Listing("avito", "3", "Hynix DDR4 16Gb 2133P ECC REG серверная", 2200, "u3"),
        Listing("avito", "4", "Hynix DDR4 16Gb 2133 ECC REG", 3500, "u4"),
        Listing("ozon", "5", "Ноутбук с DDR4 16GB", 45000, "u5"),
        Listing("wildberries", "6", "DDR3 16GB ECC REG", 900, "u6"),
    ]
    deals = select_deals(listings, config)
    keys = {d.key for d in deals}
    # 1: 292 ₽/ГБ — дороже порога 220, но это и не «дешевле рынка»; 3: 137 ₽/ГБ
    # проходит порог Авито; 4: 219 ₽/ГБ — выше авитовского порога 160, но ниже
    # 75% медианы? медиана(292, 576, 137, 219)=255, 0.75*255=191 → нет. Итог: только 3.
    expect = {"avito:3"}
    print(f"  сделки: {sorted(keys)} (ждали {sorted(expect)})")
    ok = ok and keys == expect

    import tempfile

    with tempfile.TemporaryDirectory() as td:
        sp = Path(td) / "state.json"
        first = filter_new(list(deals), sp, drop_pct=10)
        second = filter_new(list(deals), sp, drop_pct=10)
        deals[0].price_rub *= 0.5
        third = filter_new(list(deals), sp, drop_pct=10)
        print(
            f"  дедуп: 1-й прогон {len(first)}, повтор {len(second)}, "
            f"после падения цены {len(third)}"
        )
        ok = ok and (len(first), len(second), len(third)) == (1, 0, 1)

    print("selftest:", "OK" if ok else "FAILED")
    return 0 if ok else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--config", default=str(BASE_DIR / "config.yaml"))
    ap.add_argument("--once", action="store_true", help="один проход (по умолчанию)")
    ap.add_argument("--loop", type=int, metavar="SEC", help="повторять каждые SEC секунд")
    ap.add_argument("-m", "--markets", help="только эти маркетплейсы, через запятую")
    ap.add_argument("--dry-run", action="store_true", help="показать сделки, не трогая state и телеграм")
    ap.add_argument("--selftest", action="store_true", help="проверить логику без сети")
    args = ap.parse_args()

    if args.selftest:
        return selftest()

    config = load_config(Path(args.config))
    if args.loop:
        if args.loop < 600:
            print("! интервал меньше 10 минут — маркетплейсы быстро забанят, ставим 600")
            args.loop = 600
        while True:
            run_once(config, args)
            delay = args.loop * random.uniform(0.9, 1.2)  # джиттер против банов
            print(f"  сплю {delay:.0f} c\n")
            time.sleep(delay)
    else:
        run_once(config, args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
