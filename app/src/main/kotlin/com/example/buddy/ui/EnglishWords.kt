package com.example.buddy.ui

class EnglishWord(val en: String, val ru: String, val uz: String)

object EnglishWords {
    val all: List<EnglishWord> = (
        "cat|кошка|mushuk;dog|собака|it;book|книга|kitob;water|вода|suv;house|дом|uy;tree|дерево|daraxt;sun|солнце|quyosh;moon|луна|oy;" +
        "star|звезда|yulduz;apple|яблоко|olma;bread|хлеб|non;milk|молоко|sut;friend|друг|do'st;family|семья|oila;mother|мама|ona;" +
        "father|папа|ota;sister|сестра|opa;brother|брат|aka;school|школа|maktab;teacher|учитель|o'qituvchi;student|студент|talaba;" +
        "city|город|shahar;street|улица|ko'cha;car|машина|mashina;bus|автобус|avtobus;train|поезд|poyezd;phone|телефон|telefon;" +
        "table|стол|stol;chair|стул|stul;door|дверь|eshik;window|окно|deraza;room|комната|xona;garden|сад|bog';flower|цветок|gul;" +
        "river|река|daryo;mountain|гора|tog';sea|море|dengiz;rain|дождь|yomg'ir;snow|снег|qor;wind|ветер|shamol;fire|огонь|olov;" +
        "morning|утро|ertalab;night|ночь|tun;today|сегодня|bugun;tomorrow|завтра|ertaga;happy|счастливый|baxtli;big|большой|katta;" +
        "small|маленький|kichik;fast|быстрый|tez;slow|медленный|sekin;hot|горячий|issiq;cold|холодный|sovuq;new|новый|yangi;" +
        "old|старый|eski;good|хороший|yaxshi;bad|плохой|yomon;eat|есть|yemoq;drink|пить|ichmoq;sleep|спать|uxlamoq;" +
        "read|читать|o'qimoq;write|писать|yozmoq;run|бежать|yugurmoq;walk|идти|yurmoq;speak|говорить|gapirmoq;listen|слушать|tinglamoq"
        ).split(";").map { val p = it.split("|"); EnglishWord(p[0], p[1], p[2]) }
}
