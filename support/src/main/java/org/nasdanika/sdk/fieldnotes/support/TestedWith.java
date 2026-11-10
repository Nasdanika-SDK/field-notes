package org.nasdanika.sdk.fieldnotes.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Records the versions a note's tests ran against, so that the note states what was measured
 * rather than what was declared.
 * <p>
 * For each class, it finds the jar the class was loaded from. The group and the artifact come from
 * the jar's Maven <code>pom.properties</code>; the version comes from the jar's file name,
 * <code>&lt;artifactId&gt;-&lt;version&gt;.jar</code>, which is the version Maven resolved. The version
 * in <code>pom.properties</code> is not used: EMF's jars on Maven Central, for one, say
 * <code>2.43.0-SNAPSHOT</code> there although they are the 2.43.0 release. The build column is the
 * manifest's <code>Bundle-Version</code> or <code>Implementation-Version</code>, which identifies the
 * build exactly.
 * <p>
 * It writes an AsciiDoc table of the artifacts and the Java runtime to
 * <code>tested-with.adoc</code> in the directory named by the system property
 * <code>fieldnotes.target</code>, which the build sets to the module's <code>target</code>
 * directory. The note includes that file.
 */
public final class TestedWith {

	/**
	 * The file a note includes.
	 */
	public static final String FILE_NAME = "tested-with.adoc";

	private static final Pattern POM_PROPERTIES = Pattern.compile("META-INF/maven/[^/]+/[^/]+/pom\\.properties");

	record Artifact(String groupId, String artifactId, String version, String build) {}

	private TestedWith() {}

	/**
	 * Writes the versions of the jars that provide the classes.
	 * @param classes One class from each artifact the note is about
	 * @return The file written
	 */
	public static Path write(Class<?>... classes) throws IOException {
		Map<String, Artifact> artifacts = new LinkedHashMap<>();
		for (Class<?> type : classes) {
			Artifact artifact = artifact(type);
			artifacts.put(artifact.groupId() + ":" + artifact.artifactId(), artifact);
		}

		List<String> lines = new ArrayList<>();
		lines.add("[cols=\"5,2,3\",options=\"header\"]");
		lines.add("|===");
		lines.add("| Artifact | Version | Build");
		for (Artifact artifact : artifacts.values()) {
			lines.add("| `" + artifact.groupId() + ":" + artifact.artifactId() + "` | " + artifact.version() + " | " + (artifact.build() == null ? "" : artifact.build()));
		}
		lines.add("| Java | " + Runtime.version().feature() + " | " + Runtime.version() + " (" + System.getProperty("java.vendor") + ")");
		lines.add("|===");
		String runtimeVersion = System.getProperty("nasdanika.runtime.version");
		if (runtimeVersion != null) {
			lines.add("");
			lines.add("The artifacts' versions are those managed by the BOM of the Nasdanika SDK Runtime " + runtimeVersion + ".");
		}

		Path target = Path.of(System.getProperty("fieldnotes.target", "target"));
		Files.createDirectories(target);
		Path file = target.resolve(FILE_NAME);
		Files.write(file, lines);
		return file;
	}

	static Artifact artifact(Class<?> type) throws IOException {
		CodeSource codeSource = type.getProtectionDomain().getCodeSource();
		if (codeSource == null) {
			throw new IllegalArgumentException(type.getName() + " has no code source, so its version is unknown");
		}
		Path location;
		try {
			location = Path.of(codeSource.getLocation().toURI());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Cannot locate the jar of " + type.getName(), e);
		}
		if (!Files.isRegularFile(location)) {
			throw new IllegalArgumentException(type.getName() + " was not loaded from a jar: " + location);
		}
		try (JarFile jar = new JarFile(location.toFile())) {
			List<JarEntry> entries = jar.stream()
				.filter(entry -> POM_PROPERTIES.matcher(entry.getName()).matches())
				.toList();
			if (entries.size() != 1) {
				throw new IllegalArgumentException(location + " has " + entries.size() + " pom.properties entries, expected one");
			}
			Properties properties = new Properties();
			try (InputStream in = jar.getInputStream(entries.get(0))) {
				properties.load(in);
			}
			String artifactId = properties.getProperty("artifactId");
			String fileName = location.getFileName().toString();
			String prefix = artifactId + "-";
			if (!fileName.startsWith(prefix) || !fileName.endsWith(".jar")) {
				throw new IllegalArgumentException(location + " is not named " + prefix + "<version>.jar, so the resolved version is unknown");
			}
			String version = fileName.substring(prefix.length(), fileName.length() - ".jar".length());
			Attributes manifest = jar.getManifest() == null ? new Attributes() : jar.getManifest().getMainAttributes();
			String build = manifest.getValue("Bundle-Version");
			if (build == null) {
				build = manifest.getValue("Implementation-Version");
			}
			return new Artifact(properties.getProperty("groupId"), artifactId, version, build);
		}
	}

}
