package cc.unilock.bluemap_opac;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import net.minecraft.command.argument.TimeArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

@Mod("bluemap_opac")
public class BlueMapOPAC {
    public static final Logger LOGGER = LogManager.getLogger();
	private static final String MARKER_SET_KEY = "bluemap-opac";

	private static MinecraftServer minecraftServer;
	private static int update_in;

    public BlueMapOPAC() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BlueMapOPACConfig.SPEC);
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));

		MinecraftForge.EVENT_BUS.register(this);
    }

	@SubscribeEvent
	public void onServerStarting(final ServerStartingEvent event) {
		minecraftServer = event.getServer();
	}

	@SubscribeEvent
	public void onServerStopped(final ServerStoppedEvent event) {
		minecraftServer = null;
	}

	@SubscribeEvent
	public void onServerTick(final TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			if (update_in == 0) {
				BlueMapAPI.getInstance().ifPresent(BlueMapOPAC::updateClaims);
			} else {
				update_in--;
			}
		}
	}

	@SubscribeEvent
	public void onCommandRegistration(final RegisterCommandsEvent event) {
		event.getDispatcher().register(literal("bluemap-opac")
			.requires(s -> s.hasPermissionLevel(2))
			.then(literal("refresh-now")
				.requires(s -> BlueMapAPI.getInstance().isPresent())
				.executes(ctx -> {
					final BlueMapAPI api = BlueMapAPI.getInstance().orElse(null);
					if (api == null) {
						ctx.getSource().sendError(new LiteralText("BlueMap not loaded").formatted(Formatting.RED));
						return 1;
					}
					updateClaims(api);
					ctx.getSource().sendFeedback(
						new LiteralText("BlueMap OPAC claims refreshed").formatted(Formatting.GREEN),
						true
					);
					return Command.SINGLE_SUCCESS;
				})
			)
			.then(literal("refresh-in")
				.executes(ctx -> {
					ctx.getSource().sendFeedback(
						new LiteralText("BlueMap OPAC will refresh in ").append(
							new LiteralText((update_in / 20) + "s").formatted(Formatting.GREEN)
						),
						true
					);
					return Command.SINGLE_SUCCESS;
				})
				.then(argument("time", TimeArgumentType.time())
					.executes(ctx -> {
						update_in = IntegerArgumentType.getInteger(ctx, "time");
						ctx.getSource().sendFeedback(
							new LiteralText("BlueMap OPAC will refresh in ").append(
								new LiteralText((update_in / 20) + "s").formatted(Formatting.GREEN)
							),
							true
						);
						return Command.SINGLE_SUCCESS;
					})
				)
			)
			.then(literal("refresh-every")
				.executes(ctx -> {
					ctx.getSource().sendFeedback(
						new LiteralText("BlueMap OPAC auto refreshes every ").append(
							new LiteralText((BlueMapOPACConfig.updateInterval.get() / 20) + "s").formatted(Formatting.GREEN)
						),
						true
					);
					return Command.SINGLE_SUCCESS;
				})
				.then(argument("interval", TimeArgumentType.time())
					.executes(ctx -> {
						final int interval = IntegerArgumentType.getInteger(ctx, "interval");
						BlueMapOPACConfig.updateInterval.set(interval);
						if (interval < update_in) {
							update_in = interval;
						}
						ctx.getSource().sendFeedback(
							new LiteralText("BlueMap OPAC will auto refresh every ").append(
								new LiteralText((interval / 20) + "s").formatted(Formatting.GREEN)
							),
							true
						);
						return Command.SINGLE_SUCCESS;
					})
				)
			)
			.then(literal("reload")
				.executes(ctx -> {
					if (BlueMapOPACConfig.updateInterval.get() < update_in) {
						update_in = BlueMapOPACConfig.updateInterval.get();
					}
					ctx.getSource().sendFeedback(
						new LiteralText("Reloaded BlueMap OPAC config").formatted(Formatting.GREEN),
						true
					);
					return Command.SINGLE_SUCCESS;
				})
			)
		);
	}

	public static void updateClaims(BlueMapAPI blueMap) {
		if (minecraftServer == null) {
			LOGGER.warn("updateClaims called with minecraftServer == null!");
			return;
		}
		LOGGER.info("Refreshing BlueMap OPAC markers");
		OpenPACServerAPI.get(minecraftServer)
			.getServerClaimsManager()
			.getPlayerInfoStream()
			.forEach(playerClaimInfo -> {
				String name = playerClaimInfo.getClaimsName();
				final String idName;
				if (StringUtils.isBlank(name)) {
					idName = name = playerClaimInfo.getPlayerUsername();
					if (name.length() > 2 && name.charAt(0) == '"' && name.charAt(name.length() - 1) == '"') {
						name = name.substring(1, name.length() - 1) + " claim";
					} else {
						name += "'s claim";
					}
				} else {
					idName = name;
				}
				final String displayName = name;
				playerClaimInfo.getStream().forEach(entry -> {
					final BlueMapWorld world = blueMap.getWorld(RegistryKey.of(Registry.DIMENSION_KEY, entry.getKey())).orElse(null);
					if (world == null) return;
					final List<ShapeHolder> shapes = createShapes(
						entry.getValue()
							.getStream()
							.flatMap(IPlayerClaimPosListAPI::getStream)
							.collect(Collectors.toSet())
					);
					world.getMaps().forEach(map -> {
						final Map<String, Marker> markers = map
							.getMarkerSets()
							.computeIfAbsent(MARKER_SET_KEY, k ->
								MarkerSet.builder()
									.toggleable(true)
									.label("Open Parties and Claims")
									.build()
							)
							.getMarkers();
						final float minY = BlueMapOPACConfig.markerMinY.get().floatValue();
						final float maxY = BlueMapOPACConfig.markerMaxY.get().floatValue();
						final boolean flatPlane = MathHelper.approximatelyEquals(minY, maxY);
						markers.keySet().removeIf(k -> k.startsWith(idName + "---"));
						for (int i = 0; i < shapes.size(); i++) {
							final ShapeHolder shape = shapes.get(i);
							markers.put(idName + "---" + i,
								// Yes these builders are the same. No they don't share a superclass (except for label).
								flatPlane
									? ShapeMarker.builder()
									.label(displayName)
									.fillColor(new Color(playerClaimInfo.getClaimsColor(), 150))
									.lineColor(new Color(playerClaimInfo.getClaimsColor(), 255))
									.shape(shape.baseShape(), minY)
									.holes(shape.holes())
									.depthTestEnabled(BlueMapOPACConfig.depthTest.get())
									.build()
									: ExtrudeMarker.builder()
									.label(displayName)
									.fillColor(new Color(playerClaimInfo.getClaimsColor(), 150))
									.lineColor(new Color(playerClaimInfo.getClaimsColor(), 255))
									.shape(shape.baseShape(), minY, maxY)
									.holes(shape.holes())
									.depthTestEnabled(BlueMapOPACConfig.depthTest.get())
									.build()
							);
						}
					});
				});
			});
		LOGGER.info("Refreshed BlueMap OPAC markers");
		update_in = BlueMapOPACConfig.updateInterval.get();
	}

	public static List<ShapeHolder> createShapes(Set<ChunkPos> chunks) {
		return createChunkGroups(chunks)
			.stream()
			.map(ShapeHolder::create)
			.toList();
	}

	public static List<Set<ChunkPos>> createChunkGroups(Set<ChunkPos> chunks) {
		final List<Set<ChunkPos>> result = new ArrayList<>();
		final Set<ChunkPos> visited = new HashSet<>();
		for (final ChunkPos chunk : chunks) {
			if (visited.contains(chunk)) continue;
			final Set<ChunkPos> neighbors = findNeighbors(chunk, chunks);
			result.add(neighbors);
			visited.addAll(neighbors);
		}
		return result;
	}

	public static Set<ChunkPos> findNeighbors(ChunkPos chunk, Set<ChunkPos> chunks) {
		if (!chunks.contains(chunk)) {
			throw new IllegalArgumentException("chunks must contain chunk to find neighbors!");
		}
		final Set<ChunkPos> visited = new HashSet<>();
		final Queue<ChunkPos> toVisit = new ArrayDeque<>();
		visited.add(chunk);
		toVisit.add(chunk);
		while (!toVisit.isEmpty()) {
			final ChunkPos visiting = toVisit.remove();
			for (final ChunkPosDirection dir : ChunkPosDirection.values()) {
				final ChunkPos offsetPos = dir.add(visiting);
				if (!chunks.contains(offsetPos) || !visited.add(offsetPos)) continue;
				toVisit.add(offsetPos);
			}
		}
		return visited;
	}
}
