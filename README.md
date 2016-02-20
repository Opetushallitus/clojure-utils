Clojure-utils
=============

Yleiskäyttöistä koodia liittyen Opetushallituksen Clojure-projektien tarpeisiin.

Travis CI automaattitestit: [![Build Status](https://travis-ci.org/Opetushallitus/clojure-utils.svg?branch=master)](https://travis-ci.org/Opetushallitus/clojure-utils)

# Käyttäviä projekteja

* [Aitu-projekti](https://github.com/Opetushallitus/aitu) on ollut alkuperäinen malliprojekti.
* [Aipal](https://github.com/Opetushallitus/aipal)
* [Näyttötutkintohaku.fi](https://github.com/Opetushallitus/aituhaku)
* [osaan.fi](https://github.com/Opetushallitus/osaan.fi)
* CSC:n [Arvo](https://github.com/CSC-IT-Center-for-Science/arvo)

# Kun teet päivityksiä

Päivitykset eivät tule automaattisesti käyttöön projekteihin, joille tämä on submodule. Projekti on sidottu tiettyyn versioon.
Päivittäminen per projekti onnistuu näin:

```
git submodule update --remote
git add -p
git commit
```
