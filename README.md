# SO-Laborator-5

Lucrarea de laborator 5, sarcina 9: un browser cu mai multe file,
urmarit si inchis automat dupa 10 minute.

## Programe

BrowserLauncher (programul A) porneste un Chrome separat, cu un
profil temporar propriu si trei file. Scrie in `session.txt` PID-ul
browserului, calea profilului, momentul pornirii (ISO-8601) si
limita de timp in secunde. Dupa scrierea fisierului porneste
programul B (SessionMonitor) ca proces copil, inchide browserul la
expirarea limitei (destroy, apoi destroyForcibly dupa 10 s) si
afiseaza rezumatul primit de la monitor in `status.txt`.

## Rulare

    javac -d out src/*.java
    java -cp out BrowserLauncher [secunde] [URL ...] [--interval=S]

Fisierele sesiunii (`session.txt`, `status.txt`, jurnalele) se scriu
in folderul `sesiune/`, sau in cel dat cu `--dir=`.
