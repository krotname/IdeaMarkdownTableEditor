"""Уведомления о найденных выгодных предложениях: консоль + Telegram.

Telegram включается в config.yaml (notify.telegram: true) и берёт
креды из окружения: TG_BOT_TOKEN (токен бота от @BotFather) и
TG_CHAT_ID (ваш chat id, узнать у @userinfobot).
"""
from __future__ import annotations

import os
from typing import Iterable

import requests

from scrapers.common import Listing

_MARKET_NAMES = {
    "wildberries": "Wildberries",
    "ozon": "Ozon",
    "avito": "Авито",
    "yandex_market": "Яндекс Маркет",
}


def _fmt_rub(value: float) -> str:
    return f"{value:,.0f}".replace(",", " ")


def format_deal(l: Listing) -> str:
    extra = f" · {l.extra}" if l.extra else ""
    return (
        f"[{_MARKET_NAMES.get(l.marketplace, l.marketplace)}] "
        f"{_fmt_rub(l.price_rub)} ₽ ({l.price_per_gb:.0f} ₽/ГБ, {l.capacity_gb} ГБ)"
        f"{extra}\n  {l.title}\n  {l.url}"
    )


def send_console(deals: Iterable[Listing]) -> None:
    for d in deals:
        print("🔥 " + format_deal(d) + "\n")


def send_telegram(deals: list[Listing], timeout: int = 15) -> bool:
    token = os.environ.get("TG_BOT_TOKEN")
    chat_id = os.environ.get("TG_CHAT_ID")
    if not token or not chat_id:
        print("! Telegram не настроен (нужны TG_BOT_TOKEN и TG_CHAT_ID)")
        return False
    text = "🔥 Память для X99 дешевле рынка:\n\n" + "\n\n".join(
        format_deal(d) for d in deals
    )
    # Телеграм ограничивает сообщение 4096 символами — режем по предложениям.
    ok = True
    while text:
        chunk, text = text[:4000], text[4000:]
        resp = requests.post(
            f"https://api.telegram.org/bot{token}/sendMessage",
            json={"chat_id": chat_id, "text": chunk, "disable_web_page_preview": True},
            timeout=timeout,
        )
        ok = ok and resp.ok
        if not resp.ok:
            print(f"! Telegram ответил {resp.status_code}: {resp.text[:200]}")
    return ok
