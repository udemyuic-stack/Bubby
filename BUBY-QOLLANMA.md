# Bubby: bepul AI'ni ulash (Groq)

Bubby ishlashi uchun AI xizmatiga ulanishi kerak. **Har bir foydalanuvchi o'z bepul kalitini oladi**, shunda limit boshqalar bilan bo'lishilmaydi. Karta kerak emas, 2 daqiqa.

## Qadamlar (Groq)

1. Telefonda yoki kompyuterda oching: **https://console.groq.com/keys**
2. Kiring: Google, GitHub yoki e-mail orqali (ro'yxatdan o'tish bepul).
3. **Create API Key** tugmasini bosing, ixtiyoriy nom yozing (masalan `Bubby`), **Submit** ni bosing.
4. Ko'ringan kalitni **nusxalang**. U `gsk_` bilan boshlanadi va **faqat bir marta** ko'rsatiladi.
5. Bubby ilovasida: **Ещё → Подключение ИИ** (yoki bosh sahifadagi «Подключите ИИ» kartasi) → Groq → **Вставить из буфера** → **Подключить Groq**.
6. «Готово» chiqsa, Bubby'ga gapirib ko'ring.

Ilova kalitingiz uchun **hozir mavjud** modellarni o'zi so'raydi (Groq modellarni vaqti-vaqti bilan almashtirib turadi) va uchtagacha ulanish qo'shadi, masalan:
- asosiy: `openai/gpt-oss-120b` (eng aqlli)
- zaxira: `qwen/qwen3.8-27b` va `openai/gpt-oss-20b`

Har bir modelning o'z kunlik limiti bor (2026-10-25 holatiga, bepul hisobda taxminan 1000 so'rov/kun va 8000 token/daqiqa). Biri tugasa, Bubby o'zi keyingisiga o'tadi.

## Muhim

- Kalitni **hech kimga ko'rsatmang**. U sizning hisobingizdan foydalanishga ruxsat beradi.
- Kalit faqat telefoningizda saqlanadi va faqat Groq'ga yuboriladi.
- Limitlar taxminiy va o'zgarib turadi: aniq raqamlarni console.groq.com/settings/limits da ko'ring.
- Sayt tashqi ko'rinishi biroz farq qilishi mumkin; **Create API Key** tugmasini qidiring.

## Limit tugasa nima qilish

- Biroz kuting (kunlik limit ertasi kuni tiklanadi).
- Boshqa xizmatdan **zaxira ulanish** qo'shing (OpenRouter yoki Gemini): «Подключение ИИ» ekranida har biri uchun ko'rsatma bor. Ilova ulanishlarni ketma-ket sinab ko'radi.
- Ko'p hisob ochib limitni aylanib o'tishga urinmang: bu xizmatlarning qoidalariga zid bo'lishi mumkin.

---

# Bubby: бесплатный ИИ (Groq)

Чтобы Bubby отвечал, ему нужно подключение к ИИ. **У каждого пользователя свой бесплатный ключ**, поэтому лимиты ни с кем не делятся. Карта не нужна, около 2 минут.

1. Откройте **https://console.groq.com/keys**
2. Войдите через Google, GitHub или e-mail.
3. Нажмите **Create API Key**, введите любое имя (например `Bubby`), нажмите **Submit**.
4. Скопируйте ключ. Он начинается с `gsk_` и показывается **только один раз**.
5. В приложении: **Ещё → Подключение ИИ** → Groq → **Вставить из буфера** → **Подключить Groq**.

Приложение само спросит у Groq, какие модели сейчас доступны для вашего ключа (Groq их периодически меняет), и добавит до трёх подключений, например `openai/gpt-oss-120b` (основная), `qwen/qwen3.8-27b` и `openai/gpt-oss-20b` (запасные). У каждой модели свой дневной лимит (на 2026-10-25 для бесплатного аккаунта около 1000 запросов в день и 8000 токенов в минуту): если одна закончится, Bubby переключится на следующую.

Никому не показывайте свой ключ. Лимиты примерные и меняются: смотрите console.groq.com/settings/limits.
