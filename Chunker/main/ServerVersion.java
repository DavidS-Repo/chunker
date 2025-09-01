package main;

import org.bukkit.Bukkit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and parses the server version for compatibility checks.
 */
public class ServerVersion {
	private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
	private static ServerVersion instance;
	private final int major;
	private final int minor;
	private final int patch;
	private final String versionString;
	private final boolean isAffectedVersion;

	private ServerVersion() {
		String detectedVersion = detectVersion();
		this.versionString = detectedVersion;

		Matcher matcher = VERSION_PATTERN.matcher(detectedVersion);
		if (matcher.find()) {
			this.major = Integer.parseInt(matcher.group(1));
			this.minor = Integer.parseInt(matcher.group(2));
			this.patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
		} else {
			this.major = 0;
			this.minor = 0;
			this.patch = 0;
		}

		this.isAffectedVersion = checkIfAffected();
	}

	/**
	 * Gets the singleton instance of ServerVersion.
	 *
	 * @return the ServerVersion instance
	 */
	public static ServerVersion getInstance() {
		if (instance == null) {
			instance = new ServerVersion();
		}
		return instance;
	}

	/**
	 * Attempts to detect the server version using multiple methods.
	 *
	 * @return the detected version string
	 */
	private String detectVersion() {
		String version = null;

		try {
			version = Bukkit.getVersion();
			Matcher matcher = Pattern.compile("MC:\\s*(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(version);
			if (matcher.find()) {
				return matcher.group(1);
			}
		} catch (Exception ignored) {}

		try {
			version = Bukkit.getBukkitVersion();
			if (version != null && version.contains("-")) {
				return version.split("-")[0];
			}
		} catch (Exception ignored) {}

		try {
			String minecraftVersion = Bukkit.getMinecraftVersion();
			if (minecraftVersion != null) {
				return minecraftVersion;
			}
		} catch (Exception | NoSuchMethodError ignored) {}

		try {
			Package serverPackage = Bukkit.getServer().getClass().getPackage();
			String packageName = serverPackage.getName();
			Matcher matcher = Pattern.compile("v(\\d+)_(\\d+)_R\\d+").matcher(packageName);
			if (matcher.find()) {
				return "1." + matcher.group(2);
			}
		} catch (Exception ignored) {}

		return "unknown";
	}

	/**
	 * Checks if this version requires chunk safety measures.
	 *
	 * @return true if version is 1.21, 1.21.1, or 1.21.2
	 */
	private boolean checkIfAffected() {
		if (major != 1 || minor != 21) {
			return false;
		}
		return patch >= 0 && patch <= 2;
	}

	/**
	 * Returns whether chunk safety measures should be used.
	 *
	 * @return true if safety measures are needed
	 */
	public boolean requiresChunkSafety() {
		return isAffectedVersion;
	}

	/**
	 * Gets the full version string.
	 *
	 * @return the version string
	 */
	public String getVersionString() {
		return versionString;
	}

	/**
	 * Gets the major version number.
	 *
	 * @return the major version
	 */
	public int getMajor() {
		return major;
	}

	/**
	 * Gets the minor version number.
	 *
	 * @return the minor version
	 */
	public int getMinor() {
		return minor;
	}

	/**
	 * Gets the patch version number.
	 *
	 * @return the patch version
	 */
	public int getPatch() {
		return patch;
	}
}