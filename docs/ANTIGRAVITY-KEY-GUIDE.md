# Bubby: Antigravity va bepul kalit

## Muhim: Antigravity uchun API kalit yo'q

Bubby kodida «antigravity» faqat **agent nomi** sifatida uchraydi (kartochka rangi uchun, `AgentWidgets.kt`). Ilovada Antigravity **AI provayder sifatida ulanmaydi**: «Подключение ИИ» ekranida faqat Groq, OpenRouter, Google Gemini, Anthropic va «Свой сервер / Ollama» bor (`ui/AiScreen.kt`).

Antigravity — Google'ning kod yozish muhiti (IDE). U Google hisobi bilan kiriladi, Bubby'ga qo'yiladigan alohida API kaliti bermaydi. Bu qismi rasmiy hujjatdan tekshirilmadi, faqat umumiy ma'lumot; aniq shartlarni Google'ning rasmiy Antigravity sahifasidan ko'ring.

## Nima qilish kerak: Google Gemini bepul kaliti

Google'ning bepul kaliti Bubby'da ishlaydi (Antigravity bilan bir kompaniya):

1. Oching: **https://aistudio.google.com/apikey**
2. Google hisobingiz bilan kiring.
3. **Get API key** tugmasini bosing va kalit yarating.
4. `AIza…` bilan boshlanadigan kalitni nusxalang.
5. Bubby'da: **Ещё → Подключение ИИ** → Google Gemini → kalitni qo'ying (**Вставить из буфера**).

Eslatma: bepul so'rovlar kuniga kam, tugab qolishi mumkin. Shunda Groq (`docs/GROQ-KEY-GUIDE.md`) yoki OpenRouter'ni zaxira qilib qo'shing.

Kalitni hech kimga ko'rsatmang.

## Noma'lum

- Bubby'ga Antigravity'ni to'g'ridan-to'g'ri ulash usuli repoda yo'q. Agar kerak bo'lsa, buni alohida qo'shish kerak.

---

# Bubby: Antigravity и бесплатный ключ

## Важно: для Antigravity API-ключа нет

В коде Bubby «antigravity» встречается только как **имя агента** (цвет карточки, `AgentWidgets.kt`). Как **ИИ-провайдер** Antigravity в приложении не подключается: на экране «Подключение ИИ» есть только Groq, OpenRouter, Google Gemini, Anthropic и «Свой сервер / Ollama» (`ui/AiScreen.kt`).

Antigravity — среда разработки от Google, вход через аккаунт Google; отдельного API-ключа для Bubby она не выдаёт. Это не проверено по официальной документации, а только общие сведения; точные условия смотрите на официальной странице Antigravity.

## Что делать: бесплатный ключ Google Gemini

Бесплатный ключ Google в Bubby работает:

1. Откройте **https://aistudio.google.com/apikey**
2. Войдите в аккаунт Google.
3. Нажмите **Get API key** и создайте ключ.
4. Скопируйте ключ, он начинается с `AIza…`.
5. В Bubby: **Ещё → Подключение ИИ** → Google Gemini → вставьте ключ (**Вставить из буфера**).

Примечание: бесплатных запросов в день немного, они могут закончиться. Тогда добавьте запасное подключение: Groq (`docs/GROQ-KEY-GUIDE.md`) или OpenRouter.

Никому не показывайте ключ.

## Неизвестно

- Способа подключить Antigravity к Bubby напрямую в репозитории нет. Если он нужен, это придётся добавлять отдельно.
