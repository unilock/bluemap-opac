package cc.unilock.bluemap_opac;

import net.minecraftforge.common.ForgeConfigSpec;

public class BlueMapOPACConfig {
	public static final ForgeConfigSpec SPEC;

	public static ForgeConfigSpec.ConfigValue<Integer> updateInterval;
	public static ForgeConfigSpec.ConfigValue<Double> markerMinY;
	public static ForgeConfigSpec.ConfigValue<Double> markerMaxY;
	public static ForgeConfigSpec.ConfigValue<Boolean> depthTest;

	private static void setupConfig(ForgeConfigSpec.Builder builder) {
		builder.comment(
			"How often, in ticks, the markers should be refreshed. Set to 0 to disable automatic refreshing.",
			"Default is 10 minutes (12000 ticks)."
		);
		updateInterval = builder.define("update_interval", 12000);

		builder.comment(
			"The min and max Y for the markers. If these are the same, the marker will be drawn as a flat plane.",
			"Default is 75 to 75."
		);
		markerMinY = builder.define("marker_min_y", 75.0);
		markerMaxY = builder.define("marker_max_y", 75.0);

		builder.comment(
			"If set to false, the markers won't be covered up by objects in front of it.",
			"Default is false."
		);
		depthTest = builder.define("depth_test", false);
	}

	static {
		ForgeConfigSpec.Builder configBuilder = new ForgeConfigSpec.Builder();
		setupConfig(configBuilder);
		SPEC = configBuilder.build();
	}
}
