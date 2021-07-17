/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.core.map.hires;

import com.flowpowered.math.vector.Vector3i;
import de.bluecolored.bluemap.core.map.hires.blockmodel.BlockStateModelFactory;
import de.bluecolored.bluemap.core.resourcepack.NoSuchResourceException;
import de.bluecolored.bluemap.core.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.Block;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.World;

public class HiresModelRenderer {

	private final ResourcePack resourcePack;
	private final RenderSettings renderSettings;
	
	public HiresModelRenderer(ResourcePack resourcePack, RenderSettings renderSettings) {
		this.renderSettings = renderSettings;
		this.resourcePack = resourcePack;
	}
	
	public HiresTileMeta render(World world, Vector3i modelMin, Vector3i modelMax, HiresTileModel model) {
		Vector3i min = modelMin.max(renderSettings.getMin());
		Vector3i max = modelMax.min(renderSettings.getMax());
		Vector3i modelAnchor = new Vector3i(modelMin.getX(), 0, modelMin.getZ());

		HiresTileMeta tileMeta = new HiresTileMeta(modelMin.getX(), modelMin.getZ(), modelMax.getX(), modelMax.getZ()); //TODO: recycle tilemeta instances?

		// create new for each tile-render since the factory is not threadsafe
		BlockStateModelFactory modelFactory = new BlockStateModelFactory(resourcePack, renderSettings);

		int maxHeight, minY, maxY;
		float dx, dz;
		Color columnColor = new Color(), blockColor = new Color();
		Block block = new Block(world, 0, 0, 0);
		BlockModelView blockModel = new BlockModelView(model);

		int x, y, z;
		for (x = min.getX(); x <= max.getX(); x++){
			for (z = min.getZ(); z <= max.getZ(); z++){

				maxHeight = 0;
				columnColor.set(0, 0, 0, 1, true);

				minY = Math.max(min.getY(), world.getMinY(x, z));
				maxY = Math.min(max.getY(), world.getMaxY(x, z));

				for (y = minY; y <= maxY; y++){
					block.set(x, y, z);
					blockColor.set(0, 0, 0, 0, true);
					blockModel.initialize();

					try {
						modelFactory.render(block, blockModel, blockColor);
					} catch (NoSuchResourceException e) {
						try {
							modelFactory.render(block, BlockState.MISSING, blockModel.reset(), blockColor);
						} catch (NoSuchResourceException e2) {
							e.addSuppressed(e2);
						}
						//Logger.global.noFloodDebug(block.getBlockState().getFullId() + "-hiresModelRenderer-blockmodelerr", "Failed to create BlockModel for BlockState: " + block.getBlockState() + " (" + e.toString() + ")");
					}

					// skip empty blocks
					if (blockModel.getSize() <= 0) continue;

					// move block-model to correct position
					blockModel.translate(x - modelAnchor.getX(), y - modelAnchor.getY(), z - modelAnchor.getZ());
					
					//update color and height (only if not 100% translucent)
					if (blockColor.a > 0) {
						maxHeight = y;
						columnColor.overlay(blockColor);
					}
					
					//random offset
					if (block.getBlockState().isRandomOffset){
						dx = (hashToFloat(x, z, 123984) - 0.5f) * 0.75f;
						dz = (hashToFloat(x, z, 345542) - 0.5f) * 0.75f;
						blockModel.translate(dx, 0, dz);
					}
				}

				tileMeta.setHeight(x, z, maxHeight);
				tileMeta.setColor(x, z, columnColor);
				
			}
		}

		return tileMeta;
	}

	/**
	 * Hashes the provided position to a random float between 0 and 1.<br>
	 * <br>
	 * <i>(Implementation adapted from https://github.com/SpongePowered/SpongeAPI/blob/ecd761a70219e467dea47a09fc310e8238e9911f/src/main/java/org/spongepowered/api/extra/skylands/SkylandsUtil.java)</i>
	 *
	 * @param x The x component of the position
	 * @param z The z component of the position
	 * @param seed A seed for the hashing
	 * @return The hashed value between 0 and 1
	 */
	public static float hashToFloat(int x, int z, long seed) {
		final long hash = x * 73428767 ^ z * 4382893 ^ seed * 457;
		return (hash * (hash + 456149) & 0x00ffffff) / (float) 0x01000000;
	}
	
}