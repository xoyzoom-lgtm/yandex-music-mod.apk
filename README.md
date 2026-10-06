# 🎵 YM Mod для Android

Неофициальный Android-клиент для веб-версии Яндекс Музыки с интеграцией Discord, выбором шрифтов и тёмной темой.

> [!NOTE]
> **Проект основан на [Vzlomhik2005/Yandex-Music-Mod](https://github.com/Vzlomhik2005/Yandex-Music-Mod)**
> (лицензия MIT) — моде для десктопного приложения Яндекс Музыки — и переделан под Android.
> Из оригинала взяты идеи и логика Discord Rich Presence, чтения состояния плеера и смены шрифтов.
> Текст оригинальной лицензии сохранён в [`app/src/main/assets/licenses/ORIGINAL_LICENSE.txt`](app/src/main/assets/licenses/ORIGINAL_LICENSE.txt)
> и доступен в приложении на экране «О программе».

> [!IMPORTANT]
> Это **легальная** версия: приложение открывает официальный сайт [music.yandex.ru](https://music.yandex.ru),
> вход выполняется через ваш Яндекс ID, а **качество звука, реклама и доступ к трекам определяются вашей подпиской**.
> В отличие от оригинала, здесь нет обхода Яндекс Плюса, подмены ответов API и скачивания треков.
> Приложение не связано с ООО «Яндекс» и Discord Inc.

## Возможности

| Функция | Статус |
|---|---|
| Яндекс Музыка в WebView с официальной авторизацией | ✅ |
| Фоновое воспроизведение, уведомление с кнопками, экран блокировки, гарнитура | ✅ |
| Чтение текущего трека (название, исполнитель, альбом, обложка, позиция) | ✅ |
| Тёмная / светлая / системная тема | ✅ |
| Смена шрифтов (системные + любой шрифт с [Bunny Fonts](https://fonts.bunny.net), как в оригинале) | ✅ |
| Десктопная версия сайта по переключателю | ✅ |
| Экран «О программе» с лицензиями | ✅ |
| Сборка APK в GitHub Actions и публикация в Releases | ✅ |
| Discord Rich Presence через Discord Social SDK | 🚧 этап 2 — данные уже собираются, осталось подключить SDK |

## Установка

1. Откройте [Releases](https://github.com/xoyzoom-lgtm/yandex-music-mod.apk/releases/latest) и скачайте `YmMod-*.apk`.
2. Разрешите установку из неизвестных источников и установите APK.
3. Войдите в свой аккаунт Яндекса.

Требуется Android 7.0+ и актуальный Android System WebView (обновляется через Google Play).

## Как это устроено

```
app/src/main/
├── assets/
│   ├── js/ymmod-bridge.js          # внедряется в страницу: состояние плеера, управление, шрифты
│   └── licenses/                    # MIT этого проекта и оригинала
├── java/io/github/xoyzoom/ymmod/
│   ├── YmModApp.kt / AppContainer.kt  # точка входа и ручное DI
│   ├── MainActivity.kt              # WebView + Compose-экраны
│   ├── web/                         # WebView, мост с JS, обработка ссылок, user agent
│   ├── player/                      # модель состояния плеера, парсер сообщений, шина команд
│   ├── playback/PlaybackService.kt  # foreground-сервис + MediaSession + уведомление
│   ├── presence/                    # Rich Presence: маппинг трека → активность, интерфейс SDK
│   ├── settings/                    # DataStore: тема, шрифт, десктоп-режим, Discord
│   └── ui/                          # Jetpack Compose: плеер, настройки, «О программе», тема
└── res/
```

**Чтение плеера.** Оригинал достаёт данные из React Fiber десктопной разметки (`PLAYERBAR_DESKTOP`),
которой в мобильной вёрстке нет. Здесь `ymmod-bridge.js` использует стандартный
[Media Session API](https://developer.mozilla.org/docs/Web/API/Media_Session_API) — через него сайт сам
публикует название, исполнителя и обложку — и события `<audio>`. Данные уходят в приложение через
`WebViewCompat.addWebMessageListener`, который доступен только страницам `music.yandex.*`
(в отличие от `addJavascriptInterface`, видимого любому сайту).

**Discord.** `PresenceController` превращает состояние плеера в `RichPresence` (название, исполнитель,
обложка, таймер) и передаёт его в `PresenceSink`. Сейчас подключён `LoggingPresenceSink`, который пишет
в logcat (`adb logcat -s YmModPresence`). На этапе 2 сюда встанет реализация на
[Discord Social SDK](https://discord.com/developers/docs/discord-social-sdk/overview): SDK скачивается
из Discord Developer Portal под его собственной лицензией, поэтому в репозиторий не кладётся.

## Сборка

Нужны JDK 17 и Android SDK (API 35).

```bash
./gradlew testDebugUnitTest   # unit-тесты
./gradlew assembleDebug       # app/build/outputs/apk/debug/
```

### Релизы

Релиз собирается автоматически при пуше тега:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Чтобы обновления ставились поверх старых версий, все релизы должны быть подписаны **одним ключом**.
Создайте ключ и добавьте секреты в *Settings → Secrets and variables → Actions*:

```bash
keytool -genkeypair -v -keystore release.jks -alias ymmod -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # → SIGNING_KEYSTORE_BASE64
```

| Секрет | Значение |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | содержимое `release.jks` в base64 |
| `SIGNING_STORE_PASSWORD` | пароль хранилища |
| `SIGNING_KEY_ALIAS` | `ymmod` |
| `SIGNING_KEY_PASSWORD` | пароль ключа |

Без секретов релиз подписывается временным debug-ключом (в логах сборки будет предупреждение).

## Лицензия

[MIT](LICENSE). © 2026 xoyzoom-lgtm (Android-версия), © 2026 Markus ([оригинальный проект](https://github.com/Vzlomhik2005/Yandex-Music-Mod)).
