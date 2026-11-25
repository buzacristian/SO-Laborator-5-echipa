import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Programul B: se ataseaza la procesul descris in fisierul de
 * sesiune, urmareste cat timp ramane activ si scrie rezultatul
 * intr-un fisier de stare citit de programul A.
 */
public class SessionMonitor {
    static final Locale RO = Locale.forLanguageTag("ro");
    static long startMillis;

    public static void main(String[] args) throws Exception {
        Path sessionFile = Path.of(args[0]);
        Path statusFile = Path.of(args[1]);
        long interval = args.length > 2 ? Long.parseLong(args[2]) : 30;

        SessionInfo info = SessionInfo.read(sessionFile);
        startMillis = info.startMillis;
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
        timer.scheduleAtFixedRate(() -> log("STARE activ="
            + browser.isAlive()), interval, interval,
            TimeUnit.SECONDS);

        browser.onExit().get();
        long endMillis = System.currentTimeMillis();
        timer.shutdownNow();

        info.finished = true;
        info.livedSeconds = (endMillis - info.startMillis) / 1000.0;
        info.earlyClose = info.livedSeconds < info.limitSeconds - 1;
        log(String.format(RO, "Procesul s-a incheiat dupa %.1f s",
            info.livedSeconds));
        if (info.earlyClose)
            log("Inchidere inainte de limita de " + info.limitSeconds
                + " s (de utilizator sau din exterior)");
        else
            log("Inchidere la limita de " + info.limitSeconds + " s");
        info.write(statusFile);
    }

    static void log(String msg) {
        double s = (System.currentTimeMillis() - startMillis) / 1000.0;
        System.out.println(String.format(RO, "[%s] [monitor] "
            + "[T+%6.1f s] %s", LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")), s, msg));
    }
}
