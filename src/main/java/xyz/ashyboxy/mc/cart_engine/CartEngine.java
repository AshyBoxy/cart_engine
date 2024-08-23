package xyz.ashyboxy.mc.cart_engine;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CartEngine implements ModInitializer {
	public static final String MOD_ID = "cart_engine";
    public static final Logger LOGGER = LoggerFactory.getLogger("Cart Engine");

	public static final double tps = 20;
	public static final double maxSpeed = 34 / tps;
	public static final double maxMomentum = maxSpeed * 5;
	public static final double vanillaMaxSpeed = 8 / tps;
	public static final double vanillaMaxMomentum = 40 / tps;

	@Override
	public void onInitialize() {
		LOGGER.info("Unfortunately not including railguns");
	}
}
