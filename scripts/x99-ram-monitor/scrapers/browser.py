"""Общий запуск Playwright для маркетплейсов с антиботом (Ozon, Avito, ЯМ).

Используется persistent-профиль: после одного ручного (headless=false)
прохода антибот-проверки куки сохраняются в .profiles/<имя> и дальше
мониторинг ходит уже без капчи. Playwright ставится отдельно:

    pip install playwright && playwright install chromium
"""
from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path

PROFILES_DIR = Path(__file__).resolve().parent.parent / ".profiles"

_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)


@contextmanager
def open_page(profile: str, headless: bool = True):
    try:
        from playwright.sync_api import sync_playwright
    except ImportError as e:  # noqa: F841
        raise RuntimeError(
            "Для этого маркетплейса нужен Playwright: "
            "pip install playwright && playwright install chromium"
        )

    user_data = PROFILES_DIR / profile
    user_data.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as pw:
        ctx = pw.chromium.launch_persistent_context(
            user_data_dir=str(user_data),
            headless=headless,
            user_agent=_UA,
            viewport={"width": 1440, "height": 900},
            locale="ru-RU",
            args=["--disable-blink-features=AutomationControlled"],
        )
        try:
            page = ctx.pages[0] if ctx.pages else ctx.new_page()
            yield page
        finally:
            ctx.close()


def blocked_by_antibot(page_content: str) -> bool:
    markers = (
        "Доступ ограничен",
        "Подтвердите, что вы не робот",
        "Please confirm that you",
        "SmartCaptcha",
        "checkcaptcha",
    )
    return any(m in page_content for m in markers)
