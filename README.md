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

SessionMonitor (programul B) citeste `session.txt`, se ataseaza la
procesul browserului si afiseaza periodic starea lui pana cand
procesul se incheie. La final scrie in `status.txt` durata reala,
modul de incheiere si numarul maxim de procese descendente.

Programele depind unul de altul: A nu poate raporta durata fara B,
iar B nu poate porni fara `session.txt`, scris de A. Formatul
fisierului este definit in `SessionInfo`.

## Rulare

    javac -d out src/*.java
    java -cp out BrowserLauncher [secunde] [URL ...] [--interval=S]

Fisierele sesiunii (`session.txt`, `status.txt`, jurnalele) se scriu
in folderul `sesiune/`, sau in cel dat cu `--dir=`. BrowserLauncher
porneste singur monitorul; acesta se poate rula si separat:

    java -cp out SessionMonitor session.txt status.txt [interval] [jurnal]

`session.txt` contine campurile pid, profile, startTime (ISO-8601) si
limitSeconds. `status.txt` repeta aceste campuri si adauga
livedSeconds, earlyClose si maxDescendants.
