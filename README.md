# SO-Laborator-5

Lucrarea de laborator 5, sarcina 9: un browser cu mai multe file,
urmarit si inchis automat dupa 10 minute.

## Programe

SessionMonitor (programul B) citeste `session.txt`, se ataseaza la
procesul browserului si afiseaza periodic starea lui pana cand
procesul se incheie.

## Rulare

    javac -d out src/*.java
    java -cp out SessionMonitor session.txt [interval]
