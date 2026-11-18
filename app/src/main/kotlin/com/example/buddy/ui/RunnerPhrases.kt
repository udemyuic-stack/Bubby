package com.example.buddy.ui

class RunnerPhrase(val text: String, val answer: String, val pool: List<String>, val ru: String, val uz: String) {
    fun filled() = text.replace("___", answer)
}

object RunnerPhrases {
    private val dmt = listOf("do", "make", "take")
    private val hggg = listOf("have", "get", "go", "give")
    private val prep = listOf("in", "on", "at", "for", "to", "of")
    private val part = listOf("up", "down", "out", "off")

    private fun r(text: String, answer: String, ru: String, uz: String, w1: String, w2: String) =
        RunnerPhrase(text, answer, listOf(answer, w1, w2), ru, uz)

    val sectors: List<Pair<String, List<RunnerPhrase>>> = listOf(
        "DO · MAKE · TAKE" to listOf(
            RunnerPhrase("___ a contribution", "make", dmt, "внести вклад", "hissa qo'shish"),
            RunnerPhrase("___ homework", "do", dmt, "делать домашнее задание", "uy vazifasini bajarish"),
            RunnerPhrase("___ a photo", "take", dmt, "сфотографировать", "rasmga olish"),
            RunnerPhrase("___ a mistake", "make", dmt, "совершить ошибку", "xato qilish"),
            RunnerPhrase("___ a break", "take", dmt, "сделать перерыв", "tanaffus qilish"),
            RunnerPhrase("___ research", "do", dmt, "проводить исследование", "tadqiqot o'tkazish"),
            RunnerPhrase("___ an effort", "make", dmt, "приложить усилия", "harakat qilish"),
            RunnerPhrase("___ a risk", "take", dmt, "рискнуть", "tavakkal qilish"),
            RunnerPhrase("___ the dishes", "do", dmt, "мыть посуду", "idish yuvish"),
            RunnerPhrase("___ progress", "make", dmt, "делать успехи", "yutuqqa erishish"),
            RunnerPhrase("___ advantage of", "take", dmt, "воспользоваться", "foydalanib qolish"),
            RunnerPhrase("___ an exam", "take", dmt, "сдавать экзамен", "imtihon topshirish"),
            RunnerPhrase("___ a favour", "do", dmt, "оказать услугу", "yaxshilik qilish"),
            RunnerPhrase("___ money", "make", dmt, "зарабатывать деньги", "pul topish"),
            RunnerPhrase("___ care of", "take", dmt, "заботиться о", "g'amxo'rlik qilish"),
            RunnerPhrase("___ business", "do", dmt, "вести дела", "biznes yuritish"),
            RunnerPhrase("___ a plan", "make", dmt, "составить план", "reja tuzish"),
            RunnerPhrase("___ place", "take", dmt, "происходить", "bo'lib o'tish"),
            RunnerPhrase("___ a decision", "make", dmt, "принять решение", "qaror qabul qilish"),
            RunnerPhrase("___ your best", "do", dmt, "сделать всё возможное", "qo'lidan kelganini qilish")
        ),
        "HAVE · GET · GO · GIVE" to listOf(
            RunnerPhrase("___ a shower", "have", hggg, "принять душ", "dush qabul qilish"),
            RunnerPhrase("___ breakfast", "have", hggg, "завтракать", "nonushta qilish"),
            RunnerPhrase("___ a good time", "have", hggg, "хорошо провести время", "vaqtni yaxshi o'tkazish"),
            RunnerPhrase("___ married", "get", hggg, "пожениться", "turmush qurish"),
            RunnerPhrase("___ better", "get", hggg, "поправиться", "yaxshilanish"),
            RunnerPhrase("___ a job", "get", hggg, "получить работу", "ish topish"),
            RunnerPhrase("___ lost", "get", hggg, "заблудиться", "adashib qolish"),
            RunnerPhrase("___ shopping", "go", hggg, "ходить за покупками", "xarid qilishga borish"),
            RunnerPhrase("___ for a walk", "go", hggg, "пойти гулять", "sayrga chiqish"),
            RunnerPhrase("___ bald", "go", hggg, "лысеть", "kal bo'lib qolish"),
            RunnerPhrase("___ a speech", "give", hggg, "произнести речь", "nutq so'zlash"),
            RunnerPhrase("___ advice", "give", hggg, "давать совет", "maslahat berish")
        ),
        "IN · ON · AT · FOR · TO · OF" to listOf(
            RunnerPhrase("interested ___ music", "in", prep, "интересоваться музыкой", "musiqaga qiziqish"),
            RunnerPhrase("good ___ maths", "at", prep, "хорош в математике", "matematikada kuchli"),
            RunnerPhrase("depend ___ the weather", "on", prep, "зависеть от погоды", "ob-havoga bog'liq bo'lish"),
            RunnerPhrase("afraid ___ spiders", "of", prep, "бояться пауков", "o'rgimchaklardan qo'rqish"),
            RunnerPhrase("listen ___ music", "to", prep, "слушать музыку", "musiqa tinglash"),
            RunnerPhrase("wait ___ a bus", "for", prep, "ждать автобус", "avtobusni kutish"),
            RunnerPhrase("arrive ___ the airport", "at", prep, "прибыть в аэропорт", "aeroportga yetib kelish"),
            RunnerPhrase("arrive ___ Tashkent", "in", prep, "прибыть в Ташкент", "Toshkentga yetib kelish"),
            RunnerPhrase("___ the morning", "in", prep, "утром", "ertalab"),
            RunnerPhrase("___ Monday", "on", prep, "в понедельник", "dushanba kuni"),
            RunnerPhrase("___ night", "at", prep, "ночью", "kechasi"),
            RunnerPhrase("married ___ a doctor", "to", prep, "замужем за врачом", "vrachga turmushga chiqqan")
        ),
        "UP · DOWN · OUT · OFF" to listOf(
            RunnerPhrase("give ___", "up", part, "сдаваться", "taslim bo'lish"),
            RunnerPhrase("turn ___ the light", "off", part, "выключить свет", "chiroqni o'chirish"),
            RunnerPhrase("sit ___", "down", part, "садиться", "o'tirish"),
            RunnerPhrase("find ___", "out", part, "выяснить", "bilib olish"),
            RunnerPhrase("get ___ the bus", "off", part, "сойти с автобуса", "avtobusdan tushish"),
            RunnerPhrase("wake ___", "up", part, "просыпаться", "uyg'onish"),
            RunnerPhrase("write ___ the address", "down", part, "записать адрес", "manzilni yozib olish"),
            RunnerPhrase("run ___ of milk", "out", part, "закончилось молоко", "sut tugab qoldi"),
            RunnerPhrase("take ___ your shoes", "off", part, "снять обувь", "poyabzalni yechish"),
            RunnerPhrase("look ___!", "out", part, "осторожно!", "ehtiyot bo'ling!"),
            RunnerPhrase("break ___", "down", part, "сломаться", "buzilib qolish"),
            RunnerPhrase("cheer ___", "up", part, "взбодриться", "ruhlanish")
        ),
        "A · AN · THE · MUCH · MANY" to listOf(
            r("___ apple a day", "an", "яблоко в день", "kuniga bitta olma", "a", "much"),
            r("___ university", "a", "университет", "universitet", "an", "many"),
            r("___ hour ago", "an", "час назад", "bir soat oldin", "a", "many"),
            r("Look at ___ moon", "the", "Посмотри на луну", "Oyga qara", "much", "many"),
            r("How ___ money do you have?", "much", "Сколько у тебя денег?", "Sizda qancha pul bor?", "many", "an"),
            r("How ___ people came?", "many", "Сколько людей пришло?", "Nechta odam keldi?", "much", "an"),
            r("I don't have ___ time", "any", "У меня нет времени", "Mening vaqtim yo'q", "many", "an"),
            r("Would you like ___ tea?", "some", "Хотите чаю?", "Choy xohlaysizmi?", "many", "an"),
            r("She is ___ honest girl", "an", "Она честная девушка", "U halol qiz", "a", "many"),
            r("___ Earth goes around the Sun", "the", "Земля вращается вокруг Солнца", "Yer Quyosh atrofida aylanadi", "a", "an"),
            r("There isn't ___ milk left", "any", "Молока не осталось", "Sut qolmadi", "many", "an"),
            r("He has ___ friends", "many", "У него много друзей", "Uning do'stlari ko'p", "much", "a")
        ),
        "PAST FORMS" to listOf(
            r("Yesterday I ___ to school", "went", "Вчера я ходил в школу", "Kecha maktabga bordim", "gone", "goed"),
            r("She ___ a letter last week", "wrote", "Она написала письмо на прошлой неделе", "U o'tgan hafta xat yozdi", "written", "writed"),
            r("We ___ pizza for dinner", "ate", "Мы ели пиццу на ужин", "Kechki ovqatga pitsa yedik", "eaten", "eated"),
            r("I have ___ him for years", "known", "Я знаю его много лет", "Men uni yillardan beri taniyman", "knew", "knowed"),
            r("He ___ the window", "broke", "Он разбил окно", "U derazani sindirdi", "broken", "breaked"),
            r("I have ___ my keys", "lost", "Я потерял ключи", "Kalitlarimni yo'qotdim", "loosed", "losed"),
            r("She has ___ a new car", "bought", "Она купила новую машину", "U yangi mashina sotib oldi", "buyed", "brought"),
            r("The bird ___ away", "flew", "Птица улетела", "Qush uchib ketdi", "flown", "flied"),
            r("We have ___ the film", "seen", "Мы видели фильм", "Biz filmni ko'rganmiz", "saw", "seed"),
            r("He ___ me a gift", "gave", "Он дал мне подарок", "U menga sovg'a berdi", "given", "gived"),
            r("I ___ up at six", "woke", "Я проснулся в шесть", "Men oltida uyg'ondim", "waked", "woken"),
            r("She has ___ the song", "sung", "Она спела песню", "U qo'shiq kuyladi", "sang", "singed")
        ),
        "CONFUSING WORDS" to listOf(
            r("___ going to be late", "they're", "Они опоздают", "Ular kech qoladilar", "their", "there"),
            r("Put it over ___", "there", "Положи это вон там", "Buni o'sha yerga qo'y", "their", "they're"),
            r("This is ___ house", "their", "Это их дом", "Bu ularning uyi", "there", "they're"),
            r("___ a nice day", "it's", "Хороший день", "Ajoyib kun", "its", "its'"),
            r("The dog wagged ___ tail", "its", "Собака вильнула хвостом", "It dumini likillatdi", "it's", "their"),
            r("___ welcome!", "you're", "Пожалуйста!", "Marhamat!", "your", "you"),
            r("Is this ___ bag?", "your", "Это твоя сумка?", "Bu sizning sumkangizmi?", "you're", "you"),
            r("She is taller ___ me", "than", "Она выше меня", "U mendan balandroq", "then", "that"),
            r("First eat, ___ play", "then", "Сначала поешь, потом играй", "Avval ye, keyin o'yna", "than", "that"),
            r("Bad weather can ___ my mood", "affect", "Плохая погода влияет на настроение", "Yomon ob-havo kayfiyatimga ta'sir qiladi", "effect", "effort"),
            r("The ___ was amazing", "effect", "Эффект был потрясающим", "Ta'sir ajoyib edi", "affect", "effort"),
            r("I will ___ you tomorrow", "see", "Увижу тебя завтра", "Ertaga ko'raman", "sea", "say")
        ),
        "GRAMMAR PATTERNS" to listOf(
            r("I enjoy ___", "swimming", "Мне нравится плавать", "Suzishni yoqtiraman", "to swim", "swim"),
            r("I want ___ home", "to go", "Хочу пойти домой", "Uyga ketishni xohlayman", "going", "go"),
            r("She can ___ well", "sing", "Она хорошо поёт", "U yaxshi kuylay oladi", "sings", "to sing"),
            r("If it rains, I ___ stay home", "will", "Если пойдёт дождь, останусь дома", "Yomg'ir yog'sa, uyda qolaman", "would", "am"),
            r("If I ___ rich, I would travel", "were", "Будь я богат, путешествовал бы", "Boy bo'lsam sayohat qilardim", "am", "will be"),
            r("He suggested ___ a taxi", "taking", "Он предложил взять такси", "U taksi olishni taklif qildi", "to take", "take"),
            r("I'm used to ___ early", "getting up", "Я привык вставать рано", "Erta turishga o'rganganman", "get up", "got up"),
            r("You ___ see a doctor", "should", "Тебе стоит сходить к врачу", "Shifokorga borishing kerak", "to", "are"),
            r("She has ___ here since 2020", "lived", "Она живёт здесь с 2020", "U 2020 yildan beri shu yerda yashaydi", "living", "live"),
            r("I look forward to ___ you", "seeing", "Жду встречи с вами", "Sizni ko'rishni intizorlik bilan kutaman", "see", "saw"),
            r("She is good at ___", "cooking", "Она хорошо готовит", "U yaxshi ovqat pishiradi", "cook", "to cook"),
            r("I'd rather ___ tea", "have", "Я бы лучше выпил чаю", "Men choy ichgan bo'lardim", "having", "to have")
        )
    )

    val all: List<RunnerPhrase> = sectors.flatMap { it.second }
}
