Clojure-utils
=============

Yleiskäyttöistä koodia liittyen Opetushallituksen Clojure-projektien tarpeisiin. 

# Käyttäviä projekteja

* [Aitu-projekti](https://github.com/Opetushallitus/aitu) on ollut alkuperäinen malliprojekti.
* [Aipal](https://github.com/Opetushallitus/aipal)
* [Näyttötutkintohaku.fi](https://github.com/Opetushallitus/aituhaku)
* [osaan.fi](https://github.com/Opetushallitus/osaan.fi)
* CSC:n [Avop](https://github.com/CSC-IT-Center-for-Science/avop)

# Kun teet päivityksiä

Päivitykset eivät tule automaattisesti käyttöön projekteihin, joille tämä on submodule. Projekti on sidottu tiettyyn versioon.
Päivittäminen per projekti onnistuu näin:

```
git submodule update --remote
git add -p
git commit
```
