# SO-Laborator-5

Lucrarea de laborator 5, sarcina 9: un browser cu mai multe file,
urmarit si inchis automat dupa 10 minute.

## Programe

BrowserLauncher (programul A) porneste un Chrome separat, cu un
profil temporar propriu si trei file. Scrie in `session.txt` PID-ul
browserului si calea profilului.

## Rulare

    javac -d out src/*.java
    java -cp out BrowserLauncher [secunde] [URL ...]
