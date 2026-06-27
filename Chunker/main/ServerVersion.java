package main;

import org.bukkit.Bukkit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and parses the server version for compatibility checks.
 */
public class ServerVersion {
	private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
	private static final Pattern MC_VERSION_PATTERN = Pattern.compile("MC:\\s*(\\d+\\.\\d+(?:\\.\\d+)?)");
	private static final Pattern LEGACY_PACKAGE_PATTERN = Pattern.compile("v(\\d+)_(\\d+)_R\\d+");
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
		return Holder.INSTANCE;
	}

	private static final class Holder {
		private static final ServerVersion INSTANCE = new ServerVersion();
	}

	/**
	 * Attempts to detect the server version using multiple methods.
	 *
	 * @return the detected version string
	 */
	private String detectVersion() {
		try {
			String minecraftVersion = Bukkit.getMinecraftVersion();
			if (minecraftVersion != null && !minecraftVersion.isBlank()) {
				return minecraftVersion;
			}
		} catch (Exception | NoSuchMethodError ignored) {}

		try {
			String version = Bukkit.getVersion();
			Matcher matcher = MC_VERSION_PATTERN.matcher(version);
			if (matcher.find()) {
				return matcher.group(1);
			}
		} catch (Exception ignored) {}

		try {
			String version = Bukkit.getBukkitVersion();
			if (version != null && !version.isBlank()) {
				int dash = version.indexOf('-');
				return dash >= 0 ? version.substring(0, dash) : version;
			}
		} catch (Exception ignored) {}

		try {
			Package serverPackage = Bukkit.getServer().getClass().getPackage();
			String packageName = serverPackage.getName();
			Matcher matcher = LEGACY_PACKAGE_PATTERN.matcher(packageName);
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
		return major == 1 && minor == 21 && patch >= 0 && patch <= 2;
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
