import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Programul A: porneste un browser separat, cu mai multe file,
 * intr-un profil temporar creat de program.
 */
public class BrowserLauncher {
    static final long DEFAULT_LIMIT_S = 600;
    static final List<String> DEFAULT_URLS = List.of(
        "https://utm.md",
        "https://docs.oracle.com/en/java/javase/25/docs/api/"
            + "java.base/java/lang/ProcessBuilder.html",
        "https://man7.org/linux/man-pages/man2/fork.2.html");

    public static void main(String[] args) throws Exception {
        long limit = DEFAULT_LIMIT_S;
        Path dir = Path.of("sesiune");
        List<String> urls = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("--dir="))
                dir = Path.of(a.substring(6));
            else if (a.matches("\\d+") && urls.isEmpty())
                limit = Long.parseLong(a);
            else
                urls.add(a);
        }
        if (urls.isEmpty()) urls = DEFAULT_URLS;
        Files.createDirectories(dir);

        Path profile = Files.createTempDirectory("so5-profil-");
        List<String> cmd = browserCommand(profile, urls);
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
        info.write(dir.resolve("session.txt"));

        if (!browser.waitFor(limit, TimeUnit.SECONDS)) {
            log("Limita de " + limit + " s a fost atinsa");
            browser.destroy();
            browser.waitFor();
        }
        log("Browser oprit, cod de iesire " + browser.exitValue());
        deleteTree(profile);
    }

    /** Comanda de pornire, cu profil separat de cel al utilizatorului. */
    static List<String> browserCommand(Path profile, List<String> urls) {
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

    static void log(String msg) {
        System.out.println("[" + LocalTime.now().format(
            DateTimeFormatter.ofPattern("HH:mm:ss"))
            + "] [lansator] " + msg);
    }
}
