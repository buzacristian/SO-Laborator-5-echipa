# SO-Laborator-5

Lucrarea de laborator 5, sarcina 9: un browser cu mai multe file,
urmarit si inchis automat dupa 10 minute.

## Programe

SessionMonitor (programul B) citeste `session.txt`, se ataseaza la
procesul browserului si afiseaza periodic starea lui pana cand
procesul se incheie. La final scrie in `status.txt` durata reala,
modul de incheiere si numarul maxim de procese descendente.

## Rulare

    javac -d out src/*.java
    java -cp out SessionMonitor session.txt status.txt [interval] [jurnal]

`session.txt` contine campurile pid, profile, startMillis si
limitSeconds.
