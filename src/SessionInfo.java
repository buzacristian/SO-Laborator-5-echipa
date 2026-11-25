import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Fisierul de sesiune, folosit de ambele programe. */
public class SessionInfo {
    public long pid;
    public String profile;
    public String startTime;
    public long limitSeconds;

    /** Scrie o linie cheie=valoare pentru fiecare camp. */
    public void write(Path file) throws IOException {
        String text = "pid=" + pid + "\n"
            + "profile=" + profile + "\n"
            + "startTime=" + startTime + "\n"
            + "limitSeconds=" + limitSeconds + "\n";
        Files.writeString(file, text);
    }

    public static SessionInfo read(Path file) throws IOException {
        Map<String, String> m = new HashMap<>();
        for (String line : Files.readAllLines(file)) {
            int i = line.indexOf('=');
            if (i > 0) m.put(line.substring(0, i), line.substring(i + 1));
        }
        SessionInfo s = new SessionInfo();
        s.pid = Long.parseLong(m.get("pid"));
        s.profile = m.get("profile");
        s.startTime = m.get("startTime");
        s.limitSeconds = Long.parseLong(m.get("limitSeconds"));
        return s;
    }
}
