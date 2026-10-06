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
| Discord Rich Presence через Discord Social SDK («Слушает …» с обложкой и таймером) | ✅ в сборке с SDK (см. ниже) |

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
app/src/presenceDiscord/             # сборка с Discord Social SDK: Kotlin + JNI (C++/CMake)
app/src/presenceStub/                # сборка без SDK: заглушка
```

**Чтение плеера.** Оригинал достаёт данные из React Fiber десктопной разметки (`PLAYERBAR_DESKTOP`),
которой в мобильной вёрстке нет. Здесь `ymmod-bridge.js` использует стандартный
[Media Session API](https://developer.mozilla.org/docs/Web/API/Media_Session_API) — через него сайт сам
публикует название, исполнителя и обложку — и события `<audio>`. Данные уходят в приложение через
`WebViewCompat.addWebMessageListener`, который доступен только страницам `music.yandex.*`
(в отличие от `addJavascriptInterface`, видимого любому сайту).

**Discord.** `PresenceController` превращает состояние плеера в `RichPresence` (название, исполнитель,
обложка, таймер) и передаёт его в `PresenceSink`. В сборке с SDK это `DiscordSocialPresenceSink`:
JNI-модуль (`app/src/presenceDiscord/cpp`) вызывает `discordpp::Client::SetApplicationId` и
`UpdateRichPresence` с типом *Listening*. Используется режим
[Rich Presence без авторизации](https://discord.com/developers/docs/discord-social-sdk/development-guides/setting-rich-presence#rich-presence-without-authentication)
(Social SDK 1.10+): активность передаётся в установленное приложение Discord, OAuth не нужен.
В сборке без SDK используется `LoggingPresenceSink`, который пишет в logcat (`adb logcat -s YmModPresence`).

## Подключение Discord Rich Presence

[Discord Social SDK](https://discord.com/developers/docs/discord-social-sdk/overview) распространяется
только через Discord Developer Portal под лицензией Discord, поэтому в репозиторий он не входит.
Без него приложение собирается и работает, просто без Discord.

1. Создайте приложение в [Discord Developer Portal](https://discord.com/developers/applications)
   и включите для него Social SDK. Название приложения увидят в профиле: «Слушает *название*».
2. Скачайте Social SDK для Android версии **1.10 или новее** и возьмите из него `discord_partner_sdk.aar`.
3. **Локальная сборка:** положите файл в `app/libs/discord_partner_sdk.aar` и укажите ID
   в `~/.gradle/gradle.properties`: `discordApplicationId=123456789012345678`.
4. **GitHub Actions:**
   - переменная репозитория `DISCORD_APPLICATION_ID` (*Settings → Secrets and variables → Actions → Variables*);
   - секрет `DISCORD_SDK_AAR_URL` — прямая ссылка на `.aar` или zip SDK в приватном хранилище.
     Скрипт `.github/scripts/fetch-discord-sdk.sh` скачает его перед сборкой.

У пользователя должно быть установлено приложение Discord с выполненным входом. Статус соединения
виден в настройках YM Mod. Разрешения SDK для голосового чата (микрофон, Bluetooth) из манифеста
убраны — приложение их не запрашивает.

## Отладка

В debug-сборке в настройках есть пункт **«Отладка: фикстуры плеера»**: готовые состояния (пауза,
длинные строки, эмодзи, нет позиции, недоступная обложка и т. д.) подменяют данные из WebView для
интерфейса, уведомления и Discord. На экране видно, какая активность уйдёт в Discord.
Сайт и его трафик не затрагиваются. Код лежит в `app/src/debug` и в релиз не попадает.

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
