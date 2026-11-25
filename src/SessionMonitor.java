import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Programul B: se ataseaza la procesul descris in fisierul de
 * sesiune si urmareste cat timp ramane activ.
 */
public class SessionMonitor {
    static long startNs;

    public static void main(String[] args) throws Exception {
        Path sessionFile = Path.of(args[0]);
        long interval = args.length > 1 ? Long.parseLong(args[1]) : 30;

        SessionInfo info = SessionInfo.read(sessionFile);
        Optional<ProcessHandle> found = ProcessHandle.of(info.pid);
        if (found.isEmpty()) {
            log("Procesul " + info.pid + " nu exista");
            return;
        }
        ProcessHandle browser = found.get();
        startNs = System.nanoTime();
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
        timer.shutdownNow();
        log(String.format("Procesul s-a incheiat dupa %.1f s",
            (System.nanoTime() - startNs) / 1e9));
    }

    static void log(String msg) {
        double s = (System.nanoTime() - startNs) / 1e9;
        System.out.printf("[%s] [monitor] [T+%6.1f s] %s%n",
            LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss")), s, msg);
    }
}
