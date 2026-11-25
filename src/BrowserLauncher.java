import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Programul A: porneste un browser separat, cu mai multe file,
 * intr-un profil temporar creat de program.
 */
public class BrowserLauncher {
    static final long DEFAULT_LIMIT_S = 600;
    static final long GRACE_S = 10;
    static final List<String> DEFAULT_URLS = List.of(
        "https://utm.md",
        "https://docs.oracle.com/en/java/javase/25/docs/api/"
            + "java.base/java/lang/ProcessBuilder.html",
        "https://man7.org/linux/man-pages/man2/fork.2.html");

    static PrintWriter logFile;

    public static void main(String[] args) throws Exception {
        long limit = DEFAULT_LIMIT_S;
        long interval = 30;
        Path dir = Path.of("sesiune");
        List<String> urls = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("--dir="))
                dir = Path.of(a.substring(6));
            else if (a.startsWith("--interval="))
                interval = Long.parseLong(a.substring(11));
            else if (a.matches("\\d+") && urls.isEmpty())
                limit = Long.parseLong(a);
            else
                urls.add(a);
        }
        if (urls.isEmpty()) urls = DEFAULT_URLS;
        Files.createDirectories(dir);
        logFile = new PrintWriter(
            Files.newBufferedWriter(dir.resolve("launcher.log")), true);

        Path profile = Files.createTempDirectory("so5-profil-");
        int port = freePort();
        List<String> cmd = browserCommand(profile, port, urls);
        log("Profil temporar: " + profile);
        log("Comanda: " + String.join(" ", cmd));
        Process browser = new ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
        Instant started = Instant.now();
        log("Browser pornit, PID " + browser.pid());

        SessionInfo info = new SessionInfo();
        info.pid = browser.pid();
        info.profile = profile.toString();
        info.startTime = started.toString();
        info.limitSeconds = limit;
        Path sessionFile = dir.resolve("session.txt");
        Path statusFile = dir.resolve("status.txt");
        info.write(sessionFile);

        // programul B citeste fisierul de sesiune scris mai sus
        Process monitor = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path"),
                "SessionMonitor", sessionFile.toString(),
                statusFile.toString(), String.valueOf(interval),
                dir.resolve("monitor.log").toString())
            .inheritIO()
            .start();
        log("Monitor pornit, PID " + monitor.pid());

        // descendentii observati pana acum
        Set<ProcessHandle> seen = ConcurrentHashMap.newKeySet();
        ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
        timer.scheduleAtFixedRate(
            () -> seen.addAll(browser.descendants().toList()),
            2, 5, TimeUnit.SECONDS);
        long cap = limit;
        timer.schedule(() -> listTabs(port), Math.min(15,
            Math.max(5, cap / 4)), TimeUnit.SECONDS);

        boolean byTimer = false;
        if (!browser.waitFor(limit, TimeUnit.SECONDS)) {
            byTimer = true;
            log("Limita de " + limit + " s a fost atinsa");
            closeBrowser(browser, seen, profile);
        } else {
            log("Browserul s-a inchis inainte de limita");
        }
        timer.shutdownNow();
        log("Browser oprit, cod de iesire " + browser.exitValue());
        if (!monitor.waitFor(GRACE_S + 5, TimeUnit.SECONDS)) {
            log("Monitorul nu s-a oprit, il opresc");
            monitor.destroyForcibly();
        }
        log("Monitor oprit, cod de iesire " + monitor.exitValue());
        printStatus(statusFile);
        Thread.sleep(2000);
        log("Procese ramase active: "
            + instance(browser, seen, profile).size());
        log("Profil temporar sters: " + deleteTree(profile));
        logFile.close();
    }

    /** Afiseaza fisierul de stare scris de monitor. */
    static void printStatus(Path statusFile) throws IOException {
        if (Files.notExists(statusFile)) {
            log("Monitorul nu a scris fisierul de stare");
            return;
        }
        log("Rezumatul primit de la monitor:");
        for (String line : Files.readAllLines(statusFile))
            log("  " + line);
    }

    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Citeste http://127.0.0.1:PORT/json/list (DevTools). */
    static void listTabs(int port) {
        try {
            HttpRequest rq = HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + port + "/json/list"))
                .timeout(Duration.ofSeconds(2)).build();
            String body = HttpClient.newHttpClient().send(rq,
                HttpResponse.BodyHandlers.ofString()).body();
            List<String> tabs = new ArrayList<>();
            for (String obj : body.split("\\},\\s*\\{")) {
                if (field(obj, "type").equals("page"))
                    tabs.add(field(obj, "title") + " | "
                        + field(obj, "url"));
            }
            log("File deschise: " + tabs.size());
            tabs.forEach(t -> log("  - " + t));
        } catch (Exception e) {
            log("Lista filelor nu a putut fi citita");
        }
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name
            + "\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    /** Procesul principal, descendentii lui si procesele care
     *  poarta calea profilului temporar in linia de comanda. */
    static List<ProcessHandle> instance(Process main,
            Set<ProcessHandle> seen, Path profile) {
        Map<Long, ProcessHandle> all = new LinkedHashMap<>();
        all.put(main.pid(), main.toHandle());
        main.descendants().forEach(h -> {
            all.put(h.pid(), h);
            seen.add(h);
        });
        seen.forEach(h -> all.put(h.pid(), h));
        ProcessHandle.allProcesses()
            .filter(h -> h.info().commandLine()
                .map(c -> c.contains(profile.toString()))
                .orElse(false))
            .forEach(h -> all.putIfAbsent(h.pid(), h));
        all.remove(ProcessHandle.current().pid());
        return all.values().stream()
            .filter(ProcessHandle::isAlive).toList();
    }

    /** destroy() pentru toate procesele, apoi destroyForcibly()
     *  pentru cele ramase active dupa perioada de gratie. */
    static void closeBrowser(Process main, Set<ProcessHandle> seen,
            Path profile) throws InterruptedException {
        List<ProcessHandle> group = instance(main, seen, profile);
        log("Inchid " + group.size() + " procese cu destroy()");
        main.destroy();
        group.stream().filter(h -> h.pid() != main.pid())
            .forEach(ProcessHandle::destroy);
        long deadline = System.nanoTime() + GRACE_S * 1_000_000_000L;
        while (System.nanoTime() < deadline
                && group.stream().anyMatch(ProcessHandle::isAlive))
            Thread.sleep(200);
        for (ProcessHandle h : group) {
            if (h.isAlive()) {
                log("destroyForcibly() pentru PID " + h.pid());
                h.destroyForcibly();
            }
        }
    }

    /** Comanda de pornire, cu profil separat de cel al utilizatorului. */
    static List<String> browserCommand(Path profile, int port,
                                       List<String> urls) {
        String os = System.getProperty("os.name").toLowerCase();
        List<String> cmd = new ArrayList<>();
        String exe;
        if (os.contains("mac")) {
            exe = "/Applications/Google Chrome.app/Contents/MacOS/"
                + "Google Chrome";
        } else if (os.contains("win")) {
            exe = firstExisting(
                env("ProgramFiles") + "\\Google\\Chrome\\Application"
                    + "\\chrome.exe",
                env("ProgramFiles(x86)") + "\\Google\\Chrome"
                    + "\\Application\\chrome.exe",
                env("LOCALAPPDATA") + "\\Google\\Chrome\\Application"
                    + "\\chrome.exe",
                env("ProgramFiles(x86)") + "\\Microsoft\\Edge"
                    + "\\Application\\msedge.exe");
        } else {
            exe = onPath("google-chrome", "chromium",
                "chromium-browser", "firefox");
        }
        cmd.add(exe);
        if (exe.endsWith("firefox")) {
            cmd.addAll(List.of("-profile", profile.toString(),
                "-no-remote"));
        } else {
            cmd.add("--user-data-dir=" + profile);
            cmd.add("--no-first-run");
            cmd.add("--no-default-browser-check");
            cmd.add("--remote-debugging-port=" + port);
            cmd.add("--new-window");
        }
        cmd.addAll(urls);
        return cmd;
    }

    static String env(String name) {
        String v = System.getenv(name);
        return v == null ? "" : v;
    }

    static String firstExisting(String... paths) {
        for (String p : paths)
            if (Files.isExecutable(Path.of(p))) return p;
        return paths[0];
    }

    static String onPath(String... names) {
        for (String name : names)
            for (String dir : env("PATH").split(File.pathSeparator))
                if (Files.isExecutable(Path.of(dir, name)))
                    return Path.of(dir, name).toString();
        return names[0];
    }

    static boolean deleteTree(Path dir) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
            } catch (IOException e) { /* se reia */ }
            if (Files.notExists(dir)) return true;
            try { Thread.sleep(500); } catch (Exception e) { }
        }
        return Files.notExists(dir);
    }

    static synchronized void log(String msg) {
        String line = "[" + LocalTime.now().format(
            DateTimeFormatter.ofPattern("HH:mm:ss"))
            + "] [lansator] " + msg;
        System.out.println(line);
        if (logFile != null) logFile.println(line);
    }
}
