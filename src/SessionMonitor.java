import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Programul B: se ataseaza la procesul descris in fisierul de
 * sesiune, urmareste cat timp ramane activ si scrie rezultatul
 * intr-un fisier de stare citit de programul A.
 */
public class SessionMonitor {
    static final Locale RO = Locale.forLanguageTag("ro");
    static long startMillis;
    static int maxDescendants = 0;
    static PrintWriter logFile;

    public static void main(String[] args) throws Exception {
        Path sessionFile = Path.of(args[0]);
        Path statusFile = Path.of(args[1]);
        long interval = args.length > 2 ? Long.parseLong(args[2]) : 30;

        Path logPath = args.length > 3 ? Path.of(args[3])
            : Path.of("monitor.log");
        logFile = new PrintWriter(Files.newBufferedWriter(logPath),
            true);

        SessionInfo info = SessionInfo.read(sessionFile);
        startMillis = Instant.parse(info.startTime).toEpochMilli();
        Optional<ProcessHandle> found = ProcessHandle.of(info.pid);
        if (found.isEmpty()) {
            log("Procesul " + info.pid + " nu exista");
            return;
        }
        ProcessHandle browser = found.get();
        log("Atasat la PID " + browser.pid() + ", profil "
            + info.profile);

        ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
        timer.scheduleAtFixedRate(() -> report(browser), interval,
            interval, TimeUnit.SECONDS);

        // onExit() pe un proces care nu e copil a avut o intarziere
        // de aproape 2 s la proba, deci sondez isAlive() la 100 ms
        while (browser.isAlive()) Thread.sleep(100);
        long endMillis = System.currentTimeMillis();
        timer.shutdownNow();

        info.finished = true;
        info.maxDescendants = maxDescendants;
        info.livedSeconds = (endMillis - startMillis) / 1000.0;
        info.earlyClose = info.livedSeconds < info.limitSeconds - 1;
        log(String.format(RO, "Procesul s-a incheiat dupa %.1f s",
            info.livedSeconds));
        if (info.earlyClose)
            log("Inchidere inainte de limita de " + info.limitSeconds
                + " s (de utilizator sau din exterior)");
        else
            log("Inchidere la limita de " + info.limitSeconds + " s");
        info.write(statusFile);

        log("===== REZUMAT =====");
        log(String.format(RO, "Durata activa: %.1f s din limita de "
            + "%d s", info.livedSeconds, info.limitSeconds));
        log("Mod de incheiere: " + (info.earlyClose
            ? "inainte de limita" : "la limita"));
        log("Procese descendente observate (maxim): "
            + maxDescendants);
        log("Fisier de stare scris: " + statusFile);
        logFile.close();
    }

    /** O linie de stare: activ, descendente si timp CPU. */
    static void report(ProcessHandle browser) {
        List<ProcessHandle> kids = browser.descendants().toList();
        maxDescendants = Math.max(maxDescendants, kids.size());
        long cpuMs = 0;
        int known = 0;
        for (ProcessHandle h : Stream.concat(
                Stream.of(browser), kids.stream()).toList()) {
            var cpu = h.info().totalCpuDuration();
            if (cpu.isPresent()) {
                cpuMs += cpu.get().toMillis();
                known++;
            }
        }
        String cpuText = known == 0 ? "n/a"
            : String.format(RO, "%.1f s", cpuMs / 1000.0);
        log("STARE activ=" + browser.isAlive() + " descendente="
            + kids.size() + " CPU=" + cpuText);
    }

    static synchronized void log(String msg) {
        double s = (System.currentTimeMillis() - startMillis) / 1000.0;
        String line = String.format(RO, "[%s] [monitor] [T+%6.1f s] %s",
            LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")), s, msg);
        System.out.println(line);
        if (logFile != null) logFile.println(line);
    }
}
